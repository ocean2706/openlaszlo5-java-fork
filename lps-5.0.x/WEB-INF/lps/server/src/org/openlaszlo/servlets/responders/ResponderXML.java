/******************************************************************************
 * ResponderXML.java
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
import org.openlaszlo.utils.StringUtils;

public final class ResponderXML extends ResponderCompile
{
    private static Logger mLogger = Logger.getLogger(ResponderXML.class);

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
 * @org-mes="Responding with XML for " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderXML.class.getName(),"051018-52", new Object[] {fileName})
);
        if (fileName.endsWith(".lzo")) {
            fileName = StringUtils.setCharAt(fileName, fileName.length() - 1, 'x');
        }
    
        ServletOutputStream out = res.getOutputStream();
        FileInputStream fis = null;
        try {
            res.setContentType("text/xml");
            fis = new FileInputStream(new File(fileName));
            FileUtils.send(fis, out);
        } finally {
            FileUtils.close(fis);
            FileUtils.close(out);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.XML;
    }
}
