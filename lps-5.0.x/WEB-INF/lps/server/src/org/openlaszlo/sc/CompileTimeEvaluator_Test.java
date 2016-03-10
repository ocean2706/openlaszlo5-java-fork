/**
 * 
 */
package org.openlaszlo.sc;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.openlaszlo.sc.parser.ASTIdentifier;
import org.openlaszlo.sc.parser.ASTLiteral;
import org.openlaszlo.sc.parser.ASTObjectLiteral;
import org.openlaszlo.sc.parser.Parser;
import org.openlaszlo.sc.parser.SimpleNode;

/**
 * JUnit TestCase for {@link CompileTimeEvaluator}.
 * 
 * @author André Bargull
 * 
 */
public class CompileTimeEvaluator_Test extends TestCase {
    private static final boolean ES3 = false;

    private static final Map<String, Object> options = new HashMap<String, Object>() {
        private static final long serialVersionUID = 1L;

        {
            Map<String, Object> constants = new HashMap<String, Object>();
            constants.put("$swf8", Boolean.TRUE);
            constants.put("$dhtml", Boolean.FALSE);
            constants.put("$runtime", "swf8");
            constants.put("$swfversion", 8);
            constants
                .put(
                    "$quirks",
                    map("{runtime: 'swf8', swf8: true, dhtml: false, more: {info: 'info'}, 1: 42}"));

            constants.put("Infinity", Double.POSITIVE_INFINITY);
            constants.put("NaN", Double.NaN);
            constants.put("undefined", CompileTimeEvaluator.JS_UNDEFINED);

            put(Compiler.COMPILE_TIME_CONSTANTS, constants);
        }

        private Map<String, Object> map(String o) {
            // parse as expression
            ASTObjectLiteral object = (ASTObjectLiteral) parse("(" + o + ")");
            return map(object);
        }

        private Map<String, Object> map(ASTObjectLiteral object) {
            Map<String, Object> map = new HashMap<String, Object>();
            for (int i = 0, size = object.size(); i < size; i += 2) {
                SimpleNode key = object.get(i);
                SimpleNode value = object.get(i + 1);
                String _key;
                Object _value;
                if (key instanceof ASTLiteral) {
                    _key = ((ASTLiteral) key).getValue().toString();
                } else if (key instanceof ASTIdentifier) {
                    _key = ((ASTIdentifier) key).getName();
                } else {
                    System.err.printf("unsupported key: %s\n", key);
                    continue;
                }
                if (value instanceof ASTLiteral) {
                    _value = ((ASTLiteral) value).getValue();
                } else if (value instanceof ASTObjectLiteral) {
                    _value = map((ASTObjectLiteral) value);
                } else {
                    System.err.printf("unsupported value: %s\n", value);
                    continue;
                }
                map.put(_key, _value);
            }
            return map;
        }
    };

    private static SimpleNode parse(String s) {
        Reader reader = new StringReader(s);
        Parser parser = new Parser(reader);
        SimpleNode program = parser.Program();
        /*
         * ASTProgram [:1#1]
         *  ASTStatement [:1#1]
         *   ASTXXX [:1#1]
         */
        SimpleNode node = program.get(0).get(0);
        return node;
    }

    private static Boolean eval(String s) {
        return CompileTimeEvaluator.evaluate(parse(s), options);
    }

    public void testIdentifier() {
        assertEquals(Boolean.TRUE, eval("$swf8"));
        assertEquals(Boolean.FALSE, eval("$dhtml"));
        assertNull(eval("$swf10"));
    }

    public void testLiteral() {
        // boolean literals
        assertEquals(Boolean.TRUE, eval("true"));
        assertEquals(Boolean.FALSE, eval("false"));

        // number literals
        assertEquals(Boolean.TRUE, eval("1"));
        assertEquals(Boolean.FALSE, eval("0"));
        assertEquals(Boolean.TRUE, eval("1.0"));
        assertEquals(Boolean.FALSE, eval("0.0"));
        assertEquals(Boolean.TRUE, eval("42"));
        assertEquals(Boolean.TRUE, eval("3.14159"));

        // string literals
        assertEquals(Boolean.FALSE, eval("''"));
        assertEquals(Boolean.FALSE, eval("\"\""));
        assertEquals(Boolean.TRUE, eval("'foo'"));

        // null literal
        assertEquals(Boolean.FALSE, eval("null"));

        if (ES3) {
            // not literals in ES3!
            assertNull(eval("NaN"));
            assertNull(eval("undefined"));
            assertNull(eval("Infinity"));
        } else {
            assertEquals(Boolean.FALSE, eval("NaN"));
            assertEquals(Boolean.FALSE, eval("undefined"));
            assertEquals(Boolean.TRUE, eval("Infinity"));
        }

        assertNull(eval("[]"));
        assertNull(eval("({})"));
    }

