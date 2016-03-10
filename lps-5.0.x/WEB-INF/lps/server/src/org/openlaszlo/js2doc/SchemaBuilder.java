/* *****************************************************************************
 * SchemaBuilder.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.js2doc;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import javax.xml.namespace.QName;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openlaszlo.compiler.ViewSchema;
import org.openlaszlo.js2doc.JS2DocUtils.InternalError;
import org.openlaszlo.server.LPS;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

// TODO: handle initargs, e.g. this from canvas:
//  * @initarg Boolean accessible: Specifies if this application is intended to be accessible
//  * @initarg Boolean debug: If true, the application is compiled with debugging enabled.
//
// should appear as:
//  <attribute name="debug" type="boolean" value="false"/>
//  <attribute name="accessible" type="boolean" value="false"/>
//
// Unfortunately, initargs seem 'unstructured', here are various examples:
//   @initarg secureport
//   @initarg deprecated port
//   @initarg Number thickness (swf8 only)
//   @initarg public String resource: A string denoting the library resource to use...
//
// We'd probably need to more sharply define the syntax of @initargs and clean up
// all the usages


// TODO: We are NOT looking for classname.setXXX (setters)
//   or the newer form $lzc$set_apply() from the javadocs.
//   Should we, and indicate them as attributes?

/**
  * Builds a specialized schema for lfc,
  * invoked via the --schema option.
  * This uses the parsed internal XML doc tree and
  * generates the schema in a specialized XML format.
  */
public class SchemaBuilder {

    public static final String INITIAL_COMMENT = "\n\n" +
        "       !!!!!!!!   DO NOT EDIT THIS FILE   !!!!!!!!\n\n" +
        "       This file was created by js2doc.SchemaBuilder.\n" +
        "       Contents can be modified by editing lfc-undeclared.lzx.\n";

    private Element droot;
    private TreeMap<String, JsClass> jsClasses = new TreeMap<String, JsClass>(); // key is name
    private TreeMap<String, JsClass> jsClassesOrdered = new TreeMap<String, JsClass>(); // key is contrived for ordering
    private XPathFactory xpathFactory = XPathFactory.newInstance();
    private XPath xPath = xpathFactory.newXPath();

    private XPathExpressions expressions;

    private static enum XPathExpr {
        /** "tag[@name='modifiers']" */
        TagNameModifiers("tag[@name='modifiers']"),
        /** "/js2doc/property/class" */
        Classes("/js2doc/property/class"),
        /** "doc/tag[@name='lzxname']/text" */
        LZXName("doc/tag[@name='lzxname']/text"),
        /** "/js2doc/unit[id=$unitname]" */
        Unit("/js2doc/unit[id=$unitname]"),
        /** "property/function" */
        ClassMethods("property/function"),
        /** "property/object/property/function" */
        InstanceMethods("property/object/property/function"),
        /** "property[@name='__ivars__']/object/property" */
        InstanceProperties("property[@name='__ivars__']/object/property"),
        /** "property/object/property[((doc/tag[@name='lzxtype']/text) = 'event')]" */
        Events("property/object/property[((doc/tag[@name='lzxtype']/text) = 'event')]"),
        /** "property[doc]" */
        Doc("property[doc]"),
        /** "doc/tag[@name='lzxtype']/text" */
        LZXType("doc/tag[@name='lzxtype']/text"),
        /** "doc/tag[@name='lzxdefault']/text" */
        LZXDefault("doc/tag[@name='lzxdefault']/text"),
        /** "doc/tag[@name='style']/text" */
        Style("doc/tag[@name='style']/text"),
        /** "doc/tag[@name='inherit']/text" */
        Inherit("doc/tag[@name='inherit']/text"),
        /** "(doc/tag[@name='lzxtype']/text) = 'event'" */
        Events2("(doc/tag[@name='lzxtype']/text) = 'event'"),
        /** "function" */
        Function("function"),
        /** "function/parameter" */
        Parameter("function/parameter");

        XPathExpr (String expression) {
            this.expression = expression;
        }
        private final String expression;
    }

