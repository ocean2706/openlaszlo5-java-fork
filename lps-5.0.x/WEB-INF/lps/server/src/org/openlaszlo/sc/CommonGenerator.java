/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * Common Code for Translation and Code Generation
 *
 * @author steele@osteele.com
 * @author ptw@openlaszlo.org
 * @author dda@ddanderson.com
 * @description: Common baseclass for code generators
 *
 * This class is extended by CodeGenerator, JavascriptGenerator.
 */

//
// Code Generation
//

// The CodeGenerator recurses over the parse tree, sending instructions
// to an InstructionCollector.  The entry point is translate(), and it
// does its work by calling two mutually recursive functions,
// visitStatement and visitExpression, which dispatch to visitor
// functions for specific statement and expression types based on the
// name of the class of the parser node.  (A declaration or definition
// is considered to be a statement.)

package org.openlaszlo.sc;

import java.util.*;

import org.openlaszlo.sc.parser.*;
import org.openlaszlo.utils.LZUtils;


// The code generator dispatches a node whose class is named ASTName to
// a method visitName, passing the node, a context, and the node's
// children as arguments.  The context for a statement visitor is a
// TranslationContext, defined above.  The context for an expression
// visitor is a boolean value, that is true iff the value of the
// expression is used.  The return value of a statement visitor is
// ignored.  The return value of an expression visitor is true iff it
// generated code that did NOT leave a value on the stack.  (This is so
// that an expression visitor that ignores its context need do nothing
// special to indicate that it ignored it: the default return value of
// null signals this.)
//
// Methods of the form visitName are AST node visitors, and follow the
// protocol described above.  Methods of the form translateName are
// helper functions for the visitors, and have arbitrary parameter
// lists and return values.

// TODO: [2006-01-17 ptw] Remove some day
// Replace instruction subsequences by a BLOB instruction that
// represents the same bytes.  By default, the BLOB instructions are
// separated by PUSH's (which depend on the constant pool), and
// branches and targets (since they can't be resolved until the size of
// the PUSH instructions is known).  When noConstantPool=true, PUSH's
// are compiled against a null constant pool, and branches and targets
// are compiled, so the instructions combine to a single BLOB.
// public void combineInstructions(instrsIn, noConstantPool=false) {
//     instrsOut = [];
//     buffer = ByteBuffer.allocate(64000);
//     public void flush(instrsOut=instrsOut,buffer=buffer) {
//         if (buffer.position()) {
//             import jarray;
//             bytes = jarray.zeros(buffer.position(), "b");
//             buffer.flip();
//             buffer.get(bytes);
//             buffer.clear();
//             instrsOut.append(BLOB("bytes", bytes));
//     for (instr in instrsIn) {
//         if (noConstantPool || instr.isPush || instr.isLabel || instr.hasTarget) {
//             flush();
//             instrsOut.append(instr);
//         } else {
//             instr.writeBytes(buffer, null);
//     flush();
//     return instrsOut;
// }

public abstract class CommonGenerator extends GenericVisitor {

  Compiler.OptionMap options = new Compiler.OptionMap();
  String runtime;
  TranslationContext context = null;
  boolean debugVisit = false;
  SourceFileMap sources = new SourceFileMap();

  public TranslationContext makeTranslationContext(Object type, TranslationContext parent, String label) {
    return new TranslationContext(type, parent, label);
  }
  public TranslationContext makeTranslationContext(Object type, TranslationContext parent){
    return new TranslationContext(type, parent);
  }

  // Make Javascript globals 'known'
  Set<String> globalProperties = new HashSet<String>(Arrays.asList(
        "NaN", "Infinity", "undefined",
        "eval", "parseInt", "parseFloat", "isNaN", "isFinite",
        "decodeURI", "decodeURIComponent", "encodeURI", "encodeURIComponent",
        "Object", "Function", "Array", "String", "Boolean", "Number", "Date",
        "RegExp", "Error", "EvalError", "RangeError", "ReferenceError",
        "SyntaxError", "TypeError", "URIError",
        "Math",
        "lz",                   // our global namespace
        "canvas",               // implicitly defined on all user programs
        "escape", "unescape"    // global properties in ECMA-262-5 B.2
      ));

  Set<String> debugGlobals = new HashSet<String>(Arrays.asList(
        "$reportNotFunction",
        "$reportUndefinedObjectProperty",
        "$reportUndefinedMethod",
        "$reportException",
        "$reportUndefinedProperty",
        "$reportUndefinedVariable",
        "$reportSourceWarning",
        "Debug"
      ));

  // All class members of the JS 'Object' class.
  Set<String> objectClassDefined = new HashSet<String>(Arrays.asList(
        "toString",
        "toLocaleString",
        "valueOf",
        "hasOwnProperty",
        "isPrototypeOf",
        "propertyIsEnumerable",
        "constructor"
      ));

  // List of all globals, as we see them.
  Set<String> globals = new HashSet<String>(globalProperties);

  // Used for swf8 loadable libraries, to put stuff into _level0 namespace
  String globalprefix = "";

  public CommonGenerator() {
    setGeneratorOptions();
  }

  @Override
  public Compiler.OptionMap getOptions() {
    return options;
  }

  public TranslationContext getContext() {
    return context;
  }

  protected abstract void setRuntime(String runtime);

  @Override
  public void setOptions(Compiler.OptionMap options) {
    this.options = options;
    this.runtime = ((String)options.get(Compiler.RUNTIME)).intern();
    setRuntime(this.runtime);
    setGeneratorOptions();

    if (options.getBoolean(Compiler.DEBUG) || options.getBoolean(Compiler.DEBUG_SWF9)) {
      // Add debugger globals
      globals.addAll(debugGlobals);
    }
  }

