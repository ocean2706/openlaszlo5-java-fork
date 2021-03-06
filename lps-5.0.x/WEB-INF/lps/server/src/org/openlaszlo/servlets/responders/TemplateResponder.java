/******************************************************************************
 * TemplateResponder.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved. *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.compiler.Canvas;
import org.openlaszlo.compiler.CompilationError;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;
import org.openlaszlo.utils.TransformUtils;

public final class TemplateResponder extends ResponderCompile
{
    private static Logger mLogger = Logger.getLogger(TemplateResponder.class);

    /** {lzt -> Responder} */
    private static Map<String, Responder> mResponderMap = new HashMap<String, Responder>();

    public static Responder getResponder(String lzt) {
        String pathname = org.openlaszlo.server.LPS.getTemplateDirectory() +
            File.separator + lzt + org.openlaszlo.i18n.LaszloMessages.getMessage(
                TemplateResponder.class.getName(),"xsltfilename");

/* for i18n get filename from properties file.
original :   String pathname =  org.openlaszlo.server.LPS.getTemplateDirectory() +
            File.separator + lzt + "-response.xslt";
*/


        synchronized (mResponderMap) {
            Responder responder = mResponderMap.get(pathname);
            if (responder == null) {
                if (new File(pathname).exists())
                    responder = new TemplateResponder(pathname);
                mLogger.debug("getResponder(" + lzt + ") -> " + responder);
                mResponderMap.put(pathname, responder);
            }
            return responder;
        }
    }
    
    protected final String mTemplatePathname;
    
    protected TemplateResponder(String templatePathname) {
        this.mTemplatePathname = templatePathname;
    }
    
    /**
     * @param fileName Full pathname to file from request.
     */
    @Override
    protected void respondImpl(String fileName, HttpServletRequest req, 
                               HttpServletResponse res)
        throws IOException
    {
        mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Responding with HTML wrapper for " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                TemplateResponder.class.getName(),"051018-69", new Object[] {fileName})
);
        if ("svg".equals(LZHttpUtils.getLzOption("runtime", req)) &&
            "svg".equals(req.getParameter("lzt"))) {
            res.setContentType ("image/svg+xml");
        } else {
            res.setContentType("text/html");
        }
        ServletOutputStream out = res.getOutputStream();
        try {
            // Get the canvas first, so that if this fails and we
            // write the compilation error, nothing has been written
            // to out yet.
            Canvas canvas = getCanvas(fileName, req);
            writeCanvas(out, req, canvas, fileName);
        } catch (CompilationError e) {
            ResponderAPP_CONSOLE.respondCompilationError(e, req, res);
        } finally {
            FileUtils.close(out);
        }
    }

    /**
     * Writes the canvas html.  The canvas is the area in which the
     * Laszlo application is rendered.
     * @param out <tt>ServletOutputStream</tt> to write on
     * @param req request to retrieve scheme, server name, server port and
     * requested url
     * @param canvas the canvas for the given request
     * @param fileName file for the app
     */
    private void writeCanvas (ServletOutputStream out, HttpServletRequest req, 
                              Canvas canvas, String fileName)
        throws IOException 
    {
        String xmlString = canvas.getXML(ResponderAPP_CONSOLE.getRequestXML(req, fileName));
        Properties properties = new Properties();
        TransformUtils.applyTransform(mTemplatePathname, properties,
                                      xmlString, out);
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.HTML;
    }
}
