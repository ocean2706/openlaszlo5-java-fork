/* *****************************************************************************
 * JSONSimpleDeserializer.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001-2003 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Axis" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.openlaszlo.remote.json.soap.encoding;

import java.io.CharArrayWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.namespace.QName;

import org.apache.axis.encoding.DeserializationContext;
import org.apache.axis.encoding.DeserializerImpl;
import org.apache.axis.message.SOAPHandler;
import org.apache.axis.utils.Messages;
import org.apache.log4j.Logger;
import org.openlaszlo.sc.ScriptCompiler;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;


// Lifted from SimpleDeserializer
@SuppressWarnings("serial")
public class JSONSimpleDeserializer extends DeserializerImpl
{
    public static Logger mLogger =
        Logger.getLogger(JSONSimpleDeserializer.class);

    private final CharArrayWriter val = new CharArrayWriter();

    public static final Boolean TRUE = Boolean.TRUE;
    public static final Boolean FALSE = Boolean.FALSE;

    public QName xmlType;
    public Class<?> javaType;

    public JSONSimpleDeserializer(Class<?> javaType, QName xmlType) {
        this.xmlType = xmlType;
        this.javaType = javaType;
    }

    @Override
    public void characters(char [] chars, int start, int end)
        throws SAXException {
        val.write(chars,start,end);
    }

    @Override
    public void onEndElement(String namespace, String localName,
                             DeserializationContext context) 
        throws SAXException {


        //----------------------------------------------------------------------
        // FIXME: [2007-07-13 pkang] does this handle SOAP 1.2 fault format?
        // If we're deserializing fault, just pass back the string value. 
        // SOAP 1.1: <faultstring> 
        //----------------------------------------------------------------------
        if ("".equals(namespace)) {
            if ( "faultstring".equals(localName) || 
                 "faultactor".equals(localName)  ) {
                value = val.toString();
                return;
            }
        }

        StringBuilder body = new StringBuilder();

        //if (isNil || val == null) {  -- FIX http://nagoya.apache.org/bugzilla/show_bug.cgi?id=11945
        if (isNil) {
            body.append("null");
            value = body.toString();
            return;
        }

        String source = val.toString();
        try {
            if ( javaType == int.class || javaType == Integer.class) {
                body.append(Integer.parseInt(source));
            } else if (javaType == long.class || javaType == Long.class) {
                // push as int
                int n = Long.valueOf(source).intValue();
                body.append(n);
            } else if (javaType == short.class || javaType == Short.class) {
                // push as int
                int n = Short.valueOf(source).intValue();
                body.append(n);
            } else if (javaType == byte.class || javaType == Byte.class) {
                // push as int
                int n = Byte.valueOf(source).intValue();
                body.append(n);
            } else if (javaType == BigInteger.class) {
                // push as int
                int n = BigInteger.valueOf(Long.parseLong(source)).intValue();
                body.append(n);
            } else if (javaType == BigDecimal.class) {
                // push as int
                int n = BigDecimal.valueOf(Long.parseLong(source)).intValue();
                body.append(n);
            } else if (javaType == boolean.class || javaType == Boolean.class) {
                switch (source.charAt(0)) {
                case '0': case 'f': case 'F':
                    body.append("false");
                    break;
                case '1': case 't': case 'T': 
                    body.append("true");
                    break;
                default:
                    body.append("true");
                    break;
                }
            } else if (javaType == float.class || javaType == Float.class) {
                if (source.equals("NaN")) {
                    body.append(Float.NaN);
                } else if (source.equals("INF")) {
                    body.append(Float.POSITIVE_INFINITY);
                } else if (source.equals("-INF")) {
                    body.append(Float.NEGATIVE_INFINITY);
                } else {
                    body.append(Float.parseFloat(source));
                }
            } else if (javaType == double.class || javaType == Double.class) {
                if (source.equals("NaN")) {
                    body.append(new Double(Double.NaN));
                } else if (source.equals("INF")) {
                    body.append(new Double(Double.POSITIVE_INFINITY));
                } else if (source.equals("-INF")) {
                    body.append(new Double(Double.NEGATIVE_INFINITY));
                } else {
                    body.append(Double.valueOf(source));
                }
            } else if (javaType == String.class) {
                // treat as a string by default
                body.append(ScriptCompiler.JSONquote(source));
            } else {
                // catch all
                mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="treating " + p[0] + " like string: " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                JSONSimpleDeserializer.class.getName(),"051018-210", new Object[] {javaType, source})
);
                body.append(ScriptCompiler.JSONquote(source));
            }
        } catch (Exception e) {
            mLogger.error("Exception", e);
            throw new SAXException(e.getMessage());
        }

        value = body.toString();;
    }

    /**
     * There should not be nested elements.
     */
    @Override
    public SOAPHandler onStartChild(String namespace, String localName, 
                                    String prefix, Attributes attributes,
                                    DeserializationContext context)
        throws SAXException {
        throw new SAXException(Messages.getMessage("cantHandle00",
                                                   "SimpleDeserializer"));
    }
}
