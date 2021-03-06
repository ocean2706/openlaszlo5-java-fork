/******************************************************************************
 * ResponderLIB.java
 *****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;

/**
 * Respond to library requests.
 * Currently does not use compilation manager, just looks for and returns
 * the requested swf file specified by the 'libpath' query arg.
 */

public final class ResponderLIB extends Responder
{
    private static Logger mLogger = Logger.getLogger(ResponderLIB.class);

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.SWF;
    }

    @Override
    public void init(String reqName, ServletConfig config, Properties prop)
        throws ServletException, IOException
    { 
        super.init(reqName, config, prop);
    }

    /**
     * Delivers a 'snippet' compiled library file. 
     *
     * Gets the filename of the snippet we are loading from the
     * request, where it is specified in the "libpath" query
     * arg. 'libpath' is relative to the app base filename.  Thus, an
     * app at /intl2/test/snippets/main.lzx, which has &ltimport
     * href="lib/foo.lzx"&gt; will send a request with
     * lzt=lib&libpath=lib/foo.lzx
     */
    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {

        try {
            String patharg = req.getParameter("libpath");
            if (patharg == null) {
                throw new IOException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="could not find 'libpath' query arg in lzt=lib request"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderLIB.class.getName(),"051018-71")
);
            }

            /* getContextPath() = /intl2
               getPathInfo() = null
               getPathTranslated() = null
               getRequestURI() = /intl2/test/snippets/main.lzx
               getServletPath() = /test/snippets/main.lzx
            */


            ServletOutputStream out = res.getOutputStream();

            // canonicalize the separator char
            String path  = (new File(patharg)).getPath();
            String appbasedir = (new File(req.getServletPath())).getParent();
            String libpath;
            // Check if we are merging with an absolute or relative lib path.
            // Why doesn't Java have fs:merge-pathnames? 
            if (path.charAt(0) == File.separatorChar) {
                libpath = path;
            } else {
                libpath = (new File(appbasedir, path)).getPath();
            } 
            String filename = LZHttpUtils.getRealPath(mContext, libpath);
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Responding with LIB for " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderLIB.class.getName(),"051018-104", new Object[] {filename})
);
            res.setContentType(MimeType.SWF);
            InputStream ins = null;
            try {
                // open the file and return it.
                ins = new BufferedInputStream(new FileInputStream(filename));
                FileUtils.send(ins, out);
            } finally {
                FileUtils.close(out);
                FileUtils.close(ins);
            }
        } catch (java.io.FileNotFoundException e) {
            throw new IOException(e.getMessage());
        }
    }
}
    
