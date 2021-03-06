/* -*- mode: Java; c-basic-offset: 2; -*- */

package org.openlaszlo.sc;

import org.openlaszlo.sc.parser.SimpleNode;

@SuppressWarnings("serial")
public class CompilerImplementationError extends RuntimeException {
  SimpleNode node;

  public CompilerImplementationError (String message) {
    super(message);
    this.node = null;
  }

  public CompilerImplementationError (String message, SimpleNode node) {
    super(message);
    this.node = node;
  }

  @Override
  public String toString() {
    return Compiler.getLocationString(node) + ": " + super.toString();
  }
}

/**
 * @copyright Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
