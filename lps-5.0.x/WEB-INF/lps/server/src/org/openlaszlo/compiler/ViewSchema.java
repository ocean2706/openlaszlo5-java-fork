/* ****************************************************************************
 * ViewSchema.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.jdom.Document;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.openlaszlo.xml.internal.Schema;
import org.openlaszlo.xml.internal.XMLUtils;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.server.*;

/** A schema that describes a Laszlo XML file. */
public class ViewSchema extends Schema {
    /** The location of the Laszlo LFC bootstrap interface declarations file  */
    private final String SCHEMA_PATH = LPS.HOME() + File.separator +
        "WEB-INF" + File.separator +
        "lps" + File.separator +
        "schema"  + File.separator + "lfc.lzx";

    private Document schemaDOM = null;

    private static Document sCachedSchemaDOM;
    private static long sCachedSchemaLastModified;

    private static final Set<String> sMouseEventAttributes;

    static final Set<String> mMetaTags = new HashSet<String>(Arrays.asList("doc", "containsElements", "forbiddenElements"));
    static final Set<String> mClassTags = new HashSet<String>(Arrays.asList("attribute", "event", "method", "setter", "handler"));

    /** Maps a class (name) to its ClassModel. */
    private Map<String, ClassModel> mTagMap;

    /** Maps a type (name) to its ClassModel. */
    private Map<String, ClassModel> mTypeMap;

    /**
     * If true, requires class names to be valid javascript identifiers.
     * We disable this when defining LZX builtin tags such as "import"
     * which are reserved javascript tokens.
     */
    public boolean loadingLFCSchema = true;

    /** Represents an _ECMAScript_ String. */
    public static final Type STRING_TYPE = newType("string");
    /** Represents XML CDATA. */
    public static final Type XML_CDATA_TYPE = newType("cdata");
    /** Represents a XML CONTENT. */
    // NOTE: [2010-06-16 ptw] (LPP-9027) For the time being, 'html'
    // and 'text' are treated as synonyms, although 'text' should
    // be a synonym for 'cdata'
    public static final Type XML_CONTENT_TYPE = newType("html");
    static {
      addTypeAlias("text", XML_CONTENT_TYPE);
    }
    /** Represents a number. */
    public static final Type NUMBER_TYPE = newType("number");
    /** Represents an XML ID. */
    public static final Type ID_TYPE = newType("ID");

    /** Type of script expressions. */
    public static final Type EXPRESSION_TYPE          = newType("expression");

    /** 'boolean' is compiled the same as an expression type */
    public static final Type BOOLEAN_TYPE             = newType("boolean");

    public static final Type REFERENCE_TYPE           = newType("reference");
    /** Type of event handler bodies. */
    public static final Type EVENT_HANDLER_TYPE       = newType("script");

    /** Type of tokens. */
    public static final Type TOKEN_TYPE               = newType("token");
    public static final Type COLOR_TYPE               = newType("color");
    public static final Type NUMBER_EXPRESSION_TYPE   = newType("numberExpression");
    public static final Type SIZE_EXPRESSION_TYPE     = newType("size");
    public static final Type CSS_TYPE                 = newType("css");
    public static final Type INHERITABLE_BOOLEAN_TYPE = newType("inheritableBoolean");
    public static final Type XML_LITERAL              = newType("xmlLiteral");
    public static final Type METHOD_TYPE              = newType("method");
    public static final Type NODE_TYPE                = newType("node");

    public static final Set<Type> sNonEmptyValueTypes = new HashSet<Type>();
    public static final Map<String, Type> builtinTypeNames = new HashMap<String, Type>(typeNames);
    static {
        // types that cannot have empty attribute values
        List<Type> typelist = Arrays.asList(ViewSchema.BOOLEAN_TYPE,
                                    ViewSchema.EVENT_HANDLER_TYPE,
                                    ViewSchema.EXPRESSION_TYPE,
                                    ViewSchema.NODE_TYPE,
                                    ViewSchema.NUMBER_EXPRESSION_TYPE,
                                    ViewSchema.NUMBER_TYPE,
                                    ViewSchema.REFERENCE_TYPE,
                                    ViewSchema.SIZE_EXPRESSION_TYPE);
        sNonEmptyValueTypes.addAll(typelist);

        // from http://www.w3.org/TR/REC-html40/interact/scripts.html
        List<String> mouseEventAttributes = Arrays.asList(
            "onclick", "ondblclick", "onmousedown", "onmouseup", "onmouseover",
            "onmousemove", "onmouseout");
        sMouseEventAttributes = new HashSet<String>(mouseEventAttributes);
    }

