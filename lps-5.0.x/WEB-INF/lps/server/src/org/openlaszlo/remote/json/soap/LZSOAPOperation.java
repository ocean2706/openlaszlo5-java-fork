/* *****************************************************************************
 * LZSOAPOperation.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.remote.json.soap;

public class LZSOAPOperation
{
    /**
     * Name of operation.
     */
    String mName;

    /**
     * Mangled name of operation.
     */
    String mMangledName;

    /**
     * From WSDL spec: For the HTTP protocol binding of SOAP, [soapAction] is
     * value required (it has no default value).
     */
    String mSoapAction = null;

    /**
     * For document styles. See
     * http://www-106.ibm.com/developerworks/webservices/library/ws-whichwsdl:
     *
     * Characteristics of document/literal wrapped pattern:
     *
     *    - The input message has a single part
     *    - The part is an element
     *    - The element has the same name as the operation
     *    - The element's complex type has no attributes.
     *
     */
    boolean mIsDocumentLiteralWrapped = false;

    /**
     * One of document or rpc.
     */
    String mStyle = "document";

    // Let's not worry about header parts just yet
//     LZSOAPMessage mInputHeader = new LZSOAPMessage(); 
//     LZSOAPMessage mOutputHeader = new LZSOAPMessage(); 

    LZSOAPMessage mInputMessage = null;
    LZSOAPMessage mOutputMessage = null;


    public LZSOAPOperation(String name) {
        mName = name;
        mMangledName = name;
    }

    public String getName() {
        return mName;
    }

    public String getMangledName() {
        return mMangledName;
    }

    public void setMangledName(String mangledName) {
        mMangledName = mangledName;
    }

    public void setStyle(String style) {
        mStyle = style;
    }

    public String getStyle() {
        return mStyle;
    }

    public void setSoapAction(String soapAction) {
        mSoapAction = soapAction;
    }

    public String getSoapAction() {
        return mSoapAction;
    }

    public void setIsDocumentLiteralWrapped(boolean isDocumentLiteralWrapped) {
        mIsDocumentLiteralWrapped = isDocumentLiteralWrapped;
    }

    public boolean isDocumentLiteralWrapped() {
        return mIsDocumentLiteralWrapped;
    }

    public LZSOAPMessage getInputMessage() {
        return mInputMessage;
    }

    public LZSOAPMessage getOutputMessage() {
        return mOutputMessage;
    }

    public void setInputMessage(LZSOAPMessage inputMessage) {
        mInputMessage = inputMessage;
    }

    public void setOutputMessage(LZSOAPMessage outputMessage) {
        mOutputMessage = outputMessage;
    }

    public void toXML(StringBuffer sb) {
        sb.append("<operation")
            .append(" name=\"").append(mName).append("\"")
            .append(" mangledName=\"").append(mMangledName).append("\"")
            .append(" soapAction=\"").append(mSoapAction).append("\"")
            .append(" style=\"").append(mStyle).append("\"")
            .append(" is-document-literal-wrapped=\"").append(mIsDocumentLiteralWrapped).append("\"")
            .append(">");
        sb.append("<input>");
        mInputMessage.toXML(sb);
        sb.append("</input>");
        sb.append("<output>");
        mOutputMessage.toXML(sb);
        sb.append("</output>");
        sb.append("</operation>");
    }
}
