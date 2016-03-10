/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/


/**
 * LZX Types
 */

package org.openlaszlo.compiler;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

import org.jdom.Element;
import org.openlaszlo.sc.Method;
import org.openlaszlo.sc.ScriptClass;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.xml.internal.XMLUtils;

/**
 * Compiler for <code>type</code> elements.
 *
 * A <code>type</code> element defines a new 'presentation type' which
 * can be used as the type of any attribute.  The type must define how
 * to transform the Javascript representation of the type to/from a
 * String (which is how databinding is acheived).
 */
class TypeCompiler extends ClassCompiler {
    TypeCompiler(CompilationEnvironment env) {
        super(env);
    }

    /** Returns true iff this class applies to this element.
     * @param element an element
     * @return see doc
     */
    public static boolean isElement(Element element) {
        return element.getName().equals(ViewSchema.KIND_TYPE);
    }

  static class TypeClassModel extends ClassModel {
    public TypeClassModel(String kind, String tagName, boolean publish,
                          ViewSchema schema, Element definition, CompilationEnvironment env) {
      super(kind, "anonymous", null, false, schema, definition, env);
      className = LZXTag2JSClass(tagName, kind);
    }

    @Override
    public ClassModel resolve(CompilationEnvironment env)
         throws CompilationError {
             
        if (resolved) { return this; }
        resolved = true;
        return this;
    }
    
    protected void compile(CompilationEnvironment env, boolean force) {
    
        String typename = XMLUtils.requireAttributeValue(definition, "name");
        LinkedHashMap<String, Object> instanceAttributes = new LinkedHashMap<String, Object>();
        LinkedHashMap<String, Object> classAttributes = new LinkedHashMap<String, Object>();
        for (Iterator<?> iter = definition.getChildren().iterator(); iter.hasNext(); ) {
          ElementWithLocationInfo child = (ElementWithLocationInfo) iter.next();
          String childName = child.getName();
          String name_loc = CompilerUtils.attributeLocationDirective(child, "name");
          String args = CompilerUtils.attributeLocationDirective(child, "args") +
            XMLUtils.getAttributeValue(child, "args", "");
          String returnType = child.getAttributeValue("returns");
          String body = child.getText();
          if (childName.equals("accept") || childName.equals("present")) {
            instanceAttributes.put(childName, new Method(childName, args, returnType, "", body, name_loc, "override"));
          } else {
            env.warn(
              // TODO [2007-09-26 hqm] i18n this
              "The tag '" + childName + "' cannot be used as a child of <type>", definition);
          }
        }
        classAttributes.put("lzxtype", ScriptCompiler.quote(typename));
        ScriptClass scriptClass = new ScriptClass(className, "$lz$class_PresentationType", null, instanceAttributes, classAttributes, "", "class");
        env.compileScript(scriptClass.toString(), definition);
        env.compileScript("lz.Type.addType('" + typename + "', new " + className + "());");
    }
  }

    /**
     * Ensure that the definition has a present and accept method.  It
     * can have other property elements, but should not have any
     * subnodes.
     */
    @Override
    void updateSchema(Element elt, ViewSchema schema, Set<File> visited) {
      // We intentionally do _not_ call the super method.  We don't
      // want to define a tag with this name
      String name = elt.getAttributeValue("name");
      String superName = elt.getAttributeValue("extends");

      if (name == null || (! ScriptCompiler.isIdentifier(name))) {
        throw(new CompilationError("The <type> \"name\" attribute must be a valid identifier", elt));
      }
      if (superName != null && (! ScriptCompiler.isIdentifier(name))) {
        throw(new CompilationError("The <type> \"extends\" attribute must be a valid identifier", elt));
      }

      schema.putClassModel(ViewSchema.KIND_TYPE, name, new TypeClassModel(ViewSchema.KIND_TYPE, name, false, schema, elt, mEnv));
      // Add the type to the schema (will err if there is a duplicate)
      schema.defineType(name, elt);
    }

    @Override
    public void compile(Element element) {
    
        String tagName = element.getAttributeValue("name");
        if (tagName.equals("anonymous")) {
              CompilationError cerr = new CompilationError(
                  "The type 'anonymous' is reserved for system use, please choose another class name"
                  , element);
              throw(cerr);
        }        
              
        ClassModel classModel = mEnv.getSchema().getInstanceTypeClassModel(element);
        classModel.compile(mEnv, true);
    }
}

/**
 * @copyright Copyright 2010-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
