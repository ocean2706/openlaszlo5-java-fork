/* -*- mode: Java; c-basic-offset: 2; -*- */
/* ***************************************************************************
 * NodeModel.java
 * ***************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.Text;
import org.jdom.output.Format;
import org.openlaszlo.compiler.ViewSchema.ColorFormatException;
import org.openlaszlo.css.parser.CSSParser;
import org.openlaszlo.sc.CompilerImplementationError;
import org.openlaszlo.sc.Function;
import org.openlaszlo.sc.Method;
import org.openlaszlo.sc.ReferenceCollector;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.xml.internal.MissingAttributeException;
import org.openlaszlo.xml.internal.Schema;
import org.openlaszlo.xml.internal.XMLUtils;

/** Models a runtime LzNode. */
public class NodeModel implements Cloneable {

    public static final String FONTSTYLE_ATTRIBUTE = "fontstyle";
    public static final String WHEN_IMMEDIATELY = "immediately";
    public static final String WHEN_ONCE = "once";
    public static final String WHEN_ALWAYS = "always";
    public static final String WHEN_PATH = "path";
    public static final String WHEN_STYLE = "style";
    public static final String ALLOCATION_INSTANCE = "instance";
    public static final String ALLOCATION_CLASS = "class";

    private static final String SOURCE_LOCATION_ATTRIBUTE_NAME = "__LZsourceLocation";

    // pattern for identifier enclosed in quotes, i.e. 'value' or "value"
    private static final Pattern quotedIdentifierPat = Pattern.compile("\\s*(?:'\\S*'|\"\\S*\")\\s*");
    // pattern for empty or white-space only string
    private static final Pattern emptyStringPat = Pattern.compile("\\s*");

    final ViewSchema schema;
    final Element element;
    String tagName;
    String id = null;
    String localName = null;
    String globalName = null;
    private String nodePath = null;
    LinkedHashMap<String, Object> attrs = new LinkedHashMap<String, Object>();
    List<NodeModel> children = new Vector<NodeModel>();
    /** A set {eventName: String -> True) of names of event handlers
     * declared with <handler name="xxx"/>. */
    LinkedHashMap<String, Boolean> delegates = new LinkedHashMap<String, Boolean>();
    LinkedHashMap<String, Object> classAttrs = new LinkedHashMap<String, Object>();
    LinkedHashMap<String, String> setters = new LinkedHashMap<String, String>();
    LinkedHashMap<String, String> CSSAttributeProperties = new LinkedHashMap<String, String>();
    LinkedHashMap<String, String> CSSAttributeTypes = new LinkedHashMap<String, String>();
    LinkedHashMap<String, String> CSSAttributeFallbacks = new LinkedHashMap<String, String>();
    LinkedHashMap<String, String> CSSPropertyExpanders = new LinkedHashMap<String, String>();
    LinkedHashMap<String, Boolean> CSSPropertyInheritable = new LinkedHashMap<String, Boolean>();

    NodeModel     datapath = null;

    String passthroughBlock = null;
    String passthroughBlockWhen = null;

    /** [eventName: String, methodName: String, Function] */
    List<String> delegateList = new Vector<String>();
    ClassModel metamodel = null;
    ClassModel parentClassModel;
    String initstage = null;
    int totalSubnodes = 1;
    final CompilationEnvironment env;
    // Used to freeze the definition for generation
    protected boolean frozen = false;
    // Caches
    protected boolean isclassdef = false;
    // Datapaths and States don't have methods because they "donate"
    // their methods to other instances.  Where we would normally make
    // a method, we make a closure instead
    protected boolean canHaveMethods = true;

    @Override
    public Object clone() {
        NodeModel copy;
        try {
            copy = (NodeModel) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        copy.attrs = new LinkedHashMap<String, Object>(copy.attrs);
        copy.delegates = new LinkedHashMap<String, Boolean>(copy.delegates);
        copy.classAttrs = new LinkedHashMap<String, Object>(copy.classAttrs);
        copy.CSSAttributeProperties = new LinkedHashMap<String, String>(copy.CSSAttributeProperties);
        copy.CSSAttributeTypes = new LinkedHashMap<String, String>(copy.CSSAttributeTypes);
        copy.CSSAttributeFallbacks = new LinkedHashMap<String, String>(copy.CSSAttributeFallbacks);
        copy.CSSPropertyExpanders = new LinkedHashMap<String, String>(copy.CSSPropertyExpanders);
        copy.CSSPropertyInheritable = new LinkedHashMap<String, Boolean>(copy.CSSPropertyInheritable);
        copy.setters = new LinkedHashMap<String, String>(copy.setters);
        copy.delegateList = new Vector<String>(copy.delegateList);
        copy.children = new Vector<NodeModel>();
        for (NodeModel child : children) {
            copy.children.add((NodeModel) child.clone());
        }
        return copy;
    }

    NodeModel(Element element, ViewSchema schema, CompilationEnvironment env) {
        this.element = element;
        this.schema = schema;
        this.env = env;
        // Get the tag and whether we are a class definition
        this.tagName = element.getName().intern();
        this.isclassdef = schema.isClassDefinition(element);
        if (isclassdef) {
          this.metamodel = schema.getClassModel(ViewSchema.KIND_CLASS, tagName);
        }
        // Cache our parentClassModel (for an instance, the model of
        // our tag's class, for a class [including anonymous ones],
        // the model of our superclass)
        assert ("anonymous".equals(tagName) ? element.getAttributeValue("extends") != null : true);
        if (isclassdef || "anonymous".equals(tagName)) {
          String name = element.getAttributeValue("extends", ClassModel.DEFAULT_SUPERCLASS_NAME);
          this.parentClassModel = schema.getClassModel(ViewSchema.KIND_CLASS, name);
          if (parentClassModel == null) {
            throw new CompilationError("Unknown tag extends='" + name + "'", element);
          }
        } else {
          this.parentClassModel = schema.getClassModel(ViewSchema.KIND_CLASS, tagName);
          if (parentClassModel == null) {
            throw new CompilationError("Unknown tag <" + tagName + ">", element);
          }
        }
        if (parentClassModel.isstate || parentClassModel.isdatapath) {
          this.canHaveMethods = false;
        }
        this.initstage = this.element.getAttributeValue("initstage");
        if (this.initstage != null) {
            this.initstage = this.initstage.intern();
        } else if (parentClassModel.nodeModel != null) {
          this.initstage = parentClassModel.nodeModel.initstage;
        }
        // Get initial node count from superclass
        if (parentClassModel.nodeModel != null) {
          this.totalSubnodes = parentClassModel.nodeModel.totalSubnodes;
        }
        // Get ID
        this.id = element.getAttributeValue("id");
        this.localName = element.getAttributeValue("name");
        // Get global name, if applicable
        if (CompilerUtils.topLevelDeclaration(element) && (! isclassdef)) {
          this.globalName = localName;
        }
    }

    // Path of node from root.  This follows the same psuedo-xpath
    // system used in LzNode._dbg_name.  It provides a basis for
    // giving anonymous classes unique names.
    private static String computeNodePath(NodeModel node, ViewSchema schema, CompilationEnvironment env) {
        if (node.id != null) {
            return "#" + node.id;
        }
        if (node.globalName != null) {
            return "#" + node.globalName;
        }
        String path = "";
        org.jdom.Parent parentDOMNode = node.element.getParent();
        Element parentElement;
        if (parentDOMNode instanceof org.jdom.Document) {
          parentElement = ((org.jdom.Document)parentDOMNode).getRootElement();
        } else {
          parentElement = (Element)parentDOMNode;
        }
        String pn;
        if (parentElement == null) {
          pn = "?";
        } else if ((parentElement == node.element) ||
                   // <library> is only permitted at the root and is elided
                   // by the compiler
                   "library".equals(parentElement.getName())) {
          // Must be at the root, when not linking, create a UID
          // placeholder for root
          if (env.linking) {
            // linking, there is only one root
            pn = "";
          } else {
            // not linking, use a unique root
            pn = env.getUUID();
          }
        } else {
          // Ensure parent modelled
          NodeModel parent = elementAsModel((ElementWithLocationInfo)parentElement, schema, env);
          pn = computeNodePath(parent, schema, env);
        }
        String nn = node.localName;
        if (nn != null) {
          path = "@" + nn;
        } else {
          String tn = path = node.tagName;
          int count = 0, index = -1;
          for (Iterator<?> iter = parentElement.getChildren(tn, parentElement.getNamespace()).iterator();
               iter.hasNext(); ) {
            Element sibling = (Element) iter.next();
            count++;
            if (index != -1) break;
            if (node.element == sibling) { index = count; }
          }
          if (count > 1) {
            path += "[" + index + "]";
          }
        }
        return pn + "/" + path;
    };

    String getNodePath () {
        if (nodePath != null) { return nodePath; }
        return nodePath = computeNodePath(this, schema, env);
    }

    private static final String DEPRECATED_METHODS_PROPERTY_FILE = (
        LPS.getMiscDirectory() + File.separator + "lzx-deprecated-methods.properties"
        );
    private static final Properties sDeprecatedMethods = new Properties();

    static {
        try {
            InputStream is = new FileInputStream(DEPRECATED_METHODS_PROPERTY_FILE);
            try {
                sDeprecatedMethods.load(is);
            } finally {
                is.close();
            }
        } catch (java.io.IOException e) {
            throw new ChainedException(e);
        }
    }

    private static final String FLASH7_BUILTINS_PROPERTY_FILE = (
        LPS.getMiscDirectory() + File.separator + "flash7-builtins.properties"
        );

    public static final Properties sFlash7Builtins = new Properties();

    static {
        try {

            InputStream is7 = new FileInputStream(FLASH7_BUILTINS_PROPERTY_FILE);
            try {
                sFlash7Builtins.load(is7);
            } finally {
                is7.close();
            }
        } catch (java.io.IOException e) {
            throw new ChainedException(e);
        }
    }

  static class BindingExpr {
    String expr;
    BindingExpr(String expr) { this.expr = expr; }
    String getExpr() { return this.expr; }
  }

  static class CompiledAttribute {
    String name;
    Schema.Type type;
    private String value;
    boolean constantValue = false;
    String when;
    Element source;
    CompilationEnvironment env;
    String srcloc;
    String bindername;
    String dependenciesname;
    String fallbackexpression;

    static org.openlaszlo.sc.Compiler compiler;

    org.openlaszlo.sc.Compiler getCompiler() {
      if (compiler != null) { return compiler; }
      return compiler = new org.openlaszlo.sc.Compiler();
    }

    public CompiledAttribute (String name, Schema.Type type, String value, String when, Element source, CompilationEnvironment env) {
      this.name = name;
      this.type = type;
      this.value = value;
      // Current only used for WHEN_STYLE
      //
      // Only approximate -- A CSS property name has to be a CSS
      // identifier (optional leading -, followed by an alphabetic
      // character, followed by any number of alphanumeric or -.  See
      // http://www.w3.org/TR/CSS2/syndata.html#syntax.
      this.constantValue = (value != null && quotedIdentifierPat.matcher(value).matches());
      this.when = when;
      this.source = source;
      this.env = env;
      this.srcloc = CompilerUtils.sourceLocationDirective(source, true);
      if (when.equals(WHEN_STYLE) && (!constantValue)) {
        throw new CompilationError("The attribute '" + name +"' has a non-constant $style binding, which is not supported" 
                                   , source);
      } else if (when.equals(WHEN_PATH) || when.equals(WHEN_ONCE) || when.equals(WHEN_ALWAYS)) {
        this.bindername =  env.methodNameGenerator.next();
        if (when.equals(WHEN_ALWAYS)) {
          this.dependenciesname =  env.methodNameGenerator.next();
        }
      }

      // If this attribute type is not allowed to take an empty string, complain
      if (ViewSchema.sNonEmptyValueTypes.contains(type) && value != null && emptyStringPat.matcher(value).matches()) {
        throw new CompilationError("The attribute '" + name +"' has type "+type +
                                   ", which cannot have an empty value."
                                   , source);
      }
    }

    public void setFallbackExpression(String fallback) {
      this.fallbackexpression = fallback;
    }

    public Function getBinderMethod(boolean canHaveMethods) {
      if (! (when.equals(WHEN_PATH) || when.equals(WHEN_ONCE) || when.equals(WHEN_ALWAYS))) {
        return null;
      }
      String installer = "setAttribute";
      String prefix ="";
      String body = "#beginAttribute" + srcloc + value + CompilerUtils.endSourceLocationDirective + "#endAttribute";
      String suffix = "";
      String prettyBinderName = name + "='$";

      // All constraint methods need ignore args for swf9
      String args="$lzc$ignore";
      if (when.equals(WHEN_ONCE)) {
        // default
        prettyBinderName += "once";
      } else if (when.equals(WHEN_ALWAYS)) {
        // NOTE: [2009-05-18 ptw] Only call the installer if the value
        // will change, to minimize event cascades (data and style
        // binding have to handle this in their installers).  We
        // always call the installer if the target is not inited.
        // This ensures that the value is set correctly (and events
        // propagated) if the constraint is being called to initialize
        // the target
        prefix = "var $lzc$newvalue = " + body + ";\n" +
          "if ($lzc$newvalue !== this[" + ScriptCompiler.quote(name) + "] || (! this.inited)) {\n";
        body = "$lzc$newvalue";
        suffix = "\n}";
      } else if (when.equals(WHEN_PATH)) {
        installer = "dataBindAttribute";
        body = body + "," + ScriptCompiler.quote("" + type);
        prettyBinderName += "path";
      }
      body = prefix + "this." + installer + "(" +
          ScriptCompiler.quote(name) + "," +
          body + ")" + suffix;
      Function binder;
      prettyBinderName += "{...}'";
      String pragmas = "";
      // Note: for debugging, will be ignored by non-debug
      pragmas += "#pragma " + ScriptCompiler.quote("userFunctionName=" + prettyBinderName);
      // Binders are called by LzDelegate.execute, which passes the
      // value sent by sendEvent, so we have to accept it, but we
      // ignore it
      if (canHaveMethods) {
          binder = new Method(bindername, args, "", pragmas, body, srcloc, null);
      } else {
          pragmas += "\n#pragma 'withThis'\n";
          binder = new Function(bindername, args, "", pragmas, body, srcloc);
      }
      return binder;
    }

    public Function getDependenciesMethod(boolean canHaveMethods) {
      if (! when.equals(WHEN_ALWAYS)) {
        return null;
      }
      String pragmas = "";
      // Note: for debugging, will be ignored by non-debug
      pragmas += "#pragma " + ScriptCompiler.quote("userFunctionName=" + name + " dependencies");
      // Silence reference errors in dependency methods
      pragmas += "\n#pragma " + ScriptCompiler.quote("warnUndefinedReferences=false");
      // But percolate them up to applyConstraintExpr
      pragmas += "\n#pragma " + ScriptCompiler.quote("throwsError=true");
      ReferenceCollector collector = getCompiler().dependenciesForExpression(srcloc + value);
      String depExpr = collector.computeReferencesAsExpression();
      String depAnnotation = collector.computeReferencesDebugAnnoration();
      String body =
        "if ($debug) {\n" +
        "  return $lzc$validateReferenceDependencies(" + depExpr + ", " + depAnnotation + ");\n" +
        "} else {\n" +
        "  return " + depExpr + ";\n" +
        "}\n";
      Function dependencies;
      if (canHaveMethods) {
          dependencies = new Method(dependenciesname, "", "", pragmas, body, srcloc, null);
      } else {
          pragmas += "\n#pragma 'withThis'\n";
          dependencies = new Function(dependenciesname, "", "", pragmas, body, srcloc);
      }
      return dependencies;
    }

    public Object getInitialValue () {
      // A null value indicates an attribute that was declared only
      if (value == null) { return null; }
      // Handle when cases
      // N.B., $path and $style are not really when values, but
      // there you go...
      if (when.equals(WHEN_PATH) || (when.equals(WHEN_STYLE)) || when.equals(WHEN_ONCE) || when.equals(WHEN_ALWAYS)) {
        String kind = "LzOnceExpr";
        String debugDescription = "";
        debugDescription = ", ($debug ? (" + ScriptCompiler.quote("='$" + (when.equals(WHEN_ALWAYS) ? "" : when) + "{...}'") + ") : null)";
        if (when.equals(WHEN_ONCE) || when.equals(WHEN_PATH)) {
          // default
        } else if (when.equals(WHEN_STYLE)) {
          assert constantValue;
          // Style constraints on constant properties have a special
          // compact mechanism indicated by a unique marker.  If the
          // marker survives inheritance and overriding, then the
          // actual style binding will be computed and installed
          // (after inits and before constraints).
          return new BindingExpr("LzStyleConstraintExpr.StyleConstraintExpr");
        } else if (when.equals(WHEN_ALWAYS)) {
          kind = "LzAlwaysExpr";
          // Always constraints have a second value, the dependencies method
          return new BindingExpr("new " + kind + "(" +
                                 ScriptCompiler.quote(bindername) + ", " +
                                 ScriptCompiler.quote(dependenciesname) +
                                 debugDescription + ")");
        }
        // Return an initExpr as the 'value' of the attribute
        return new BindingExpr("new " + kind + "(" +
                               ScriptCompiler.quote(bindername) +
                               debugDescription + ")");
      } else if (when.equals(WHEN_IMMEDIATELY)) {
        if (CanvasCompiler.isElement(source) &&
            ("width".equals(name) || "height".equals(name))) {
          // The Canvas compiler depends on seeing width/height
          // unadulterated <sigh />.
          return value;
        } else {
          // TODO: [2007-05-05 ptw] (LPP-3949) The
          // #beginAttribute directives for the parser should
          // be added when the attribute is written, not
          // here...
          return "#beginAttribute\n" + srcloc + value + CompilerUtils.endSourceLocationDirective +"#endAttribute";
        }
      } else {
        throw new CompilationError("invalid when value '" +
                                   when + "'", source);
      }
    }
  }

  public String toLZX() {
    return toLZX("");
  }

  public String toLZX(String indent) {
    // NOTE: For LZO's, we have to emit anonymous parent classes, so
    // they can be correctly accounted for.  This may not be
    // appropriate if the schema is to be used for other purposes
    // (since such classes are not available for instantiating).
    return indent + "<" + parentClassModel.tagName
        + ((globalName != null) ? (" id='" + globalName + "'") :  "")
        + ((localName != null) ? (" name='" + localName + "'") :  "")
        + " />";
  }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append("{NodeModel class=" + tagName);
        if (!attrs.isEmpty())
            buffer.append(" attrs=" + attrs.keySet());
        if (!delegates.isEmpty())
            buffer.append(" delegates=" + delegates.keySet());
        if (!classAttrs.isEmpty())
            buffer.append(" classAttrs=" + classAttrs.keySet());
        if (!setters.isEmpty())
            buffer.append(" setters=" + setters.keySet());
        if (!delegateList.isEmpty())
            buffer.append(" delegateList=" + delegateList);
        if (!children.isEmpty())
            buffer.append(" children=" + children);
        buffer.append("}");
        return buffer.toString();
    }

