package org.openlaszlo.sc;

import org.openlaszlo.sc.parser.ASTAndExpressionSequence;
import org.openlaszlo.sc.parser.ASTArrayLiteral;
import org.openlaszlo.sc.parser.ASTAssignmentExpression;
import org.openlaszlo.sc.parser.ASTBinaryExpressionSequence;
import org.openlaszlo.sc.parser.ASTBreakStatement;
import org.openlaszlo.sc.parser.ASTCallExpression;
import org.openlaszlo.sc.parser.ASTCaseClause;
import org.openlaszlo.sc.parser.ASTCatchClause;
import org.openlaszlo.sc.parser.ASTClassDefinition;
import org.openlaszlo.sc.parser.ASTClassDirectiveBlock;
import org.openlaszlo.sc.parser.ASTClassIfDirective;
import org.openlaszlo.sc.parser.ASTConditionalExpression;
import org.openlaszlo.sc.parser.ASTContinueStatement;
import org.openlaszlo.sc.parser.ASTDebuggerStatement;
import org.openlaszlo.sc.parser.ASTDefaultClause;
import org.openlaszlo.sc.parser.ASTDirectiveBlock;
import org.openlaszlo.sc.parser.ASTDoWhileStatement;
import org.openlaszlo.sc.parser.ASTEmptyExpression;
import org.openlaszlo.sc.parser.ASTExpressionList;
import org.openlaszlo.sc.parser.ASTFinallyClause;
import org.openlaszlo.sc.parser.ASTForEachStatement;
import org.openlaszlo.sc.parser.ASTForEachVarStatement;
import org.openlaszlo.sc.parser.ASTForInStatement;
import org.openlaszlo.sc.parser.ASTForStatement;
import org.openlaszlo.sc.parser.ASTForVarInStatement;
import org.openlaszlo.sc.parser.ASTForVarStatement;
import org.openlaszlo.sc.parser.ASTFormalInitializer;
import org.openlaszlo.sc.parser.ASTFormalParameterList;
import org.openlaszlo.sc.parser.ASTFunctionCallParameters;
import org.openlaszlo.sc.parser.ASTFunctionDeclaration;
import org.openlaszlo.sc.parser.ASTFunctionExpression;
import org.openlaszlo.sc.parser.ASTIdentifier;
import org.openlaszlo.sc.parser.ASTIfDirective;
import org.openlaszlo.sc.parser.ASTIfStatement;
import org.openlaszlo.sc.parser.ASTIncludeDirective;
import org.openlaszlo.sc.parser.ASTLabeledStatement;
import org.openlaszlo.sc.parser.ASTLiteral;
import org.openlaszlo.sc.parser.ASTMethodDeclaration;
import org.openlaszlo.sc.parser.ASTMixinsList;
import org.openlaszlo.sc.parser.ASTModifiedDefinition;
import org.openlaszlo.sc.parser.ASTNewExpression;
import org.openlaszlo.sc.parser.ASTObjectLiteral;
import org.openlaszlo.sc.parser.ASTOperator;
import org.openlaszlo.sc.parser.ASTOrExpressionSequence;
import org.openlaszlo.sc.parser.ASTPassthroughDirective;
import org.openlaszlo.sc.parser.ASTPostfixExpression;
import org.openlaszlo.sc.parser.ASTPragmaDirective;
import org.openlaszlo.sc.parser.ASTProgram;
import org.openlaszlo.sc.parser.ASTPropertyIdentifierReference;
import org.openlaszlo.sc.parser.ASTPropertyValueReference;
import org.openlaszlo.sc.parser.ASTReturnStatement;
import org.openlaszlo.sc.parser.ASTStatement;
import org.openlaszlo.sc.parser.ASTStatementList;
import org.openlaszlo.sc.parser.ASTSuperCallExpression;
import org.openlaszlo.sc.parser.ASTSwitchStatement;
import org.openlaszlo.sc.parser.ASTThisReference;
import org.openlaszlo.sc.parser.ASTThrowStatement;
import org.openlaszlo.sc.parser.ASTTryStatement;
import org.openlaszlo.sc.parser.ASTUnaryExpression;
import org.openlaszlo.sc.parser.ASTVariableDeclaration;
import org.openlaszlo.sc.parser.ASTVariableDeclarationList;
import org.openlaszlo.sc.parser.ASTVariableStatement;
import org.openlaszlo.sc.parser.ASTWhileStatement;
import org.openlaszlo.sc.parser.ASTWithStatement;
import org.openlaszlo.sc.parser.ParserVisitor;
import org.openlaszlo.sc.parser.SimpleNode;

/**
 * Simple {@link ParserVisitor} which returns <code>null</code> for every
 * visit() method.
 * 
 * @author André Bargull
 * 
 */
class EmptyParserVisitor implements ParserVisitor {

    public Object visit(SimpleNode arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTLiteral arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTIdentifier arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTThisReference arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTPropertyValueReference arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTPropertyIdentifierReference arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTFunctionCallParameters arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTArrayLiteral arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTEmptyExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTObjectLiteral arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTCallExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTNewExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTSuperCallExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTOperator arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTPostfixExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTUnaryExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTBinaryExpressionSequence arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTAndExpressionSequence arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTOrExpressionSequence arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTConditionalExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTAssignmentExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTExpressionList arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTStatementList arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTVariableStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTVariableDeclarationList arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTVariableDeclaration arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTIfStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTWhileStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTDoWhileStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTForStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTForVarStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTForInStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTForVarInStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTForEachStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTForEachVarStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTContinueStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTBreakStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTReturnStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTWithStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTSwitchStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTCaseClause arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTDefaultClause arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTLabeledStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTThrowStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTTryStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTCatchClause arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTFinallyClause arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTDebuggerStatement arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTFunctionDeclaration arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTMethodDeclaration arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTFunctionExpression arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTFormalParameterList arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTFormalInitializer arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTProgram arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTModifiedDefinition arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTDirectiveBlock arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTIncludeDirective arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTPragmaDirective arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTPassthroughDirective arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTIfDirective arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTMixinsList arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTClassDirectiveBlock arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTClassIfDirective arg0, Object arg1) {
        return null;
    }

    public Object visit(ASTClassDefinition arg0, Object arg1) {
        return null;
    }

}
/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/