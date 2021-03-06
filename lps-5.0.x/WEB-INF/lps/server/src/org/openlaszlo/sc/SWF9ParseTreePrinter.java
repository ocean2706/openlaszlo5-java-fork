/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2007-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

//
// Parse Tree Printer for SWF9
//

package org.openlaszlo.sc;

import java.util.List;

import org.openlaszlo.compiler.ClassModel;
import org.openlaszlo.sc.Compiler.Ops;
import org.openlaszlo.sc.parser.*;

// This class supports the Javascript translator
public class SWF9ParseTreePrinter extends ParseTreePrinter {

  /** We collect certain parts of translation units
   * into the 'top level', appearing outside a class.
   */
  public final static int TOP_LEVEL_STREAM = 1;

  /** We collect certain parts of translation units
   * into the class level, appearing inside a class.
   */
  public final static int CLASS_LEVEL_STREAM = 2;

  /** We collect certain parts of translation units
   * to appear in the constructor for the main class only.
   */
  public final static int MAIN_CONSTRUCTOR_STREAM = 3;

  /** Configuration options for an instance of the SWF9ParseTreePrinter.
   * Nicer than a mess of constructor parameters.
   */
  public static class Config extends ParseTreePrinter.Config {
    // What are we compiling? 
    public static final String APP = "application";   // A user application
    public static final String LFCLIB = "lfclib"; // The LFC
    public static final String LZOLIB = "lzolib"; // A user lzo library
    public static final String SNIPPET = "snippet"; // A runtime loadable library

    String compileType = APP;
    public Config setCompileType(String value) { compileType = value; return this; }

    String mainClassName = null;
    public Config setMainClassName(String value) { mainClassName = value; return this; }

    boolean forcePublicMembers = false;
    public Config setForcePublicMembers(boolean value) { forcePublicMembers = value; return this; }
  }
  Config config;


  /** State variable that is true while we are in a mixin */
  private boolean inmixin = false;

  /** State variable that is true while we are in a method */
  private boolean inmethod = false;

  /** State variable that is true while we are in a class */
  private boolean inclass = false;

  // Adjust the known operator names we output to include
  // ones that we know about.
  static {
    OperatorNames[Ops.CAST] = "as";
    OperatorNames[Ops.IS] = "is";
  }
  
  public SWF9ParseTreePrinter() {
    this(new Config());
  }
  
  public SWF9ParseTreePrinter(boolean compress) {
    this((Config)new Config().setCompress(compress));
  }
  
  public SWF9ParseTreePrinter(boolean compress, boolean obfuscate) {
    this((Config)new Config().setCompress(compress).setObfuscate(obfuscate));
  }

  public SWF9ParseTreePrinter(Config config) {
    // never compress or obfuscate
    super(config.setCompress(false).setObfuscate(false));
    this.config = (Config)super.config;
  }

  /**
   * Intercept parent.
   *
   * We need to know if we are inside a mixin, since it changes how we
   * emit its contents.  This must be noticed in previsit rather than in
   * visitClassDefinition because it's already too late there - the
   * unparser works bottom up.  At the moment we do not allow nested
   * classes/mixins, so we can keep a single state variable.
   */
  @Override
  public SimpleNode previsit(SimpleNode node) {
    if (node instanceof ASTClassDefinition) {
      ASTIdentifier id = (ASTIdentifier)(node.getChildren()[0]);
      if (!"class".equals(id.getName())) {
        this.inmixin = true;
      }
      else {
        this.inclass = true;
      }
    }
    return super.previsit(node);
  }

