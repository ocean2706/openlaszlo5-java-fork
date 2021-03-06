/******************************************************************************
 * ResponderEVAL.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2008-2009, 2011 Laszlo Systems, Inc.  All Rights Reserved. *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.compiler.Compiler;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;


public final class ResponderEVAL extends Responder
{
    private static Logger mLogger = Logger.getLogger(ResponderEVAL.class);


    public static SimpleDateFormat RFC822DATEFORMAT 
        = new SimpleDateFormat( "EEE, d MMM yyyy HH:mm:ss Z");

    public static String getDateAsRFC822String(Date date)
    {
        return RFC822DATEFORMAT.format(date);
    }

    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream out = res.getOutputStream();

        String script = req.getParameter("lz_script");
        boolean logmsg = false;

        String seqnum = req.getParameter("lzrdbseq");

        String lz_log = req.getParameter("lz_log");

        if ((lz_log != null) && lz_log.equals("true")) {
            logmsg = true;
        }

        // try and make the browser cache this compiled code
        GregorianCalendar cal = new GregorianCalendar();
        cal.add(Calendar.YEAR, 10);
        Date expires = cal.getTime();
        res.setHeader("Expires", getDateAsRFC822String(expires));

        if (logmsg) {
          String message =
            /* (non-Javadoc)
             * @i18n.test
             * @org-mes="CLIENT_LOG " + p[0]
             */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
              ResponderEVAL.class.getName(),"051018-50", new Object[] {script});
          if (script.startsWith("ERROR")) {
            mLogger.error(message);
          } else if (script.startsWith("WARNING")) {
            mLogger.warn(message);
          } else if (script.startsWith("INFO")) {
            mLogger.info(message);
          } else {
            mLogger.debug(message);
          }
          byte[] action = new byte[0];
          int swfversion = 8;
          ScriptCompiler.writeScriptToStream(action, out, swfversion);
          out.flush();
          FileUtils.close(out);
        } else {
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="doEval for " + p[0] + ", seqnum=" + p[1]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                ResponderEVAL.class.getName(),"051018-64", new Object[] {script, seqnum})
);
            try {
                res.setContentType(MimeType.SWF);
                Compiler compiler = new Compiler();
                String swfversion = req.getParameter("lzr");
                if (swfversion == null) {
                    swfversion = "swf8";
                }
                if (Compiler.AS3_RUNTIMES.contains(swfversion)) {
                    File appfile = new File(LZHttpUtils.getRealPath(mContext, req));
                    compiler.compileAndWriteToAS3(script, swfversion, seqnum, appfile, out);
                } else {
                    compiler.compileAndWriteToSWF(script, seqnum, out, swfversion);
                }
            } catch (Exception e) {
                mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="LZServlet got error compiling/writing SWF!" + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                ResponderEVAL.class.getName(),"051018-83", new Object[] {e})
);
                StringWriter err = new StringWriter();
                e.printStackTrace(new PrintWriter(err));
                mLogger.info(err.toString());
            }
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.SWF;
    }
}
