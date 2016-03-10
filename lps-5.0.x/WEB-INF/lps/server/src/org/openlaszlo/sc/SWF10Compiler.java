/* -*- mode: Java; c-basic-offset: 2; -*- */

/***
 * Compiler.java
 * Author: Oliver Steele, P T Withington
 * Description: JavaScript -> SWF bytecode compiler
 */

package org.openlaszlo.sc;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.openlaszlo.sc.parser.SimpleNode;


public class SWF10Compiler extends Compiler {

  private Set<String> externalClassNames = new HashSet<String>();

  @SuppressWarnings("unchecked")
  public SWF10Compiler (Properties initialOptions) {
    // just for the effect
    this((Map<String, Object>) ((Map<?, ?>) initialOptions));
  }

  public SWF10Compiler (Map<String, Object> initialOptions) {
    super(initialOptions);
  }

  // Set up state for a swf10 compiler, so that multiple blocks of script
  // can be passed in to compile.
  // Usage:
  // [1] startSWF10App();
  // [2] Call compileSWF10Block for each chunk of script
  // [3] Call finishSWF10App(), which will
  //     + finish the generation of as3
  //     + run flex compiler
  //     + pass back InputStream to compile app .swf file 
  // 

  @Override
  public void startApp() {
    super.startApp();
    profiler = new Profiler();
    cg = new SWF9Generator();
    cg.setOptions(options);

    boolean compress = (! options.getBoolean(NAME_FUNCTIONS));
    boolean obfuscate = options.getBoolean(OBFUSCATE);
    ((SWF9Generator)cg).setupParseTreePrinter(compress, obfuscate);

    // This creates the main app boilerplate code.
    compileBlock(cg.preProcess(""));
  }

  public void setLZOLibraries(Set<File> libs) {
    ((SWF9Generator)cg).setLZOLibraries(libs);
  }

  // Returns byte stream of compiled app.swf file
  public InputStream finishApp() {
    ((SWF9Generator)cg).makeInterstitials(mParser.parse(""));
    ((SWF9Generator)cg).writeGlobalTUnitsToAS3();
    ((SWF9Generator)cg).writeMainTranslationUnit();
    return ((SWF9Generator)cg).callFlexCompiler(externalClassNames);
  }

  public void addClassModel(String script, String classname) {
    externalClassNames.add(classname);
    compileBlock(script);
  }

  @Override
  public void compileBlock(String source) {
    try {

      cg.setOriginalSource(source);
      String srcDumpFile = (String)options.get(DUMP_SRC_INPUT);
      if (srcDumpFile != null) {
        String newname = emitFile(srcDumpFile, source);
        System.err.println("Created " + newname);
      }

      profiler.enter("parse");
      // Can we reuse the Parser object? 
      SimpleNode program = mParser.parse(source);

      String astInputFile = (String)options.get(DUMP_AST_INPUT);
      if (astInputFile != null) {
        String newname = emitFile(astInputFile, program);
        System.err.println("Created " + newname);
      }
      //       profiler.phase("transform");
      //       SimpleNode transformed = xformer.transform(program);
      profiler.phase("generate");
      SimpleNode translated = ((SWF9Generator)cg).translateBlock(program /* transformed */);
      program = null;
      String astOutputFile = (String)options.get(DUMP_AST_OUTPUT);
      if (astOutputFile != null) {
        String newname = emitFile(astOutputFile, translated);
        System.err.println("Created " + newname);
      }

      cg.compileBlock(translated);
      translated = null;

      profiler.exit();
      if (options.getBoolean(PROFILE_COMPILER)) {
        profiler.pprint();
        System.err.println();
      }
      if (options.getBoolean(PROGRESS)) {
        System.err.println("done.");
      }
    }
    catch (CompilerImplementationError e) {
      String ellipses = source.trim().length() > 80 ? "..." : "";
      System.err.println("while compiling " +  source.trim().substring(0, 80) + ellipses);
      throw(e);
    }
    catch (CompilerError e) {
      throw(new CompilerException(e.toString()));
    }
  }


}


/**
 * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
