/******************************************************************************
 * ResponderSOURCE.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.  *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.xml.internal.XMLUtils;

public final class ResponderSOURCE extends ResponderCompile
{
    private static Logger mLogger = Logger.getLogger(ResponderSOURCE.class);

    /**
     * Overridden method from ReponseCompile.
     *
     * @param req unused
     */
    @Override
    protected long getLastModified(String fileName, HttpServletRequest req)
    {
        // We don't care about other dependencies since all we show is the
        // top-level LZX file.
        return new File(fileName).lastModified();
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
 * @org-mes="Responding with SOURCE for " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderSOURCE.class.getName(),"051018-53", new Object[] {fileName})
);

        res.setContentType ("text/html; charset=utf-8");
        ServletOutputStream  out = res.getOutputStream();
        try {
             writeHeader(out, null);
//            writeText(out, FileUtils.readFileString(new File(fileName)));
                    FileInputStream fis = new FileInputStream(fileName);
                    InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
                    BufferedReader reader = new BufferedReader(isr);

                    String line;
                    StringBuilder sb = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                        sb.append("\n");
                    }
                    reader.close();
            isr.close();
            fis.close();
//           writeText(out, FileUtils.readFileString(new File(fileName)));
            writeText(out, sb.toString());
            writeFooter(out);
        } finally {
            FileUtils.close(out);
        }
    }

    /**
     * Writes the Text entry 
     * @param out A PrintWriter
     * @param text A <tt>String</tt> containing the .lzx text
     * @param message Message(s) generated during the compilation process
     */
    private void writeText(ServletOutputStream out, String text)
        throws IOException {
        out.println ("<pre class=\"code\">");
        // TODO: [2003-04-03 bloch] should rename SWFHTML to HTML
        // since that's a better description.
        out.println (XMLUtils.escapeXmlForSWFHTML(text));
        out.println ("</pre>");
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.HTML;
    }
}