  // Override parent class.
  //
  // We need a translation unit (class) to put all the executable
  // statements appearing outside the class definitions - initially
  // these are put into the 'default' translation unit.  For building an
  // application or the LFC, we have a 'main' class that must
  // be present to accept these statements.
  //
  @Override
  public List<TranslationUnit> makeTranslationUnits(SimpleNode node, SourceFileMap sources,
                                       TranslationUnit defaultTunit) {
    List<TranslationUnit> result = super.makeTranslationUnits(node, sources, defaultTunit);
    TranslationUnit mainTunit = defaultTunit;
    for (TranslationUnit tunit : result) {
      if (tunit.getName().equals(config.mainClassName)) {
        mainTunit = tunit;
      }
    }

    assert (defaultTunit != null);

    // If the main class is not present, we'll need to revisit
    // our assumptions about where we put top level statements.
    if (mainTunit != null) {
      mainTunit.setMainTranslationUnit(true);
      if (config.compileType == Config.LFCLIB) {
        mainTunit.setStreamObject(CLASS_LEVEL_STREAM, defaultTunit);
      } else {
        mainTunit.setStreamObject(MAIN_CONSTRUCTOR_STREAM, defaultTunit);
      }
    }
    else {
      throw new CompilerError("Could not find application main or library main class");
    }

    return result;
  }


  public static final String MULTILINE_COMMENT_BEGIN = "/*";
  public static final String MULTILINE_COMMENT_END = "*/";

  // push modifiers string next to the following keywords in the code.
  // modifiers, like 'static' or 'dynamic' normally appear before
  // keywords like 'function', 'class' or 'var'.  However, after
  // a function/class is processed, the resulting string may
  // contain comments, newlines or annotations.
  //
  // The annotation in fact may state that the class is beginning
  // at this point.  In order to get the modifier string into the right
  // file, we need to push it past the annotation.
  //
  // Also keywords like 'static' must appear on the same line as
  // 'function' so as not to tickle a bug in the third party
  // compiler.
  //
  // Moving the modifier next to the next piece of real code
  // resolves all these difficulties.
  protected String prependMods(String code, String mods)
  {
    if (mods.length() == 0)
      return code;

    mods += " ";
    // simple 
    int pos = 0;
    pos = skipWhitespace(code, pos);
    if (code.substring(pos).startsWith(MULTILINE_COMMENT_BEGIN)) {
      pos = code.indexOf(MULTILINE_COMMENT_END, pos);
      if (pos < 0)
        return mods + code;
      pos += MULTILINE_COMMENT_END.length();
      pos = skipWhitespace(code, pos);
    }
    return code.substring(0, pos) + mods + code.substring(pos);
  }

  public static int skipWhitespace(String s, int pos)
  {
    int len = s.length();
    while (pos < len) {
      char ch = s.charAt(pos);
      if (ANNOTATE_MARKER == ch) {
        pos++;
        while (pos < len) {
          if (s.charAt(pos) == ANNOTATE_MARKER) {
            pos++;
            break;
          }
          pos++;
        }
      }
      else if (Character.isWhitespace(ch)) {
        pos++;
      }
      else
        break;
    }
    return pos;
  }

  @Override
  public String visitModifiedDefinition(SimpleNode node, String[] children) {
    SimpleNode subnode = node.getChildren()[0];
    boolean forcePublic =
      config.forcePublicMembers && !inmethod &&
      !(subnode instanceof ASTEmptyExpression) &&
      // top level functions cannot be marked public
      (inclass || inmixin || !(subnode instanceof ASTFunctionDeclaration));

    ASTModifiedDefinition defnode = (ASTModifiedDefinition)node;

    // If this is an 'interface' declaration, we need to remove any 'dynamic' modifier,
    // or we'll get a flex compile error
    if (subnode instanceof ASTClassDefinition) {
      ASTIdentifier id = (ASTIdentifier)(subnode.getChildren()[0]);
      if ("mixin".equals(id.getName())) {
        defnode.setDynamic(false);
      }
    }

    String mods = defnode.toJavascriptString(forcePublic);

    return prependMods(children[0], mods);
  }

  // We have to translate types in the `lz` "namespace" to their
  // actual type
  @Override
  public String lzTypeToPlatformType(String type) {
    if ((type != null) && type.startsWith("lz.")) {
      return ClassModel.LZXTag2JSClass(type.substring(3));
    } else {
      return type;
    }
  }

