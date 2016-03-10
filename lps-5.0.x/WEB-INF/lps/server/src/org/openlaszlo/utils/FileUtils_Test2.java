/* ****************************************************************************
 * FileUtils_Test2.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2011 Laszlo Systems, Inc.  All Rights Reserved.                   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
package org.openlaszlo.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.openlaszlo.utils.FileUtils.Encoding;

import static org.junit.Assert.*;
import static org.hamcrest.CoreMatchers.*;

import static org.openlaszlo.utils.FileUtils.getBOMEncoding;
import static org.openlaszlo.utils.FileUtils.getXMLStreamReader;
import static org.openlaszlo.utils.FileUtils.stripByteOrderMark;

/**
 * @author André Bargull
 * 
 */
public class FileUtils_Test2 {

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        // LPS_HOME must be defined for the test case to run
        // String LPS_HOME = "";
        // System.setProperty("LPS_HOME", LPS_HOME);
    }

    @Test
    public void testEncoding() {
        assertEquals(0, Encoding.UNKNOWN.getBOMLength());
        assertEquals(null, Encoding.UNKNOWN.getCharset());
        assertEquals(null, Encoding.UNKNOWN.getXMLEncoding());

        assertEquals(2, Encoding.UTF16_LE.getBOMLength());
        assertEquals("UTF-16LE", Encoding.UTF16_LE.getCharset());
        assertEquals("UTF-16", Encoding.UTF16_LE.getXMLEncoding());

        assertEquals(2, Encoding.UTF16_BE.getBOMLength());
        assertEquals("UTF-16BE", Encoding.UTF16_BE.getCharset());
        assertEquals("UTF-16", Encoding.UTF16_BE.getXMLEncoding());

        assertEquals(3, Encoding.UTF8.getBOMLength());
        assertEquals("UTF-8", Encoding.UTF8.getCharset());
        assertEquals("UTF-8", Encoding.UTF8.getXMLEncoding());
    }

    @Test
    public void testGetXMLStreamReader() throws IOException {
        final String UTF8 = "UTF-8";
        final String UTF16LE = "UTF-16LE";
        final String UTF16BE = "UTF-16BE";
        final String ISO8859_1 = "ISO-8859-1";

        final String document = "<document/>";
        final String documentUTF8 = "<?xml encoding=\"UTF-8\"?>" + document;
        final String documentUTF16 = "<?xml encoding=\"UTF-16\"?>" + document;
        final String documentUTF16LE = "<?xml encoding=\"UTF-16LE\"?>"
            + document;
        final String documentUTF16BE = "<?xml encoding=\"UTF-16BE\"?>"
            + document;
        final String documentISO8859_1 = "<?xml encoding=\"ISO-8859-1\"?>"
            + document;

        // writing and reading the same encoding should work
        assertEquals("", string(getXMLStreamReader(stream("", UTF8), UTF8)));
        assertEquals("hello",
            string(getXMLStreamReader(stream("hello", UTF8), UTF8)));

        assertThat(string(getXMLStreamReader(stream(document, UTF8), UTF8)),
            is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16LE), UTF16LE)),
            is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16BE), UTF16BE)),
            is(document));

        // writing and reading with different encoding should fail
        assertThat(string(getXMLStreamReader(stream(document, UTF16LE), UTF8)),
            not(is(document)));
        assertThat(string(getXMLStreamReader(stream(document, UTF16BE), UTF8)),
            not(is(document)));
        assertThat(string(getXMLStreamReader(stream(document, UTF8), UTF16LE)),
            not(is(document)));
        assertThat(string(getXMLStreamReader(stream(document, UTF8), UTF16BE)),
            not(is(document)));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16BE), UTF16LE)),
            not(is(document)));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16LE), UTF16BE)),
            not(is(document)));

        // give a hint for the correct encoding (BOM)
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16LE, 0xFF, 0xFE),
                UTF8)), is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16BE, 0xFE, 0xFF),
                UTF8)), is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF8, 0xEF, 0xBB, 0xBF),
                UTF16LE)), is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF8, 0xEF, 0xBB, 0xBF),
                UTF16BE)), is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16BE, 0xFE, 0xFF),
                UTF16LE)), is(document));
        assertThat(
            string(getXMLStreamReader(stream(document, UTF16LE, 0xFF, 0xFE),
                UTF16BE)), is(document));

        // give a hint for the correct encoding (prolog)
        assertThat(
            string(getXMLStreamReader(stream(documentUTF16LE, UTF16LE), UTF8)),
            is(documentUTF16LE));
        assertThat(
            string(getXMLStreamReader(stream(documentUTF16BE, UTF16BE), UTF8)),
            is(documentUTF16BE));
        assertThat(
            string(getXMLStreamReader(stream(documentUTF8, UTF8), UTF8)),
            is(documentUTF8));
        assertThat(
            string(getXMLStreamReader(stream(documentISO8859_1, ISO8859_1),
                UTF8)), is(documentISO8859_1));

        // BOM and encoding from xml-prolog must match
        try {
            getXMLStreamReader(stream(documentUTF8, UTF16LE, 0xFF, 0xFE), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(stream(documentUTF8, UTF16BE, 0xFE, 0xFF), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(stream(documentUTF16, UTF8, 0xEF, 0xBB, 0xBF),
                UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(stream(documentUTF16LE, UTF8, 0xEF, 0xBB, 0xBF),
                UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(stream(documentUTF16BE, UTF8, 0xEF, 0xBB, 0xBF),
                UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(stream(documentUTF16BE, UTF16LE, 0xFF, 0xFE),
                UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(stream(documentUTF16LE, UTF16BE, 0xFE, 0xFF),
                UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
    }

    /**
     * Same tests as above, this time without mark() support
     * 
     * @throws IOException
     */
    @Test
    public void testGetXMLStreamReader2() throws IOException {
        final String UTF8 = "UTF-8";
        final String UTF16LE = "UTF-16LE";
        final String UTF16BE = "UTF-16BE";
        final String ISO8859_1 = "ISO-8859-1";

        final String document = "<document/>";
        final String documentUTF8 = "<?xml encoding=\"UTF-8\"?>" + document;
        final String documentUTF16 = "<?xml encoding=\"UTF-16\"?>" + document;
        final String documentUTF16LE = "<?xml encoding=\"UTF-16LE\"?>"
            + document;
        final String documentUTF16BE = "<?xml encoding=\"UTF-16BE\"?>"
            + document;
        final String documentISO8859_1 = "<?xml encoding=\"ISO-8859-1\"?>"
            + document;

        // writing and reading the same encoding should work
        assertEquals("",
            string(getXMLStreamReader(filter(stream("", UTF8)), UTF8)));
        assertEquals("hello",
            string(getXMLStreamReader(filter(stream("hello", UTF8)), UTF8)));

        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF8)), UTF8)),
            is(document));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF16LE)),
                UTF16LE)), is(document));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF16BE)),
                UTF16BE)), is(document));

        // writing and reading with different encoding should fail
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF16LE)), UTF8)),
            not(is(document)));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF16BE)), UTF8)),
            not(is(document)));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF8)), UTF16LE)),
            not(is(document)));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF8)), UTF16BE)),
            not(is(document)));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF16BE)),
                UTF16LE)), not(is(document)));
        assertThat(
            string(getXMLStreamReader(filter(stream(document, UTF16LE)),
                UTF16BE)), not(is(document)));

        // give a hint for the correct encoding (BOM)
        assertThat(
            string(getXMLStreamReader(
                filter(stream(document, UTF16LE, 0xFF, 0xFE)), UTF8)),
            is(document));
        assertThat(
            string(getXMLStreamReader(
                filter(stream(document, UTF16BE, 0xFE, 0xFF)), UTF8)),
            is(document));
        assertThat(
            string(getXMLStreamReader(
                filter(stream(document, UTF8, 0xEF, 0xBB, 0xBF)), UTF16LE)),
            is(document));
        assertThat(
            string(getXMLStreamReader(
                filter(stream(document, UTF8, 0xEF, 0xBB, 0xBF)), UTF16BE)),
            is(document));
        assertThat(
            string(getXMLStreamReader(
                filter(stream(document, UTF16BE, 0xFE, 0xFF)), UTF16LE)),
            is(document));
        assertThat(
            string(getXMLStreamReader(
                filter(stream(document, UTF16LE, 0xFF, 0xFE)), UTF16BE)),
            is(document));

        // give a hint for the correct encoding (prolog)
        assertThat(
            string(getXMLStreamReader(filter(stream(documentUTF16LE, UTF16LE)),
                UTF8)), is(documentUTF16LE));
        assertThat(
            string(getXMLStreamReader(filter(stream(documentUTF16BE, UTF16BE)),
                UTF8)), is(documentUTF16BE));
        assertThat(
            string(getXMLStreamReader(filter(stream(documentUTF8, UTF8)), UTF8)),
            is(documentUTF8));
        assertThat(
            string(getXMLStreamReader(
                filter(stream(documentISO8859_1, ISO8859_1)), UTF8)),
            is(documentISO8859_1));

        // BOM and encoding from xml-prolog must match
        try {
            getXMLStreamReader(
                filter(stream(documentUTF8, UTF16LE, 0xFF, 0xFE)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(
                filter(stream(documentUTF8, UTF16BE, 0xFE, 0xFF)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(
                filter(stream(documentUTF16, UTF8, 0xEF, 0xBB, 0xBF)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(
                filter(stream(documentUTF16LE, UTF8, 0xEF, 0xBB, 0xBF)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(
                filter(stream(documentUTF16BE, UTF8, 0xEF, 0xBB, 0xBF)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(
                filter(stream(documentUTF16BE, UTF16LE, 0xFF, 0xFE)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
        try {
            getXMLStreamReader(
                filter(stream(documentUTF16LE, UTF16BE, 0xFE, 0xFF)), UTF8);
            fail("expected exception");
        } catch (ChainedException e) {
        }
    }

    /**
     * Test: stream is reset
     * 
     * @throws IOException
     */
    @Test
    public void testGetXMLStreamReader3() throws IOException {
        final String UTF8 = "UTF-8";
        final String UTF16LE = "UTF-16LE";
        final String UTF16BE = "UTF-16BE";
        final String document = "<document/>";

        InputStream in;
        int a;

        // stream() returns ByteArrayInputStream which supports mark()
        in = stream(document, UTF8);
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(a, in.available());

        in = stream(document, UTF8, 0xEF, 0xBB, 0xBF);
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(a - 3, in.available());

        in = stream(document, UTF16LE, 0xFF, 0xFE);
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(a - 2, in.available());

        in = stream(document, UTF16BE, 0xFE, 0xFF);
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(a - 2, in.available());

        // filter() returns FilterInputStream without mark() support
        in = filter(stream(document, UTF8));
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(0, in.available());

        in = filter(stream(document, UTF8, 0xEF, 0xBB, 0xBF));
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(0, in.available());

        in = filter(stream(document, UTF16LE, 0xFF, 0xFE));
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(0, in.available());

        in = filter(stream(document, UTF16BE, 0xFE, 0xFF));
        a = in.available();
        getXMLStreamReader(in, UTF8);
        assertEquals(0, in.available());
    }

    /**
     * Test: document longer than {@value FileUtils#MAX_SIZE_PROLOG}
     * 
     * @throws IOException
     */
    @Test
    public void testGetXMLStreamReader_Big() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<document>");
        for (int i = 0; i < 10; ++i) {
            sb.append("<element").append(i).append(">");
            for (int j = 0; j < 5; ++j) {
                sb.append("<node").append(j).append("/>");
            }
            sb.append("/<element").append(i).append(">");
        }
        sb.append("</document>");

        final String UTF8 = "UTF-8";
        final String UTF16LE = "UTF-16LE";
        final String UTF16BE = "UTF-16BE";
        final String document = sb.toString();

        InputStream in;
        int a;
        Reader r;

        // with mark()
        in = stream(document, UTF8);
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        in = stream(document, UTF8, 0xEF, 0xBB, 0xBF);
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 3, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        in = stream(document, UTF16LE, 0xFF, 0xFE);
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 2, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        in = stream(document, UTF16BE, 0xFE, 0xFF);
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 2, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        // without mark()
        in = filter(stream(document, UTF8));
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 256, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        in = filter(stream(document, UTF8, 0xEF, 0xBB, 0xBF));
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 256, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        in = filter(stream(document, UTF16LE, 0xFF, 0xFE));
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 256, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());

        in = filter(stream(document, UTF16BE, 0xFE, 0xFF));
        a = in.available();
        r = getXMLStreamReader(in, UTF8);
        assertEquals(a - 256, in.available());
        assertThat(string(r), is(document));
        assertEquals(0, in.available());
    }

    /**
     * Test: BOM encoding detected
     * 
     * @throws IOException
     */
    @Test
    public void testGetBOMEncoding() throws IOException {
        try {
            assertEquals(Encoding.UNKNOWN, getBOMEncoding(buffered()));
            fail("expected IOException");
        } catch (IOException e) {
        }
        assertEquals(Encoding.UNKNOWN, getBOMEncoding(buffered(0)));
        assertEquals(Encoding.UNKNOWN, getBOMEncoding(buffered(0, 0)));
        assertEquals(Encoding.UNKNOWN, getBOMEncoding(buffered(0, 0, 0)));

        // invalid BOM
        assertEquals(Encoding.UNKNOWN, getBOMEncoding(buffered(0xFF, 0xFF, 0)));
        assertEquals(Encoding.UNKNOWN, getBOMEncoding(buffered(0xFE, 0xFE, 0)));
        assertEquals(Encoding.UNKNOWN,
            getBOMEncoding(buffered(0x0F, 0xBB, 0xBF)));
        assertEquals(Encoding.UNKNOWN,
            getBOMEncoding(buffered(0xEF, 0xB0, 0xBF)));
        assertEquals(Encoding.UNKNOWN,
            getBOMEncoding(buffered(0xEF, 0xBB, 0xB0)));

        // UTF-16LE
        assertEquals(Encoding.UTF16_LE, getBOMEncoding(buffered(0xFF, 0xFE, 0)));
        assertEquals(Encoding.UTF16_LE, getBOMEncoding(buffered(0xFF, 0xFE, 1)));
        assertEquals(Encoding.UTF16_LE,
            getBOMEncoding(buffered(0xFF, 0xFE, 0, 0)));

        // UTF-16BE
        assertEquals(Encoding.UTF16_BE, getBOMEncoding(buffered(0xFE, 0xFF, 0)));
        assertEquals(Encoding.UTF16_BE, getBOMEncoding(buffered(0xFE, 0xFF, 1)));
        assertEquals(Encoding.UTF16_BE,
            getBOMEncoding(buffered(0xFE, 0xFF, 0, 0)));

        // UTF-8
        assertEquals(Encoding.UTF8, getBOMEncoding(buffered(0xEF, 0xBB, 0xBF)));
        assertEquals(Encoding.UTF8,
            getBOMEncoding(buffered(0xEF, 0xBB, 0xBF, 0)));
        assertEquals(Encoding.UTF8,
            getBOMEncoding(buffered(0xEF, 0xBB, 0xBF, 1)));
    }

    /**
     * Test: stream is reset
     * 
     * @throws IOException
     */
    @Test
    public void testGetBOMEncoding2() throws IOException {
        BufferedInputStream in;

        in = buffered(0);
        getBOMEncoding(in);
        assertEquals(1, in.available());

        in = buffered(0, 0);
        getBOMEncoding(in);
        assertEquals(2, in.available());

        in = buffered(0, 0, 0);
        getBOMEncoding(in);
        assertEquals(3, in.available());

        // UTF-16LE
        in = buffered(0xFF, 0xFE, 0);
        getBOMEncoding(in);
        assertEquals(3, in.available());

        // UTF-16BE
        in = buffered(0xFE, 0xFF, 0);
        getBOMEncoding(in);
        assertEquals(3, in.available());

        // UTF-8
        in = buffered(0xEF, 0xBB, 0xBF);
        getBOMEncoding(in);
        assertEquals(3, in.available());
    }

    /**
     * Test: BOM encoding detected
     * 
     * @throws IOException
     */
    @Test
    public void testStripByteOrderMark2() throws IOException {
        assertEquals(null, stripByteOrderMark(stream()));
        assertEquals(null, stripByteOrderMark(stream(0)));
        assertEquals(null, stripByteOrderMark(stream(0, 0)));
        assertEquals(null, stripByteOrderMark(stream(0, 0, 0)));

        // invalid BOM
        assertEquals(null, stripByteOrderMark(stream(0xFE)));
        assertEquals(null, stripByteOrderMark(stream(0xFF)));
        assertEquals(null, stripByteOrderMark(stream(0xFF, 0xFF, 0)));
        assertEquals(null, stripByteOrderMark(stream(0xFE, 0xFE, 0)));
        assertEquals(null, stripByteOrderMark(stream(0x0F, 0xBB, 0xBF)));
        assertEquals(null, stripByteOrderMark(stream(0xEF, 0xB0, 0xBF)));
        assertEquals(null, stripByteOrderMark(stream(0xEF, 0xBB, 0xB0)));

        // UTF-16LE
        assertEquals("UTF-16LE", stripByteOrderMark(stream(0xFF, 0xFE, 0)));
        assertEquals("UTF-16LE", stripByteOrderMark(stream(0xFF, 0xFE, 1)));
        assertEquals("UTF-16LE", stripByteOrderMark(stream(0xFF, 0xFE, 0, 0)));

        // UTF-16BE
        assertEquals("UTF-16BE", stripByteOrderMark(stream(0xFE, 0xFF, 0)));
        assertEquals("UTF-16BE", stripByteOrderMark(stream(0xFE, 0xFF, 1)));
        assertEquals("UTF-16BE", stripByteOrderMark(stream(0xFE, 0xFF, 0, 0)));

        // UTF-8
        assertEquals("UTF-8", stripByteOrderMark(stream(0xEF, 0xBB, 0xBF)));
        assertEquals("UTF-8", stripByteOrderMark(stream(0xEF, 0xBB, 0xBF, 0)));
        assertEquals("UTF-8", stripByteOrderMark(stream(0xEF, 0xBB, 0xBF, 1)));
    }

    /**
     * Test: non-BOM bytes are unread
     * 
     * @throws IOException
     */
    @Test
    public void testStripByteOrderMark3() throws IOException {
        PushbackInputStream in;

        in = stream();
        stripByteOrderMark(in);
        assertEquals(0, in.available());

        in = stream(0);
        stripByteOrderMark(in);
        assertEquals(1, in.available());

        in = stream(0, 0);
        stripByteOrderMark(in);
        assertEquals(2, in.available());

        in = stream(0, 0, 0);
        stripByteOrderMark(in);
        assertEquals(3, in.available());

        in = stream(0xFE);
        stripByteOrderMark(in);
        assertEquals(1, in.available());

        in = stream(0xFF);
        stripByteOrderMark(in);
        assertEquals(1, in.available());

        in = stream(0xFF, 0xFF, 0);
        stripByteOrderMark(in);
        assertEquals(3, in.available());

        in = stream(0xFE, 0xFE, 0);
        stripByteOrderMark(in);
        assertEquals(3, in.available());

        // UTF-16LE
        in = stream(0xFF, 0xFE, 0);
        stripByteOrderMark(in);
        assertEquals(1, in.available());

        // UTF-16BE
        in = stream(0xFE, 0xFF, 0);
        stripByteOrderMark(in);
        assertEquals(1, in.available());

        // UTF-8
        in = stream(0xEF, 0xBB, 0xBF);
        stripByteOrderMark(in);
        assertEquals(0, in.available());

        in = stream(0xEF, 0xBB, 0xBF, 1);
        stripByteOrderMark(in);
        assertEquals(1, in.available());
    }

    /**
     * Test: BOM length detected
     * 
     * @throws IOException
     */
    @Test
    public void testStripByteOrderMark() {
        assertEquals(0, stripByteOrderMark(array()));
        assertEquals(0, stripByteOrderMark(array(0)));
        assertEquals(0, stripByteOrderMark(array(0, 0)));
        assertEquals(0, stripByteOrderMark(array(0, 0, 0)));

        // invalid BOM
        assertEquals(0, stripByteOrderMark(array(0xFE)));
        assertEquals(0, stripByteOrderMark(array(0xFF)));
        assertEquals(0, stripByteOrderMark(array(0xFF, 0xFF, 0)));
        assertEquals(0, stripByteOrderMark(array(0xFE, 0xFE, 0)));
        assertEquals(0, stripByteOrderMark(array(0x0F, 0xBB, 0xBF)));
        assertEquals(0, stripByteOrderMark(array(0xEF, 0xB0, 0xBF)));
        assertEquals(0, stripByteOrderMark(array(0xEF, 0xBB, 0xB0)));

        // UTF-16LE
        assertEquals(2, stripByteOrderMark(array(0xFF, 0xFE, 0)));
        assertEquals(2, stripByteOrderMark(array(0xFF, 0xFE, 1)));
        assertEquals(2, stripByteOrderMark(array(0xFF, 0xFE, 0, 0)));

        // UTF-16BE
        assertEquals(2, stripByteOrderMark(array(0xFE, 0xFF, 0)));
        assertEquals(2, stripByteOrderMark(array(0xFE, 0xFF, 1)));
        assertEquals(2, stripByteOrderMark(array(0xFE, 0xFF, 0, 0)));

        // UTF-8
        assertEquals(3, stripByteOrderMark(array(0xEF, 0xBB, 0xBF)));
        assertEquals(3, stripByteOrderMark(array(0xEF, 0xBB, 0xBF, 0)));
        assertEquals(3, stripByteOrderMark(array(0xEF, 0xBB, 0xBF, 1)));
    }

    private static final String string(Reader r) throws IOException {
        StringWriter s = new StringWriter();
        FileUtils.send(r, s);
        FileUtils.close(r);
        return s.toString();
    }

    private static final InputStream stream(String s, String cs, int... prefix)
        throws UnsupportedEncodingException {
        byte bp[] = array(prefix);
        byte bs[] = s.getBytes(cs);
        byte bb[] = new byte[bp.length + bs.length];
        System.arraycopy(bp, 0, bb, 0, bp.length);
        System.arraycopy(bs, 0, bb, bp.length, bs.length);
        ByteArrayInputStream in = new ByteArrayInputStream(bb);
        return in;
    }

    private static final InputStream filter(InputStream is) {
        return new FilterInputStream(is) {
            @Override
            public boolean markSupported() {
                return false;
            }
        };
    }

    private static final BufferedInputStream buffered(int... rest) {
        BufferedInputStream in = new BufferedInputStream(
            new ByteArrayInputStream(array(rest)));
        return in;
    }

    private static final PushbackInputStream stream(int... rest) {
        // need to unread 3 bytes
        PushbackInputStream in = new PushbackInputStream(
            new ByteArrayInputStream(array(rest)), 3);
        return in;
    }

    private static final byte[] array(int... rest) {
        int len = rest.length;
        byte bs[] = new byte[len];
        for (int i = 0; i < len; ++i) {
            bs[i] = (byte) rest[i];
        }
        return bs;
    }
}
