/* *****************************************************************************
 * NullAuthentication.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.auth;

import java.util.HashMap;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class NullAuthentication implements Authentication
{
    public static final String DEFAULT_USERNAME = "user";
    public String mDefaultUserName = null;

    public void init(Properties prop) {
        mDefaultUserName = 
            prop.getProperty("connection.none-authenticator.username", DEFAULT_USERNAME);
    }

    public int login(HttpServletRequest req, HttpServletResponse res,
              HashMap<String, String> param, StringBuffer xmlResponse) {

        String usr = param.get("usr");
        if (usr == null)
            usr = mDefaultUserName;

        xmlResponse
            .append("<authentication>")
            .append("<response type=\"login\">")
            .append("<status code=\"0\" msg=\"ok\" />")
            .append("<username>").append(usr).append("</username>")
            .append("</response>")
            .append("</authentication>");
        return 0;
    }


    public int logout(HttpServletRequest req, HttpServletResponse res,
               HashMap<String, String> param, StringBuffer xmlResponse) {
        xmlResponse
            .append("<authentication>")
            .append("<response type=\"logout\">")
            .append("<status code=\"0\" msg=\"ok\" />")
            .append("</response>")
            .append("</authentication>");
        return 0;
    }


    public String getUsername(HttpServletRequest req, HttpServletResponse res,
                              HashMap<String, String> param)
    {
        String usr = param.get("usr");
        return (usr!=null ? usr : mDefaultUserName);
    }
}
