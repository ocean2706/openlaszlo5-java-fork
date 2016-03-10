/* *****************************************************************************
 * FileUtils.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;
import org.openlaszlo.server.LPS;

// A dir is absolute if it begins with "" (the empty string to
// the left of the initial '/'), or a drive letter.
class AbsolutePathnameTester {
     static boolean test(List<String> dirs) {
        if (!dirs.isEmpty()) {
            String dir = dirs.get(0);
            // Add '/' since c:/ will be split to c:, which
            // isn't absolute.  Also turns "" into "/" which
            // is absolute, so the UNIX case can share this
            // test.
            return new File(dir + "/").isAbsolute();
        }
        return false;
    }
}

/**
 * A utility class containing file utility functions.
 *
 * @author Oliver Steele
 * @author Eric Bloch
 */
public abstract class FileUtils {

    private static final Logger mLogger = Logger.getLogger(FileUtils.class);

    // TODO: [2003-09-23 bloch] could build out the encoder a bit more
    public static final String GZIP = "gzip";
    public static final String DEFLATE = "deflate";
    public static final int GZIP_HEADER_LENGTH = 10;
    public static final int MIN_GZIP_PEEK = 2;
    public static final int MIN_SWF_PEEK = 9;

    public static final int BUFFER_SIZE;
    public static final int THROTTLE;

    static {
        int buffersize = 10240;
        int lpsthrottle = 0;
        try {
            String throttle = LPS.getProperty("lps.throttle");
            buffersize = Integer.parseInt(LPS.getProperty("buffer.size", "10240"));
            if (throttle != null) {
                lpsthrottle = Integer.parseInt(throttle);
                buffersize = 1024;
                mLogger.info("throttle " + throttle);
            }
        } catch (RuntimeException e) {
            mLogger.error("Exception initializing FileUtils", e);
        } catch (Exception e) {
            mLogger.error("Exception initializing FileUtils", e);
        }
        BUFFER_SIZE = buffersize;
        THROTTLE = lpsthrottle;
    }

    /**
     * Simple enum for encodings
     * 
     * @author André Bargull
     */
    public static enum Encoding {
        // UTF-8
        UTF8("UTF-8", "UTF-8", 3),
        // UTF-16 Little Endian
        UTF16_LE("UTF-16LE", "UTF-16", 2),
        // UTF-16 Big Endian
        UTF16_BE("UTF-16BE", "UTF-16", 2),
        // unknown encoding
        UNKNOWN(null, null, 0);

        private final String charset;
        private final String xmlencoding;
        private final int bom;

        private Encoding(String charset, String xmlencoding, int bom) {
            this.charset = charset;
            this.xmlencoding = xmlencoding;
            this.bom = bom;
        }

        /**
         * Returns the Java charset name
         * 
         * @see Charset#forName(String)
         */
        public String getCharset() {
            return charset;
        }

        /**
         * Return the XML-encoding name
         */
        public String getXMLEncoding() {
            return xmlencoding;
        }

        /**
         * Returns the length of the BOM
         */
        public int getBOMLength() {
            return bom;
        }
    }

    /** Returns the contents of <var>file</var> as a byte[].
     * @param file a File
     * @return a byte[]
     * @throws IOException if an error occurs
     */
    public static byte[] readFileBytes(File file)
        throws IOException
    {
        InputStream istr = new FileInputStream(file);
        byte bytes[] = new byte[istr.available()];
        istr.read(bytes);
        istr.close();
        return bytes;
    }

    /** Returns the contents of <var>file</var> as a String, using UTF-8 character encoding <var>encoding</var>.
     * @param file a File
     * @return a String
     * @throws IOException if an error occurs
     */
    public static String readFileString(File file)
        throws IOException
    {
        byte data[] = readFileBytes(file);
        return new String(data, "UTF-8");
    }

    /** Returns the contents of <var>file</var> as a String, using character encoding <var>encoding</var>.
     * @param file a File
     * @param defaultEncoding a character set encoding name
     * @return a String
     * @throws IOException if an error occurs
     */
    public static String readFileString(File file, String defaultEncoding)
        throws IOException
    {
        Reader reader = makeXMLReaderForFile(file.getAbsolutePath(), defaultEncoding);
        return readString(reader);
    }

    /**
     * Reads {@code reader} completely into a {@code String}. The {@code Reader}
     * will be closed after this operation.
     * 
     * @param reader
     * @return the reader's content
     * @throws IOException
     */
    public static String readString(Reader reader) throws IOException {
        StringWriter writer = new StringWriter();
        send(reader, writer);
        close(reader);
        return writer.toString();
    }

