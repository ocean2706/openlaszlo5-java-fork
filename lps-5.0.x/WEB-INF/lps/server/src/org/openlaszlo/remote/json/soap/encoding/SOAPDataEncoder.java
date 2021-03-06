/* ****************************************************************************
 * SOAPDataEncoder.java
 *
 * Compile XML to JSON which will tell the LFC to build a LzDataElement DOM strucure 
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.remote.json.soap.encoding;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Vector;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;

import org.apache.axis.AxisFault;
import org.apache.axis.message.SOAPHeader;
import org.apache.log4j.Logger;
import org.openlaszlo.remote.json.soap.ObjectWrapper;
import org.openlaszlo.sc.ScriptCompiler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;

/**
 */
public class SOAPDataEncoder implements ContentHandler {

    /* Logger */
    private static Logger mLogger  = Logger.getLogger(SOAPDataEncoder.class);

    private StringBuffer body = new StringBuffer();
    private StringBuffer headers = new StringBuffer();

    /**
     * Constructs an empty SOAPDataEncoder.
     */
    public SOAPDataEncoder () {
        body = new StringBuffer();
        headers = new StringBuffer();
    }

    /**
     * Workaround variable for bug 4680.
     */
    boolean isProgram = false;

    public SOAPDataEncoder(Vector<SOAPElement> v, SOAPHeader h) {
        body = new StringBuffer();
        headers = new StringBuffer();

        buildFromElements(v, h);
    }

    public SOAPDataEncoder( Object p, SOAPHeader h) {
        body = new StringBuffer();
        headers = new StringBuffer();
        buildFromProgram(p, h);
    }

    public SOAPDataEncoder buildFromFault(AxisFault fault) {
        start();
        String actor = fault.getFaultActor(); // can be null
        QName  code = fault.getFaultCode(); // can be null
        String node = fault.getFaultNode(); // SOAP1.2
        String reason = fault.getFaultReason(); // SOAP1.2==SOAP1.1 faultstring
        String role = fault.getFaultRole(); // SOAP1.2==SOAP1.1 actor
        String faultstring = fault.getFaultString(); // never null
        QName[]  subcodes = fault.getFaultSubCodes(); // can be null
//         Element[] details  = fault.getFaultDetails(); // can be null
//         ArrayList headers = fault.getHeaders();

        body.append("{");

        // +++ TODO [hqm 2007-03-08] should there be a key string like "subcodes" pushed here??

        int items = 0;
        if (subcodes != null) {
            body.append ("subcodes: ");
            body.append("[");
            for (int i=0; i < subcodes.length; i++) {
                if (i > 0) { body.append(","); }
                QName qname = subcodes[i];
                if (qname == null) {
                    body.append("null");
                } else {
                    body.append("new QName("+
                                ScriptCompiler.JSONquote(qname.getLocalPart()) +
                                ", "+
                                ScriptCompiler.JSONquote(qname.getNamespaceURI())
                                +")");
                }
            } 
            body.append("]");
            items++;
        }

        if (actor != null) {
            body.append(items > 0 ? "," : "");
            body.append("actor: "+ ScriptCompiler.JSONquote(actor));
            items++;
        }
        if (node != null) {
            body.append(items > 0 ? "," : "");
            body.append("node: "+ScriptCompiler.JSONquote(node));
            items++;
        }

        if (reason != null) {
            body.append(items > 0 ? "," : "");
            body.append("reason: "+ScriptCompiler.JSONquote(reason));
            items++;
        }

        if (role != null) {
            body.append(items > 0 ? "," : "");
            body.append("role: "+ScriptCompiler.JSONquote(role));
            items++;
        }

        if (faultstring != null) {
            body.append(items > 0 ? "," : "");
            body.append("faultstring: "+ScriptCompiler.JSONquote(faultstring));
            items++;
        }

        if (code != null) {
            body.append(items > 0 ? "," : "");
            body.append("code: "+
                        "new QName("+
                                ScriptCompiler.JSONquote(code.getLocalPart()) +
                                ", "+
                                ScriptCompiler.JSONquote(code.getNamespaceURI())
                                +")");
            items++;
        }


        body.append(items > 0 ? "," : "");
        body.append("errortype: "+ScriptCompiler.JSONquote("fault"));
        body.append("}");
        end();

        return this;
    }

    public SOAPDataEncoder buildFromException(Exception e) {
        start();
        {
            Throwable cause = e.getCause();
            String message = e.getMessage();
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));

            body.append ("{");
            body.append("stacktrace: "+ ScriptCompiler.JSONquote(sw.toString()));

            body.append(",faultString: ");
            if (message != null) {
                body.append(ScriptCompiler.JSONquote(message));
            } else { 
                ScriptCompiler.JSONquote(sw.toString());
            }

            if (cause != null) {
                body.append(",cause: ");
                body.append(ScriptCompiler.JSONquote(cause.toString()));
            }

            body.append(", errortype: ");
            body.append(ScriptCompiler.JSONquote("exception"));

