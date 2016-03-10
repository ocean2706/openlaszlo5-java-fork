/* -*- mode: Java; c-basic-offset: 2; -*- */
/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
/**
 * Javascript Generation
 *
 * @author steele@osteele.com
 * @author ptw@openlaszlo.org
 * @description: JavaScript -> JavaScript translator
 *
 * Transform the parse tree from ECMA ~4 to ECMA 3.  Includes
 * analyzing constraint functions and generating their dependencies.
 */

// TODO: [2006-01-25 ptw] Share this with the SWF Code generator, from
// which this was derived.

package org.openlaszlo.sc;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openlaszlo.sc.parser.*;
import org.openlaszlo.utils.FileUtils;

public class JavascriptGenerator extends CommonGenerator implements Translator {

  @Override
  protected void setRuntime(String runtime) {
    assert org.openlaszlo.compiler.Compiler.SCRIPT_RUNTIMES.contains(runtime) : "unknown runtime " + runtime;
  }

  public SimpleNode translate(SimpleNode program) {
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTProgram.class, context);
      context.setProperty(TranslationContext.VARIABLES, globals);
      return translateInternal(program, true);
    }
    finally {
      context = context.parent;
    }
  }

  /**
   * Subclasses may override this method
   * 
   * @see ScriptCompiler#isKeyword(String)
   */
  protected boolean isKeyword(String s) {
      return ScriptCompiler.isKeyword(s);
  }

  /**
   * Copies parser information from {@code src} to {@code dest}:
   * <ul>
   * <li>{@link SimpleNode#filename}</li>
   * <li>{@link SimpleNode#beginColumn}</li>
   * <li>{@link SimpleNode#beginLine}</li>
   * <li>{@link SimpleNode#comment}</li>
   * </ul>
   */
  private <T extends SimpleNode, U extends SimpleNode> U copyNodeInfo(T src, U dest) {
    dest.filename = src.filename;
    dest.beginColumn = src.beginColumn;
    dest.beginLine = src.beginLine;
    dest.comment = src.comment;
    return dest;
  }

  /**
  * Transforms {@code idref} from {@link ASTPropertyIdentifierReference} to
  * {@link ASTPropertyValueReference}:<br>
  * <code>IDRef(*, Identifier) -> ValRef(*, Literal)</code>
  */
  private ASTPropertyValueReference toPropertyValueReference(ASTPropertyIdentifierReference idref) {
    SimpleNode children[] = idref.getChildren();
    ASTIdentifier id = (ASTIdentifier) children[1];
    ASTLiteral literal = copyNodeInfo(id, new ASTLiteral(id.getName()));
    ASTPropertyValueReference valref = copyNodeInfo(idref, new ASTPropertyValueReference(0));
    valref.setChildren(new SimpleNode[]{ children[0], literal });
    return valref;
  }

  int tempNum = 0;

  String newTemp() {
    return newTemp("$lzsc$");
  }

  String newTemp(String prefix) {
    return prefix + tempNum++;
  }

  // In-line version of Profiler#event (q.v.).
  //
  // If name is set, uses that, otherwise uses
  // function pretty name.  This code must be appended to the
  // function prefix or suffix, as appropriate.
  SimpleNode meterFunctionEvent(SimpleNode node, String event, String name) {
    String getname;
    if (name != null) {
      getname = "'" + name + "'";
    } else {
      getname = "arguments.callee['" + Function.FUNCTION_NAME + "']";
    }

    // Note _root.$lzprofiler can be undedefined to disable profiling
    // at run time.

    return parseFragment(
      "var $lzsc$lzp = global['$lzprofiler'];" +
      "if ($lzsc$lzp) {" +
      // Array keys are strings
      "  var $lzsc$now = '' + ((new Date).getTime() - $lzsc$lzp.base);" +
      "  var $lzsc$name = " + getname + ";" +
      // If the clock has not ticked (or the ms->String conversion
      // makes it appear so), we log explicitly to the event buffer,
      // otherwise we use the optimization of logging calls and
      // returns to separate buffers
      "  if ($lzsc$lzp.last == $lzsc$now) {" +
      "    $lzsc$lzp.events[$lzsc$now] += ('," + event + ":' + $lzsc$name);" +
      "  } else {" +
      "    $lzsc$lzp." + event + "[$lzsc$now] = $lzsc$name;" +
      "  }" +
      "  $lzsc$lzp.last = $lzsc$now;" +
      "}");
  }

  SimpleNode translateInternal(SimpleNode program, boolean top) {
    assert program instanceof ASTProgram;
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTProgram.class, context);
      return visitProgram(program, program.getChildren(), top);
    }
    finally {
      context = context.parent;
    }
  }

  @Override
  void showStats(SimpleNode node) {
    // No implementation to collect stats for Javascript
  }

  public String preProcess(String source) {
    return source;
  }

  class JavascriptParseTreePrinter extends ParseTreePrinter {

    public JavascriptParseTreePrinter(ParseTreePrinter.Config config) {
      super(config);
    }

    // TODO: [2009-03-23 dda] Should not need to comment the #pragma as they
    // should not normally appear in emitted code.  But LPP-7824 requires it
    // for now.
    @Override
    public String visitPragmaDirective(SimpleNode node, String[] children) {
      return "// #pragma " + children[0] + "\n";
    }

    @Override
    public String visitModifiedDefinition(SimpleNode node, String[] children) {
      // In JavascriptGenerator 'static' is handled elsewhere.  This is
      // just for debugging.
      String mods = config.compress ? "" : ("/* " + ((ASTModifiedDefinition)node).toJavascriptString(false) + " */ ");
      return mods + children[0];
    }

    // No types or ellipsis in Javascript
    @Override
    public String visitIdentifier(SimpleNode node, String[] children) {
      ASTIdentifier id = (ASTIdentifier)node;
      String name = id.getRegister();
      if (id.isConstructor()) {
        name = currentClassName;
      }
      return name;
    }

    // No return types in Javascript
    @Override
    public String functionReturnType(SimpleNode node) {
      return "";
    }

    @Override
    public String visitClassDefinition(SimpleNode node, String[] children) {
      // Should never be called for plain Javascript, these are stripped out
      throw new CompilerException("ClassDefinition found in printing Javascript AST");
    }
  }

  /** Implements CodeGenerator.
   * Call the unparser to separate the program into translation units.
   */
  public List<TranslationUnit> makeTranslationUnits(SimpleNode translatedNode, boolean compress, boolean obfuscate)
  {
    ParseTreePrinter.Config config = new ParseTreePrinter.Config();
    config.compress = compress;
    config.obfuscate = obfuscate;
    config.trackLines = options.getBoolean(Compiler.TRACK_LINES);
    config.dumpLineAnnotationsFile = (String)options.get(Compiler.DUMP_LINE_ANNOTATIONS);
    return (new JavascriptParseTreePrinter(config)).makeTranslationUnits(translatedNode, sources);
  }

  public byte[] postProcess(List<TranslationUnit> tunits) {
    assert (tunits.size() == 1);
    return tunits.get(0).getContents().getBytes();
  }

  @Override
  public SimpleNode visitProgram(SimpleNode node, SimpleNode[] directives) {
    return visitProgram(node, directives, false);
  }

  // We want to have the complete list of globals classes and vars available.
  // We cannot use the 'globals' - that tracks ones seen so far to check multiply defined.
  public Set<String> forwardGlobals = new HashSet<String>(globalProperties);

  // Build the forward global list via a shallow walk through the AST
  // to see top level definitions.
  public void buildForwardGlobals(SimpleNode node) {
    SimpleNode[] children = node.getChildren();
    if (node instanceof ASTClassDefinition) {
      ASTIdentifier id = (ASTIdentifier)children[1];
      forwardGlobals.add(id.getName());
    }
    else if (node instanceof ASTVariableDeclaration) {
      ASTIdentifier id = (ASTIdentifier)children[0];
      forwardGlobals.add(id.getName());
    }
    // These are part of the structure seen at the top of the AST.
    // By recursing on these, and only these, we'll see all the
    // top level variable and class definitions.
    else if (node instanceof ASTProgram ||
             node instanceof ASTModifiedDefinition ||
             node instanceof ASTVariableStatement ||
             node instanceof ASTVariableDeclarationList ||
             node instanceof ASTClassIfDirective ||
             node instanceof ASTClassDirectiveBlock ||
             node instanceof ASTDirectiveBlock ||
             node instanceof ASTStatement ||
             node instanceof ASTIfDirective ||
             node instanceof ASTIncludeDirective ||
             node instanceof ASTStatementList) {
      for (int i=0; i<children.length; i++) {
        buildForwardGlobals(children[i]);
      }
    }
  }

  public SimpleNode visitProgram(SimpleNode node, SimpleNode[] directives, boolean top) {
    assert node instanceof ASTProgram || node instanceof ASTDirectiveBlock : node.getClass().getName();
    if (top &&
        // Here this means 'compiling the LFC' we only want to emit
        // the constants into the LFC
        // FIXME: There needs to be a way that the object writer
        // ensures that the constants the LZX is compiled with are the
        // same ones as are set in the LFC it is linked to
        options.getBoolean(Compiler.FLASH_COMPILER_COMPATABILITY)) {
      // emit compile-time contants to runtime
      Map<String, Object> constants = options.getProperty(Compiler.COMPILE_TIME_CONSTANTS);
      if (constants != null) {
        String code = "";
        for (Map.Entry<String, Object> entry : constants.entrySet()) {
          Object value = entry.getValue();
          String type = null;
          if (value instanceof String) {
            type = "String";
            value = "\"" + value + "\"";
          } else if (value instanceof Boolean) {
            type = "Boolean ";
            value = value.toString();
          }
          String name = entry.getKey();
          code += "var " + name + ((type != null) ? (":" + type) : "") + " = " + value + ";";
        }
        List<SimpleNode> c = new ArrayList<SimpleNode>();
        if (!options.getBoolean("noconstants")) c.add(parseFragment(code));
        c.addAll(Arrays.asList(directives));
        directives = c.toArray(directives);
        node.setChildren(directives);
      }
    }

    buildForwardGlobals(node);
    // The 'Debug' class, at least, is used commonly in code that is not
    // necessarily compiled debug, so we add these unconditionally here.
    forwardGlobals.addAll(debugGlobals);

    for (int index = 0, len = directives.length; index < len; index++) {
      SimpleNode directive = directives[index];
      SimpleNode newDirective = directive;
      SimpleNode[] children = directive.getChildren();
      newDirective = visitDirective(directive, children);
      if (! newDirective.equals(directive)) {
        directives[index] = newDirective;
      }
    }
    showStats(node);
    return node;
  }

  @Override
  public SimpleNode visitDirective (SimpleNode directive, SimpleNode[] children) {
    if (directive instanceof ASTDirectiveBlock) {
      Compiler.OptionMap savedOptions = options;
      try {
        options = options.copy();
        return visitProgram(directive, children);
      }
      finally {
        options = savedOptions;
      }
    } else if (directive instanceof ASTIfDirective) {
      return visitIfDirective(directive, children);
    } else if (directive instanceof ASTIncludeDirective) {
      // Disabled by default, since it isn't supported in the
      // product.  (It doesn't go through the compilation
      // manager for dependency tracking.)
      if (! options.getBoolean(Compiler.INCLUDES)) {
        throw new UnimplementedError("unimplemented: #include", directive);
      }
      String userfname = (String)((ASTLiteral)children[0]).getValue();
      return translateInclude(userfname);
    } else if (directive instanceof ASTProgram) {
      // This is what an include looks like in pass 2
      return visitProgram(directive, children);
    } else if (directive instanceof ASTPragmaDirective) {
      return visitPragmaDirective(directive, children);
    } else if (directive instanceof ASTPassthroughDirective) {
      return visitPassthroughDirective(directive, children);
    } else {
      return visitStatement(directive, children);
    }
  }

  // Somehow we can have IfDirective's that are not at the top level?
  // -> Yes, that's possible due to ClassIfDirectives which aren't at top level! [20101231 anba]
  @Override
  public SimpleNode visitIfDirective (SimpleNode directive, SimpleNode[] children) {
    assert (directive instanceof ASTIfDirective || directive instanceof ASTClassIfDirective) : directive.getClass();
    // NOTE: [2009-10-03 ptw] (LPP-1933) People expect the
    // branches of a compile-time conditional to establish a
    // directive block
    Boolean value = evaluateCompileTimeConditional(children[0]);
    if (value == null) {
      if (directive instanceof ASTIfDirective) {
        throw new CompilerError("undefined compile-time conditional " + Compiler.nodeString(directive.get(0)));
      } else {
        // only top-level if-directives must be resolvable at compile-time
        return visitIfStatement(directive, children);
      }
    } else if (value.booleanValue()) {
      SimpleNode clause = children[1];
      Compiler.OptionMap savedOptions = options;
      try {
        options = options.copy();
        return visitDirective(clause, clause.getChildren());
      }
      finally {
        options = savedOptions;
      }
    } else if (children.length > 2) {
      SimpleNode clause = children[2];
      Compiler.OptionMap savedOptions = options;
      try {
        options = options.copy();
        return visitDirective(clause, clause.getChildren());
      }
      finally {
        options = savedOptions;
      }
    } else {
      return new ASTEmptyExpression(0);
    }
  }

  @Override
  SimpleNode translateInclude(String userfname) {

    if (Compiler.CachedInstructions == null) {
      Compiler.CachedInstructions = new ScriptCompilerCache<String, String, Object>();
    }

    File file = includeNameToFile(userfname);
    String source = includeFileToSourceString(file, userfname);

    try {
      String optionsKey = 
        getCodeGenerationOptionsKey(Collections.singletonList(
                                      // The constant pool isn't cached, so it doesn't affect code
                                      // generation so far as the cache is concerned.
                                      Compiler.DISABLE_CONSTANT_POOL));
      // If these could be omitted from the key for files that didn't
      // reference them, then the cache could be shared between krank
      // and krank debug.  (The other builds differ either on OBFUSCATE,
      // RUNTIME, NAMEFUNCTIONS, or PROFILE, so there isn't any other
      // possible sharing.)
      String instrsKey = file.getAbsolutePath();
      // Only cache on file and pass, to keep cache size resonable,
      // but check against optionsKey
      String instrsChecksum = "" + file.lastModified() + optionsKey; // source;
      // Use previously modified parse tree if it exists
      SimpleNode instrs = (SimpleNode) Compiler.CachedInstructions.get(instrsKey, instrsChecksum);
      if (instrs == null) {
        ParseResult result = parseFile(file, userfname, source);
        instrs = result.parse;
        instrs = translateInternal(instrs, false);
        if (! result.hasIncludes) {
          if (options.getBoolean(Compiler.CACHE_COMPILES)) {
            Compiler.CachedInstructions.put(instrsKey, instrsChecksum, instrs);
          }
        }
      }
      return instrs;
    }
    catch (ParseException e) {
      System.err.println("while compiling " + file.getAbsolutePath());
      throw e;
    }
  }

  @Override
  public SimpleNode visitFunctionDeclaration(SimpleNode node, SimpleNode[] ast) {
    // Inner functions are handled by translateFunction
    if (context.findFunctionContext() != null) {
      return null;
    } else {
      assert (! options.getBoolean(Compiler.CONSTRAINT_FUNCTION));
      return translateFunction(node, true, ast);
    }
  }

  // A method declaration is simply a function in a class
  @Override
  public SimpleNode visitMethodDeclaration(SimpleNode node, SimpleNode[] ast) {
    assert context.isClassBoundary() : ("Method not in class context? " + context);
    return translateMethod(node, true, ast);
  }

  //
  // Statements
  //
  @Override
  public SimpleNode visitVariableDeclaration(SimpleNode node, SimpleNode[] children) {
    ASTIdentifier id = (ASTIdentifier)children[0];
    JavascriptReference ref = translateReference(id);
    if (ASTProgram.class.equals(context.type)) {
      // Initial value not used in this runtime
      addGlobalVar(id.getName(), null, null);
    }
    if (children.length > 1) {
      SimpleNode initValue = children[1];
      children[1] = visitExpression(initValue);
      children[0] = ref.init();
      return node;
    } else {
      children[0] = ref.declare();
      return node;
    }
  }

  @Override
  public SimpleNode visitIfStatement(SimpleNode node, SimpleNode[] children) {
    SimpleNode test = children[0];
    SimpleNode a = children[1];
    SimpleNode b = (children.length > 2) ? children[2] : null;
    // Compile-time conditional evaluations
    Boolean value = evaluateCompileTimeConditional(test);
//     if (test instanceof ASTIdentifier) {
//       System.err.println("visitIfStatement: " +  (new ParseTreePrinter()).annotatedText(test) +" == " + value);
//     }
    if (value != null) {
      if (value.booleanValue()) {
        return visitStatement(a);
      } else if (b != null) {
        return visitStatement(b);
      } else {
        return new ASTEmptyExpression(0);
      }
    } else if (b != null) {
      children[0] = visitExpression(test);
      children[1] = visitStatement(a);
      children[2] = visitStatement(b);
    } else {
      children[0] = visitExpression(test);
      children[1] = visitStatement(a);
    }
    return node;
  }

  @Override
  public SimpleNode visitWhileStatement(SimpleNode node, SimpleNode[] children) {
    SimpleNode test = children[0];
    SimpleNode body = children[1];
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTWhileStatement.class, context);
      children[0] = visitExpression(test);
      children[1] = visitStatement(body);
      return node;
    }
    finally {
      context = context.parent;
    }
  }

  @Override
  public SimpleNode visitDoWhileStatement(SimpleNode node, SimpleNode[] children) {
    SimpleNode body = children[0];
    SimpleNode test = children[1];
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTDoWhileStatement.class, context);
      children[0] = visitStatement(body);
      children[1] = visitExpression(test);
      return node;
    }
    finally {
      context = context.parent;
    }
  }

  @Override
  public SimpleNode visitForStatement(SimpleNode node, SimpleNode[] children) {
    return translateForStatement(node, children);
  }

  @Override
  public SimpleNode visitForVarStatement(SimpleNode node, SimpleNode[] children) {
    return translateForStatement(node, children);
  }

  SimpleNode translateForStatement(SimpleNode node, SimpleNode[] children) {
    SimpleNode init = children[0];
    SimpleNode test = children[1];
    SimpleNode step = children[2];
    SimpleNode body = children[3];
    // TODO: [2003-04-15 ptw] bind context slot macro
    Compiler.OptionMap savedOptions = options;
    try {
      options = options.copy();
      context = makeTranslationContext(ASTForStatement.class, context);
      options.putBoolean(Compiler.WARN_GLOBAL_ASSIGNMENTS, true);
      children[0] = visitStatement(init);
      options.putBoolean(Compiler.WARN_GLOBAL_ASSIGNMENTS, false);
      children[1] = visitExpression(test);
      children[3] = visitStatement(body);
      children[2] = visitStatement(step);
      return node;
    }
    finally {
      context = context.parent;
      options = savedOptions;
    }
  }

  @Override
  public SimpleNode visitForInStatement(SimpleNode node, SimpleNode[] children) {
    SimpleNode var = children[0];
    SimpleNode obj = children[1];
    SimpleNode body = children[2];
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTForInStatement.class, context);
      children[1] = visitExpression(obj);
      JavascriptReference ref = translateReference(var);
      children[0] = ref.set(true);
      children[2] = visitStatement(body);
      return node;
    }
    finally {
      context = context.parent;
    }
  }

  @Override
  public SimpleNode visitForVarInStatement(SimpleNode node, SimpleNode[] children) {
    assert children.length == 4;
    SimpleNode var = children[0];
    SimpleNode init = children[1];
    SimpleNode obj = children[2];
    SimpleNode body = children[3];
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTForInStatement.class, context);
      // visitStatement should translate this as a variable declaration
      children[0] = visitStatement(var);
      children[1] = visitExpression(init);
      children[2] = visitExpression(obj);
      children[3] = visitStatement(body);
      return node;
    }
    finally {
      context = context.parent;
    }
  }

  @Override
  public SimpleNode visitTryStatement(SimpleNode node, SimpleNode[] children) {
    SimpleNode block = children[0];
    int len = children.length;
    assert len == 2 || len == 3;
    children[0] = visitStatement(block);
    if (len == 2) {
      // Could be catch or finally clause
      SimpleNode catfin = children[1];
      if (catfin instanceof ASTCatchClause) {
        // Treat the catch identifier as a binding.  This is not quite
        // right, need to integrate with variable analyzer, but this is
        // the one case in ECMAScript where a variable does have block
        // extent.
        catfin.set(0, translateReference(catfin.get(0)).declare());
        catfin.set(1, visitStatement(catfin.get(1)));
      } else {
        assert catfin instanceof ASTFinallyClause;
        catfin.set(0, visitStatement(catfin.get(0)));
      }
    } else if (len == 3) {
      SimpleNode cat = children[1];
      SimpleNode fin = children[2];
      assert cat instanceof ASTCatchClause;
      // Treat the catch identifier as a binding.  This is not quite
      // right, need to integrate with variable analyzer, but this is
      // the one case in ECMAScript where a variable does have block
      // extent.
      cat.set(0, translateReference(cat.get(0)).declare());
      cat.set(1, visitStatement(cat.get(1)));
      assert fin instanceof ASTFinallyClause;
      fin.set(0, visitStatement(fin.get(0)));
    }
    return node;
  }

  @Override
  public SimpleNode visitSwitchStatement(SimpleNode node, SimpleNode[] children) {
    try {
      context = makeTranslationContext(ASTSwitchStatement.class, context);
      return super.visitSwitchStatement(node, children);
    }
    finally {
      context = context.parent;
    }
  }

  //
  // Expressions
  //
  @Override
  public SimpleNode visitExpression(SimpleNode node, boolean isReferenced) {
    if (this.debugVisit) {
      System.err.println("visitExpression: " + node.getClass());
    }

    SimpleNode newNode = super.visitExpression(node, isReferenced);

    if ((! isReferenced) && (newNode == null)) {
      newNode = new ASTEmptyExpression(0);
    }
    if (this.debugVisit) {
      if (! newNode.equals(node)) {
        System.err.println("expression: " + node + " -> " + newNode);
      }
    }
    return newNode;
  }

  @Override
  public SimpleNode visitIdentifier(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    // Side-effect free expressions can be suppressed if not referenced
    // Following is disabled by default for regression testing.
    // TODO: [2003-02-17 ows] enable this
    if ((! isReferenced) && options.getBoolean(Compiler.ELIMINATE_DEAD_EXPRESSIONS)) {
      return null;
    }
    if ("_root".equals(((ASTIdentifier)node).getName()) && (! options.getBoolean(Compiler.ALLOW_ROOT))) {
      throw new SemanticError("Illegal variable name: " + node, node);
    }
    return translateReference(node).get();
  }

  @Override
  public SimpleNode visitLiteral(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    // Side-effect free expressions can be suppressed if not referenced
    // Following is disabled by default for regression testing.
    // TODO: [2003-02-17 ows] enable this
    if ((! isReferenced) && options.getBoolean(Compiler.ELIMINATE_DEAD_EXPRESSIONS)) {
      return null;
    }
    return translateLiteralNode(node);
  }

  SimpleNode translateLiteralNode(SimpleNode node) {
    return node;
  }

  protected SimpleNode translateArrayLiteral(SimpleNode node, SimpleNode[] children) {
    int last = children.length - 1;
    if (last >= 1 && children[last] instanceof ASTEmptyExpression) {
      // Workaround for JScript-Bug, elided elements at end are not ignored:
      // Per ES3/5 [,] == [void 0], but in JScript [,] == [void 0, void 0].
      // 
      // A trailing comma in an ArrayLiteral produces an EmptyExpression
      // in the parser, e.g. [0,] => [NumberLiteral, EmptyExpression] and
      // [,] => [EmptyExpression, EmptyExpression]. So whenever the last
      // child is an EmptyExpression, we're simply removing it. If the last
      // but one element is also an EmptyExpression, we're replacing it with
      // 'undefined'. That covers both cases [0,] => [0] and [,] => [void 0].
      SimpleNode copy[] = new SimpleNode[last];
      System.arraycopy(children, 0, copy, 0, last);
      if (copy[last - 1] instanceof ASTEmptyExpression) {
        copy[last - 1] = UNDEFINED;
      }
      node.setChildren(copy);
    }
    return node;
  }

  @Override
  public SimpleNode visitArrayLiteral(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    node = translateArrayLiteral(node, children);
    // translateArrayLiteral() may update the children array!
    children = node.getChildren();
    for (int i = 0, len = children.length; i < len; ++i) {
      children[i] = visitExpression(children[i], isReferenced);
    }
    return node;
  }

  @Override
  public SimpleNode visitObjectLiteral(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    boolean isKey = true;
    for (int i = 0, len = children.length; i < len; i++, isKey = (! isKey)) {
      SimpleNode item = children[i];
      if (isKey && (item instanceof ASTIdentifier)) {
        ASTIdentifier id = (ASTIdentifier) item;
        // generate ES3 compliant code for now
        if (isKeyword(id.getName())) {
          item = copyNodeInfo(id, new ASTLiteral(id.getName()));
        } else {
          // Identifiers are a shorthand for a literal string, should
          // not be evaluated (or remapped).  [Maybe call visitLiteral?]
          continue;
        }
      }
      children[i] = visitExpression(item);
    }
    return node;
  }

  @Override
  public SimpleNode visitEmptyExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    // Side-effect free expressions can be suppressed if not referenced
    if ((! isReferenced) && options.getBoolean(Compiler.ELIMINATE_DEAD_EXPRESSIONS)) {
      return null;
    }
    return node;
  }

  @Override
  public SimpleNode visitThisReference(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    // Side-effect free expressions can be suppressed if not referenced
    if ((! isReferenced) && options.getBoolean(Compiler.ELIMINATE_DEAD_EXPRESSIONS)) {
      return null;
    }
    return translateReference(node).get();
  }

  @Override
  public SimpleNode visitFunctionExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    Compiler.OptionMap savedOptions = options;
    try {
      options = options.copy();
      options.putBoolean(Compiler.CONSTRAINT_FUNCTION, false);
      return translateFunction(node, false, children);
    }
    finally {
      options = savedOptions;
    }
  }

  // A method declaration may appear in an expression context (when it
  // is in the Class.make plist)
  @Override
  public SimpleNode visitMethodDeclarationAsExpression(SimpleNode node,  boolean isReferenced, SimpleNode[] ast) {
    assert context.isClassBoundary() : ("Method not in class context? " + context);
    assert (! (this instanceof SWF9Generator)) : "Method expressions should not happen in swf9";
    Compiler.OptionMap savedOptions = options;
    try {
      options = options.copy();
      options.putBoolean(Compiler.CONSTRAINT_FUNCTION, false);
      // When a method declaration is an expression, don't use the name
      return translateMethod(node, false, ast);
    }
    finally {
      options = savedOptions;
    }
  }

  @Override
  public SimpleNode visitModifiedDefinitionAsExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    assert (! (this instanceof SWF9Generator)) : "Modified expressions should not happen in swf9";
    // Modifiers, like 'final', are ignored unless this is handled
    // by the runtime.

    assert children.length == 1;
    SimpleNode child = children[0];

    ((ASTModifiedDefinition)node).verifyTopLevel(child);

    return visitExpression(child, isReferenced);
  }

  @Override
  public SimpleNode visitPropertyIdentifierReference(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    assert children.length == 2;
    assert children[1] instanceof ASTIdentifier;
    ASTIdentifier id = (ASTIdentifier) children[1];
    // generate ES3 compliant code for now
    if (isKeyword(id.getName())) {
      ASTPropertyIdentifierReference idref = (ASTPropertyIdentifierReference) node;
      ASTPropertyValueReference valref = toPropertyValueReference(idref);
      return visitExpression(valref);
    } else {
      return translateReference(node).get();
    }
  }

  @Override
  public SimpleNode visitPropertyValueReference(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    return translateReference(node).get();
  }

  private SimpleNode makeCheckedNode(SimpleNode node) {
    // Now that debugging automatically catches errors, we don't need
    // evalCarefully.  All a checked node needs to do is update the
    // lineno in the backtrace (if it is on).
    if (options.getBoolean(Compiler.DEBUG) && options.getBoolean(Compiler.WARN_UNDEFINED_REFERENCES)
        // Only check this where 'this' is available
        && (context.findFunctionContext() != null)) {
      return noteCallSite(node);
    }
    return node;
  }

  @Override
  public SimpleNode noteCallSite(SimpleNode node) {
    // Note current call-site in a function context and backtracing
    if (node instanceof Compiler.AnnotatedNode) { return node; }
    if ((options.getBoolean(Compiler.DEBUG_BACKTRACE) && (node.beginLine > 0)) &&
        (context.findFunctionContext() != null)) {
      SimpleNode newNode = new ASTExpressionList(0);
      newNode.set(0, visitExpression((new Compiler.Parser()).parse("$lzsc$a.lineno = " + node.beginLine).get(0).get(0)));
      newNode.set(1, node);
      return new Compiler.AnnotatedNode(newNode);
    }
    return node;
  }

  // Could do inline expansions here, like setAttribute
  @Override
  public SimpleNode visitCallExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    SimpleNode fnexpr = children[0];
    // TODO: [2002-12-03 ptw] There should be a more general
    // mechanism for matching patterns against AST's and replacing
    // them.
    SimpleNode params = children[1];
    children[1] = visitFunctionCallParameters(params, isReferenced, params.getChildren());
    children[0] = translateReferenceForCall(fnexpr, true, node);
//     if (options.getBoolean(Compiler.WARN_UNDEFINED_REFERENCES)) {
//       return makeCheckedNode(node);
//     }
    return noteCallSite(node);
  }

  // TODO: [2009-10-29 ptw] If we obfuscate private methods, this
  // needs to translate the method identifier.  Maybe that will be
  // correctly handled by visitIdentifier?
  @Override
  public SimpleNode visitSuperCallExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    SimpleNode n = translateSuperCallExpression(node, isReferenced, children);
    children = n.getChildren();
    for (int i = 0, len = children.length ; i < len; i++) {
      children[i] = visitExpression(children[i], isReferenced);
    }
    // FIXME: [2009-10-29 ptw] Why no noteCallSite here?
    return n;
  }

  @Override
  public SimpleNode visitPrefixExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    SimpleNode ref = children[1];
    children[1] = translateReference(ref).get();
    return node;
  }

  @Override
  public SimpleNode visitPostfixExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    SimpleNode ref = children[0];
    children[0] = translateReference(ref).get();
    return node;
  }

  @Override
  public SimpleNode visitUnaryExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    int op = ((ASTOperator)children[0]).getOperator();
    // I guess the parser doesn't know the difference
    if (ParserConstants.INCR == (op) || ParserConstants.DECR == (op)) {
      return visitPrefixExpression(node, isReferenced, children);
    }
    SimpleNode arg = children[1];
    // special-case typeof(variable) to not emit undefined-variable
    // checks so there is a warning-free way to check for undefined
    // Details: noteCallSite() may change the expression to =>
    //   `typeof ($lzsc$a.lineno = 42, <ID>)`
    // If <ID> is not defined in the lexical environment, the comma expression
    // throws a ReferenceError when it is evaluated. Therefore we can't allow
    // noteCallSite() for identifiers here.
    if (ParserConstants.TYPEOF == (op) && arg instanceof ASTIdentifier) {
      children[1] = translateReference(arg).get(false);
    } else {
      children[1] = visitExpression(arg);
    }
    return node;
  }

  @Override
  public SimpleNode visitBinaryExpressionSequence(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    SimpleNode a = children[0];
    SimpleNode op = children[1];
    SimpleNode b = children[2];
    if (ParserConstants.CAST ==  ((ASTOperator)op).getOperator()) {
      // Approximate a cast b as a
      // TODO: [2008-01-08 ptw] We could typecheck and throw an error
      // in debug mode
      return visitExpression(a);
    }
    if (ParserConstants.IS ==  ((ASTOperator)op).getOperator()) {
      // Approximate a is b as b['$lzsc$isa'] ? b.$lzsc$isa(a) : (a
      // instanceof b)
      Map<String, Object> map = new HashMap<String, Object>();
      map.put("_1", a);
      map.put("_2", b);
      String pattern;
      if ((a instanceof ASTIdentifier ||
           a instanceof ASTPropertyValueReference ||
           a instanceof ASTPropertyIdentifierReference ||
           a instanceof ASTThisReference) &&
          (b instanceof ASTIdentifier ||
           b instanceof ASTPropertyValueReference ||
           b instanceof ASTPropertyIdentifierReference ||
           b instanceof ASTThisReference)) {
        pattern = "(_2['$lzsc$isa'] ? _2.$lzsc$isa(_1) : (_1 instanceof _2))";
      } else {
        pattern = "((function (a, b) {return b['$lzsc$isa'] ? b.$lzsc$isa(a) : (a instanceof b)})(_1, _2))";
      }
      SimpleNode n = (new Compiler.Parser()).substitute(node, pattern, map);
      return visitExpression(n);
    }
    if (ParserConstants.SUBCLASSOF ==  ((ASTOperator)op).getOperator()) {
      Map<String, Object> map = new HashMap<String, Object>();
      map.put("_1", a);
      map.put("_2", b);
      String pattern = "($lzsc$issubclassof(_1, _2))";
      SimpleNode n = (new Compiler.Parser()).substitute(node, pattern, map);
      return visitExpression(n);
    }
    children[0] = visitExpression(a);
    children[2] = visitExpression(b);
    return node;
  }

  @Override
  public SimpleNode visitConditionalExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    assert children.length == 3;
    SimpleNode test = children[0];
    SimpleNode a = children[1];
    SimpleNode b = children[2];

    // Compile-time conditional evaluations
    Boolean value = evaluateCompileTimeConditional(test);
    if (value != null) {
      if (value.booleanValue()) {
        return visitExpression(a);
      } else {
        return visitExpression(b);
      }
    } else {
      children[0] = visitExpression(test);
      children[1] = visitExpression(a);
      children[2] = visitExpression(b);
      return node;
    }
  }

  @Override
  public SimpleNode visitAssignmentExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    JavascriptReference lhs = translateReference(children[0]);
    SimpleNode rhs = visitExpression(children[2]);
    children[2] = rhs;
    children[0] = lhs.set();
    return node;
  }

  @Override
  SimpleNode translateFunction(SimpleNode node, boolean useName, SimpleNode[] children) {
    return translateFunction(node, useName, children, false);
  }

  @Override
  SimpleNode translateMethod(SimpleNode node, boolean useName, SimpleNode[] children) {
    assert (node instanceof ASTMethodDeclaration) : "expected method but was: " + node.getClass();
    // we don't (yet) support accessor methods for this runtime
    ASTMethodDeclaration method = (ASTMethodDeclaration) node;
    if (method.getMethodType() != ASTMethodDeclaration.MethodType.DEFAULT) {
      throw new CompilerError("accessor methods are not supported for the target runtime");
    }
    return translateFunction(node, useName, children, true);
  }

  // useName => declaration not expression
  @Override
  SimpleNode translateFunction(SimpleNode node, boolean useName, SimpleNode[] children, boolean isMethod) {
    // TODO: [2003-04-15 ptw] bind context slot macro
    SimpleNode[] result;
    // methodName and scriptElement
    Compiler.OptionMap savedOptions = options;
    try {
      options = options.copy();
      context = makeTranslationContext(ASTFunctionExpression.class, context);
      node = formalArgumentsTransformations(node);
      children = node.getChildren();
      result = translateFunctionInternalJavascript(node, useName, children, isMethod);
    }
    finally {
      options = savedOptions;
      context = context.parent;
    }
    node = result[0];
    // Dependency function is not compiled in the function context
    if (result[1] != null) {
      SimpleNode dependencies = result[1];
      Map<String, Object> map = new HashMap<String, Object>();
      map.put("_1", node);
      map.put("_2", translateFunction(dependencies, false, dependencies.getChildren()));
      SimpleNode newNode = (new Compiler.Parser()).substitute(node,
        "(function () {var $lzsc$f = _1; $lzsc$f.dependencies = _2; return $lzsc$f })();", map);
      return newNode;
    }
    return node;
  }

  SimpleNode rewriteScriptVars(SimpleNode node) {
    // Convert the variable declarations to assignments.  This is
    // a little sneaky, by replacing the VariableStatement with a
    // Statement, we magically remove the `var` and all is well...
    if (node instanceof ASTVariableStatement) {
      SimpleNode newNode = new ASTStatement(0);
      newNode.set(0, node.get(0));
      return newNode;
    }
    // Don't descend into inner functions
    if (node instanceof ASTFunctionDeclaration ||
        node instanceof ASTFunctionExpression) {
      return node;
    }
    SimpleNode[] children = node.getChildren();
    for (int i = 0, ilim = children.length; i < ilim; i++) {
      SimpleNode oldNode = children[i];
      SimpleNode newNode = rewriteScriptVars(oldNode);
      if (! newNode.equals(oldNode)) {
        children[i] = newNode;
      }
    }
    return node;
  }

  // Internal helper function for above
  // useName => declaration not expression
  // This is a dumb way to do this.  The caller should use the new
  // interface for computing dependencies rather that this having to
  // return multiple values.
  SimpleNode[] translateFunctionInternalJavascript(SimpleNode node, boolean useName, SimpleNode[] children, boolean isMethod) {
    // ast can be any of:
    //   FunctionDefinition(name, args, body)
    //   FunctionDeclaration(name, args, body)
    //   FunctionDeclaration(args, body)
    // Handle the two arities:
    String functionName = null;
    SimpleNode params;
    SimpleNode stmts;
    int stmtsIndex;
    ASTIdentifier functionNameIdentifier = null;
    if (children.length == 3) {
      if (children[0] instanceof ASTIdentifier) {
        functionNameIdentifier = (ASTIdentifier)children[0];
        functionName = functionNameIdentifier.getName();
      }
      params = children[1];
      stmts = children[stmtsIndex = 2];
    } else {
      params = children[0];
      stmts = children[stmtsIndex = 1];
    }
    // inner functions do not get scriptElement treatment, shadow any
    // outer declaration
    options.putBoolean(Compiler.SCRIPT_ELEMENT, false);
    // or the magic with(this) treatment
    options.putBoolean(Compiler.WITH_THIS, false);
    if (this instanceof SWF9Generator) {
      // Used to work around Adobe bug
      // http://bugs.adobe.com/jira/browse/ASC-3852
      context.setProperty("returnType", ((ASTFormalParameterList)params).getReturnType());
    }
    // function block
    String userFunctionName = null;
    String filename = node.filename != null? node.filename : "unknown file";
    String lineno = "" + node.beginLine;
    if (functionName != null) {
      userFunctionName = functionName;
      if (! useName) {
        // NOTE: [2009-09-01 ptw] (LPP-8431) IE ruins naming function
        // expressions for everyone because it has a retarded
        // implementation of Javascript that no one in their right
        // mind could conceive of.  See:
        // http://yura.thinkweb2.com/named-function-expressions/ for
        // full details.
        //
        // NOTE: [2009-09-01 ptw] The swf9 JIT seems to have a similar
        // lossage, so we don't ever use named function expressions
//         if (
//               // This is a function-expression that has been annotated
//               // with a non-legal function name
//               (! identifierPattern.matcher(functionName).matches()) ||
//               (! (this instanceof SWF9Generator))
//             )
//         {
          // Remove the function name, it will be emitted as the
          // function's pretty name below instead.
          functionName = null;
          children[0] = new ASTEmptyExpression(0);
//         }
      }
    } else {
      userFunctionName = "" + filename + "#" +  lineno + "/" + node.beginColumn;
    }
    // Tell metering to look up the name at runtime if it is not a
    // global name (this allows us to name closures more
    // mnemonically at runtime
    String meterFunctionName = useName ? functionName : null;
    SimpleNode[] paramIds = params.getChildren();
    // Pull all the pragmas from the list: process them, and remove
    // them
    assert stmts instanceof ASTStatementList;
    List<SimpleNode> stmtList = new ArrayList<SimpleNode>(Arrays.asList(stmts.getChildren()));
    // Parse out the pragmas of the function body
    for (int i = 0, len = stmtList.size(); i < len; i++) {
      SimpleNode stmt = stmtList.get(i);
      if (stmt instanceof ASTPragmaDirective) {
        SimpleNode newNode = visitStatement(stmt);
        if (! newNode.equals(stmt)) {
          stmtList.set(i, newNode);
        }
      }
    }
    // Allows the tag compiler to pass through a pretty name for debugging
    String explicitUserFunctionName = (String)options.get("userFunctionName");
    if (explicitUserFunctionName != null) {
      userFunctionName = explicitUserFunctionName;
    }
    assert (! options.getBoolean(Compiler.CONSTRAINT_FUNCTION));
    boolean isStatic = isStatic(node);
    // Analyze local variables (and functions)
    VariableAnalyzer analyzer = 
      new VariableAnalyzer(params, 
                           options.getBoolean(Compiler.FLASH_COMPILER_COMPATABILITY),
                           (this instanceof SWF9Generator),
                           this);
    analyzer.visit(stmtList);
    // We only want to compute these analysis components on the
    // original body of code, not on the annotations, since these are
    // used to enforce LZX semantics in Javascript
    analyzer.computeReferences();
    // Parameter _must_ be in order
    LinkedHashSet<String> parameters = analyzer.parameters;
    // Linked for determinism for regression testing
    Set<String> variables = analyzer.variables;
    // Linked for determinism for regression testing
    LinkedHashMap<String, ASTFunctionDeclaration> fundefs = analyzer.fundefs;
    Set<String> closed = analyzer.closed;
    Set<String> free = analyzer.free;
    Set<String> possibleInstance = new HashSet<String>(free);

    // Look for #pragma
    boolean scriptElement = options.getBoolean(Compiler.SCRIPT_ELEMENT);
    List<SimpleNode> newBody = new ArrayList<SimpleNode>();
    if (scriptElement) {
      // Create all variables (including inner functions) in global scope
      if (! variables.isEmpty()) {
        String code = "";
        for (String name : variables) {
          // TODO: [2008-04-16 ptw] Retain type information through
          // analyzer so it can be passed on here
          addGlobalVar(name, null, "void 0");
          code +=  name + "= void 0;";
        }
        newBody.add(parseFragment(code));

        for (int i = 0, ilim = stmtList.size(); i < ilim; i++) {
          SimpleNode stmt = stmtList.get(i);
          SimpleNode newNode = rewriteScriptVars(stmt);
          if (! newNode.equals(stmt)) {
            stmtList.set(i, newNode);
          }
        }
      }
    } else {
      // Leave var declarations as is
      // Emit function declarations here
      if (! fundefs.isEmpty()) {
        String code = "";
        for (String name : fundefs.keySet()) {
          code += "var " + name + ";";
        }
        newBody.add(parseFragment(code));
      }
    }

    // If either of error or suffix are set, a try block is created.
    // postlude is only used if error is set, to insert a dummy return
    // value to keep the Flex compiler happy (See:
    // https://bugs.adobe.com/jira/browse/ASC-3576)
    List<SimpleNode> prelude = new ArrayList<SimpleNode>();  // before try
    List<SimpleNode> prefix = new ArrayList<SimpleNode>();   // before body
    List<SimpleNode> error = new ArrayList<SimpleNode>();    // error body
    List<SimpleNode> suffix = new ArrayList<SimpleNode>();   // finally body
    List<SimpleNode> postlude = new ArrayList<SimpleNode>(); // after try
    // If backtrace is on, we maintain a call stack for debugging
    boolean debugBacktrace = options.getBoolean(Compiler.DEBUG_BACKTRACE);
    if (debugBacktrace) {
      // TODO: [2007-09-04 ptw] Come up with a better way to
      // distinguish LFC from user stack frames.  See
      // lfc/debugger/LzBacktrace
      String fn = (options.getBoolean(Compiler.FLASH_COMPILER_COMPATABILITY) ? "lfc/" : "") + filename;
      String args = "[";
      for (Iterator<String> i = parameters.iterator(); i.hasNext(); ) {
        String arg = i.next();
        args += ScriptCompiler.quote(arg) + "," + arg;
        if (i.hasNext()) { args += ","; }
      }
      args += "]";
      prelude.add(parseFragment(
                    "var $lzsc$d = Debug; var $lzsc$s = $lzsc$d.backtraceStack;"));
      prefix.add(parseFragment(
                   "  var $lzsc$a = " + args + ";" +
                   "  $lzsc$a.callee = " +
                   // For now, we don't try to reference the
                   // function/method object
                   ((this instanceof SWF9Generator) ?
                    ScriptCompiler.quote(userFunctionName) :
                    "arguments.callee") + ";" +
                   (isStatic ? "" : "  $lzsc$a['this'] = this;") +
                   "  $lzsc$a.filename = " + ScriptCompiler.quote(fn) + ";" +
                   "  $lzsc$a.lineno = " + lineno + ";" +
                   "  $lzsc$s.push($lzsc$a);" +
                   "  if ($lzsc$s.length > $lzsc$s.maxDepth) {$lzsc$d.stackOverflow()};" +
                   ""));
      suffix.add(parseFragment(
                    "if ($lzsc$s) {" +
                    "  $lzsc$s.length--;" +
                    "}"));
    }
    // If profile is on, we meter function enter/exit
    if (options.getBoolean(Compiler.PROFILE)) {
      prefix.add((meterFunctionEvent(node, "calls", meterFunctionName)));
      // put the function end before other annotations
      suffix.add(0, (meterFunctionEvent(node, "returns", meterFunctionName)));
    }
    // If debug or compiler.catcherrors is on, we create an error
    // handler to catch the error and return a type-safe null value
    // (to emulate the behavior of as2).
    // Non-errors will simply be re-thrown by the catch logic.
    boolean catchExceptions = options.getBoolean(Compiler.CATCH_FUNCTION_EXCEPTIONS);
    // NOTE: [2009-09-14 ptw] `#pragma "throwsError=true"` can be used
    // to selectively disable the catching of errors when it is
    // intentional on the part of the user program, but better
    // practice would be for user programs to use non-Error object as
    // the value to be thrown.
    boolean throwExceptions = options.getBoolean(Compiler.THROWS_ERROR);
    // If debugging is on we create an error handler to catch the
    // error, report it, and unless the error is declared, neuter it
    // as we would for compiler.catcherrors
    boolean debugExceptions = (options.getBoolean(Compiler.DEBUG) ||
                               options.getBoolean(Compiler.DEBUG_SWF9));
    // For efficiency, we only insert the error handler if the
    // variable analysis shows that there is a dereference or free
    // reference in the original body of the function, or if we are
    // recording a declared exception.  We will always establish the
    // error handler if backtracing is on, since that establishes a
    // try block anyways.  Enabling backtracing is thus the way to get
    // the most accurate error reporting, at the cost of additional
    // overhead.
    if (((catchExceptions || debugExceptions) && (debugBacktrace || analyzer.dereferenced || (! free.isEmpty()))) ||
        throwExceptions) {
      String fragment = "";
      if (throwExceptions) {
        // Just record declared errors and always rethrow
        fragment +=
          "if ($lzsc$e is Error) {" +
          "  lz.$lzsc$thrownError = $lzsc$e" +
          "}" +
          "throw $lzsc$e;";
      } else {
        // Don't process errors declared to be thrown
        fragment +=
          "if (($lzsc$e is Error) && ($lzsc$e !== lz['$lzsc$thrownError'])) {";
        // Report all errors when debugging
        if (debugExceptions) {
          // TODO: [2009-03-20 dda] In DHTML, having trouble
          // successfully defining the $lzsc$runtime class, so we'll
          // report the warning more directly.
          if (this instanceof SWF9Generator) {
            fragment += "  $lzsc$runtime.$reportException(";
          } else {
            fragment += "  $reportException(";
          }
          fragment += ScriptCompiler.quote(filename) + ", " + (debugBacktrace ? "$lzsc$a.lineno" : lineno) + ", $lzsc$e);";
        }
        // Neuter errors:  either compiler.catcherrors is true, or we
        // are debugging and don't want undeclared errors to halt the
        // program.  But re-throw declared and non-errors
        fragment +=
          "} else {" +
          "  throw $lzsc$e;" +
          "}";
      }
      error.add(parseFragment(fragment));

      // Currently we only do this for the back-end that enforces
      // types (because the compiler will complain about not returning
      // a value if we ignore the error).
      //
      //  NOTE: [2010-09-24 ptw] (LPP-9386) Only add this work-around
      // if there are return values in the function body, to avoid
      // masking a programmer error
      if ((this instanceof SWF9Generator) && analyzer.hasReturnValue) {
        // In either case, we return a type-safe null value that is as
        // close to the default value as2 would have returned
        ASTIdentifier.Type returnType = ((ASTFormalParameterList)params).getReturnType();
        if (returnType != null) {
          String returnValue = "null";
          String typeName = returnType.typeName;
          // How handy that Java does not allow you to write switch on
          // String...
          // I think this covers all the types that won't accept null
          // (the types that are not sub-types of Object)
          if ("Boolean".equals(typeName)) {
            returnValue = "false";
          } else if ("Number".equals(typeName) ||
                     // NOTE: [2009-04-16 ptw] These are as3-only
                     // types, included for completeness but not
                     // really legal in LZX
                     "int".equals(typeName) ||
                     "uint".equals(typeName)) {
            returnValue = "0";
          } else if ("void".equals(typeName)) {
            returnValue = "";
          }
          // This _should_ be able to be in the error clause, but see
          // the note where postlude is declared
          postlude.add(parseFragment("return " + returnValue + ";"));
        }
      }
      // Not sure why we turn this off now...  Some suspicion we will recurse?
      options.putBoolean(Compiler.CATCH_FUNCTION_EXCEPTIONS, false);
    }
    // Now we visit all the wrapper code and add the variables
    // declared there (so they can be renamed properly)
    analyzer.visit(prelude);
    analyzer.visit(prefix);
    analyzer.visit(error);
    analyzer.visit(suffix);
    analyzer.visit(postlude);
    analyzer.computeReferences();
    variables.addAll(analyzer.variables);
    // Whether to insert with (this) ...
    boolean withThis = false;
    // Never in static methods
    if (! isStatic) {
      // Look for explicit #pragma, e.g., for 'dynamic' methods
      if (options.getBoolean(Compiler.WITH_THIS)) {
        withThis = true;
      } else if (! (this instanceof SWF9Generator)) {
        // In JS1 back-ends, need `with (this) ...` for implicit
        // instance references.  This doesn't necessarily mean we'll
        // actually use a `with (this)`, we'll try instead to fix up
        // the free references directly.
        withThis = isMethod;
      }
    }
    // Note usage due to activation object and withThis.  If withThis
    // is on, we'll seek to explicitly bind free references to
    // instance variables (i.e. 'ident' -> 'this.ident').  This is a
    // big win, especially with IE.  If all remaining free references
    // can be accounted for, as globals and class names, (that is, if
    // possibleInstance is empty) we can drop the need for an
    // enclosing with(this) construct.  possibleInstance might better
    // be thought of as 'unaccounted for instance vars'.
    //
    // Generally, we can achieve our goal unless *both* of these spoilers occur:
    //  #1 we have an incomplete knowledge of the class heirarchy.
    //  #2 there are free references
    // However, if this spoiler occurs:
    //  #3 original source code contains a with(...)
    // then we cannot safely do 'this.ident' binding within the with(...),
    // we must emit with(this) anyway.  There are some potential optimizations
    // for case #3, but any code that has with() probably doesn't care much about speed.

    boolean hasWith = false;    // to track spoiler #3
    if (withThis) {
      ClassDescriptor classdesc = context.getProperty(TranslationContext.CLASS_DESCRIPTION);
      if (classdesc != null) {
        Set<String> instanceprops = classdesc.getInstanceProperties();

        // Create bindthis, the list of variables that we will bind to 'this'.
        Set<String> bindthis = new HashSet<String>();
        // Check for spoiler #1 and #2
        if (classdesc.complete || possibleInstance.isEmpty()) {
          bindthis.addAll(possibleInstance);
          bindthis.retainAll(instanceprops);
          if (classdesc.complete) {
            // See if we can account for all the free references.
            // Warn about any we don't know about.
            possibleInstance.removeAll(bindthis);
            possibleInstance.removeAll(objectClassDefined);
            possibleInstance.removeAll(forwardGlobals);

            // Unknown globals may occur with any use of an LFC class name,
            // e.g. 'new LzFoo()' or 'LzFoo.staticMethod()'
            // TODO: [2011-03-16 dda] should really pass through all
            // all LFC classes as reference classes so we have all the names,
            // then we could feasibly make this warning the default.
            if ((! possibleInstance.isEmpty()) && options.getBoolean(Compiler.WARN_UNKNOWN_GLOBALS)) {
              System.err.println("Warning: " + userFunctionName + " uses unknown variables, treated as globals: " + possibleInstance);
            }
          }

          // regardless, all remaining free references are treated
          // as globals, since they certainly aren't instance vars.
          possibleInstance.clear();
        }
        if (! bindthis.isEmpty()) {
          // Modify all references to variables in the bindthis
          // set to use 'this.'
          WithThisAnalyzer wtanalyzer =
            new WithThisAnalyzer(params, 
                                 options.getBoolean(Compiler.FLASH_COMPILER_COMPATABILITY),
                                 (this instanceof SWF9Generator),
                                 this,
                                 bindthis,
                                 userFunctionName);

          wtanalyzer.visit(stmtList);
          wtanalyzer.visit(prelude);
          wtanalyzer.visit(prefix);
          wtanalyzer.visit(error);
          wtanalyzer.visit(suffix);
          wtanalyzer.visit(postlude);
          wtanalyzer.apply();
          hasWith = wtanalyzer.hasWith;
          analyzer.incrementUsed("this");
        }
      }

      if (hasWith) {
        System.err.println("Warning: " + userFunctionName + " uses deprecated 'with (...)' statement");
      }

      // If there's anything in possibleInstance, it means we'll
      // generate 'with(this)'.  Issue a warning if asked.
      if (! possibleInstance.isEmpty() || hasWith) {
        analyzer.incrementUsed("this");
        boolean wantError = options.getBoolean(Compiler.ERROR_WITH_THIS);
        boolean wantWarn = options.getBoolean(Compiler.WARN_WITH_THIS);
        if (wantError || wantWarn) {
          String msg = "";
          if (hasWith) {
            msg = "with() construct found in source";
          }
          if (!possibleInstance.isEmpty()) {
            if (msg.length() != 0) {
              msg += "; ";
            }
            if (classdesc == null) {
              msg += "unknown class";
            }
            else {
              msg += "unknown parts of class hierarchy: " + classdesc.incompleteSet();
            }
            msg += " and unaccounted refs: " + possibleInstance;
          }
          msg = "with(this) added in " + userFunctionName + ": " + msg;
          if (wantError) {
            throw new CompilerError(msg);
          }
          else {
            System.err.println("Warning: " + msg);
          }
        }
      }
    }
    // Scripts do not get withThis.  If there are no possible instance
    // refs, and no uses of with() in the input source, we don't need withThis.
    if (scriptElement || (possibleInstance.isEmpty() && !hasWith)) {
      withThis = false;
    }
    Map<String, Integer> used = analyzer.used;
    // If this is a closure, annotate the Username for metering
    if ((! closed.isEmpty()) && (userFunctionName != null) && options.getBoolean(Compiler.PROFILE)) {
      // Is there any other way to construct a closure in js
      // other than a function returning a function?
      if (context.findFunctionContext().parent.findFunctionContext() != null) {
        userFunctionName = userFunctionName + " closure";
      }
    }
//    if (false) {
//      System.err.println(userFunctionName +
//                         ":: parameters: " + parameters +
//                         ", variables: " + variables +
//                         ", fundefs: " + fundefs +
//                         ", used: " + used +
//                         ", closed: " + closed +
//                         ", free: " + free +
//                         ", possible: " + possibleInstance);
//    }
    // Deal with warnings
    if (options.getBoolean(Compiler.WARN_UNUSED_PARAMETERS)) {
      Set<String> unusedParams = new LinkedHashSet<String>(parameters);
      unusedParams.removeAll(used.keySet());
      for (String p : unusedParams) {
        System.err.println("Warning: parameter " + p + " of " + userFunctionName +
                           " unused in " + filename + "(" + lineno + ")");
      }
    }
    if (options.getBoolean(Compiler.WARN_UNUSED_LOCALS)) {
      Set<String> unusedVariables = new LinkedHashSet<String>(variables);
      unusedVariables.removeAll(used.keySet());
      for (String v : unusedVariables) {
        System.err.println("Warning: variable " + v + " of " + userFunctionName +
                           " unused in " + filename + "(" + lineno + ")");
      }
    }
    // auto-declared locals
    Set<String> auto = new LinkedHashSet<String>();
    auto.add("this");
    auto.add("arguments");
    auto.retainAll(used.keySet());
    // parameters, locals, and auto-registers
    Set<String> known = new LinkedHashSet<String>(parameters);
    known.addAll(variables);
    known.addAll(auto);
    // for now, ensure that super has a value
    known.remove("super");

    Map<String, String> registerMap = new HashMap<String, String>();
    if (! scriptElement) {
      // All parameters and locals are remapped to 'registers' of the
      // form `$n`.  This prevents them from colliding with member
      // slots due to implicit `with (this)` added below, and also makes
      // the emitted code more compact.
      int regno = 0;
      boolean debug = options.getBoolean(Compiler.NAME_FUNCTIONS);
      for (String k : new LinkedHashSet<String>(known)) {
        String r;
        // Can't rename the "auto" variables.  If we are not using
        // withThis, don't rename closed variables either.  (If we
        // _are_ using withThis, we _have_ to rename closed over
        // parameters and copy them inside the with block to their
        // original name.)
        if (auto.contains(k) ||
            ((! withThis) && closed.contains(k)) ||
            (withThis && closed.contains(k) && (! parameters.contains(k)))) {
          ;
        } else {
          // Find a valid 'register' name (repeat until you don't
          // collide with the known or free sets)
          do {
            // When debugging prepend non-$ names for legibility
            r = ((debug && (! k.startsWith("$"))) ? (k + "_$") : "$") + Integer.toString(regno++, Character.MAX_RADIX) ;
          } while (known.contains(r) || free.contains(r));
          registerMap.put(k, r);
          // remove from known map
          known.remove(k);
        }
      }
    }
    // Always set register map.  Inner functions should not see
    // parent registers (which they would if the setting of the
    // registermap were conditional on function vs. function2)
    context.setProperty(TranslationContext.REGISTERS, registerMap);
    // Set the knownSet.  This includes the parent's known set, so
    // closed over variables are not treated as free.
    Set<String> knownSet = new LinkedHashSet<String>(known);
    // Add parent known
    Set<String> parentKnown = context.parent.getProperty(TranslationContext.VARIABLES);
    if (parentKnown != null) {
      knownSet.addAll(parentKnown);
    }
    context.setProperty(TranslationContext.VARIABLES, knownSet);
    // Replace params with their registers
    for (int i = 0, len = paramIds.length; i < len; i++) {
      if (paramIds[i] instanceof ASTIdentifier) {
        ASTIdentifier oldParam = (ASTIdentifier)paramIds[i];
        SimpleNode newParam = translateReference(oldParam).declare();
        params.set(i, newParam);
      }
    }
    translateFormalParameters(params);
    if (withThis) {
      // create closed parameters inside the with context: inner
      // functions refer to the unregistered parameter name, we have
      // to create that name inside the with context to avoid the
      // parameter name incorrectly getting shadowed by an instance
      // property.
      LinkedHashSet<String> toCreate = new LinkedHashSet<String>(parameters);
      toCreate.retainAll(closed);
      if (! toCreate.isEmpty()) {
        String code = "";
        for (String var : toCreate) {
          code += "var " + var + " = " + registerMap.get(var) + ";";
          // Remove from the map, so the value cell is shared between
          // the function body and the closures
          registerMap.remove(var);
        }
        // Insert these declarations at the front of the body
        newBody.add(0, parseFragment(code));
      }
    }

    // Cf. LPP-4850: Prefix has to come after declarations (above),
    // which means they are stuck inside the withThis (if any),
    // unfortunately.
    newBody.addAll(prefix);

    // Now emit functions in the activation context
    // Note: variable has already been declared so assignment does the
    // right thing (either assigns to global or local
    for (String name : fundefs.keySet()) {
      if (scriptElement || used.containsKey(name)) {
        SimpleNode fundecl = fundefs.get(name);
        SimpleNode funexpr = new ASTFunctionExpression(0);
        funexpr.setBeginLocation(fundecl.filename, fundecl.beginLine, fundecl.beginColumn);
        funexpr.setChildren(fundecl.getChildren());
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_1", funexpr);
        // Do I need a new one of these each time?
        newBody.add((new Compiler.Parser()).substitute(fundecl, name + " = _1;", map));
      }
    }

    // The actual body of the function
    newBody.addAll(stmtList);

    // Wrap body in try and make suffix be a finally clause, so suffix
    // will not be skipped by inner returns (but postlude _will_ be skipped).
    if (! suffix.isEmpty() || ! error.isEmpty()) {
      int i = 0;
      SimpleNode newStmts = new ASTStatementList(0);
      newStmts.setChildren(newBody.toArray(new SimpleNode[0]));
      SimpleNode tryNode = new ASTTryStatement(0);
      tryNode.set(i++, newStmts);
      if (! error.isEmpty()) {
        SimpleNode catchNode = new ASTCatchClause(0);
        SimpleNode catchStmts = new ASTStatementList(0);
        catchStmts.setChildren(error.toArray(new SimpleNode[0]));
        catchNode.set(0, new ASTIdentifier("$lzsc$e"));
        catchNode.set(1, catchStmts);
        tryNode.set(i++, catchNode);
      }
      if (! suffix.isEmpty()) {
        SimpleNode finallyNode = new ASTFinallyClause(0);
        SimpleNode suffixStmts = new ASTStatementList(0);
        suffixStmts.setChildren(suffix.toArray(new SimpleNode[0]));
        finallyNode.set(0, suffixStmts);
        tryNode.set(i, finallyNode);
      }
      newBody = new ArrayList<SimpleNode>();
      newBody.addAll(prelude);
      newBody.add(tryNode);
      newBody.addAll(postlude);
    }

    // NOTE: [2009-09-30 ptw] This properly belongs inside any try
    // block, but the AS3 compiler chokes if we do that
    // https://bugs.adobe.com/jira/browse/ASC-3849

    // If we have free references and are not a script, we have to
    // obey withThis.  NOTE: [2009-09-30 ptw] See NodeModel where it
    // only inserts withThis if the method will by dynamically
    // attached, as in a <state>
    if (withThis) {
      // NOTE: [2009-10-20 ptw] Now that we are doing this analysis in
      // the script compiler, we may insert a withThis into LFC code
      // where previously we would not have.  For now, we warn if we
      // are doing this, because it is a new policy for the LFC
      //
      // Here FLASH_COMPILER_COMPATABILITY means 'compiling the lfc'
      if (options.getBoolean(Compiler.FLASH_COMPILER_COMPATABILITY)) {
        System.err.println("Warning: " + userFunctionName + " free reference(s) converted to instance reference(s): " + possibleInstance);
      }
      SimpleNode newStmts = new ASTStatementList(0);
      newStmts.setChildren(newBody.toArray(new SimpleNode[0]));
      SimpleNode withNode = new ASTWithStatement(0);
      SimpleNode id = new ASTThisReference(0);
      withNode.set(0, id);
      withNode.set(1, newStmts);
      newBody = new ArrayList<SimpleNode>();
      newBody.add(withNode);
    }

    // Process amended body
    SimpleNode newStmts = new ASTStatementList(0);
    newStmts.setChildren(newBody.toArray(new SimpleNode[0]));
    newStmts = visitStatement(newStmts);
    // Finally replace the function body with that whole enchilada
    children[stmtsIndex] = newStmts;
    if ( options.getBoolean(Compiler.NAME_FUNCTIONS) && (! options.getBoolean(Compiler.DEBUG_SWF9))) {
      // TODO: [2007-09-04 ptw] Come up with a better way to
      // distinguish LFC from user stack frames.  See
      // lfc/debugger/LzBacktrace
      String fn = (options.getBoolean(Compiler.FLASH_COMPILER_COMPATABILITY) ? "lfc/" : "") + filename;
      if (functionName != null &&
          // Either it is a declaration or we are not doing backtraces
          // or profiling, so the name will be available for debugging
          // from the runtime
          (useName ||
           (! (options.getBoolean(Compiler.PROFILE) ||
               options.getBoolean(Compiler.DEBUG_BACKTRACE))))) {
        if (options.getBoolean(Compiler.PROFILE) ||
            options.getBoolean(Compiler.DEBUG_BACKTRACE)) {
          SimpleNode newNode = new ASTStatementList(0);
          int nn = 0;
          newNode.set(nn++, new Compiler.PassThroughNode(node));
          if (options.getBoolean(Compiler.PROFILE)) {
            newNode.set(nn++, parseFragment(functionName + "['" + Function.FUNCTION_NAME + "'] = " + ScriptCompiler.quote(functionName)));
          }
          if (options.getBoolean(Compiler.DEBUG_BACKTRACE)) {
            newNode.set(nn++, parseFragment(functionName + "['" + Function.FUNCTION_FILENAME + "'] = " + ScriptCompiler.quote(fn)));
            newNode.set(nn++, parseFragment(functionName + "['" + Function.FUNCTION_LINENO + "'] = " + lineno));
          }
          node = visitStatement(newNode);
        }
      } else {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("_1", node);
        SimpleNode newNode = new Compiler.PassThroughNode((new Compiler.Parser()).substitute(
          node,
          "(function () {" +
          "   var $lzsc$temp = _1;" +
          "   $lzsc$temp['" + Function.FUNCTION_NAME + "'] = " + ScriptCompiler.quote(userFunctionName) + ";" +
          ((options.getBoolean(Compiler.DEBUG_BACKTRACE)) ?
           ("   $lzsc$temp['" + Function.FUNCTION_FILENAME + "'] = " + ScriptCompiler.quote(fn) + ";" +
            "   $lzsc$temp['" + Function.FUNCTION_LINENO + "'] = " + lineno + ";") : 
           "") +
          "   return $lzsc$temp})()",
          map));
        node = newNode;
      }
    }
    if (catchExceptions) {
      options.putBoolean(Compiler.CATCH_FUNCTION_EXCEPTIONS, true);
    }
    return new SimpleNode[] { node, null };
  }

  // walk up the AST and find a match by type, optionally skipping the original node
  SimpleNode matchingAncestor(SimpleNode node, Class<?> matchClass, boolean includeThis) {
    while (node != null) {
      if (includeThis && matchClass.equals(node.getClass())) {
        return node;
      }
      includeThis = true;
      node = node.getParent();
    }
    return null;
  }

  // walk down the AST and find a match by type, optionally skipping the original node
  SimpleNode matchingDescendant(SimpleNode node, Class<?> matchClass, boolean includeThis) {
    if (node == null) {
      return null;
    }
    else if (matchClass.equals(node.getClass()) && includeThis) {
      return node;
    }
    else {
      SimpleNode[] children = node.getChildren();
      for (int i=0; i<children.length; i++) {
        SimpleNode result = matchingDescendant(children[i], matchClass, true);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  // walk down the AST and find a match of an identifier with a name
  SimpleNode matchingIdentifier(SimpleNode node, String identName) {
    if (node == null) {
      return null;
    }
    else if (node instanceof ASTIdentifier && ((ASTIdentifier)node).getName().equals(identName)) {
      return node;
    }
    else {
      SimpleNode[] children = node.getChildren();
      for (int i=0; i<children.length; i++) {
        SimpleNode result = matchingIdentifier(children[i], identName);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  SimpleNode translateReferenceForCall(SimpleNode ast, boolean checkDefined, SimpleNode node) {
    SimpleNode[] children = ast.getChildren();
    if (checkDefined) {
      assert node != null : "Must supply node for checkDefined";
    }
    if (ast instanceof ASTPropertyIdentifierReference) {
      JavascriptReference ref = translateReference(children[0]);
      children[0] = ref.get();
    } else if (ast instanceof ASTPropertyValueReference) {
      // TODO: [2002-10-26 ptw] (undefined reference coverage) Check
      JavascriptReference ref = translateReference(children[0]);
      children[1] = visitExpression(children[1]);
      children[0] = ref.get();
    }
    // The only other reason you visit a reference is to make a funcall
    if (ast instanceof ASTIdentifier) {
      JavascriptReference ref = translateReference(ast);
      ast = ref.preset();
    } else {
      ast = visitExpression(ast);
    }
    return ast;
  }

  static public class JavascriptReference {
    protected Compiler.OptionMap options;
    SimpleNode node;
    SimpleNode checkedNode = null;

    public JavascriptReference(ASTVisitor visitor, SimpleNode node) {
      this.options = visitor.getOptions();
      this.node = node;
    }

    public boolean isChecked() {
      return checkedNode != null;
    }

    public SimpleNode get(boolean checkUndefined) {
      if (checkUndefined && checkedNode != null) {
        return checkedNode;
      }
      return this.node;
    }

    public SimpleNode get() {
      return get(true);
    }

    public SimpleNode preset() {
      return this.node;
    }

    public SimpleNode set (Boolean warnGlobal) {
      return this.node;
    }

    public SimpleNode set() {
      return set(null);
    }

    public SimpleNode set(boolean warnGlobal) {
      return set(Boolean.valueOf(warnGlobal));
    }

    public SimpleNode declare() {
      return this.node;
    }

    public SimpleNode init() {
      return this.node;
    }
  }

  static public abstract class MemberReference extends JavascriptReference {
    protected SimpleNode object;

    public MemberReference(ASTVisitor visitor, SimpleNode node, SimpleNode object) {
      super(visitor, node);
      this.object = object;
    }
  }

  static public class VariableReference extends JavascriptReference {
    TranslationContext context;
    public final String name;

    public VariableReference(JavascriptGenerator generator, SimpleNode node, String name) {
      super(generator, node);
      this.name = name;
      this.context = (TranslationContext)generator.getContext();
      Map<String, String> registers = context.getProperty(TranslationContext.REGISTERS);
      // Set identifier 'register' (i.e. rename them)
      if ((registers != null) && (node instanceof ASTIdentifier) && registers.containsKey(name)) {
        String register = registers.get(name);
        ((ASTIdentifier)node).setRegister(register);
        return;
      }
      if (options.getBoolean(Compiler.WARN_UNDEFINED_REFERENCES)) {
        Set<String> variables = context.getProperty(TranslationContext.VARIABLES);
        if (variables != null) {
          boolean known = variables.contains(name);
          // Ensure undefined is "defined"
          known |= "undefined".equals(name);
          if (! known) {
            this.checkedNode = generator.makeCheckedNode(node);
          }
        }
      }
    }

    @Override
    public SimpleNode declare() {
      Set<String> variables = context.getProperty(TranslationContext.VARIABLES);
      if (variables != null) {
        variables.add(this.name);
      }
      return this.node;
    }

    @Override
    public SimpleNode init() {
      Set<String> variables = context.getProperty(TranslationContext.VARIABLES);
      if (variables != null) {
        variables.add(this.name);
      }
      return this.node;
    }

    @Override
    public SimpleNode get(boolean checkUndefined) {
      if (checkUndefined && checkedNode != null) {
        return checkedNode;
      }
      return node;
    }

    @Override
    public SimpleNode set(Boolean warnGlobal) {
      if (warnGlobal == null) {
        if (context.type instanceof ASTProgram) {
          warnGlobal = Boolean.FALSE;
        } else {
          warnGlobal = Boolean.valueOf(options.getBoolean(Compiler.WARN_GLOBAL_ASSIGNMENTS));
        }
      }
      if ((checkedNode != null) && warnGlobal.booleanValue()) {
        System.err.println("Warning: Assignment to free variable " + name +
                           " in " + node.filename + 
                           " (" + node.beginLine + ")");
      }
      return node;
    }
  }

  static public Set<String> uncheckedProperties = new HashSet<String>(Arrays.asList("call", "apply", "prototype"));

  static public class PropertyReference extends MemberReference {
    String propertyName;

    public PropertyReference(ASTVisitor visitor, SimpleNode node,
                               SimpleNode object, ASTIdentifier propertyName) {
      super(visitor, node, object);
      this.propertyName = propertyName.getName();
      // TODO: [2006-04-24 ptw] Don't make checkedNode when you know
      // that the member exists
      // This is not right, but Opera does not support [[Call]] on
      // call or apply, so we can't check for them
//       if (! uncheckedProperties.contains(this.propertyName)) {
//         this.checkedNode = ((JavascriptGenerator)visitor).makeCheckedNode(node);
//       }
    }
  }

  static public class IndexReference extends MemberReference {
    SimpleNode indexExpr;

    public IndexReference(ASTVisitor visitor, SimpleNode node,
                          SimpleNode object, SimpleNode indexExpr) {
      super(visitor, node, object);
      this.indexExpr = indexExpr;
      // We don't check index references for compatibility with SWF compiler
    }
  }


  JavascriptReference translateReference(SimpleNode node) {
    if (node instanceof ASTIdentifier) {
      return new VariableReference(this, node, ((ASTIdentifier)node).getName());
    }

    SimpleNode[] args = node.getChildren();
    if (node instanceof ASTPropertyIdentifierReference) {
      assert (args[1] instanceof ASTIdentifier) : String.format(
        "expected %s got %s", ASTIdentifier.class.getName(), args[1].getClass().getName());

      args[0] = visitExpression(args[0]);
      ASTIdentifier id = (ASTIdentifier) args[1];
      // generate ES3 compliant code for now
      if (isKeyword(id.getName())) {
        ASTPropertyIdentifierReference idref = (ASTPropertyIdentifierReference) node;
        ASTPropertyValueReference valref = toPropertyValueReference(idref);
        node = valref;
        args = node.getChildren();
        // don't call visitExpression() for args[0] a second time
        args[1] = visitExpression(args[1]);
        return new IndexReference(this, node, args[0], args[1]);
      } else {
        return new PropertyReference(this, node, args[0], (ASTIdentifier)args[1]);
      }
    } else if (node instanceof ASTPropertyValueReference) {
      args[0] = visitExpression(args[0]);
      args[1] = visitExpression(args[1]);
      return new IndexReference(this, node, args[0], args[1]);
    } else if (node instanceof ASTThisReference) {
      return new JavascriptReference(this, node);
    }
    else {
      return new JavascriptReference(this, visitExpression(node));
    }
  }


  /****************************************************************/

  // Calls to compileBlock send output to this stream (usually a file open for append)
  PrintWriter out;
  JavascriptParseTreePrinter ptp;

  /** set up persistent parser to compile successive blocks of script via
   * calls to compileBlock.
   */

  public void setupParseTreePrinter(boolean compress, boolean obfuscate, PrintWriter out)
  {
    this.out = out;
    ParseTreePrinter.Config config = new ParseTreePrinter.Config();
    config.compress = compress;
    config.obfuscate = obfuscate;
    config.trackLines = options.getBoolean(Compiler.TRACK_LINES);
    config.dumpLineAnnotationsFile = (String)options.get(Compiler.DUMP_LINE_ANNOTATIONS);
    ptp = new JavascriptParseTreePrinter(config);
  }

  public void finish() {
    out.close();
  }

  /** Copy text from input stream direct to output file */
  public void copyRawFromInputStream(InputStream is) throws IOException {
    FileUtils.send(new InputStreamReader(is), out);
  }


  public void compileBlock(SimpleNode translatedNode) {
    // Loop over top level nodes in AST, calling makeTranslationUnits
    // on each one, to keep heap size from growing too big.
    SimpleNode[] children = translatedNode.getChildren();

    // Write the class files out. This used to be in postProcess.
    for (int i=0; i < children.length; i++) {
      SimpleNode child = children[i];
      //System.err.println((new ParseTreePrinter()).text(child));

      List<TranslationUnit> tunits = ptp.makeTranslationUnits(child, sources);

      for (TranslationUnit tunit : tunits) {
        String objcode = tunit.getContents();
        out.write(objcode);
        if (!objcode.endsWith(";")) {
          out.write(";");
        }
        // Clear out string data to avoid wasting memory. But leave
        // class name because a list of all class names is needed when
        // constructing the command line to call flex.
        tunit.clearMost();
      }
    }

  }

}

/**
 * @copyright Copyright 2006-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */

