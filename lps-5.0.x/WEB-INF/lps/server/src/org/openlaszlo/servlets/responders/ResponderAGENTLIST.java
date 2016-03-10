/******************************************************************************
 * ResponderAGENTLIST.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.connection.ConnectionGroup;


public class ResponderAGENTLIST extends ResponderConnectionAgent
{
    private static Logger mLogger = Logger.getLogger(ResponderAGENTLIST.class);

    @Override
    protected void respondAgent(HttpServletRequest req, HttpServletResponse res,
                                ConnectionGroup group) throws IOException
    {
        String users = req.getParameter("users");
        if ( users == null || users.equals("") ) {
            replyWithXMLStatus(res, "missing 'users' parameter", SC_MISSING_PARAMETER);
            return;
        }

        StringBuilder buf = new StringBuilder("<list>");
        Set<String> set = group.list(users);
        for (String s : set) {
            buf.append("<user name=\"")
                .append(s)
                .append("\" />");
        }
        buf.append("</list>");

        mLogger.debug(buf.toString());

        replyWithXML(res, "ok", buf.toString());
    }
}
