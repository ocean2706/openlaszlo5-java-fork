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
 * Simple {@link ParserVisitor} which returns a fresh instance of the visited element
 * 
 * @author André Bargull
 * 
 */
class NewInstanceVisitor implements ParserVisitor {

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.SimpleNode, java.lang.Object)
     */
    public Object visit(SimpleNode node, Object data) {
        throw new IllegalStateException("unexpected call with type: "
            + node.getClass());
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTLiteral, java.lang.Object)
     */
    public Object visit(ASTLiteral node, Object data) {
        return new ASTLiteral(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTIdentifier, java.lang.Object)
     */
    public Object visit(ASTIdentifier node, Object data) {
        return new ASTIdentifier(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTThisReference, java.lang.Object)
     */
    public Object visit(ASTThisReference node, Object data) {
        return new ASTThisReference(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTPropertyValueReference, java.lang.Object)
     */
    public Object visit(ASTPropertyValueReference node, Object data) {
        return new ASTPropertyValueReference(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTPropertyIdentifierReference, java.lang.Object)
     */
    public Object visit(ASTPropertyIdentifierReference node, Object data) {
        return new ASTPropertyIdentifierReference(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTFunctionCallParameters, java.lang.Object)
     */
    public Object visit(ASTFunctionCallParameters node, Object data) {
        return new ASTFunctionCallParameters(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTArrayLiteral, java.lang.Object)
     */
    public Object visit(ASTArrayLiteral node, Object data) {
        return new ASTArrayLiteral(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTEmptyExpression, java.lang.Object)
     */
    public Object visit(ASTEmptyExpression node, Object data) {
        return new ASTEmptyExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTObjectLiteral, java.lang.Object)
     */
    public Object visit(ASTObjectLiteral node, Object data) {
        return new ASTObjectLiteral(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTCallExpression, java.lang.Object)
     */
    public Object visit(ASTCallExpression node, Object data) {
        return new ASTCallExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTNewExpression, java.lang.Object)
     */
    public Object visit(ASTNewExpression node, Object data) {
        return new ASTNewExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTSuperCallExpression, java.lang.Object)
     */
    public Object visit(ASTSuperCallExpression node, Object data) {
        return new ASTSuperCallExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTOperator, java.lang.Object)
     */
    public Object visit(ASTOperator node, Object data) {
        return new ASTOperator(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTPostfixExpression, java.lang.Object)
     */
    public Object visit(ASTPostfixExpression node, Object data) {
        return new ASTPostfixExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTUnaryExpression, java.lang.Object)
     */
    public Object visit(ASTUnaryExpression node, Object data) {
        return new ASTUnaryExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTBinaryExpressionSequence, java.lang.Object)
     */
    public Object visit(ASTBinaryExpressionSequence node, Object data) {
        return new ASTBinaryExpressionSequence(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTAndExpressionSequence, java.lang.Object)
     */
    public Object visit(ASTAndExpressionSequence node, Object data) {
        return new ASTAndExpressionSequence(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTOrExpressionSequence, java.lang.Object)
     */
    public Object visit(ASTOrExpressionSequence node, Object data) {
        return new ASTOrExpressionSequence(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTConditionalExpression, java.lang.Object)
     */
    public Object visit(ASTConditionalExpression node, Object data) {
        return new ASTConditionalExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTAssignmentExpression, java.lang.Object)
     */
    public Object visit(ASTAssignmentExpression node, Object data) {
        return new ASTAssignmentExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTExpressionList, java.lang.Object)
     */
    public Object visit(ASTExpressionList node, Object data) {
        return new ASTExpressionList(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTStatement, java.lang.Object)
     */
    public Object visit(ASTStatement node, Object data) {
        return new ASTStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTStatementList, java.lang.Object)
     */
    public Object visit(ASTStatementList node, Object data) {
        return new ASTStatementList(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTVariableStatement, java.lang.Object)
     */
    public Object visit(ASTVariableStatement node, Object data) {
        return new ASTVariableStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTVariableDeclarationList, java.lang.Object)
     */
    public Object visit(ASTVariableDeclarationList node, Object data) {
        return new ASTVariableDeclarationList(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTVariableDeclaration, java.lang.Object)
     */
    public Object visit(ASTVariableDeclaration node, Object data) {
        return new ASTVariableDeclaration(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTIfStatement, java.lang.Object)
     */
    public Object visit(ASTIfStatement node, Object data) {
        return new ASTIfStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTWhileStatement, java.lang.Object)
     */
    public Object visit(ASTWhileStatement node, Object data) {
        return new ASTWhileStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTDoWhileStatement, java.lang.Object)
     */
    public Object visit(ASTDoWhileStatement node, Object data) {
        return new ASTDoWhileStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTForStatement, java.lang.Object)
     */
    public Object visit(ASTForStatement node, Object data) {
        return new ASTForStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTForVarStatement, java.lang.Object)
     */
    public Object visit(ASTForVarStatement node, Object data) {
        return new ASTForVarStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTForInStatement, java.lang.Object)
     */
    public Object visit(ASTForInStatement node, Object data) {
        return new ASTForInStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTForVarInStatement, java.lang.Object)
     */
    public Object visit(ASTForVarInStatement node, Object data) {
        return new ASTForVarInStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTForEachStatement, java.lang.Object)
     */
    public Object visit(ASTForEachStatement arg0, Object arg1) {
        return new ASTForEachStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTForEachVarStatement, java.lang.Object)
     */
    public Object visit(ASTForEachVarStatement arg0, Object arg1) {
        return new ASTForEachVarStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTContinueStatement, java.lang.Object)
     */
    public Object visit(ASTContinueStatement node, Object data) {
        return new ASTContinueStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTBreakStatement, java.lang.Object)
     */
    public Object visit(ASTBreakStatement node, Object data) {
        return new ASTBreakStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTReturnStatement, java.lang.Object)
     */
    public Object visit(ASTReturnStatement node, Object data) {
        return new ASTReturnStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTWithStatement, java.lang.Object)
     */
    public Object visit(ASTWithStatement node, Object data) {
        return new ASTWithStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTSwitchStatement, java.lang.Object)
     */
    public Object visit(ASTSwitchStatement node, Object data) {
        return new ASTSwitchStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTCaseClause, java.lang.Object)
     */
    public Object visit(ASTCaseClause node, Object data) {
        return new ASTCaseClause(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTDefaultClause, java.lang.Object)
     */
    public Object visit(ASTDefaultClause node, Object data) {
        return new ASTDefaultClause(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTLabeledStatement, java.lang.Object)
     */
    public Object visit(ASTLabeledStatement node, Object data) {
        return new ASTLabeledStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTThrowStatement, java.lang.Object)
     */
    public Object visit(ASTThrowStatement node, Object data) {
        return new ASTThrowStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTTryStatement, java.lang.Object)
     */
    public Object visit(ASTTryStatement node, Object data) {
        return new ASTTryStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTCatchClause, java.lang.Object)
     */
    public Object visit(ASTCatchClause node, Object data) {
        return new ASTCatchClause(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTFinallyClause, java.lang.Object)
     */
    public Object visit(ASTFinallyClause node, Object data) {
        return new ASTFinallyClause(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTDebuggerStatement, java.lang.Object)
     */
    public Object visit(ASTDebuggerStatement node, Object data) {
        return new ASTDebuggerStatement(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTFunctionDeclaration, java.lang.Object)
     */
    public Object visit(ASTFunctionDeclaration node, Object data) {
        return new ASTFunctionDeclaration(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTMethodDeclaration, java.lang.Object)
     */
    public Object visit(ASTMethodDeclaration node, Object data) {
        return new ASTMethodDeclaration(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTFunctionExpression, java.lang.Object)
     */
    public Object visit(ASTFunctionExpression node, Object data) {
        return new ASTFunctionExpression(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTFormalParameterList, java.lang.Object)
     */
    public Object visit(ASTFormalParameterList node, Object data) {
        return new ASTFormalParameterList(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTFormalInitializer, java.lang.Object)
     */
    public Object visit(ASTFormalInitializer node, Object data) {
        return new ASTFormalInitializer(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTProgram, java.lang.Object)
     */
    public Object visit(ASTProgram node, Object data) {
        return new ASTProgram(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTModifiedDefinition, java.lang.Object)
     */
    public Object visit(ASTModifiedDefinition node, Object data) {
        return new ASTModifiedDefinition(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTDirectiveBlock, java.lang.Object)
     */
    public Object visit(ASTDirectiveBlock node, Object data) {
        return new ASTDirectiveBlock(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTIncludeDirective, java.lang.Object)
     */
    public Object visit(ASTIncludeDirective node, Object data) {
        return new ASTIncludeDirective(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTPragmaDirective, java.lang.Object)
     */
    public Object visit(ASTPragmaDirective node, Object data) {
        return new ASTPragmaDirective(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTPassthroughDirective, java.lang.Object)
     */
    public Object visit(ASTPassthroughDirective node, Object data) {
        return new ASTPassthroughDirective(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTIfDirective, java.lang.Object)
     */
    public Object visit(ASTIfDirective node, Object data) {
        return new ASTIfDirective(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTMixinsList, java.lang.Object)
     */
    public Object visit(ASTMixinsList node, Object data) {
        return new ASTMixinsList(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTClassDirectiveBlock, java.lang.Object)
     */
    public Object visit(ASTClassDirectiveBlock node, Object data) {
        return new ASTClassDirectiveBlock(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTClassIfDirective, java.lang.Object)
     */
    public Object visit(ASTClassIfDirective node, Object data) {
        return new ASTClassIfDirective(0);
    }

    /* (non-Javadoc)
     * @see org.openlaszlo.sc.parser.ParserVisitor#visit(org.openlaszlo.sc.parser.ASTClassDefinition, java.lang.Object)
     */
    public Object visit(ASTClassDefinition node, Object data) {
        return new ASTClassDefinition(0);
    }
}
/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2011 Laszlo Systems, Inc.  All Rights Reserved.                   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