    boolean containsMouseEventAttribute (Set<String> set) {
        return (! Collections.disjoint(sMouseEventAttributes, set));
    }

    private static final String AUTOINCLUDES_PROPERTY_FILE =
        LPS.getMiscDirectory() + File.separator +
        "lzx-autoincludes.properties";
    public static final Properties sAutoincludes = new Properties();

    static {
        try {
            InputStream is = new FileInputStream(AUTOINCLUDES_PROPERTY_FILE);
            try {
                sAutoincludes.load(is);
            } finally {
                is.close();
            }
        } catch (java.io.IOException e) {
            throw new ChainedException(e);
        }
    }

    public ViewSchema() {
    }

    CompilationEnvironment mEnv = null;

    public ViewSchema(CompilationEnvironment env) {
        this.mEnv = env;
    }

    public CompilationEnvironment getCompilationEnvironment() {
        return mEnv;
    }

    public static class UserType extends Schema.Type {
      // If non-null, the element that defines the type
      private Element definition;
      // If non-null, the first forward-reference to the type
      private Element reference;

      public UserType(String name, Element definition, Element reference) {
        super(name);
        this.definition = definition;
        this.reference = reference;
      }
      public UserType(String name, Element definition) {
        this(name, definition, null);
      }
      public UserType(String name) {
        this(name, null, null);
      }
      public Type resolve(CompilationEnvironment env) {
        if (reference != null && definition == null) {
          env.warn("Reference to undefined <type> " + name, reference);
          return UNKNOWN_TYPE;
        }
        return this;
      }
      public Element getDefinition() {
        return definition;
      }
      public void setDefinition(Element definition) {
        this.definition = definition;
      }
    }

    /**
     * Creates a user-defined type
     */
    public Type defineType(String typeName, Element definition) {
      // A brand new type
      if (! typeNames.containsKey(typeName)) {
        Type newtype = new UserType(typeName, definition);
        typeNames.put(typeName, newtype);
        return newtype;
      }
      // Maybe the definition of a forward-referenced type?
      Type t = typeNames.get(typeName);
      if ((t instanceof UserType) && (((UserType) t).getDefinition() == null)) {
        ((UserType) t).setDefinition(definition);
        return t;
      }
      // A duplicate!
      String builtin = "builtin ";
      String also = "";
      if (t instanceof UserType) {
        Element other = ((UserType) t).getDefinition();
        builtin = "";
        also =  "; also defined at " + Parser.getSourceMessagePathname(other) + ":" + Parser.getSourceLocation(other, Parser.LINENO);
      }
      CompilationError cerr = new CompilationError("Duplicate <type> definition for " + builtin + typeName + also , definition);
      throw(cerr);
    }

    /** Look up the Type object from a Javascript type name, possibly
     * creating a forward reference. */
    public Type getTypeForName (String typeName, Element reference) {
      if (typeNames.containsKey(typeName)) {
        return typeNames.get(typeName);
      }
      Type newtype = new UserType(typeName, null, reference);
      typeNames.put(typeName, newtype);
      return newtype;
    }

    /** Ensure all types that have been referenced have definitions */
    public void resolveTypes() {
      for (Type t : typeNames.values()) {
        if (t instanceof UserType) {
          ((UserType) t).resolve(mEnv);
        }
      }
      // Now resolve the classes backing the user types
      TreeSet<ClassModel> typeSet = new TreeSet<ClassModel>(mTypeMap.values());
      for (ClassModel model : typeSet) {
        // System.err.println(model + " " + model.sortkey);
        model.resolve(mEnv);
      }
    }