    List<NodeModel> getChildren() {
        return children;
    }

    public static boolean isPropertyElement(Element elt) {
        String name = elt.getName();
        return "attribute".equals(name) || "method".equals(name)
          || "handler".equals(name) || "setter".equals(name)
          || "event".equals(name) || "passthrough".equals(name);
    }

    /** Returns a name that is used to report this element in warning
     * messages. */
    String getMessageName() {
        return "element " + tagName;
    }

    /**
     * Returns a script that creates a runtime representation of a
     * model.  The format of this representation is specified <a
     * href="../../../../doc/compiler/views.html">here</a>.
     *
     * The CompilationEnvironment is passed in because we may be emitting
     * code to a library. 
     */
    public String asJavascript(CompilationEnvironment cEnv) {
        try {
            StringBuilder writer = new StringBuilder();
            ScriptCompiler.writeObject(this.asMap(cEnv), writer);
            return writer.toString();
        } catch (java.io.IOException e) {
            throw new ChainedException(e);
        }
    }

    /** Returns true iff clickable should default to true. */
    private static boolean computeDefaultClickable(ViewSchema schema,
                                                   Map<String, Object> attrs,
                                                   Map<String, Boolean> delegates) {
        if ("true".equals(attrs.get("cursor"))) {
            return true;
        } else if (schema.containsMouseEventAttribute(attrs.keySet())) {
            // TODO: [2008-05-05 ptw] See if it really is an event,
            // not just an attribute with a coincidental name?
            return true;
        } else if (schema.containsMouseEventAttribute(delegates.keySet())) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a NodeModel that represents an Element, including the
     * element's children
     *
     * @param elt an element
     * @param schema a schema, used to encode attribute values
     * @param env the CompilationEnvironment
     */
    public static NodeModel elementAsModel(Element elt, ViewSchema schema,
                                           CompilationEnvironment env) {
        return elementAsModelInternal(elt, schema, true, env);
    }

    /**
     * Returns a NodeModel that represents an Element, excluding the
     * element's children
     *
     * @param elt an element
     * @param schema a schema, used to encode attribute values
     * @param env the CompilationEnvironment
     */
    public static NodeModel elementOnlyAsModel(Element elt, ViewSchema schema,
                                               CompilationEnvironment env) {
        return elementAsModelInternal(elt, schema, false, env);
    }

    /** Returns a NodeModel that represents an Element
     *
     * @param elt an element
     * @param schema a schema, used to encode attribute values
     * @param includeChildren whether or not to include children
     * @param env the CompilationEnvironment
     */
    private static NodeModel elementAsModelInternal(
        Element elt, ViewSchema schema, 
        boolean includeChildren, CompilationEnvironment env)
    {
        NodeModel model = ((ElementWithLocationInfo) elt).model;
        if (model != null) { return model; }

        ElementCompiler compiler = Compiler.getElementCompiler(elt, env);
        compiler.preprocess(elt, env);

        model = new NodeModel(elt, schema, env);
        LinkedHashMap<String, Object> attrs = model.attrs;
        Map<String, Boolean> delegates = model.delegates;
        model.addAttributes(env);

        // This emits a local dataset node, so only process
        // <dataset> tags that are not top level datasets.
        if (elt.getName().equals("dataset")) {
            boolean contentIsLiteralXMLData = true;
            String datafromchild = elt.getAttributeValue("datafromchild");
            String src = elt.getAttributeValue("src");
            String type = elt.getAttributeValue("type");

            if ((type != null && ("soap".equals(type) || "http".equals(type)))
                || (src != null && (XMLUtils.isURL(src) || constraintPat.matcher(src).matches()))
                || "true".equals(datafromchild)) {
                contentIsLiteralXMLData = false;
            }

            if (contentIsLiteralXMLData) {
              // Default to legacy behavior, treat all children as XML literal data.
              model.addProperty("initialdata", getDatasetContent(elt, env), ALLOCATION_INSTANCE, elt);
              includeChildren = false;
            }
        }

        if (includeChildren) {
            model.addChildren(env);
            // If any children are subclasses of <state>, recursively
            // hoist named children up in order to declare them so
            // they can be referenced as vars without a 'this.---" prefix.
            if (! model.parentClassModel.isstate) {
              model.addStateChildren(env);
            }
            model.addText();
            if (!attrs.containsKey("clickable")
                && computeDefaultClickable(schema, attrs, delegates)) {
              model.addProperty("clickable", "true", ALLOCATION_INSTANCE, elt);
            }
        }

        // Check that all attributes required by the class or it's superclasses are present
        checkRequiredAttributes(elt, model, schema);

        ((ElementWithLocationInfo) elt).model = model;
        return model;
    }

  // Return the value of 'extends' if it exists, otherwise return the tag name
  public static String tagOrClassName(Element elt) {
    String extclass = elt.getAttributeValue("extends");
    return (extclass != null) ? extclass : elt.getName();
  }

  private static void checkRequiredAttributes(Element element, NodeModel model, ViewSchema schema) {
    ClassModel classinfo =  model.parentClassModel;
    Map<String, Object> attrs = model.attrs;

    CompilationEnvironment env = schema.getCompilationEnvironment();
    // Check that each required attribute has a value supplied by either this instance, class, or ancestor class
    for (String reqAttrName : classinfo.requiredAttributes) {
      boolean supplied = false;
      // check if this node model declares a value
      if (attrs.containsKey(reqAttrName)) {
        supplied = true;
      } else {
        // check if the class or superclass models declares a value
        supplied = attrHasDefaultValue(classinfo, reqAttrName,  NodeModel.ALLOCATION_INSTANCE);
      }
      
      if (!supplied) {
        env.warn(
            new CompilationError("Missing required attribute "+reqAttrName+ " for "+classinfo , element),
            element);
      }
    }
  }

  // Return true if a given attribute has a non-null value supplied by the class or ancestor class
  static boolean attrHasDefaultValue(ClassModel classmodel, String attrName, String allocation) {
    Map<String, AttributeSpec> attrtable = allocation.equals(NodeModel.ALLOCATION_INSTANCE) ?
                         classmodel.attributeSpecs : classmodel.classAttributeSpecs;
    AttributeSpec attr = attrtable.get(attrName);
    if ((attr != null) && attr.defaultValue != null && !attr.defaultValue.equals("null")) {
      return true;
    } else if (classmodel.superModel != null) {
      return(attrHasDefaultValue(classmodel.superModel, attrName, allocation));
    } else {
      return false;
    }
  }

    // Calculate how many nodes this object will put on the
    // instantiation queue.
    int totalSubnodes() {
        // A class does not instantiate its subnodes.
        // States override LzNode.thaw to delay subnodes.
        if (isclassdef || parentClassModel.isstate) {
            return 1;
        }
        // initstage late, defer delay subnodes
        if (this.initstage != null &&
            (this.initstage == "late" ||
             this.initstage == "defer")) {
            return 0;
        }
        return this.totalSubnodes;
    }

    /**
     * Returns the tag-name used when defining this node, equal to
     * {@link NodeModel#tagName} unless this is a NodeModel for an anonymous
     * class, in which case the base class' tag-name is returned.
     * 
     * @return tag-name used when defining this node
     */
    @SuppressWarnings("unused")
    private String getUserTagName () {
       String tagName = this.tagName;
       if ("anonymous".equals(tagName)) {
           tagName = element.getAttributeValue("extends");
       }
       assert tagName != null;
       return tagName;
    }

    // Get an attribute value, defaulting to the
    // inherited value, or ultimately the supplied default
    String getAttributeValueDefault(String attribute,
                                    String allocation,
                                    String name,
                                    String defaultValue) {
        // TODO: [2008-05-05 ptw] Schema needs to learn about
        // allocation
        assert ALLOCATION_INSTANCE.equals(allocation);
        // Look for an inherited value
        if (this.parentClassModel != null) {
            AttributeSpec attrSpec =
              this.parentClassModel.getAttribute(attribute, allocation);
            if (attrSpec != null) {
                Element source = attrSpec.source;
                if (source != null) {
                    return XMLUtils.getAttributeValue(source, name, defaultValue);
                }
            }
        }

        return defaultValue;
    }

    String getAttributeValueDefault(String attribute,
                                    String name,
                                    String defaultValue) {
      return getAttributeValueDefault(attribute, ALLOCATION_INSTANCE, name, defaultValue);
    }

    // Not used at present
//     private static String buildNameBinderBody (String symbol) {
//         return
//             "var $lzc$old = $lzc$parent." + symbol + ";\n" +
//             "if ($lzc$bind) {\n" +
//             "  if ($debug) {\n" +
//             "    if ($lzc$old && ($lzc$old !== $lzc$node)) {\n" +
//             "      Debug.warn('Redefining %w." + symbol + " from %w to %w', \n" +
//             "        $lzc$parent, $lzc$old, $lzc$node);\n" +
//             "    }\n" +
//             "  }\n" +
//             "  $lzc$parent." + symbol + " = $lzc$node;\n" +
//             "} else if ($lzc$old === $lzc$node) {\n" +
//             "  $lzc$parent." + symbol + " = null;\n" +
//             "}\n";
//     }

    /**
     * Builds the body of a binder method for binding a global value to
     * an instance.  NOTE: [2008-10-21 ptw] In swf9, we shadow these
     * bindings in a table `global` to support the `globalValue` API for
     * looking up global ID's at runtime.
     */
    private static String buildIdBinderBody (String symbol, boolean setId) {
        return
          "#pragma " + ScriptCompiler.quote("userFunctionName=bind #" + symbol) + "\n" +
          "if ($lzc$bind) {\n" +
          "  if ($debug) {\n" +
          "    if (" + symbol + " && (" + symbol + " !== $lzc$node)) {\n" +
          "      Debug.warn('Redefining #" + symbol + " from %w to %w', \n" +
          "        " + symbol + ", $lzc$node);\n" +
          "    }\n" +
          "  }\n" +
          (setId ? ("  $lzc$node.id = " + ScriptCompiler.quote(symbol) + ";\n") : "") +
          "  " + symbol + " = $lzc$node;\n" +
          "  if ($as3) { global[" + ScriptCompiler.quote(symbol) + "] = $lzc$node; }\n" +
          "} else if (" + symbol + " === $lzc$node) {\n" +
          "  " + symbol + " = null;\n" +
          "  if ($as3) { global[" + ScriptCompiler.quote(symbol) + "] = null; }\n" +
          (setId ? ("  $lzc$node.id = null;\n") : "") +
          "}\n";
    }

    // Some attributes are just for the compiler to track the type internally,
    // we don't want to emit them to the runtime.
    private static final Set<String> INTERNAL_ATTRIBUTES = new HashSet<String>(Arrays.asList(
            "extends", "jsname", "metasym"
        ));

    /**
     * Add the attributes that are specified in the open tag.  These
     * attributes are by definition instance attributes.  If you want
     * class attributes, you have to use the longhand (attribute child
     * node) form.
     */
    void addAttributes(CompilationEnvironment env) {
        // Add source locators, if requested.  Added here because it
        // is not in the schema
        if (env.getBooleanProperty(CompilationEnvironment.SOURCELOCATOR_PROPERTY)) {
            String location = "document(" + 
                ScriptCompiler.quote(Parser.getSourceMessagePathname(element)) + 
                ")" +
                XMLUtils.getXPathTo(element);
            CompiledAttribute cattr = compileAttribute(
                element, SOURCE_LOCATION_ATTRIBUTE_NAME,
                location, ViewSchema.STRING_TYPE,
                WHEN_IMMEDIATELY);
            addProperty(SOURCE_LOCATION_ATTRIBUTE_NAME, cattr, ALLOCATION_INSTANCE);
        }

        // Encode the attributes
        for (Iterator<?> iter = element.getAttributes().iterator(); iter.hasNext(); ) {
            Attribute attr = (Attribute) iter.next();
            Namespace ns = attr.getNamespace();
            String name = attr.getName();
            String value = element.getAttributeValue(name, ns);

            // Certain attributes do not get emitted.
            if (INTERNAL_ATTRIBUTES.contains(name)) {
              continue;
            }

            if (name.equals(FONTSTYLE_ATTRIBUTE)) {
                // "bold italic", "italic bold" -> "bolditalic"
                value = FontInfo.normalizeStyleString(value, false);
            }

            if (name.toLowerCase().equals("defaultplacement")) {
                if (value != null && quotedIdentifierPat.matcher(value).matches()) {
                    String oldValue = value;
                    // strip off start and ending quotes;
                    value = value.trim();
                    value = value.substring(1, value.length()-1);
                    env.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Replacing defaultPlacement=\"" + p[0] + "\" by \"" + p[1] + "\".  For future compatibility" + ", you should make this change to your source code."
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-513", new Object[] {oldValue, value})
                        ,element);
                }
            }

            // Special case for compiling a class, the class
            // attributes are really 'meta' attributes, not
            // attributes of the class -- they will be processed by
            // the ClassModel or ClassCompiler
            if ((metamodel != null) && (metamodel.getAttribute(name, ALLOCATION_INSTANCE) != null)) {
              assert ("name".equals(name) || "extends".equals(name) || "with".equals(name)
                      || "implements".equals(name))
                : "Meta attribute " + tagName + ": " + name;
              continue;
            }

            // Warn for redefine of a flash builtin
            // TODO: [2006-01-23 ptw] What about colliding with DHTML globals?
            if (("id".equals(name) || "name".equals(name)) &&
                 (value != null &&
                  (env.getRuntime().indexOf("swf") == 0) &&
                  sFlash7Builtins.containsKey(value)))  {
                env.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="You have given the " + p[0] + " an attribute " + p[1] + "=\"" + p[2] + "\", " + "which may overwrite the Flash builtin class named \"" + p[3] + "\"."
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-532", new Object[] {getMessageName(), name, value, value})
                    ,element);
            }

            // Check that the view name is a valid javascript identifier
            if (("name".equals(name) || "id".equals(name)) &&
                (value == null || !ScriptCompiler.isIdentifier(value))) {
                CompilationError cerr = new CompilationError(
                    "The "+name+" attribute of this node,  "+ "\"" + value + "\", is not a valid javascript identifier " , element);
                throw(cerr);
            }

            Schema.Type type;
            // NOTE: [2008-05-05 ptw] These are instance attributes, by
            // definition
            AttributeSpec attrspec = parentClassModel.getAttribute(name, ALLOCATION_INSTANCE);
            if (parentClassModel.isstate) {
              // Special case for <state>'s, they can have any
              // attribute which belongs to the parent DOM node
              if (attrspec == null) {
                Element DOMparent = element.getParentElement();
                ClassModel DOMparentModel = schema.getInstanceClassModel(DOMparent);
                attrspec = DOMparentModel.getAttribute(name, ALLOCATION_INSTANCE);
              }
            }
            if (attrspec != null) {
              // The Canvas compiler depends on seeing width/height
              // unadulterated <sigh />.
              if ("canvas".equals(tagName) &&
                  ("width".equals(name) || "height".equals(name))) {
                type = ViewSchema.NUMBER_TYPE;
              } else {
                type = attrspec.type;
              }
            } else {
                String solution;
                AttributeSpec alt = parentClassModel.findSimilarAttribute(name);
                if (alt != null) {
                    String classmessage = "";
                    if (alt.source != null) {
                        classmessage = " on class "+alt.source.getName()+"\"";
                    } else {
                      classmessage = " on class "+getMessageName();
                    }
                    solution = 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="found an unknown attribute named \"" + p[0] + "\" on " + p[1] + ", however there is an attribute named \"" + p[2] + "\"" + p[3] + ", did you mean to use that?"
 */
                      org.openlaszlo.i18n.LaszloMessages.getMessage(
                        NodeModel.class.getName(),"051018-616", new Object[] {name, getMessageName(), alt.name, classmessage});
                } else {
                  solution =
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="found an unknown attribute named \"" + p[0] + "\" on " + p[1] + ", check the spelling of this attribute name"
 */
                    org.openlaszlo.i18n.LaszloMessages.getMessage(
                      NodeModel.class.getName(),"051018-624", new Object[] {name, getMessageName()});
                }
                env.warn(solution, element);
                type = ViewSchema.EXPRESSION_TYPE;
            }

            if (type == ViewSchema.EVENT_HANDLER_TYPE) {
                addHandlerFromAttribute(element, name, value);
            } else {
                if (type == ViewSchema.ID_TYPE) {
                    this.id = value;
                }
                String when = this.getAttributeValueDefault(
                    name, "when", WHEN_IMMEDIATELY);
                try {
                    CompiledAttribute cattr = compileAttribute(
                        element, name, value, type, when);
                    addProperty(name, cattr, ALLOCATION_INSTANCE);
                    // If this attribute is "id", you need to bind the
                    // id
                    if ("id".equals(name)) {
                        String symbol = value;
                        Function idbinder = new Function(
                            "$lzc$node:LzNode, $lzc$bind:Boolean=true",
                            buildIdBinderBody(symbol, true));
                        addProperty("$lzc$bind_id", idbinder, ALLOCATION_INSTANCE);
                    }
                    // Ditto for top-level name "name"
                    if (CompilerUtils.topLevelDeclaration(element) && "name".equals(name)) {
                        String symbol = value;
                        // A top-level name is also an ID, for
                        // hysterical reasons
                        // TODO: [2008-04-10 ptw] We should really
                        // just change the name element to an id
                        // element in any top-level node and be
                        // done with it.
                        Function namebinder = new Function (
                            "$lzc$node:LzNode, $lzc$bind:Boolean=true",
                            buildIdBinderBody(symbol, false));
                        addProperty("$lzc$bind_name", namebinder, ALLOCATION_INSTANCE);
                    }

                    // Check if we are aliasing another 'name'
                    // attribute of a sibling
                    if ("name".equals(name)) {
                        Element parent = element.getParentElement();
                        if (parent != null) {
                            for (Iterator<?> iter2 = parent.getChildren().iterator(); iter2.hasNext(); ) {
                                Element e = (Element) iter2.next();
                                if (!e.getName().equals("resource") && !e.getName().equals("font")
                                    && e != element && value.equals(e.getAttributeValue("name"))) {
                                    String dup_location =
                                        CompilerUtils.sourceLocationPrettyString(e);
                                    env.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes=p[0] + " has the same name=\"" + p[1] + "\" attribute as a sibling element at " + p[2]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-658", new Object[] {getMessageName(), value, dup_location})
                                        ,element);
                                }
                            }
                        }
                    }
                } catch (CompilationError e) {
                    env.warn(e);
                }
            }
        }
    }

  void addProperty(String name, Object value, String allocation, Element source) {
    if (frozen) {
      throw new CompilerImplementationError("Attempting to addProperty when NodeModel frozen");
    }

    LinkedHashMap<String, Object> lattrs;
    if (ALLOCATION_INSTANCE.equals(allocation)) {
      lattrs = this.attrs;
    } else if (ALLOCATION_CLASS.equals(allocation)) {
      lattrs = this.classAttrs;
    } else {
      throw new CompilationError("Unknown allocation: " + allocation, source);
    }
    // TODO: [2008-05-05 ptw] Make warning say whether it is a
    // class or instance property that is conflicting
    if (lattrs.containsKey(name)) {
      env.warn(
                /* (non-Javadoc)
                 * @i18n.test
                 * @org-mes="an attribute or method named '" + p[0] + "' already is defined on " + p[1]
                 */
                org.openlaszlo.i18n.LaszloMessages.getMessage(
                  NodeModel.class.getName(),"051018-682", new Object[] {name, getMessageName()})
                ,source);
    }
    if (value instanceof CompiledAttribute) {
      // Special handling for attribute with binders
      CompiledAttribute cattr = (CompiledAttribute)value;
      // The methods of a datapath constraint are moved to the
      // replicator, so must be compiled as closures
      boolean chm = "datapath".equals(name) ? false : canHaveMethods;
      if (cattr.bindername != null) {
        lattrs.put(cattr.bindername, cattr.getBinderMethod(chm));
      }
      if (cattr.dependenciesname != null) {
        lattrs.put(cattr.dependenciesname, cattr.getDependenciesMethod(chm));
      }
      lattrs.put(name, cattr.getInitialValue());
    } else {
      lattrs.put(name, value);
    }
  }

  void addProperty(String name, Object value, String allocation) {
    addProperty(name, value, allocation, element);
  }

    static String getDatasetContent(Element element, CompilationEnvironment env) {
        return getDatasetContent(element, env, false);
    }

    // For a dataset (well really for any Element), writes out the
    // child literal content as an escaped string, which could be used
    // to initialize the dataset at runtime.
    static String getDatasetContent(Element element, CompilationEnvironment env, boolean trimwhitespace) {
        // If type='http' or the path starts with http: or https:,
        // then don't attempt to include the data at compile
        // time. The runtime will have to recognize it as runtime
        // loaded URL data.
            
        if ("http".equals(element.getAttributeValue("type"))) {
            return "null";
        }

        boolean nsprefix = false;
        if ("true".equals(element.getAttributeValue("nsprefix"))) {
            nsprefix = true;
        }

        Element content = new Element("data");

        String src = element.getAttributeValue("src");
        // If 'src' attribute is a URL or null, don't try to expand it now,
        // just return. The LFC will have to interpret it as a runtime
        // loadable data URL.
        if ( (src != null) &&
             (src.startsWith("http:") ||
               src.startsWith("https:")) ) {
            return "null";
        } else if (src != null) {
            // Expands the src file content inline
            File file = env.resolveReference(element);
            try {
                Element literaldata = new org.jdom.input.SAXBuilder(false)
                    .build(file)
                    .getRootElement();
                if (literaldata == null) {
                    return "null";
                }
                literaldata.detach();
                // add the expanded file contents as child node
                content.addContent(literaldata);
            } catch (org.jdom.JDOMException e) {
                throw new CompilationError(e);
            } catch (IOException e) {
                throw new CompilationError(e);
            } catch (java.lang.OutOfMemoryError e) {
                // The runtime gc is necessary in order for subsequent
                // compiles to succeed.  The System gc is for good
                // luck.
                throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="out of memory"
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-761")
                        , element);
            }
        } else {
            // no 'src' attribute, use element inline content
            content.addContent(element.cloneContent());
        }

        // Serialize the child elements, as the local data
        org.jdom.output.XMLOutputter xmloutputter =
            new org.jdom.output.XMLOutputter();
        // strip the <dataset> wrapper

        Format fmt = Format.getRawFormat();
        fmt.setLineSeparator("\n");
        xmloutputter.setFormat(fmt);

        // If nsprefix is false, remove namespace from elements before
        // serializing, or XMLOutputter puts a "xmlns" attribute on
        // the top element in the serialized string.

        if (!nsprefix) {
            removeNamespaces(content);
        }

        if (trimwhitespace) {
            trimWhitespace(content);
        }

        String body = xmloutputter.outputString(content);
        return ScriptCompiler.quote(body);
    }

    // Recursively null out the namespace on elt and children
    static void removeNamespaces(Element elt) {
        elt.setNamespace(null);
        for (Iterator<?> iter = elt.getChildren().iterator(); iter.hasNext(); ) {
            Element child = (Element) iter.next();
            removeNamespaces(child);
        }
    }


    // Recursively removes the namespace if it matches the specified namespace.
    static void removeNamespace(Element elt, Namespace ns) {
      if (ns != null && ns == elt.getNamespace()) {
        elt.setNamespace(null);
      }

      for (Iterator<?> iter = elt.getChildren().iterator(); iter.hasNext(); ) {
        Element child = (Element) iter.next();
        removeNamespace(child, ns);
      }
    }


    // Recursively trim out the whitespace on text nodes
    static void trimWhitespace(Content elt) {
        if (elt instanceof Text) {
            ((Text) elt).setText(((Text)elt).getTextTrim());
        } else if (elt instanceof Element) {
            for (Iterator<?> iter = ((Element) elt).getContent().iterator(); iter.hasNext(); ) {
                Content child = (Content) iter.next();
                trimWhitespace(child);
            }
        }
    }

    boolean isDatapathElement(Element child) {
        return (child.getName().equals("datapath"));
    }

    /** Warn if named child tag conflicts with a declared attribute in the parent class.
     */
    void checkChildNameConflict(String parentName, Element child, CompilationEnvironment env) {
        String childName = child.getAttributeValue("name");
        String allocation = child.getAttributeValue("allocation", ALLOCATION_INSTANCE);

        if (childName != null) {
            AttributeSpec attrSpec = parentClassModel.getAttribute ( childName, allocation );
            if (attrSpec != null && attrSpec.type != ViewSchema.NODE_TYPE) {
                ClassModel attrClassModel = schema.getInstanceClassModel(attrSpec.source.getParentElement());
                String inherited = parentClassModel != attrClassModel ? "superclass " : "";
                // TODO [2007-09-26 hqm] i18n this
                env.warn(
                    "The child node <"+child.getName()+" name='"+childName+"'>" +
                    " conflicts with <attribute name='"+childName+"' type='"+attrSpec.type+"'> of "+inherited+attrClassModel+".  " +
                    "Either use a different name, or if you intend the child to be assigned to the attribute, declare the " +
                    "attribute type to be `node`.",
                    element);
            }
        }
    }



    void addChildren(CompilationEnvironment env) {
        // Encode the children
        for (Iterator<?> iter = element.getChildren().iterator(); iter.hasNext(); ) {
            ElementWithLocationInfo child = (ElementWithLocationInfo) iter.next();
            if (!schema.canContainElement(element, child)) {
                // If this element is allowed to contain  HTML content, then
                // we don't want to warn about encountering an HTML child element.
                if (!( schema.hasTextContent(element) && ViewSchema.isHTMLElement(child))) {
                    env.warn(
                        // TODO [2007-09-26 hqm] i18n this
                        "The tag '" + child.getName() +
                        "' cannot be used as a child of " + tagName,
                        element);
                }
            }
            try {
                 if (child.getName().equals("data")) {
                    checkChildNameConflict(tagName, child, env);
                    // literal data
                    addLiteralDataElement(child);
                } else if (isPropertyElement(child)) {
                    addPropertyElement(child);
                } else if (ViewSchema.isHTMLElement(child)) {
                    ; // ignore; the text compiler will handle this
                } else if (ViewSchema.isMetaElement(child)) {
                    ; // ignore doc nodes.
                } else {
                    checkChildNameConflict(tagName, child, env);
                    NodeModel childModel = elementAsModel(child, schema, env);
                    if (childModel.parentClassModel.isdatapath) {
                      this.datapath = childModel;
                    } else {
                      children.add(childModel);
                      // Declare the child name (if any) as a property
                      // of the node so that references to it from
                      // methods can be resolved at compile-time
                      String childName = child.getAttributeValue("name");
                      if (childName != null) {
                        addProperty(childName, null, ALLOCATION_INSTANCE, child);
                      }
                      totalSubnodes += childModel.totalSubnodes();
                    }
                }
            } catch (CompilationError e) {
                env.warn(e);
            }
        }
    }

  void addStateChildren(CompilationEnvironment env ) {
    // Check for each child, if it is a subclass of <state>.
    // If so, we need to declare any named children as attributes,
    // so the swf9 compiler won't complain about references to them.
    for (NodeModel childModel : children) {
      if (childModel.parentClassModel.isstate) {
        declareNamedChildren(childModel);
      }
    }
  }

  /** Hoist named children declarations from a state into the parent.

   Take all named children of this NodeModel (including named children
   in the classmodel) and declare them as attributes, so the swf9
   compiler will permit references to them at compile time.
  */
  void declareNamedChildren(NodeModel model) {
    List<String> childnames = collectNamedChildren(model);
    for (String childname : childnames) {
      if (!attrs.containsKey(childname)) {
        addProperty(childname, null, ALLOCATION_INSTANCE);
      }
    }
  }

  // Returns a list of names of all named children of this instance and
  // those of its superclasses
  List<String> collectNamedChildren(NodeModel model) {
    List<String> names = new ArrayList<String>();
    // iterate over children, getting named ones. If a child is a <state>, recurse into it to
    // hoist any named children up.
    for (NodeModel child : model.children) {
      if (child.parentClassModel.isstate) {
         List<String> childnames = collectNamedChildren(child);
         // merge in returned list with names
         names.addAll(childnames);
      } else {
        String childNameAttr = child.element.getAttributeValue("name");
        if (childNameAttr != null) {
          names.add(childNameAttr);
        }
      }
    }
    // Then recurse over superclasses, up to <state>
    ClassModel classModel = model.parentClassModel;
     if (classModel.hasNodeModel()) {
         List<String> supernames = collectNamedChildren(classModel.nodeModel);
         // merge in returned list with names
         names.addAll(supernames);
       }
     return names;
  }



   void warnIfHasChildren(Element element) {
     if (element.getChildren().size() > 0) {
       CompilationError cerr = new CompilationError(
           "The "+tagName+" tag cannot have child tags in this context", element);
       throw(cerr);        
     }
   }

    void addPropertyElement(Element element) {
      warnIfHasChildren(element);
      String tagName = element.getName();
      if ("method".equals(tagName)) {
        addMethodElement(element);
      } else if ("handler".equals(tagName)) {
        addHandlerElement(element);
      } else if ("event".equals(tagName)) {
        addEventElement(element);
      } else if ("attribute".equals(tagName)) {
        addAttributeElement(element);
      } else if ("setter".equals(tagName)) {
        addSetterElement(element);
      } else if ("passthrough".equals(tagName)) {
        addPassthroughElement(element);
      }
    }

    /** Compile a <passthrough> block for a node.
     *
     * Transforms to a #passthrough compiler pragma in the script output
     */
    void addPassthroughElement(Element element) {
      passthroughBlock = element.getText();
      String when = element.getAttributeValue("when");
      if (when != null) {
        when = when.trim();
        if (!ScriptCompiler.isIdentifier(when)) {
          throw new CompilationError(
            "'when' attribute for passthrough element must be a valid javascript identifier",
            element);
        }
        passthroughBlockWhen = when;
      }
    }

    /** Defines an event handler.
     *
     * <handler name="eventname" [method="methodname"]>
     *   [function body]
     * </handler>
     *
     * This can do a compile time check to see if eventname is declared or
     * if there is an attribute FOO such that name="onFOO".
     */
    void addHandlerElement(Element element) {
        ViewSchema.checkHandlerAttributes(element, env);
        String method = element.getAttributeValue("method");
        // event name
        String event = element.getAttributeValue("name");
        String args = CompilerUtils.attributeLocationDirective(element, "args") +
            // Handlers get called with one argument, default to
            // ignoring that
            XMLUtils.getAttributeValue(element, "args", "$lzc$ignore");
        if ((event == null || !ScriptCompiler.isIdentifier(event))) {
            env.warn("handler needs a non-null name attribute");
            return;
        }
        String parent_name =
            element.getParentElement().getAttributeValue("id");
        String reference = element.getAttributeValue("reference");
        if (reference != null && emptyStringPat.matcher(reference).matches()) {
          throw new CompilationError("The 'reference' attribute value cannot be an empty string"
                                     , element);
        }
        String body = element.getText();
        if (body.trim().length() == 0) { body = null; }
        // If non-empty body AND method name are specified, flag an error
        // If non-empty body, pack it up as a function definition
        if (body != null) {
            if (method != null) {
                env.warn("you cannot declare both a 'method' attribute " +
                         "*and* a function body on a handler element",
                         element);
            }
            body = CompilerUtils.attributeLocationDirective(element, "text") + body;
        }
        addHandlerInternal(element, parent_name, event, method, args, body, reference);
    }

    /**
     * An event handler defined in the open tag
     */
    void addHandlerFromAttribute(Element element, String event, String body) {
        String parent_name = element.getAttributeValue("id");
        // Handlers get called with one argument, default to ignoring
        // that
        addHandlerInternal(element, parent_name, event, null, "$lzc$ignore", body, null);
    }

    /**
     * Adds a handler for `event` to be handled by `method`.  If
     * `body` is non-null, adds the method too.  If `reference` is
     * non-null, adds a method to compute the sender.
     *
     * @devnote: For backwards-compatibility, you are allowed to pass
     * in both a `method` (name) and a `body`.  This supports the old
     * method syntax where you were allowed to specify the event to be
     * handled and the name of the method for the body of the handler.
     */
    void addHandlerInternal(Element element, String parent_name, String event,  String method, String args, String body, String reference) {
        if (body == null && method == null) {
            env.warn("Refusing to compile an empty handler, you should declare the event instead", element);
            return;
        }

        if (parent_name == null) {
            parent_name = CompilerUtils.attributeUniqueName(element, "handler");
        }
        String referencename = null;
        String srcloc =  CompilerUtils.sourceLocationDirective(element, true);
        // delegates is only used to determine whether to
        // default clickable to true.  Clickable should only
        // default to true if the event handler is attached to
        // this view.
        if (reference == null) {
            delegates.put(event, Boolean.TRUE);
        } else {
            // TODO [2008-05-20 ptw] Replace the $debug code with actual
            // type declarations if/when they are enforced in all run
            // times
            referencename = env.methodNameGenerator.next();
            String pragmas = "";
            // Note: for debugging, will be ignored by non-debug
            pragmas += "#pragma " + ScriptCompiler.quote("userFunctionName=get " + reference);
            String refbody = "return (#beginAttribute" + srcloc + reference + CompilerUtils.endSourceLocationDirective + "#endAttribute);";
            Function referencefn;
            if (canHaveMethods) {
                referencefn = new Method(referencename, "", "", pragmas, refbody, srcloc, null);
            } else {
                pragmas += "\n#pragma 'withThis'\n";
                referencefn = new Function(referencename, "", "", pragmas, refbody, srcloc);
            }
            // Add reference computation as a method (so it can have
            // 'this' references work)
            addProperty(referencename, referencefn, ALLOCATION_INSTANCE, element);
        }

        if (body != null) {
            String pragmas = "#beginContent\n";
            if (method == null) {
                method = env.methodNameGenerator.next();
                // Note: for debugging, will be ignored by non-debug
                pragmas += "#pragma " + ScriptCompiler.quote(
                  "userFunctionName=handle " +
                  ((reference != null) ? (reference + ".") : "") +
                  event) +"\n";
            }
            body = body + "\n#endContent\n";
            Function fndef;
            if (canHaveMethods) {
                fndef = new Method(method, args, "", pragmas, body, srcloc, null);
            } else {
                pragmas += "#pragma 'withThis'\n";
                fndef = new Function(method, args, "", pragmas, body, srcloc);
            }
            // Add handler as a method
            addProperty(method, fndef, ALLOCATION_INSTANCE, element);
        }

        // Put everything into the delegate list
        delegateList.add(ScriptCompiler.quote(event));
        delegateList.add(ScriptCompiler.quote(method));
        if (reference != null) {
            delegateList.add(ScriptCompiler.quote(referencename));
        } else {
            delegateList.add("null");
        }
    }

    void addMethodElement(Element element) {
        ViewSchema.checkMethodAttributes(element, env);
        String name = element.getAttributeValue("name");
        String event = element.getAttributeValue("event");
        String allocation = XMLUtils.getAttributeValue(element, "allocation", ALLOCATION_INSTANCE);
        String args = CompilerUtils.attributeLocationDirective(element, "args") +
            XMLUtils.getAttributeValue(element, "args", "");
        String body = element.getText();
        String returnType = element.getAttributeValue("returns");

        if (name == null || !ScriptCompiler.isIdentifier(name)) {
            env.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="method needs a non-null name or event attribute"
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-832")
);
            return;
        }
        if (name != null && sDeprecatedMethods.containsKey(name)) {
            String oldName = name;
            String newName = (String) sDeprecatedMethods.get(name);
            name = newName;
            env.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes=p[0] + " is deprecated.  " + "This method will be compiled as <method name='" + p[1] + "' instead.  " + "Please update your sources."
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-846", new Object[] {oldName, newName})
                ,element);
        }

        // TODO: Remove after 5.0
        if (event != null) {
            throw new CompilationError("The `event` property of methods is no longer supported.  Please update your source to use the `<handler>` tag.",
                     element);
        }
        addMethodInternal(name, args, returnType, body, element, allocation);
    }

  static String setterPrefix = "$lzc$set_";
  void addMethodInternal(String name, String args, String returnType, String body, Element element, String allocation) {
        // Override will be required if there is an inherited method
        // of the same name.
        boolean override = false;
        boolean explicitOverride = false;

        // The user may know better than any of us
        String overrideAttr = element.getAttributeValue("override");
        if (overrideAttr != null) {
            explicitOverride = true;
            override = "true".equals(overrideAttr);
        }

        if (ALLOCATION_CLASS.equals(allocation)) {
            // No additional override-check required for class-allocated methods
        }
        // This gets methods from the schema, in particular, the LFC
        // interface
        else if (parentClassModel.getAttribute(name, ALLOCATION_INSTANCE) != null) {
          // This is not possible for setters, so we don't need this
          // additional check for the other cases
          if (explicitOverride && !override) {
              env.warn("Method "+tagName+"."+name+" is overriding a superclass method"
                  + " but was set to 'override=false'" , element);
          }
          override = true;
        }
        // This gets methods the compiler has added, in
        // particular, setter methods
        else if (parentClassModel.getMergedMethods().containsKey(name)) {
          override = true;
        }
        // We have to be a little tricky to find potential setters
        // from <interface><attribute setter="..."></interface>
        else if (name.startsWith(setterPrefix)) {
          AttributeSpec superAttr = parentClassModel.getAttribute(name, ALLOCATION_INSTANCE);
          if ((superAttr != null) && (superAttr.setter != null)) {
            override = true;
          }
        }
        boolean isfinal = "true".equals(element.getAttributeValue("final"));

        // Just check method declarations on regular node (if an
        // override was detected!).  Method declarations inside of
        // class definitions will be already checked elsewhere, in the
        // call from ClassCompiler.updateSchema to
        // schema.addClassModel
        if (override && (! isclassdef)) {
            parentClassModel.checkInstanceMethodDeclaration(name, env);
        }

        String name_loc =
            (name == null ?
             CompilerUtils.attributeLocationDirective(element, "handler") :
             CompilerUtils.attributeLocationDirective(element, "name"));
        Function fndef;
        String pragmas = "\n#beginContent\n";
        body = body + "\n#endContent";
        if (canHaveMethods) {
            String adjectives = "";
            if (override) { adjectives += " override"; }
            if (isfinal) { adjectives += " final"; }
            fndef = new Method(name, args, returnType, pragmas, body, name_loc, adjectives);
        } else {
            if (ALLOCATION_INSTANCE.equals(allocation)) {
              pragmas += "\n#pragma 'withThis'\n";
            }
            fndef = new Function(name, args, returnType, pragmas, body, name_loc);
        }
        addProperty(name, fndef, allocation, element);
    }

    // Pattern matcher for '$once{...}' style constraints
    private static final Pattern constraintPat = Pattern.compile("^\\s*\\$(\\w*)\\s*\\{(.*)\\}\\s*");

    CompiledAttribute compileAttribute(
        Element source, String name,
        String value, Schema.Type type,
        String when)
        {
            String parent_name = source.getAttributeValue("id");
            if (parent_name == null) {
                parent_name =  CompilerUtils.attributeUniqueName(source, name);
            }
            String canonicalValue = null;
            boolean warnOnDeprecatedConstraints = true;

            if (value == null) {
                throw new RuntimeException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="value is null in " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-956", new Object[] {source})
);
            }

            Matcher m = constraintPat.matcher(value);
            if (m.matches()) {
                // extract out $when{value}
                when = m.group(1);
                value = m.group(2);
                // Expressions override any when value, default is
                // constraint
                if (when.equals("")) {
                    when = WHEN_ALWAYS;
                }
                if (when.equals(WHEN_STYLE)) {
                  // Handle old-school `$style{'prop'}` value
                  // That value is a constant will be checked later
                  // But we need to record the property now
                  CSSAttributeProperties.put(name, value);
                }
            } else if (type == ViewSchema.XML_LITERAL) {
                value = "LzDataElement.stringToLzData("+value+")";
            } else if (type == ViewSchema.COLOR_TYPE) {
                if (when.equals(WHEN_IMMEDIATELY)) {
                    try {
                        value = "0x" +
                            Integer.toHexString(ViewSchema.parseColor(value));
                    } catch (ColorFormatException e) {
                        when = WHEN_ONCE;
                        value = "LzColorUtils.convertColor(" + ScriptCompiler.quote(value) + ")";
                    }
                } else {
                  value = "LzColorUtils.convertColor(" + ScriptCompiler.quote(value) + ")";
                }
            } else if (type == ViewSchema.CSS_TYPE) {
                if (when.equals(WHEN_IMMEDIATELY)) {
                    try {
                        Map<String, Object> cssProperties = new CSSParser
                            (new AttributeStream(source, name, value)).Parse();
                        for (Map.Entry<String, Object> entry : cssProperties.entrySet()) {
                            Object mv = entry.getValue();
                            if (mv instanceof String) {
                                entry.setValue(ScriptCompiler.quote((String) mv));
                            }
                        }
                        canonicalValue = ScriptCompiler.objectAsJavascript(cssProperties);
                    } catch (org.openlaszlo.css.parser.ParseException e) {
                        // Or just set when to WHEN_ONCE and fall
                        // through to TODO?
                        throw new CompilationError(e);
                    } catch (org.openlaszlo.css.parser.TokenMgrError e) {
                        // Or just set when to WHEN_ONCE and fall
                        // through to TODO?
                        throw new CompilationError(e);
                    }
                }
                // TODO: [2003-05-02 ptw] Wrap non-constant styles in
                // runtime parser
            } else if (type == ViewSchema.STRING_TYPE) {
                // Immediate string types are auto-quoted, but must
                // _first_ be parsed as ES strings!
                if (when.equals(WHEN_IMMEDIATELY)) {
                    org.openlaszlo.sc.parser.ASTLiteral literal = new org.openlaszlo.sc.parser.ASTLiteral(0);
                    literal.setStringValue(value);
                    value = ScriptCompiler.quote((String)literal.getValue());
                }
            } else if (type == ViewSchema.XML_CDATA_TYPE
                       || type == ViewSchema.XML_CONTENT_TYPE
                       || type == ViewSchema.TOKEN_TYPE
                       || type == ViewSchema.ID_TYPE) {
                // Immediate text, html, token, and id types are
                // auto-quoted.
                //
                // NOTE: [2010-06-16 ptw] (LPP-9027) For the time being, 'html'
                // and 'text' are treated as synonyms
                if (when.equals(WHEN_IMMEDIATELY)) {
                    value = ScriptCompiler.quote(value);
                }
            } else if ((type == ViewSchema.EXPRESSION_TYPE) ||
                       (type == ViewSchema.BOOLEAN_TYPE) ||
                       (type == ViewSchema.NODE_TYPE)) {
                // No change currently, possibly analyze expressions
                // and default non-constant to when="once" in the
                // future
            } else if (type == ViewSchema.INHERITABLE_BOOLEAN_TYPE) {
                // change "inherit" to null and pass true/false through as expression
                if ("inherit".equals(value)) {
                    value = "null";
                } else if ("true".equals(value)) {
                    value = "true";
                } else if ("false".equals(value)) {
                    value = "false";
                } else {
                    // TODO [hqm 2007-0] i8nalize this message
                    env.warn("attribute '"+name+"' must have the value 'true', 'false', or 'inherit'",
                             element);
                }
            } else if (type == ViewSchema.NUMBER_TYPE) {
                // No change currently, possibly analyze expressions
                // and default non-constant to when="once" in the
                // future
            } else if (type == ViewSchema.NUMBER_EXPRESSION_TYPE ||
                       type == ViewSchema.SIZE_EXPRESSION_TYPE) {
                // if it's a number that ends in percent:
                if (value.trim().endsWith("%")) {
                    String numstr = value.trim();
                    numstr = numstr.substring(0, numstr.length() - 1);
                    try {
                        double scale = new Float(numstr).floatValue() / 100.0;
                        warnOnDeprecatedConstraints = false;
                        String referenceAttribute = name;
                        if ("x".equals(name)) {
                            referenceAttribute = "width";
                        } else if ("y".equals(name)) {
                            referenceAttribute = "height";
                        }
                        value = "immediateparent." + referenceAttribute;
                        if (scale != 1.0) {
                            // This special case doesn't change the
                            // semantics, but it generates shorter (since
                            // the sc doesn't fold constants) and more
                            // debuggable code
                            value += "\n * " + scale;
                        }
                        // fall through to the reference case
                    } catch (NumberFormatException e) {
                        // fall through
                    }
                }
                // if it's a literal, treat it the same as a number
                try {
                    new Float(value); // for effect, to generate the exception
                    when = WHEN_IMMEDIATELY;
                } catch (NumberFormatException e) {
                    // It's not a constant, unless when has been
                    // specified, default to a constraint
                    if (when.equals(WHEN_IMMEDIATELY)) {
                        if (warnOnDeprecatedConstraints) {
                            env.warn(
                                "Use " + name + "=\"${" + value + "}\" instead.",
                                element);
                        }
                        when = WHEN_ALWAYS;
                    }
                }
            } else if (type == ViewSchema.EVENT_HANDLER_TYPE) {
              // Someone said <attribute name="..." ... /> instead of
              // <event name="..." />
              throw new CompilationError(element, name, 
                                         new Throwable ("'" + name + "' is an event and may not be redeclared as an attribute"));
            } else if (type == ViewSchema.REFERENCE_TYPE) {
                // type="reference" is defined to imply when="once"
                // since reference expressions are unlikely to
                // evaluate correctly at when="immediate" time
                if (when.equals(WHEN_IMMEDIATELY)) {
                    when = WHEN_ONCE;
                }
            } else if (type == ViewSchema.METHOD_TYPE) {
                // methods are emitted elsewhere
            } else if (type instanceof ViewSchema.UserType) {
                // Immediate user-defined types are auto-quoted.  At
                // least until we load them into the compiler rhino
                // engine and run acceptTypeValue in the compiler.
                if (when.equals(WHEN_IMMEDIATELY)) {
                    value = ScriptCompiler.quote(value);
                }
            } else {
                throw new RuntimeException("unknown schema datatype " + type);
            }

            if (canonicalValue == null)
                canonicalValue = value;

            return new CompiledAttribute(name, type, canonicalValue, when, source, env);
        }

    static final Schema.Type EVENT_TYPE = Schema.newType("LzEvent");

    /* Handle the <event> tag
     * example: <event name="onfoobar"/>
    */
    void addEventElement(Element element) {
        ViewSchema.checkEventAttributes(element, env);
        String name = element.getAttributeValue("name");
        if (! parentClassModel.isbuiltin) {
          try {
              name = ElementCompiler.requireIdentifierAttributeValue(element, "name");
          } catch (MissingAttributeException e) {
              throw new CompilationError(
                  /* (non-Javadoc)
                   * @i18n.test
                   * @org-mes="'name' is a required attribute of <" + p[0] + "> and must be a valid identifier"
                   */
                  org.openlaszlo.i18n.LaszloMessages.getMessage(
                      NodeModel.class.getName(),"051018-1157", new Object[] {"event"})
                  , element);
          }
        }

        // An event is really just an attribute with an implicit
        // default (sentinal) value
        CompiledAttribute cattr =
          new CompiledAttribute(name, EVENT_TYPE, "LzDeclaredEvent", WHEN_IMMEDIATELY, element, env);
        addProperty(name, cattr, ALLOCATION_INSTANCE, element);
    }

    void addAttributeElement(Element element) {
        String name = element.getAttributeValue("name");
        if (! parentClassModel.isbuiltin) {
          try {
              name = ElementCompiler.requireIdentifierAttributeValue(element, "name");
          } catch (MissingAttributeException e) {
              throw new CompilationError(
  /* (non-Javadoc)
   * @i18n.test
   * @org-mes="'name' is a required attribute of <" + p[0] + "> and must be a valid identifier"
   */
              org.openlaszlo.i18n.LaszloMessages.getMessage(
                  NodeModel.class.getName(),"051018-1157", new Object[] {"attribute"})
                      , element);
          }
        }

        // Check that all attributes on this <attribute> are known names
        ViewSchema.checkAttributeAttributes(element, env);

        String value = element.getAttributeValue("value");
        String when = element.getAttributeValue("when");
        String typestr = element.getAttributeValue("type");
        String allocation = XMLUtils.getAttributeValue(element, "allocation", ALLOCATION_INSTANCE);
        String style = element.getAttributeValue("style");
        Element parent = element.getParentElement();
        String parent_name = parent.getAttributeValue("id");


        if (parent_name == null) {
            parent_name = CompilerUtils.attributeUniqueName(element, name);
        }

        // Default when according to parent
        if (when == null) {
            when = this.getAttributeValueDefault(
                name, "when", WHEN_IMMEDIATELY);
        }

        Schema.Type type = null;
        Schema.Type parenttype = null;
        boolean forceOverride = false;

        // Class attributes are not inherited, hence do not override
        // and do not have to be congruent in type
        if (ALLOCATION_INSTANCE.equals(allocation)) {
          AttributeSpec parentAttrSpec = parentClassModel.getAttribute(name, allocation);
          // If attribute is not defined on parent, leave parenttype
          // null.  The user can define it however they like.
          if (parentAttrSpec != null) {
            forceOverride = (! parentAttrSpec.isfinal);
            parenttype = parentAttrSpec.type;
          }
        }

        if (typestr == null) {
            // Did user supply an explicit attribute type?
            //  No.  Default to parent type if there is one, else
            // EXPRESSION type.
            if (parenttype == null) {
                type = ViewSchema.EXPRESSION_TYPE;
            } else {
                type = parenttype;
            }
            // TODO: LFC classes (other than Canvas) do not have
            // attribute or CSS descriptors, so we have to fake it
            // here.  We need a LZS syntax for declaring attributes...
            if ((type != ViewSchema.EXPRESSION_TYPE) &&
                (parentClassModel.isbuiltin
                 || (! parentClassModel.getNodeModel(env).CSSAttributeTypes.containsKey(name)))) {
              CSSAttributeTypes.put(name, ScriptCompiler.quote("" + type));
            }
        } else {
            // parse attribute type and compare to parent type
            type = schema.getTypeForName(typestr, element);

            // If we are trying to declare the attribute with a
            // conflicting type to the parent, throw an error
            if (!forceOverride && parenttype != null && type != parenttype) {
                env.warn(
                    new CompilationError(
                        element,
                        name,
                        new Throwable(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="In element '" + p[0] + "' attribute '" + p[1] + "' with type '" + p[2] + "' is overriding parent class attribute with same name but different type: " + p[3]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                NodeModel.class.getName(),"051018-1227", new Object[] {parent.getName(), name, type.toString(), parenttype.toString()}))
                        )
                    );
            }

            if (type != ViewSchema.EXPRESSION_TYPE) { CSSAttributeTypes.put(name, ScriptCompiler.quote("" + type)); }
        }



        // Warn if we are overidding a method, handler, or other function
        if (!forceOverride &&
            (parenttype == ViewSchema.METHOD_TYPE ||
             parenttype == ViewSchema.EVENT_HANDLER_TYPE ||
             parenttype == ViewSchema.REFERENCE_TYPE)) {
            env.warn( "In element '" + parent.getName() 
                      + "' attribute '" +  name 
                      + "' is overriding parent class attribute which has the same name but type: "
                      + parenttype.toString(),
                      element);
        }

        CompiledAttribute cattr;
        // Value may be null if attribute is only declared
        if (value != null) {
            cattr = compileAttribute(element, name, value, type, when);
        } else {
            cattr = new CompiledAttribute(name, type, null, WHEN_IMMEDIATELY, element, env);
        }


        if (style == null) {
          if (element.getAttributeValue("inherit") != null) {
            env.warn("In element '" + parent.getName() 
                     + "' attribute '" +  name 
                     + "' has an 'inherit' property but no 'style' property",
                      element);
          }
          if (element.getAttributeValue("expander") != null) {
            env.warn("In element '" + parent.getName() 
                     + "' attribute '" +  name 
                     + "' has an 'expander' property but no 'style' property",
                      element);
          }
        } else {
            CSSAttributeProperties.put(name, ScriptCompiler.quote(style));
            // style attributes take precedent over value attributes
            value = "$style{" + ScriptCompiler.quote(style) + "}";
            // check for inherit, if specified, add it.  It can
            // override the default in either direction.
            String inherit = element.getAttributeValue("inherit");
            if (inherit != null) {
              CSSPropertyInheritable.put(style, Boolean.valueOf("true".equals(inherit)));
            }
            // check for expander, if specified, add it.  An attribute
            // with a style expander does not get a style binder.  It
            // is given it's default value and the expanded attributes
            // must update the value as appropriate.
            String expander = element.getAttributeValue("expander");
            if (expander != null) {
              CSSPropertyExpanders.put(style, ScriptCompiler.quote(expander));
            } else {
              // style attributes take precedent over value attributes
              value = "$style{" + ScriptCompiler.quote(style) + "}";
              // preserve the original value in case the style match fails...
              Object origvalue = cattr.getInitialValue();
              String origvalueexpression;
              if (origvalue instanceof NodeModel.BindingExpr) {
                LinkedHashMap<String, Object> lattrs;
                if (ALLOCATION_INSTANCE.equals(allocation)) {
                  lattrs = this.attrs;
                } else if (ALLOCATION_CLASS.equals(allocation)) {
                  lattrs = this.classAttrs;
                } else {
                  throw new CompilationError("Unknown allocation: " + allocation, element);
                }

                // add dependancy methods
                if (cattr.bindername != null) {
                  lattrs.put(cattr.bindername, cattr.getBinderMethod(false));
                }
                if (cattr.dependenciesname != null) {
                  lattrs.put(cattr.dependenciesname, cattr.getDependenciesMethod(false));
                }

                origvalueexpression = ((NodeModel.BindingExpr)origvalue).getExpr();
              } else {
                origvalueexpression = (String)origvalue;
              }

              // replace compiled attr with the new style expression
              cattr = compileAttribute(element, name, value, type, when);
              CSSAttributeFallbacks.put(name, origvalueexpression);
            }
        }

        if (ALLOCATION_CLASS.equals(allocation) && cattr.when != WHEN_IMMEDIATELY) {
          throw new CompilationError("You cannot use constraints or styles with an attribute "
                                     + "that has 'class' (static) allocation",
                                     element);
        }

        addProperty(name, cattr, allocation, element);

        // Add entry for attribute setter function
        String setter = element.getAttributeValue("setter");
        if (setter != null) {
          addSetterFromAttribute(element, name, setter, allocation);
        }
    }

    /** Defines a setter
     *
     * <setter name="attr-name" [args="attr-name"]>
     *   [function body]
     * </handler>
     *
     * This defines a setter for the attribute named `attr-name` that
     * will be invoked by setAttribute.
     *
     * TODO [2008-07-25 ptw] Reconcile how this works with storing the
     * state in an attribute of the same name vs. having real
     * setter/getter pairs.
     */
    void addSetterElement(Element element) {
        ViewSchema.checkSetterAttributes(element, env);
        String attribute = element.getAttributeValue("name");
        if ((attribute == null || !ScriptCompiler.isIdentifier(attribute))) {
            env.warn("setter needs a non-null name attribute");
            return;
        }
        String args = CompilerUtils.attributeLocationDirective(element, "args") +
            // Setters get called with one argument.  The default is
            // for the argument to have the same name as the attribute
            // this is the setter for
            XMLUtils.getAttributeValue(element, "args", attribute);
        String allocation = XMLUtils.getAttributeValue(element, "allocation", ALLOCATION_INSTANCE);
        String body = element.getText();
        if (body.trim().length() == 0) { body = null; }
        if (body != null) {
          body = CompilerUtils.attributeLocationDirective(element, "text") + body;
        }
        addSetterInternal(element, attribute, args, body, allocation);
    }

    /**
     * A setter defined in the open tag
     */
    void addSetterFromAttribute(Element element, String attribute, String body, String allocation) {
      // By default the argument to the setter method is the same name
      // as the attribute it is the setter for
      addSetterInternal(element, attribute, attribute, body, allocation);
    }

    /**
     * Adds a setter method for `attribute` with body `body`.
     */
    void addSetterInternal(Element element, String attribute, String args, String body, String allocation) {
      if (body == null) {
        env.warn("Refusing to compile an empty setter", element);
        return;
      }
      // By convention setters are put in the 'lzc' namespace with
      // the name set_<property name> NOTE: LzNode#applyArgs and
      // #setAttribute depend on this convention to find setters
      String settername = "$lzc$" + "set_" + attribute;
      String pragmas = "";
      // Note: for debugging, will be ignored by non-debug
      pragmas += "#pragma " + ScriptCompiler.quote("userFunctionName=set " + attribute) + "\n";
      addMethodInternal(settername, args, "", pragmas + body, element, allocation);
      // This is just for nice error messages
      if (setters.get(attribute) != null) {
        env.warn(
          "a setter for attribute named '"+attribute+
          "' is already defined on "+getMessageName(),
          element);
      }
      setters.put(attribute, ScriptCompiler.quote(settername));
    }

    /* Handle a <data> tag.
     * If there is more than one immediate child data node at the top level, signal a warning.
     */
    void addLiteralDataElement(Element element) {
        String name = element.getAttributeValue("name");

        if (name == null) {
            name = "initialdata";
        }

        boolean trimWhitespace = "true".equals(element.getAttributeValue("trimwhitespace"));

        String xmlcontent = getDatasetContent(element, env, trimWhitespace);

        CompiledAttribute cattr = compileAttribute(element,
                                                   name,
                                                   xmlcontent,
                                                   ViewSchema.XML_LITERAL,
                                                   WHEN_IMMEDIATELY);

        addProperty(name, cattr, ALLOCATION_INSTANCE, element);
    }

        

    boolean hasAttribute(String name) {
        return attrs.containsKey(name);
    }

    void removeAttribute(String name) {
        attrs.remove(name);
    }

    void setAttribute(String name, Object value) {
        attrs.put(name, value);
    }

    boolean hasClassAttribute(String name) {
        return classAttrs.containsKey(name);
    }

    void removeClassAttribute(String name) {
        classAttrs.remove(name);
    }

    void setClassAttribute(String name, Object value) {
        classAttrs.put(name, value);
    }

    void addText() {
      boolean hasTextAttr = (parentClassModel.getAttribute("text", ALLOCATION_INSTANCE) != null);
      if (hasTextAttr) {
        if (schema.hasHTMLContent(element)) {
          String text = TextCompiler.getHTMLContent(element, env);
          if (text.length() != 0) {
            if (!attrs.containsKey("text")) {
              addProperty("text", ScriptCompiler.quote(text), ALLOCATION_INSTANCE);
            } else {
              env.warn("Element '"+element.getName()+ "' has both an explicit text attribute value (" + element.getAttributeValue("text") +
                       "), and text content ("+ text + "). The attribute value will be used.", element);
            }
          }
        } else if (schema.hasTextContent(element)) {
          String text;
          // The current inputtext component doesn't understand
          // HTML, but we'd like to have some way to enter
          // linebreaks in the source.
          text = TextCompiler.getInputText(element);
          if (text.length() != 0) {
            if (!attrs.containsKey("text")) {
              addProperty("text", ScriptCompiler.quote(text), ALLOCATION_INSTANCE);
            } else {
              env.warn("Element '"+element.getName()+ "' has both an explicit text attribute value (" + element.getAttributeValue("text") +
                       "), and text content ("+ text + "). The attribute value will be used.", element);
            }
          }
        }
      }
    }

  // Hard to believe there is not a primitive operation on Map to do
  // this...
  private <K, V> void addNew (Map<K, V> child, Map<K, V> parent) {
     for (Map.Entry<K, V> entry : parent.entrySet()) {
         if (! child.containsKey(entry.getKey())) {
             child.put(entry.getKey(), entry.getValue());
         }
     }
  }

  private Map<String, Map<String, String>> attributeDescriptor = null;
  Map<String, Map<String, String>> getAttributeDescriptor() {
    if (attributeDescriptor != null) { return attributeDescriptor; }
    NodeModel parentNodeModel = parentClassModel.getNodeModel(env);
    boolean inherit =  (CSSAttributeProperties.isEmpty() &&
                        CSSAttributeTypes.isEmpty() &&
                        CSSAttributeFallbacks.isEmpty());

    // System.err.println("getAttributeDescriptor: " + parentClassModel.toString()
    //                    + " (" + parentClassModel.className +  (frozen ? ", frozen" : "") + ")"
    //                    + ((parentNodeModel != null)
    //                       ? (" Parent: "
    //                          + parentNodeModel.parentClassModel.toString()
    //                          + " (" + parentNodeModel.parentClassModel.className +  (parentNodeModel.frozen ? ", frozen" : "") + ")"
    //                          )
    //                       : ""));
    // System.err.println("Properties local: " + CSSAttributeProperties.keySet()
    //                    + " inherit: " + ((parentNodeModel != null) ? parentNodeModel.CSSAttributeProperties.keySet().toString() : ""));
    // System.err.println("Types local: " + CSSAttributeTypes.keySet()
    //                    + " inherit: " + ((parentNodeModel != null) ? parentNodeModel.CSSAttributeTypes.keySet().toString() : ""));
    // System.err.println("Fallbacks local: " + CSSAttributeFallbacks.keySet()
    //                    + " inherit: " + ((parentNodeModel != null) ? parentNodeModel.CSSAttributeFallbacks.keySet().toString() : ""));

    if (parentNodeModel != null) {
      // Ensure parent is merged -- may not be for interfaces
      parentNodeModel.getAttributeDescriptor();
      addNew(CSSAttributeProperties, parentNodeModel.CSSAttributeProperties);
 // frank commoent this line
//      addNew(CSSAttributeTypes, parentNodeModel.CSSAttributeTypes);
      addNew(CSSAttributeFallbacks, parentNodeModel.CSSAttributeFallbacks);
    }

    // TODO:  Until builtin classes actually store their descriptors,
    // we can't actually inherit them
    if (inherit && (! parentClassModel.isbuiltin)) { return null; }

    attributeDescriptor = new LinkedHashMap<String, Map<String, String>>();
    if (! CSSAttributeProperties.isEmpty()) {
      attributeDescriptor.put("properties", CSSAttributeProperties);
    }
    if (! CSSAttributeTypes.isEmpty()) {
      attributeDescriptor.put("types", CSSAttributeTypes);
    }
    if (! CSSAttributeFallbacks.isEmpty()) {
      attributeDescriptor.put("fallbacks", CSSAttributeFallbacks);
    }
    return attributeDescriptor;
  }
  
  Map<String, Object> getClassAttributeDescriptor(ClassModel classModel) {
      Map<String, Object> descriptor = new LinkedHashMap<String, Object>();
      Map<String, Map<String, String>> attributeDescriptor = this.getAttributeDescriptor();
      
    if (! CSSAttributeProperties.isEmpty()) {
      descriptor.put("properties", CSSAttributeProperties);
    }
    
    if (! CSSAttributeFallbacks.isEmpty()) {
        descriptor.put("fallbacks", CSSAttributeFallbacks);
    }
    StringBuffer typesDeclare = new StringBuffer();    
    typesDeclare.append("lz.ClassAttributeTypes[\"");
    if (classModel.tagName!=null){
        typesDeclare.append(classModel.tagName);
      } else {
          typesDeclare.append(classModel.className);
      }    
    typesDeclare.append("\"]");
     
    descriptor.put("types", typesDeclare.toString());
    return descriptor;
  }
  
  Map<String, Object> getNodeAttributeDescriptor() {
      Map<String, Object> descriptor = new LinkedHashMap<String, Object>();
      Map<String, Map<String, String>> attributeDescriptor = this.getAttributeDescriptor();
      
    if (! CSSAttributeProperties.isEmpty()) {
      descriptor.put("properties", CSSAttributeProperties);
    }
    
    if (! CSSAttributeFallbacks.isEmpty()) {
        descriptor.put("fallbacks", CSSAttributeFallbacks);
    }
    StringBuffer typesDeclare = new StringBuffer();    
    String nodeName ;
    if (this.localName != null) {
        nodeName = this.localName;
    } else {
        nodeName =  this.tagName;
    }
    
    if (CSSAttributeTypes.isEmpty()) {
        typesDeclare.append("lz.ClassAttributeTypes[\"");
        typesDeclare.append(nodeName);
        typesDeclare.append("\"]");
      } else {
            typesDeclare.append( "LzNode.mergeAttributeTypes(lz.ClassAttributeTypes[\"");
            typesDeclare.append(nodeName);
            typesDeclare.append("\"],");
            typesDeclare.append(ScriptCompiler.objectAsJavascript(CSSAttributeTypes));
            typesDeclare.append(")");
      }
    descriptor.put("types", typesDeclare.toString());
    return descriptor;
  }
  
  public String getClassTypeDefine(){
      
      StringBuffer typesDeclare = new StringBuffer();        
        
        if (parentClassModel != null) { 
        
            if ( CSSAttributeTypes.isEmpty()) {
                typesDeclare.append( "LzNode.mergeAttributeTypes(lz.ClassAttributeTypes[\"");
                typesDeclare.append( parentClassModel.tagName);
                typesDeclare.append("\"],{})");
                
            } else {
                
                typesDeclare.append( "LzNode.mergeAttributeTypes(lz.ClassAttributeTypes[\"");
                typesDeclare.append( parentClassModel.tagName);
                typesDeclare.append("\"],");
                typesDeclare.append(ScriptCompiler.objectAsJavascript(CSSAttributeTypes));
                typesDeclare.append(")");
            }
            
        } else {
            typesDeclare.append(ScriptCompiler.objectAsJavascript(CSSAttributeTypes));
        }
          
          return typesDeclare.toString();
  }
      

  private Map<String, Map<String, ? extends Object>> CSSDescriptor = null;
  Map<String, Map<String, ? extends Object>> getCSSDescriptor () {
    if (CSSDescriptor != null) { return CSSDescriptor; }
    NodeModel parentNodeModel = parentClassModel.getNodeModel(env);
    boolean inherit = (CSSPropertyExpanders.isEmpty() &&
                       CSSPropertyInheritable.isEmpty());

    if (parentNodeModel != null) {
      // Ensure parent is merged -- may not be for interfaces
      parentNodeModel.getCSSDescriptor();
      addNew(CSSPropertyExpanders, parentNodeModel.CSSPropertyExpanders);
      addNew(CSSPropertyInheritable, parentNodeModel.CSSPropertyInheritable);
    }

    // TODO:  Until builtin classes actually store their descriptors,
    // we can't actually inherit them
    if (inherit && (! parentClassModel.isbuiltin)) { return null; }

    CSSDescriptor = new LinkedHashMap<String, Map<String, ? extends Object>>();
    if (! CSSPropertyExpanders.isEmpty()) {
      CSSDescriptor.put("expanders", CSSPropertyExpanders);
    }
    if (! CSSPropertyInheritable.isEmpty()) {
      CSSDescriptor.put("inheritable", CSSPropertyInheritable);
    }
    return CSSDescriptor;
  }

    void updateAttrs() {
        if (frozen) return;
        // TODO: [2010-11-01] ptw These psuedo-vars seem to create
        // class members, when they really just need to get into the
        // init list
        if (!delegateList.isEmpty()) {
          addProperty("$delegates", delegateList, ALLOCATION_INSTANCE);
        }
        if (datapath != null) {
          addProperty("$datapath", datapath.asMap(env), ALLOCATION_INSTANCE);
          // If we've got an explicit datapath value, we have to null
          // out the "datapath" attribute with the magic
          // LzNode._ignoreAttribute value, so it doesn't get
          // overridden by an inherited value from the class.
          addProperty("datapath", "LzNode._ignoreAttribute", ALLOCATION_INSTANCE);
        }
        // This can only happen once.
        frozen = true;
    }

    Map<String, Object> getAttrs() {
      updateAttrs();
      return attrs;
    }

    boolean hasMethods() {
        for (Object attr : attrs.values()) {
            if (attr instanceof Method) { return true; }
        }
        return false;
    }

    Map<String, Object> getClassAttrs() {
      return classAttrs;
    }

    Map<String, String> getSetters() {
        return setters;
    }

  /*
    A CompilationEnvironment is passed in explicitly, because when
    compiling an lzo or dynamic library, a separate
    CompilationEnvironment is created for the library, in order to
    provide a separate output stream to write the code to.
   */
    Map<String, Object> asMap(CompilationEnvironment cEnv) {
      if (frozen) {
        throw new CompilerImplementationError("Attempting to asMap when NodeModel frozen");
      }
      ClassModel classModel = parentClassModel;
      // Allow forward references, but we need this to be compiled now
      // so we can inherit from it.

      // While we need to ensure local classes are emitted before
      // they are used, we _don't_ force a compile here because
      // the class may be external (model-only), which means it
      // will be defined elsewhere.
      classModel.compile(env);

      // System.err.println("asMap: " + classModel.className);
      updateAttrs();
      // We don't allow class attributes for now.  (We could make an
      // anonymous class if there were, but how would you name them?)
      assert classAttrs.isEmpty();
      Map<String, Object> map = new LinkedHashMap<String, Object>();
      Map<String, Object> inits = new LinkedHashMap<String, Object>();
      boolean hasMethods = false;
      // Whether we make a class to hold the methods or not,
      // implicit replication relies on non-method properties coming
      // in as instance attributes, so we have to pluck them out
      // here (and turn the attributes into just declarations, by
      // setting their value to null).
      //
      // Node as map just wants to see all the attrs, so clean out
      // the binding markers
      for (Map.Entry<String, Object> entry : attrs.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        if (value instanceof Method) {
          hasMethods = true;
        } else if (! (value instanceof NodeModel.BindingExpr)) {
          inits.put(key, value);
          attrs.put(key, null);
        } else {
          inits.put(key, ((NodeModel.BindingExpr)value).getExpr());
          attrs.put(key, null);
        }
      }
      if (hasMethods) {
        // If there are methods, make a class (but don't publish it)
        classModel = new ClassModel(ViewSchema.KIND_INSTANCE_CLASS, tagName, null, false, schema, element, cEnv);
        classModel.setNodeModel(this);

        // We need to pass modelOnly for the case where we are
        // compiling a .swc for an as3 lzo, and we need to be sure
        // to tell the as3 backend to not link in any external
        // class defs or anonymous subclasses thereof.
        classModel.emitClassDeclaration(cEnv, cEnv.compilingExternalLibrary);
      } else {
        // If no class needed, Put children into map
        if (!children.isEmpty()) {
          map.put("children", childrenMaps(cEnv));
        }
        // Look for any instance attributes (if we emit a class,
        // above, the class will have these)
    //    Map<String, Map<String, String>> attributeDescriptor = getAttributeDescriptor();
        Map<String, Object> attributeDescriptor = getNodeAttributeDescriptor();
        if (attributeDescriptor != null) {
          inits.put("$attributeDescriptor", attributeDescriptor);
        }
        Map<String, Map<String, ? extends Object>> CSSDescriptor = getCSSDescriptor();
        if (CSSDescriptor != null) {
          inits.put("$CSSDescriptor", CSSDescriptor);
        }
      }

      // Non-method attributes
      if (!inits.isEmpty()) {
        map.put("attrs", inits);
      }
      if (classModel.anonymous || classModel.isbuiltin || env.tagDefined(tagName) || "anonymous".equals(tagName)) {
        // The class to instantiate
        map.put("class", classModel.className);
      } else {
        // Non-anonymous classes may be deferred, so we must
        // indirect through the tagname
        map.put("tag", ScriptCompiler.quote(tagName));
      }

      return map;
    }


    void assignClassRoot(int depth) {
        if (! parentClassModel.isstate) { depth++; }
        Integer d = depth;
        for (NodeModel child : children) {
            child.addProperty("$classrootdepth", d, ALLOCATION_INSTANCE);
            child.assignClassRoot(depth);
        }
    }

    List<Map<String, Object>> childrenMaps(CompilationEnvironment cEnv) {
        List<Map<String, Object>> childMaps = new Vector<Map<String, Object>>(children.size());
        for (NodeModel child : children)
            childMaps.add(child.asMap(cEnv));

        // TODO: [2006-09-28 ptw] There must be a better way. See
        // comment in LzNode where $lzc$class_userClassPlacement is
        // inserted in lz regarding the wart this is. You need some
        // way to not set defaultplacement until the class-defined
        // children are instantiated, only the instance-defined
        // children should get default placement. For now this is done
        // by inserting this sentinel in the child nodes...
        if (ViewSchema.KIND_CLASS.equals(tagName) && hasAttribute("defaultplacement")) {
            LinkedHashMap<String, Object> dummy = new LinkedHashMap<String, Object>();
            dummy.put("class", "$lzc$class_userClassPlacement");
            dummy.put("attrs", attrs.get("defaultplacement"));
            removeAttribute("defaultplacement");
            childMaps.add(dummy);
        }

        return childMaps;
    }

}
