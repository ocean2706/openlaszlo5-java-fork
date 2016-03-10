/* *****************************************************************************
 * RoleAuthentication.java
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
import javax.servlet.http.HttpSession;

import org.apache.log4j.Logger;


/** 
 * Role implementation of Authentication. 
 *
 * This class implements the Authentication interface 
 * methods.  Every public member is an implementation of
 * the Authentication interface.  
 **/
public class RoleAuthentication implements Authentication
{
    /** RoleAuthentication logger */
    protected static Logger mLogger = Logger.getLogger(RoleAuthentication.class);

    public void init(Properties prop) 
    {
    }


    public int login(HttpServletRequest req, HttpServletResponse res,
                     HashMap<String, String> param, StringBuffer xmlResponse)
    {

        mLogger.debug("login(req,res,param,xmlResponse)");
        String role = req.getParameter("role");
        return req.isUserInRole(role) ? 0 : 1;
    }

    public int logout(HttpServletRequest req, HttpServletResponse res,
                      HashMap<String, String> param, StringBuffer xmlResponse)
    {

        mLogger.debug("logout(req,res,param,xmlResponse)");
        // get the current session, if it doesn't exist, we can't logout
        HttpSession sess = req.getSession(false);
        if (sess == null) {
            return 1;
        } else {
            req.getSession().invalidate();
            return 0;
        }
    }


    public String getUsername(HttpServletRequest req, HttpServletResponse res,
                              HashMap<String, String> param)
    {
        mLogger.debug("getUsername(req,res,param)");
            return req.getRemoteUser();
    }

}