    /**
     * Add a new entry to the kind:name->model map
     *
     * @param elt the element to model
     * @param kind the kind of element
     * @param name the name of the element
     */
    public void addClassModel (Element elt, String kind, String name, String className, CompilationEnvironment env) {
      addClassModel(elt, kind, name, className, env, true);
    }

    /**
     * Add a new entry to the kind:name->model map
     *
     * @param elt the element to model
     * @param name the name of the element
     * @param publish this should go in the tag table
     */
  public void addClassModel (Element elt, String kind, String name, String className, CompilationEnvironment env, boolean publish)
    {
        ClassModel other = getClassModelUnresolved(kind, name);
        if (other != null) {
          Element otherdef = other.definition;
          // This relies on getClassModel not caring about kind
          if (KIND_INTERFACE.equals(kind) && KIND_MIXIN.equals(other.kind)) {
              // We are loading an LZO where the mixin interface has
              // already been compiled.  Just note that.
              other.declarationEmitted = true;
              return;
          }
          String builtin = "builtin " + kind + " ";
          String also = "";
          if (otherdef != null) {
            builtin = kind + " ";
            also = "; also defined at " +
              Parser.getSourceMessagePathname(otherdef) + ":" +
              Parser.getSourceLocation(otherdef, Parser.LINENO);
          }
          throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="duplicate class definitions for " + p[0] + p[1] + p[2]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
              ViewSchema.class.getName(),"051018-435", new Object[] {builtin, name, also}) , elt);
        }
        ClassModel model = new ClassModel(kind, name, className, publish, this, elt, env);
        putClassModel(kind, name, model);
    }


    /** Adds a ClassModel entry into the class table for CLASSNAME. */
    private void makeNewStaticClass (String classname, CompilationEnvironment env) {
        ClassModel info = new ClassModel(KIND_CLASS, classname, this, env);
        if (getClassModel(KIND_CLASS, classname) == null) {
            putClassModel(KIND_CLASS, classname, info);
        } else {
            throw new CompilationError("makeNewStaticClass: `duplicate definition for static class " + classname);
        }
    }

  public static final String KIND_CLASS = "class";
  public static final String KIND_INTERFACE = "interface";
  public static final String KIND_MIXIN = "mixin";
  public static final String KIND_INSTANCE_CLASS = "instance class";

  public static boolean kindIsTag (String kind) {
   return (KIND_CLASS.equals(kind) || KIND_INTERFACE.equals(kind)
           || KIND_MIXIN.equals(kind) || KIND_INSTANCE_CLASS.equals(kind));
  }

  public static String KIND_TYPE ="type";
  public static boolean kindIsType (String kind) {
    return (KIND_TYPE.equals(kind));
  }

  boolean isClassDefinition(Element element) {
    return kindIsTag(element.getName());
  }

    public void putClassModel (String kind, String elementName, ClassModel model) {
      if (kindIsTag(kind)) {
        mTagMap.put(elementName, model);
      } else if (kindIsType(kind)) {
        mTypeMap.put(elementName, model);
      } else {
        throw new CompilationError("Unknown class kind " + kind);
      }
    }

    ClassModel getClassModelUnresolved (String kind, String elementName) {
      if (kindIsTag(kind)) {
        return mTagMap.get(elementName);
      } else if (kindIsType(kind)) {
        return mTypeMap.get(elementName);
      } else {
        throw new CompilationError("Unknown class kind " + kind);
      }
    }

    ClassModel getClassModel (String kind, String elementName) {
      // Ensure the class model is resolved
      ClassModel model = getClassModelUnresolved(kind, elementName);
      if (model != null) {
        return model.resolve(mEnv);
      }
      return model;
    }
    
    ClassModel getInstanceTypeClassModel(Element element) {
        String kind = KIND_TYPE;
        String typename = XMLUtils.requireAttributeValue(element, "name");
        ClassModel model = getClassModel(kind, typename);
        if (model == null ) {
          throw new CompilationError("no type"+typename, element);
        }
        return model;
  }

  ClassModel getInstanceClassModel(Element element) {
    return getInstanceClassModel(element, true);
  }

  /** Get the class model that will be used to instantiate an element */
  ClassModel getInstanceClassModel(Element element, boolean required) {
    String kind = KIND_CLASS;
    String name = element.getName();
    String message = "Unknown tag <" + name + ">";
    if ("anonymous".equals(name)) {
      kind = KIND_INSTANCE_CLASS;
      name = element.getAttributeValue("extends");
      message = "Unknown tag extends='" + name + "'";
    } else if (isClassDefinition(element)) {
      kind = name;
      name = element.getAttributeValue("name");
      message = "Unknown tag name='" + name + "'";
    }
    ClassModel model = getClassModel(kind, name);
    if (model == null && required) {
      throw new CompilationError(message, element);
    }
    return model;
  }

    public void resolveClassModels () {
      TreeSet<ClassModel> classSet = new TreeSet<ClassModel>(mTagMap.values());
      for (ClassModel model : classSet) {
        // System.err.println(model + " " + model.sortkey);
        model.resolve(mEnv);
      }
    }

    // Walk through all builtin class models and compile them.
    // This is done in advance of other compilations so that
    // reference classes will be available for all of these.
    public void compileReferenceClassModels() {
      StringBuffer references = new StringBuffer();
      TreeSet<ClassModel> classSet = new TreeSet<ClassModel>(mTagMap.values());
      for (ClassModel model : classSet) {
        boolean ismetasym = (model.definition != null &&
                             "true".equals(model.definition.getAttributeValue("metasym")));
        if (model.isbuiltin && (! ismetasym)) {
          model.compile(mEnv, false);
        }
      }
    }

    public String toLZX() {
      return toLZX("");
    }

    public String toLZX(String indent) {
      String lzx = "";
      Set<ClassModel> allTags = new TreeSet<ClassModel>(mTagMap.values());
      // Output mixins first, so they can be referred to by other interfaces
      for (ClassModel model : allTags) {
        if (KIND_MIXIN.equals(model.kind) &&
            (! (model.modelOnly || model.isbuiltin))
            && model.hasNodeModel()) {
          lzx += model.toLZX(indent);
          lzx += "\n";
        }
      }
      for (ClassModel model : allTags) {
        // NOTE: For LZO's, we have to emit anonymous classes, so they
        // can be correctly accounted for.  This may not be
        // appropriate if the schema is to be used for other purposes
        // (since such classes are not available for instantiating).
        if ((! (KIND_MIXIN.equals(model.kind) || model.modelOnly || model.isbuiltin))
            // A non model-only interface will be from an lzo that
            // includes another lzo, so must be (re-)emitted.
            // Anything else with a model that is _not_ modelonly is
            // part of this lzo and must be emitted.
            && ((KIND_INTERFACE.equals(model.kind) || model.hasNodeModel()))) {
          if (! model.hasNodeModel()) { model.getNodeModel(mEnv); }
          lzx += model.toLZX(indent);
          lzx += "\n";
        }
      }
      return lzx;
    }

    public void loadSchema(CompilationEnvironment env) throws JDOMException, IOException {
        typeNames = new HashMap<String, Type>(builtinTypeNames);
        mTypeMap = new HashMap<String, ClassModel>();
        mTagMap = new HashMap<String, ClassModel>();
        String schemaPath = SCHEMA_PATH;
        // Load the schema if it hasn't been.
        // Reload it if it's been touched, to make it easier for developers.
        while (sCachedSchemaDOM == null ||
               new File(schemaPath).lastModified() != sCachedSchemaLastModified) {
            // using 'while' and reading the timestamp *first* avoids a
            // race condition --- although since this doesn't happen in
            // production code, this isn't critical
            sCachedSchemaLastModified = new File(schemaPath).lastModified();
            sCachedSchemaDOM = new Parser().read(new File(schemaPath));
        }

        // This is the base class from which all classes derive unless otherwise
        // specified. It has no attributes.
        makeNewStaticClass("Object", env);

        schemaDOM = (Document) sCachedSchemaDOM.clone();
        Element docroot = schemaDOM.getRootElement();
        ToplevelCompiler ec = (ToplevelCompiler) Compiler.getElementCompiler(docroot, env);
        Set<File> visited = new HashSet<File>();
        ec.updateSchema(docroot, this, visited);

        // Resolve the built-ins
        resolveClassModels();

        /** From here on, user-defined classes must not use reserved javascript identifiers */
        this.loadingLFCSchema = false;
    }



    /** Check if a child element can legally be contained in a parent element.
        This works with the class hierarchy as follows:

        + Look up the ClassModel corresponding to the parentTag

        + Check the containsElements table to see if child tag is in there

        + If not, look up the ClassModel of the child tag, and follow up the chain
          checking if that name is present in the table
     */
    public boolean canContainElement (Element parentElement, Element childElement) {
        // Get list of legally nestable tags
        ClassModel parent = getInstanceClassModel(parentElement);
        Set<String> tagset = parent.getContainsSet();
        Set<String> forbidden = parent.getForbiddenSet();
        // Check childTag and it's superclasses
        String childTag = childElement.getName().intern();
        do {
            if (forbidden.contains(childTag)) {
                return false;
            }
            if (tagset.contains(childTag)) {
                return true;
            }
            ClassModel childClassModel = getClassModel(KIND_CLASS, childTag);
            if (childClassModel == null) { childClassModel = getClassModel(KIND_INTERFACE, childTag); }
            childTag = childClassModel != null ? childClassModel.getSuperTagName() : null;
        } while (childTag != null);
        return false;
    }


    /** @return true if this element is an input text field */
    boolean isInputTextElement(Element e) {
        return getInstanceClassModel(e).isInputText;
    }

    boolean supportsTextAttribute(Element e) {
        return getInstanceClassModel(e).supportsTextAttribute;
    }


    /** @return true if this element content is interpreted as text */
    boolean hasTextContent(Element e) {
        // input text elements can have text
        return isInputTextElement(e) || supportsTextAttribute(e);
    }

    /** @return true if this element's content is HTML. */
    boolean hasHTMLContent(Element e) {
        String name = e.getName().intern();
        // TBD: return ClassModel.supportsHTMLAttribute. Currently
        // uses a blacklist instead of a whitelist because the parser
        // doesn't tell the schema about user-defined classes.
        // XXX Since any view can have a text body, this implementation
        // is actually correct.  That is, blacklist rather than whitelist
        // is the way to go here.
        return (! isClassDefinition(e)) && name != "method"
            && name != "property" && name != "script"
            && name != "attribute" && name != "handler"
            && name != "event"
            && !isHTMLElement(e) && !isInputTextElement(e);
    }

    /** @return true if this element is an HTML element, that should
     * be included in the text content of an element that tests true
     * with hasHTMLContent. */
    static boolean isHTMLElement(Element e) {
        String name = e.getName();
        return name.equals("a")
            || name.equals("b")
            || name.equals("img")
            || name.equals("br")
            || name.equals("font")
            || name.equals("i")
            || name.equals("p")
            || name.equals("pre")
            || name.equals("u")
            || name.equals("ul")
            || name.equals("li")
            || name.equals("ol");
    }

    static boolean isMetaElement(Element e) {
      String name = e.getName().intern();
        return mMetaTags.contains(name);
    }

    /* Constants for parsing CSS colors. */
    private static final Pattern sRGBPattern;
    private static final Pattern sHex3Pattern;
    private static final Pattern sHex6Pattern;
    private static final HashMap<String, Integer> sColorValues = new HashMap<String, Integer>();
    static {
        try {
            String s = "\\s*(-?\\d+(?:(?:.\\d*)%)?)\\s*"; // component
            String hexDigit = "[0-9a-fA-F]";
            String hexByte = hexDigit + hexDigit;
            sRGBPattern = Pattern.compile(
                "\\s*rgb\\("+s+","+s+","+s+"\\)\\s*");
            sHex3Pattern = Pattern.compile(
                "\\s*#\\s*(" + hexDigit + hexDigit + hexDigit + ")\\s*");
            sHex6Pattern = Pattern.compile(
                "\\s*#\\s*(" + hexByte + hexByte + hexByte + ")\\s*");
        } catch (PatternSyntaxException e) {
            throw new ChainedException(e);
        }

        // From http://www.w3.org/TR/css3-color/#svg-color
        String[] colorNameValues = {
          "aliceblue", "F0F8FF"
          ,"antiquewhite", "FAEBD7"
          ,"aqua", "00FFFF"
          ,"aquamarine", "7FFFD4"
          ,"azure", "F0FFFF"
          ,"beige", "F5F5DC"
          ,"bisque", "FFE4C4"
          ,"black", "000000"
          ,"blanchedalmond", "FFEBCD"
          ,"blue", "0000FF"
          ,"blueviolet", "8A2BE2"
          ,"brown", "A52A2A"
          ,"burlywood", "DEB887"
          ,"cadetblue", "5F9EA0"
          ,"chartreuse", "7FFF00"
          ,"chocolate", "D2691E"
          ,"coral", "FF7F50"
          ,"cornflowerblue", "6495ED"
          ,"cornsilk", "FFF8DC"
          ,"crimson", "DC143C"
          ,"cyan", "00FFFF"
          ,"darkblue", "00008B"
          ,"darkcyan", "008B8B"
          ,"darkgoldenrod", "B8860B"
          ,"darkgray", "A9A9A9"
          ,"darkgreen", "006400"
          ,"darkgrey", "A9A9A9"
          ,"darkkhaki", "BDB76B"
          ,"darkmagenta", "8B008B"
          ,"darkolivegreen", "556B2F"
          ,"darkorange", "FF8C00"
          ,"darkorchid", "9932CC"
          ,"darkred", "8B0000"
          ,"darksalmon", "E9967A"
          ,"darkseagreen", "8FBC8F"
          ,"darkslateblue", "483D8B"
          ,"darkslategray", "2F4F4F"
          ,"darkslategrey", "2F4F4F"
          ,"darkturquoise", "00CED1"
          ,"darkviolet", "9400D3"
          ,"deeppink", "FF1493"
          ,"deepskyblue", "00BFFF"
          ,"dimgray", "696969"
          ,"dimgrey", "696969"
          ,"dodgerblue", "1E90FF"
          ,"firebrick", "B22222"
          ,"floralwhite", "FFFAF0"
          ,"forestgreen", "228B22"
          ,"fuchsia", "FF00FF"
          ,"gainsboro", "DCDCDC"
          ,"ghostwhite", "F8F8FF"
          ,"gold", "FFD700"
          ,"goldenrod", "DAA520"
          ,"gray", "808080"
          ,"green", "008000"
          ,"greenyellow", "ADFF2F"
          ,"grey", "808080"
          ,"honeydew", "F0FFF0"
          ,"hotpink", "FF69B4"
          ,"indianred", "CD5C5C"
          ,"indigo", "4B0082"
          ,"ivory", "FFFFF0"
          ,"khaki", "F0E68C"
          ,"lavender", "E6E6FA"
          ,"lavenderblush", "FFF0F5"
          ,"lawngreen", "7CFC00"
          ,"lemonchiffon", "FFFACD"
          ,"lightblue", "ADD8E6"
          ,"lightcoral", "F08080"
          ,"lightcyan", "E0FFFF"
          ,"lightgoldenrodyellow", "FAFAD2"
          ,"lightgray", "D3D3D3"
          ,"lightgreen", "90EE90"
          ,"lightgrey", "D3D3D3"
          ,"lightpink", "FFB6C1"
          ,"lightsalmon", "FFA07A"
          ,"lightseagreen", "20B2AA"
          ,"lightskyblue", "87CEFA"
          ,"lightslategray", "778899"
          ,"lightslategrey", "778899"
          ,"lightsteelblue", "B0C4DE"
          ,"lightyellow", "FFFFE0"
          ,"lime", "00FF00"
          ,"limegreen", "32CD32"
          ,"linen", "FAF0E6"
          ,"magenta", "FF00FF"
          ,"maroon", "800000"
          ,"mediumaquamarine", "66CDAA"
          ,"mediumblue", "0000CD"
          ,"mediumorchid", "BA55D3"
          ,"mediumpurple", "9370DB"
          ,"mediumseagreen", "3CB371"
          ,"mediumslateblue", "7B68EE"
          ,"mediumspringgreen", "00FA9A"
          ,"mediumturquoise", "48D1CC"
          ,"mediumvioletred", "C71585"
          ,"midnightblue", "191970"
          ,"mintcream", "F5FFFA"
          ,"mistyrose", "FFE4E1"
          ,"moccasin", "FFE4B5"
          ,"navajowhite", "FFDEAD"
          ,"navy", "000080"
          ,"oldlace", "FDF5E6"
          ,"olive", "808000"
          ,"olivedrab", "6B8E23"
          ,"orange", "FFA500"
          ,"orangered", "FF4500"
          ,"orchid", "DA70D6"
          ,"palegoldenrod", "EEE8AA"
          ,"palegreen", "98FB98"
          ,"paleturquoise", "AFEEEE"
          ,"palevioletred", "DB7093"
          ,"papayawhip", "FFEFD5"
          ,"peachpuff", "FFDAB9"
          ,"peru", "CD853F"
          ,"pink", "FFC0CB"
          ,"plum", "DDA0DD"
          ,"powderblue", "B0E0E6"
          ,"purple", "800080"
          ,"red", "FF0000"
          ,"rosybrown", "BC8F8F"
          ,"royalblue", "4169E1"
          ,"saddlebrown", "8B4513"
          ,"salmon", "FA8072"
          ,"sandybrown", "F4A460"
          ,"seagreen", "2E8B57"
          ,"seashell", "FFF5EE"
          ,"sienna", "A0522D"
          ,"silver", "C0C0C0"
          ,"skyblue", "87CEEB"
          ,"slateblue", "6A5ACD"
          ,"slategray", "708090"
          ,"slategrey", "708090"
          ,"snow", "FFFAFA"
          ,"springgreen", "00FF7F"
          ,"steelblue", "4682B4"
          ,"tan", "D2B48C"
          ,"teal", "008080"
          ,"thistle", "D8BFD8"
          ,"tomato", "FF6347"
          ,"turquoise", "40E0D0"
          ,"violet", "EE82EE"
          ,"wheat", "F5DEB3"
          ,"white", "FFFFFF"
          ,"whitesmoke", "F5F5F5"
          ,"yellow", "FFFF00"
          ,"yellowgreen", "9ACD32"
        };
        for (int i = 0; i < colorNameValues.length; ) {
            String name = colorNameValues[i++];
            String value = colorNameValues[i++];
            sColorValues.put(name, Integer.parseInt(value, 16));
        }
    }

    @SuppressWarnings("serial")
    static class ColorFormatException extends RuntimeException {
        ColorFormatException(String message) {
            super(message);
        }
    }

    /** Parse according to http://www.w3.org/TR/2001/WD-css3-color-20010305,
     * but also allow 0xXXXXXX, and 'transparent' */
    public static int parseColor(String str) {
        if (str.equals("transparent")) {
            return -1;
        }
        {
            Object value = sColorValues.get(str);
            if (value != null) {
                return ((Integer) value).intValue();
            }
        }
        if (str.startsWith("0x")) {
            try {
                return Integer.parseInt(str.substring(2), 16);
            } catch (java.lang.NumberFormatException e) {
                // fall through
            }
        }
        Matcher sMatcher = sHex3Pattern.matcher(str);
        if (sMatcher.matches()) {
            int r1g1b1 = Integer.parseInt(sMatcher.group(1), 16);
            int r = (r1g1b1 >> 8) * 17;
            int g = ((r1g1b1 >> 4) & 0xf) * 17;
            int b = (r1g1b1 & 0xf) * 17;
            return (r << 16) + (g << 8) + b;
        }
        sMatcher = sHex6Pattern.matcher(str);
        if (sMatcher.matches()) {
            return Integer.parseInt(sMatcher.group(1), 16);
        }
        sMatcher = sRGBPattern.matcher(str);
        if (sMatcher.matches()) {
            int v = 0;
            for (int i = 0; i < 3; i++) {
                String s = sMatcher.group(i+1);
                int c;
                if (s.charAt(s.length()-1) == '%') {
                    s = s.substring(0, s.length()-1);
                    float f = Float.parseFloat(s);
                    c = (int) f * 255 / 100;
                } else {
                    c = Integer.parseInt(s);
                }
                if (c < 0) c = 0;
                if (c > 255) c = 255;
                v = (v << 8) | c;
            }
            return v;
        }
        throw new ColorFormatException(str);
    }

    public void clearClassMaps() {
        mTagMap = null;
        mTypeMap = null;
    };



    /* Check for unknown attributes on a <method> tag */
    static HashSet<String>  methodAttributes = new HashSet<String>();
    static HashSet<String>  attributeAttributes = new HashSet<String>();
    static HashSet<String>  handlerAttributes = new HashSet<String>();
    static HashSet<String>  setterAttributes = new HashSet<String>();
    static HashSet<String>  eventAttributes = new HashSet<String>();
    static {
        eventAttributes.add("name");
        eventAttributes.add("final");

        handlerAttributes.add("method");
        handlerAttributes.add("name");
        handlerAttributes.add("args");
        handlerAttributes.add("reference");

        setterAttributes.add("name");
        setterAttributes.add("args");
        setterAttributes.add("allocation");

        methodAttributes.add("name");
        methodAttributes.add("args");
        methodAttributes.add("returns");
        methodAttributes.add("allocation");
        methodAttributes.add("override");
        methodAttributes.add("final");

        attributeAttributes.add("value");
        attributeAttributes.add("when");
        attributeAttributes.add("type");
        attributeAttributes.add("allocation");
        attributeAttributes.add("final");
        attributeAttributes.add("name");
        attributeAttributes.add("setter");
        attributeAttributes.add("required");
        attributeAttributes.add("style");
        attributeAttributes.add("inherit");
        attributeAttributes.add("expander");
        // What is this?  Output by schema generator
        attributeAttributes.add("enum");

    }

    /* Check if any of the attributes are not valid for use on a <attribute> tag */
    static void checkAttributeAttributes(Element elt, CompilationEnvironment env) throws CompilationError {
        checkAttributes(elt, attributeAttributes, env);
    }

    /* Check if any of the attributes are not valid for use on a <method> tag */
    static void checkMethodAttributes(Element elt, CompilationEnvironment env) throws CompilationError {
        checkAttributes(elt, methodAttributes, env);
    }

    static void checkSetterAttributes(Element elt, CompilationEnvironment env) throws CompilationError {
        checkAttributes(elt, setterAttributes, env);
    }

    static void checkHandlerAttributes(Element elt, CompilationEnvironment env) throws CompilationError {
        checkAttributes(elt, handlerAttributes, env);
    }

    static void checkEventAttributes(Element elt, CompilationEnvironment env) throws CompilationError {
        checkAttributes(elt, eventAttributes, env);
    }

    static void checkAttributes(Element elt, HashSet<String> validValues, CompilationEnvironment env) {
        for (Iterator<?> iter = elt.getAttributes().iterator(); iter.hasNext();) {
            Attribute attr = (Attribute) iter.next();
            if (!validValues.contains(attr.getName())) {
                env.warn("Unknown attribute '"+attr.getName()+"' on "+elt.getName(), elt);
            }
        }
    }

    /**
     * Throw an error if there are any unknown attributes on a tag
     */
    public void checkValidAttributeNames(Element elt) {
        ClassModel classModel = getInstanceClassModel(elt);

        for (Iterator<?> iter = elt.getAttributes().iterator(); iter.hasNext(); ) {
            Attribute attr = (Attribute) iter.next();
            String name = attr.getName();
            AttributeSpec attrspec  = classModel.getAttribute(name, NodeModel.ALLOCATION_INSTANCE);
            if (attrspec == null) {
                throw new CompilationError("Unknown attribute '"+name+"' on "+classModel, elt);
            }
        }
    }
}