    public void testAndExpressionSequence() {
        // boolean literals
        assertEquals(Boolean.FALSE, eval("false && false"));
        assertEquals(Boolean.FALSE, eval("false && true"));
        assertEquals(Boolean.FALSE, eval("true && false"));
        assertEquals(Boolean.TRUE, eval("true && true"));

        // number literals
        assertEquals(Boolean.FALSE, eval("0 && 0"));
        assertEquals(Boolean.FALSE, eval("0 && 1"));
        assertEquals(Boolean.FALSE, eval("1 && 0"));
        assertEquals(Boolean.TRUE, eval("1 && 1"));

        // string literals
        assertEquals(Boolean.FALSE, eval("'' && ''"));
        assertEquals(Boolean.FALSE, eval("'' && 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' && ''"));
        assertEquals(Boolean.TRUE, eval("'foo' && 'foo'"));

        // null literal
        assertEquals(Boolean.FALSE, eval("null && null"));

        // mixed literals
        assertEquals(Boolean.FALSE, eval("false && 0"));
        assertEquals(Boolean.FALSE, eval("false && 1"));
        assertEquals(Boolean.FALSE, eval("true && 0"));
        assertEquals(Boolean.TRUE, eval("true && 1"));
        assertEquals(Boolean.FALSE, eval("false && ''"));
        assertEquals(Boolean.FALSE, eval("false && 'foo'"));
        assertEquals(Boolean.FALSE, eval("true && ''"));
        assertEquals(Boolean.TRUE, eval("true && 'foo'"));
        assertEquals(Boolean.FALSE, eval("false && null"));
        assertEquals(Boolean.FALSE, eval("true && null"));

        // sequences > 2
        assertEquals(Boolean.FALSE, eval("true && true && false"));
        assertEquals(Boolean.TRUE, eval("true && true && true"));

        // short-circuiting
        assertEquals(Boolean.FALSE, eval("$dhtml && $swf10"));
        assertNull(eval("$swf8 && $swf10"));
        assertNull(eval("$swf10 && $swf8"));
        assertNull(eval("$swf10 && $dhtml"));
    }

    public void testOrExpressionSequence() {
        // boolean literals
        assertEquals(Boolean.FALSE, eval("false || false"));
        assertEquals(Boolean.TRUE, eval("false || true"));
        assertEquals(Boolean.TRUE, eval("true || false"));
        assertEquals(Boolean.TRUE, eval("true || true"));

        // number literals
        assertEquals(Boolean.FALSE, eval("0 || 0"));
        assertEquals(Boolean.TRUE, eval("0 || 1"));
        assertEquals(Boolean.TRUE, eval("1 || 0"));
        assertEquals(Boolean.TRUE, eval("1 || 1"));

        // string literals
        assertEquals(Boolean.FALSE, eval("'' || ''"));
        assertEquals(Boolean.TRUE, eval("'' || 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' || ''"));
        assertEquals(Boolean.TRUE, eval("'foo' || 'foo'"));

        // null literal
        assertEquals(Boolean.FALSE, eval("null && null"));

        // mixed literals
        assertEquals(Boolean.FALSE, eval("false || 0"));
        assertEquals(Boolean.TRUE, eval("false || 1"));
        assertEquals(Boolean.TRUE, eval("true || 0"));
        assertEquals(Boolean.TRUE, eval("true || 1"));
        assertEquals(Boolean.FALSE, eval("false || ''"));
        assertEquals(Boolean.TRUE, eval("false || 'foo'"));
        assertEquals(Boolean.TRUE, eval("true || ''"));
        assertEquals(Boolean.TRUE, eval("true || 'foo'"));
        assertEquals(Boolean.FALSE, eval("false || null"));
        assertEquals(Boolean.TRUE, eval("true || null"));

        // sequences > 2
        assertEquals(Boolean.TRUE, eval("false || false || true"));
        assertEquals(Boolean.FALSE, eval("false || false || false"));

        // short-circuiting
        assertEquals(Boolean.TRUE, eval("$swf8 || $swf10"));
        assertNull(eval("$dhtml || $swf10"));
        assertNull(eval("$swf10 || $swf8"));
        assertNull(eval("$swf10 || $dhtml"));
    }

    public void testUnaryExpression() {
        // only the logical-not operator (!) is supported

        // boolean literals
        assertEquals(Boolean.TRUE, eval("! false"));
        assertEquals(Boolean.FALSE, eval("! true"));

        // number literals
        assertEquals(Boolean.TRUE, eval("! 0"));
        assertEquals(Boolean.FALSE, eval("! 1"));

        // string literals
        assertEquals(Boolean.TRUE, eval("! ''"));
        assertEquals(Boolean.FALSE, eval("! 'foo'"));

        // null literal
        assertEquals(Boolean.TRUE, eval("! null"));

        // identifier
        assertEquals(Boolean.FALSE, eval("! $swf8"));
        assertEquals(Boolean.TRUE, eval("! $dhtml"));
        assertNull(eval("! $swf10"));

        // unary +/-/~/typeof/void are supported
        assertEquals(Boolean.TRUE, eval("+1"));
        assertEquals(Boolean.TRUE, eval("-1"));
        assertEquals(Boolean.TRUE, eval("~1"));
        assertEquals(Boolean.TRUE, eval("typeof 1"));
        assertEquals(Boolean.FALSE, eval("void 0"));

        // no support for other unary expressions
        assertNull(eval("++1"));
        assertNull(eval("--1"));
        assertNull(eval("delete 0"));
    }

