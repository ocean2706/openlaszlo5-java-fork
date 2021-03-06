/******************************************************************************
 * ResponderCONNECTIONLOGIN.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.auth.Authentication;
import org.openlaszlo.auth.AuthenticationException;
import org.openlaszlo.connection.Application;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.xml.internal.DataCompiler;
import org.openlaszlo.xml.internal.DataCompilerException;

public final class ResponderCONNECTIONLOGIN extends ResponderConnection
{
    private static Logger mLogger = 
        Logger.getLogger(ResponderCONNECTIONLOGIN.class);

    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res,
                               Application app, int serial, String username)
        throws IOException
    {
        Authentication auth = app.getAuthenticator();

        String xml;
        String status="error";
        StringBuffer buf = new StringBuffer();
        try {
            int code = auth.login(req, res, getAuthParam(req), buf);
            status = (code == 0 ? "success" : "failure" );
            String xmlResponse = buf.toString();
            int i = xmlResponse.indexOf("<authentication>");
            if (i != -1) {
                xmlResponse = xmlResponse.substring(i);

                xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<!DOCTYPE laszlo-data>"
                    + "<resultset s=\"" + serial + "\">"
                    + "<login status=\"" + status + "\">"
                    + xmlResponse
                    + "</login></resultset>";

            } else {
                xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" 
                    + "<!DOCTYPE laszlo-data>" 
                    + "<resultset s=\"" + serial + "\">"
                    + "<login status=\"error\">"
                    + "<error message=\"could not find &lt;authentication&gt; element\" />"
                    + "</login></resultset>";
                mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="could not find <authentication> element: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCONNECTIONLOGIN.class.getName(),"051018-64", new Object[] {xmlResponse})
);
            }
        } catch (AuthenticationException e) {
            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" 
                + "<!DOCTYPE laszlo-data>" 
                + "<resultset s=\"" + serial + "\">"
                + "<login status=\"error\">"
                + "<error message=\"authentication exception\" />"
                + "</login></resultset>";
            mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="AuthenticationException"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCONNECTIONLOGIN.class.getName(),"051018-80")
, e);
        }

        res.setContentType(MimeType.SWF);

        ServletOutputStream sos = res.getOutputStream();
        try {
            InputStream swfbytes = DataCompiler.compile(xml, mSWFVersionNum);
            FileUtils.sendToStream(swfbytes, sos);
            sos.flush();
        } catch (FileUtils.StreamWritingException e) {
            mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="StreamWritingException while sending connection login response: "
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCONNECTIONLOGIN.class.getName(),"051018-98")
 + e.getMessage());
        } catch (DataCompilerException e) {
            respondWithExceptionSWF(res, e);
            return;
        } finally {
            FileUtils.close(sos);
        }
    }
}