    private static final int MAX_SIZE_PROLOG = 256;
    private static final Pattern pXMLProlog;
    static {
        // http://www.w3.org/TR/REC-xml/#NT-prolog
        String key = "[a-zA-Z0-9_\\-.]+";
        String value = "[a-zA-Z0-9_\\-.]*";
        // other: 'version' or 'standalone'
        String other = "(?:\\s+" + key + "\\s*=\\s*[\"']" + value + "[\"'])*";
        String encoding = "(?:\\s+encoding\\s*=\\s*[\"'](" + value + ")[\"'])";
        String p = "^\\s*<\\?xml" + other + encoding + other + "\\s*\\?>";
        pXMLProlog = Pattern.compile(p);
    }

    /**
     * Attempt to deduce the encoding of an XML file, by looking for the
     * "encoding" attribute in the XML declaration and reading the BOM if
     * present. If no encoding could be deduced, {@code defaultEncoding} is used
     * instead.
     * 
     * @param is
     *            InputStream to read from
     * @param defaultEncoding
     *            Fallback for encoding
     * @return a reader with the computed encoding
     * @throws IOException
     *             if an error occurs
     */
    public static Reader getXMLStreamReader(InputStream is, String defaultEncoding)
      throws IOException {
        int size = MAX_SIZE_PROLOG;
        byte array[] = new byte[size];
        boolean mark = is.markSupported();

        if (mark) {
            is.mark(size);
        }

        // fill buffer
        int total = readFully(is, array);

        // http://www.w3.org/TR/REC-xml/#sec-guessing-no-ext-info
        // try to read encoding
        Encoding enc = getEncoding(array);
        int offset = enc.getBOMLength();

        // guess best charset to read prolog
        String cs;
        if (enc != Encoding.UNKNOWN) {
            cs = enc.getCharset();
        } else {
            Encoding guess = guessXMLEncoding(array);
            if (guess != Encoding.UNKNOWN) {
                cs = guess.getCharset();
            } else {
                cs = defaultEncoding;
            }
        }

        // read prolog
        String charset;
        String prolog = new String(array, offset, total - offset, cs);
        Matcher matcher = pXMLProlog.matcher(prolog);
        if (matcher.find()) {
            String xmlencoding = matcher.group(1);
            if (enc == Encoding.UNKNOWN) {
                // no BOM, use encoding from prolog
                charset = xmlencoding;
            } else if (LZUtils.equalsIgnoreCase(enc.getXMLEncoding(), xmlencoding)
                || LZUtils.equalsIgnoreCase(enc.getCharset(), xmlencoding)) {
                // BOM and encoding from prolog must match
                charset = enc.getCharset();
            } else {
                String message = String.format("Encoding error: '%s' != '%s'",
                    enc.getXMLEncoding(), xmlencoding);
                throw new ChainedException(message);
            }
        } else if (enc != Encoding.UNKNOWN) {
            // no prolog, use encoding from BOM
            charset = enc.getCharset();
        } else {
            // neither prolog nor BOM, use default
            charset = defaultEncoding;
        }

        // revert read operations
        if (mark) {
            is.reset();
            is.mark(-1);
        } else {
            ByteArrayInputStream bais = new ByteArrayInputStream(array, 0, total);
            SequenceInputStream seq = new SequenceInputStream(bais, is);
            is = seq;
        }

        // skip BOM
        if (offset > 0) {
            skipFully(is, offset);
        }

        return new InputStreamReader(is, charset);
    }

    /**
     * Reads from {@code is} up to {@code b.length} bytes into {@code b}. If
     * less than {@code b.length} bytes are read, {@code is} is at EOF.
     * 
     * @param is
     * @param b
     * @return total number of bytes read
     * @throws IOException
     */
    private static int readFully(InputStream is, byte b[]) throws IOException {
        int size = b.length;
        int n = -1, total = 0;
        while ((size - total) > 0
            && (n = is.read(b, total, size - total)) != -1) {
            total += n;
        }
        assert (n == -1) ? (total < size) : (total == size);

        return total;
    }

    /**
     * Skips {@code n} bytes from {@code is} using
     * {@link InputStream#skip(long)}. {@code skip()} may be applied multiple
     * times to ensure no less than {@code n} bytes are skipped.
     * 
     * @param is
     * @param n
     * @return total number of bytes skipped
     * @throws IOException
     */
    private static long skipFully(InputStream is, long n) throws IOException {
        // just an arbitrary number
        final int MAX_SKIP = 10;

        long s, skip = n;
        int a = 0;
        while ((skip -= (s = is.skip(skip))) > 0) {
            a = (s <= 0) ? (a + 1) : 0;
            if (a > MAX_SKIP) {
                break;
            }
        }
        assert (skip == 0) || (a > MAX_SKIP);

        return (n - skip);
    }