  // does nothing here - may be overridden to set options
  public void setGeneratorOptions() {
  }


  // Give the generators an option to save the original
  // input source for debugging.
  public void setOriginalSource(String source) {
    // no action by default
  }

  // Options that don't affect code generation.  This is used to decide
  // what it's okay to cache across LFC build versions.  It's okay if
  // it's too small.
  static Set<String> NonCodeGenerationOptions = new HashSet<String>();
  static {
    NonCodeGenerationOptions.add(Compiler.CACHE_COMPILES);
    NonCodeGenerationOptions.add(Compiler.INSTR_STATS);
    NonCodeGenerationOptions.add(Compiler.PRINT_COMPILER_OPTIONS);
    NonCodeGenerationOptions.add(Compiler.PRINT_CONSTRAINTS);
    NonCodeGenerationOptions.add(Compiler.PROFILE_COMPILER);
    NonCodeGenerationOptions.add(Compiler.PROGRESS);
    NonCodeGenerationOptions.add(Compiler.RESOLVER);
    // These affect the default settings for the options above, but
    // do not themselves make a difference.
    NonCodeGenerationOptions.add(Compiler.DEBUG);
  }

  static Compiler.Parser fragmentParser = (new Compiler.Parser());

  static SimpleNode parseFragment(String code) {
    if (code == null || code.equals("\"\"") ) {
        code = "";
    }
    // Put the statements in a function body, so they are not parsed
    // as top-level directives.
    code =
      "(function () {" +
      "\n#pragma 'warnUndefinedReferences=false'\n" +
      "\n#file [CommonGenerator.parseFragment]\n#line 0\n" +
      code +
      "})()";
    // Extract Body from Statement(CallExpression(FunctionExpression(Arguments, Body)))
    try {
//      SimpleNode parse = (new Compiler.Parser()).parse(code);
      return fragmentParser.parse(code).get(0).get(0).get(0).get(1);
    } catch (ParseException e) {
      System.err.println("while compiling " + code);
      throw e;
    }
  }

  // TODO: [2007-08-20 ptw] Replace with Java 1.5 UUID
  private Boolean usePredictable = null;
  private Random rand = new Random();
  private int uuidCounter = 1;
  protected String UUID() {
    if (usePredictable == null) {
      usePredictable = options.getBoolean(Compiler.GENERATE_PREDICTABLE_TEMPS);
    }
    if (usePredictable.equals(Boolean.TRUE)) {
      return Integer.toString(uuidCounter++, Character.MAX_RADIX);
    }
    else {
      return Integer.toString(rand.nextInt(Integer.MAX_VALUE), Character.MAX_RADIX);
    }
  }

  //skip any passthrough nodes
  SimpleNode passThrough(SimpleNode node)
  {
    if (node instanceof Compiler.PassThroughNode)
      node = ((Compiler.PassThroughNode)node).realNode;
    return node;
  }

  @Override
  public Boolean evaluateCompileTimeConditional(SimpleNode node) {
    return CompileTimeEvaluator.evaluate(node, options);
  }

  private <K, V> String mapToString(Map<K, V> map) {
    StringBuilder result = new StringBuilder();
    result.append("{");
    TreeMap<K, V> sorted = new TreeMap<K, V>(map);
    for (Iterator<K> i = sorted.keySet().iterator(); i.hasNext(); ) {
      K key = i.next();
      result.append(key);
      result.append(": ");
      result.append(sorted.get(key));
      if (i.hasNext()) {
        result.append(", ");
      }
    }
    result.append("}");
    return result.toString();
  }

  String getCodeGenerationOptionsKey(List<String> ignore) {
    Map<String, Object> options = new HashMap<String, Object>(this.options);
    options.keySet().removeAll(NonCodeGenerationOptions);
    if (ignore != null) {
      options.keySet().removeAll(ignore);
    }
    return mapToString(options);
  }

  @Override
  public SimpleNode visitPragmaDirective(SimpleNode node, SimpleNode[] children) {
    String key = (String)((ASTLiteral)children[0]).getValue();
    String value = "true";
    int equals = key.indexOf('=');
    if (equals > 0) {
      value = key.substring(equals + 1);
      key = key.substring(0, equals);
    }
    if (LZUtils.equalsIgnoreCase("false", value) ||
        LZUtils.equalsIgnoreCase("true", value)) {
      options.putBoolean(key, value);
    } else {
      options.put(key, value);
    }
    return new ASTEmptyExpression(0);
  }

  public void errPassthroughProperty(SimpleNode node, String key, Object value, String errorType) {
    throw new ParseException(key + ": " + errorType +
                             " for #passthrough property in " +
                             node.filename + " (" + node.beginLine + ")");
  }

  public void checkPassthroughProperty(SimpleNode node, String key, Object value) {
    // by default, no parameters are allowed
    // this can be overridden by the various runtime generators
    errPassthroughProperty(node, key, value, "unknown key");
  }

  @Override
  public SimpleNode visitPassthroughDirective(SimpleNode node, SimpleNode[] children) {
    // just check parameters here.
    assert node instanceof ASTPassthroughDirective;
    Map<String, Boolean> props = ((ASTPassthroughDirective)node).getProperties();
    for (String key : props.keySet()) {
      Boolean value = props.get(key);

      checkPassthroughProperty(node, key, value);
    }
    return node;
  }

  // Flatten nested StatementList structures
  private List<SimpleNode> flatten(SimpleNode[] src) {
    List<SimpleNode> dst = new ArrayList<SimpleNode>();
    for (int i = 0; i < src.length; i++) {
      SimpleNode node = src[i];
      if (node instanceof ASTStatementList) {
        dst.addAll(flatten(node.getChildren()));
      } else {
        dst.add(node);
      }
    }
    return dst;
  }

  public class ClassDescriptor {
    boolean complete = false;
    String name;
    String[] superclasses;
    Set<String> instanceprops = new HashSet<String>();

    public ClassDescriptor (String name, SimpleNode superclasses, List<SimpleNode> instanceprops) {
      this.name = name;
      SimpleNode[] propArray = instanceprops.toArray(new SimpleNode[0]);
      // Only add prop names
      for (int i = 0, len = propArray.length; i < len; i += 2) {
        this.instanceprops.add((String) ((ASTLiteral)propArray[i]).getValue());
      }
      if (superclasses instanceof ASTIdentifier) {
        String[] sc = { ((ASTIdentifier)superclasses).getName() };
        this.superclasses = sc;
      } else if (superclasses instanceof ASTArrayLiteral) {
        List<String> classlist = new ArrayList<String>();
        SimpleNode[] supernodes = superclasses.getChildren();
        for (int i = 0, len = supernodes.length; i < len; i++) {
          String supername =  ((ASTIdentifier)supernodes[i]).getName();
          classlist.add(supername);
        }
        this.superclasses = classlist.toArray(new String[0]);
      } else {
        assert (superclasses == null) : "Invalid superclasses list";
//         System.err.println(name + " complete: "); // + this.instanceprops);
        complete = true;
      }
    }

    // Returns the set of instance properties.  If the set may be incomplete (due to forward
    // or unknown references), the 'complete' member variable is false.
    public Set<String> getInstanceProperties () {
      if (!complete) {
        Set<String> result = new HashSet<String>(instanceprops);
        boolean missing = false;
        // From least-specific to most-
        for (int i = superclasses.length - 1; i >= 0; i--) {
          ClassDescriptor superdesc = classDescriptors.get(superclasses[i]);
          if (superdesc == null) {
//             System.err.println("Forward reference: " + name + " -> " + superclasses[i]);
            missing = true;
          }
          else {
            Set<String> superprops = superdesc.getInstanceProperties();
            // superclass may be defined, but not complete...
            if (!superdesc.complete) {
              missing = true;
            }
            result.addAll(superprops);
          }
        }
//         System.err.println(name + " complete: "); // + instanceprops);
        complete = !missing;
        instanceprops = result;
      }
      return instanceprops;
    }

    // Return the set of incomplete hierarchies, each in string form, e.g.
    // E.g. [ "ThisClass->ParentClass->UnknownClass" ]
    // Can be useful for informational messages to help track
    // missing forward references, etc.
    public Set<String> incompleteSet() {
      Set<String> result = new HashSet<String>();
      for (int i = superclasses.length - 1; i >= 0; i--) {
        String supername = superclasses[i];
        ClassDescriptor superdesc = classDescriptors.get(supername);
        if (superdesc == null) {
          result.add(supername);
        }
        else {
          result.addAll(superdesc.incompleteSet());
        }
      }
      if (result.size() > 0) {
        Set<String> newresult = new HashSet<String>();
        for (String s : result) {
          newresult.add(name + "->" + s);
        }
        result = newresult;
      }
      return result;
    }

    @Override
    public String toString() {
      StringBuilder b = new StringBuilder();
      b.append(name);
      if (superclasses != null && superclasses.length > 0) {
        b.append(" extends ");
        for (int i=0; i<superclasses.length; i++) {
          if (i>0)
            b.append(',');
          b.append(superclasses[i]);
        }
      }
      if (complete)
        b.append(" (complete): [");
      else
        b.append(" (incomplete): [");
      boolean first = true;
      for (Object o : instanceprops) {
        if (!first)
          b.append(",");
        first = false;
        b.append(o);
      }
      b.append("]");
      return b.toString();
    }
  }

  Map<String, ClassDescriptor> classDescriptors = new HashMap<String, ClassDescriptor>();

  // NOTE [2009-10-06 ptw]  This really belongs in JS1Generator,
  // shared by CodeGenerator(swf8) and DHTMLGenerator.
  @Override
  public SimpleNode visitClassDefinition(SimpleNode node, SimpleNode[] children) {
//     System.err.println("enter visitClassDefinition: " +  (new ParseTreePrinter()).text(node));

    ASTIdentifier classormixin = (ASTIdentifier)children[0];
    ASTIdentifier classname = (ASTIdentifier)children[1];
    String classnameString = classname.getName();
    globals.add(classnameString);
    SimpleNode superclass = children[2];
    SimpleNode mixins = children[3];
    SimpleNode mixinsandsuper;
    boolean isReference = options.getBoolean(Compiler.REFERENCE_CLASS);

    if (isReference && !options.getBoolean(Compiler.REGISTER_REFERENCE_CLASSES)) {
      return new ASTEmptyExpression(0);
    }

      if (mixins instanceof ASTEmptyExpression) {
        if (superclass instanceof ASTEmptyExpression) {
          mixinsandsuper = null;
        } else {
          mixinsandsuper = superclass;
        }
      } else {
        mixinsandsuper = new ASTArrayLiteral(0);
        mixinsandsuper.setChildren(mixins.getChildren());
        if (! (superclass instanceof ASTEmptyExpression)) {
          mixinsandsuper.set(mixinsandsuper.size(), superclass);
        }
      }
    SimpleNode interfaces = children[4];
    if (interfaces instanceof ASTEmptyExpression) {
      interfaces = null;
    } else {
      // Transform from ASTMixinsList to ASTArrayLiteral
      SimpleNode ml = interfaces;
      interfaces = new ASTArrayLiteral(0);
      interfaces.setChildren(ml.getChildren());
    }

    SimpleNode[] dirs = Arrays.asList(children).subList(5, children.length).toArray(new SimpleNode[0]);
    List<SimpleNode> props = new ArrayList<SimpleNode>();
    List<SimpleNode> classProps = new ArrayList<SimpleNode>();
    List<SimpleNode> stmts = new ArrayList<SimpleNode>();
    // TODO: [2009-10-09 ptw] (LPP-5813) This context would be where
    // we would accumulate the class member names so that implicit
    // class references from methods can be resovled explicitly
    context = makeTranslationContext(ASTClassDefinition.class, context);
    try {
    translateClassDirectivesBlock(dirs, classnameString, props, classProps, stmts, TranslateHow.AS_PROPERTY_LIST);

    SimpleNode instanceProperties;
    if (props.isEmpty()) {
      instanceProperties = null;
    } else {
      instanceProperties = new ASTArrayLiteral(0);
      instanceProperties.setChildren(props.toArray(new SimpleNode[0]));
      instanceProperties.setLocation(node);
    }
    SimpleNode classProperties;
    if (classProps.isEmpty()) {
      classProperties = null;
    } else {
      classProperties = new ASTArrayLiteral(0);
      classProperties.setChildren(classProps.toArray(new SimpleNode[0]));
      classProperties.setLocation(node);
    }

    ClassDescriptor classdesc = new ClassDescriptor(classnameString, mixinsandsuper, props);
    // Store class descriptor
    classDescriptors.put(classnameString, classdesc);
    // Note class description in context
    context.setProperty(TranslationContext.CLASS_DESCRIPTION, classdesc);

    if (isReference) {

      // If this is a reference class, then we're done with it.
      // The point of having it was to create a class descriptor to locate it's symbols.
      // We've done that, so we can remove the class from the AST.

      return new ASTEmptyExpression(0);
    }
    Map<String, Object> map = new HashMap<String, Object>();
    String xtor = "class".equals(classormixin.getName()) ? "Class" : "Mixin";
    map.put("_1", classname);
    map.put("_2", instanceProperties);
    map.put("_3", mixinsandsuper);
    map.put("_4", classProperties);
    map.put("_5", interfaces);
    String args = "";
    if (interfaces != null) {
      args = ", _5";
    }
    if (classProperties != null) {
      args = ", _4" + args;
    } else if (args.length() > 0) {
      args = ", null" + args;
    }
    if (mixinsandsuper != null) {
      args = ", _3" + args;
    } else if (args.length() > 0) {
      args = ", null" + args;
    }
    if (instanceProperties != null) {
      args = ", _2" + args;
    } else if (args.length() > 0) {
      args = ", null" + args;
    }
    SimpleNode replNode = (new Compiler.Parser()).substitute(node,
                                                             xtor + ".make(" +
                                                             ScriptCompiler.quote(classnameString) +
                                                             args + ");",
                                                             map);
    if (! "".equals(globalprefix)) {
      // The classname, with possibly prefix of "_level0.", for SWF8
      // runtime loadable libraries
      ASTIdentifier globalclassname = new ASTIdentifier(globalprefix + classnameString);
      SimpleNode varNode = new ASTVariableDeclaration(0);
      varNode.set(0, globalclassname);
      varNode.set(1, replNode);
      replNode = varNode;
    }

    if (! stmts.isEmpty()) {
      SimpleNode statements = new ASTStatementList(0);
      statements.setChildren(stmts.toArray(new SimpleNode[0]));
      map.put("_6", statements);
      SimpleNode stmtNode = (new Compiler.Parser()).substitute(node,
                                                               "(function ($lzsc$c) { with($lzsc$c)"+
                                                               "with($lzsc$c.prototype) { _6 }})("+globalprefix+"_1)",
                                                               map);
      SimpleNode listNode = new ASTStatementList(0);
      listNode.set(0, replNode);
      listNode.set(1, stmtNode);
      replNode = listNode;
    }
    return visitStatement(replNode);
    } finally {
      context = context.parent;
    }
  }

  /**
   * If there are formal args that have initializers or indicate variable
   * arguments, add a preamble to the function to perform any needed
   * initialization to simulate the initialization.
   * For example:
   * <pre>
   *   function foo(w, x = null, y = null, ...z) {
   *       some statements
   *   }
   * </pre>
   * is rewritten as:
   * <pre>
   *   function foo(w, x, y) { 
   *      if (arguments.length < 2) { x = null; }
   *      if (arguments.length < 3) { y = null; }
   *      var z = new Array();
   *      for (__i=3; __i<arguments.length; __i++) {
   *          z.push(arguments[i]);
   *      }
   *      some statements
   *   }
   * </pre>
   * @param n function definition node
   * @return modified function definition node
   */
  public SimpleNode formalArgumentsTransformations(SimpleNode n) {
    // children are:
    //   (*) ASTIdentifier (name)      (*) may be omitted
    //       ASTFormalParameterList
    //       ASTStatement (or statement list)
    SimpleNode[] children = n.getChildren();
    int formalPos = 0;
    if (children.length > 0 && children[0] instanceof ASTIdentifier) {
      formalPos++;
    }
    assert (children.length == formalPos+2 && children[formalPos] instanceof ASTFormalParameterList)
      : "function has unexpected children";

    SimpleNode[] args = children[formalPos].getChildren();
    ASTIdentifier curid = null;
    List<SimpleNode> stmts = new ArrayList<SimpleNode>();
    List<SimpleNode> newargs = new ArrayList<SimpleNode>();
    Map<String, Object> map = new HashMap<String, Object>();
    
    // The argument list is a list of ASTIdentifiers and ASTFormalInitializer.
    // When a ASTFormalInitializer appears, it contains an initialization expression
    // that applies to the previous argument.  An ASTIdentifier can be marked
    // with an ellipsis, the grammar should enforce it is the last one.
    int argno = 0;
    String optional = null;
    String rest = null;
    // TODO: [2008-05-30 ptw] Does the parser ensure that args are
    // only permitted in required*, optional*, rest+ pattern?
    for (int i=0; i<args.length; i++) {
      if (args[i] instanceof ASTIdentifier) {
        curid = (ASTIdentifier)args[i];
        if (curid.getEllipsis()) {
          // Unfortunately, arguments is not an Array in some JS
          // runtimes, so we have to apply slice to it...
          rest = "var "+ curid.getName() +" = Array.prototype.slice.call(arguments, "+argno+");\n";
        }
        else {
          assert ((optional == null ||
                   (i+1 < args.length && args[i+1] instanceof ASTFormalInitializer))) :
            "Required argument after optional: " + (new ParseTreePrinter()).text(n);
          assert (rest == null) :
            "Required argument after rest: " + (new ParseTreePrinter()).text(n);
          newargs.add(args[i]);
        }
        argno++;
      }
      // TODO: [2008-05-30 ptw] This is a little bogus:  the parser
      // should be attaching the initializer as a property of the
      // parameter, not inserting it into the parameter list, IMO
      else if (args[i] instanceof ASTFormalInitializer) {
        assert curid != null : "ASTFormalInitializer appears first in list";
        assert rest == null : "Optional argument after rest: " +
          (new ParseTreePrinter()).text(n);
        SimpleNode initialValue = args[i].get(0);
        map.put("_" + argno, initialValue);
        if (optional == null) {
          optional = "switch (arguments.length) {\n";
        }
        optional += "  case " + (argno - 1) + ": " + curid.getName() + " = _" + argno +";\n";
      }
      else {
        throw new IllegalArgumentException("Unexpected item in argument list: " + args[i]);
      }
    }

    // any alterations needed?
    if (optional != null || rest != null) {
      String defaults = "";
      if (optional != null) {
        optional += "}\n";
        defaults += optional;
      }
      if (rest != null) {
        defaults += rest;
      }
      SimpleNode[] newNodes = (new Compiler.Parser()).substituteStmts(children[formalPos], defaults, map);
      stmts.addAll(flatten(newNodes));
      // newargs contains arguments without initializers
      children[formalPos].setChildren(newargs.toArray(new SimpleNode[0]));

      // Build a new statement list, consisting of new stmts for formal
      // initializations, followed by original statements.  Have to
      // keep original statments at the same level, in case there are
      // pragmas that apply to the function body!
      ASTStatementList oldstmt = (ASTStatementList)children[formalPos+1];
      stmts.addAll(Arrays.asList(oldstmt.getChildren()));
      SimpleNode newstmt = new ASTStatementList(0);
      newstmt.setChildren(stmts.toArray(new SimpleNode[0]));
      children[formalPos+1] = newstmt;
    }
    return n;
  }

  public static enum TranslateHow {
    /** Runtime initialization of properties and methods */
    AS_PROPERTY_LIST,

    /** Static definition of properties and methods */
    AS_CLASS,

    /** Only keep public methods, and omit the method implementation */
    AS_INTERFACE;
  }


  static final SimpleNode UNDEFINED = parseFragment("void 0").get(1).get(0);

  private static void addVarProp(SimpleNode v, List<SimpleNode> p) {
    assert v instanceof ASTVariableDeclaration : v.getClass();
    p.add(new ASTLiteral(((ASTIdentifier)v.get(0)).getName()));
    if (v.getChildren().length > 1) {
      p.add(v.get(1));
    } else {
      p.add(UNDEFINED);
    }
  }

  // translate the class directives according to the 'how' argument.
  // If how is AS_PROPERTY_LIST, function name/values and variable
  // name/initvalues are added to either classProps (for statics)
  // or props (for non-statics).  For AS_CLASS, these
  // are added to stmts, for AS_INTERFACE, the public ones are added
  // to stmts.  Any other thing in the directive list is added
  // to stmts, no matter what 'how' is.
  public void translateClassDirectivesBlock(SimpleNode[] dirs,
      String classnameString, List<SimpleNode> props, List<SimpleNode> classProps,
      List<SimpleNode> stmts, TranslateHow how) {
    dirs = flatten(dirs).toArray(new SimpleNode[0]);
    // Scope #pragma directives to block
    Compiler.OptionMap savedOptions = options;
    try {
      for (int i = 0; i < dirs.length; i++) {
        SimpleNode n = dirs[i];
        List<SimpleNode> p = props;

        // any modifiers, like 'static', 'final' are kept in mod.
        ASTModifiedDefinition mod = null;
        boolean ispublic = false;
        options = savedOptions.copy();

        // first handle modified definition and extract the 'modified' node
        if (n instanceof ASTModifiedDefinition) {
          assert (n.getChildren().length == 1);
          mod = (ASTModifiedDefinition)n;
          if (mod.isStatic()) {
            p = classProps;
          }
          ispublic = ASTModifiedDefinition.PUBLIC_ACCESS.equals(mod.getAccess());
          n = n.get(0);
          mod.verifyClassLevel(n);
        }

        if (n instanceof ASTMethodDeclaration) {
          SimpleNode[] c = n.getChildren();
          assert c.length == 3;
          ASTIdentifier fid = (ASTIdentifier)c[0];
          String fname = fid.getName();
          boolean isconstructor = classnameString.equals(fname);
          fid.setIsConstructor(isconstructor);

          if (how == TranslateHow.AS_PROPERTY_LIST) {
            // Transform constructor into '$lzsc$initialize' method
            if (isconstructor) {
              fname = "$lzsc$initialize";
              c[0] = new ASTIdentifier(fname);
            }
            p.add(new ASTLiteral(fname));
            p.add((mod != null) ? mod : n);
          } else if (how == TranslateHow.AS_INTERFACE) {
            if (!isconstructor && ispublic && (mod == null || ! mod.isStatic())) {
              stmts.add(n);
            }
          } else {
            assert how == TranslateHow.AS_CLASS;
            if (mod != null) {
              stmts.add(mod);
            } else {
              stmts.add(n);
            }
          }
        } else if (n instanceof ASTVariableStatement) {
          if (TranslateHow.AS_PROPERTY_LIST == how) {
            SimpleNode [] c = n.getChildren();
            for (int j = 0, clen = c.length; j < clen; j++) {
              SimpleNode v = c[j];
              if (v instanceof ASTVariableDeclarationList) {
                SimpleNode [] d = v.getChildren();
                for (int k = 0, dlen = d.length ; k < dlen; k++) {
                  addVarProp(d[k], p);
                }
              } else {
                addVarProp(v, p);
              }
            }
          } else if (TranslateHow.AS_INTERFACE == how) {
            if (ispublic && (mod == null || ! mod.isStatic())) {
              // TODO: generate getter/setter interface-pairs for public, instance-allocated properties
            } else {
              // omit non-public or class-allocated properties
            }
          } else if (TranslateHow.AS_CLASS == how) {
            // for classes, add modified definition if applicable
            stmts.add(mod != null ? mod : n);
          }
        } else if (n instanceof ASTClassDirectiveBlock) {
          translateClassDirectivesBlock(n.getChildren(), classnameString, props, classProps, stmts, how);
        } else if (n instanceof ASTClassIfDirective) {
          Boolean value = evaluateCompileTimeConditional(n.get(0));
          if (value == null) {
            stmts.add(n);
          } else if (value.booleanValue()) {
            SimpleNode clause = n.get(1);
            translateClassDirectivesBlock(clause.getChildren(), classnameString, props, classProps, stmts, how);
          } else if (n.size() > 2) {
            SimpleNode clause = n.get(2);
            translateClassDirectivesBlock(clause.getChildren(), classnameString, props, classProps, stmts, how);
          }
        } else if (n instanceof ASTPragmaDirective) {
          visitPragmaDirective(n, n.getChildren());
        } else if (n instanceof ASTPassthroughDirective) {
          visitPassthroughDirective(n, n.getChildren());
          stmts.add(n);
        } else {
          if (TranslateHow.AS_CLASS == how) {
            // for classes, add modified definition if applicable
            stmts.add(mod != null ? mod : n);
          } else if (TranslateHow.AS_PROPERTY_LIST == how) {
            // property lists don't accept modified definitions, so use the actual node
            stmts.add(n);
          } else if (TranslateHow.AS_INTERFACE == how) {
            // interfaces can only have functions and variables, those are handled above
          }
        }
      }
    }
    finally {
      options = savedOptions;
    }
  }

  public void translateFormalParameters(SimpleNode params)
  {
    // Warn about any formal param initializers that are not '= null'
    int paramCount = 0;
    for (int i = 0, len = params.size(); i < len; i++) {
      SimpleNode param = passThrough(params.get(i));
      if (param instanceof ASTIdentifier) {
        paramCount++;
      }
      else if (!(param instanceof ASTFormalInitializer) || param.size() != 1) {
        System.err.println("Warning: unknown formal parameter in " +
                           param.filename + " (" + param.beginLine + ")");
      }
    }

    if (!options.getBoolean(Compiler.PASSTHROUGH_FORMAL_INITIALIZERS)) {
      // Remove any initializers seen.
      SimpleNode[] newParams = new SimpleNode[paramCount];
      paramCount = 0;
      for (int i = 0, len = params.size(); i < len; i++) {
        SimpleNode param = passThrough(params.get(i));
        if (param instanceof ASTIdentifier) {
          newParams[paramCount++] = param;
        }
      }
      params.setChildren(newParams);
    }
  }

  protected SimpleNode translateForEachStatement (SimpleNode node, SimpleNode[] children) {
    String pattern = "";
    pattern += "for (var _3 in _4 = _1) {";
    pattern += "var _4; _0 = _4[_3];";
    pattern += "_2;";
    pattern += "}";

    Map<String, Object> keys = new HashMap<String, Object>();
    keys.put("_0", children[0]);
    keys.put("_1", children[1]);
    keys.put("_2", children[2]);
    keys.put("_3", new ASTIdentifier("$" + UUID()));
    keys.put("_4", new ASTIdentifier("$" + UUID()));

    SimpleNode replNode = (new Compiler.Parser()).substituteStmt(node, pattern, keys);
    return visitStatement(replNode);
  }

  protected SimpleNode translateForEachVarStatement (SimpleNode node, SimpleNode[] children) {
      String pattern = "";
      if (children[1] instanceof ASTEmptyExpression) {
        pattern += "for (var _4 in _5 = _2) {";
      } else {
        pattern += "for (var _4 in _0 = _1, _5 = _2) {";
      }
      pattern += "var _5, _0 = _5[_4];";
      pattern += "_3;";
      pattern += "}";

      Map<String, Object> keys = new HashMap<String, Object>();
      keys.put("_0", children[0]);
      keys.put("_1", children[1]);
      keys.put("_2", children[2]);
      keys.put("_3", children[3]);
      keys.put("_4", new ASTIdentifier("$" + UUID()));
      keys.put("_5", new ASTIdentifier("$" + UUID()));

      SimpleNode replNode = (new Compiler.Parser()).substituteStmt(node, pattern, keys);
      return visitStatement(replNode);
  }

