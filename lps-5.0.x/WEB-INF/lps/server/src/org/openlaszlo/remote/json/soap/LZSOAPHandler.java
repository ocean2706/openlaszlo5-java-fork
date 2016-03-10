/* *****************************************************************************
 * LZSOAPHandler.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.remote.json.soap;

import javax.xml.namespace.QName;
import javax.xml.rpc.handler.GenericHandler;
import javax.xml.rpc.handler.MessageContext;
import javax.xml.rpc.handler.soap.SOAPMessageContext;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.apache.log4j.Logger;

public class LZSOAPHandler extends GenericHandler
{
    private static Logger mLogger = Logger.getLogger(LZSOAPHandler.class);

    @Override
    public QName[] getHeaders() {
        return null;
    }

    @Override
    public boolean handleRequest(MessageContext context) {
        mLogger.debug(
            /* (non-Javadoc)
             * @i18n.test
             * @org-mes="========== handleRequest(" + p[0] + ") "
             */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                LZSOAPHandler.class.getName(),"051018-34", new Object[] {context})
                      );
        displaySOAPMessage(context);
        mLogger.debug("==========");

        return true;
    }

    @Override
    public boolean handleResponse(MessageContext context) {
        mLogger.debug(
            /* (non-Javadoc)
             * @i18n.test
             * @org-mes="========== handleResponse(" + p[0] + ") "
             */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                LZSOAPHandler.class.getName(),"051018-50", new Object[] {context})
                      );
        displaySOAPMessage(context);
        mLogger.debug("==========");
        return true;
    }

    public void displaySOAPMessage(MessageContext context) {
        try {
            SOAPMessageContext soapContext = (SOAPMessageContext)context;

            SOAPMessage message = soapContext.getMessage();
            SOAPPart sp = message.getSOAPPart();
            SOAPEnvelope envelope = sp.getEnvelope();
//            SOAPBody body = envelope.getBody();
//            SOAPHeader header = envelope.getHeader();

            mLogger.debug(envelope.getClass().getName() + '@' + Integer.toHexString(envelope.hashCode()));
            mLogger.debug(envelope);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
