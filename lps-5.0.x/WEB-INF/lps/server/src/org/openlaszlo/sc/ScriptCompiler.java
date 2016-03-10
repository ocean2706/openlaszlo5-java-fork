/* *****************************************************************************
 * ScriptCompiler.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN ********************************************************
 * Copyright 2001-2006, 2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.  *
 * Use is subject to license terms.                                            *
 * J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.openlaszlo.cache.Cache;
import org.openlaszlo.compiler.CompilationError;
import org.openlaszlo.iv.flash.api.FlashFile;
import org.openlaszlo.iv.flash.api.Frame;
import org.openlaszlo.iv.flash.api.Script;
import org.openlaszlo.iv.flash.api.action.DoAction;
import org.openlaszlo.iv.flash.api.action.Program;
import org.openlaszlo.iv.flash.util.IVException;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;

/** Utility class for compiling scripts, translating Java objects
 * (maps, lists, and strings) to source expressions for the
 * corresponding JavaScript object.
 *
 * @author  Oliver Steele
 */
public class ScriptCompiler extends Cache {
    private static Writer intermediateWriter = null;

    static public final String SCRIPT_CACHE_NAME = "scache";

    /** Map(String properties+script, byte[] bytes), or null if the
     * cache hasn't been initialized, has been cleared, or the cache
     * size is zero. */
    // TODO: [2002-11-28 ows] use org.apache.commons.util.BufferCache?
    // TODO: [2002-11-28 ows] wrap in Collections.synchronizedMap?
    private static ScriptCompiler mScriptCache = null;

    public  ScriptCompiler(String name, File cacheDirectory, Properties props)
        throws IOException {
        super(name, cacheDirectory, props);
    }

    public static ScriptCompiler getScriptCompilerCache() {
        return mScriptCache;
    }

    public static void setIntermediateWriter(Writer writer) {
        intermediateWriter = writer;
    }

    public static synchronized ScriptCompiler initScriptCompilerCache(File cacheDirectory, Properties initprops)
      throws IOException {
        if (mScriptCache != null) {
            return mScriptCache;
        }
        mScriptCache = new ScriptCompiler(SCRIPT_CACHE_NAME, cacheDirectory, initprops);
        return mScriptCache;
    }

    /**
     * Compiles the ActionScript in script to a new movie in the swf
     * file named by outfile.
     *
     * @param script a <code>String</code> value
     * @param outfile a <code>File</code> value
     */
    public static void compile(String script, File outfile, int swfversion)
        throws IOException
    {
        FileOutputStream ostream = new FileOutputStream(outfile);
        compile(script, ostream, swfversion);
        ostream.close();
    }

    /**
     * Compile the ActionScript in script to a movie, that's written
     * to output.
     *
     * @param script a <code>String</code> value
     * @param ostream an <code>OutputStream</code> value
     */
    public static void compile(String script, OutputStream ostream, int swfversion)
        throws IOException
    {
        byte[] action = compileToByteArray(script, new Properties());
        writeScriptToStream(action, ostream, swfversion);
    }

    /*
    // TODO: [2004-01-07 hqm] This cache clearing method has the
    following bug now; if the in memory ScriptCache has not been
    created yet when clearCache is called, then the disk cache won't
    get cleared. We need to make sure mScriptCache is initialized at
    server startup.]
    */
    public static boolean clearCacheStatic() {
        if (mScriptCache != null) {
            return mScriptCache.clearCache();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static final Map<String, Object> castProperties (Properties p) {
        // just for the effect...
        return (Map<String, Object>) ((Map<?, ?>) p);
    }

    private static byte[] _compileToByteArray(String script,
                                              Properties properties) {
        org.openlaszlo.sc.Compiler compiler =
            new org.openlaszlo.sc.Compiler(castProperties(properties));
        try {
            return compiler.compile(script);
        } catch (org.openlaszlo.sc.parser.TokenMgrError e) {
            // The error message isn't helpful, and has the wrong
            // source location in it, so ignore it.
            // TODO: [2003-01-09 ows] Fix the error message.
            throw new CompilerException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Lexical error.  The source location is for the element that contains the erroneous script.  The error may come from an unterminated comment."
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                ScriptCompiler.class.getName(),"051018-119")
);
        }
    }

    /**
     * @return a cache key for the given properties
     */
    static String sortedPropertiesList(Properties props) {
        TreeSet<String> sorted = new TreeSet<String>();
        for (java.util.Enumeration<?> e = props.propertyNames();
             e.hasMoreElements(); ) {
            String key = (String) e.nextElement();

            String value = props.getProperty(key);
            StringBuilder buf = new StringBuilder();
            buf.append(key);
            buf.append(' ');
            buf.append(value);

            sorted.add(buf.toString());
        }
        StringBuilder buf = new StringBuilder();
        for (String str : sorted) {
            buf.append(str);
        }
        String propstring = buf.toString();
        return propstring;
    }

