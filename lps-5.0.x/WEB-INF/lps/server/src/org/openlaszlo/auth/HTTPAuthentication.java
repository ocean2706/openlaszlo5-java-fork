/* *****************************************************************************
 * HTTPAuthentication.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.auth;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.openlaszlo.data.Data;
import org.openlaszlo.data.DataSourceException;
import org.openlaszlo.data.HTTPDataSource;


/** 
 * HTTP implementation of Authentication. 
 *
 * This class implements the Authentication interface 
 * methods.  Every public member is an implementation of
 * the Authentication interface.  
 **/
public class HTTPAuthentication implements Authentication
{
    /** Default URL */
    private String mDefaultURL = null;

    /** Builder to create documents with */
    private SAXBuilder mBuilder = new SAXBuilder();

    /** HTTPAuthentication logger */
    protected static Logger mLogger = Logger.getLogger(HTTPAuthentication.class);

    public void init(Properties prop) 
    {
        mDefaultURL = prop.getProperty("httpauthentication.url");
        mLogger.debug("default url: " + mDefaultURL);            
    }


    /**
     * ?rt=login&usr=username&pwd=password
     *
     * [successful login]
     * <authentication>
     *     <response type="login">
     *         <status code="0" msg="ok"/>
     *         <username>username</username>
     *     </response>
     * </authentication>
     *
     * [login failure]
     * <authentication>
     *     <response type="login">
     *         <status code="3" msg="invalid"/>
     *     </response>
     * </authentication>
     */
    public int login(HttpServletRequest req, HttpServletResponse res,
                     HashMap<String, String> param, StringBuffer xmlResponse)
        throws AuthenticationException {

        mLogger.debug("login(req,res,param,xmlResponse)");
        int code=1;
        String usr = req.getParameter("usr");
        String pwd = req.getParameter("pwd");
        String query = "rt=login&usr=" + usr + "&pwd=" + pwd;
        callAuthenticationServer(req, res, param, query, xmlResponse);
        if (xmlResponse.toString().indexOf("code=\"0\"") != -1)
            code = 0;
        return code;
    }


    /**
     * ?rt=logout
     *
     * [logout w/valid session]
     * <authentication>
     *     <response type="logout">
     *         <status code="0" msg="ok"/>
     *     </response>
     * </authentication>
     *
     * [logout w/invalid session]
     * <authentication>
     *     <response type="logout">
     *         <status code="4" msg="invalid session"/>
     *     </response>
     * </authentication>
     */
    public int logout(HttpServletRequest req, HttpServletResponse res,
                      HashMap<String, String> param, StringBuffer xmlResponse)
        throws AuthenticationException {

        mLogger.debug("logout(req,res,param,xmlResponse)");
        int code = 1;
        String query = "rt=logout";
        callAuthenticationServer(req, res, param, query, xmlResponse);
        if (xmlResponse.toString().indexOf("code=\"0\"") != -1)
            code = 0;
        return code;
    }


    /**
     * ?rt=getusername
     *
     * [valid session -- return username]
     * <authentication>
     *     <response type="getusername">
     *         <status code="0" msg="ok"/>
     *         <username>username</username>
     *     </response>
     * </authentication>
     *
     * [invalid session -- return no username]
     * <authentication>
     *     <response type="getusername">
     *         <status code="4" msg="invalid session"/>
     *     </response>
     * </authentication>
     */
    public String getUsername(HttpServletRequest req, HttpServletResponse res,
                              HashMap<String, String> param)
        throws AuthenticationException
    {
        mLogger.debug("getUsername(req,res,param)");
        try {
            String query = "rt=getusername";
            StringBuilder buf = new StringBuilder();

            callAuthenticationServer(req, res, param, query, buf);

            StringReader reader = new StringReader(buf.toString());
            Document document = mBuilder.build(reader);
            Element root = document.getRootElement();
            Element eUsername = root.getChild("response");
            boolean isOk = (getStatusCode(eUsername)==0);
            return isOk ? eUsername.getChildText("username") : null;
        } catch (JDOMException e) {
            throw new AuthenticationException(e.getMessage());
        } catch (IOException e) {
            throw new AuthenticationException(e.getMessage());
        } 
    }


    /** 
     * This proxies request and response headers.
     */
    private void callAuthenticationServer(HttpServletRequest req, 
                                          HttpServletResponse res,
                                          HashMap<String, String> param, String query, 
                                          Appendable xmlResponse)
        throws AuthenticationException
    {
        if (mDefaultURL == null) {
            String scheme = req.getScheme();
            String host = req.getServerName();
            int port = req.getServerPort();
            String path = req.getContextPath();
            mDefaultURL = scheme + "://" + host + ":" + port + path + "/AuthenticationServlet";
        }

        Data data = null;
        try {

            String urlstr = param.get("url");
            if (urlstr == null) 
                urlstr = mDefaultURL;
            urlstr += "?" + query;
            data = HTTPDataSource.getHTTPData(req, res, urlstr, -1);
            xmlResponse.append(data.getAsString());

        } catch (DataSourceException e) {
            throw new AuthenticationException(e.getMessage());
        } catch (MalformedURLException e) {
            throw new AuthenticationException(e.getMessage());
        } catch (IOException e) {
            throw new AuthenticationException(e.getMessage());
        } finally {
            if (data != null)
                data.release();
        }
    }


    /** Fetch status code request. 
     * @param element element to retrieve status from
     * @return <0: error, 0: ok, 0<: ok but warning */
    static private int getStatusCode(Element element)
    {
        mLogger.debug("getStatusCode(element)");

        int code = 1;
        if (element != null) {
            Element eStatus = element.getChild("status");
            String statCode = eStatus.getAttributeValue("code");
            //String statMesg = eStatus.getAttributeValue("msg");
            try {
                code = Integer.parseInt(statCode);
            } catch (NumberFormatException e) {
                mLogger.debug(e.getMessage());
            }
        }
        return code;
    }
}
