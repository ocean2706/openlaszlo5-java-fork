/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * LZX Function representation
 * @author steele@osteele.com
 */

package org.openlaszlo.sc;

public class Method extends Function {
  private final String adjectives;

  public Method(String body) {
    this("", body);
  }

  public Method(String args, String body) {
    this("", args, body);
  }

  public Method(String name, String args, String body) {
    this(name, args, "", "", body, null, null);
  }

  // When there is a source location, we ask that the body be broken
  // up into a preface (any pragmas, etc. that the compiler must add)
  // and the body - the original function body in the program.
  public Method(String name, String args, String returnType, String preface, String body, String loc, String adjectives) {
    super(name, args, returnType, preface, body, loc);
    this.adjectives = adjectives;
  }

  @Override
  public String toString() {
    return  (adjectives != null?(adjectives + " "):"") +
      super.toString();
  }

  @Override
  public Function asFunction() {
    return new Function(name, args, null, preface, body, sourceLocation);
  }
}

/**
 * @copyright Copyright 2001-2008, 2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
