/* ****************************************************************************
 * PerfTest.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test.xmlrpc;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

class PerfDataParser
{
    DocumentBuilder mBuilder;

    public PerfDataParser() throws ParserConfigurationException
    {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        mBuilder = factory.newDocumentBuilder();
    }
    
    
    public Map<String, Map<String, String>> parse(String file) 
        throws IOException, SAXException {

        Map<String, Map<String, String>> map = new HashMap<String, Map<String, String>>();

        Element root = mBuilder.parse(new InputSource(new FileReader(file))).getDocumentElement();
        NodeList children = root.getChildNodes();
        for (int i=0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Map<String, String> item = new HashMap<String, String>();
            NamedNodeMap attributes = ((Element)node).getAttributes();
            for (int j=0; j < attributes.getLength(); j++) { 
                Attr attr = (Attr)attributes.item(j);
                item.put(attr.getName(), attr.getValue());
            }
            map.put(item.get("id"), item);
        }

        return map;
    }
}


public class PerfTest
{
    static String PERF_DATA_DIR = "/home/pkang/perforce/qa/testharness/docroot/perf-data";

    static Map<Integer, Map<String, Map<String, String>>> addrMap = new HashMap<Integer, Map<String, Map<String, String>>>();

    static {
        try {
            PerfDataParser p = new PerfDataParser();
            addrMap.put(0, p.parse(PERF_DATA_DIR + "/0k.xml"));
            addrMap.put(1, p.parse(PERF_DATA_DIR + "/1k.xml"));
            addrMap.put(10, p.parse(PERF_DATA_DIR + "/10k.xml"));
            addrMap.put(25, p.parse(PERF_DATA_DIR + "/25k.xml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, Map<String, String>> getAddresses(int k) {
        return addrMap.get(k);
    }
}
