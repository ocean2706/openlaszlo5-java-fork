/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * <interface> compiler
 *
 * @author ptw@openlaszlo.org
 *
 * Adds interface to schema
 */

package org.openlaszlo.compiler;

import java.io.File;
import java.util.Set;

import org.jdom.Element;

/**
 * Compiler for <code>interface</code> elements.
 */
class InterfaceCompiler extends ClassCompiler {
    
  InterfaceCompiler(CompilationEnvironment env) {
    super(env);
  }
    
  /**
   * Returns true iff this class applies to this element.
   * @param element an element
   * @return see doc
   */
  static boolean isElement(Element element) {
    return element.getName().equals(ViewSchema.KIND_INTERFACE);
  }

  @Override
  void updateSchema(Element element, ViewSchema schema, Set<File> visited) {
    // although InterfaceCompiler extends ClassCompiler, you cannot extend interfaces
    ensureValidDeclaration(element);
    super.updateSchema(element, schema, visited);
  }

  /**
   * Throws {@link CompilationError} if <code>element</code> contains one
   * of the following attributes:
   * <ul>
   * <li>"with"</li>
   * <li>"implements"</li>
   * </ul>
   * 
   * @param element
   * @throws CompilationError
   */
  private void ensureValidDeclaration (Element element) {
    // "extends" and "implements" are allowed for <interface>, see lfc.lzx schema file
    if (element.getAttributeValue("with") != null) {
      CompilationError cerr = new CompilationError(
        "'with' not allowed for <interface>", element);
      throw cerr;
    }
  }
}

/**
 * @copyright Copyright 2007-2008, 2010-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