    /** Collects the script into an intermediate output file
     * if we are configured to do so.
     *
     * @param script a script
     */
    private static void writeIntermediateFile(String script)
    {
        if (intermediateWriter != null) {
            try {
                intermediateWriter.write(script);
            }
            catch (IOException ioe) {
                throw new CompilationError("Could not create intermediate script file: " + ioe);
            }
        }
    }

    /** Compiles the specified script into bytecode
     *
     * @param script a script
     * @return an array containing the bytecode
     */
    public static byte[] compileToByteArray(String script,
                                            Properties properties) {

        writeIntermediateFile(script);

        // We only want to keep off the properties that affect the
        // compilation.  Currently, "filename" is the only property
        // that tends to change and that the script compiler ignores,
        // so make a copy of properties that neutralizes that.
        properties = (Properties) properties.clone();
        properties.setProperty("filename", "");
        // Check the cache.  clearCache may clear the cache at any
        // time, so use a copy of it so that it doesn't change state
        // between a test that it's null and a method call on it.
        Item item = null;
        byte[] code = null;
        try {
            if (mScriptCache == null) {
                return _compileToByteArray(script, properties);
            } else {
                // The key is a string representation of the arguments:
                // properties, and the script.
                StringWriter writer = new StringWriter();
                writer.write(sortedPropertiesList(properties));
                writer.getBuffer().append(script);
                String key = writer.toString();
                synchronized (mScriptCache) {
                    item = mScriptCache.findItem(key, null, false);
                }
            }

            if (item.getInfo().getSize() != 0) {
                code = item.getRawByteArray();
            } else {
                code = _compileToByteArray(script, properties);
                // Another thread might already have set this since we
                // called get.  That's okay --- it's the same value.
                synchronized (mScriptCache) {
                    item.update(new ByteArrayInputStream(code), null);
                    item.updateInfo();
                    item.markClean();
                }
            }

            mScriptCache.updateCache(item);

            return (byte[]) code;
        } catch (IOException e) {
            throw new CompilationError(e, "IOException in compilation/script-cache");
        }
    }

    /**
     * @param action actionscript byte codes
     * @param ostream outputstream to write SWF
     */
    public static void writeScriptToStream(byte[] action,
           OutputStream ostream, int swfversion) throws IOException {
        FlashFile file = FlashFile.newFlashFile();
        Script s = new Script(1);
        file.setMainScript(s);
        file.setVersion(swfversion);
        Program program = new Program(action, 0, action.length);
        Frame frame = s.newFrame();
        frame.addFlashObject(new DoAction(program));
        InputStream input;
        try {
            input = file.generate().getInputStream();
        }
        catch (IVException e) {
            throw new ChainedException(e);
        }

        FileUtils.send(input, ostream, 1024);
    }

    public static String objectAsJavascript(Object object) {
        try {
            StringBuilder writer = new StringBuilder();
            writeObject(object, writer);
            return writer.toString();
        } catch (IOException e) {
            throw new ChainedException(e);
        }
    }


    /** Writes a LaszloScript expression that evaluates to a
     * LaszloScript representation of the object.
     *
     * @param elt an element
     * @param writer an appendable
     * @throws java.io.IOException if an error occurs
     */
    @SuppressWarnings("unchecked")
    public static void writeObject(Object object, Appendable writer)
        throws IOException
    {
        if (object instanceof Map) {
            writeMap((Map<String, Object>) object, writer);
        } else if (object instanceof List) {
            writeList((List<Object>) object, writer);
        } else if (object != null) {
            writer.append(object.toString());
        } else {
          // A declared property with no intial value
          writer.append("void 0");
        }
    }

