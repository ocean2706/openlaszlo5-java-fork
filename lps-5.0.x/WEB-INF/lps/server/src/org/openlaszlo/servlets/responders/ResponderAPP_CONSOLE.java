/******************************************************************************
 * ResponderAPP_CONSOLE.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2010, 2011 Laszlo Systems, Inc.  All Rights Reserved.  *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.compiler.Canvas;
import org.openlaszlo.compiler.CompilationEnvironment;
import org.openlaszlo.compiler.CompilationError;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;
import org.openlaszlo.utils.TransformUtils;
import org.openlaszlo.xml.internal.XMLUtils;

public final class ResponderAPP_CONSOLE extends ResponderCompile
{
    private static Logger mLogger = Logger.getLogger(ResponderAPP_CONSOLE.class);

/* for i18n get filename from properties file.
original :  private static String sStyleSheetPathname =
        org.openlaszlo.server.LPS.getTemplateDirectory() +
        File.separator + "app-console.xslt";
*/   

 private static String sStyleSheetPathname =
        org.openlaszlo.server.LPS.getTemplateDirectory() + 
        File.separator + org.openlaszlo.i18n.LaszloMessages.getMessage(ResponderAPP_CONSOLE.class.getName(),"xsltfilename");
    
    /*
     * @param fileName Full pathname to file from request.
     */
    @Override
    protected void respondImpl(String fileName, HttpServletRequest req, 
                               HttpServletResponse res)
        throws IOException
    {
        mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Responding with HTML wrapper for " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                ResponderAPP_CONSOLE.class.getName(),"051018-47", new Object[] {fileName})
);
        res.setContentType("text/html");
        ServletOutputStream out = res.getOutputStream();
        try {
            // Get the canvas first, so that if this fails and we
            // write the compilation error, nothing has been written
            // to out yet.
            // Replace .lzo with .lzx 
            String orig = fileName;

            /* This method doesn't call writeHeader and writeFooter, since
             * the stylesheet handles the whole HTML generation. */
            Canvas canvas = getCanvas(fileName, req);
            writeCanvas(out, req, canvas, orig);
        } finally {
            FileUtils.close(out);
        }
    }
    
    static public String getRequestXML(HttpServletRequest req, String fileName)
        throws IOException
    {
        String lps  = req.getContextPath();
        String agent = req.getHeader("user-agent");
        // MS-specific header
        String os   = req.getHeader("ua-os");
        String debugconsole = Boolean.toString("true".equals(req.getParameter(CompilationEnvironment.CONSOLEDEBUG_PROPERTY)));
        mLogger.info("debugconsole == "+debugconsole +", getParameter(lzconsoledebug)="+req.getParameter(CompilationEnvironment.CONSOLEDEBUG_PROPERTY));
        String popupdebugconsole = Boolean.toString("true".equals(LZHttpUtils.getLzOption("lzconsolewindow", req)));
        String query_args = getQueryArgs(req);
        String url  = req.getRequestURI();
        int i = url.lastIndexOf("/");
        url = url.substring(i + 1, url.length() );
        String relurl  = req.getRequestURI();
        i = relurl.indexOf("/", 1);
        relurl = relurl.substring(i + 1, relurl.length());
        String unopturl = url.substring(0, url.length() - 4) + ".lzx";
        String opturl = url.substring(0, url.length() - 4) + ".lzo";
        boolean isPocketPC = (os != null && os.indexOf("POCKET PC") > -1);
        boolean isWin = (agent != null && agent.toLowerCase(Locale.ENGLISH).indexOf("win") > -1);

        Properties props = initCMgrProperties(req);

        StringBuilder buffer = new StringBuilder();
        buffer.append( 
            "<request " +
            "lps=\"" + XMLUtils.escapeXml(lps) + "\" " +
            "url=\"" + XMLUtils.escapeXml(url) + "\" " +
            "relurl=\"" + XMLUtils.escapeXml(relurl) + "\" " +
            "fullpath=\"" + XMLUtils.escapeXml(req.getRequestURI()) + "\" " +
            "opt-url=\"" + XMLUtils.escapeXml(opturl) + "\" " +
            "unopt-url=\"" + XMLUtils.escapeXml(unopturl) + "\" " +
            "query_args=\"" + XMLUtils.escapeXml(query_args) + "\" " +
            "pocketpc=\"" + isPocketPC + "\" " +
            "console-remote-debug=\"" + debugconsole +"\" " +
            "console-floating-window=\"" + popupdebugconsole +"\" " +
            "appuid=\"" + System.currentTimeMillis()  +"\" " +
            "windows=\"" + isWin + "\" ");
        if (fileName != null) {
            String optname = fileName.substring(0, fileName.length() - 4) + ".lzo";
            buffer.append(
                "opt-exists=\"" + new File(optname).exists() + "\" ");
        }
        buffer.append(
            ">\n");

        // this lists all the query args
        for (Enumeration<?> e = req.getParameterNames(); e.hasMoreElements(); ) {
            String name = (String)e.nextElement();
            String value = req.getParameter(name);
            buffer.append("<param name=\"" + XMLUtils.escapeXml(name) + "\" ");
            buffer.append("value=\"" + XMLUtils.escapeXml(value) + "\"/>\n");
        }

        // And this unpacks the lzoptions, so the dev-console can see
        // them as if they were query args
        String lzoptions = req.getParameter("lzoptions");
        if (lzoptions != null) {
            HashMap<String, String> optionsmap = LZHttpUtils.getLzRequestOptions(lzoptions);
            for (Map.Entry<String, String> pairs : optionsmap.entrySet()) {
                String key = pairs.getKey();
                String vals = pairs.getValue();
                buffer.append("<param name=\"" + XMLUtils.escapeXml(key) + "\" ");
                buffer.append("value=\"" + XMLUtils.escapeXml(vals) + "\"/>\n");                
            }
        }



        buffer.append("</request>");

        String infoXML = "";
        if (fileName != null) {
            boolean isOpt = fileName.endsWith(".lzo");
            if (isOpt) {
                infoXML = getInfoXML(fileName, props);
            } else {
                infoXML = getCompilationManager().getInfoXML(fileName, props);
            }
        }

        buffer.append(infoXML);
        
        // Encode the <request> and <info> block we just created as URL-encoded
        // element named console_appinfo. This will be used by the SOLO dev console
        // app, to parse out when it runs. 
        String console_appinfo = URLEncoder.encode("<data>"+buffer.toString()+"</data>", "UTF-8");
        buffer.append("<console_appinfo>"+console_appinfo+"</console_appinfo>\n");

        return buffer.toString();
    }

    /**
     * @return info XML string for .lzo files
     */
    private static String getInfoXML(String fileName, Properties props)
        throws IOException
    {
        String enc = props.getProperty(LZHttpUtils.CONTENT_ENCODING);
        String zippedSize = null;
        String unzippedSize = null;
        String size = null;
        
        try {
            unzippedSize = "" + FileUtils.fileSize(new File(fileName));
        } catch (FileNotFoundException e) {
            mLogger.error(e.getMessage());
        }
        
        {
            String gzName = fileName + ".gz";
            File f = new File(gzName);
            // Handle case where .lzo exists but not .gz yet or 
            // gz out of date
            File nongz = new File(fileName);
            if (nongz.exists()) {
                if (!f.exists() || f.lastModified() < nongz.lastModified()) {
                    FileUtils.encode(nongz, f, "gzip");
                    f = new File(gzName);
                }
            }
            try {
                zippedSize = "" + FileUtils.fileSize(f);
            } catch (FileNotFoundException e) {
                mLogger.error(e.getMessage());
            }
        }
        
        size = ("gzip".equals(enc)) ? zippedSize : unzippedSize;
        
        return "<info size=\"" + size +
            "\" zipped-size=\"" + zippedSize +
            "\" unzipped-size=\"" + unzippedSize +
            "\" encoding=\"" + enc +
            "\" />";
    }

    @Override
    protected void handleCompilationError(CompilationError e,
                                          HttpServletRequest req,
                                          HttpServletResponse res)
        throws IOException
    {
        respondCompilationError(e, req, res);
    }

    public static void respondCompilationError(CompilationError e,
                                               HttpServletRequest req,
                                               HttpServletResponse res)
        throws IOException
    {
        res.setContentType("text/html");
        ServletOutputStream out = res.getOutputStream();
        String xmlString = 
            "<errors>" +
            getRequestXML(req, null) +
            e.toXML() +
            "</errors>";
        try {
            TransformUtils.applyTransform(sStyleSheetPathname, xmlString, out);
        } catch (Exception ex) {
            // Create a new compilation error which contains the
            // original compiler error plus this error from the XSLT
            List<Exception> elist = new ArrayList<Exception>();
            elist.add(ex);
            e.attachErrors(elist);
            reportTransformException(req, sStyleSheetPathname, e, out);
        }
    }
    
    /**
     * Writes the canvas html.  The canvas is the area in which the
     * Laszlo application is rendered.
     * @param out <tt>ServletOutputStream</tt> to write on
     * @param req request to retrieve scheme, server name, server port and
     * requested url
     * @param canvas the canvas for the given request
     */
    private void writeCanvas(ServletOutputStream out, HttpServletRequest req, 
                             Canvas canvas, String fileName)
        throws IOException 
    {
        String xmlString = canvas.getXML(getRequestXML(req, fileName));
        mLogger.debug(xmlString);
        Properties properties = new Properties();
        try {
            TransformUtils.applyTransform(sStyleSheetPathname, properties,
                                          xmlString, out);
        } catch (Exception e) {
            reportTransformException(req, sStyleSheetPathname, e, out);
        }
    }
    
    /** Report the exception to output as an internal server error,
     * rendered in HTML, with instructions for bug reporting and with
     * a solution message if one is available.
     */
    protected static void reportTransformException(
        HttpServletRequest req,
        String styleSheetPathname,
        Exception e,
        OutputStream out)
        throws IOException
    {
        // Note that the following doesn't quote HTML in the filename,
        // message, and stacktrace.  This is to make it as likely as
        // possible that this will succeed, since it's typically
        // called when the system is having trouble calling external
        // functions or libraries.  The failure mode is that under
        // rare cases the display could be messed up, but it will
        // still have enough information and the page source will have
        // everything.
        
        // Special solution message if the error contains this string:
        final String XALAN_ERR_MSG =
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="output format must have a '{http://xml.apache.org/xalan}content-handler' property"
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                ResponderAPP_CONSOLE.class.getName(),"051018-270")
;
        
        Writer writer = new OutputStreamWriter(out);
        try {
            String title = "500 Internal Server Error";
            writer.write("<html><title>");
            writer.write(title);
            writer.write("</title>");
            writer.write("<style type='text/css'>");
            writer.write("table th {text-align: right; vertical-align: top; padding-right: 10pt}");
            writer.write("</style>");
            writer.write("</head><body><h1>");
            writer.write(title);
            writer.write("</h1>");
            writer.write("<p>An internal server error occurred. ");
            // Use toString() instead of getMessage(), since for chained
            // exceptions it's possible for the original message to
            // get buried where the latter doesn't retrieve it.
            if (e.toString().indexOf(XALAN_ERR_MSG) >= 0) {
                writer.write("If you are running Tomcat, please verify that ");
                writer.write("that the <code>xalan.jar</code> file from the ");
                writer.write("LPS distribution has been installed in the ");
                writer.write("Tomcat <code>/common/endorsed</code> directory. ");
                writer.write("Otherwise, please check the ");
                writer.write("<a href='http://www.openlaszlo.org/lps-latest/docs/deployers/'>");
                writer.write("OpenLaszlo System Administrator's Guide</a> for additional ");
                writer.write("instructions.  If that does not work, please ");
            } else {
                writer.write("Please ");
            }
            writer.write("file a bug report containing a copy of this web page and a ");
            writer.write("description of your server environment (operating ");
            writer.write("system, JRE version, servlet container and ");
            writer.write("version) at ");
            writer.write("<a href='http://jira.openlaszlo.org/'>");
            writer.write("OpenLaszlo Jira</a>.</p>");
            writer.write("<hr /><table border='0'>");
            // TODO [2004-04-29 ows]: Eric says the following LPS call
            // should work, but it returns an empty string
            //writer.write("<tr><th>Server Info</th>");
            //writer.write("<td>" + LPS.getInfo(req, mContext, "lps-server-info") + "</td></tr>");
            writer.write("<tr><th>Version</th>");
            writer.write("<td>" + LPS.getShortVersion() + "</td></tr>");
            writer.write("<tr><th>Build</th>");
            writer.write("<td>" + LPS.getBuildDate() + "</td></tr>");
            writer.write("<tr><th>Stylesheet</th>");
            writer.write("<td>" + new File(styleSheetPathname).getName() + "</td></tr>");
            writer.write("<tr><th>Message</th>");
            writer.write("<td>" + e.getMessage() + "</td></tr>");
            writer.write("</table><pre>");
            writer.flush();
            PrintStream ps = new PrintStream(out);
            try {
                e.printStackTrace(ps);
            } finally {
                ps.flush();
            }
            writer.write("</pre></body></html>");
        } finally {
            writer.close();
        }
    }
    
    /** 
     * Return all query args except for "lzt"
     */
    private static String getQueryArgs(HttpServletRequest req) throws IOException {
        StringBuilder query = new StringBuilder();
        Enumeration<?> e = req.getParameterNames();
        while (e.hasMoreElements()) {
            String name = (String)e.nextElement();
            String val = req.getParameter(name);
            if (!name.equals("lzt")) {
                query.append("&"+name+"="+URLEncoder.encode(val, "UTF-8"));
            }
        }
        return query.toString();
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.HTML;
    }



}