    public void testBinaryExpressionSequence() {
        // only (not-)equals and (not)-strictequals (==, !=, ===, !==) are
        // supported

        // boolean literals
        assertEquals(Boolean.TRUE, eval("false == false"));
        assertEquals(Boolean.FALSE, eval("false == true"));
        assertEquals(Boolean.FALSE, eval("true == false"));
        assertEquals(Boolean.TRUE, eval("true == true"));
        assertEquals(Boolean.FALSE, eval("false != false"));
        assertEquals(Boolean.TRUE, eval("false != true"));
        assertEquals(Boolean.TRUE, eval("true != false"));
        assertEquals(Boolean.FALSE, eval("true != true"));
        assertEquals(Boolean.TRUE, eval("false === false"));
        assertEquals(Boolean.FALSE, eval("false === true"));
        assertEquals(Boolean.FALSE, eval("true === false"));
        assertEquals(Boolean.TRUE, eval("true === true"));
        assertEquals(Boolean.FALSE, eval("false !== false"));
        assertEquals(Boolean.TRUE, eval("false !== true"));
        assertEquals(Boolean.TRUE, eval("true !== false"));
        assertEquals(Boolean.FALSE, eval("true !== true"));

        // number literals
        assertEquals(Boolean.TRUE, eval("0 == 0"));
        assertEquals(Boolean.FALSE, eval("0 == 1"));
        assertEquals(Boolean.FALSE, eval("1 == 0"));
        assertEquals(Boolean.TRUE, eval("1 == 1"));
        assertEquals(Boolean.FALSE, eval("0 != 0"));
        assertEquals(Boolean.TRUE, eval("0 != 1"));
        assertEquals(Boolean.TRUE, eval("1 != 0"));
        assertEquals(Boolean.FALSE, eval("1 != 1"));
        assertEquals(Boolean.TRUE, eval("0 === 0"));
        assertEquals(Boolean.FALSE, eval("0 === 1"));
        assertEquals(Boolean.FALSE, eval("1 === 0"));
        assertEquals(Boolean.TRUE, eval("1 === 1"));
        assertEquals(Boolean.FALSE, eval("0 !== 0"));
        assertEquals(Boolean.TRUE, eval("0 !== 1"));
        assertEquals(Boolean.TRUE, eval("1 !== 0"));
        assertEquals(Boolean.FALSE, eval("1 !== 1"));

        // floating point number literals
        assertEquals(Boolean.TRUE, eval("1.0 == 1.0"));
        assertEquals(Boolean.TRUE, eval("1.0 === 1.0"));
        assertEquals(Boolean.TRUE, eval("0.0 == 0.0"));
        assertEquals(Boolean.TRUE, eval("0.0 === 0.0"));
        assertEquals(Boolean.TRUE, eval("1.0 != 0.0"));
        assertEquals(Boolean.TRUE, eval("1.0 !== 0.0"));
        assertEquals(Boolean.TRUE, eval("0.0 != 1.0"));
        assertEquals(Boolean.TRUE, eval("0.0 !== 1.0"));

        // mixed floating point and integer literals
        assertEquals(Boolean.TRUE, eval("1 == 1.0"));
        assertEquals(Boolean.TRUE, eval("1 === 1.0"));
        assertEquals(Boolean.TRUE, eval("0 == 0.0"));
        assertEquals(Boolean.TRUE, eval("0 === 0.0"));
        assertEquals(Boolean.TRUE, eval("1 != 0.0"));
        assertEquals(Boolean.TRUE, eval("1 !== 0.0"));
        assertEquals(Boolean.TRUE, eval("0 != 1.0"));
        assertEquals(Boolean.TRUE, eval("0 !== 1.0"));

        // string literals
        assertEquals(Boolean.TRUE, eval("'' == ''"));
        assertEquals(Boolean.FALSE, eval("'' == 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' == ''"));
        assertEquals(Boolean.TRUE, eval("'foo' == 'foo'"));
        assertEquals(Boolean.FALSE, eval("'' != ''"));
        assertEquals(Boolean.TRUE, eval("'' != 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' != ''"));
        assertEquals(Boolean.FALSE, eval("'foo' != 'foo'"));
        assertEquals(Boolean.TRUE, eval("'' === ''"));
        assertEquals(Boolean.FALSE, eval("'' === 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' === ''"));
        assertEquals(Boolean.TRUE, eval("'foo' === 'foo'"));
        assertEquals(Boolean.FALSE, eval("'' !== ''"));
        assertEquals(Boolean.TRUE, eval("'' !== 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' !== ''"));
        assertEquals(Boolean.FALSE, eval("'foo' !== 'foo'"));

        // null literal
        assertEquals(Boolean.TRUE, eval("null == null"));
        assertEquals(Boolean.TRUE, eval("null === null"));
        assertEquals(Boolean.FALSE, eval("null != null"));
        assertEquals(Boolean.FALSE, eval("null !== null"));

        // coercion tests
        // -> null
        assertEquals(Boolean.FALSE, eval("null == false"));
        assertEquals(Boolean.FALSE, eval("null == 0"));
        assertEquals(Boolean.FALSE, eval("null == ''"));
        assertEquals(Boolean.FALSE, eval("null == 'null'"));
        // -> string to number
        assertEquals(Boolean.TRUE, eval("1 == '1'"));
        assertEquals(Boolean.FALSE, eval("1 === '1'"));
        assertEquals(Boolean.TRUE, eval("0 == '0'"));
        assertEquals(Boolean.FALSE, eval("0 === '0'"));
        // strip whitespace
        assertEquals(Boolean.TRUE, eval("42 == ' 42 '"));
        assertEquals(Boolean.TRUE, eval("0 == ''"));
        assertEquals(Boolean.TRUE, eval("0 == '  '"));
        assertEquals(Boolean.TRUE, eval("0 == '\\t\\r\\n\\f'"));
        // infinity
        assertEquals(Boolean.TRUE, eval("Infinity == '1e1000'"));
        // -> boolean to number
        assertEquals(Boolean.TRUE, eval("true == 1"));
        assertEquals(Boolean.FALSE, eval("true === 1"));
        assertEquals(Boolean.TRUE, eval("false == 0"));
        assertEquals(Boolean.FALSE, eval("false === 0"));
        // -> boolean to number and string to number
        assertEquals(Boolean.TRUE, eval("true == '1'"));
        assertEquals(Boolean.FALSE, eval("true === '1'"));
        assertEquals(Boolean.TRUE, eval("false == '0'"));
        assertEquals(Boolean.FALSE, eval("false === '0'"));

        // string comparison with identifier
        assertEquals(Boolean.TRUE, eval("$runtime == $runtime"));
        assertEquals(Boolean.TRUE, eval("$runtime === $runtime"));
        assertEquals(Boolean.TRUE, eval("$runtime == 'swf8'"));
        assertEquals(Boolean.FALSE, eval("$runtime == 'dhtml'"));
        assertEquals(Boolean.FALSE, eval("$runtime == 'swf10'"));
        assertEquals(Boolean.FALSE, eval("$runtime != 'swf8'"));
        assertEquals(Boolean.TRUE, eval("$runtime != 'dhtml'"));
        assertEquals(Boolean.TRUE, eval("$runtime != 'swf10'"));
        assertEquals(Boolean.TRUE, eval("$runtime === 'swf8'"));
        assertEquals(Boolean.FALSE, eval("$runtime === 'dhtml'"));
        assertEquals(Boolean.FALSE, eval("$runtime === 'swf10'"));
        assertEquals(Boolean.FALSE, eval("$runtime !== 'swf8'"));
        assertEquals(Boolean.TRUE, eval("$runtime !== 'dhtml'"));
        assertEquals(Boolean.TRUE, eval("$runtime !== 'swf10'"));

        assertEquals(Boolean.TRUE, eval("$swf8 == true"));
        assertEquals(Boolean.FALSE, eval("$swf8 == false"));
        assertEquals(Boolean.FALSE, eval("$dhtml == true"));
        assertEquals(Boolean.TRUE, eval("$dhtml == false"));
        assertNull(eval("$swf10 == true"));
        assertNull(eval("$swf10 == false"));
    }

