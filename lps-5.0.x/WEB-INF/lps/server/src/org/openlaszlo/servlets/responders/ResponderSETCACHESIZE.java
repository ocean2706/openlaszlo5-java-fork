/******************************************************************************
 * ResponderSETCACHESIZE.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved. *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.cache.Cache;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZUtils;

public final class ResponderSETCACHESIZE extends ResponderAdmin
{
    private static Logger mLogger = Logger.getLogger(ResponderSETCACHESIZE.class);

    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream out = res.getOutputStream();
        try {
            String l = req.getParameter("size");
            String msg = null;
            String t = req.getParameter("t"); 
            String k = req.getParameter("k"); 
            boolean inMem = true;
            Cache cache;
            if (t != null && LZUtils.equalsIgnoreCase(t, "data")) {
                cache = ResponderDATA.getCache();
            } else if (t != null && LZUtils.equalsIgnoreCase(t, "compiler")) {
                cache = ResponderCompile.getCompilationManager();
            } else {
                throw new RuntimeException("unknown cache type "+t);
            }
            if (k != null && LZUtils.equalsIgnoreCase(k, "disk")) {
                inMem = false; 
            }

            if (l != null) {
                long s = -1;
                try {
                    s = Long.parseLong(l);
                } catch (NumberFormatException e) {
                }
                if (s >= 0) {
                    if (inMem) {
                        cache.setMaxMemSize(s);
                    } else {
                        cache.setMaxDiskSize(s);
                    }
                } else {
                    mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="ignored bad size parameter"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderSETCACHESIZE.class.getName(),"051018-67")
);
                }
                res.setContentType ("text/xml");
                msg = ResponderCACHEINFO.cacheInfo(false, false);
                out.println(msg);
            } else {
                res.setContentType ("text/html");
msg =
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Can't set cache size: size parameter missing from setcachesize request"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderSETCACHESIZE.class.getName(),"051018-81")
;
                mLogger.error(msg);
                msg = msg + "<br/>" + ResponderCACHEINFO.cacheInfo(false, false);

                out.println("<html><head><title>LPS Cache Information</title></head>");
                out.println("<body>");
                out.println(msg);
                out.println("</body></html>");
            }
    
            mLogger.info(msg);
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
