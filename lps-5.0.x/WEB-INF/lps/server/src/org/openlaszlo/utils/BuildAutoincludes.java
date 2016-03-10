/******************************************************************************
 * BuildAutoincludes.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2008-2009, 2011 Laszlo Systems, Inc.  All Rights Reserved.  *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.parsers.DocumentBuilderFactory;

import org.openlaszlo.server.LPS;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


public class BuildAutoincludes {

    /* Parses LZX files, and lists all class or interface definitions in the file. Output
     is in the format used by the lzx-autoincludes.properties file, e.g.,

     alert: lz/alert.lzx
     axis: charts/common/axis.lzx
     axisstyle: charts/styles/chartstyle.lzx
     barchart: charts/barchart/barchart.lzx
     ...
     ...


    */

    private static String[] USAGE = {
        "Usage: buildautoincludes [OPTION]... [FILE...]",
        "",
        "If no input filenames are supplied on command line, they are taken from stdin",
        "Options:",
        "--output pathname",
        "  The name of the output file to write.",
        "--help",
        "  Prints this message.",

    };

    public static void usage () {
        System.err.println("Usage: buildautoincludes [OPTION]... file...");
        System.err.println("Try `buildautoincludes --help' for more information.");
        System.exit(1);
    }

    public static void main(String args[])
      throws IOException
    {
        File outfile = new File(LPS.HOME() + File.separator + "WEB-INF" +
                                File.separator + "lps" +
                                File.separator + "misc" +
                                File.separator + "lzx-autoincludes.properties");

        List<String> files = new ArrayList<String>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].intern();
            if (arg.startsWith("-")) {
                if (arg == "--output") {
                    outfile = new File(args[++i]);
                } else if (arg == "--help") {
                    for (int j = 0; j < USAGE.length; j++) {
                        System.err.println(USAGE[j]);
                    }
                    System.exit(0);
                } else {
                    usage();
                }
                continue;
            }
            files.add(args[i]);
        }

        if (files.size() == 0) {
            // get files from stdin
            Scanner sc  = new Scanner (System.in);
            while (sc.hasNextLine()) {
                String filename = sc.nextLine();
                files.add(filename);
            }
        }

        SimpleDateFormat format =
            new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy");

        String datestring = format.format(new Date());

        // Open the file for over-writing and header
        PrintWriter out = new PrintWriter(new  FileWriter(outfile, false));
        out.println("# DO NOT EDIT THIS FILE");
        out.println("# This file is generated by the `ant autoincludes` task in the lps/components direcory");
        out.println("# Copyright "+ datestring + " Laszlo Systems, Inc.  All Rights Reserved.  Use is subject to license terms.");
        out.close();

        try {
            for (int i = 0; i < files.size(); i++) {
                System.err.println("scanning file "+files.get(i));
                checkFile(new File(files.get(i)));
            }

            // open for appending
            out = new PrintWriter(new FileWriter(outfile, true));
            ArrayList<String> tagnames = new ArrayList<String>(tags.keySet());
            Collections.sort(tagnames);
 
            for (int i = 0; i < tagnames.size(); i++) {
                String tag = tagnames.get(i);
                out.println(tag+": "+tags.get(tag));
                System.out.println(tag+": "+tags.get(tag));
            }


        } catch (IOException e) {
            System.err.println(e);
            System.exit(1);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    static Map<String, String> tags = new HashMap<String, String>();

    // Find all the <class> or <interface> tags in the file, and store
    // the tag names they define
    static void checkFile(File infile) throws IOException {
        // append to output file

        String filename = infile.getPath();
        if (File.separatorChar != '/') {
            filename = filename.replace(File.separatorChar, '/');
        }
        if (filename.startsWith("./")) {
            filename = filename.substring(2);
        }

        /* Create a DOM for the LZX file  */
        try {
            Document root = parse(infile);
            NodeList classes = root.getElementsByTagName("class");
            listDefs(classes, filename);
            NodeList interfaces = root.getElementsByTagName("interface");
            listDefs(interfaces, filename);
            NodeList mixins = root.getElementsByTagName("mixin");
            listDefs(mixins, filename);
        } catch (Exception e) {
            System.err.println("could not parse lzx file "+infile);
        }

    }
    
    static void listDefs(NodeList nlist, String filename) {
        for (int i = 0; i < nlist.getLength(); i++) {
            Element def = (Element) nlist.item(i);
            String defname = def.getAttribute("name");
            if (defname == null || defname.length() == 0) {
                continue;
            }
            if (tags.get(defname) != null) {
                System.err.println("Duplicate tags: "+defname+" in "+filename+", "+tags.get(defname));
            }
            // Don't include classes which start with "_"
            if (!defname.startsWith("_")) {
                tags.put(defname, filename);
            }
        }
    }

    static Element getChild(Element elt, String name) {
        NodeList elts = elt.getChildNodes();
        for (int i=0; i < elts.getLength(); i++) {
            Node child = elts.item(i);
            if (child instanceof Element && ((Element)child).getTagName().equals(name)) {
                return (Element) child;
            }
        }
        return null;
    }

    static Document parse(File content) throws IOException {
        try {
            // Create a DOM builder and parse the fragment
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            Document d = factory.newDocumentBuilder().parse( content );

            return d;

        } catch (java.io.IOException e) {
        } catch (javax.xml.parsers.ParserConfigurationException e) {
        } catch (org.xml.sax.SAXException e) {
        }
        return null;
    }
}