    public void testRelationalExpression() {
        // boolean literals
        assertEquals(Boolean.FALSE, eval("false < false"));
        assertEquals(Boolean.TRUE, eval("false < true"));
        assertEquals(Boolean.FALSE, eval("true < false"));
        assertEquals(Boolean.FALSE, eval("true < true"));
        assertEquals(Boolean.TRUE, eval("false <= false"));
        assertEquals(Boolean.TRUE, eval("false <= true"));
        assertEquals(Boolean.FALSE, eval("true <= false"));
        assertEquals(Boolean.TRUE, eval("true <= true"));
        assertEquals(Boolean.FALSE, eval("false > false"));
        assertEquals(Boolean.FALSE, eval("false > true"));
        assertEquals(Boolean.TRUE, eval("true > false"));
        assertEquals(Boolean.FALSE, eval("true > true"));
        assertEquals(Boolean.TRUE, eval("false >= false"));
        assertEquals(Boolean.FALSE, eval("false >= true"));
        assertEquals(Boolean.TRUE, eval("true >= false"));
        assertEquals(Boolean.TRUE, eval("true >= true"));

        // number literals
        assertEquals(Boolean.FALSE, eval("0 < 0"));
        assertEquals(Boolean.TRUE, eval("0 < 1"));
        assertEquals(Boolean.FALSE, eval("1 < 0"));
        assertEquals(Boolean.FALSE, eval("1 < 1"));
        assertEquals(Boolean.TRUE, eval("0 <= 0"));
        assertEquals(Boolean.TRUE, eval("0 <= 1"));
        assertEquals(Boolean.FALSE, eval("1 <= 0"));
        assertEquals(Boolean.TRUE, eval("1 <= 1"));
        assertEquals(Boolean.FALSE, eval("0 > 0"));
        assertEquals(Boolean.FALSE, eval("0 > 1"));
        assertEquals(Boolean.TRUE, eval("1 > 0"));
        assertEquals(Boolean.FALSE, eval("1 > 1"));
        assertEquals(Boolean.TRUE, eval("0 >= 0"));
        assertEquals(Boolean.FALSE, eval("0 >= 1"));
        assertEquals(Boolean.TRUE, eval("1 >= 0"));
        assertEquals(Boolean.TRUE, eval("1 >= 1"));

        // floating point number literals
        assertEquals(Boolean.FALSE, eval("0.0 < 0.0"));
        assertEquals(Boolean.TRUE, eval("0.0 < 1.0"));
        assertEquals(Boolean.FALSE, eval("1.0 < 0.0"));
        assertEquals(Boolean.FALSE, eval("1.0 < 1.0"));
        assertEquals(Boolean.TRUE, eval("0.0 <= 0.0"));
        assertEquals(Boolean.TRUE, eval("0.0 <= 1.0"));
        assertEquals(Boolean.FALSE, eval("1.0 <= 0.0"));
        assertEquals(Boolean.TRUE, eval("1.0 <= 1.0"));
        assertEquals(Boolean.FALSE, eval("0.0 > 0.0"));
        assertEquals(Boolean.FALSE, eval("0.0 > 1.0"));
        assertEquals(Boolean.TRUE, eval("1.0 > 0.0"));
        assertEquals(Boolean.FALSE, eval("1.0 > 1.0"));
        assertEquals(Boolean.TRUE, eval("0.0 >= 0.0"));
        assertEquals(Boolean.FALSE, eval("0.0 >= 1.0"));
        assertEquals(Boolean.TRUE, eval("1.0 >= 0.0"));
        assertEquals(Boolean.TRUE, eval("1.0 >= 1.0"));

        // mixed floating point and integer literals
        assertEquals(Boolean.TRUE, eval("1 <= 1.0"));
        assertEquals(Boolean.TRUE, eval("1 >= 1.0"));
        assertEquals(Boolean.TRUE, eval("0 <= 0.0"));
        assertEquals(Boolean.TRUE, eval("0 >= 0.0"));
        assertEquals(Boolean.TRUE, eval("1 > 0.0"));
        assertEquals(Boolean.TRUE, eval("1 >= 0.0"));
        assertEquals(Boolean.TRUE, eval("0 < 1.0"));
        assertEquals(Boolean.TRUE, eval("0 <= 1.0"));

        // string literals
        assertEquals(Boolean.FALSE, eval("'' < ''"));
        assertEquals(Boolean.TRUE, eval("'' < 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' < ''"));
        assertEquals(Boolean.FALSE, eval("'foo' < 'foo'"));
        assertEquals(Boolean.TRUE, eval("'' <= ''"));
        assertEquals(Boolean.TRUE, eval("'' <= 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' <= ''"));
        assertEquals(Boolean.TRUE, eval("'foo' <= 'foo'"));
        assertEquals(Boolean.FALSE, eval("'' > ''"));
        assertEquals(Boolean.FALSE, eval("'' > 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' > ''"));
        assertEquals(Boolean.FALSE, eval("'foo' > 'foo'"));
        assertEquals(Boolean.TRUE, eval("'' >= ''"));
        assertEquals(Boolean.FALSE, eval("'' >= 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' >= ''"));
        assertEquals(Boolean.TRUE, eval("'foo' >= 'foo'"));

        // string literals 2
        assertEquals(Boolean.FALSE, eval("'foo' < 'bar'"));
        assertEquals(Boolean.TRUE, eval("'bar' < 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' < 'foobar'"));
        assertEquals(Boolean.FALSE, eval("'foobar' < 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' <= 'bar'"));
        assertEquals(Boolean.TRUE, eval("'bar' <= 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' <= 'foobar'"));
        assertEquals(Boolean.FALSE, eval("'foobar' <= 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' > 'bar'"));
        assertEquals(Boolean.FALSE, eval("'bar' > 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' > 'foobar'"));
        assertEquals(Boolean.TRUE, eval("'foobar' > 'foo'"));
        assertEquals(Boolean.TRUE, eval("'foo' >= 'bar'"));
        assertEquals(Boolean.FALSE, eval("'bar' >= 'foo'"));
        assertEquals(Boolean.FALSE, eval("'foo' >= 'foobar'"));
        assertEquals(Boolean.TRUE, eval("'foobar' >= 'foo'"));

        // null literal
        assertEquals(Boolean.FALSE, eval("null < null"));
        assertEquals(Boolean.TRUE, eval("null <= null"));
        assertEquals(Boolean.FALSE, eval("null > null"));
        assertEquals(Boolean.TRUE, eval("null >= null"));

        // coercion tests
        // -> null
        assertEquals(Boolean.FALSE, eval("null < false"));
        assertEquals(Boolean.FALSE, eval("null < 0"));
        assertEquals(Boolean.FALSE, eval("null < ''"));
        assertEquals(Boolean.FALSE, eval("null < 'null'"));
        assertEquals(Boolean.TRUE, eval("null <= false"));
        assertEquals(Boolean.TRUE, eval("null <= 0"));
        assertEquals(Boolean.TRUE, eval("null <= ''"));
        assertEquals(Boolean.FALSE, eval("null <= 'null'"));
        assertEquals(Boolean.FALSE, eval("null > false"));
        assertEquals(Boolean.FALSE, eval("null > 0"));
        assertEquals(Boolean.FALSE, eval("null > ''"));
        assertEquals(Boolean.FALSE, eval("null > 'null'"));
        assertEquals(Boolean.TRUE, eval("null >= false"));
        assertEquals(Boolean.TRUE, eval("null >= 0"));
        assertEquals(Boolean.TRUE, eval("null >= ''"));
        assertEquals(Boolean.FALSE, eval("null >= 'null'"));
        // -> string to number
        assertEquals(Boolean.FALSE, eval("0 < '0'"));
        assertEquals(Boolean.TRUE, eval("0 < '1'"));
        assertEquals(Boolean.FALSE, eval("1 < '0'"));
        assertEquals(Boolean.FALSE, eval("1 < '1'"));
        assertEquals(Boolean.TRUE, eval("0 <= '0'"));
        assertEquals(Boolean.TRUE, eval("0 <= '1'"));
        assertEquals(Boolean.FALSE, eval("1 <= '0'"));
        assertEquals(Boolean.TRUE, eval("1 <= '1'"));
        assertEquals(Boolean.FALSE, eval("0 > '0'"));
        assertEquals(Boolean.FALSE, eval("0 > '1'"));
        assertEquals(Boolean.TRUE, eval("1 > '0'"));
        assertEquals(Boolean.FALSE, eval("1 > '1'"));
        assertEquals(Boolean.TRUE, eval("0 >= '0'"));
        assertEquals(Boolean.FALSE, eval("0 >= '1'"));
        assertEquals(Boolean.TRUE, eval("1 >= '0'"));
        assertEquals(Boolean.TRUE, eval("1 >= '1'"));
        // -> boolean to number
        assertEquals(Boolean.FALSE, eval("false < 0"));
        assertEquals(Boolean.TRUE, eval("false < 1"));
        assertEquals(Boolean.FALSE, eval("true < 0"));
        assertEquals(Boolean.FALSE, eval("true < 1"));
        assertEquals(Boolean.TRUE, eval("false <= 0"));
        assertEquals(Boolean.TRUE, eval("false <= 1"));
        assertEquals(Boolean.FALSE, eval("true <= 0"));
        assertEquals(Boolean.TRUE, eval("true <= 1"));
        assertEquals(Boolean.FALSE, eval("false > 0"));
        assertEquals(Boolean.FALSE, eval("false > 1"));
        assertEquals(Boolean.TRUE, eval("true > 0"));
        assertEquals(Boolean.FALSE, eval("true > 1"));
        assertEquals(Boolean.TRUE, eval("false >= 0"));
        assertEquals(Boolean.FALSE, eval("false >= 1"));
        assertEquals(Boolean.TRUE, eval("true >= 0"));
        assertEquals(Boolean.TRUE, eval("true >= 1"));

        // -> boolean to number and string to number
        assertEquals(Boolean.FALSE, eval("false < '0'"));
        assertEquals(Boolean.TRUE, eval("false < '1'"));
        assertEquals(Boolean.FALSE, eval("true < '0'"));
        assertEquals(Boolean.FALSE, eval("true < '1'"));
        assertEquals(Boolean.TRUE, eval("false <= '0'"));
        assertEquals(Boolean.TRUE, eval("false <= '1'"));
        assertEquals(Boolean.FALSE, eval("true <= '0'"));
        assertEquals(Boolean.TRUE, eval("true <= '1'"));
        assertEquals(Boolean.FALSE, eval("false > '0'"));
        assertEquals(Boolean.FALSE, eval("false > '1'"));
        assertEquals(Boolean.TRUE, eval("true > '0'"));
        assertEquals(Boolean.FALSE, eval("true > '1'"));
        assertEquals(Boolean.TRUE, eval("false >= '0'"));
        assertEquals(Boolean.FALSE, eval("false >= '1'"));
        assertEquals(Boolean.TRUE, eval("true >= '0'"));
        assertEquals(Boolean.TRUE, eval("true >= '1'"));

        // infinity
        assertEquals(Boolean.FALSE, eval("Infinity < 1"));
        assertEquals(Boolean.FALSE, eval("Infinity <= 1"));
        assertEquals(Boolean.TRUE, eval("Infinity > 1"));
        assertEquals(Boolean.TRUE, eval("Infinity >= 1"));
        assertEquals(Boolean.TRUE, eval("-Infinity < 1"));
        assertEquals(Boolean.TRUE, eval("-Infinity <= 1"));
        assertEquals(Boolean.FALSE, eval("-Infinity > 1"));
        assertEquals(Boolean.FALSE, eval("-Infinity >= 1"));

        assertEquals(Boolean.FALSE, eval("Infinity < Infinity"));
        assertEquals(Boolean.FALSE, eval("Infinity < -Infinity"));
        assertEquals(Boolean.TRUE, eval("-Infinity < Infinity"));
        assertEquals(Boolean.FALSE, eval("-Infinity < -Infinity"));
        assertEquals(Boolean.TRUE, eval("Infinity <= Infinity"));
        assertEquals(Boolean.FALSE, eval("Infinity <= -Infinity"));
        assertEquals(Boolean.TRUE, eval("-Infinity <= Infinity"));
        assertEquals(Boolean.TRUE, eval("-Infinity <= -Infinity"));
        assertEquals(Boolean.FALSE, eval("Infinity > Infinity"));
        assertEquals(Boolean.TRUE, eval("Infinity > -Infinity"));
        assertEquals(Boolean.FALSE, eval("-Infinity > Infinity"));
        assertEquals(Boolean.FALSE, eval("-Infinity > -Infinity"));
        assertEquals(Boolean.TRUE, eval("Infinity >= Infinity"));
        assertEquals(Boolean.TRUE, eval("Infinity >= -Infinity"));
        assertEquals(Boolean.FALSE, eval("-Infinity >= Infinity"));
        assertEquals(Boolean.TRUE, eval("-Infinity >= -Infinity"));

        // NaN
        assertEquals(Boolean.FALSE, eval("NaN < 1"));
        assertEquals(Boolean.FALSE, eval("NaN <= 1"));
        assertEquals(Boolean.FALSE, eval("NaN > 1"));
        assertEquals(Boolean.FALSE, eval("NaN >= 1"));
        assertEquals(Boolean.FALSE, eval("NaN < NaN"));
        assertEquals(Boolean.FALSE, eval("NaN <= NaN"));
        assertEquals(Boolean.FALSE, eval("NaN > NaN"));
        assertEquals(Boolean.FALSE, eval("NaN >= NaN"));

        // identifier
        assertEquals(Boolean.TRUE, eval("$swfversion == 8"));
        assertEquals(Boolean.FALSE, eval("$swfversion < 8"));
        assertEquals(Boolean.TRUE, eval("$swfversion <= 8"));
        assertEquals(Boolean.FALSE, eval("$swfversion > 8"));
        assertEquals(Boolean.TRUE, eval("$swfversion >= 8"));
    }

