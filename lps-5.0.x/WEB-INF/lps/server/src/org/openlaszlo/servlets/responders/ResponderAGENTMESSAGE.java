/******************************************************************************
 * ResponderAGENTMESSAGE.java
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
import org.openlaszlo.connection.ConnectionGroup;


public class ResponderAGENTMESSAGE extends ResponderConnectionAgent
{
    private static Logger mLogger = Logger.getLogger(ResponderAGENTMESSAGE.class);

    @Override
    protected void respondAgent(HttpServletRequest req, HttpServletResponse res,
                                ConnectionGroup group) throws IOException
    {
        String to  = req.getParameter("to");
        String msg = req.getParameter("msg");
        String range = req.getParameter("range");
        String dset = req.getParameter("dset");

        if (isEmpty(to)) {
            replyWithXMLStatus(res, "missing 'to' parameter", SC_MISSING_PARAMETER);
            return;
        }

        if (isEmpty(dset)) {
            replyWithXMLStatus(res, "missing 'dset' parameter", SC_MISSING_PARAMETER);
            return;
        }

        if (msg == null) {
            replyWithXMLStatus(res, "missing 'msg' parameter", SC_MISSING_PARAMETER);
            return;
        }

        mLogger.debug("to='" + to + "',range='" + range + "', msg='" + msg + "'");

        // Wrap it around resultset so serial number is always '0'.  This makes
        // sure it fools it into believing the local dataset got the right data.
        String xml = "<resultset s=\"0\">" +
            "<root dset=\"" + dset + "\">" + 
            msg +
            "</root>" +
            "</resultset>";

        int count = group.sendMessage(to, xml, range, null);

        replyWithXMLStatus(res, (count > 0 
                                 ? "message sent"
                                 : "no one specified connected (range: " + range + ")"));
    }

    private boolean isEmpty(String str) {
        return str==null || str.equals("");
    }
}