    /**
     * Returns {@link Encoding} for {@code bs} by reading the BOM
     * 
     * @param bs
     *            byte-array to read from
     * @return Encoding from BOM or {@link Encoding#UNKNOWN}
     */
    private static Encoding getEncoding(byte bs[]) {
        if (bs.length < 3) {
            return Encoding.UNKNOWN;
        }

        int c1 = ((int) bs[0]) & 0xff;
        int c2 = ((int) bs[1]) & 0xff;
        int c3 = ((int) bs[2]) & 0xff;
        if (c1 == 0xFE && c2 == 0xFF) {
            // UTF16 Big Endian BOM
            return Encoding.UTF16_BE;
        } else if (c1 == 0xFF && c2 == 0xFE) {
            // UTF16 Little Endian BOM
            return Encoding.UTF16_LE;
        } else if (c1 == 0xEF && c2 == 0xBB && c3 == 0xBF) {
            // UTF-8 BOM
            return Encoding.UTF8;
        } else {
            return Encoding.UNKNOWN;
        }
    }

    /**
     * Returns a likely {@link Encoding} for {@code bs} by reading the first
     * bytes
     * 
     * @param bs
     *            byte-array to read from
     * @return Possible encoding or {@link Encoding#UNKNOWN}
     */
    private static Encoding guessXMLEncoding(byte bs[]) {
        if (bs.length < 4) {
            return Encoding.UNKNOWN;
        }
        int c1 = ((int) bs[0]) & 0xff;
        int c2 = ((int) bs[1]) & 0xff;
        int c3 = ((int) bs[2]) & 0xff;
        int c4 = ((int) bs[3]) & 0xff;
        // we're looking for the xml declaration: "<?xml"
        if (c1 == 0x00 && c2 == 0x3C && c3 == 0x00 && c4 == 0x3F) {
            // UTF16 Big Endian
            return Encoding.UTF16_BE;
        } else if (c1 == 0x3C && c2 == 0x00 && c3 == 0x3F && c4 == 0x00) {
            // UTF16 Little Endian
            return Encoding.UTF16_LE;
        } else if (c1 == 0x3C && c2 == 0x3F && c3 == 0x78 && c4 == 0x6D) {
            // UTF-8 or other 7-/8-bit encoding with standard ASCII characters
            return Encoding.UTF8;
        } else {
            return Encoding.UNKNOWN;
        }
    }

    /**
     * Retrieve the encoding of a text file based on a possibly existing
     * Byte Order Marker (BOM) within the leading bytes of the file.
     *
     * see http://www.w3.org/TR/CSS21/syndata.html#charset.
     *
     * @param in BufferedInputStream, must be positioned at the first byte of the file
     * @return the encoding if a BOM is found (UTF-8,UTF-16BE,UTF-16LE)
     * @throws IOException
     */
    public static String detectBOMEncoding(BufferedInputStream in) throws IOException {
        Encoding enc = getBOMEncoding(in);
        return enc.getCharset();
    }

    /**
     * Retrieve the encoding of a text file based on a possibly existing
     * Byte Order Marker (BOM) within the leading bytes of the file.
     *
     * see http://www.w3.org/TR/CSS21/syndata.html#charset.
     *
     * @param in BufferedInputStream, must be positioned at the first byte of the file
     * @return the encoding if a BOM is found (UTF-8,UTF-16BE,UTF-16LE)
     * @throws IOException
     */
    public static Encoding getBOMEncoding(BufferedInputStream in) throws IOException {
        final int maxBytesToRead = 3; // maximum number of bytes which need to
                                      // be read
        byte[] buffer = new byte[maxBytesToRead];

        in.mark(maxBytesToRead);
        // Read bytes that might contain BOM
        int results = in.read(buffer); // max number of bytes read to determine
        if (results == -1) {
            mLogger.error("Reading bytes was unsuccessful!");
            throw new IOException();
        } else if (mLogger.isDebugEnabled()) {
            mLogger.debug("Read the following bytes: " + Arrays.toString(buffer));
        }
        // reset stream and clear mark
        in.reset();
        in.mark(-1);

        Encoding enc = getEncoding(buffer);
        if (mLogger.isDebugEnabled()) {
            if (enc != Encoding.UNKNOWN) {
                mLogger.debug("Found BOM on file, encoding is " + enc.getCharset());
            } else {
                mLogger.debug("no BOM found in file!");
            }
        }
        return enc;
    }

