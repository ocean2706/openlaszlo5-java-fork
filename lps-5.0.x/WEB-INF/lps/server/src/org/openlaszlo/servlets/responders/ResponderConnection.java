/******************************************************************************
 * ResponderConnection.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.auth.Authentication;
import org.openlaszlo.auth.AuthenticationException;
import org.openlaszlo.auth.NullAuthentication;
import org.openlaszlo.compiler.Canvas;
import org.openlaszlo.connection.Application;
import org.openlaszlo.connection.ConnectionGroup;
import org.openlaszlo.connection.HTTPConnection;
import org.openlaszlo.utils.StringUtils;


public abstract class ResponderConnection extends ResponderCompile
{
    private static final String DEFAULT_AUTHENTICATOR =
        "org.openlaszlo.auth.HTTPAuthentication";
    private static final String ANONYMOUS_AUTHENTICATOR =
        "org.openlaszlo.auth.NullAuthentication";

    private static String mDefaultAuthClass = null;

    private static String mDefaultUserName = null;
    private static Properties mLPSProperties = null;
    private static String mCMOption = "check";
    private static boolean mIsInitialized = false;
    private static Logger mLogger = Logger.getLogger(ResponderConnection.class);

    private static Hashtable<String, Authentication> mAuthenticators = new Hashtable<String, Authentication>();

    protected abstract void respondImpl(HttpServletRequest req, HttpServletResponse res,
                                        Application app, int serial, String username)
        throws IOException;

    @Override
    synchronized public void init(String reqName, ServletConfig config, Properties prop)
        throws ServletException, IOException
    {
        super.init(reqName, config, prop);

        if (! mIsInitialized) {

            HTTPConnection.init(prop);
            mLPSProperties = prop;
            mDefaultUserName = 
                prop.getProperty("connection.none-authenticator.username", 
                                 NullAuthentication.DEFAULT_USERNAME);
            mDefaultAuthClass = 
                prop.getProperty("connection.default.authenticator", 
                                 DEFAULT_AUTHENTICATOR);
            mCMOption = prop.getProperty("compMgrDependencyOption", "check");

            mIsInitialized = true;
        }
    }

    @Override
    protected final void respondImpl(String fileName, HttpServletRequest req,
                                     HttpServletResponse res)
        throws IOException
    {
        try {
            String info = null;

            Application app = getConnectedApplication(fileName, req);
            if (app == null) {
                info = "application does not allow persistent connection calls";
                respondWithErrorSWF(res, info);
                return;
            }

            // Session is valid if a username is returned
            String username = mDefaultUserName;
            Authentication authenticator = app.getAuthenticator();

            // Kludge: skip authentication if lzt=connectionlogin. 
            String lzt = req.getParameter("lzt");
            if (! lzt.equals("connectionlogin") && authenticator != null ) {
                username = authenticator.getUsername(req, res, getAuthParam(req));
                if (username == null) {
                    respondWithErrorSWF(res, "invalid user or session");
                    return;
                }
            }

            // s: serial number of request; this number needs to be echoed
            // back for successful response as an attribute of the resultset
            // tag. See xml response in respondWithStatusSWF() for more
            // information.
            int serial = 0;
            try {
                String serialStr = req.getParameter("s");
                if (serialStr != null) {
                    serial = Integer.parseInt(serialStr);
                }
            } catch (NumberFormatException e) {
                respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="'s' is not a number"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderConnection.class.getName(),"051018-113")
);
                return;
            }

            respondImpl(req, res, app, serial, username);

        } catch (AuthenticationException e) {

            respondWithExceptionSWF(res, e);

        }
    }


    synchronized private 
        Application getConnectedApplication(String filename, HttpServletRequest req)
        throws IOException, AuthenticationException
    {
        mLogger.debug("checkConnection(filename,req)");

        if (filename.endsWith(".lzo")) {
            filename = filename.substring(0, filename.length() - 1) + "x";
        }

        Application app = null;
        String path = req.getServletPath();

        app = Application.getApplication(path, false);
        if (app != null && mCMOption.equals("never"))
            return app;

        // FIXME: [2003-11-03 pkang] should have another object that connection and
        // connectionlogin inherit from. This is the safer option for now.
        String lzt = req.getParameter("lzt");
        if (! lzt.equals("connect") && ! lzt.equals("connectionlogin") )
            return app;

        Canvas canvas = getCanvas(filename, req);
        if (canvas.isConnected()) {

            app = Application.getApplication(path);

            // Fetching lmt should be low impact, since the file should already
            // be cached.
            long lmt = getLastModified(filename, req);
            if (lmt != app.getLastModifiedTime()) {

                String authClass = canvas.getAuthenticator();
                Authentication authenticator;
                try {
                    authenticator = ( authClass == null
                                      ? getAuthenticator(mDefaultAuthClass)
                                      : getAuthenticator(authClass) );
                } catch (ClassNotFoundException e) {
                    if (app != null) Application.removeApplication(app);
                    throw new AuthenticationException("ClassNotFoundException: " + e.getMessage());
                } catch (InstantiationException e) {
                    if (app != null) Application.removeApplication(app);
                    throw new AuthenticationException("InstantiationException: " + e.getMessage());
                } catch (IllegalAccessException e) {
                    if (app != null) Application.removeApplication(app);
                    throw new AuthenticationException("IllegalAccessException: " + e.getMessage());
                }

                long heartbeat = canvas.getHeartbeat();
                boolean sendUserDisconnect = canvas.doSendUserDisconnect();

                String grName = canvas.getGroup();
                if (grName == null)
                    grName = path;
                ConnectionGroup group = ConnectionGroup.getGroup(grName);

                app.setAgents(canvas.getAgents());

                app.setConnectionGroup(group);
                app.setAuthenticator(authenticator);
                app.setHeartbeat(heartbeat);
                app.setLastModifiedTime(lmt);
                app.setSendUserDisconnect(sendUserDisconnect);

                mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="connected app settings " + "- authenticator: " + p[0] + ", senduserdisconnect: " + p[1] + ", heartbeat: " + p[2] + ", lmt: " + p[3]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderConnection.class.getName(),"051018-200", new Object[] {authenticator, sendUserDisconnect, heartbeat, lmt})
                                );
            }

        } else if (app != null) {

            Application.removeApplication(app);

            mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Removed " + p[0] + " as a connected application"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderConnection.class.getName(),"051018-213", new Object[] {path})
); 
        }

        return app;
    }


    protected HashMap<String, String> getAuthParam(HttpServletRequest req)
    {
        HashMap<String, String> map = new HashMap<String, String>();
        String authParam = req.getParameter("authparam");
        if (authParam != null) {
            try {
                authParam = URLDecoder.decode(authParam, "UTF-8");    
            } catch (UnsupportedEncodingException e) {
                // should never happen
                throw new RuntimeException(e);
            }
            String[] params = StringUtils.split(authParam, "&");
            for (int i=0; i < params.length; i++) {
                String[] kv = StringUtils.split(params[i], "=");
                if (kv.length == 2) {
                    map.put(kv[0], kv[1]);
                }
            }
        }

        if (mLogger.isDebugEnabled()) {
            for (String k : map.keySet()) {
                mLogger.debug(k + ": "  + map.get(k));
            }
        }

        return map;
    }

    synchronized private Authentication getAuthenticator(String className)
        throws AuthenticationException, InstantiationException, 
               IllegalAccessException, ClassNotFoundException
    {
        if (className.equals("anonymous"))
            className = ANONYMOUS_AUTHENTICATOR;

        Authentication auth = mAuthenticators.get(className);
        if (auth == null) {
            auth = (Authentication)Class.forName(className).newInstance(); 
            auth.init(mLPSProperties);
            mAuthenticators.put(className, auth);
        }
        return auth;
    }

    @Override
    public final MIME_Type getMimeType()
    {
        return MIME_Type.SWF;
    }
}
