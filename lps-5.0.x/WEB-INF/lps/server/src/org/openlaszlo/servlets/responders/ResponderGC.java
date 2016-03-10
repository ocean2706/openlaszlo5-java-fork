/******************************************************************************
 * ResponderGC.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public final class ResponderGC extends ResponderAdmin
{

    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        System.gc();
        respondWithXML(res, "<gc>ran garbage collector</gc>");
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.XML;
    }
}
