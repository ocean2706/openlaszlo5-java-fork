/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

/**
 * Library output
 *
 * @author ptw@openlaszlo.org
 *
 * Outputs schema and script for a library
 */

package org.openlaszlo.compiler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import org.jdom.Element;
import org.openlaszlo.sc.JavascriptCompressor;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;

/** Accumulates code, XML, and assets to a Library object file.
 *
 * Properties documented in Compiler.getProperties.
 */
class IntermediateWriter extends DHTMLWriter {

  Element root;
  JavascriptCompressor mCompressor;
  org.openlaszlo.sc.Compiler.Parser mParser;

  // Prefixes that will be searched by FileResolver
  String componentsPath;
  String fontsPath;
  String LFCPath;

  IntermediateWriter(Properties props, OutputStream stream,
                     CompilerMediaCache cache,
                     boolean importLibrary,
                     CompilationEnvironment env,
                     Element root)
    throws CompilationError
    {
      super(props, stream, cache, importLibrary, env);
      this.root = root;
      mCompressor = new JavascriptCompressor(props);
      mParser = new org.openlaszlo.sc.Compiler.Parser();

      // NOTE: [2010-07-02 ptw] The directories that are "unsearched"
      // for adjustRelativePath must agree with the directories that are
      // searched by FileResover.resolveInternal
      try {
        // Make sure these end with "/" as they are directories
        componentsPath = FileUtils.toURLPath(new File(LPS.getComponentsDirectory()).getCanonicalFile()) + "/";
        fontsPath = FileUtils.toURLPath(new File(LPS.getFontDirectory()).getCanonicalFile()) + "/";
        LFCPath = FileUtils.toURLPath(new File(LPS.getLFCDirectory()).getCanonicalFile()) + "/";
      } catch (IOException ioe) {
        throw new CompilationError(ioe);
      }
    }

  @Override
  public void open(String compileType) {
    // We don't do anything except write input to output
  }

  @Override
  void setCanvas(Canvas canvas, String canvasConstructor) {
    addScript(canvasConstructor);
    // Don't write out any canvas script, the script for a canvas will be created
    // from the <canvas> tag that we write when the .lzi file is compiled.
  }

  void beginExportScript() {
    // Lists the runtimes that we have precompiled object libraries for.
    boolean buildPlatformLibs = mEnv.getBooleanProperty(CompilationEnvironment.BUILD_LZO_FOR_RUNTIMES_PROPERTY);
    String runtimes = "runtimes='";
    if (buildPlatformLibs) {
      runtimes += mEnv.getProperty(CompilationEnvironment.RUNTIMES_PROPERTY);
    } 
    runtimes += "'";
    
    String compilerOptions = "options='" +
      "debug:"+mEnv.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY) + ";" +
      "backtrace:"+mEnv.getBooleanProperty(CompilationEnvironment.BACKTRACE_PROPERTY) + ";" +
      "profile:"+mEnv.getBooleanProperty(CompilationEnvironment.PROFILE_PROPERTY) +
      "'";
    mPrintStream.println("<script when='immediate' type='LZBinary' "+runtimes+ " "+compilerOptions+">\n<![CDATA[\n");
  }

  @Override
  public int addScript(String script) {
    // Write 'compressed' output
    org.openlaszlo.sc.parser.SimpleNode program =
      mParser.parse(script);
    // Cf., ScriptCompiler _compileToByteArray
    mCompressor.compress(program, mPrintStream);
    mPrintStream.flush();
    return script.length();
  }


  @Override
  public void finish(boolean isMainApp) throws IOException {
    if (mCloseCalled) {
      throw new IllegalStateException("IntermediateWriter.close() called twice");
    }

    try {
      addScript("canvas.initDone()");
    } catch (Exception e) {
      throw new ChainedException(e);
    } 

  }

  @Override
  public void close() throws IOException {
    mPrintStream.close();
    mCloseCalled = true;
  }

  // schema and resources have been defined, output them now, and prepare to accept script blocks
  // from the compile phase of the compiler
  @Override
  public void schemaDone() throws IOException {
    try {      
        if (mEnv.linking) {
            String filename = getSpritePath(mEnv.getApplicationFile().toString());
            writeMasterSprite(filename);
        }
        addResourceDefs();      
    } catch (Exception e) {
      throw new ChainedException(e);
    }
  }


}

/**
 * @copyright Copyright 2008-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
