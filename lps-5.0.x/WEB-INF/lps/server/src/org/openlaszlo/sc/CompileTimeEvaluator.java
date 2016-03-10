package org.openlaszlo.sc;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openlaszlo.sc.parser.ASTAndExpressionSequence;
import org.openlaszlo.sc.parser.ASTBinaryExpressionSequence;
import org.openlaszlo.sc.parser.ASTCallExpression;
import org.openlaszlo.sc.parser.ASTConditionalExpression;
import org.openlaszlo.sc.parser.ASTIdentifier;
import org.openlaszlo.sc.parser.ASTLiteral;
import org.openlaszlo.sc.parser.ASTOperator;
import org.openlaszlo.sc.parser.ASTOrExpressionSequence;
import org.openlaszlo.sc.parser.ASTPropertyIdentifierReference;
import org.openlaszlo.sc.parser.ASTPropertyValueReference;
import org.openlaszlo.sc.parser.ASTUnaryExpression;
import org.openlaszlo.sc.parser.ParserConstants;
import org.openlaszlo.sc.parser.ParserVisitor;
import org.openlaszlo.sc.parser.SimpleNode;

/**
 * Compile-time evaluator, supports the following operations:
 * <ul>
 * <li>simple compile-time identifier, like <code>$dhtml</code></li>
 * <li>object compile-time identifier, like <code>$quirks</code></li>
 * <li>constant literal, like {@literal true} or {@literal 1}</li>
 * <li>logical expressions (!, &&, ||)</li>
 * <li>equality expressions (==, !=, ===, !==)</li>
 * <li>relational expressions (<, <=, >, >=)</li>
 * <li>bit-wise expressions (~, &, |, ^)</li>
 * <li>arithmetic expressions (+, -, *, /, %)</li>
 * <li>others (typeof, void)</li>
 * </ul>
 * 
 * @author André Bargull
 * @see #evaluate(SimpleNode, Map)
 */