    public void testUndefinedVoid() {
        assertEquals(Boolean.TRUE, eval("undefined == null"));
        assertEquals(Boolean.TRUE, eval("undefined !== null"));
        assertEquals(Boolean.TRUE, eval("undefined == undefined"));
        assertEquals(Boolean.TRUE, eval("undefined === undefined"));
        assertEquals(Boolean.TRUE, eval("void 0 == null"));
        assertEquals(Boolean.TRUE, eval("void 0 !== null"));
        assertEquals(Boolean.TRUE, eval("void 0 == undefined"));
        assertEquals(Boolean.TRUE, eval("void 0 === undefined"));
    }

    public void testConditionalExpression() {
        // boolean literals
        assertEquals(Boolean.FALSE, eval("false ? false : false"));
        assertEquals(Boolean.TRUE, eval("false ? false : true"));
        assertEquals(Boolean.FALSE, eval("false ? true : false"));
        assertEquals(Boolean.TRUE, eval("false ? true : true"));
        assertEquals(Boolean.FALSE, eval("true ? false : false"));
        assertEquals(Boolean.FALSE, eval("true ? false : true"));
        assertEquals(Boolean.TRUE, eval("true ? true : false"));
        assertEquals(Boolean.TRUE, eval("true ? true : true"));

        // number literals
        assertEquals(Boolean.FALSE, eval("0 ? false : false"));
        assertEquals(Boolean.TRUE, eval("0 ? false : true"));
        assertEquals(Boolean.FALSE, eval("0 ? true : false"));
        assertEquals(Boolean.TRUE, eval("0 ? true : true"));
        assertEquals(Boolean.FALSE, eval("1 ? false : false"));
        assertEquals(Boolean.FALSE, eval("1 ? false : true"));
        assertEquals(Boolean.TRUE, eval("1 ? true : false"));
        assertEquals(Boolean.TRUE, eval("1 ? true : true"));

        // string literals
        assertEquals(Boolean.FALSE, eval("'' ? false : false"));
        assertEquals(Boolean.TRUE, eval("'' ? false : true"));
        assertEquals(Boolean.FALSE, eval("'' ? true : false"));
        assertEquals(Boolean.TRUE, eval("'' ? true : true"));
        assertEquals(Boolean.FALSE, eval("'foo' ? false : false"));
        assertEquals(Boolean.FALSE, eval("'foo' ? false : true"));
        assertEquals(Boolean.TRUE, eval("'foo' ? true : false"));
        assertEquals(Boolean.TRUE, eval("'foo' ? true : true"));

        // null literal
        assertEquals(Boolean.FALSE, eval("null ? false : false"));
        assertEquals(Boolean.TRUE, eval("null ? false : true"));
        assertEquals(Boolean.FALSE, eval("null ? true : false"));
        assertEquals(Boolean.TRUE, eval("null ? true : true"));

        // identifier
        assertEquals(Boolean.FALSE, eval("$swf8 ? false : false"));
        assertEquals(Boolean.FALSE, eval("$swf8 ? false : true"));
        assertEquals(Boolean.TRUE, eval("$swf8 ? true : false"));
        assertEquals(Boolean.TRUE, eval("$swf8 ? true : true"));
        assertEquals(Boolean.FALSE, eval("$dhtml ? false : false"));
        assertEquals(Boolean.TRUE, eval("$dhtml ? false : true"));
        assertEquals(Boolean.FALSE, eval("$dhtml ? true : false"));
        assertEquals(Boolean.TRUE, eval("$dhtml ? true : true"));
        assertNull(eval("$swf10 ? false : false"));
        assertNull(eval("$swf10 ? false : true"));
        assertNull(eval("$swf10 ? true : false"));
        assertNull(eval("$swf10 ? true : true"));
    }