  //
  // Statements
  //
  @Override
  public SimpleNode visitStatement(SimpleNode node) {
    return visitStatement(node, node.getChildren());
  }

  @Override
  public SimpleNode visitStatement(SimpleNode node, SimpleNode[] children) {
    /* This function, unlike the other statement visitors, can be
       applied to any statement node, so it dispatches based on the
       node's class. */
    assert context instanceof TranslationContext;
    showStats(node);
    SimpleNode newNode = node;

    if (this.debugVisit) {
      System.err.println("visitStatement: " + node.getClass());
    }

    // Are we doing OO programming yet?
    if (node instanceof ASTPragmaDirective) {
      newNode = visitPragmaDirective(node, children);
    }
    else if (node instanceof ASTPassthroughDirective) {
      newNode = visitPassthroughDirective(node, children);
    }
    else if (node instanceof ASTClassDefinition) {
      newNode = visitClassDefinition(node, children);
    }
    else if (node instanceof ASTStatementList) {
      newNode = visitStatementList(node, children);
    }
    else if (node instanceof ASTDirectiveBlock ||
             node instanceof ASTClassDirectiveBlock) {
      newNode = visitDirectiveBlock(node, children);
    }
    else if (node instanceof ASTFunctionDeclaration) {
      newNode = visitFunctionDeclaration(node, children);
    }
    else if (node instanceof ASTMethodDeclaration) {
      newNode = visitMethodDeclaration(node, children);
    }
    else if (node instanceof ASTStatement) {
      // an empty statement, introduced by an extra ";", has no children
      if (children.length > 0) {
        children[0] = visitStatement(children[0], children[0].getChildren());
      } else {
        newNode = new ASTEmptyExpression(0);
      }
    }
    else if (node instanceof ASTLabeledStatement) {
      newNode = visitLabeledStatement(node, children);
    }
    else if (node instanceof ASTVariableDeclaration) {
      newNode = visitVariableDeclaration(node, children);
    }
    else if (node instanceof ASTVariableDeclarationList) {
      newNode = visitVariableDeclarationList(node, children);
    }
    else if (node instanceof ASTVariableStatement) {
      newNode = visitVariableStatement(node, children);
    }
    else if (node instanceof ASTIfStatement) {
      newNode = visitIfStatement(node, children);
    }
    else if (node instanceof ASTIfDirective ||
             node instanceof ASTClassIfDirective) {
      newNode = visitIfDirective(node, children);
    }
    else if (node instanceof ASTWhileStatement) {
      newNode = visitWhileStatement(node, children);
    }
    else if (node instanceof ASTDoWhileStatement) {
      newNode = visitDoWhileStatement(node, children);
    }
    else if (node instanceof ASTForStatement) {
      newNode = visitForStatement(node, children);
    }
    else if (node instanceof ASTForVarStatement) {
      newNode = visitForVarStatement(node, children);
    }
    else if (node instanceof ASTForInStatement) {
      newNode = visitForInStatement(node, children);
    }
    else if (node instanceof ASTForVarInStatement) {
      newNode = visitForVarInStatement(node, children);
    }
    else if (node instanceof ASTForEachStatement) {
      newNode = visitForEachStatement(node, children);
    }
    else if (node instanceof ASTForEachVarStatement) {
      newNode = visitForEachVarStatement(node, children);
    }
    else if (node instanceof ASTContinueStatement) {
      newNode = visitContinueStatement(node, children);
    }
    else if (node instanceof ASTBreakStatement) {
      newNode = visitBreakStatement(node, children);
    }
    else if (node instanceof ASTReturnStatement) {
      newNode = visitReturnStatement(node, children);
    }
    else if (node instanceof ASTWithStatement) {
      newNode = visitWithStatement(node, children);
    }
    else if (node instanceof ASTTryStatement) {
      newNode = visitTryStatement(node, children);
    }
    else if (node instanceof ASTThrowStatement) {
      newNode = visitThrowStatement(node, children);
    }
    else if (node instanceof ASTDebuggerStatement) {
      newNode = visitDebuggerStatement(node, children);
    }
    else if (node instanceof ASTSwitchStatement) {
      newNode = visitSwitchStatement(node, children);
    }
    else if (node instanceof ASTModifiedDefinition) {
      newNode = visitModifiedDefinition(node, children);
    }
    else if (node instanceof Compiler.PassThroughNode) {
      newNode = node;
    } else {
      // Not a statement, must be an expression
      newNode = visitExpression(node, false);
    }
    // Check for elided statments
    if (newNode == null) {
      newNode = new ASTEmptyExpression(0);
    }
    if (this.debugVisit) {
      if (! newNode.equals(node)) {
        System.err.println("statement: " + node + " -> " + newNode);
      }
    }
    return newNode;
  }

  @Override
  public SimpleNode visitStatementList(SimpleNode node, SimpleNode[] stmts) {
    int i = 0;
    // ensure dynamic extent of #pragma in a block
    Compiler.OptionMap prevOptions = options;
    Compiler.OptionMap newOptions = options.copy();
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      options = newOptions;
      while (i < stmts.length) {
        SimpleNode stmt = stmts[i];
        stmts[i] = visitStatement(stmt);
        i += 1;
      }
    }
    finally {
      options = prevOptions;
    }
    return node;
  }

  // An ASTModifiedDefinition can appear as a statement (modifying
  // class and function definitions) or as an expression (modifying
  // function expressions)
  @Override
  public SimpleNode visitModifiedDefinition(SimpleNode node, SimpleNode[] children) {
    // Modifiers, like 'final', are ignored unless this is handled
    // by the runtime.

    assert children.length == 1;
    SimpleNode child = children[0];

    ((ASTModifiedDefinition)node).verifyTopLevel(child);

    return visitStatement(child);
  }

  @Override
  public SimpleNode visitLabeledStatement(SimpleNode node, SimpleNode[] children) {
    ASTIdentifier name = (ASTIdentifier)children[0];
    SimpleNode stmt = children[1];
    // TODO: [2003-04-15 ptw] bind context slot macro
    try {
      context = makeTranslationContext(ASTLabeledStatement.class, context, name.getName());
      // TODO: [2002 ows] throw semantic error for duplicate label
      children[1] = visitStatement(stmt);
      return node;
    }
    finally {
      context = context.parent;
    }
  }

  @Override
  public SimpleNode visitForEachStatement(SimpleNode node, SimpleNode[] children) {
    // translate into for-in for runtimes without e4x support
    return translateForEachStatement(node, children);
  }

  @Override
  public SimpleNode visitForEachVarStatement(SimpleNode node, SimpleNode[] children) {
    // translate into for-in for runtimes without e4x support
    return translateForEachVarStatement(node, children);
  }

  static class DoubleCollator implements Comparator<Double> {
    public boolean equals(Object o1, Object o2) {
      return ((Double)o1).equals((Double)o2);
    }

    public int compare(Double o1, Double o2) {
      return (o1).compareTo(o2);
    }
  }

  public SimpleNode translateSuperCallExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children) {
    assert children.length == 3;
    SimpleNode fname = children[0];
    SimpleNode callapply = children[1];
    SimpleNode args = children[2];
    String name;
    String ca = null;
    String pattern = "((arguments.callee['$superclass']&&arguments.callee.$superclass.prototype[_1])||this.nextMethod(arguments.callee, _1)).call(this, _2)";
    if (fname instanceof ASTEmptyExpression) {
      // super with no selector is the constructor, which will be
      // renamed to $lzsc$initialize in translateClassDirective to
      // mesh with lfc/compiler/Class.lzs framework
      name = "$lzsc$initialize";
    } else {
      name = ((ASTIdentifier)fname).getName();
    }
    Map<String, Object> map = new HashMap<String, Object>();

    // Transform `super.setAttribute` to a setter method call
    SimpleNode sargs[] = args.getChildren();
    if ("setAttribute".equals(name) && (sargs.length == 2)) {
      SimpleNode property = sargs[0];
      SimpleNode value = sargs[1];
      if (property instanceof ASTLiteral) {
        name = "$lzc$set_" + (String)((ASTLiteral)property).getValue();
        map.put("_1", new ASTLiteral(name));
      } else {
        pattern = "this.nextMethod(arguments.callee, '$lzc$set_' + _1).call(this, _2)";
        map.put("_1", property);
      }
      map.put("_2", value);
    } else {
      map.put("_1", new ASTLiteral(name));
      map.put("_2", new Compiler.Splice(args.getChildren()));
    }

    if (callapply instanceof ASTIdentifier) {
      ca = ((ASTIdentifier)callapply).getName();
    }
    // FIXME: [2005-03-09 ptw] (LPP-98 "Compiler source-source
    // transformations should be in separate phase") This should be
    // in a phase before the compiler, so that register analysis
    // sees it.  [Or this should be eliminated altogether and we
    // should use swf7's real super call, but that will mean we
    // have to solve the __proto__ vs. super in constructor
    // problem.]
    if (ca == null) {
      ;
    } else if ("call".equals(ca)) {
      pattern = "((arguments.callee['$superclass']&&arguments.callee.$superclass.prototype[_1])||this.nextMethod(arguments.callee, _1)).call(_2)";
    } else if ("apply".equals(ca)) {
      pattern = "((arguments.callee['$superclass']&&arguments.callee.$superclass.prototype[_1])||this.nextMethod(arguments.callee, _1)).apply(_2)";
    } else {
      assert false: "Unhandled super call " + ca;
    }
    SimpleNode n = (new Compiler.Parser()).substitute(node, pattern, map);
    return n;
  }

  // Hook for any special processing of globals, like noting them in
  // the known list, or emitting a declaration
  void addGlobalVar(String name, String type, String initializer) {
    globals.add(name);
  }

  @Override
  public SimpleNode visitExpression(SimpleNode node, boolean isReferenced) {
    // Are we doing OO programming yet?
    SimpleNode[] children = node.getChildren();

    // These should really only be in JS1-based platforms...
    if (node instanceof ASTMethodDeclaration) {
      // When a class is transformed to a plist, it's methods appear
      // in an expression context
      return visitMethodDeclarationAsExpression(node, isReferenced, children);
    }
    else if (node instanceof ASTModifiedDefinition) {
      return visitModifiedDefinitionAsExpression(node, isReferenced, children);
    }
    else if (node instanceof Compiler.PassThroughNode) {
      return node;
    }
    return super.visitExpression(node, isReferenced);
  }

  // These should really only be in JS1-based platforms...
  abstract SimpleNode visitMethodDeclarationAsExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);
  abstract SimpleNode visitModifiedDefinitionAsExpression(SimpleNode node, boolean isReferenced, SimpleNode[] children);

  /** Collect runtime statistics at this point in the program if asked for.
   */
  abstract void showStats(SimpleNode node);

  boolean isStatic(SimpleNode node) {
    SimpleNode parent = node.getParent();
    return (parent instanceof ASTModifiedDefinition &&
            ((ASTModifiedDefinition)parent).isStatic());
  }
}

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