final class CompileTimeEvaluator extends EmptyParserVisitor implements
    ParserVisitor {
    private static final CompileTimeEvaluator INSTANCE = new CompileTimeEvaluator();

    private CompileTimeEvaluator() {
    }

    /**
     * Evaluates {@code node} at compile-time and returns the result.
     * 
     * @param node
     *            constant expression to evaluate
     * @param options
     *            compiler options map, see {@link Compiler#options}
     * @return {@link Boolean#TRUE} iff the expression evaluates to
     *         <code>true</code>, {@link Boolean#FALSE} iff the expression
     *         evaluates to <code>false</code>, otherwise <code>null</code>
     */
    public static Boolean evaluate(SimpleNode node, Map<String, Object> options) {
        // get the constants map from the compiler
        Object constants = options.get(Compiler.COMPILE_TIME_CONSTANTS);

        JSType value = (JSType) node.jjtAccept(INSTANCE, constants);
        Boolean ret = (value != null ? Boolean.valueOf(value.booleanValue())
            : null);

        return ret;
    }

    /*
     * ECMAScript5:
     * 11.11 Binary Logical Operators
     * 
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTAndExpressionSequence, java.lang.Object)
     */
    @Override
    public Object visit(ASTAndExpressionSequence and, Object constants) {
        assert and.size() > 0;

        JSType result = null;
        for (int i = 0, size = and.size(); i < size; ++i) {
            SimpleNode child = and.get(i);

            JSType value = (JSType) child.jjtAccept(this, constants);
            if (value == null) {
                return null;
            } else if (!value.booleanValue()) {
                return value;
            } else {
                result = value;
            }
        }

        assert result != null;
        return result;
    }

    /*
     * ECMAScript5:
     * 11.11 Binary Logical Operators
     * 
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTOrExpressionSequence, java.lang.Object)
     */
    @Override
    public Object visit(ASTOrExpressionSequence or, Object constants) {
        assert or.size() > 0;

        JSType result = null;
        for (int i = 0, size = or.size(); i < size; ++i) {
            SimpleNode child = or.get(i);

            JSType value = (JSType) child.jjtAccept(this, constants);
            if (value == null) {
                return null;
            } else if (value.booleanValue()) {
                return value;
            } else {
                result = value;
            }
        }

        assert result != null;
        return result;
    }

    /*
     * ECMAScript5:
     * 11.12 Conditional Operator ( ?: )
     * 
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTConditionalExpression, java.lang.Object)
     */
    @Override
    public Object visit(ASTConditionalExpression cond, Object constants) {
        SimpleNode test = cond.get(0);
        JSType value = (JSType) test.jjtAccept(this, constants);
        if (value != null) {
            SimpleNode child = cond.get(value.booleanValue() ? 1 : 2);
            value = (JSType) child.jjtAccept(this, constants);
        }
        return value;
    }

    /*
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTLiteral, java.lang.Object)
     */
    @Override
    public Object visit(ASTLiteral literal, Object constants) {
        Object value = literal.getValue();
        return fromObject(value);
    }

    /*
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTIdentifier, java.lang.Object)
     */
    @Override
    public Object visit(ASTIdentifier identifier, Object constants) {
        String name = identifier.getName();

        @SuppressWarnings("unchecked")
        Map<String, Object> _constants = (Map<String, Object>) constants;
        if (_constants != null && _constants.containsKey(name)) {
            Object value = _constants.get(name);
            return fromObject(value);
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTCallExpression, java.lang.Object)
     */
    @Override
    public Object visit(ASTCallExpression call, Object constants) {
        // implements access for objects, e.g. "$quirks.runtime"
        SimpleNode node = call.get(0);
        if (node instanceof ASTIdentifier) {
            ASTIdentifier identifier = (ASTIdentifier) node;
            String name = identifier.getName();

            @SuppressWarnings("unchecked")
            Map<String, Object> _constants = (Map<String, Object>) constants;
            if (_constants != null && _constants.containsKey(name)) {
                Object value = _constants.get(name);
                if (value instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    for (int i = 1, size = call.size(); i < size; ++i) {
                        SimpleNode suffix = call.get(i);

                        // resolve key
                        String key = null;
                        if (suffix instanceof ASTPropertyIdentifierReference) {
                            key = ((ASTIdentifier) suffix.get(0)).getName();
                        } else if (suffix instanceof ASTPropertyValueReference) {
                            JSType type = (JSType) suffix.get(0).jjtAccept(
                                this, constants);
                            if (type != null) {
                                key = type.stringValue();
                            }
                        }

                        // resolve entry
                        if (key == null) {
                            return null;
                        } else if (map.containsKey(key)) {
                            Object entry = map.get(key);
                            if (entry instanceof Map) {
                                map = (Map<?, ?>) entry;
                            } else if (i == size - 1) {
                                // only fromObject() for last item
                                return fromObject(entry);
                            } else {
                                // invalid entry?
                                return null;
                            }
                        } else {
                            return UNDEFINED;
                        }
                    }
                }
            }
        }
        return null;
    }

    /*
     * ECMAScript5:
     * 11.5.1 Applying the * Operator
     * 11.5.2 Applying the / Operator
     * 11.5.3 Applying the % Operator
     * 11.6.1 The Addition operator ( + )
     * 11.6.2 The Subtraction Operator ( - )
     * 11.7.1 The Left Shift Operator ( << )
     * 11.7.2 The Signed Right Shift Operator ( >> )
     * 11.7.3 The Unsigned Right Shift Operator ( >>> )
     * 11.8.1 The Less-than Operator ( < )
     * 11.8.2 The Greater-than Operator ( > )
     * 11.8.3 The Less-than-or-equal Operator ( <= )
     * 11.8.4 The Greater-than-or-equal Operator ( >= )
     * 11.9.1 The Equals Operator ( == )
     * 11.9.2 The Does-not-equals Operator ( != )
     * 11.9.4 The Strict Equals Operator ( === )
     * 11.9.5 The Strict Does-not-equal Operator ( !== )
     * 11.10 Binary Bitwise Operators
     * 
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTBinaryExpressionSequence, java.lang.Object)
     */
    @Override
    public Object visit(ASTBinaryExpressionSequence binary, Object constants) {
        SimpleNode left = binary.get(0);
        ASTOperator operator = (ASTOperator) binary.get(1);
        SimpleNode right = binary.get(2);

        JSType x = (JSType) left.jjtAccept(this, constants);
        if (x == null) {
            return null;
        }
        JSType y = (JSType) right.jjtAccept(this, constants);
        if (y == null) {
            return null;
        }

        switch (operator.getOperator()) {
        case ParserConstants.STAR:
            return fromNumber(x.numberValue() * y.numberValue());
        case ParserConstants.SLASH:
            return fromNumber(x.numberValue() / y.numberValue());
        case ParserConstants.REM:
            return fromNumber(x.numberValue() % y.numberValue());
        case ParserConstants.PLUS: {
            if (x instanceof JSString || y instanceof JSString) {
                return fromString(x.stringValue() + y.stringValue());
            } else {
                return fromNumber(x.numberValue() + y.numberValue());
            }
        }
        case ParserConstants.MINUS:
            return fromNumber(x.numberValue() - y.numberValue());
        case ParserConstants.LSHIFT:
            return fromNumber(int32Value(x) << ((int) (uint32Value(y) & 0x1F)));
        case ParserConstants.RSIGNEDSHIFT:
            return fromNumber(int32Value(x) >> ((int) (uint32Value(y) & 0x1F)));
        case ParserConstants.RUNSIGNEDSHIFT:
            return fromNumber(int32Value(x) >>> ((int) (uint32Value(y) & 0x1F)));
        case ParserConstants.LT: {
            Boolean r = jsRelational(x, y);
            return fromBoolean((r == null) ? false : r);
        }
        case ParserConstants.GT: {
            Boolean r = jsRelational(y, x);
            return fromBoolean((r == null) ? false : r);
        }
        case ParserConstants.LE: {
            Boolean r = jsRelational(y, x);
            return fromBoolean((r == null || r) ? false : true);
        }
        case ParserConstants.GE: {
            Boolean r = jsRelational(x, y);
            return fromBoolean((r == null || r) ? false : true);
        }
        case ParserConstants.EQ:
            return fromBoolean(jsEquals(x, y));
        case ParserConstants.NE:
            return fromBoolean(!jsEquals(x, y));
        case ParserConstants.SEQ:
            return fromBoolean(jsStrictEquals(x, y));
        case ParserConstants.SNE:
            return fromBoolean(!jsStrictEquals(x, y));
        case ParserConstants.BIT_AND:
            return fromNumber(int32Value(x) & int32Value(y));
        case ParserConstants.XOR:
            return fromNumber(int32Value(x) ^ int32Value(y));
        case ParserConstants.BIT_OR:
            return fromNumber(int32Value(x) | int32Value(y));
        default:
            return null;
        }
    }

    /*
     * ECMAScript5:
     * 11.4.2 The void Operator
     * 11.4.3 The typeof Operator
     * 11.4.6 Unary + Operator
     * 11.4.7 Unary - Operator
     * 11.4.8 Bitwise NOT Operator ( ~ )
     * 11.4.9 Logical NOT Operator ( ! )
     * 
     * (non-Javadoc)
     * @see org.openlaszlo.sc.EmptyParserVisitor#visit(org.openlaszlo.sc.parser.ASTUnaryExpression, java.lang.Object)
     */
    @Override
    public Object visit(ASTUnaryExpression unary, Object constants) {
        ASTOperator operator = (ASTOperator) unary.get(0);
        SimpleNode child = unary.get(1);

        JSType x = (JSType) child.jjtAccept(this, constants);
        if (x == null) {
            return null;
        }

        switch (operator.getOperator()) {
        case ParserConstants.VOID:
            return UNDEFINED;
        case ParserConstants.TYPEOF:
            return fromString(jsTypeof(x));
        case ParserConstants.PLUS:
            return fromNumber(x.numberValue());
        case ParserConstants.MINUS:
            return fromNumber(-x.numberValue());
        case ParserConstants.TILDE:
            return fromNumber(~int32Value(x));
        case ParserConstants.BANG:
            return fromBoolean(!x.booleanValue());
        default:
            return null;
        }
    }

    // constants
    private static final JSUndefined UNDEFINED = new JSUndefined();
    private static final JSNull NULL = new JSNull();
    private static final JSBoolean TRUE = new JSBoolean(true);
    private static final JSBoolean FALSE = new JSBoolean(false);
    private static final JSNumber ONE = new JSNumber(1);
    private static final JSNumber ZERO = new JSNumber(0);

    // package visible
    static final Object JS_UNDEFINED = new Object();

    private static JSType fromObject(Object o) {
        if (o == JS_UNDEFINED) {
            return UNDEFINED;
        } else if (o instanceof Boolean) {
            return fromBoolean((Boolean) o);
        } else if (o instanceof Number) {
            return fromNumber((Number) o);
        } else if (o instanceof String) {
            return fromString((String) o);
        } else if (o == null) {
            return NULL;
        } else {
            return null;
        }
    }

    private static final JSBoolean fromBoolean(boolean b) {
        return b ? TRUE : FALSE;
    }

    private static final JSBoolean fromBoolean(Boolean b) {
        return b.booleanValue() ? TRUE : FALSE;
    }

    private static final JSNumber fromNumber(double d) {
        return new JSNumber(d);
    }

    private static final JSNumber fromNumber(Number n) {
        return new JSNumber(n.doubleValue());
    }

    private static final JSString fromString(String s) {
        return new JSString(s);
    }

    /*
     * ECMAScript5:
     * 11.4.3 The typeof Operator 
     */
    private static String jsTypeof(JSType x) {
        if (x instanceof JSUndefined) {
            return "undefined";
        } else if (x instanceof JSNull) {
            return "object";
        } else if (x instanceof JSBoolean) {
            return "boolean";
        } else if (x instanceof JSNumber) {
            return "number";
        } else if (x instanceof JSString) {
            return "string";
        } else {
            throw new IllegalArgumentException(x.getClass()
                + " is not a supported type!");
        }
    }

    /*
     * ECMAScript5:
     * 11.8.5 The Abstract Relational Comparison Algorithm
     */
    private static Boolean jsRelational(JSType x, JSType y) {
        if (!(x instanceof JSString && y instanceof JSString)) {
            double nx = x.numberValue(), ny = y.numberValue();
            if (Double.isNaN(nx) || Double.isNaN(ny)) {
                return null;
            } else if (x == y) {
                return false;
            } else if (nx == Double.POSITIVE_INFINITY) {
                return false;
            } else if (ny == Double.POSITIVE_INFINITY) {
                return true;
            } else if (ny == Double.NEGATIVE_INFINITY) {
                return false;
            } else if (nx == Double.NEGATIVE_INFINITY) {
                return true;
            } else {
                return (nx < ny);
            }
        } else {
            return (x.stringValue().compareTo(y.stringValue())) < 0;
        }
    }

    /*
     * ECMAScript5:
     * 11.9.3 The Abstract Equality Comparison Algorithm
     */
    private static boolean jsEquals(JSType x, JSType y) {
        if (x.getClass() == y.getClass()) {
            return jsStrictEquals(x, y);
        } else {
            if (x instanceof JSNull && y instanceof JSUndefined) {
                return true;
            } else if (x instanceof JSUndefined && y instanceof JSNull) {
                return true;
            } else if ((x instanceof JSNumber && y instanceof JSString)
                || x instanceof JSString && y instanceof JSNumber) {
                return x.numberValue() == y.numberValue();
            } else if (x instanceof JSBoolean) {
                return jsEquals(x.booleanValue() ? ONE : ZERO, y);
            } else if (y instanceof JSBoolean) {
                return jsEquals(x, y.booleanValue() ? ONE : ZERO);
            } else {
                return false;
            }
        }
    }

    /*
     * ECMAScript5: 
     * 11.9.6 The Strict Equality Comparison Algorithm
     */
    private static boolean jsStrictEquals(JSType x, JSType y) {
        if (x.getClass() == y.getClass()) {
            if (x instanceof JSUndefined || x instanceof JSNull) {
                return true;
            } else if (x instanceof JSNumber) {
                if (Double.isNaN(x.numberValue())
                    || Double.isNaN(y.numberValue())) {
                    return false;
                } else {
                    return x.numberValue() == y.numberValue();
                }
            } else if (x instanceof JSString) {
                return x.stringValue().equals(y.stringValue());
            } else if (x instanceof JSBoolean) {
                return x.booleanValue() == y.booleanValue();
            } else {
                throw new IllegalArgumentException(x.getClass()
                    + " is not a supported type!");
            }
        } else {
            return false;
        }
    }

    /**
     * Simple class to hold a Javascript type
     */
    private static abstract class JSType {
        /* ECMAScript5: 9.2 ToBoolean */
        abstract boolean booleanValue();

        /* ECMAScript5: 9.3 ToNumber */
        abstract double numberValue();

        /* ECMAScript5: 9.8 ToString */
        abstract String stringValue();
    }

    /* ECMAScript5: 9.4 ToInteger */
    @SuppressWarnings("unused")
    private static final double integerValue(JSType type) {
        double d = type.numberValue();
        return Double.isNaN(d) ? 0 : (Double.isInfinite(d) || d == 0) ? d
            : (d > 0) ? Math.floor(d) : Math.ceil(d);
    }

    /* ECMAScript5: 9.5 ToInt32 */
    private static final int int32Value(JSType type) {
        double d = type.numberValue();
        if (Double.isNaN(d) || d == 0 || Double.isInfinite(d)) {
            return 0;
        } else if (d == (int) d) {
            // fast-path
            return (int) d;
        } else {
            d = (d > 0) ? Math.floor(d) : Math.ceil(d);
            d = Math.IEEEremainder(d, 4294967296D);
            return (int) ((long) d);
        }
    }

    /* ECMAScript5: 9.6 ToUint32 */
    private static final long uint32Value(JSType type) {
        double d = type.numberValue();
        if (Double.isNaN(d) || d == 0 || Double.isInfinite(d)) {
            return 0;
        } else if (d == (long) d) {
            // fast-path
            return (long) d & 0xFFFFFFFFL;
        } else {
            d = (d > 0) ? Math.floor(d) : Math.ceil(d);
            d = Math.IEEEremainder(d, 4294967296D);
            return (long) d & 0xFFFFFFFFL;
        }
    }

    /* ECMAScript5: 9.7 ToUint16 */
    @SuppressWarnings("unused")
    private static final int uint16Value(JSType type) {
        throw new UnsupportedOperationException();
    }

    /**
     * Class for the Javascript {@code undefined} type
     */
    private static final class JSUndefined extends JSType {
        @Override
        boolean booleanValue() {
            return false;
        }

        @Override
        double numberValue() {
            return Double.NaN;
        }

        @Override
        String stringValue() {
            return "undefined";
        }
    }

    /**
     * Class for the Javascript {@code null} type
     */
    private static final class JSNull extends JSType {
        @Override
        boolean booleanValue() {
            return false;
        }

        @Override
        double numberValue() {
            return 0;
        }

        @Override
        String stringValue() {
            return "null";
        }
    }

    /**
     * Class for the Javascript {@code Boolean} type
     */
    private static final class JSBoolean extends JSType {
        private final boolean b;

        JSBoolean(boolean b) {
            this.b = b;
        }

        @Override
        boolean booleanValue() {
            return b;
        }

        @Override
        double numberValue() {
            return b ? 1 : 0;
        }

        @Override
        String stringValue() {
            return Boolean.toString(b);
        }
    }

    /**
     * Class for the Javascript {@code Number} type
     */
    private static final class JSNumber extends JSType {
        private final double d;

        JSNumber(double d) {
            this.d = d;
        }

        @Override
        boolean booleanValue() {
            double d = this.d;
            return !(d == 0 || Double.isNaN(d));
        }

        @Override
        double numberValue() {
            return d;
        }

        @Override
        String stringValue() {
            String s = Double.toString(d);
            // strip off .0 for integral values
            return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
        }
    }

    /**
     * Class for the Javascript {@code String} type
     */
    private static final class JSString extends JSType {
        private static final Pattern numliteral;
        static {
            // ECMAScript5: 9.3.1 ToNumber Applied to the String Type
            final String Digits = "(?:\\p{Digit}+)";
            final String HexDigits = "(?:\\p{XDigit}+)";
            final String WhiteSpaceOpt = "(?:[\\u0009\\u0020\\u00A0\\u000C\\u000B\\u000D\\u000A\\u2028\\u2029\\p{Zs}]*)";
            final String ExponentPart = "(?:[eE][+-]?" + Digits + ")";

            final String NumericLiteral = WhiteSpaceOpt
                + "((?:[+-]?Infinity|(?:" + Digits + "\\.?" + Digits + "?"
                + ExponentPart + "?)|" + "(?:\\." + Digits + "" + ExponentPart
                + "?))|(?:0[xX]" + HexDigits + "))?" + WhiteSpaceOpt;

            numliteral = Pattern.compile(NumericLiteral);
        }

        private final String s;

        JSString(String s) {
            this.s = s;
        }

        @Override
        boolean booleanValue() {
            return (s.length() != 0);
        }

        @Override
        double numberValue() {
            Matcher m = numliteral.matcher(s);
            if (m.matches()) {
                String num = m.group(1);
                if (num == null) {
                    // return 0 for the empty string
                    return 0;
                } else {
                    try {
                        return Double.parseDouble(num);
                    } catch (NumberFormatException e) {
                        throw new IllegalStateException(s);
                    }
                }
            } else {
                return Double.NaN;
            }
        }

        @Override
        String stringValue() {
            return s;
        }
    }
}
/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/