/******************************************************************************
 * ResponderERRORCOUNT.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.utils.FileUtils;

public final class ResponderERRORCOUNT extends ResponderAdmin
{
    private static Logger mLogger = Logger.getLogger(ResponderERRORCOUNT.class);

    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream out = res.getOutputStream();
        try {
            res.setContentType ("text/xml");
            // ignore the race in accessing mErrorSWFCount
            out.println("<lps-errorcount>" + Responder.getErrorSWFCount() + "</lps-errorcount>");
            if (req.getParameter("clear") != null) {
                Responder.clearErrorSWFCount();
                mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Cleared error count"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderERRORCOUNT.class.getName(),"051018-41")
);
            }
        } finally {
            FileUtils.close(out);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.XML;
    }
}