    /** Writes a LaszloScript object literal whose properties are the
     * keys of the map and whose property values are the LaszloScript
     * representations of the map's values.
     *
     * The elements of the map are strings that represent JavaScript
     * expressions, not values.  That is, the value "foo" will compile
     * to a reference to the variable named foo; "'foo'" or "\"foo\""
     * is necessary to enter a string in the map.
     *
     * @param map String -> Object
     * @param writer an appendable
     * @return a string
     */
    private static <V> void writeMap(Map<String, Object> map, Appendable writer)
        throws IOException
    {
        writer.append("{");
        // Sort the keys, so that regression tests aren't sensitive to
        // the undefined order of iterating a (non-TreeMap) Map.
        SortedMap<String, Object> smap = new TreeMap<String, Object>(map);
        for (Iterator<Map.Entry<String, Object>> iter = smap.entrySet().iterator(); iter.hasNext(); ) {
            Map.Entry<String, Object> entry = iter.next();
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!isIdentifier(key))
                key = quote(key);
            writer.append(key).append(": ");
            writeObject(value, writer);
            if (iter.hasNext()) {
                writer.append(", ");
            }
        }
        writer.append("}");
    }

    /** Writes a LaszloScript array literal that evaluates to a
     * LaszloScript array whose elements are LaszloScript
     * representations of the arguments elements.
     *
     * The elements of the list are strings that represent JavaScript
     * expressions, not values.  That is, the value "foo" will compile
     * to a reference to the variable named foo; "'foo'" or "\"foo\""
     * is necessary to enter a string in the array.
     *
     * @param list a list
     * @param writer an appendable
     * @return a string
     */
    private static void writeList(List<Object> list, Appendable writer)
        throws IOException
    {
        writer.append("[");
        for (Iterator<Object> iter = list.iterator();
             iter.hasNext(); ) {
            writeObject(iter.next(), writer);
            if (iter.hasNext()) {
                writer.append(", ");
            }
        }
        writer.append("]");
    }

    private static final String _KEYWORDS[] = {
        // Keywords
        "break", "case", "catch", "continue", "default", "delete", "do",
        "else", "finally", "for", "function", "if", "in", "instanceof", "new",
        "return", "switch", "this", "throw", "try", "typeof", "var", "void",
        "while", "with",
        // Future Keywords
        "class", "const", "debugger", "enum", "export", "extends",
        "implements", "import", "interface", "super",
        // LZS
        "mixin",
        // Literal
        "true", "false", "null" };

    private static final Set<String> KEYWORDS;
    static {
        Set<String> set = new HashSet<String>();
        Collections.addAll(set, _KEYWORDS);
        KEYWORDS = Collections.unmodifiableSet(set);
    }

    /** Returns true iff the string is a valid JavaScript identifier. */
    public static boolean isIdentifier(String s) {
        int length = s.length();
        if (length == 0)
            return false;
        if (!Character.isJavaIdentifierStart(s.charAt(0)))
            return false;
        for (int i = 1; i < length; i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i)))
                return false;
        }
        return !(KEYWORDS.contains(s));
    }

    /** Returns true iff the string is a JavaScript keyword. */
    public static boolean isKeyword(String s) {
        return KEYWORDS.contains(s);
    }

    /** Enclose the specified string in double-quotes, and character-quote
     * any characters that need it.
     * @param s a string
     * @return a quoted string
     */
    public static String quote(String s) {
        char quote = '\"';
        // Minimize escaping of quotes
        if (s.indexOf('\'') >= 0 || s.indexOf('\"') >= 0) {
            int n = 0;
            for (int i = 0, len = s.length(); i < len; ++i) {
                char c = s.charAt(i);
                switch (c) {
                case '\'': n--; break;
                case '\"': n++; break;
                }
            }
            quote = n > 0 ? '\'' : '\"';
        }

        return quoteInternal(s, quote, false);
    }

    /** Escape the specified string for JSON, enclosed in double-quotes, and character-quote
     * any characters that need it.
     * @param s a string
     * @return a quoted string
     */
    public static String JSONquote(String s) {
        return quoteInternal(s, '"', true);
    }

    private static final char HEX_CHARS[] = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    private static final char hexchar(int c) {
        return HEX_CHARS[c & 0x0F];
    }

    /**
     * 
     * @param s
     * @param quote
     * @param escapeSlash
     * @return
     * @see #quote(String)
     * @see #JSONquote(String)
     */
    private static String quoteInternal(String s, char quote, boolean escapeSlash) {
        final char CHAR_ESCAPE = '\\';

        int length = s.length();
        StringBuilder sb = new StringBuilder(length);
        sb.append(quote);
        for (int i = 0; i < length; ++i) {
            char c = s.charAt(i);
            switch (c) {
            case '\n':
                sb.append("\\n");
                break;
            case '\r':
                sb.append("\\r");
                break;
            case '\b':
                sb.append("\\b");
                break;
            case '\t':
                sb.append("\\t");
                break;
            case '\u000B':
                sb.append("\\v");
                break;
            case '\f':
                sb.append("\\f");
                break;
            case '\\':
                sb.append(CHAR_ESCAPE)
                  .append(c);
                break;
            case '\'':
            case '\"':
                if (c == quote) {
                    sb.append(CHAR_ESCAPE);
                }
                sb.append(c);
                break;
            case '/':
                if (escapeSlash) {
                    // only required for JSON
                    sb.append(CHAR_ESCAPE);
                }
                sb.append(c);
                break;
            default:
                if (c == 0) {
                    // ECMAScript NUL is a special case
                    sb.append(CHAR_ESCAPE)
                      .append('0');
                } else if (c < 32 || (c >= 128 && c <= 0xff)) {
                    // ECMAScript string literal hex unicode escape sequence
                    sb.append(CHAR_ESCAPE)
                      .append('x')
                    // Format as \ xXX two-digit zero padded hex string
                      .append(hexchar((c >> 4) & 0x0F))
                      .append(hexchar(c & 0x0F));
                } else if (c > 0xff) {
                    // ECMAScript string literal hex unicode escape sequence
                    sb.append(CHAR_ESCAPE)
                      .append('u')
                    // Format as \ uXXXX four-digit zero padded hex string
                      .append(hexchar((c >> 12) & 0x0F))
                      .append(hexchar((c >> 8) & 0x0F))
                      .append(hexchar((c >> 4) & 0x0F))
                      .append(hexchar(c & 0x0F));
                } else {
                    sb.append(c);
                }
            }
        }
        sb.append(quote);

        return sb.toString();
    }

}
