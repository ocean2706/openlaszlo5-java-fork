/******************************************************************************
 * ResponderUTCSWF.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.SWFUtils;

/**
 * Send out a response that current utc in a SWF
 */
public final class ResponderUTCSWF extends ResponderAdmin
{
    private static Logger mLogger = Logger.getLogger(ResponderUTCSWF.class);

    @Override
    public void init(String reqName, ServletConfig config, Properties prop)
        throws ServletException, IOException
    {
        super.init(reqName, config, prop);
    }

    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream out = res.getOutputStream();
        InputStream in = null;
        try {
            res.setContentType ("application/x-shockwave-flash");
            long utc = System.currentTimeMillis();
            String s = "" + utc;
            in = SWFUtils.getErrorMessageSWF(s);
            res.setContentLength(in.available());
            FileUtils.sendToStream(in, out);
        } catch (FileUtils.StreamWritingException e) {
            mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="StreamWritingException while sending utcswf: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderUTCSWF.class.getName(),"051018-54", new Object[] {e.getMessage()})
);
        } finally {
            FileUtils.close(in);
            FileUtils.close(out);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.SWF;
    }
}
