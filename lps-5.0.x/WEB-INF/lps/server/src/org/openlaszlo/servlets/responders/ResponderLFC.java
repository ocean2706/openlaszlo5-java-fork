/******************************************************************************
 * ResponderLFC.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openlaszlo.compiler.CompilationEnvironment;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;

/* Sends the requested version of the LFC runtime library.
 * query args: lzr=dhtml|swf8|...
 * debug=(Non null value)
 */
public final class ResponderLFC extends Responder
{

    private boolean notModified(long lastModified, HttpServletRequest req,
                                HttpServletResponse res)
        throws IOException
    {
        if (lastModified != 0) {

            String lms = LZHttpUtils.getDateString(lastModified);

            // Check last-modified and if-modified-since dates
            String ims = req.getHeader(LZHttpUtils.IF_MODIFIED_SINCE);
            long ifModifiedSince = LZHttpUtils.getDate(ims);

            if (ifModifiedSince != -1) {
                if (lastModified <= ifModifiedSince) {
                    res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return true;
                }
            }

            res.setHeader(LZHttpUtils.LAST_MODIFIED, lms);
        }

        return false;
    }


    /**
     */
    @Override
    protected void respondImpl(HttpServletRequest req, 
                               HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream  out = res.getOutputStream();

        String lfc = LPS.getLFCname(
            LZHttpUtils.getLzOption("runtime", req),
          "true".equals(req.getParameter(CompilationEnvironment.DEBUG_PROPERTY)) || 
            req.getParameter("_canvas_debug") != null,
          "true".equals(req.getParameter(CompilationEnvironment.PROFILE_PROPERTY)),
          "true".equals(req.getParameter(CompilationEnvironment.BACKTRACE_PROPERTY)),
          "true".equals(req.getParameter(CompilationEnvironment.SOURCE_ANNOTATIONS_PROPERTY)));
        String path = LPS.getLFCDirectory();

        File lfcfile = new File(path, lfc);

        long lastModified = lfcfile.lastModified();
        // Round to the nearest second.
        lastModified = ((lastModified + 500L)/1000L) * 1000L;
        if (notModified(lastModified, req, res)) {
            return;
        }

        try {
            res.setContentType ("application/x-javascript");

            String content = FileUtils.readFileString(lfcfile);
            out.println(content);
            out.flush();
        } finally {
            FileUtils.close(out);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.HTML;
    }
}
