/******************************************************************************
 * ResponderDISCONNECT.java
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
import org.openlaszlo.connection.Application;
import org.openlaszlo.connection.HTTPConnection;

public final class ResponderDISCONNECT extends ResponderConnection
{

    private static Logger mLogger = Logger.getLogger(ResponderDISCONNECT.class);

    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res,
                               Application app, int serial, String username)
        throws IOException
    {
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="respondImpl(username=" + p[0] + ")"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderDISCONNECT.class.getName(),"051018-37", new Object[] {username})
);

        // Removes connection and unregisters itself from application through
        // binding event listener
        HTTPConnection connection = app.getConnection(req.getParameter("i"));
        if (connection != null) {
            connection.disconnect();
            app.unregister(connection);
        }

        respondWithStatusSWF(res, HttpServletResponse.SC_OK,
                             "disconnected", serial);
    }
}
