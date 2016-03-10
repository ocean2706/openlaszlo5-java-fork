/******************************************************************************
 * ResponderLOG.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.utils.FileUtils;

public final class ResponderLOG extends ResponderAdmin
{
    private static Logger mLogger = Logger.getLogger(ResponderLOG.class);

    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream out = res.getOutputStream();
        FileInputStream in = null;
        try {
            res.setContentType ("text/html");
            out.println("<html><head><title>LPS Log</title></head>");
            out.println("<body><pre>");
            File logFile = ResponderLOGCONFIG.getLogFile();
            if (logFile != null) {
                in = new FileInputStream(logFile);
                FileUtils.escapeHTMLAndSend(in, out);
            } else {
                out.println("No log file.");
            }
            out.println("</pre></body></html>");
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Sent log"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderLOG.class.getName(),"051018-49")
);
        } finally {
            FileUtils.close(in);
            FileUtils.close(out);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.HTML;
    }
}
