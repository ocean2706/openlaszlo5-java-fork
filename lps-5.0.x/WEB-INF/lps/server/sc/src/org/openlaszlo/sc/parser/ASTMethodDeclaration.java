package org.openlaszlo.sc.parser;

public class ASTMethodDeclaration extends SimpleNode {
  public static enum MethodType {
    DEFAULT, GETTER, SETTER;
  }

  private MethodType methodType = MethodType.DEFAULT;

  public ASTMethodDeclaration(int id) {
    super(id);
  }

  public ASTMethodDeclaration(Parser p, int id) {
    super(p, id);
  }

  public void setMethodType(MethodType methodType) {
    if (methodType == null) {
      throw new IllegalArgumentException("methodType must not be null");
    }
    this.methodType = methodType;
  }

  public MethodType getMethodType() {
    return this.methodType;
  }

  public SimpleNode deepCopy() {
    ASTMethodDeclaration result = (ASTMethodDeclaration)super.deepCopy();
    result.methodType = this.methodType;
    return result;
  }

  /** Accept the visitor. **/
  public Object jjtAccept(ParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2011 Laszlo Systems, Inc.  All Rights Reserved.                   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/