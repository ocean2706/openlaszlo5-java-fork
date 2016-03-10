package org.openlaszlo.test;

import java.io.Reader;
import java.io.StringReader;

import org.openlaszlo.sc.parser.ASTFormalParameterList;
import org.openlaszlo.sc.parser.ASTIdentifier;
import org.openlaszlo.sc.parser.ASTLiteral;
import org.openlaszlo.sc.parser.ASTMethodDeclaration;
import org.openlaszlo.sc.parser.ASTModifiedDefinition;
import org.openlaszlo.sc.parser.ASTOperator;
import org.openlaszlo.sc.parser.ASTPassthroughDirective;
import org.openlaszlo.sc.parser.ParseException;
import org.openlaszlo.sc.parser.Parser;
import org.openlaszlo.sc.parser.SimpleNode;

import junit.framework.TestCase;

/**
 * Automatic semicolon insertion test cases for:
 * <ul>
 * <li>LPP-9714 (Parser does not enfore no-line-terminator in
 * break/continue/throw/postfix-op statements)</li>
 * <li>LPP-9719 (Semicolon after do-while creates empty statement)</li>
 * <li>LPP-9720 (Newline in multi-line comment not treated as newline for ASI)</li>
 * <li>LPP-9721 (TypeIdentifier() should enforce NoLineTerminator before
 * Nullable()/NonNullable())</li>
 * <li>LPP-9722 (Multi-line comment not handled when computing ASI)</li>
 * </ul>
 * 
 * @author André Bargull
 * 
 */
public class TestParserASI extends TestCase {

    public void testParseEquals() {
        // Simple tests for the equals() methods defined below
        assertTrue(equals(parse(""), parse("")));
        assertTrue(equals(parse("var a;"), parse("var a;")));
        assertTrue(equals(parse("var a = 'hello';"), parse("var a = 'hello';")));
        assertFalse(equals(parse("var a;"), parse("var b;")));
        assertTrue(equals(parse("function foo () {}"),
            parse("function foo () {}")));
        assertTrue(equals(parse("function foo () :String {}"),
            parse("function foo () :String {}")));
        assertFalse(equals(parse("function foo () {}"),
            parse("function bar () {}")));
        assertFalse(equals(parse("function bar () {}"),
            parse("function foo () {}")));
        assertFalse(equals(parse("function foo () :String {}"),
            parse("function foo () :Number {}")));
    }

    public void testASI() {
        // simple ASI for break/continue/return
        assertTrue(equals(parse("class Foo {function f(){for(;;){break;}}}"),
            parse("class Foo {function f(){for(;;){break}}}")));
        assertTrue(equals(
            parse("class Foo {function f(){for(;;){continue;}}}"),
            parse("class Foo {function f(){for(;;){continue}}}")));
        assertTrue(equals(parse("class Foo {function f(){for(;;){return;}}}"),
            parse("class Foo {function f(){for(;;){return}}}")));
        assertTrue(equals(parse("function foo () { L0: for (;;){break;} }"),
            parse("function foo () { L0: for (;;){break} }")));
        assertTrue(equals(parse("function foo () { L0: for (;;){continue;} }"),
            parse("function foo () { L0: for (;;){continue} }")));
        assertTrue(equals(parse("function foo () { L0: for (;;){return;} }"),
            parse("function foo () { L0: for (;;){return} }")));

        assertTrue(equals(parse("class Foo {function f(){for(;;){break;}}}"),
            parse("class Foo {function f(){for(;;){break\n}}}")));
        assertTrue(equals(
            parse("class Foo {function f(){for(;;){continue;}}}"),
            parse("class Foo {function f(){for(;;){continue\n}}}")));
        assertTrue(equals(parse("class Foo {function f(){for(;;){return;}}}"),
            parse("class Foo {function f(){for(;;){return\n}}}")));
        assertTrue(equals(parse("function foo () { L0: for (;;){break;} }"),
            parse("function foo () { L0: for (;;){break\n} }")));
        assertTrue(equals(parse("function foo () { L0: for (;;){continue;} }"),
            parse("function foo () { L0: for (;;){continue\n} }")));
        assertTrue(equals(parse("function foo () { L0: for (;;){return;} }"),
            parse("function foo () { L0: for (;;){return\n} }")));

        // ASI with NoLineTerminator special case for break/continue/return
        assertTrue(equals(
            parse("function foo () {L0: { L1: for (;;) {continue; L0; continue L0;}}}"),
            parse("function foo () {L0: { L1: for (;;) {continue\nL0; continue L0;}}}")));
        assertTrue(equals(
            parse("function foo () {L0: { L1: for (;;) {break; L0; break L0;}}}"),
            parse("function foo () {L0: { L1: for (;;) {break\nL0; break L0;}}}")));
        assertTrue(equals(parse("function foo () {return; 0;}"),
            parse("function foo () {return \n 0}")));

        // ASI with NoLineTerminator special case for postfix operator
        assertTrue(equals(parse("a = b; ++c;"), parse("a = b\n++c;")));
        assertTrue(equals(parse("a = b; ++c;"), parse("a = b\n++\nc;")));
        assertTrue(equals(parse("a = b + c(d + e).print()"),
            parse("a = b + c\n(d + e).print()")));
        assertNull(parse("(x\n)-- y"));
        assertNull(parse("(x)-- y"));

        // NoLineTerminator special case for typing
        assertTrue(equals(parse("function foo () {var x:Boolean; !!x}"),
            parse("function foo () {var x:Boolean\n!!x}")));
        assertTrue(equals(parse("function foo () {var x:Boolean?;}"),
            parse("function foo () {var x:Boolean?}")));
        assertTrue(equals(parse("function foo () {var x:Boolean!;}"),
            parse("function foo () {var x:Boolean!}")));

        // ASI with multi-line comment
        assertTrue(equals(parse("function foo () {return; 0;}"),
            parse("function foo () {return /*\n*/0;}")));
        assertTrue(equals(parse("function foo () {var a = 3; console.log(a)}"),
            parse("function foo () {var a = 3\n/* */ console.log(a)}")));

        // ASI for do-while
        assertTrue(equals(parse("function foo () {do {} while(0); (0)}"),
            parse("function foo () {do {} while(0)\n(0)}")));
        assertNull(parse("function foo () {if (a) do {} while (b) else f()}"));
        assertNotNull(parse("function foo () {if (a) do {} while (b); else f()}"));
        assertNull(parse("function foo () {do {} while(0)(0)}"));

        // ASI with NoLineTerminator special case for throw
        assertNull(parse("public class testClass { public function f(){ for(;;){throw; 0} } }"));
        assertNotNull(parse("public class testClass { public function f(){ for(;;){throw 0} } }"));
        assertNull(parse("public class testClass { public function f(){ for(;;){throw\n 0} } }"));
        assertNotNull(parse("function f () {throw 0}"));
        assertNull(parse("function f () {throw \n 0}"));
        assertNull(parse("function f () {throw //\n 0}"));
        assertNull(parse("function f () {throw //\n/**/ 0}"));
    }

