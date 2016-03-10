/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * LZX Attributes
 */

package org.openlaszlo.compiler;

import org.jdom.Element;
import org.openlaszlo.xml.internal.Schema.Type;
import org.openlaszlo.xml.internal.XMLUtils;

/** Contains information about an attribute of a laszlo viewsystem class.
 */
public class AttributeSpec {
    /** The source Element from which this attribute was parsed */
    Element source = null;
    /** The attribute name */
    public String name;
    /** The default value */
    public String defaultValue = null;
    /** The setter function */
    public String setter;
    /** The type of the attribute value*/
    public Type type;
    /** The CSS style */
    public String style;
    /** If the style inherits */
    public String inherit;
    /** If the style has an expander */
    public String expander;

    /** Is this attribute required to instantiate an instance of this class? */
    public boolean required = false;
    /** When does the initial value for this attribute get evaluated? */
    String when = NodeModel.WHEN_IMMEDIATELY;

    /**
     * Is this property allocated on the instance or the class.
     * Legal values are NodeModel.ALLOCATION_INSTANCE, and NodeModel.ALLOCATION_CLASS
     */
    String allocation = NodeModel.ALLOCATION_INSTANCE;

    /** If this is a method, the arglist */
    public String arglist = null;

    /** Can this attribute be overridden without a warning? */
    boolean isfinal = false;

    /** Is this attribute equivalent to element content of a given type? */
    int contentType = NO_CONTENT;

    /** Element content types: */
    static final int NO_CONTENT = 0;
    static final int TEXT_CONTENT = 1;
    static final int HTML_CONTENT = 2;

  private String typeToLZX() {
    switch (contentType) {
      case TEXT_CONTENT:
        return "text";
      case HTML_CONTENT:
        return "html";
      default:
        return type.toString();
    }
  }

  private String styleToLZX() {
    if (style != null) {
      return " style='" + style + "'" +
        ((inherit != null)?(" inherit='" + inherit + "'"):"") +
        ((expander != null)?(" expander='" + expander + "'"):"");
    } else {
      return "";
    }
  }

  public String toLZX(String indent, ClassModel superclass) {
    AttributeSpec superSpec = superclass.getAttribute(name, allocation);
    String value = null;
    if (superSpec == null) {
      // Anything not in the superclass needs to be declared
      if (ViewSchema.METHOD_TYPE.equals(type)) {
        return indent + "<method name='" + name + "'" +
          (((arglist == null) || "".equals(arglist))?"":(" args='" + XMLUtils.escapeXml(arglist) +"'")) +
          (isfinal?" isfinal='true'":"") +
          " />";
      } else if (ViewSchema.EVENT_HANDLER_TYPE.equals(type)) {
        return indent + "<event name='" + name + "' />";
      }
      value = indent + "<attribute name='" + name + "'" +
        styleToLZX() +
        ((defaultValue != null)?(" value='" + XMLUtils.escapeXml(defaultValue) + "'"):"") +
        ((type != null)?(" type='" + typeToLZX() + "'"):"") +
        ((when != NodeModel.WHEN_IMMEDIATELY)?(" when='" + when + "'"):"") +
        (required?(" required='true'"):"") +
        " />";
      if (setter != null) {
        // We don't need the body of the setter in the interface, just
        // note that there is one.
        value += "\n" + indent +"<setter name='" + name + "' />";
      }
    } else if (! (ViewSchema.METHOD_TYPE.equals(type) ||
                  ViewSchema.EVENT_HANDLER_TYPE.equals(type))) {
      // Methods, events (obsolete) must be congruent, so don't need
      // to redeclared, but properties of attributes can be
      // overridden, and hence need to be redeclared.
      String props = styleToLZX();
      if (defaultValue != null &&
          (! defaultValue.equals(superSpec.defaultValue))) {
        props += " value='" + XMLUtils.escapeXml(defaultValue) + "'";
      }
      // TODO: [2010-09-14 ptw] Do we really allow a subclass to
      // redeclare the type?
      if (type != null && (! type.equals(superSpec.type))) {
        props += " type='" + typeToLZX() + "'";
      }
      if (when != null &&
          (! when.equals(superSpec.when))) {
        props += " when='" + when + "'";
      }
      if (required != superSpec.required) {
        props += " required='" + required + "'";
      }
      if (props.length() > 0) {
         value = indent + "<attribute name='" + name + "'" + props + " />";
      }
      if ((setter != null) && (superSpec.setter == null)) {
        if (value == null) { value = ""; } else { value += "\n"; }
        value += indent +"<setter name='" + name + "' />";
      }
    }
    return value;
  }

  @Override
  public String toString() {
    if (ViewSchema.METHOD_TYPE.equals(type)) {
      return "[AttributeSpec: method name='" + name + "'" + (("".equals(arglist))?"":(" args='" + arglist +"'")) + (isfinal?(" isfinal='true'"):"")+" allocation="+allocation+"]";
    } 
    if (ViewSchema.EVENT_HANDLER_TYPE.equals(type)) {
      return "[AttributeSpec: event name='" + name + "' ]";
    }
    return "[AttributeSpec: attribute name='" + name + "'" +
        ((defaultValue != null)?(" value='" + defaultValue + "'"):"") +
        ((type != null)?(" type='" + typeToLZX() + "'"):"") +
        ((when != NodeModel.WHEN_IMMEDIATELY)?(" when='" + when + "'"):"") + 
        (required?(" required='true'"):"") +
      " allocation="+allocation+
        " ";
  }
    
    AttributeSpec (String name, Type type, String defaultValue, String setter, Element source) {
        this.source = source;
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.setter = setter;
        this.when = XMLUtils.getAttributeValue(source, "when", NodeModel.WHEN_IMMEDIATELY);
    }

    AttributeSpec (String name, Type type, String defaultValue, String setter, boolean required, Element source) {
        this.source = source;
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.setter = setter;
        this.required = required;
        this.when = XMLUtils.getAttributeValue(source, "when", NodeModel.WHEN_IMMEDIATELY);
    }

    public AttributeSpec (String name, Type type, String defaultValue, String setter) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.setter = setter;
    }

    AttributeSpec (String name, Type type, String defaultValue, String setter, boolean required) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
        this.setter = setter;
        this.required = required;
    }
}

/**
 * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