    public void testMixed() {
        assertEquals(Boolean.FALSE, eval("1 === true"));
        assertEquals(Boolean.TRUE, eval("!(1 === true)"));

        assertEquals(Boolean.TRUE, eval("('foo' || 'bar') == 'foo'"));
        assertEquals(Boolean.TRUE, eval("('foo' && 'bar') == 'bar'"));

        assertEquals(Boolean.FALSE, eval("(null || true) === 1"));
        assertEquals(Boolean.TRUE, eval("(null || 1) === 1"));
        assertEquals(Boolean.TRUE, eval("(null && 1) === null"));

        assertEquals(Boolean.FALSE, eval("(null && 1) == (null || 1)"));
        assertEquals(Boolean.FALSE, eval("(null && 1) == !(null || 1)"));
        assertEquals(Boolean.TRUE, eval("(null ? 0 : 1) == (1 || 2)"));
    }

    public void testStringConcat() {
        assertEquals(Boolean.TRUE, eval("2 === 1 + 1"));
        assertEquals(Boolean.TRUE, eval("'11' === '1' + 1"));
        assertEquals(Boolean.TRUE, eval("'11' === 1 + '1'"));
        assertEquals(Boolean.TRUE, eval("'11' === '1' + '1'"));

        // minus
        assertEquals(Boolean.TRUE, eval("0 === 1 - 1"));
        assertEquals(Boolean.TRUE, eval("0 === '1' - 1"));
        assertEquals(Boolean.TRUE, eval("0 === 1 - '1'"));
        assertEquals(Boolean.TRUE, eval("0 === '1' - '1'"));
    }

    public void testExplicitCast() {
        // to int32
        assertEquals(Boolean.TRUE, eval("1 != 1.1"));
        assertEquals(Boolean.TRUE, eval("1 == (1.1|0)"));
        assertEquals(Boolean.TRUE, eval("1 === (1.1|0)"));

        // equal but not strict equal
        assertEquals(Boolean.TRUE, eval("1 == '1'"));
        assertEquals(Boolean.TRUE, eval("1 !== '1'"));

        // force number comparison
        assertEquals(Boolean.TRUE, eval("1 === +'1'"));
        assertEquals(Boolean.TRUE, eval("1 === +'1.0'"));
        assertEquals(Boolean.TRUE, eval("1.1 === +'1.1'"));
        assertEquals(Boolean.TRUE, eval("1.1 === +'1.10'"));
        assertEquals(Boolean.TRUE, eval("10 === +'010'"));
        assertEquals(Boolean.TRUE, eval("10 === +'0010'"));

        // force string comparison
        assertEquals(Boolean.TRUE, eval("''+1 === '1'"));
        assertEquals(Boolean.TRUE, eval("''+1.0 === '1'"));
        assertEquals(Boolean.TRUE, eval("''+1.1 === '1.1'"));
        assertEquals(Boolean.TRUE, eval("''+1.10 === '1.1'"));
    }

    public void testShift() {
        assertEquals(Boolean.TRUE, eval("1 === 1 << 0"));
        assertEquals(Boolean.TRUE, eval("2 === 1 << 1"));
        assertEquals(Boolean.TRUE, eval("1024 === 1 << 10"));
        assertEquals(Boolean.TRUE, eval("-2147483648 === 1 << 31"));
        assertEquals(Boolean.TRUE, eval("1 === 1 << 32"));

        assertEquals(Boolean.TRUE, eval("1 === 1 >> 0"));
        assertEquals(Boolean.TRUE, eval("1 === 2 >> 1"));
        assertEquals(Boolean.TRUE, eval("1 === 1024 >> 10"));

        assertEquals(Boolean.TRUE, eval("1 === 1 >>> 0"));
        assertEquals(Boolean.TRUE, eval("1 === 2 >>> 1"));
        assertEquals(Boolean.TRUE, eval("1 === 1024 >>> 10"));

        assertEquals(Boolean.TRUE, eval("-1 === (-1 >> 1)"));
        assertEquals(Boolean.TRUE, eval("2147483647 === (-1 >>> 1)"));

        assertEquals(Boolean.TRUE, eval(" 2147483647 === (~(1 << 31))"));
        assertEquals(Boolean.TRUE, eval(" 2147483647 === ((~(1 << 31))|0)"));
        assertEquals(Boolean.TRUE, eval(" 2147483648 === (~(1 << 31)+1)"));
        assertEquals(Boolean.TRUE, eval("-2147483648 === ((~(1 << 31)+1)|0)"));
    }

    public void testMultiplicativeExpression() {
        assertEquals(Boolean.TRUE, eval("Infinity === (1/0)"));
        assertEquals(Boolean.TRUE, eval("-Infinity === (-1/0)"));
        assertEquals(Boolean.TRUE, eval("NaN !== (0/0)"));
        assertEquals(Boolean.TRUE, eval("(0/0) !== (0/0)"));
        assertEquals(Boolean.TRUE, eval("'NaN' === ''+(0/0)"));

        assertEquals(Boolean.TRUE,
            eval("(undefined * undefined) !== (undefined * undefined)"));
        assertEquals(Boolean.TRUE,
            eval("(undefined / undefined) !== (undefined / undefined)"));

        assertEquals(Boolean.TRUE, eval("42 === 7*6"));
        assertEquals(Boolean.TRUE, eval("6 === 42/7"));
        assertEquals(Boolean.TRUE, eval("0 === 42%7"));
        assertEquals(Boolean.TRUE, eval("6 === 41%7"));
        assertEquals(Boolean.TRUE, eval("1 === 43%7"));

        assertEquals(Boolean.TRUE, eval("1 === 5%2"));
        assertEquals(Boolean.TRUE, eval("1 === 5%-2"));
        assertEquals(Boolean.TRUE, eval("-1 === -5%2"));
        assertEquals(Boolean.TRUE, eval("-1 === -5%-2"));

        assertEquals(Boolean.TRUE, eval("0.5 === 4.5 % 2.0"));

        assertEquals(Boolean.TRUE, eval("0.2 === (0.1 + 0.1)"));
        assertEquals(Boolean.TRUE, eval("0.30000000000000004 === (0.1 + 0.2)"));
    }

    public void testTypeOf() {
        assertEquals(Boolean.TRUE, eval("'boolean' === typeof true"));
        assertEquals(Boolean.TRUE, eval("'boolean' === typeof false"));

        assertEquals(Boolean.TRUE, eval("'number' === typeof 0"));
        assertEquals(Boolean.TRUE, eval("'number' === typeof 1"));
        assertEquals(Boolean.TRUE, eval("'number' === typeof 1.123"));
        assertEquals(Boolean.TRUE, eval("'number' === typeof NaN"));
        assertEquals(Boolean.TRUE, eval("'number' === typeof Infinity"));
        assertEquals(Boolean.TRUE, eval("'number' === typeof -Infinity"));

        assertEquals(Boolean.TRUE, eval("'object' === typeof null"));

        assertEquals(Boolean.TRUE, eval("'undefined' === typeof undefined"));

        assertEquals(Boolean.TRUE, eval("'string' === typeof ''"));
        assertEquals(Boolean.TRUE, eval("'string' === typeof '1'"));
        assertEquals(Boolean.TRUE, eval("'string' === typeof 'hello'"));

        assertEquals(Boolean.TRUE, eval("'string' === typeof $runtime"));
        assertEquals(Boolean.TRUE, eval("'boolean' === typeof $swf8"));
        assertEquals(Boolean.TRUE, eval("'number' === typeof $swfversion"));
    }

    public void testJSObject() {
        assertEquals(Boolean.TRUE, eval("true === $quirks.swf8"));
        assertEquals(Boolean.TRUE, eval("false === $quirks.dhtml"));
        assertEquals(Boolean.TRUE, eval("'swf8' === $quirks.runtime"));
        assertEquals(Boolean.TRUE, eval("'swf8' === $quirks['runtime']"));
        assertEquals(Boolean.TRUE, eval("'swf8' === $quirks['run' + 'time']"));
        assertEquals(Boolean.TRUE, eval("'info' === $quirks.more.info"));

        assertEquals(Boolean.TRUE,
            eval("'swf8player' === $quirks.runtime + 'player'"));
        assertEquals(Boolean.TRUE, eval("42 === $quirks['1']"));
        assertEquals(Boolean.TRUE, eval("84 === $quirks['1'] * 2"));

        assertEquals(Boolean.TRUE, eval("undefined === $quirks.invalid"));
        assertEquals(Boolean.TRUE, eval("undefined === $quirks.more.invalid"));

        assertNull(eval("$quirks.runtime.invalid"));
        assertNull(eval("$quirks.swf8.invalid"));
        assertNull(eval("$quirks.more"));
        assertNull(eval("$quirks"));
    }
}
/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/