    /**
     * Set up a reader for an XML file with the correct charset encoding, and strip
     * out any Unicode Byte Order Mark if there is one present. We need to scan the file
     * once to try to parse the charset encoding in the XML declaration.
     * @param pathname the name of a file to open
     * @return reader stream for the file
     */
    public static Reader makeXMLReaderForFile (String pathname, String defaultEncoding)
      throws IOException {
        InputStream ifs = new BufferedInputStream( new FileInputStream(pathname) );
        if (pathname.endsWith(".lzo")) {
            // It's a zip file, scan it for entry named "lzo" and
            // return the inputstream that reads from it.
            ZipInputStream zis = new ZipInputStream(ifs);
            ZipEntry entry;
            while (true) {
                entry = zis.getNextEntry();
                if (entry == null) {
                    throw new IOException("could not find main lzo entry in .lzo zip file "+pathname);
                }
                if ("lzo".equals(entry.getName())) {
                    ifs = zis;
                    break;
                }
            }
        }

        Reader reader = getXMLStreamReader(toByteArrayInputStream(ifs), defaultEncoding);
        close(ifs);

        return reader;
    }

    /**
     * Reads {@code input} completely and returns a {@link ByteArrayInputStream}
     * with the same content. This operation ensures all bytes from
     * {@code input} completely reside in-memory.
     * 
     * @param input
     * @return
     * @throws IOException
     */
    private static InputStream toByteArrayInputStream(InputStream input)
        throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        send(input, bout);
        byte[] array = bout.toByteArray();
        ByteArrayInputStream bais = new ByteArrayInputStream(array);