            body.append("}");
        }
        end();
        return this;
    }

    public void startElement(String localName, Attributes atts) { }
    public void startElement(String uri, String localName, String qName, Attributes atts) {
    }

    public void start() {
        body = new StringBuffer();
        headers = new StringBuffer();
    }

    public void end() {
    }
    public void endElement(String uri, String localName, String qName) {
    }

    public void endElement() {
    }

    private void buildHeaders(SOAPHeader h) throws IOException {
        int hCount = 0;
        @SuppressWarnings("unchecked")
        Iterator<SOAPElement> iter = h.getChildElements();
        StringBuilder buf = new StringBuilder();
        buf.append("[");

        while (iter.hasNext()) {
            SOAPElement elt = iter.next();
            String str = serializeSOAPElt(elt);
            buf.append(ScriptCompiler.JSONquote(str));
            if (hCount++ > 0) {
                buf.append(",");
            }
        }
        buf.append("]");

        if (hCount != 0) {
            headers.append(buf.toString());
        } else {
            headers.append("null");
        }
    }



    private String serializeSOAPElt(SOAPElement elem) throws IOException {
        return elem.toString();
        /*
        StringWriter outbuf = new StringWriter();
        XMLSerializer xser = new XMLSerializer(outbuf, new OutputFormat());
        xser.serialize(elem);
        String str = outbuf.toString();
        return str;
        */
    }

    /**
     * Build from a vector of SOAPElement items.
     *
     * @param v vector of SOAPElement items.
     */
    public void buildFromElements(Vector<SOAPElement> v, SOAPHeader h) {
        try {
        startDocument();

        // soap headers
        buildHeaders(h);


        //LzDataNode.stringToLzData
        // array of documents

        if (v.size() != 1) {
            throw new RuntimeException("SOAPDataEncoder.buildFromElements expected just one value, got "+v.size()+" "+v);
        }

        SOAPElement elem = v.get(0);
        String str = serializeSOAPElt(elem);
        body.append(str);

        endDocument();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }


    /**
     * Build from a vector of SOAPElement items.
     *
     * @param p an Object value, already coerced to a string
     */
    public void buildFromProgram(Object p, SOAPHeader h) {
        if (p == null) {
            mLogger.debug("json.SOAPDataEncoder buildFromProgram p=null");
            body = new StringBuffer("null");
            return;
        }
        if (p instanceof String) {
            mLogger.debug("json.SOAPDataEncoder buildFromProgram p=String "+p);
            body = new StringBuffer(p.toString());
        } else {
            mLogger.debug("json.SOAPDataEncoder buildFromProgram p.getClass()="+p.getClass());
            Object[] p0 = (Object[]) p;
            body = new StringBuffer(encodeObject(p0[0]));
        }
    }

    String encodeArray(Object obj) {
        Object[] p = (Object[]) obj;
        StringBuilder buf = new StringBuilder();
        buf.append("[");
        for (int i = 0; i < p.length; i++) {
            if (i > 0) buf.append(",");
            buf.append(encodeObject(p[i]));
        }
        buf.append("]");
        return buf.toString();
    }


    String encodeObject(Object p) {
        try {
            if (p instanceof ObjectWrapper) {
            org.w3c.dom.Element e = (((ObjectWrapper) p).getElement());
            return serializeSOAPElt((SOAPElement) e );
        } else if (p.getClass().isArray()) {
            return encodeArray((Object[])p);
        } else {
            return p.toString();
        }
        } catch (java.io.IOException e) {
            return e.getMessage();
        }
    }

    /**
     * End the scope of a prefix-URI mapping. This method is unimplemented.
     *
     * @param prefix the prefix that was being mapped.
     */
    public void endPrefixMapping(String prefix) {
    }


    public void characters(char[] ch, int start, int length) {
    }

    public void characters(String text) { }


    /**
     * Receive notification of ignorable whitespace in element content. This
     * method is unimplemented.
     *
     * @param ch the characters from the XML document.
     * @param start the start position in the array.
     * @param length the number of characters to read from the array.
     */
    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    /**
     * Receive notification of a processing instruction. This method is
     * unimplemented.
     *
     * @param target the processing instruction target.
     * @param data the processing instruction data, or null if none was
     * supplied. The data does not include any whitespace separating it from the
     * target.
     */
    public void processingInstruction(String target, String data) {
    }

    /**
     * Receive an object for locating the origin of SAX document events. This
     * method is unimplemented.
     *
     * @param locator an object that can return the location of any SAX document
     * event.
     */
    public void setDocumentLocator(Locator locator) {
    }

    /**
     * Receive notification of a skipped entity. This method is unimplemented.
     *
     * @param name the name of the skipped entity. If it is a parameter entity,
     * the name will begin with '%', and if it is the external DTD subset, it
     * will be the string "[dtd]".
     */
    public void skippedEntity(String name) {
    }
    public void startPrefixMapping(String prefix, String uri) {}

    public void startDocument() {}

    public void endDocument() {
        end();
    }

    public InputStream getInputStream() throws IOException {
        //        String bodyplusheaders = "["+headers.toString()+","+body.toString()+"]";

        // TODO [hqm 4-9-2007] Need to figure out how to pass headers; the xmlrpc and javarpc
        // code needs to be modified to pass headers also, then it can go as a list of
        // [data, headers], and the client in rpc/library.dhtml/rpc.js will separate it out.
        String bodystr = body.toString();
        return new ByteArrayInputStream(bodystr.getBytes("UTF-8"));
    }

    /**
     */
    public long getSize() {
        //String bodyplusheaders = "["+headers.toString()+","+body.toString()+"]";
        String bodystr = body.toString();
        return bodystr.length();
    }

}
