/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * Javascript Class representation
 *
 * @author ptw@openlaszlo.org
 */

package org.openlaszlo.sc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ScriptClass {
  String name;
  String kind = null;
  String superclass;
  String[] interfaces;
  Map<String, Object> attributes;
  Map<String, Object> classAttributes;
  String classBody;

  public ScriptClass(String name, String superclass, String[] interfaces,
      Map<String, Object> attributes, Map<String, Object> classAttributes,
      String classBody, String kind) {
    this.name = name;
    this.superclass = superclass;
    this.interfaces = interfaces;
    this.attributes = attributes;
    this.classAttributes = classAttributes;
    this.classBody = classBody;
    this.kind = kind;
  }

    // This list of Javascript reserved words is not complete, it's only
    // the ones we stumble across.
  private static final Set<String> RESERVED;
  static {
      HashSet<String> set = new HashSet<String>();
      Collections.addAll(set, "class", "mixin", "interface", "with", "import", "extends", "implements");
      RESERVED = Collections.unmodifiableSet(set);
  }

  public String joinids(String[] ids, String sep) {
    String result = "";
    for (String id : ids) {
      if (! RESERVED.contains(id)) {
        if (! result.equals("")) {
          result += sep;
        }
        result += id;
      }
    }
    return result;
  }

  @Override
  public String toString() {
    if (RESERVED.contains(name))
      return "";
    assert kind != null;
    String extends_str = "";
    String implements_str = "";

    if (superclass != null && (! RESERVED.contains(superclass))) {
      extends_str = " extends " + superclass + " ";
    }
    if (interfaces != null) {
      String joinable = joinids(interfaces, ", ");
      if (! "".equals(joinable)) {
        implements_str = " implements " + joinable;
      }
    }

    String modifier ="dynamic ";
    if (kind.equals("mixin")) {
      extends_str = "";
    } else {
      kind = "class";
    }

    // For now we make all user classes dynamic
    StringBuilder str = new StringBuilder(1024);
    str.append(modifier)
       .append(kind)
       .append(' ')
       .append(name)
       .append(extends_str)
       .append(implements_str)
       .append(" {\n");
    int n = 1; Map<String, Object> attrs = classAttributes; String prefix = "static ";
    for (; n <= 2; n++, attrs = attributes, prefix = "") {
      if (attrs != null) {
        for (Map.Entry<String, Object> entry : attrs.entrySet()) {
          String name = entry.getKey();
          if (! RESERVED.contains(name)) {
            Object value = entry.getValue();
            if (value instanceof Method) {
              Method fn = (Method)value;
              fn.setName(name);
              str.append(prefix)
                 .append(value.toString())
                 .append('\n');
            } else if (value != null) {
              str.append(prefix)
                 .append("var ")
                 .append(name)
                 .append(" = ")
                 .append(ScriptCompiler.objectAsJavascript(value))
                 .append(";\n");
            } else {
              str.append(prefix)
                 .append("var ")
                 .append(name)
                 .append(";\n");
            }
          }
        }
      }
    }
    str.append(classBody).append("}\n");
    return str.toString();
  }
}

/**
 * @copyright Copyright 2006-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