    @SuppressWarnings("unused")
    private static final class XPathExpressions {
        private final XPathExpression[] exprs;

        XPathExpressions (XPath xpath) throws XPathExpressionException {
            XPathExpr[] values = XPathExpr.values();
            XPathExpression[] exprs = new XPathExpression[values.length];
            for (int i = 0, len = values.length; i < len; ++i) {
                exprs[i] = xpath.compile(values[i].expression);
            }
            this.exprs = exprs;
        }

        @SuppressWarnings("unchecked")
        <T> T evaluate(XPathExpr expr, Object item, QName returnType)
            throws XPathExpressionException {
            return (T) exprs[expr.ordinal()].evaluate(item, returnType);
        }

        Boolean _boolean(XPathExpr expr, Object item)
            throws XPathExpressionException {
            return evaluate(expr, item, XPathConstants.BOOLEAN);
        }

        Number number(XPathExpr expr, Object item)
            throws XPathExpressionException {
            return evaluate(expr, item, XPathConstants.NUMBER);
        }

        String string(XPathExpr expr, Object item)
            throws XPathExpressionException {
            return evaluate(expr, item, XPathConstants.STRING);
        }

        Node node(XPathExpr expr, Object item)
            throws XPathExpressionException {
            return evaluate(expr, item, XPathConstants.NODE);
        }

        Element element(XPathExpr expr, Object item)
            throws XPathExpressionException {
            return evaluate(expr, item, XPathConstants.NODE);
        }

        NodeList nodelist(XPathExpr expr, Object item)
            throws XPathExpressionException {
            return evaluate(expr, item, XPathConstants.NODESET);
        }
    }

    private Element mergeSchema = null;
    private String mergeName = null;

    /** A class seen in the input */
    public static class JsClass {
        String jsname;
        String access;
        String lzxname;
        String extname;
        // detached!
        Element classnode;
        boolean ismetasym;
    }

    // values of 'kind' argument to addMember()
    public static final String MEMBER_ATTRIBUTE = "attribute";
    public static final String MEMBER_METHOD = "method";
    public static final String MEMBER_EVENT = "event";

    /** A member of a class seen in the input.
     * This may be a method, event or attribute.
     */
    public static class JsMember {
        String name;
        String type = null;         // only used for attribute, otherwise null
        String style = null;         // only used for attribute, otherwise null
        String inherit = null;         // only used for attribute, otherwise null
        String defaultvalue = null; // only used for attribute (but optional), otherwise null
        String enumvalues = null;   // If attribute is enum, this is a '|' separated list
        List<JsParam> params = new ArrayList<JsParam>();
        boolean isfinal;
        boolean isstatic;
    }

    /** A parameter of a method seen in the input.
     */
    public static class JsParam {
        String name;
        String type = null;
    }

    @SuppressWarnings("serial")
    public class SchemaBuilderError extends RuntimeException {
        public SchemaBuilderError(String s) {
            super("Error building schema: " + s);
        }
    }

    /**
     * Initialize the SchemaBuilder using the document root.
     */
    public SchemaBuilder(Element droot) {
        this.droot = droot;
    }

    /**
     * Get the data string associated with a Text element.
     */
    private String getTextData(Element textElement) {
        if (textElement == null) {
            return null;
        }
        Node ch = textElement.getFirstChild();
        if (ch == null || !(ch instanceof Text)) {
            return null;
        } else {
            return ((Text)ch).getData();
        }
    }

    private static final String LZX_CREATED_NAME_PREFIX = "$lfc$";
    private String createLzxName(String jsname) {
        return LZX_CREATED_NAME_PREFIX + jsname;
    }

    private boolean isRealLzxName(String lzxname) {
        return (lzxname != null && (! lzxname.startsWith(LZX_CREATED_NAME_PREFIX)));
    }

    /**
     * Create an return a new child tag beneath an existing node.
     */
    private Element createChild(Node n, String tagname) {
        return (Element)n.appendChild(n.getOwnerDocument().createElement(tagname));
    }

