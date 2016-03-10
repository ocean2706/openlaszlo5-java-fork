/* -*- mode: Java; c-basic-offset: 2; -*- */
/***
 * DHTMLCompiler.java
 * Author: Henry Minsky
 * Description: JavaScript -> DHTML compiler
 */

package org.openlaszlo.sc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Properties;

public class DHTMLCompiler extends Compiler {

  @SuppressWarnings("unchecked")
  public DHTMLCompiler (Properties initialOptions) {
    // just for the effect
    this((Map<String, Object>) ((Map<?, ?>) initialOptions));
  }

  public DHTMLCompiler (Map<String, Object> initialOptions) {
    super(initialOptions);
  }

  ScriptCompilerInfo mInfo;

  /** temp file to accumulate script output */
  File mAppFile;
  PrintWriter mOut;

  static int objfileCounter = 1;

  @Override
  public void startApp() {
    super.startApp();
    profiler = new Profiler();
    cg = new JavascriptGenerator();
    Compiler.OptionMap newOptions = options.copy();
    newOptions.putBoolean(Compiler.REGISTER_REFERENCE_CLASSES, true);
    cg.setOptions(newOptions);
    boolean compress = (! options.getBoolean(NAME_FUNCTIONS));
    boolean obfuscate = options.getBoolean(OBFUSCATE);

    mInfo = (ScriptCompilerInfo) options.get(Compiler.COMPILER_INFO);

    if (mInfo == null) {
      mInfo = new ScriptCompilerInfo();
    }

    // Create temp app output file
    File workdir = SWF9External.createCompilationWorkDir(SWF9ParseTreePrinter.Config.APP, mInfo);
    mAppFile = new File (workdir.getPath() + File.separator + "obj"+(objfileCounter++) + ".js");

    // JavascriptGenerator will append output from each compileBlock() call to this file.
    try {
      if (mAppFile.exists()) {
        mAppFile.delete();
      }
      mOut = new PrintWriter(new FileWriter(mAppFile));
      ((JavascriptGenerator)cg).setupParseTreePrinter(compress, obfuscate, mOut);
    } catch (IOException e) {
      throw(new CompilerException(e.toString()));
    }
  }

  /** Copy text from input stream direct to output file */
  public void copyRawFromInputStream(InputStream is)
    throws IOException {
    ((JavascriptGenerator)cg).copyRawFromInputStream(is);
  }

  // Returns byte stream of compiled JS file
  public InputStream finishApp() {
    try {
      ((JavascriptGenerator)cg).finish();
      mOut.close();
      return new FileInputStream(mAppFile);
    } catch (IOException e) {
      throw(new CompilerException(e.toString()));
    }
  }

}

/**
 * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