    /*
     * Parser stuff follows
     */

    private SimpleNode parse(String s) {
        Reader reader = new StringReader(s);
        Parser parser = new Parser(reader);
        try {
            SimpleNode program = parser.Program();
            return program;
        } catch (ParseException e) {
            return null;
        }
    }

    private static boolean nullsafeEquals(Object expected, Object actual) {
        return (expected == null) ? (actual == null) : expected.equals(actual);
    }

    private static boolean equals(ASTIdentifier.Type expected,
        ASTIdentifier.Type actual) {
        return expected == null ? actual == null : actual != null
            && nullsafeEquals(expected.typeName, actual.typeName)
            && expected.nullable == actual.nullable
            && expected.notnullable == actual.notnullable
            && expected.untyped == actual.untyped;
    }

    private static boolean equals(ASTFormalParameterList expected,
        ASTFormalParameterList actual) {
        return equals(expected.getReturnType(), actual.getReturnType());
    }

    private static boolean equals(ASTIdentifier expected, ASTIdentifier actual) {
        return equals(expected.getType(), actual.getType())
            && nullsafeEquals(expected.getName(), actual.getName())
            && nullsafeEquals(expected.getRegister(), actual.getRegister())
            && expected.getEllipsis() == actual.getEllipsis()
            && expected.isConstructor() == actual.isConstructor();
    }

    private static boolean equals(ASTLiteral expected, ASTLiteral actual) {
        return nullsafeEquals(expected.getValue(), actual.getValue());
    }

    private static boolean equals(ASTMethodDeclaration expected,
        ASTMethodDeclaration actual) {
        return expected.getMethodType() == actual.getMethodType();
    }

    private static boolean equals(ASTModifiedDefinition expected,
        ASTModifiedDefinition actual) {
        return nullsafeEquals(expected.getAccess(), actual.getAccess())
            // && expected.getNamespace.equals(actual.getNamespace())
            && expected.isStatic() == actual.isStatic()
            && expected.isFinal() == actual.isFinal()
            && expected.isDynamic() == actual.isDynamic()
            && expected.isOverride() == actual.isOverride()
            && expected.isStatic() == actual.isStatic();
    }

    private static boolean equals(ASTOperator expected, ASTOperator actual) {
        return expected.getOperator() == actual.getOperator();
    }

    private static boolean equals(ASTPassthroughDirective expected,
        ASTPassthroughDirective actual) {
        return nullsafeEquals(expected.getText(), actual.getText())
            && nullsafeEquals(expected.getProperties(), actual.getProperties());
    }

    private static boolean equals(SimpleNode expected, SimpleNode actual) {
        if (expected == null || actual == null) {
            return (expected == null && actual == null);
        } else if (expected.size() != actual.size()) {
            return false;
        } else if (expected.getClass() != actual.getClass()) {
            return false;
        }

        // handle all modified ast nodes - not really a fail-safe approach...
        if (expected instanceof ASTFormalParameterList) {
            if (!equals((ASTFormalParameterList) expected,
                (ASTFormalParameterList) actual)) {
                return false;
            }
        } else if (expected instanceof ASTIdentifier) {
            if (!equals((ASTIdentifier) expected, (ASTIdentifier) actual)) {
                return false;
            }
        } else if (expected instanceof ASTLiteral) {
            if (!equals((ASTLiteral) expected, (ASTLiteral) actual)) {
                return false;
            }
        } else if (expected instanceof ASTMethodDeclaration) {
            if (!equals((ASTMethodDeclaration) expected,
                (ASTMethodDeclaration) actual)) {
                return false;
            }
        } else if (expected instanceof ASTModifiedDefinition) {
            if (!equals((ASTModifiedDefinition) expected,
                (ASTModifiedDefinition) actual)) {
                return false;
            }
        } else if (expected instanceof ASTOperator) {
            if (!equals((ASTOperator) expected, (ASTOperator) actual)) {
                return false;
            }
        } else if (expected instanceof ASTPassthroughDirective) {
            if (!equals((ASTPassthroughDirective) expected,
                (ASTPassthroughDirective) actual)) {
                return false;
            }
        }

        for (int i = 0, size = expected.size(); i < size; ++i) {
            if (!equals(expected.get(i), actual.get(i))) {
                return false;
            }
        }
        return true;
    }
}

/**
 * @copyright Copyright 2011 Laszlo Systems, Inc. All Rights Reserved. Use is
 *            subject to license terms.
 */