    /**
     * Precompile xpath expressions that are used frequently.
     */
    private void compileXpathExpressions()
        throws XPathExpressionException {
        expressions = new XPathExpressions(xPath);
    }

    /**
     * Create a key that guarantees that superclasses appear first.
     * <pre>
     * class:                        key:
     *  Bar                           "Bar"
     *  Foo extends Bar               "Bar.Foo"
     *  Aha extends Foo extends Bar   "Bar.Foo.Aha"
     * </pre>
     */
    public String getOrderedKey(JsClass j) {
        JsClass sup = (j.extname == null) ? null : jsClasses.get(j.extname);
        if (sup == null) {
            return j.jsname;
        } else {
            return getOrderedKey(sup) + "." + j.jsname;
        }
    }

    public void addSchemaToMerge(String filename, Element merge) {
        this.mergeName = filename;
        this.mergeSchema = merge;
    }

    /**
     * Just pull the Element children.  If there are
     * unexpected other nodes (anything other than
     * whitespace Text nodes and Comment nodes), throw
     * an exception.
     */
    public Element[] getChildElements(Element el) {
        List<Element> list = new ArrayList<Element>();
        NodeList children = el.getChildNodes();
        for (int count=children.getLength(), i=0; i<count; i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                list.add((Element) n);
            } else if (n instanceof org.w3c.dom.Text) {
                String str = ((Text)n).getData();
                if (str.trim().length() > 0) {
                    throw new SchemaBuilderError(position(n) + "unexpected text \"" +
                                                 str + "\" beneath " + el);
                }
            } else if (n instanceof org.w3c.dom.Comment) {
                //ignored
            } else {
                throw new SchemaBuilderError(position(n) + "unexpected node " +
                                             n + "(" + n.getClass().getName() + ") child of " + el);
            }
        }
        return list.toArray(new Element[0]);
    }

    String position(Node n) {
        String pos = mergeName + ": ";
        // TODO: no way to get an input line number from a node?

        String path = showpath(n);
        if (path.length() > 0) {
            path += ": ";
        }
        return pos + path;
    }

    Element getSingleChild(Element el) {
        String tagname = el.getTagName();
        Element[] children = getChildElements(el);
        if (children.length != 1) {
            if (children.length > 1) {
                String got = "";
                for (int i=0; i< children.length; i++) {
                    if (i > 0) {
                        got += ", ";
                    }
                    if (i > 2) {
                        break;
                    }
                    got += children[i];
                }
                throw new SchemaBuilderError(position(el) + "each <" + tagname +
                                             "> may have only one child node, got: " + got);
            } else {
                throw new SchemaBuilderError(position(el) + "each <" + tagname +
                                             "> must have a single child node");
            }
        }
        return children[0];
    }

    Element getMatchingElement(Element el, Element match) {
        String tagname = match.getTagName();
        String name = match.getAttribute("name");
        String xpath = tagname;
        if (name != null) {
            xpath += "[@name = \"" + name + "\"]";
        }

        //System.out.println("xpath=\"" + xpath + "\": matching against " + show(el));
        try {
            return (Element)xPath.evaluate(xpath, el,
                                           XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new InternalError(e);
        }

    }

    String show(Element el) {
        String name = el.getAttribute("name");
        if (name == null) {
            name = "";
        } else {
            name = " name=\"" + name + "\"";
        }
        return "<" + el.getTagName() + name + ">";
    }

    String showpath(Node n) {
        String path = "";
        while (n != null) {
            if (n instanceof Element) {
                Element el = (Element)n;
                String name = el.getAttribute("name");
                if (name == null || name.length() == 0) {
                    name = "";
                } else {
                    name = " \"" + name + "\"";
                }
                path = "/" + el.getTagName() + name + path;
            }
            n = n.getParentNode();
        }
        return path;
    }

    /**
     * Insert the new node under the parent.
     * If ordering under the parent is important (e.g. for <interface> dependencies),
     * that is handled here.
     */
    public void insertChild(Element parent, Element newnode) {
        // The new node is not from the same document from the parent
        // so it needs to be imported.
        Element n = (Element)parent.getOwnerDocument().importNode(newnode, true);
        if (ViewSchema.KIND_INTERFACE.equals(n.getTagName())) {
            String extend = n.getAttribute("extends");

            // TODO: find the best 'sorted' spot, or resort the result.
            //   right now, we put Object at the front and everything else at the end.
            //   This happens to work with what we have in lfc-undefined.lzx, but is fragile.
            //   Should use ClassModel's comparator to sort
            if ("Object".equals(extend)) {
                parent.insertBefore(n, parent.getFirstChild());
            } else {
                parent.appendChild(n);
            }
        } else {
            parent.appendChild(n);
        }
    }

    public void replaceChild(Element parent, Element newnode, Element orignode) {
        Node n = parent.getOwnerDocument().importNode(newnode, true);
        parent.replaceChild(n, orignode);
    }

    public void merge(Element orig, Element merge) {
        Element[] children = getChildElements(merge);
        for (int i=0; i<children.length; i++) {
            Element mergeel = children[i];
            String tagname = mergeel.getTagName();
            if (tagname.equals("insert")) {
                Element[] subchildren = getChildElements(mergeel);
                for (int j=0; j<subchildren.length; j++) {
                    Element sub = subchildren[j];
                    Element origel = getMatchingElement(orig, sub);
                    if (origel == null) {
                        insertChild(orig, sub);
                    } else {
                        throw new SchemaBuilderError(show(sub) + " in <insert> matches an existing element: " + showpath(origel));
                    }
                }
            } else if (tagname.equals("replace")) {
                Element[] subchildren = getChildElements(mergeel);
                for (int j=0; j<subchildren.length; j++) {
                    Element sub = subchildren[j];
                    Element origel = getMatchingElement(orig, sub);
                    if (origel == null) {
                        throw new SchemaBuilderError(show(sub) + " in <replace> does not match an existing element under " + showpath(orig));
                    } else {
                        replaceChild(orig, sub, origel);
                    }
                }
            } else if (tagname.equals("delete")) {
                Element[] subchildren = getChildElements(mergeel);
                for (int j=0; j<subchildren.length; j++) {
                    Element sub = subchildren[j];
                    Element origel = getMatchingElement(orig, sub);
                    if (origel == null) {
                        throw new SchemaBuilderError(show(sub) + " in <delete> does not match an existing element under " + showpath(orig));
                    } else {
                        orig.removeChild(origel);
                    }
                }
            } else {              // merge with existing node, recursively
                Element origel = getMatchingElement(orig, mergeel);
                if (origel == null) {
                    throw new SchemaBuilderError(show(mergeel) + " does not match an existing element under " + showpath(orig));
                } else {
                    merge(origel, mergeel);
                }
            }
        }
    }

    public boolean booleanAttributeValue(String name, String val, boolean defaultValue) {
      if (val == null || "".equalsIgnoreCase(val))
        return defaultValue;
      else if ("true".equalsIgnoreCase(val))
        return true;
      else if ("false".equalsIgnoreCase(val))
        return false;
      throw new SchemaBuilderError("boolean attribute value " + name + "=\"" + val + "\" must be true or false");
    }

    /**
     * Top level method to build the XML output from the
     * parsed XML input tree.
     */
    public void build(Element sroot) {
        try {
            compileXpathExpressions();

            String versionstr = LPS.getVersion() + "|" +
                LPS.getRelease() + "|" +
                LPS.getBuild() + "|" +
                LPS.getBuildDate();

            sroot.setAttribute("version", versionstr);

            // Walk the set of nodes that correspond to classes
            // to collect their names and position in the input XML tree.
            //NodeList nodes = (NodeList)xPath.evaluate("/js2doc/property/doc/tag[@name='lzxname']/text", droot, XPathConstants.NODESET);
            NodeList classNodes = expressions.nodelist(XPathExpr.Classes, droot);
            for (int count = classNodes.getLength(), i = 0; i < count; i++) {
                Element classNode = (Element) classNodes.item(i);
                Element eprop = (Element)classNode.getParentNode();
                Element elzxname = expressions.element(XPathExpr.LZXName, eprop);

                // TODO: we currently say:
                //   if the class doesn't have access, get it from the unitid.
                //   If an element in the class doesn't have access, get it from the class.
                // A slightly different approach, but one which I think matches the doc is:
                //   if the class doesn't have access, get it from the unitid.
                //   If an element in the class doesn't have access, get it from the *unitid*.
                // TODO: test this difference, and if so, implement it.
                //
                if (classNode != null) {
                    JsClass j = new JsClass();
                    j.jsname = eprop.getAttribute("name");
                    j.access = eprop.getAttribute("access");
                    if (j.access == null) {
                        String unitid = eprop.getAttribute("unitid");
                        if (unitid != null) {
                            Element unitNode = (Element)xPath.evaluate("/js2doc/unit[id='" + unitid + "']", droot, XPathConstants.NODE);
                            if (unitNode != null) {
                                j.access = unitNode.getAttribute("access");
                            }
                        }
                    }
                    j.lzxname = getTextData(elzxname);
                    if (j.lzxname == null) {
                        if (j.jsname == null) {
                          throw new SchemaBuilderError(position(classNode) + "class has no tagname or jsname");
                        }
                        j.lzxname = createLzxName(j.jsname);
                    }
                    j.extname = classNode.getAttribute("extends");
                    j.ismetasym = booleanAttributeValue("metasym", classNode.getAttribute("metasym"), false);
                    j.classnode = classNode;
                    // detach from parent - boosts performance by >1000% [LPP-9841]
                    classNode.getParentNode().removeChild(classNode);
                    jsClasses.put(j.jsname, j);
                }
            }

            //
            // The reader of this schema file needs the
            // classes ordered so that no class is used as a superclass
            // before it is defined.  Use a different tree to
            // hold that ordering.  We do that after seeing
            // all the classes so we know the complete inheritance chains.
            for (String name : jsClasses.keySet()) {
                JsClass j = jsClasses.get(name);
                jsClassesOrdered.put(getOrderedKey(j), j);
            }

            // Walk the classes, and for each one, pull out its
            // methods, events, attrs
            for (String name : jsClassesOrdered.keySet()) {
                JsClass j = jsClassesOrdered.get(name);
                Element iface = createChild(sroot, ViewSchema.KIND_INTERFACE);

                iface.setAttribute("name", j.lzxname);
                String lzxExtends = "Instance";
                if (j.extname != null) {
                    JsClass extj = jsClasses.get(j.extname);
                    if (extj != null && extj.lzxname != null) {
                        lzxExtends = extj.lzxname;
                    }
                }
                iface.setAttribute("extends", lzxExtends);
                if (j.jsname != null) {
                    iface.setAttribute("jsname", j.jsname);
                }
                if (j.ismetasym) {
                    iface.setAttribute("metasym", "true");
                }

                TreeMap<String, JsMember> methods = new TreeMap<String, JsMember>();
                TreeMap<String, JsMember> events = new TreeMap<String, JsMember>();
                TreeMap<String, JsMember> attrs = new TreeMap<String, JsMember>();

                addMethods(methods, j, true);
                addMethods(methods, j, false);

                addAttributes(attrs, j, true);
                addAttributes(attrs, j, false);

                // Add events not already defined as attributes
                addEvents(events, attrs, j);

                // Put the found class members into the output XML
                createMembers(methods, iface, "method");
                createMembers(events, iface, "event");
                createMembers(attrs, iface, "attribute");
            }
            if (mergeSchema != null) {
                merge(sroot, mergeSchema);
            }
            sroot.insertBefore(sroot.getOwnerDocument().createComment(INITIAL_COMMENT), sroot.getFirstChild());
        } catch (XPathExpressionException e) {
            throw new InternalError(e);
        }
    }

    /**
     * Add any methods to the treemap.
     */
    protected void addMethods(TreeMap<String, JsMember> methods, JsClass jsclass,
                              boolean classMethods)
        throws XPathExpressionException {
        XPathExpr expr = (classMethods ? XPathExpr.ClassMethods : XPathExpr.InstanceMethods);
        NodeList nodes = expressions.nodelist(expr, jsclass.classnode);
        for (int count = nodes.getLength(), i = 0; i < count; i++) {
            JsMember member = addMember(MEMBER_METHOD, (Element) nodes.item(i).getParentNode(), jsclass, classMethods);
            if (member != null) {
                methods.put(member.name, member);
            }
        }
    }

    /**
     * Add any events to the treemap.  Any names that are already
     * defined as attributes are ignored.
     */
    protected void addEvents(TreeMap<String, JsMember> events, TreeMap<String, JsMember> attrs, JsClass jsclass)
        throws XPathExpressionException {
        // Every instance attribute has an implicit event associated with it.
        NodeList nodes = expressions.nodelist(XPathExpr.InstanceProperties, jsclass.classnode);
        for (int count = nodes.getLength(), i = 0; i < count; i++) {
            Element eprop = (Element)nodes.item(i);
            JsMember member = addMember(MEMBER_EVENT, eprop, jsclass, false);
            if (member != null) {
                member.name = "on" + member.name;
                if (!attrs.containsKey(member.name)) {
                    events.put(member.name, member);
                }
            }
        }

        // Explicit event declarations override any implicit ones
        nodes = expressions.nodelist(XPathExpr.Events, jsclass.classnode);
        for (int count = nodes.getLength(), i = 0; i < count; i++) {
            Element eprop = (Element)nodes.item(i);
            JsMember member = addMember(MEMBER_EVENT, eprop, jsclass, false);
            if (member != null) {
                if (attrs.containsKey(member.name)) {
                    System.err.println("Warning: attribute " + jsclass.jsname + "." + member.name + " is also defined as an event in the same class");
                } else {
                    events.put(member.name, member);
                }
            }
        }

    }

    /**
     * Add any attributes to the treemap.
     */
    protected void addAttributes(TreeMap<String, JsMember> attrs, JsClass jsclass,
                                 boolean classAttrs)
        throws XPathExpressionException {
        XPathExpr expr = (classAttrs ? XPathExpr.Doc : XPathExpr.InstanceProperties);
        NodeList nodes = expressions.nodelist(expr, jsclass.classnode);
        for (int count = nodes.getLength(), i = 0; i < count; i++) {
            Element eprop = (Element)nodes.item(i);
            // Our query for attributes can pick up events too.
            String lzxtype = getTextData(expressions.element(XPathExpr.LZXType, eprop));
            String lzxdefault = getTextData(expressions.element(XPathExpr.LZXDefault, eprop));
            String style = getTextData(expressions.element(XPathExpr.Style, eprop));
            String inherit = getTextData(expressions.element(XPathExpr.Inherit, eprop));
            // TODO: consider using "event".equals(lzxtype)?
            Boolean isEvent = expressions._boolean(XPathExpr.Events2, eprop);
            if (isEvent.booleanValue()) {
                continue;
            }
            // Our query for class attributes can pick up functions too.
            Boolean isFunction = expressions._boolean(XPathExpr.Function, eprop);
            if (isFunction.booleanValue()) {
                continue;
            }
            JsMember member = addMember(MEMBER_ATTRIBUTE, eprop, jsclass, classAttrs);
            if (member != null) {
                member.type = lzxtype;
                member.style = style;
                member.inherit = inherit;
                if (lzxdefault != null) {
                    member.defaultvalue = convertDefaultValue(lzxdefault);
                }
                if (member.type == null || member.type.equals("")) {
                    member.type = eprop.getAttribute("type");
                }
                if (member.type != null && !"".equals(member.type)) {
                    member.type = convertAttributeType(eprop, member.type, isRealLzxName(jsclass.lzxname));
                    if (member.type != null && member.type.indexOf('|') >= 0) {
                        member.enumvalues = member.type;
                        member.type = "string";
                    }
                } else {
                    member.type = null;
                }
                if (member.type == null && isRealLzxName(jsclass.lzxname)) {

                    System.err.println("Warning: attribute " + jsclass.jsname + "." + member.name + " has no @lzxtype or @type in javadoc, needed for schema");
                }
                attrs.put(member.name, member);
            }
        }
    }

    /**
     * Create and initialize a JsMember (method/attribute/event),
     * from the given <property> node.
     */
    protected JsMember addMember(String kind, Element eprop, JsClass jsclass, boolean isstatic)
        throws XPathExpressionException {
        String access = eprop.getAttribute("access");
        JsMember member = null;
        if (access == null) {
            access = jsclass.access;
        }
        if (access == null) {
            System.err.println("Warning: access is unknown for " + jsclass.jsname + "." + eprop.getAttribute("name"));
        }
        // Note: we need to include methods that are marked private in doc,
        // because if they are redefined, they must be marked as 'override'.
        if (kind.equals(MEMBER_METHOD) || !"private".equals(access)) {
            member = new JsMember();
            member.name = eprop.getAttribute("name");
            // All events are final
            member.isfinal = isFinal(eprop) || kind.equals(MEMBER_EVENT);
            member.isstatic = isstatic;
            if (kind.equals(MEMBER_METHOD)) {
                NodeList params = expressions.nodelist(XPathExpr.Parameter, eprop);
                for (int count = params.getLength(), i = 0; i < count; i++) {
                    Element paramelem = (Element) params.item(i);
                    JsParam param = new JsParam();
                    param.name = paramelem.getAttribute("name");
                    param.type = paramelem.getAttribute("type");
                    member.params.add(param);
                }
            }
        }
        return member;
    }

    /**
     * True if the member is marked 'final'.
     */
    protected boolean isFinal(Element eprop)
        throws XPathExpressionException {
        // Modifiers can exist as attributes on the property containing
        // function and also as <tag name="modifiers"> nodes under property.
        String mods = eprop.getAttribute("modifiers");
        if (mods == null) {
            mods = "";
        }
        Element emod = expressions.element(XPathExpr.TagNameModifiers, eprop);
        if (emod != null) {
            String modtext = getTextData(emod);
            if (modtext != null) {
                mods += " " + modtext;
            }
        }
        //TODO: Can't final also appear as:  <tag name="modifiers"><text>final</text></tag>
        // TODO: how are multiple modifiers delimited?
        return (mods != null && mods.contains("final"));
    }

    /**
     * Given a map of JsMembers, create them all within the interface.
     */
    protected void createMembers(TreeMap<String, JsMember> methods, Element iface, String tag) {
        for (String name : methods.keySet()) {
            JsMember member = methods.get(name);
            createMember(iface, member, tag);
        }
    }

    /**
     * Given a JsMember, create it within the interface.
     */
    protected void createMember(Element iface, JsMember member, String tag) {
        Element emember = createChild(iface, tag);
        emember.setAttribute("name", member.name);
        if (member.isfinal) {
            emember.setAttribute("final", "true");
        }
        if (member.type != null) {
            emember.setAttribute("type", member.type);
        }
        if (member.style != null) {
            emember.setAttribute("style", member.style);
        }
        if (member.inherit != null) {
            emember.setAttribute("inherit", member.inherit);
        }
        if (member.enumvalues != null) {
            emember.setAttribute("enum", member.enumvalues);
        }
        if (member.defaultvalue != null) {
            emember.setAttribute("value", member.defaultvalue);
        }
        if (member.isstatic) {
            emember.setAttribute("allocation", "class");
        }
        if (member.params.size() != 0) {
            String args = "";
            for (JsParam jsparam : member.params) {
                if (!args.equals("")) {
                  args += ", ";
                }
                String thisarg = jsparam.name;
                if (jsparam.type != null && !jsparam.type.equals("")) {
                  // A special notation of [*] indicates optional arguments
                  if (jsparam.type.equals("[*]")) {
                    thisarg = "..." + thisarg;
                  }
                  // The best we can do for 'type1|type2' is simply untyped (*).
                  else if (jsparam.type.contains("|")) {
                    thisarg += ":*";
                  }
                  // synonym for * type.
                  else if (jsparam.type.equals("any")) {
                    thisarg += ":*";
                  }
                  else {
                    //TODO: [2011-02-04 dda] In order to pass actual
                    // types, we'd need some more work.  Some arg type
                    // in the schema reference javascript util types,
                    // like 'Dictionary', some need to be converted
                    // (in source or here) e.g. boolean -> Boolean.
                    // The confusing thing (for the latter case) is
                    // that attribute types in the lfc must use the
                    // lowercase name (boolean) for tag names, whereas
                    // here we want Javascript names (Boolean).
                    // So we have need for both forms within the schema file.
                    // At the moment we don't need arg types.

                    //thisarg += ":" + jsparam.type;
                    thisarg += ":*";
                  }
                }
                args += thisarg;
            }
            emember.setAttribute("args", args);
        }
    }

    /**
     * A type found in doc may need to be converted to
     * a different naming convention for the output schema format.
     * TODO: is there a good reason for this, maybe we should
     * generally use the type found in the doc, and teach the reader
     * of lfc.lzx to know those types.
     */
    String convertAttributeType(Element el, String s, boolean required) {
        String result = null;
        if (s.indexOf('|') >= 0) {
            result = s.replaceAll("\"", "").replaceAll("'", "").replaceAll(" ", "");
            // special case: booleanLiteral|'inherit' becomes inheritableBoolean
            if ("booleanLiteral|inherit".equals(result)) {
                result = "inheritableBoolean";
            }
        } else if ("String".equals(s)) {
            result = "string";
        } else if ("Number".equals(s) || "integer".equals(s) || "uint".equals(s)) {
            result = "number";
        } else if ("Boolean".equals(s)) {
            result = "boolean";
        } else if ("booleanLiteral".equals(s)) {
            result = "boolean";   // TODO: not exactly true, but this is what appears in the schema...
        } else if ("sizeExpression".equals(s)) {
            result = "size";
        } else if ("Array".equals(s) ||
                   "LzContextMenu".equals(s) ||
                   "LzDataNodeMixin".equals(s) ||
                   "LzDataProvider".equals(s) ||
                   "LzDelegate".equals(s) ||
                   "LzNode".equals(s) ||
                   "LzParam".equals(s) ||
                   "LzReplicationManager".equals(s) ||
                   "LzView".equals(s) ||
                   "Object".equals(s) ||
                   "[LzView]".equals(s) ||
                   "[String]".equals(s) ||
                   "DisplayKeys".equals(s) ||
                   "Dictionary".equals(s)) {
            // TODO: these have no good alternative!
            result = "string";
        } else if ("ID".equals(s) ||
                   "boolean".equals(s) ||
                   "color".equals(s) ||
                   "css".equals(s) ||
                   "expression".equals(s) ||
                   "inheritableBoolean".equals(s) ||
                   "number".equals(s) ||
                   "numberExpression".equals(s) ||
                   "reference".equals(s) ||
                   "size".equals(s) ||
                   "string".equals(s) ||
                   "text".equals(s) ||
                   "html".equals(s) ||
                   "cdata".equals(s) ||
                   "token".equals(s)) {
            result = s;
        } else if (required) {
            throw new SchemaBuilderError(position(el) + "type \"" +
                                         s + "\" not supported by schema builder");
        }
        return result;
    }

    String convertDefaultValue(String s) {
        return s.replaceAll("\"", "").replaceAll("'", "");
    }

    /**
     * For debugging, show the given tree as XML.
     */
    @SuppressWarnings("unused")
    private void showNodeTree(Element e, String prefix)
    {
        System.out.print(prefix + "<" + e.getTagName());
        NamedNodeMap attrs = e.getAttributes();
        for (int i=0; i<attrs.getLength(); i++) {
            Node n = attrs.item(i);
            System.out.print(" " + n.getNodeName() + "='" + n.getNodeValue() + "'");
        }
        System.out.println(">");
        NodeList children = e.getChildNodes();
        for (int i=0; i<children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element) {
                showNodeTree((Element)n, prefix + "  ");
            }
        }
        System.out.println(prefix + "</" + e.getTagName() + ">");
    }
}
