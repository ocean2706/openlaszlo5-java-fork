/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * <mixin> compiler
 *
 * @author ptw@openlaszlo.org
 *
 * Adds mixin to schema
 */

package org.openlaszlo.compiler;

import java.io.File;
import java.util.Set;

import org.jdom.Element;

/**
 * Compiler for <code>mixin</code> elements.
 */
class MixinCompiler extends ClassCompiler {
    
  MixinCompiler(CompilationEnvironment env) {
    super(env);
  }
    
  /**
   * Returns true iff this class applies to this element.
   * @param element an element
   * @return see doc
   */
  static boolean isElement(Element element) {
    return element.getName().equals(ViewSchema.KIND_MIXIN);
  }

  @Override
  void updateSchema(Element element, ViewSchema schema, Set<File> visited) {
    // although MixinCompiler extends ClassCompiler, you cannot extend mixins
    ensureValidDeclaration(element);
    super.updateSchema(element, schema, visited);
  }

  /**
   * Throws {@link CompilationError} if <code>element</code> contains one
   * of the following attributes:
   * <ul>
   * <li>"extends"</li>
   * <li>"with"</li>
   * <li>"implements"</li>
   * </ul>
   * 
   * @param element
   * @throws CompilationError
   */
  private void ensureValidDeclaration (Element element) throws CompilationError {
    if (element.getAttributeValue("extends") != null) {
      CompilationError cerr = new CompilationError(
        "'extends' not allowed for <mixin>", element);
      throw cerr;
    } else if (element.getAttributeValue("with") != null) {
      CompilationError cerr = new CompilationError(
        "'with' not allowed for <mixin>", element);
      throw cerr;
    } else if (element.getAttributeValue("implements") != null) {
      CompilationError cerr = new CompilationError(
        "'implements' not allowed for <mixin>", element);
      throw cerr;
    }
  }
}

/**
 * @copyright Copyright 2007-2008, 2010-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
