/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * Common Code for Translation and Code Generation
 *
 * @author steele@osteele.com
 * @author ptw@openlaszlo.org
 * @author dda@ddanderson.com
 * @description: Common baseclass for code generators
 *
 * This interface defines the methods required to walk the abstract
 * syntax tree.
 */

//
// AST Visitor
//

package org.openlaszlo.sc;

import org.openlaszlo.sc.parser.*;

/**
 * Methods to visit each kind of object in the AST.
 */
public interface ASTVisitor {

  // Required interface
  public Compiler.OptionMap getOptions();

  public void setOptions(Compiler.OptionMap options);

  Boolean evaluateCompileTimeConditional(SimpleNode node);

  //
  // All the AST types that need visiting
  //
  SimpleNode visitAndExpressionSequence(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitArrayLiteral(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitAssignmentExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitBinaryExpressionSequence(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitBreakStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitCallExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitCatchClause(SimpleNode node, SimpleNode[] children);
  SimpleNode visitClassDefinition(SimpleNode node, SimpleNode[] children);
  SimpleNode visitConditionalExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitContinueStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitDirectiveBlock(SimpleNode node, SimpleNode[] children);
  SimpleNode visitDoWhileStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitEmptyExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
//  SimpleNode visitExpression(SimpleNode node);
  SimpleNode visitExpression(SimpleNode node, boolean isReferenced);
  SimpleNode visitExpressionList(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitFinallyClause(SimpleNode node, SimpleNode[] children);
  SimpleNode visitForInStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitForStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitForVarInStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitForVarStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitForEachStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitForEachVarStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitFunctionCallParameters(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitFunctionDeclaration(SimpleNode node, SimpleNode[] ast);
  SimpleNode visitFunctionExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitIdentifier(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitIfDirective(SimpleNode node, SimpleNode[] children);
  SimpleNode visitIfStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitLabeledStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitLiteral(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitMethodDeclaration(SimpleNode node, SimpleNode[] ast);
  SimpleNode visitModifiedDefinition(SimpleNode node, SimpleNode[] children);
  SimpleNode visitNewExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitObjectLiteral(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitOrExpressionSequence(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitPassthroughDirective(SimpleNode node, SimpleNode[] children);
  SimpleNode visitPostfixExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitPragmaDirective(SimpleNode node, SimpleNode[] children);
  SimpleNode visitPrefixExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitProgram(SimpleNode node, SimpleNode[] directives);
  SimpleNode visitPropertyIdentifierReference(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitPropertyValueReference(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitReturnStatement(SimpleNode node, SimpleNode[] children);
//  SimpleNode visitStatement(SimpleNode node);
  SimpleNode visitStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitStatementList(SimpleNode node, SimpleNode[] children);
  SimpleNode visitSuperCallExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitSwitchStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitThisReference(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitThrowStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitTryStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitUnaryExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  SimpleNode visitVariableDeclaration(SimpleNode node, SimpleNode[] children);
  SimpleNode visitVariableDeclarationList(SimpleNode node, SimpleNode[] children);
  SimpleNode visitVariableStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitWhileStatement(SimpleNode node, SimpleNode[] children);
  SimpleNode visitWithStatement(SimpleNode node, SimpleNode[] children);
}

/**
 * @copyright Copyright 2006-2009, 2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */

