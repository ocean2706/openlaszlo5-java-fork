/* *****************************************************************************
* StyleSheetCompiler.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.openlaszlo.css.CSSHandler;
import org.openlaszlo.css.Rule;
import org.openlaszlo.css.StyleProperty;
import org.openlaszlo.sc.ScriptCompiler;
import org.w3c.css.sac.*;

/** Compiler for <code>stylesheet</code> elements.
 *
 * @author  Benjamin Shine
 */
class StyleSheetCompiler extends LibraryCompiler {
    /** Logger */
    private static Logger mLogger = Logger.getLogger(StyleSheetCompiler.class);

    private static final String SRC_ATTR_NAME = "src";
    private static final String CHARSET_ATTR_NAME = "charset";

    StyleSheetCompiler(CompilationEnvironment env) {
        super(env);
    }

    /** Returns true iff this class applies to this element.
     * @param element an element
     * @return see doc
     */
    static boolean isElement(Element element) {
        return element.getName().intern() == "stylesheet";
    }

    @Override
    public void compile(Element element) {
        try {
            if (mLogger.isInfoEnabled()) {
            mLogger.info("StyleSheetCompiler.compile called!");
            }

            if (!element.getChildren().isEmpty()) {
                throw new CompilationError("<stylesheet> elements can't have children",
                                           element);
            }

            String stylesheetText = element.getText();
            String src = element.getAttributeValue(SRC_ATTR_NAME);
            String encoding = element.getAttributeValue(CHARSET_ATTR_NAME);
            if (encoding != null) {
                if (mLogger.isDebugEnabled()) {
                mLogger.info("@charset=" + encoding + " found on stylesheet tag");
                }
            } else {
                if (mLogger.isDebugEnabled()) {
                mLogger.info("no attribute @charset found on stylesheet tag, using default value " + encoding);
                }
           }

            if (src != null) {
                if (mLogger.isInfoEnabled()) {
                mLogger.info("reading in stylesheet from src=\"" + src + "\"");
                }
                // Find the css file
                // Using the FileResolver accomplishes two nice things:
                // 1, it searches the standard directory include paths
                // including the application directory for the css file.
                // 2, it adds the css file to the dependencies for the
                // current application. This makes the application be
                // recompiled if the css file changes.
                // This fixes LPP-2733 [bshine 10.20.06]

                String base =  mEnv.getApplicationFile().getParent();

                // [bshine 12.29.06] For LPP-2974, we also have to
                // check for the css file relative to the file which is including it.
                // First try to find the css file as a sibling of this source file
                String sourceDir = new File(Parser.getSourcePathname(element)).getParent();
                File resolvedFile = mEnv.resolve(src, sourceDir);

                // If our first try at finding the css file doesn't find it as a sibling,
                // try to resolve relative to the application source file.
                if (! resolvedFile.exists() ) {
                    resolvedFile = mEnv.resolve(src, base);
                    if (resolvedFile.exists()) {
                        if (mLogger.isInfoEnabled()) {
                        mLogger.info("Resolved css file to a file that exists!");
                        }
                    } else {
                        mLogger.error("Could not resolve css file to a file that exists.");
                        throw new CompilationError("Could not find css file " + src);
                    }
                }

                // Actually parse and compile the stylesheet! W00t!
                CSSHandler fileHandler = CSSHandler.parse( resolvedFile, encoding );
                this.compile(fileHandler, element);


            } else if (stylesheetText != null && (!"".equals(stylesheetText))) {
                if (mLogger.isInfoEnabled()) {
                mLogger.info("inline stylesheet");
                }
                CSSHandler inlineHandler = CSSHandler.parse(stylesheetText);
                this.compile(inlineHandler, element);
                //
            } else {
                // TODO: i18n errors
                throw new CompilationError("<stylesheet> element must have either src attribute or inline text. This has neither.",
                    element);
            }

        } catch (CompilationError e) {
            // If there was an error compiling a stylesheet, we report
            // it as a compilation error, and fail the compile.
            // Fixes LPP-2734 [bshine 10.20.06]
            mLogger.error("Error compiling StyleSheet element: " + element);
            throw e;
        } catch (IOException e) {
            // This exception indicates there was a problem reading the
            // CSS from the file
            mLogger.error("IO error compiling StyleSheet: " + element);
            throw new CompilationError("IO error, can't find source file for <stylesheet> element.",
                 element);
        } catch (CSSParseException e) {
            // CSSParseExceptions provide a line number and URI,
            // as well as a helpful message
            // Fixes LPP-2734 [bshine 10.20.06]
            String message = "Error parsing css file at line " + e.getLineNumber()
                    + ", " + e.getMessage();

            mLogger.error(message);
            throw new CompilationError(message);
        } catch (CSSException e) {
            // CSSExceptions don't provide a line number, just a message
            // Fixes LPP-2734 [bshine 10.20.06]
            mLogger.error("Error compiling css: " + element);
            throw new CompilationError("Error compiling css, no line number available: "
                    + e.getMessage());
        } catch (Exception e) {
            // This catch clause will catch disastrous errors; normal expected
            // css-related errors are handled with the more specific catch clauses
            // above.

            /**
             * NOTE: [2008-10-14 ptw] If you are trying to debug CSS
             * style sheet errors, you propbably want to disable this
             * catch clause, because it will hide the real error from
             * you.
             */

            mLogger.error("Exception compiling css: " + element + ", " + e.getMessage());
            throw new CompilationError("Error compiling css. " + e.getMessage());
        }

    }

    void compile(CSSHandler handler, Element element) throws CompilationError {
        if (mLogger.isDebugEnabled()) {
        mLogger.debug("compiling CSSHandler using new unique names");
        }
        List<RuleModel> ruleList = new ArrayList<RuleModel>();
        for (int i = 0, l = handler.mRuleList.size(); i < l; i++) {
            Rule rule = handler.mRuleList.get(i);
            ruleList.add(new RuleModel(rule, element, i));
        }
        Collections.sort(ruleList);
        String script = "";
        for (RuleModel rule : ruleList) {
            script += rule.toJavascript(mEnv);
        }
        if (mLogger.isDebugEnabled()) {
        mLogger.debug("whole stylesheet as css " + script +"\n\n");
        }
        mEnv.compileScript(CompilerUtils.sourceLocationDirective(element, true) +
                           // NOTE [2007-06-02 bshine] This semicolon is needed
                           // to work around bug LPP-4083, javascript compiler
                           // doesn't emit a semicolon somewhere
                           ";" +
                           // NOTE: [2007-02-11 ptw] It is crucial
                           // that this be terminated with a `;` so
                           // that it is a statement, not an
                           // expression.
                           " (function() { var $lzc$style = LzCSSStyle, $lzc$rule = LzCSSStyleRule;\n" + script + "})();", element );
    }

    static class RuleModel implements Comparable<RuleModel> {
        Rule rule;
        Element element;
        int number;
        Object selector;
        int specificity = 0;


    String buildSelectorJavascript(CompilationEnvironment env) {
        try {
            StringBuilder result = new StringBuilder();
            ScriptCompiler.writeObject(selector, result);
            return result.toString();
        } catch (IOException e) {
            throw new CompilationError(element, e);
        }
    }

    Object buildSelector(Selector sel) {
        switch (sel.getSelectorType()) {
            case Selector.SAC_ELEMENT_NODE_SELECTOR:
                //  This selector matches only tag type
                ElementSelector es = (ElementSelector)sel;
                return buildElementSelector(es.getLocalName());
            case Selector.SAC_CONDITIONAL_SELECTOR:
                // This selector matches all the interesting things:
                // #myId
                // [someattr="someval"]
                // simple[role="private"]
                ConditionalSelector cs = (ConditionalSelector)sel;
                // Take care of the simple selector part of this
                return buildConditionalSelector(cs.getCondition(), cs.getSimpleSelector());
            case Selector.SAC_DESCENDANT_SELECTOR:
                DescendantSelector ds = (DescendantSelector)sel;
                return buildDescendantSelector(ds);
            default:
                throw new CompilationError("Unknown selector " + sel.getSelectorType());
        }
    }

    Map<String, Object> buildElementSelector(String localName) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("s", 1);
        if (localName != null) {
            result.put("t", ScriptCompiler.quote(localName));
        }
        return result;
    }

    Map<String, Object> buildConditionalSelector(Condition cond, SimpleSelector simpleSelector) {
        int condType = cond.getConditionType();
        int specificity = 0;
        Map<String, Object> result = new HashMap<String, Object>();
        switch (condType) {
            case Condition.SAC_ID_CONDITION: /* #id */
                AttributeCondition idCond = (AttributeCondition) cond;
                // should be the id specified
                result.put("i", ScriptCompiler.quote(idCond.getValue()));
                result.put("s", 100);
                return result;

             case Condition.SAC_ATTRIBUTE_CONDITION: // [attr] or [attr="val"] or elem[attr="val"]
             case Condition.SAC_CLASS_CONDITION: // .foo
             case Condition.SAC_ONE_OF_ATTRIBUTE_CONDITION: // [attr~="foo"]
             case Condition.SAC_BEGIN_HYPHEN_ATTRIBUTE_CONDITION: // [attr|="foo"]
               specificity = 10;
               AttributeCondition attrCond = (AttributeCondition)cond;
               if (condType == Condition.SAC_CLASS_CONDITION) {
                   result.put("a", ScriptCompiler.quote("styleclass"));
               } else {
                   result.put("a", ScriptCompiler.quote(attrCond.getLocalName()));
               }
               String value = attrCond.getValue();
               if (value != null) {
                   result.put("v", ScriptCompiler.quote(value));
               }
               if ((condType == Condition.SAC_CLASS_CONDITION) ||
                   (condType == Condition.SAC_ONE_OF_ATTRIBUTE_CONDITION)) {
                   result.put("m", ScriptCompiler.quote("~="));
               } else if (condType == Condition.SAC_BEGIN_HYPHEN_ATTRIBUTE_CONDITION) {
                   result.put("m", ScriptCompiler.quote("|="));
               }
               // The simple selector is the element part of the selector, ie,
               // foo in foo[bar="baz"]. If there is no element part of the selector, ie
               // [bar="lum"] then batik gives us a non-null SimpleSelector with a
               // localName of the null string. We don't write out the simple selector if
               // it's not specified.
               if (simpleSelector != null) {
                 if (simpleSelector.getSelectorType() == Selector.SAC_ELEMENT_NODE_SELECTOR) {
                   ElementSelector es = (ElementSelector)simpleSelector;
                   String simpleSelectorString = es.getLocalName();
                   // Discard the simple selector if it isn't specified
                   if (simpleSelectorString != null) {
                     specificity += 1;
                     result.put("t", ScriptCompiler.quote(simpleSelectorString));
                   }
                 } else {
                   throw new CompilationError("Can't handle CSS selector " + simpleSelector);
                 }
               }
               result.put("s", specificity);
               return result;

          case Condition.SAC_AND_CONDITION: // tag[attr].foo.bar, etc.
              Map<String, Object> first = buildConditionalSelector(((CombinatorCondition)cond).getFirstCondition(), simpleSelector);
              Map<String, Object> second = buildConditionalSelector(((CombinatorCondition)cond).getSecondCondition(), null);
              specificity = ((Integer)first.get("s")) + ((Integer)second.get("s"));
              first.put("s", specificity);
              // Find the end of the conditional and tack on the
              // new one
              Map<String, Object> l = first;
              while (l.containsKey("&")) { l = (Map) l.get("&"); }
              l.put("&", second);
              return first;
        }
        throw new CompilationError("Unhandled conditional selector " + condType);
    }

    /**
     * Build a string holding the javascript to create the selector at runtime, where
       the selector is a descendant selector, ie
       E F
       would be
       descendantrule.selector =  [
           "E",
           "F" ];
       The selector is specified as an array of selectors, ancestor first.
      */
    List<Object> buildDescendantSelector(DescendantSelector ds) {
        // We need the simple selector and the ancestor selector
        SimpleSelector ss = ds.getSimpleSelector();
        Selector ancestorsel = ds.getAncestorSelector();

        // TODO: Compute the summary specificity here
        Object ancestorselobj = buildSelector(ancestorsel);
        Map<String, Object> ssmap = (Map)buildSelector(ss);
        if (ancestorselobj instanceof List) {
            List<Object> a = (List)ancestorselobj;
            a.add(ssmap);
            return a;
        }
        List<Object> result = new ArrayList<Object>();
        result.add(ancestorselobj);
        result.add(ssmap);
        return result;
    }


  Pattern resourcePattern = Pattern.compile("^\\s*resource\\s*\\(\\s*['\"]\\s*(.*)\\s*['\"]\\s*\\)\\s*$");
    /**
      * Build a string holding the javascript to create the rule's properties attribute.
      * This should just be a standard javascript object composed of attributes and values,
      * wrapped in curly quotes. Escape the quotes for attributes' values.
      * for example "{ width: 500, occupation: \"pet groomer and holistic veterinarian\",
                       miscdata: \"spends most days indoors\"}""
      */
  String buildPropertiesJavascript(CompilationEnvironment env) {
        /*
        String props = "{ width: 500, occupation: \"pet groomer and holistic veterinarian\"," +
                        " miscdata: \"spends most days indoors\"} ";
                        */

      StringBuilder result = new StringBuilder();
      try {
        Map<String, StyleProperty> properties = rule.getStyleMap();
        // Special handling for `resource` function: The argument is
        // compiled as a resource `src` (pathname) and given a unique
        // name, which replaces the function
        for (Map.Entry<String, StyleProperty> entry : properties.entrySet()) {
          StyleProperty property = entry.getValue();
          Matcher m = resourcePattern.matcher(property.value);
          if (m.matches()) {
            String path = m.group(1);
            String base = new File(Parser.getSourcePathname(element)).getParent();
            File file = env.resolve(path, base);
            // N.B.: Resources are always imported into the main
            // program for the Flash target, hence the use of
            // getResourceGenerator below
            try {
              String value = env.getResourceGenerator().importResource(file);
              // Resources are passed by name
              property.value = ScriptCompiler.quote(value);
            } catch (ObjectWriter.ImportResourceError e) {
              env.warn(e, element);
            }
          }
        }
        ScriptCompiler.writeObject(properties, result);
      } catch (IOException e) {
        throw new CompilationError(element, e);
      }
      return result.toString();
    }

    RuleModel (Rule rule, Element element, int number) {
        this.rule = rule;
        this.element = element;
        this.number = number;
        this.selector = buildSelector(rule.getSelector());
        Map<String, Object> sel;
        if (selector instanceof List) {
            for (Iterator<?> i = ((List<?>)selector).iterator(); i.hasNext(); ) {
                sel = (Map)(i.next());
                specificity += (Integer)(sel.get("s"));
            }
        } else {
            sel = (Map)selector;
            specificity = (Integer)(sel.get("s"));
        }
    }

    public int compareTo(RuleModel other) throws ClassCastException {
        if (this == other) { return 0; }

        if (this.specificity == other.specificity) {
            return 0;
        } else {
            return (this.specificity < other.specificity) ? 1 : -1;
        }
    }

    String toJavascript (CompilationEnvironment env) {
        return "$lzc$style._addRule(new $lzc$rule(" +
            buildSelectorJavascript(env) + ", " +
            buildPropertiesJavascript(env) + "," +
            ScriptCompiler.quote(Parser.getSourceMessagePathname(element)) + ", " +
            number + "));\n";
    }

    }
}






