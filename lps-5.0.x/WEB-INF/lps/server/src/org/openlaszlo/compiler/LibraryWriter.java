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

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jdom.Element;
import org.openlaszlo.sc.SWF9ParseTreePrinter;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;


/** Accumulates code, XML, and assets to a Library object file.
 *
 * Properties documented in Compiler.getProperties.
 */
class LibraryWriter extends IntermediateWriter {

  ZipOutputStream zout;
  SWF9Writer mSWF10Writer = null;
  DHTMLWriter mDHTMLWriter = null;

  LibraryWriter(Properties props, OutputStream stream,
                CompilerMediaCache cache,
                boolean importLibrary,
                CompilationEnvironment env,
                Element root) {


    super(props, stream, cache, importLibrary, env, root);

    try {
      this.zout = new ZipOutputStream(mStream);
      // Create main file entry named 'lzo', which contains XML schema
      // declarations plus lzs script.
      zout.putNextEntry(new ZipEntry("lzo"));
      this.mStream = zout;
      this.mPrintStream = new PrintStream(zout);
    } catch (Exception e) {
      throw new ChainedException(e);
    }
    this.root = root;
    boolean makePlatformBinaries = env.getBooleanProperty(CompilationEnvironment.BUILD_LZO_FOR_RUNTIMES_PROPERTY);
    if (makePlatformBinaries && env.targetRuntimesContain("dhtml")) {
      Properties cprops = (Properties)mProperties.clone();
      props.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, "dhtml");
      // Problem here, need to set this on clone of properties , so we don't bash the global compilation env
      CompilationEnvironment.setRuntimeConstants("dhtml", cprops, mEnv);
      mDHTMLWriter = new DHTMLWriter(props, mStream, cache, true, mEnv);
    }
    if (makePlatformBinaries && (env.targetRuntimesContain("swf10") || env.targetRuntimesContain("swf9"))) {
      Properties cprops = (Properties)mProperties.clone();
      cprops.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, env.targetRuntimesContain("swf10") ? "swf10" : "swf9");
      // Problem here, need to set this on clone of properties , so we don't bash the global compilation env
      CompilationEnvironment.setRuntimeConstants("swf10", cprops, mEnv);
      // We are building a precompiled flash 10 .swc library for the LZO.
      cprops.put(org.openlaszlo.sc.Compiler.COMPILE_TYPE, SWF9ParseTreePrinter.Config.LZOLIB);
      mSWF10Writer = new SWF9Writer(cprops, mStream, cache, true, mEnv);
    } 
  }


    public final static String LZO_MAIN_CLASSNAME = "LZOApplication";

  /** Creates boilerplate lzo library 'main class' entry point.
      This is a class into which all top level statements will get placed by the swf9 backend compiler.

      When compiling an app with lzo's that have precompiled swc
      libraries in them, it should call the 'runTopLevelDefinitions'
      of each of it's lzo libraries 'main' classes.
   */
    public String makeLZOPreamble(String lzoSanitizedName) {
        // Turn off all annotation for the preamble
        String source = "{\n#pragma 'debug=false'\n#pragma 'debugSWF9=false'\n#pragma 'debugBacktrace=false'\n";
        source += "public class " + lzoSanitizedName +  " extends LZOApplication {\n " + SWF9Writer.imports + "\n" +
          "public function "+lzoSanitizedName +"(){ super(); } "+
          "}\n";
        source += "\n}\n";
        return source;
    }

  
  /** Add any external lzo files to link against to the
   * runtime-specific ObjectWriters, in case they require it (only
   * swf10 actuall uses this at linking right now)
   */
  @Override
  public void addLZOFile(File lzo) {
    super.addLZOFile(lzo);
    if (mSWF10Writer != null) {
      mSWF10Writer.addLZOFile(lzo);
    }
    if (mDHTMLWriter != null) {
      mDHTMLWriter.addLZOFile(lzo);
    }
  }

  public static String fileToSymbol(File appfile) {
    try {
      String lzoAbsPath = appfile.getCanonicalPath();
      lzoAbsPath = lzoAbsPath.substring(0,lzoAbsPath.length()-4);
      String relPath = FileUtils.relativePath(lzoAbsPath, LPS.HOME());
      // remove "/" and "." from pathname, replace with "_"
      relPath += relPath.hashCode();
      String safename = "";
      for (int i = 0; i < relPath.length(); i++) {
        if (Character.isJavaIdentifierPart(relPath.charAt(i))) {
          safename += relPath.charAt(i);
        } else {
          safename += "_";
        }
      }
      return safename;
    } catch (IOException e) {
      throw new CompilationError("Could not generate classname from LZO file name "+appfile);
    }
  }

  // Returns true if s is a legal Java identifier.
  public static boolean isJavaIdentifier(String s) {
    if (s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0))) {
      return false;
    }
    for (int i=1; i<s.length(); i++) {
      if (!Character.isJavaIdentifierPart(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }



  /** For an lzo library, we look at the runtimes specified, and
   * create ObjectWriters if needed for DHTML and/or SWF10 if requested.
   */
  @Override
  public void open(String compileType) {
    if (mSWF10Writer != null) {
      String safename = fileToSymbol(mEnv.getObjectFile());
      mSWF10Writer.mAppMainClassname = safename;
      mSWF10Writer.mAppPreamble = makeLZOPreamble(safename);

      mSWF10Writer.open(SWF9ParseTreePrinter.Config.LZOLIB);
    }
    if (mDHTMLWriter != null) {
      mDHTMLWriter.open(SWF9ParseTreePrinter.Config.LZOLIB);
    }

  }

  /** Compiles a class as as3 script, for referencing against, but
      directs that the swf10 backend not include it in the final .swc
      that it produces.
      

   */
  @Override
  void addClassModel(String script, String classname) {
    if (mSWF10Writer != null) {    
      mSWF10Writer.addClassModel(script, classname);
    }
  }

  @Override
  public int addScript(String script) {
    int val = super.addScript(script);
    if (mDHTMLWriter != null) {
      mDHTMLWriter.addScript(script);
    }
    if (mSWF10Writer != null) {
      mSWF10Writer.addScript(script);
    }
    return val;
  } 

  /**
   * Sets the canvas for the app
   *
   * @param canvas
   *
   */
  // TODO: [[2007-01-30 ptw] This should become an error
  @Override
  void setCanvas(Canvas canvas, String canvasConstructor) {
  }

  private void exportInterface() {
    mPrintStream.println(mEnv.getSchema().toLZX());
  }

  private String libraries() {
    StringWriter writer = new StringWriter();
    PrintWriter sout = new PrintWriter(writer);
    String indent = "";
    for (File library : includes.keySet()) {
      if (! autoIncludes.containsKey(library)) {
        String path = adjustResourcePath(library.getPath());
        sout.println(indent + path);
        indent = "  ";
      }
    }
    String result = writer.toString();
    if (result.length() > 0) {
      return " includes=\"" + result.substring(0, result.length()-1) + "\"";
    }
    return "";
  }

  void exportIncludes() {
    Set<File> implicit = new HashSet<File>();
    for (File key : autoIncludes.keySet()) {
      if (! implicit.contains(key)) {
        Set<File> subIncludes = autoIncludes.get(key);
        if (subIncludes != null) {
          // An auto-include will not have been parsed for sub-includes?
          implicit.addAll(subIncludes);
        }
        String path = adjustResourcePath(key.getPath());
        mPrintStream.println("<include href='" + path + "' />");
      }
    }
  }

  @Override
  public void finish(boolean isMainApp) throws IOException {
    //Should we emit javascript or SWF?
    //boolean emitScript = mEnv.isLibrary();

    if (mCloseCalled) {
      throw new IllegalStateException("LibraryWriter.close() called twice");
    }

    try {
      endExportScript();
      exportInterface();

      mPrintStream.println("</library>");
      mPrintStream.flush();
      zout.closeEntry();

      if (mSWF10Writer != null) {
        String nameWithOptions = addLZOCompilerOptionFlags("swc", mEnv);
        zout.putNextEntry(new ZipEntry(nameWithOptions));
        mSWF10Writer.setOutputStream(zout);
        mSWF10Writer.finish(false);
        zout.closeEntry();
      }

      if (mDHTMLWriter != null) {
        String nameWithOptions = addLZOCompilerOptionFlags("js", mEnv);
        zout.putNextEntry(new ZipEntry(nameWithOptions));
        mDHTMLWriter.setOutputStream(zout);
        mDHTMLWriter.finish(false);
        zout.closeEntry();
      }
    } finally {
      zout.close();
    }
    mCloseCalled = true;
  }

  // schema and resources have been defined, output them now, and prepare to accept script blocks
  // from the compile phase of the compiler
  @Override
  public void schemaDone() throws IOException {
    try {
      ToplevelCompiler.getLibraries(mEnv, root, null, autoIncludes, includes);

      mPrintStream.println("<!-- This is a binary library.  Not meant for human consumption. -->");
      mPrintStream.println("<!-- DO NOT EDIT THIS FILE.  Edit the source and recompile with `-c` -->");
      mPrintStream.println("<library" + libraries() + ">");

      exportIncludes();
      exportResources();
      beginExportScript();

      // trampoline calls to runtime-specific object writers
      if (mSWF10Writer != null) {
        mSWF10Writer.schemaDone();
      }

      if (mDHTMLWriter != null) {
        mDHTMLWriter.schemaDone();
      }

      
    } catch (Exception e) {
      throw new ChainedException(e);
    }
  }
  


  /** Import a resource file into the current movie.
   * Using a name that already exists clobbers the
   * old resource (for now).
   *
   * @param fileName file name of the resource
   * @param name name of the MovieClip/Sprite
   * @throws CompilationError
   */
  
  @Override
  public void importResource(File inputFile, String name)
    {
      importResource(inputFile.toString(), name, null);
    }

  @Override
  public void importResource(String fileName, String name)
    {
      importResource(fileName, name, null);
    }

  @Override
  public void importResource(File inputFile, String name, ResourceCompiler.Offset2D offset)
    {
      importResource(inputFile.toString(), name, offset);
    }

  @Override
  public void importResource(String fileName, String name, ResourceCompiler.Offset2D offset)
    {

      File absfile = new File(fileName);
      // If we're compiling a binary library, only declare <resource>
      // if it's source file lives under the current app (library) dir
      if (!mEnv.isExternal(absfile)) {
        Set<String> seen = mEnv.importedResources();
        if (seen.contains(name)) {
          mEnv.warn("Duplicate resource name declared "+name+": "+fileName);
        } else {
          seen.add(name);
        }
        resourceList.add(new ResourceDescriptor(name, fileName, offset));
      } 
    }

  @Override
  public void importResource(List<String> sources, String name, File parent)
    {
      importResource(sources, name, parent, null);
    }

  @Override
  public void importResource(List<String> sources, String name, File parent, ResourceCompiler.Offset2D offset)
    {
      // If we're compiling a binary library, only declare <resource>
      // if it's source file lives under the current app (library) dir
      if (!mEnv.isExternal(parent)) {
        Set<String> seen = mEnv.importedResources();
        if (seen.contains(name)) {
          mEnv.warn("Duplicate resource name declared "+name+": "+parent);
        } else {
          seen.add(name);
        }
        resourceList.add(new ResourceDescriptor(name, sources, offset));

      }
    }   
  
  public void endExportScript() {
        mPrintStream.println("\n]]>\n</script>");
      }
      
      // Must preserve visited order for output of includes
      Map<File, Set<File>> autoIncludes = new LinkedHashMap<File, Set<File>>();
      Map<File, Set<File>> includes = new LinkedHashMap<File, Set<File>>();

      List<ResourceDescriptor> resourceList = new LinkedList<ResourceDescriptor>();

      String adjustResourcePath(String src) {
        try {
          return new URL(src).toString();
        } catch (MalformedURLException e) {
          try {
            File file = new File(src).getCanonicalFile();
            String path = FileUtils.toURLPath(file);

            if (path.startsWith(componentsPath)) {
              return path.substring(componentsPath.length());
            }
            if (path.startsWith(fontsPath)) {
              return path.substring(fontsPath.length());
            }
            if (path.startsWith(LFCPath)) {
              return path.substring(LFCPath.length());
            }

            String outdir = FileUtils.toURLPath(mEnv.getObjectFile().getCanonicalFile().getParentFile());
            return FileUtils.adjustRelativePath(file.getName(),
                                                outdir,
                                                FileUtils.toURLPath(file.getParentFile()));
          } catch (IOException f) {
            return src;
          }
        }
      }

      class ResourceDescriptor {
        String name;
        String file = null;
        List<String> sources = null;
        ResourceCompiler.Offset2D offset = null;

        ResourceDescriptor (String name, String file, ResourceCompiler.Offset2D offset) {
          this.name = name;
          this.file = file;
          this.offset = offset;
        }

        ResourceDescriptor (String name, List<String> sources, ResourceCompiler.Offset2D offset) {
          this.name = name;
          this.sources = sources;
          this.offset = offset;
        }

        String toLZX () {
          String result = "<resource name='" + name + "'";
          if (this.file != null) {
            result += " src='" + adjustResourcePath(file) + "'";
          }
          if (offset != null) {
            result += " offsetx='" + offset.offsetx + "' offsety='" + offset.offsety + "'";
          }
          if (this.sources == null) {
            result += " />";
          } else {
            result += ">";
            for (String source : sources) {
              result += "\n  <frame src='" + adjustResourcePath(source) + "' />";
            }
             result += "\n</resource>";
          }
          return result;
        }
      }

      void exportResources() {
        for (ResourceDescriptor resource : resourceList) {
          mPrintStream.println(resource.toLZX());
        }
      }

}

/**
 * @copyright Copyright 2008-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
