/* *****************************************************************************
 * XMLUtils.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2006-2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.js2doc;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.openlaszlo.sc.parser.ASTIdentifier;
import org.openlaszlo.sc.parser.ASTLiteral;
import org.openlaszlo.sc.parser.SimpleNode;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

public class JS2DocUtils {

    private static final Logger logger = Logger.getLogger("org.openlaszlo.js2doc");

    private static final String XALAN_INDENT_AMOUNT =
        "{http://xml.apache.org/xslt}indent-amount";

    private static DocumentBuilderFactory documentBuilderFactory;
    private static DocumentBuilder documentBuilder;
    private static TransformerFactory transformerFactory;

    @SuppressWarnings("serial")
    public static class InternalError extends RuntimeException {

        public SimpleNode node;

        /** Constructs an instance.
         * @param message a string
         * @param node offending parse node
         */
        public InternalError(String message, SimpleNode node) {
            super(message);
            this.node = node;
        }

        /** Constructs an instance.
         * @param e an exception
         */
        public InternalError(Exception e) {
            super(e);
        }

        /** Constructs an instance.
         * @param message a string
         * @param e an exception
         */
        public InternalError(String message, Exception e) {
            super(message, e);
        }
    }

    private static DocumentBuilderFactory getDocumentBuilderFactory() {
        DocumentBuilderFactory factory = documentBuilderFactory;
        if (factory == null) {
            factory = DocumentBuilderFactory.newInstance();
            factory.setValidating(false);
            documentBuilderFactory = factory;
        }
        return factory;
    }

    private static DocumentBuilder getDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilder builder = documentBuilder;
        if (builder == null) {
            builder = getDocumentBuilderFactory().newDocumentBuilder();
            documentBuilder = builder;
        } else {
            try {
                builder.reset();
            } catch (UnsupportedOperationException e) {
            }
        }
        return builder;
    }

    private static TransformerFactory getTransformerFactory() {
        TransformerFactory factory = transformerFactory;
        if (factory == null) {
            factory = TransformerFactory.newInstance();
            transformerFactory = factory;
        }
        return factory;
    }

    public static String xmlToString(org.w3c.dom.Node node) throws RuntimeException {
        String stringResult = null;
        try {
            Source source = new DOMSource(node);
            StringWriter stringWriter = new StringWriter();
            Result result = new StreamResult(stringWriter);
            TransformerFactory factory = getTransformerFactory();
            Transformer transformer = factory.newTransformer();
            transformer.transform(source, result);
            stringResult = stringWriter.getBuffer().toString();
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to instantiate XML Transformer");
        } catch (TransformerException e) {
            e.printStackTrace();
            throw new RuntimeException("Error in XML Transformer");
        }
        return stringResult;
    }

    private static void setReadable(Transformer xformer) {
        xformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        xformer.setOutputProperty(OutputKeys.INDENT, "yes");
        xformer.setOutputProperty(XALAN_INDENT_AMOUNT, "2");
    }

    public static void xmlToFile(org.w3c.dom.Node node, String filename, boolean readable) throws RuntimeException {
        try {
            // Prepare the DOM document for writing
            Source source = new DOMSource(node);

            // Prepare the output file
            File file = new File(filename);
            Result result = new StreamResult(file);

            // Write the DOM document to the file
            Transformer xformer = getTransformerFactory().newTransformer();
            setReadable(xformer);
            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            e.printStackTrace();
            throw new RuntimeException("Unable to instantiate XML Transformer");
        } catch (TransformerException e) {
            e.printStackTrace();
            throw new RuntimeException("Error in XML Transformer");
        }
    }


    public static void setXMLContent(org.w3c.dom.Element node, String content) {

        // Wrap the fragment in an arbitrary element
        content = "<fragment>"+content+"</fragment>";

        try {
            // Create a DOM builder and parse the fragment
            DocumentBuilder builder = getDocumentBuilder();
            Document d = builder.parse( new org.xml.sax.InputSource(new StringReader(content)) );

            // Import the nodes of the new document into doc so that they
            // will be compatible with doc
            Document doc = node.getOwnerDocument();
            org.w3c.dom.Node fragNode = doc.importNode(d.getDocumentElement(), true);

            // Create the document fragment node to hold the new nodes
            DocumentFragment docfrag = doc.createDocumentFragment();

            // Move the nodes into the fragment
            while (fragNode.hasChildNodes()) {
                docfrag.appendChild(fragNode.removeChild(fragNode.getFirstChild()));
            }

            node.appendChild(docfrag);
        } catch (java.io.IOException e) {
            logger.warning("Could not parse comment '" + content + "'");
            e.printStackTrace();
        } catch (javax.xml.parsers.ParserConfigurationException e) {
            logger.warning("Could not parse comment '" + content + "'");
            e.printStackTrace();
        } catch (org.xml.sax.SAXException e) {
            logger.warning("Could not parse comment '" + content + "'");
            e.printStackTrace();
        }
    }

    public static org.w3c.dom.Element findFirstChildElementWithAttribute(org.w3c.dom.Element docNode, String tagName, String attrName, String name) {
        org.w3c.dom.Element foundNode = null;
        org.w3c.dom.NodeList childNodes = docNode.getChildNodes();
        final int n = childNodes.getLength();
        for (int i=0; i<n; i++) {
            org.w3c.dom.Node childNode = childNodes.item(i);
            if (childNode instanceof org.w3c.dom.Element && childNode.getNodeName().equals(tagName)) {
                String eName = ((org.w3c.dom.Element) childNode).getAttribute(attrName);
                if (eName != null && eName.equals(name)) {
                    foundNode = (org.w3c.dom.Element) childNode;
                    break;
                }
            }
        }
        return foundNode;
    }

    public static org.w3c.dom.Node firstChildNodeWithName(org.w3c.dom.Node node, String name) {
        org.w3c.dom.Node foundNode = null;
        org.w3c.dom.NodeList childNodes = node.getChildNodes();
        final int n = childNodes.getLength();
        for (int i=0; i<n; i++) {
            org.w3c.dom.Node childNode = childNodes.item(i);
            if (childNode.getNodeName().equals(name)) {
                foundNode = childNode;
                break;
            }
        }
        return foundNode;
    }

    static void appendToAttribute(org.w3c.dom.Element node, String attr, String value)
    {
        String oldvalue = node.getAttribute(attr);
        if (oldvalue == null || oldvalue.length() == 0)
            node.setAttribute(attr, value.trim());
        else
            node.setAttribute(attr, oldvalue + " " + value.trim());
    }

    public static void describeConditionalState(ConditionalState state, org.w3c.dom.Element docNode) {
        if (state.inferredValue == ConditionalState.Value.Indeterminate) {
            Set<String> includeSet = new HashSet<String>();
            Set<String> excludeSet = new HashSet<String>();

            state.describeExclusiveConditions(includeSet);

            if (includeSet.isEmpty() == false) {
                // Show the complement (e.g. 'except foo bar')
                // if that list is shorter than the original list.
                Set<String> complement = new HashSet<String>(Main.runtimeOptions);
                complement.removeAll(includeSet);
                if (complement.size() > 0 && complement.size() < includeSet.size()) {
                    docNode.setAttribute("runtimes", "except " + optionsToString(complement));
                } else {
                    docNode.setAttribute("runtimes", optionsToString(includeSet));
                }
            }

            includeSet.clear();

            state.describeIndependentConditions(includeSet, excludeSet);

            if (includeSet.isEmpty() == false) {
                docNode.setAttribute("includebuilds", optionsToString(includeSet));
            }

            if (excludeSet.isEmpty() == false) {
                docNode.setAttribute("excludebuilds", optionsToString(excludeSet));
            }
        }
    }

    static ConditionalState conditionalStateFromElement(org.w3c.dom.Element propertyOwner, Set<String> exclusiveOptions, List<String> independentOptions) {
        String runtimes = propertyOwner.getAttribute("runtimes");
        String includebuilds = propertyOwner.getAttribute("includebuilds");
        String excludebuilds = propertyOwner.getAttribute("excludebuilds");

        ConditionalState newState = new ConditionalState(ConditionalState.Value.True, exclusiveOptions, independentOptions);

        if (runtimes != null) {
            Scanner sc = new Scanner(runtimes);
            while (sc.hasNext()) {
                String option = sc.next();
                newState.addTrueCase(option);
            }
        }
        if (includebuilds != null) {
            Scanner sc = new Scanner(includebuilds);
            while (sc.hasNext()) {
                String option = sc.next();
                newState.addTrueCase(option);
            }
        }
        if (excludebuilds != null) {
            Scanner sc = new Scanner(excludebuilds);
            while (sc.hasNext()) {
                String option = sc.next();
                newState.addFalseCase(option);
            }
        }

        return newState;
    }


    static String derivePropertyID(org.w3c.dom.Node propertyOwner, String propertyName, ConditionalState state) {
        String objectID = null;
        org.w3c.dom.Node parentNode = propertyOwner;
        while (parentNode != null && parentNode instanceof org.w3c.dom.Element) {
            org.w3c.dom.Element ownerBinding = (org.w3c.dom.Element) parentNode;
            if (ownerBinding.hasAttribute("id")) {
                objectID = ownerBinding.getAttribute("id");
                break;
            }
            parentNode = parentNode.getParentNode();
        }
        String classPrefix = (objectID != null) ? (objectID + ".") : "";
        String conditionSuffix = "";
        if (state != null && state.inferredValue == ConditionalState.Value.Indeterminate)
           conditionSuffix = state.toString();
        return classPrefix + propertyName + conditionSuffix;
    }

    public static String optionsToString(Collection<String> options) {
        List<String> c = new ArrayList<String>(options);
        Collections.sort(c);
        String s = "";
        for (String option : c) {
            if (s != "") s += " ";
            s += option;
        }
        return s;
    }

    static String nodeLocationInfo(SimpleNode parseNode) {
        return parseNode.getFilename() + ": " + parseNode.getLineNumber() + ", " + parseNode.getColumnNumber();
    }

    static String nodeDescription(SimpleNode parseNode) {
        if (parseNode instanceof ASTIdentifier) {
            return ((ASTIdentifier) parseNode).getName();
        } else if (parseNode instanceof ASTLiteral) {
            return String.valueOf(((ASTLiteral) parseNode).getValue());
        } else {
            return "";
        }
    }

    public static void debugPrintNode(SimpleNode parseNode) {
        debugPrintNode(parseNode, 0, 0);
    }

    static void debugPrintNode(SimpleNode parseNode, int level, int index) {
        System.err.println("node: (" + level + ">>" + index + ") " + parseNode.getClass().getName() + " "
                            + nodeDescription(parseNode)
                            + " (" + nodeLocationInfo(parseNode) + ")");
        SimpleNode[] children = parseNode.getChildren();
        for (int i = 0; i < children.length; i++) {
            debugPrintNode(children[i], level + 1, i);
        }
    }

    public static void checkChildrenLowerBounds(SimpleNode node, int min, int expectedMax, String methodName) {
        SimpleNode[] children = node.getChildren();

        if (children.length < min) {
            logger.throwing("JS2Doc", methodName, new InternalError("Too few child nodes in " + node.getClass().getName(), node));
        } else if (expectedMax > 0 && children.length > expectedMax) {
            logger.fine("Unexpected number of child nodes in " + node.getClass().getName());
        }
    }

}
