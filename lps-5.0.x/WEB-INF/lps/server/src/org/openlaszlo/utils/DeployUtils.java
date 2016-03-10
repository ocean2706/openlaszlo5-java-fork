/******************************************************************************
 * DeploySOLODHTML.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2008-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.servlet.jsp.JspWriter;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class DeployUtils {

    public static void listFiles(ArrayList<String> fnames, File dir) {
        if (dir.isDirectory()) {
            if (!(dir.getName().startsWith(".svn"))) {
                String[] children = dir.list();
                for (int i=0; i<children.length; i++) {
                    listFiles(fnames, new File(dir, children[i]));
                }
            }
        } else {
            fnames.add(dir.getPath());
            //System.out.println("adding "+dir.getPath());
        }
    }


    public static void listFilesAndDirs(File appdir, String prefix, File dir, JspWriter out, String padding) throws IOException {
        int prefixlength = prefix.length();
        String dirname = dir.getPath();
        String trimmed = dir.isDirectory() ? dirname.substring(prefixlength) : dirname.substring(prefixlength+1);
        boolean systemfile = dirname.startsWith(appdir.getPath()+"/lps");

        if (dir.isDirectory()) {
            if (!(dir.getName().startsWith(".svn"))) {
                String fontcolor = systemfile ? "gray" : "green";
                String cbox = systemfile ? "&nbsp;&nbsp;&nbsp;&nbsp;" : ("<input type=\"checkbox\" " +
                                                                         "onclick=\"toggleLineThrough(this) \" "+
                                                                         "name=\"excludes\" value=\""+ dirname +"\">");
                out.println("<span>");
                out.println("<br>"+cbox+"<font color="+fontcolor+"><tt>"+padding+ trimmed+"</tt></font>");
                String[] children = dir.list();
                for (int i=0; i<children.length; i++) {
                    listFilesAndDirs(appdir, prefix+ trimmed, new File(dir, children[i]), out, "&nbsp;&nbsp;&nbsp;&nbsp;" + padding );
                }
                out.println("</span>");
            }
        } else {
            String fontcolor = systemfile ? "gray" : "black";
            String cbox = systemfile ? "&nbsp;&nbsp;&nbsp;&nbsp;" : "<input type=\"checkbox\" name=\"excludes\" "+
                "onclick=\"toggleLineThrough(this) \" "+
                "value=\""+ dirname +"\">";
            out.println("<span>");
            out.println("<br>"+cbox + "<font color="+fontcolor+"><tt>"+padding+trimmed+"</tt></font>");
            out.println("</span>");
        }

    }



    public static void copyByteArrayToZipFile (String prefix,
                                               ZipOutputStream zout,
                                               byte lbytes[],
                                               String dstfile,
                                               Set<String> zipped)
      throws IOException
    {
        String dstfixed = fixSlashes(prefix + dstfile);
        zout.putNextEntry(new ZipEntry(dstfixed));
        zout.write(lbytes, 0, lbytes.length);
        zout.closeEntry();
        zipped.add(dstfixed);
    }

    public static void copyFileToZipFile (String prefix,
                                          ZipOutputStream zout,
                                          String srcfile,
                                          String dstfile,
                                          Set<String> zipped)
      throws IOException, FileNotFoundException {
        String dstfixed = fixSlashes(prefix + dstfile);
        if (zipped.contains(dstfixed)) {
            return;
        }
        FileInputStream in = new FileInputStream(srcfile);
        // Add ZIP entry to output stream.
        zout.putNextEntry(new ZipEntry(dstfixed));
        // Transfer bytes from the file to the ZIP file
        FileUtils.send(in, zout, 1024);
        // Complete the entry
        zout.closeEntry();
        in.close();
        zipped.add(dstfixed);
    }


    public static String fixSlashes (String path) {
        return(path.replace('\\', '/'));
    }



    public static void deleteDirectoryFiles(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            for (int i=0; i<children.length; i++) {
                File f = children[i];
                if (f.isFile()) {
                    f.delete();
                }
            }
        }
    }


    public static Element getChild(Element elt, String name) {
        NodeList elts = elt.getChildNodes();
        for (int i=0; i < elts.getLength(); i++) {
            Node child = elts.item(i);
            if (child instanceof Element && ((Element)child).getTagName().equals(name)) {
                return (Element) child;
            }
        }
        return null;
    }

    public static Element parse(String content) throws IOException {
        try {
            // Create a DOM builder and parse the fragment
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            Document d = factory.newDocumentBuilder().parse( new
                                                             org.xml.sax.InputSource(new StringReader(content)) );

            return d.getDocumentElement();

        } catch (java.io.IOException e) {
        } catch (javax.xml.parsers.ParserConfigurationException e) {
        } catch (org.xml.sax.SAXException e) {
        }
        return null;
    }

    /* returns zipfile name
     * @param prefix A directory name prefix to put all files in
     * @param excludes a List of filenames or directories to exclude. May be null.
     * @param runtime a supported runtime name : dhtml, swf10, etc
     * @param zout Zip file output stream
     * @param basedir the top level directory of the LPS server
     * @param appdir  the  directory containing the application
     * @param out warnings and messages to the user use this output console stream
     * @param skipfiles optional set of files to leave out of archive
     * @param wrapper the HTML wrapper file (will be typically written to index.html)
     * @param widgetType one of osx,w3c,opera,android
     * @param appUrl used in widget template file
     * @param title  used in widget template file
     * @param appheight used in widget template file
     * @param appwidth used in widget template file
     */
    public static void buildZipFile(String prefix, String runtime, ZipOutputStream zout, File basedir, File appdir,
                                    PrintWriter out, HashMap<String, ?> skipfiles,
                                    String wrapper, String widgetType,
                                    String appUrl, String title, String appheight, String appwidth)
      throws IOException {

        int maxZipFileSize = 64000000; // 64MB max
        String htmlfile = "";

        // Keep track of which files we have output to the zip archive, so we don't
        // write any duplicate entries.
        HashSet<String> zippedfiles = new HashSet<String>();

        // These are the files to include in the ZIP file
        ArrayList<String> filenames = new ArrayList<String>();
        // LPS includes, (originally copied from /lps/includes/*)
        filenames.add("lps/includes/embed-compressed.js");
        filenames.add("lps/includes/blank.gif");
        filenames.add("lps/includes/spinner.gif");
        filenames.add("lps/includes/excanvas.js");
        filenames.add("lps/includes/json2.js");
        filenames.add("lps/includes/iframestub.js");
        filenames.add("lps/includes/iframemanager.js");
        filenames.add("lps/includes/rtemanager.js");
        filenames.add("lps/includes/rtewrapper.html");
        filenames.add("lps/includes/flash.js"); // only needed for swf
        filenames.add("lps/includes/laszlo-debugger.css");
        filenames.add("lps/includes/laszlo-debugger.html");

        ArrayList<String> appfiles = new ArrayList<String>();
        listFiles(appfiles, appdir);

        try {

            ////////////////
            // Create wrapper .html file, we make a byte array from
            // lzhistory wrapper text, and write it to the zip archive.
            //
            //htmlfile = new File(appUrl).getName()+".html";
            htmlfile = "index.html";

            byte lbytes[] = wrapper.getBytes();
            //Write out a copy of the lzhistory wrapper as appname.lzx.html
            copyByteArrayToZipFile(prefix, zout, lbytes, htmlfile, zippedfiles);
            ////////////////

            ////////////////
            // Write the widget config.xml file

            // This is the default, if no template matches widgetType
            if (widgetType == null) {
                widgetType = "jil";
            }
            File template = new File(basedir + "/" + "lps/admin/widget-templates/" + "config."+widgetType+".xml");

            String configXML = FileUtils.readFileString(template);

            // We substitute for these vars

            configXML = configXML.replaceAll("%APPURL%", appUrl);
            configXML = configXML.replaceAll("%APPTITLE%", title);
            configXML = configXML.replaceAll("%APPHEIGHT%", appheight);
            configXML = configXML.replaceAll("%APPWIDTH%", appwidth);

            String configfilename = "config.xml";

            // Special case for OSX Dashboard Widgets
            if ("osx".equals(widgetType)) {
                configfilename = "Info.plist";
                copyFileToZipFile(prefix, zout, basedir + "/" + "lps/admin/widget-icon.png", "Default.png", zippedfiles);
                copyFileToZipFile(prefix, zout, basedir + "/" + "lps/admin/widget-icon.png", "Icon.png", zippedfiles);

            }

            copyByteArrayToZipFile(prefix, zout, configXML.getBytes(), configfilename, zippedfiles);
            ////////////////

            // Copy widget icon file
            copyFileToZipFile(prefix, zout, basedir + "/" + "lps/admin/widget-icon.png", "widget-icon.png", zippedfiles);

            // Compress the include files
            for (int i=0; i<filenames.size(); i++) {
                String srcfile = basedir + "/" + filenames.get(i);
                // Add ZIP entry to output stream.
                String dstfile = filenames.get(i);
                copyFileToZipFile(prefix, zout, srcfile, dstfile, zippedfiles);
            }

            if (runtime.equals("dhtml") || runtime.equals("mobile")) {
                // special case for IE7, need to copy lps/includes/blank.gif to lps/resources/lps/includes/blank.gif
                String srcfile = basedir + "/" + "lps/includes/blank.gif";
                String dstfile = "lps/resources/lps/includes/blank.gif";
                copyFileToZipFile(prefix, zout, srcfile, dstfile, zippedfiles);

                // Copy the DHTML LFC to lps/includes/LFC-dhtml.js
                ArrayList<String> lfcfiles = new ArrayList<String>();
                listFiles(lfcfiles, new File(basedir + "/lps/includes/lfc"));
                for (int i=0; i<lfcfiles.size(); i++) {
                    String fname = lfcfiles.get(i);
                    // exclude the other LFC's from other runtimes
                    if (!fname.matches(".*LFC"+runtime+".*.js")) { continue; }
                    String stripped = fname.substring(basedir.getCanonicalPath().length()+1);
                    copyFileToZipFile(prefix, zout, fname, stripped, zippedfiles);
                }
            }
            // track how big the file is, check that we don't write more than some limit
            int contentSize = 0;

            // Compress the app files
            for (int i=0; i<appfiles.size(); i++) {
                String srcname = appfiles.get(i);
                String dstname = srcname.substring(appdir.getPath().length()+1);
                if (skipfiles != null && !skipFile(skipfiles,srcname)) {
                    // Add ZIP entry to output stream.
                    copyFileToZipFile(prefix, zout, srcname, dstname, zippedfiles);
                    if (contentSize > maxZipFileSize) {
                        throw new IOException("file length exceeds max of "+ (maxZipFileSize/1000000) +"MB");
                    }
                }
            }

            // Complete the ZIP file
            zout.close();
        } catch (java.io.IOException e) {
            out.println("<font color=\"red\">Error generating zip file: "+e.toString() + " </h3>");
        }
    }

    // Decide whether to skip including a file;
    // Return true if any key in skipfiles is a prefix of srcname
    public static boolean skipFile(HashMap<String, ?> skipfiles, String srcname) {
        for (String path : skipfiles.keySet()) {
            if (srcname.startsWith(path)) {
                return true;
            }
        }
        return false;
    }


    public static void copyInputStream(InputStream in, OutputStream out)
      throws IOException
    {
        FileUtils.send(in, out, 1024);

        in.close();
        out.close();
    }

    public static  void unpackZipfile(String stripprefix, String zipfile, String outdir, PrintWriter out)
      throws java.io.IOException {
        try {
            ZipFile zipFile = new ZipFile(zipfile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while(entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                // Strip off prefix
                entryName = entryName.substring(stripprefix.length());

                if(entry.isDirectory()) {
                    // Assume directories are stored parents first then children.
                    String dir = outdir + File.separator + entryName;
                    (new File(dir)).mkdir();
                    continue;
                }

                String outfile = outdir + File.separator + entryName;
                File outf = new File(outfile);
                if (outf.getParent() != null) {
                    outf.getParentFile().mkdirs();
                }
                //        out.println("extracting to "+outfile+"<br>");
                copyInputStream(zipFile.getInputStream(entry), new BufferedOutputStream(new FileOutputStream(outfile)));
            }

            zipFile.close();
        } catch (IOException ioe) {
            out.println("Unhandled exception:" + ioe + ", "+ioe.getMessage());
            ioe.printStackTrace();
            return;
        }
    }

    public static String adjustDHTMLWrapper(String wrapper, String lpspath, String runtime, String debug)
    {
        // We need to adjust the  wrapper, to make the path to lps/includes/dhtml-embed.js
        // be relative rather than absolute.

        // remove the servlet prefix and leading slash
        //  src="/legals/lps/includes/embed-dhtml.js"
        wrapper = wrapper.replaceAll(lpspath + "/", "");

        // Replace object file URL with SOLO filename
        // Lz.dhtmlEmbedLFC({url: 'animation.lzx?lzt=object&lzproxied=false&lzr=dhtml'
        // Lz.dhtmlEmbed({url: 'animation.lzx?lzt=object&lzr=dhtml&_canvas_debug=false',
        //                 bgcolor: '#eaeaea', width: '800', height: '300', id: 'lzapp'});

        //wrapper = wrapper.replaceAll("[.]lzx[?]lzt=object.*'", ".lzx.js'");
        wrapper = wrapper.replaceAll("[.]lzx[?]lzt=object.*?'", ".lzx.js'");

        // Replace serverroot and lfcurl:
        // lz.embed.dhtml({url: 'html.lzx?lzt=object&lzr=dhtml', lfcurl: '/trunk2/lps/includes/lfc/LFCdhtml.js', serverroot: '/trunk2/', bgcolor: '#ffffff', width: '100%', height: '100%', id: 'lzapp', accessible: 'false', cancelmousewheel: false, cancelkeyboardcontrol: false, skipchromeinstall: false, usemastersprite: false, approot: ''});
        if (debug.equals("true")) {
            wrapper = wrapper.replaceFirst("lfcurl:(.*?),",
                                           "lfcurl: 'lps/includes/lfc/LFC"+runtime+"-debug.js',");
        } else {
            wrapper = wrapper.replaceFirst("lfcurl:(.*?),",
                                           "lfcurl: 'lps/includes/lfc/LFC"+runtime+".js',");
        }
        wrapper = wrapper.replaceFirst("serverroot:(.*?),",
                                       "serverroot: 'lps/resources/',");
        return wrapper;
    }

}
