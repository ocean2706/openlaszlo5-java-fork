/******************************************************************************
 * ResponderLIST.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.connection.Application;
import org.openlaszlo.connection.ConnectionGroup;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.xml.internal.DataCompiler;
import org.openlaszlo.xml.internal.DataCompilerException;

public final class ResponderLIST extends ResponderConnection
{
    private static Logger mLogger = Logger.getLogger(ResponderLIST.class);

    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res,
                               Application app, int serial, String username)
        throws IOException
    {
        String users = req.getParameter("users");
        if (users==null||users.equals("")) {
            respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="missing 'users' parameter"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderLIST.class.getName(),"051018-40")
);
            return;
        }

        ConnectionGroup group = app.getConnectionGroup();
        StringBuilder buf = new StringBuilder("<list>");
        Set<String> set = group.list(users);
        for (String s : set) {
            buf.append("<user name=\"")
                .append(s)
                .append("\" />");
        }
        buf.append("</list>");

        mLogger.debug(buf.toString());

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<!DOCTYPE laszlo-data>" +
            "<resultset s=\"" + serial + "\">"
            + buf.toString()
            + "</resultset>";

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
 * @org-mes="StreamWritingException while sending list response: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderLIST.class.getName(),"051018-79", new Object[] {e.getMessage()})
);
        } catch (DataCompilerException e) {
            respondWithExceptionSWF(res, e);
            return;
        } finally {
            FileUtils.close(sos);
        }
    }
}
