/******************************************************************************
 * ResponderCLEARLOG.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

public final class ResponderCLEARLOG extends ResponderAdmin
{
    private static Logger mLogger = Logger.getLogger(ResponderCLEARLOG.class);

    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException
    {
        String[] status = new String[1];
        status[0] = "";

        boolean cleared = ResponderLOGCONFIG.clearLog(status);

        StringBuilder buf = new StringBuilder();
        buf.append("<clearlog ")
            .append("cleared=\"").append(cleared).append("\" ");
        if (status[0].intern() != "") {
            buf.append("status=\"").append(status[0]).append("\" ");
        }
        buf.append(" />");

        respondWithXML(res, buf.toString());

        if (cleared)
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Cleared log."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCLEARLOG.class.getName(),"051018-51")
);
        else
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Could not clear log."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCLEARLOG.class.getName(),"051018-60")
);

    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.XML;
    }
}
