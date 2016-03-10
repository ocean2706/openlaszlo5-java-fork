/******************************************************************************
 * ResponderCANVAS.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

/* Return the canvas xml descriptor, as XML document */

package org.openlaszlo.servlets.responders;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openlaszlo.compiler.Canvas;
import org.openlaszlo.utils.FileUtils;

public final class ResponderCANVAS extends ResponderCompile
{
    
    /**
     * @param fileName Full pathname to file from request.
     */
    @Override
    protected void respondImpl(String fileName, HttpServletRequest req, 
                               HttpServletResponse res)
        throws IOException
    {
        ServletOutputStream out = res.getOutputStream();
        try {
            res.setContentType ("text/xml");
            Canvas canvas = getCanvas(fileName, req);
            String xml = canvas.getXML(ResponderAPP_CONSOLE.getRequestXML(req, fileName));
            out.write(xml.getBytes());
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