        return bais;
    }

    /** Read a (pushback-able) byte input stream looking for some form of
        Unicode Byte Order Mark Defaults. If found, strip it out.
     * @param pbis a byte input stream
     * @return encoding name, defaults to null if we have no byte order mark
     */
    public static String stripByteOrderMark (PushbackInputStream pbis) throws IOException {
        // We need to peek at the stream and if the first three chars
        // are a UTF-8 or UTF-16 encoded BOM (byte order mark) we will
        // discard them.

        int cs[] = { pbis.read(), pbis.read(), pbis.read() };
        Encoding enc = Encoding.UNKNOWN;
        if (cs[0] != -1 && cs[1] != -1 && cs[2] != -1) {
            byte bs[] = { (byte) cs[0], (byte) cs[1], (byte) cs[2] };
            enc = getEncoding(bs);
        }

        // discard all BOM chars
        for (int i = 2; i >= enc.getBOMLength(); --i) {
            if (cs[i] != -1) {
                pbis.unread(cs[i]);
            }
        }

        return enc.getCharset();
    }

    /** Read a byte array looking for some form of Unicode Byte Order Mark Defaults.
     * @param raw bytes
     * @return the count of characters to skip
     */
    public static int stripByteOrderMark(byte[] raw) {
        // We need to peek at the stream and if the first three chars
        // are a UTF-8 or UTF-16 encoded BOM (byte order mark) we will
        // discard them.
        Encoding enc = getEncoding(raw);
        return enc.getBOMLength();
    }

    /**
     * @param file file to read
     * @return size of file
     * @throws FileNotFoundException
     * @throws IOException
     */
    static public long fileSize(File file)
        throws IOException, FileNotFoundException {

        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(file, "r");
            return raf.length();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception e) {
mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="ignoring exception while closing random access file: "
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-246")
                    , e);
                }
            }
        }
    }

    /**
     * @param file file to read
     * @return size of file or -1 if it can't be determined.
     */
    static public long getSize(File file) {
        try {
            return fileSize(file);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Returns the base name (without the extension) of
     * <var>pathname</var>.  For example,
     * <code>FileUtils.getBase("a/b.c") == "a/b"</code>.
     * @param pathname a String
     * @return a String
     */
    public static String getBase(String pathname) {
        int pos = pathname.lastIndexOf('.');
        if (pos >= 0) {
            return pathname.substring(0, pos);
        } else {
            return pathname;
        }
    }

    /**
     * @return the ending extension
     * @param pathname a string
     */
    public static String getExtension(String pathname) {
        int pos = pathname.lastIndexOf('.');
        if (pos >= 0 && pos != pathname.length() - 1) {
            return pathname.substring(pos+1);
        }
        return "";
    }

    /** Inserts a subdir between the file and existing location.
     * Probably deals with cases that will never happen.
     * @return path a string specifying file in subdir
     * @param pathname a string
     * @param subdir a string
     */
    public static String insertSubdir(String pathname, String subdir) {
        File f = new File(pathname);
        File result = null;
        int lastOff = (pathname.length() - 1);
        if (lastOff != -1) {
            // NOTE [20100421 anba]:
            // If the last character is the file separator or '/', "subdir" is
            // not inserted in between the path of the file denoted by pathname.
            // Yepp, that means insertSubdir("a/b", "c") => "a/c/b",
            // whereas insertSubdir("a/b/", "c") => "a/b/c".
            // This doesn't make sense to me, but it is required :-/
            char lastChar = pathname.charAt(lastOff);
            if ((lastChar == File.separatorChar) || (lastChar == '/')) {
                result = new File(pathname, subdir);
            }
        }
        if (result == null) {
            result = new File(new File(f.getParentFile(), subdir), f.getName());
        }
        return result.getPath();
    }

    public static String insertSuffix(String pathname, String suffix) {
        String base=getBase(pathname);
        String type=getExtension(pathname);
        return (base + suffix + '.' + type);
    }

    public static File[] matchPlusSuffix(File fFullPath) {
        File mDir = fFullPath.getParentFile();
        String sName = fFullPath.getName();
        String sBase = getBase(sName);
        String sType = getExtension(sName);

        class StartsWithNameFilter implements FilenameFilter {
                final String _name;
            final String _type;

            StartsWithNameFilter(String sBase, String sType) {
                _name = sBase;
                _type = sType;
            }

            public boolean accept(File fDir, String sName) {
        final boolean lenTest = (_name.length() + _type.length() + 1) < sName.length();
                return ((lenTest && sName.startsWith(_name) && sName.endsWith(_type)));
            }
        }
        File[] fileNames = mDir.listFiles(new StartsWithNameFilter(sBase, sType));
        if ((fileNames == null) || (fileNames.length == 0)) {
        return null;
        } else {
        return fileNames;
         }
     }

    /** Remove the : from Windows Drive specifier found
     *  in Absolute path names.
     *
     * @param src
     * @return a fixed string
     */
    public static String fixAbsWindowsPaths(String src) {

        // See if we're on Windows
        if (File.separatorChar == '/')
            return src;

        // One char strings
        if (src.length() < 2)
            return src;

        // Check to make sure we have a Windows absolute path
        if (src.charAt(1) != ':')
            return src;

        // Sanity check to make sure we really have a drive specifier
        char c = src.charAt(0);
        int t = Character.getType(c);
        boolean isAscii = (t == Character.UPPERCASE_LETTER ||
                           t == Character.LOWERCASE_LETTER);
        if (!isAscii) {
            return src;
        }

        // Remove the : that was the 2nd character.
        return src.substring(0,1) + src.substring(2);
    }

    /**
     * Insure a file and all parent directories exist.
     * @param file the file
     * @throws IOException
     */
    public static void makeFileAndParentDirs(File file)
        throws IOException {
        File dir = file.getParentFile();
        if (dir != null)
            dir.mkdirs();
        file.createNewFile();
    }

    /** Delete an entire directory tree. Named after the Python
     * function.
     * @return true if full removal of cache was successful, otherwise false.
     */
    public static boolean rmTree(File file) {
        boolean ok = true;
        File[] files = file.listFiles(); // null if not a directory
        if (files != null) {
            for (File f : files) {
                ok &= rmTree(f);
            }
        }
        ok &= file.delete();

        return ok;
    }

    /**
     * Read the input stream and write contents to output stream.
     * @param input stream to read
     * @param output stream to write
     * @return number of bytes written to output
     * @throws IOException if there's an error reading or writing
     * the steams
     */
    public static int send(InputStream input, OutputStream output)
        throws IOException {

        int available = input.available();
        int bsize;
        if (available == 0) {
            bsize = BUFFER_SIZE;
        } else {
            bsize = Math.min(input.available(), BUFFER_SIZE);
        }
        return send(input, output, bsize);
    }

    /**
     * Exception when there's an error writing a stream
     */
    @SuppressWarnings("serial")
    public static class StreamWritingException  extends IOException {
        public StreamWritingException (String s)
        {
            super(s);
        }
    }

    /**
     * Exception when there's an error reading a stream
     */
    @SuppressWarnings("serial")
    public static class StreamReadingException  extends IOException {
        public StreamReadingException (String s)
        {
            super(s);
        }
    }

    /**
     * Read the input character stream and write contents to output stream.
     * @param input stream to read
     * @param output stream to write
     * @return number of bytes written to output
     * @throws IOException if there's an error reading or writing
     * the steams
     */
    public static int send(Reader input, Writer output)
        throws IOException {
        int bsize = BUFFER_SIZE;
        return send(input, output, bsize);
    }

    /**
     * Read the input stream and write contents to output
     * stream using a buffer of the requested size.
     * @param input stream to read
     * @param output stream to write
     * @param size size of buffer to use
     * @return number of bytes written to output
     * @throws IOException if there's an error reading or writing
     * the steams
     */
    public static int send(Reader input, Writer output, int size)
        throws IOException {
        int c = 0;
        char[] buffer = new char[size];
        int b = 0;
        while((b = input.read(buffer)) > 0) {
            c += b;
            output.write(buffer, 0, b);
        }
        return c;
    }

    /**
     * Read the input stream and write contents to output
     * stream using a buffer of the requested size.
     * @param input stream to read
     * @param output stream to write
     * @param size size of buffer to use
     * @return number of bytes written to output
     * @throws IOException if there's an error reading or writing
     * the steams
     */
    public static int send(InputStream input, OutputStream output, int size)
        throws IOException {
        int c = 0;
        byte[] buffer = new byte[size];
        int b = 0;
        while((b = input.read(buffer)) > 0) {
            c += b;
            output.write(buffer, 0, b);
        }
        return c;
    }

    /**
     * Read the input stream and write contents to output stream.
     * @param input stream to read
     * @param output stream to write
     * @return number of bytes written to output
     * @throws StreamWritingException if there's an error writing output
     * @throws StreamReadingException if there's an error reading input
     */
    public static int sendToStream(InputStream input, OutputStream output)
        throws IOException {

        int available = input.available();
        int bsize;
        if (available == 0) {
            bsize = BUFFER_SIZE;
        } else {
            bsize = Math.min(input.available(), BUFFER_SIZE);
        }
        return sendToStream(input, output, bsize);
    }

    /**
     * Read the input stream and write contents to output
     * stream using a buffer of the requested size.
     * @param input stream to read
     * @param output stream to write
     * @param size size of buffer to use
     * @return number of bytes written to output
     * @throws StreamWritingException if there's an error writing output
     * @throws StreamReadingException if there's an error reading input
     */
    public static int sendToStream(InputStream input,
            OutputStream output, int size)
        throws IOException {
        int c = 0;
        byte[] buffer = new byte[size];
        int b = 0;
        while(true) {
            try {
                // Until end of stream
                if ((b = input.read(buffer)) <= 0) {
                    return c;
                }
            } catch (IOException e) {
                throw new StreamReadingException(e.getMessage());
            }
            c += b;
            try {
                output.write(buffer, 0, b);
            } catch (IOException e) {
                throw new StreamWritingException(e.getMessage());
            }
            if (THROTTLE != 0) {
                try {
mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Sleeping " + p[0] + " msecs "
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-519", new Object[] {THROTTLE})
                    );
                    Thread.sleep(THROTTLE);
                } catch (InterruptedException e) {
mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="interrupted during sleep"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-529")
                    , e);
                }
            }
        }
    }

    /**
     * Read the input stream and write contents to output stream.
     * @param input stream to read
     * @param output stream to write
     * @return number of bytes written to output
     * @throws IOException
     */
    public static int escapeHTMLAndSend(InputStream input, OutputStream output)
        throws IOException {

        int available = input.available();
        int bsize;
        if (available == 0) {
            bsize = BUFFER_SIZE;
        } else {
            bsize = Math.min(input.available(), BUFFER_SIZE);
        }
        return escapeHTMLAndSend(input, output, bsize);
    }

    /**
     * Read the input stream and write contents to output
     * stream using a buffer of the requested size.
     * @param input stream to read
     * @param output stream to write
     * @param size size of buffer to use
     * @return number of bytes written to output
     * @throws IOException
     */
    public static int escapeHTMLAndSend(InputStream input, OutputStream output, int size)
        throws IOException {
        int c = 0;
        byte[] buffer = new byte[size];
        byte[] buffer2 = new byte[size*4]; // big enuff to escape each char
        int b = 0;
        int b2 = 0;
        while((b = input.read(buffer)) > 0) {
            b2 = escapeHTML(buffer, b, buffer2);
            c += b2;
            output.write(buffer2, 0, b2);
        }
        return c;
    }

    /**
     * copy input into output escaping HTML chars
     * escaped.  Output buffer must be 4 x the input buffer size.
     *
     * @param ib input buffer
     * @param buflen input buffer size;
     * @param ob output buffer size;
     * @return number of bytes written to ob
     */
    public static int escapeHTML(byte[] ib, int buflen, byte[] ob) {
        int bc = 0;
        for(int i = 0; i < buflen; i++) {
            if (ib[i] == '<') {
                ob[bc++] = '&';
                ob[bc++] = 'l';
                ob[bc++] = 't';
                ob[bc++] = ';';
            } else if (ib[i] == '>') {
                ob[bc++] = '&';
                ob[bc++] = 'g';
                ob[bc++] = 't';
                ob[bc++] = ';';
            } else {
                ob[bc++] = ib[i];
            }
        }

        return bc;
    }

    @SuppressWarnings("serial")
    public static class RelativizationError extends Error {
        RelativizationError(String message) {
            super(message);
        }
    }

    /**
       Find maximum common prefix of path1 and path2
     */
    public static String findMaxCommonPrefix(String path1, String path2) {
        int i = 0;
        int len1 = path1.length();
        int len2 = path2.length();
        while ((i < len1) && (i < len2)) {
            if (path1.charAt(i) != path2.charAt(i)) {
                break;
            }
            i++;
        }
        if (i > 1 && path1.charAt(i-1) == '/') {
            return path1.substring(0, i-1);
        } else {
            return path1.substring(0, i);
        }

    }


    /** Return a path that resolves to the same file relative to
     * dest as path does relative to source. Paths use '/' as
     * separators. source and dest are assumed to be directories. */
    public static String adjustRelativePath(String path,
                                            String source, String dest)
        throws RelativizationError
    {
        final String separator = "/";
        if (path.endsWith(separator)) {
            return path;
        }
        if (source.endsWith(separator)) {
            source = source.substring(0, source.length() - 1);
        }
        if (dest.endsWith(separator)) {
            dest = dest.substring(0, dest.length() - 1);
        }
        List<String> sourcedirs = new ArrayList<String>(Arrays.asList(StringUtils.split(source, separator)));
        List<String> destdirs = new ArrayList<String>(Arrays.asList(StringUtils.split(dest, separator)));
        normalizePath(sourcedirs);
        normalizePath(destdirs);
        // Remove the common prefix
        while (!sourcedirs.isEmpty() && !destdirs.isEmpty() &&
               sourcedirs.get(0).equals(destdirs.get(0))) {
            sourcedirs.remove(0);
            destdirs.remove(0);
        }
        // If either dir is absolute, we can't relativize a file in
        // one to the other.  Do this after removing the common
        // prefix, since if both files are absolute and are not on
        // different drives (Windows only) then a pathname that's
        // relative to one can be rewritten relative to the other.
        //
        //System.err.println("" + sourcedirs + " -> " + AbsolutePathnameTester.test(sourcedirs));
        if (AbsolutePathnameTester.test(sourcedirs) ||
            AbsolutePathnameTester.test(destdirs)) {
            throw new RelativizationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="can't create a pathname relative to " + p[0] + " that refers to a file relative to " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-657", new Object[] {dest, source})
            );
        }
        List<String> components = new ArrayList<String>();
        // '..'s to get out of the source directory...
        for (String sourcedir : sourcedirs) {
            components.add("..");
        }
        // ...and directory names to get into the dest directory
        for (String destdir : destdirs) {
            components.add(destdir);
        }
        components.add(path);
        return StringUtils.join(components, separator);
    }

    /**
     * @return the path to the given file, relative to the
     * given directory name.  If the file isn't inside the directory,
     * just return the entire file name.  Path separators are
     * modified to always be '/'.
     */
    public static String relativePath(File file, String directoryName) {
        try {
            String fileName = file.getCanonicalPath().replace('\\', '/');
            String dirName = new File(directoryName).getCanonicalPath().replace('\\', '/');
            if (!fileName.startsWith(dirName)) {
                return fileName;
            }
            return fileName.substring(dirName.length());
        } catch (IOException ioe) {
            throw new ChainedException(ioe);
        }
    }

    /**
     * @return the path to the given file, relative to the
     * given directory name.  If the file isn't inside the directory,
     * just return the entire file name.  Path separators are
     * modified to always be '/'.
     */
    public static String relativePath(String filename, String directoryName) {
        return relativePath(new File(filename), directoryName);
    }

    public static String relativePath(File fFile, File directoryName) {
        return relativePath(fFile, directoryName.toString());
    }

    public static String toURLPath(File file) {
      return toURLPath(file.getPath());
    }

    public static String toURLPath(String path) {
        // Can't call File.toURL, since it looks at the disk
        return StringUtils.join(StringUtils.split(path, File.separator),
                                "/");
    }

    public static String fromURLPath(String path) {
      return StringUtils.join(StringUtils.split(path,
                                                "/"),
                              File.separator);
    }

    /** Remove "." and non-top "..".  Destructive. */
    public static void normalizePath(List<String> path) {
        for (int i = 0; i < path.size(); i++) {
            String component = path.get(i);
            if (component.equals(".") || component.equals("")) {
                path.remove(i--);
            } else if (component.equals("..") && i > 0) {
                path.remove(i--);
                path.remove(i--);
            }
        }
    }

    /**
     * Encode the given file as requested to an outputFile.
     * @param inputFile file to be encoded
     * @param outputFile encoded file
     * @param encoding type of encoding to use ("gzip" or "deflate" supported).
     * @throws ChainedException if the requested encoding is not supported.
     */
    static public void encode(File inputFile, File outputFile, String encoding)
        throws IOException {

        FileInputStream    in = null;

        try {
            in = new FileInputStream(inputFile);
            encode(in, outputFile, encoding);
        } finally {
            if (in != null)
                in.close();
        }
    }

    /**
     * Decode the given file as requested to an outputFile.
     * @param inputFile file to be decoded
     * @param outputFile decoded file
     * @param encoding type of encoding to use ("gzip" or "deflate" supported).
     * @throws ChainedException if the requested encoding is not supported.
     */
    static public void decode(File inputFile, File outputFile, String encoding)
        throws IOException {

        mLogger.debug("Starting decoding from " + encoding);
        FilterInputStream in = null;
        OutputStream out = new FileOutputStream(outputFile);
        InputStream ins = new FileInputStream(inputFile);

        try {
            if (encoding.equals(GZIP)) {
                in = new GZIPInputStream(ins);
            } else if (encoding.equals(DEFLATE)) {
                in = new InflaterInputStream(ins);
            } else {
String message =
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Unsuppored encoding: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-771", new Object[] {encoding});

                mLogger.error(message);
                throw new ChainedException(message);
            }

            // Note: use fixed size for buffer.  The send(in, out) method
            // will use in.available() to guess a good buffersize but
            // that won't work out well for these input streams.
            int b = FileUtils.send(in, out, BUFFER_SIZE);
mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="decoded into " + p[0] + " bytes"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-787", new Object[] {b})
             );
        } finally {
            close(in);
            close(ins);
            close(out);
        }
mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Done decoding from " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-800", new Object[] {encoding})
        );
    }

    /**
     * Encode the given file as requested to an outputFile.
     * @param input file to be encodeded
     * @param outputFile encoded file
     * @param encoding type of encoding to use ("gzip" or "deflate" supported).
     * @throws IOException if the requested encoding is not supported.
     */
    static public void encode(InputStream input, File outputFile, String encoding)
        throws IOException {

        FileOutputStream   out = null;
        FilterOutputStream filter  = null;

mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Encoding into " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-823", new Object[] {encoding})
        );

        try {
            FileUtils.makeFileAndParentDirs(outputFile);

            out = new FileOutputStream(outputFile);

            if (encoding.equals(GZIP)) {
                filter = new GZIPOutputStream(out);
            } else if (encoding.equals(DEFLATE)) {
                filter = new DeflaterOutputStream(out);
            } else {
String message =
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Unsupported encoding: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-842", new Object[] {encoding});

                mLogger.error(message);
                throw new ChainedException(message);
            }

            FileUtils.send(input, filter, BUFFER_SIZE);

        } finally {
            if (filter != null)
                close(filter);
            if (out != null)
                close(out);
        }

mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Done encoding into " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-863", new Object[] {encoding})
        );
    }

    /**
     * Make sure you really want to ignore the exception
     * before using this.
     * @param in stream to close w/out throwing an exception
     */
    static public void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Remove all files in the given list that are immediate children
     * of this directory.  This does <b>not</b> recursively remove
     * files in subdirecetories.
     *
     * @return true if all files to be removed, could be removed
     */
    static public boolean deleteFiles(File dir, String[] fileNames) {
        boolean ok = true;

        for(int i = 0; i < fileNames.length; i++) {
            File file = new File(dir + File.separator + fileNames[i]);
            try {
                if (!file.isDirectory()) {
                    file.delete();
                }
            } catch (Throwable e) {
mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="can't delete file: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                FileUtils.class.getName(),"051018-932", new Object[] {fileNames[i]})
                                                  );
                ok = false;
            }
        }
        return ok;
    }

    /**
     * Remove all files that are immediate children
     * of this directory.  This does <b>not</b> recursively remove
     * files in subdirecetories.
     *
     * @return true if all files to be removed, could be removed
     */
    static public boolean deleteFiles(File dir) {

        if (!dir.isDirectory()) {
            return false;
        }
        String[] fileNames = dir.list();

        return deleteFiles(dir, fileNames);
    }

    /**
     * Remove all files that start with the following name
     * in the given directory.  This does <b>not</b> recursively remove
     * files in subdirecetories.
     * @return true if all files to be removed, could be removed
     */
    static public boolean deleteFilesStartingWith(File dir, String name) {

        /**
         * Filter that selects all files that begin with a
         * given file name.  See java.io.FilenameFilter.
         */
        class StartsWithNameFilter implements FilenameFilter {
            final String _name;

            StartsWithNameFilter(String n) {
                _name = n;
            }

            public boolean accept(File d, String n) {
                return (n.startsWith(_name));
            }
        }

        String[] fileNames = dir.list(new StartsWithNameFilter(name));

        return deleteFiles(dir, fileNames);
    }
}