  @Override
  public String visitClassDefinition(SimpleNode node, String[] children) {
    String classnm = unannotate(children[1]);
    StringBuilder sb = new StringBuilder();

    // Everything inserted by #passthrough (toplevel:true) goes here.
    sb.append(annotateInsertStream(TOP_LEVEL_STREAM));
    String classtype = unannotate(children[0]);
    if ("class".equals(classtype)) {
      sb.append("class");
    }
    else if ("interface".equals(classtype) || "mixin".equals(classtype)) {
      sb.append("interface");
    }
    else {
      throw new CompilerError("Internal error: bad classtype: " + classtype);
    }
    sb.append(SPACE + classnm + SPACE);
    if (unannotate(children[2]).length() > 0)
      sb.append("extends" + SPACE + children[2] + SPACE);

    // The meaning of children[3] is 'with', which has already be
    // processed by SWF9Generator
    assert unannotate(children[3]).length() == 0;

    // The meaning of children[4] is 'implements'
    if (unannotate(children[4]).length() > 0) {
      sb.append("implements" + SPACE + children[4] + SPACE);
    }

    sb.append("{\n");
    sb.append(annotateInsertStream(CLASS_LEVEL_STREAM));

    if (classnm.equals(config.mainClassName) && config.compileType != Config.LFCLIB) {
      sb.append("override public function runToplevelDefinitions () {");
      sb.append(annotateInsertStream(MAIN_CONSTRUCTOR_STREAM));
      sb.append("}\n");
    }

    for (int i=5; i<children.length; i++) {
      sb.append(children[i]);
    }
    sb.append("}\n");

    // Note: assumes no nested mixins/classes
    inmixin = false;
    inclass = false;

    return annotateClass(classnm, sb.toString());
  }

  // don't emit body of methods for mixins
  @Override
  public String visitFunctionDeclaration(SimpleNode node, String[] children) {
    inmethod = true;
    try {
      return doFunctionDeclaration(node, children, true, inmixin);
    }
    finally {
      inmethod = false;
    }
  }

  // TODO: [2009-03-23 dda] Should not need to comment the #pragma as they
  // should not normally appear in emitted code.  But LPP-7824 requires it
  // for now.
  @Override
  public String visitPragmaDirective(SimpleNode node, String[] children) {
    return "// #pragma " + children[0] + "\n";
  }

  @Override
  public String visitPassthroughDirective(SimpleNode node, String[] children) {
    ASTPassthroughDirective passthrough = (ASTPassthroughDirective)node;
    String text = passthrough.getText();
    if (passthrough.getBoolean(SWF9Generator.PASSTHROUGH_TOPLEVEL))
      text = annotateStream(TOP_LEVEL_STREAM, text);
    return text;
  }

  @Override
  public String visitPropertyValueReference(SimpleNode node, String[] children) {
    // These have prec of 0 even though they don't have ops
    int thisPrec = 0;
    children[0] = maybeAddParens(thisPrec, node.get(0), children[0], true);
    // AS3 seems to get confused about COMMA in [], perhaps they plan
    // to add multi-dimensional arrays some day...
    thisPrec = prec(Ops.COMMA);
    children[1] = maybeAddParens(thisPrec, node.get(1), children[1]);
    if (node.get(0) instanceof ASTArrayLiteral) {
      // add parenthesis around array literal
      return "(" + children[0] + ")[" + children[1] + "]";
    } else {
      return children[0] + "[" + children[1] + "]";
    }
  }

  @Override
  public String visitPropertyIdentifierReference(SimpleNode node, String[] children) {
    // These have prec of 0 even though they don't have ops
    int thisPrec = 0;
    for (int i = 0; i < children.length; i++) {
      children[i] = maybeAddParens(thisPrec, node.get(i), children[i], true);
    }
    assert node.get(1) instanceof ASTIdentifier;
    if (node.get(0) instanceof ASTArrayLiteral) {
      // add parenthesis around array literal
      return "(" + children[0] + ")." + children[1];
    } else {
      return children[0] + "." + children[1];
    }
  }

  @Override
  public String visitDebuggerStatement(SimpleNode node, String[] children) {
    // already processed by SWF9Generator
    throw new CompilerError("found unexpected 'debugger' statement");
  }
}
  
