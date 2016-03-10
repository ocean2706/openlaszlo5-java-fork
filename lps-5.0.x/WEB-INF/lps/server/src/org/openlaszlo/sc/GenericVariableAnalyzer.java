/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.openlaszlo.sc.parser.*;

public abstract class GenericVariableAnalyzer {
  // A ref will cause an auto_reg to be declared
  // compiler magic variables (e.g., $flasm) are always "available"
  static Set<String> AVAILABLE = new HashSet<String>(Instructions.Register.AUTO_REG);
  static {
    AVAILABLE.add("$flasm");
  }

  // _Must_ be in order
  public LinkedHashSet<String> parameters;
  // Kept in order for deterministic code generation
  public LinkedHashSet<String> variables;
  public LinkedHashMap<String, ASTFunctionDeclaration> fundefs;
  // Order unimportant for the rest
  public Set<String> closed;
  public Set<String> free;
  // Contains `.`, `[]`, or `()` expression(s)
  public boolean dereferenced = false;
  // Has `return *` statement
  public boolean hasReturnValue = false;

  boolean ignoreFlasm;
  // Runtime natively supports super calls
  boolean hasSuper;
  Set<String> locals;

  ASTVisitor visitor;

  // create a new analyzer for closures
  public abstract GenericVariableAnalyzer newAnalyzer(SimpleNode params);

  // add a use of a variable by a given node
  public abstract void addUse(String variable, SimpleNode node);

  // merge the uses from another analyzer run for closures
  public abstract void subsumeUses(GenericVariableAnalyzer analyzer);

  // return the used variables
  public abstract Set<String> usedVariables();

  // return the innerFree variables (free variables within closures)
  public abstract Set<String> innerFreeVariables();

  public GenericVariableAnalyzer(boolean ignoreFlasm, boolean hasSuper, ASTVisitor visitor) {
    this.ignoreFlasm = ignoreFlasm;
    this.hasSuper = hasSuper;
    this.visitor = visitor;
    locals = new LinkedHashSet<String>();
    fundefs = new LinkedHashMap<String, ASTFunctionDeclaration>();
    parameters = new LinkedHashSet<String>();
  }

  public void visitParams(SimpleNode params) {
    // Parameter order is significant
    for (int i = 0, len = params.size(); i < len; i++) {
      SimpleNode param = params.get(i);
      // might also be an initializer
      // TODO: [2007-12-20 dda] if it is an default parameter initializer,
      // should call visit() on the initializer expression?
      if (param instanceof ASTIdentifier) {
        parameters.add(((ASTIdentifier)param).getName());
      }
    }
    locals.addAll(parameters);
  }

  // Computes parameters, variables, fundefs, used, closed, free
  public void computeReferences() {
    // variables (locals - parameters)
    variables = new LinkedHashSet<String>(locals);
    variables.removeAll(parameters);
    // available (locals + AVAILABLE)
    Set<String> available = new HashSet<String>(locals);
    available.addAll(AVAILABLE);
    // Closing over a variable counts as a use
    Set<String> innerFree = innerFreeVariables();
    for (String v : innerFree) {
      // the node doesn't matter here, subsumeUses picks that up if needed
      addUse(v, null);
    }
    // calculate actual closed (innerFree & available)
    closed = new HashSet<String>(innerFree);
    closed.retainAll(available);
    // Calculate free references (used - available)
    free = new HashSet<String>(usedVariables());
    free.removeAll(available);
  }

  public void visit(SimpleNode node) {
    SimpleNode[] children;
    // Calculate children for recursive visiting
    if (node instanceof ASTPropertyIdentifierReference) {
      // For `a.b`, only `a` is a reference, not `b`.  (Cf., `a[b]`,
      // where _both_ are references).
      SimpleNode[] c = {node.get(0)};
      children = c;
    } else if (node instanceof ASTIfStatement) {
      children = node.getChildren();
      // Look for $flasm or compile-time conditional
      SimpleNode test = node.get(0);
      if (ignoreFlasm && test instanceof ASTIdentifier && ("$flasm".equals(((ASTIdentifier)test).getName()))) {
        SimpleNode[] c = {test};
        children = c;
      } else {
        // Look for compile-time conditionals
        Boolean value = visitor.evaluateCompileTimeConditional(test);
        if (value == null) {
          // default
        } else if (value.booleanValue()) {
          children = node.get(1).getChildren();
        } else if (node.size() > 2) {
          children = node.get(2).getChildren();
        } else {
          SimpleNode[] c = {};
          children = c;
        }
      }
    } else if (node instanceof ASTSuperCallExpression) {
      // For a super call, only the parameters are references
      SimpleNode[] c = {node.get(2)};
      children = c;
    } else {
      children = node.getChildren();
    }
    // Calculate locals, fundefs, and used
    // (ForVar has a VariableDeclaration as a child, so we don"t
    // need to handle it specially, but ForVarIn/ForEachVar does not.)
    // catch has a variable declaration as a child
    if (node instanceof ASTVariableDeclaration ||
        node instanceof ASTForVarInStatement ||
        node instanceof ASTForEachVarStatement ||
        node instanceof ASTCatchClause) {
      String v = ((ASTIdentifier)children[0]).getName();
      // In ECMAscript you can re-declare variables and
      // parameters and not shadow them
      if (! locals.contains(v)) {
        locals.add(v);
      }
    } else if (node instanceof ASTFunctionDeclaration) {
      String v = ((ASTIdentifier)children[0]).getName();
      fundefs.put(v, (ASTFunctionDeclaration) node);
      if (! locals.contains(v)) {
        locals.add(v);
      }
    } else if (node instanceof ASTIdentifier) {
      addUse(((ASTIdentifier)node).getName(), node);
    } else if (node instanceof ASTThisReference) {
      addUse("this", node);
    }
    // Calculate dereferenced
    if ((node instanceof ASTPropertyIdentifierReference) ||
        (node instanceof ASTPropertyValueReference) ||
        (node instanceof ASTCallExpression)) {
      dereferenced = true;
    }
    // For JS1 runtimes, super calls are translated into runtime calls
    // that reference `this` and `arguments`.  Chicken/egg problem --
    // we'd like to translate before analyzing, but translation uses
    // analysis.  We need a separate transformer phase...
    if ((node instanceof ASTSuperCallExpression) &&
         (! hasSuper)) {
      addUse("this", node);
      addUse("arguments", node);
    }
    // Calculate hasReturnValue
    if ((node instanceof ASTReturnStatement) &&
        (! (children[0] instanceof ASTEmptyExpression))) {
      hasReturnValue = true;
    }

    // Now descend into children.  Closures get special treatment.
    if (node instanceof ASTFunctionDeclaration ||
        node instanceof ASTFunctionExpression) {
      SimpleNode params = children[children.length - 2];
      SimpleNode stmts = children[children.length - 1];
      GenericVariableAnalyzer analyzer = newAnalyzer(params);
      for (int i = 0, len = stmts.size(); i < len; i++) {
        SimpleNode stmt = stmts.get(i);
        analyzer.visit(stmt);
      }
      subsumeUses(analyzer);
    } else if (node instanceof ASTObjectLiteral) {
      // Only the values of an object literal are references, the keys
      // are constants
      for (int i = 1, len = children.length; i < len; i += 2) {
        visit(children[i]);
      }
    } else {
      for (int i = 0, len = children.length; i < len; i++) {
        visit(children[i]);
      }
    }
  }

  void visit(List<SimpleNode> list) {
    for (SimpleNode node : list) {
      visit(node);
    }
  }
}
