/* *****************************************************************************
 * Compiler.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.*;
import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.ProcessingInstruction;
import org.jdom.filter.AbstractFilter;
import org.openlaszlo.sc.SWF9ParseTreePrinter;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.sc.ScriptCompilerInfo;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.ListFormat;



/**
 * Compiles a Laszlo XML source file, and any files that it
 * references, into an object file that can be executed on the client.
 *
 * The compiler parses the file into XML, and then gets an element
 * compiler for each toplevel element.
 */
public class Compiler {
    public static long starttime = System.currentTimeMillis();

    /** Set this to log the modified schema. */
    public static Logger SchemaLogger = Logger.getLogger("schema");

    public static List<String> KNOWN_RUNTIMES =
        Arrays.asList("swf8", "swf9", "swf10", "dhtml", "mobile", "null");
    public static List<String> SUPPORTED_RUNTIMES =
        Arrays.asList("swf10", "dhtml", "mobile", "null");
    public static List<String> SCRIPT_RUNTIMES =
        Arrays.asList("swf10", "swf9", "dhtml", "mobile", "null");
    // Runtimes which are capable of having platform-specific LZO
    // object libraries which can be linked in as a whole file
    public static List<String> BINARY_LINKABLE_RUNTIMES =
        Arrays.asList("swf10", "swf9", "dhtml", "mobile");
    public static List<String> SWF_RUNTIMES =
        Arrays.asList("swf7", "swf8");
    public static List<String> AS3_RUNTIMES =
        Arrays.asList("swf9", "swf10");

    public static List<String> KNOWN_FLEX_VERSIONS =
        Arrays.asList("10.0", "10.1");

    public static List<String> GLOBAL_RUNTIME_VARS =
        Arrays.asList(
                "$debug", "$profile", "$backtrace", "$runtime",
                "$swf7", "$swf8", "$as2", "$swf9", "$swf10", "$as3", "$dhtml", "$j2me", "$svg", "$js1", "$mobile"
            );

    /** Called to resolve file references (<code>src</code>
     * attributes).
     */
    protected FileResolver mFileResolver = FileResolver.DEFAULT_FILE_RESOLVER;

    /**
     * CompilerMediaCache
     */
    protected CompilerMediaCache mMediaCache = null;

    /** See getProperties.
     */
    protected final Properties mProperties = new Properties();

    /** Logger
     */
    private static Logger mLogger  = Logger.getLogger(Compiler.class);


    /**
     * A <code>Compiler</code>'s <code>Properties</code> stores
     * properties that affect the compilation.
     *
     * <table><tr><th>Name=default</th><th>Meaning</th></tr>
     * <tr><td>trace.xml=false</td><td>Display XML that's compiled to
     * script, and the script that it compiles to.</td></tr>
     * <tr><td>debug=true</td><td>Add in debug features to the output.</td></tr>
     * <tr><td>trace.fonts=false</td><td>Additional font traces.</td></tr>
     * <tr><td>swf.frame.rate=30</td><td>Output SWF frame rate.</td></tr>
     * <tr><td>default.text.height=12</td><td>Default text height for
     * fonts.</td></tr>
     * <tr><td>text.borders=false</td><td>Display text borders in the output SWF
     *                                    for debugging.</td></tr>
     * </table>
     *
     * Properties that one part of the compiler sets for another part to read:
     * <table><tr><th>Name=default</th><th>Meaning</th></tr>
     * <tr><td>lzc_*</td><td>Source location values.</td></tr>
     * </table>
     *
     * @return a <code>Properties</code> value
     */
    public String getProperty(String key) {
        return mProperties.getProperty(key);
    }

    public void setProperty(String key, String value) {
        mProperties.setProperty(key, value);
    }

    public FileResolver getFileResolver() {
        return mFileResolver;
    }

    /** Sets the file resolver for this compiler.  The file resolver
     * is used to resolve the names used in include directives to
     * files.
     * @param resolver a FileResolver
     */
    public void setFileResolver(FileResolver resolver) {
        this.mFileResolver = resolver;
    }

    /** Sets the media cache for this compiler.
     * @param cache a CompilerMediaCache
     */
    public void setMediaCache(CompilerMediaCache cache) {
        this.mMediaCache = cache;
    }

    /** Create a CompilationEnvironment with the properties and
     * FileResolver of this compiler.
     */
    public CompilationEnvironment makeCompilationEnvironment(Properties options) {
        Properties props = new Properties();
        // First copy in properties that were set on the Compiler instance
        props.putAll(mProperties);
        // Then merge in properties that were passed into the call to compile()
        if (options != null) {
            props.putAll(options);
        }
        return new CompilationEnvironment(props, mFileResolver, mMediaCache);
    }

    /** Compiles <var>sourceFile</var> to <var>objectFile</var>.  If
     * compilation fails, <var>objectFile</var> is deleted.
     *
     * @param sourceFile source File
     * @param objectFile a File to place object code in
     * @param url request url, ignore if null
     * @throws CompilationError if an error occurs
     * @throws IOException if an error occurs
     */
    public Canvas compile(File sourceFile, File objectFile, String url)
        throws CompilationError, IOException
    {
        Properties props = new Properties();
        return compile(sourceFile, objectFile, props);
    }

    public static String getObjectFileExtensionForRuntime (String runtime) {
        String ext;
        if (AS3_RUNTIMES.contains(runtime)) {
            ext = "." + runtime + ".swf";
        } else {
            ext = SCRIPT_RUNTIMES.contains(runtime) ? ".js" : "." + runtime + ".swf";
        }
        return ext;
    }

    /** Compiles <var>sourceFile</var> to <var>objectFile</var>.  If
     * compilation fails, <var>objectFile</var> is deleted.
     *
     * @param sourceFile source File
     * @param objectFile a File to place object code in
     * @param props parameters for the compile
     * @throws CompilationError if an error occurs
     * @throws IOException if an error occurs

     * The  PROPS parameters for the compile may include
     * <ul>
     * <li> url, optional, ignore if null
     * <li> debug := "true" | "false"   include debugger
     * <li> logdebug := "true" | "false" makes debug.write() calls get logged to server
     * </ul>
     */
    public Canvas compile(File sourceFile, File objectFile, Properties props)
        throws CompilationError, IOException
    {
        FileUtils.makeFileAndParentDirs(objectFile);

        // Compile to a byte-array, and write out to objectFile, and
        // write a copy into sourceFile.[swf|js] if this is a serverless deployment.
        CompilationEnvironment env = makeCompilationEnvironment(props);

        ByteArrayOutputStream bstream = new ByteArrayOutputStream();
        OutputStream ostream = new FileOutputStream(objectFile);
        env.setObjectFile(objectFile);

        boolean success = false;
        try {
            Canvas canvas = compile(sourceFile, bstream, env);
            bstream.writeTo(ostream);
            ostream.close();
            ostream = null;

            // If the app is serverless, write out a .lzx.swf file when compiling
            if (canvas != null && !canvas.isProxied() && env.getProperty(CompilationEnvironment.NO_DEPLOY) == null) {

                // Create a foo.lzx.[js|swf] serverless deployment file for sourcefile foo.lzx
                String runtime = env.getRuntime();
                String soloExtension = getObjectFileExtensionForRuntime(runtime);

                OutputStream fostream = null;
                try {
                    File deploymentFile = new File(sourceFile+soloExtension);
                    fostream = new FileOutputStream(deploymentFile);
                    bstream.writeTo(fostream);
                } finally {
                    if (fostream != null) {
                        fostream.close();
                    }
                }
            }

            // In AS3, we need to compile the import libraries after
            // the main is compiled, in order to let the flex compiler
            // check references in the library against the main app
            // classes
            if (env.isAS3()) {
                List<LibraryCompilation> libs = env.libraryCompilationQueue();
                for (int i = 0; i < libs.size(); i++) {
                    LibraryCompilation lc = libs.get(i);
                    lc.importCompiler.compileLibrary(lc.infile, lc.outfile, lc.liburl, lc.element);
                }
            }

            success = true;
            return canvas;
        } catch (java.lang.OutOfMemoryError e) {
            // The runtime gc is necessary in order for subsequent
            // compiles to succeed.  The System gc is for good luck.
            System.gc();
            Runtime.getRuntime().gc();
            throw new CompilationError("out of memory");
        } finally {
            if (!success) {
                if (ostream != null) {
                    ostream.close();
                }
                objectFile.delete();
            }
        }
    }

    public Properties getProperties() {
        return (Properties)mProperties.clone();
    }

  ObjectWriter createObjectWriter(Properties props,  OutputStream ostr, CompilationEnvironment env, Element root) {
        if ("true".equals(props.getProperty(CompilationEnvironment.INTERMEDIATE_PROPERTY))) {
            return new IntermediateWriter(props, ostr, mMediaCache, true, env, root);
        }

        if (! env.linking) {
            return new LibraryWriter(props, ostr, mMediaCache, true, env, root);
        }

        String runtime = props.getProperty(CompilationEnvironment.RUNTIME_PROPERTY);
        // Must be kept in sync with server/sc/lzsc.py compile
        if ("null".equals(runtime)) {
            return new NullWriter(props, ostr, mMediaCache, true, env);
        } else if (env.isAS3()) {
            return new SWF9Writer(props, ostr, mMediaCache, true, env);
        } else if (SCRIPT_RUNTIMES.contains(runtime)) {
            return new DHTMLWriter(props, ostr, mMediaCache, true, env);
        } else {
            return new SWFWriter(props, ostr, mMediaCache, true, env);
        }
    }

    static void checkKnownRuntime (String runtime) throws CompilationError {
        if (runtime != null) {
            if (! KNOWN_RUNTIMES.contains(runtime)) {
                List<String> runtimes = new ArrayList<String>();
                for (String s : KNOWN_RUNTIMES) {
                    runtimes.add("\"" + s + "\"");
                }

                throw new CompilationError(
                    MessageFormat.format(
                        "Request for unknown runtime: The runtime or \"lzr\" query parameter has the value \"{0}\".  It must be {1}{2}.",
                            runtime,
                            new ChoiceFormat(
                                "1#| 2#either | 2<one of ").
                            format(runtimes.size()),
                            new ListFormat("or").format(runtimes)
                        ));
            }
        }
    }


    /**
     * Compiles <var>file</var>, and write the bytes to
     * a stream.
     *
     * @param file a <code>File</code> value
     * @param ostr an <code>OutputStream</code> value
     * @param props parameters for the compilation
     * @exception CompilationError if an error occurs
     * @exception IOException if an error occurs
     *
     * The parameters currently being looked for in the PROPS arg
     * are "debug", "logdebug", "profile", "krank"
     *
     */
    public Canvas compile(File file, OutputStream ostr, CompilationEnvironment env)
        throws CompilationError, IOException
    {
        if (mLogger.isInfoEnabled()) {
            mLogger.info("compiling " + file + "...");
        }

        CompilationErrorHandler errors = env.getErrorHandler();
        env.setApplicationFile(file);
        
        boolean linking = env.linking;
        boolean noCodeGeneration = "true".equals(env.getProperty(CompilationEnvironment.NO_CODE_GENERATION));

        try {
            if (mLogger.isTraceEnabled()) {
            mLogger.trace(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Parsing " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Compiler.class.getName(),"051018-303", new Object[] {file.getAbsolutePath()})
);
            }
            // Initialize the schema from the base LFC interface file

            try {
                env.getSchema().loadSchema(env);
            } catch (org.jdom.JDOMException e) {
                throw new ChainedException(e);
            }

            Properties props = env.getProperties();

            props.put("$debug", env.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY));
            props.put("$profile", env.getBooleanProperty(CompilationEnvironment.PROFILE_PROPERTY));
            props.put("$backtrace", env.getBooleanProperty(CompilationEnvironment.BACKTRACE_PROPERTY));


            props.put(org.openlaszlo.sc.Compiler.INCREMENTAL_COMPILE,
                      env.getBooleanProperty(CompilationEnvironment.INCREMENTAL_MODE));


            props.put(org.openlaszlo.sc.Compiler.EMIT_AS3_ONLY,
                      env.getBooleanProperty(CompilationEnvironment.LZXONLY));

            String runtime = env.getProperty(CompilationEnvironment.RUNTIME_PROPERTY);
            // Must be kept in sync with server/sc/lzsc.py main
            CompilationEnvironment.setRuntimeConstants(runtime, env.getProperties(), env);


            // Check if an LFC actually exists for these compilation options
            File LFClib =new File(LPS.getLFCDirectory() + File.separator +
                                  LPS.getLFCname(runtime,
                                                 env.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY),
                                                 env.getBooleanProperty(CompilationEnvironment.PROFILE_PROPERTY),
                                                 env.getBooleanProperty(CompilationEnvironment.BACKTRACE_PROPERTY),
                                                 false));
            if (linking && !LFClib.exists()) {
                throw new CompilationError(
                        "No LFC library was found at "+LFClib+" for compilation options: runtime="+runtime+", debug="+
                        props.get("$debug") + ", backtrace="+props.get("$backtrace") + ", profile="+
                        props.get("$profile"));
            }

            Document doc = env.getParser().parse(file, env);
            Element root = doc.getRootElement();
            
            checkKnownRuntime(env.getProperty(CompilationEnvironment.RUNTIME_PROPERTY));

            // cssfile cannot be set in the canvas tag
            String cssfile = env.getProperty(CompilationEnvironment.CSSFILE_PROPERTY);
            if (cssfile != null) {
                if (mLogger.isInfoEnabled()) {
                    mLogger.info("Got cssfile named: " + cssfile);
                }
                cssfile = root.getAttributeValue("cssfile");
                 throw new CompilationError(
                        "cssfile attribute of canvas is no longer supported. Use <stylesheet> instead.");

            }

            if (mLogger.isTraceEnabled()) {
                mLogger.trace("Making a writer...");
            }
            ViewSchema schema = env.getSchema();
            Set<File> externalLibraries = null;
            // If we are not linking, then we consider all external
            // files to have already been imported.
            if (! linking) { externalLibraries = env.getImportedLibraryFiles(); }
            if (root.getName().intern() !=
                (linking ? "canvas" :  "library")) {
                throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="invalid root element type: " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Compiler.class.getName(),"051018-357", new Object[] {root.getName()})
                );
            }


            // Setup pointer to working directory, for backend compilers to use
            ScriptCompilerInfo compilerInfo = new ScriptCompilerInfo();
            // Working directory path to place intermediate .as3 files
            compilerInfo.buildDirPathPrefix = env.getLibPrefix();
            props.put(org.openlaszlo.sc.Compiler.COMPILER_INFO, compilerInfo);
            env.setScriptCompilerInfo(compilerInfo);


            Properties nprops = (Properties) props.clone();
            // [todo: 2006-04-17 hqm] These compileTimeConstants will be used by the script compiler
            // at compile time, but they won't be emitted into the object code for user apps. Only
            // the compiled LFC emits code which defines these constants. We need to have some
            // better way to ensure that the LFC's constants values match the app code's.

            nprops.put("compileTimeConstants", env.getCompileTimeConstants());

            ObjectWriter writer = createObjectWriter(nprops, ostr, env, root);

            writer.open(linking ? SWF9ParseTreePrinter.Config.APP : SWF9ParseTreePrinter.Config.LZOLIB );

            env.setObjectWriter(writer);

            // If user specified external lzo's to link against, pass them to the ObjectWriter now
            env.addLZOFileNames(env.getProperty(CompilationEnvironment.EXTERNAL_LZO_FILES_PROPERTY));
            env.addLZOFileNames(env.getProperty(CompilationEnvironment._ADDITIONAL_LZO_FILES_PROPERTY));

            Compiler.updateRootSchema(root, env, schema, externalLibraries);
            // Tell the ObjectWriter that we've finished building the schema, 
            // classes and resources are all defined now.
            writer.schemaDone();
                        
            if (noCodeGeneration) {
                return null;
            }

            processCompilerInstructions(root, env);
            schema.compileReferenceClassModels();
            LPS.generateMiddle = true;
            compileElement(root, env);

            // Free up some memory
            root = null;
            doc = null;

            // We can free up the schema and class info now, unless there are <import> libs to compile.
            if (env.libraryCompilationQueue().size() == 0 && linking) {
                env.releaseParserAndSchema();
            }
            // Now would be a good time to GC
            Runtime.getRuntime().gc();

            if (mLogger.isTraceEnabled()) {
                mLogger.trace("done...");
            }
            // This isn't in a finally clause, because it won't generally
            // succeed if an error occurs.
            writer.finish();
            writer.close();
            
            Canvas canvas = env.getCanvas();
            if (!errors.isEmpty()) {
                if (canvas != null) {
                    canvas.setCompilationWarningText(
                        errors.toCompilationError().getMessage());
                    canvas.setCompilationWarningXML(
                        errors.toXML());
                }
                System.err.println(errors.toCompilationError().getMessage());
            }
            if (canvas != null) {
              canvas.setBacktrace(env.getBooleanProperty(CompilationEnvironment.BACKTRACE_PROPERTY));
              canvas.setSourceAnnotations(env.getBooleanProperty(CompilationEnvironment.SOURCE_ANNOTATIONS_PROPERTY));
              // set file path (relative to webapp) in canvas
              String path = FileUtils.relativePath(file, LPS.HOME());
              canvas.setFilePath(path);
              // If the canvas has not set a title, create a useful default
              if (canvas.getTitle() == null) {
                String application = FileUtils.getBase(file.getName());
                // Approximate ServletContext.getContextPath()
                String context = new File(LPS.HOME()).getName() + FileUtils.toURLPath(FileUtils.getBase(path));
                canvas.setTitle("OpenLaszlo " + LPS.getVersion() + ": " + application + " (" + context + ")");
              }
            }

            if (linking) {
              ViewCompiler.checkUnresolvedResourceReferences (env);
            }

            if (mLogger.isInfoEnabled()) {
                mLogger.info("done");
            }
            return canvas;
        } catch (CompilationError e) {
            // TBD: e.initPathname(file.getPath());
            e.attachErrors(errors.getErrors());
            throw e;
            //errors.addError(new CompilationError(e.getMessage() + "; compilation aborted"));
            //throw errors.toCompilationError();
        } catch (org.openlaszlo.xml.internal.MissingAttributeException e) {
            /* The validator will have caught this, but if we simply
             * pass the error through, the vaildation warnings will
             * never be printed.  Create a new message that includes
             * them so that we get the source information. */
            errors.addError(new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes=p[0] + "; compilation aborted"
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Compiler.class.getName(),"051018-399", new Object[] {e.getMessage()})
));
            throw errors.toCompilationError();
        }
    }

    /**
     * Compiles a script first as an expression. If that fails, compiles as
     * a sequence of statements. If that fails too, reports the parse error.
     *
     * @param script    the script to be evaluated
     * @param seqnum    only set for remote debug requests
     * @param runtime   target runtime
     * @param props     script compiler properties
     * @return          the compiled eval script
     */
    private byte[] compileEvalScript (String script, String seqnum, String runtime, Properties props) {
        boolean toplevel;
        String start = "", end = "";
        if (SWF_RUNTIMES.contains(runtime)) {
            // declarations are made global in swf8, so they're available in subsequent calls
            toplevel = true;
            // this must be the last instruction, so the loading movieclip is properly unloaded.
            // 'this._parent' point the loadobj movieclip (see kernel/swf/LzLoader.as)
            end = "this._parent.loader.returnData( this._parent );";
        } else if (AS3_RUNTIMES.contains(runtime)) {
            // you can't add global variables dynamically in AS3
            toplevel = false;
            start = "public class " + SWF9Writer.DEBUG_EVAL_SUPERCLASS + " extends Sprite {\n" +
                "#passthrough (toplevel:true) {\n" +
                "import flash.display.Sprite;\n" +
                "}#\n" +
                "public function " + SWF9Writer.DEBUG_EVAL_SUPERCLASS + " (...ignore) {runToplevelDefinitions();}\n" +
                "public function runToplevelDefinitions() {}\n" +
                "}\n";
        } else {
            throw new CompilationError("invalid runtime: " + runtime);
        }

        // some extra newlines, so e.g. a single-line comment won't break the compilation
        script = "\n" + script + "\n";

        byte[] action;
        try {
            String prog =
                    "(function () {\n" +
                        (toplevel
                        ? "    #pragma 'scriptElement'\n"
                        : "") +
                "try{" +
                // See LPP-8633 flex compiler screws up compiling functions inside of "with"
                         ((!(AS3_RUNTIMES.contains(runtime))) ? "with(Debug.environment){\n" : "" ) +
                    "Debug.displayResult(" + script + ");\n" +
                        ((!(AS3_RUNTIMES.contains(runtime))) ? "}" : "" ) +

                 "}\n" +
                    "catch(e){\n" +
                    "Debug.displayResult(e);\n" +
                    "}}());\n";
            action = ScriptCompiler.compileToByteArray(start + prog + end, props);
        } catch (Exception e) {
            try {
                String prog =
                        "(function () {\n" +
                            (toplevel
                            ? "    #pragma 'scriptElement'\n"
                            : "") +
                        CompilerUtils.sourceLocationDirective(null, 0, 0) +
                        "try{" +
                    // See LPP-8633 flex compiler screws up compiling functions inside of "with"
                         ((!(AS3_RUNTIMES.contains(runtime))) ? "with(Debug.environment){\n" : "" ) +

                        // intentionally wrapped script in block, so it doesn't interfere with next statements
                        "{" + script + "}\n" +
                        // call 'displayResult()' to ensure fresh line and prompt
                        "Debug.displayResult();\n" +
                    ((!(AS3_RUNTIMES.contains(runtime))) ? "}" : "" ) +
                    "}\n" +
                        "catch(e){\n" + "Debug.displayResult(e);\n" +
                        "}}());\n";
                action = ScriptCompiler.compileToByteArray(start + prog + end, props);
            } catch (Exception e2) {
                // mLogger.info("error compiling/writing script: " + e2.getMessage());
                String msg = ScriptCompiler.quote("Parse error: "+ e2.getMessage());
                String prog =
                            "(function () {\n" +
                            "Debug.displayResult(" + msg + ");\n" +
                            "})();\n";
                action = ScriptCompiler.compileToByteArray(start + prog + end, props);
            }
        }

        return action;
    }

    /*
     * Used by the debugger-evaluator.
     */
    public void compileAndWriteToSWF (String script, String seqnum, OutputStream out, String runtime) {
        try {
            CompilationEnvironment env = makeCompilationEnvironment(null);
            env.setProperty(CompilationEnvironment.DEBUG_PROPERTY, true);
            Properties props = (Properties) env.getProperties().clone();
            env.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, runtime);
            byte[] action = this.compileEvalScript(script, seqnum, runtime, props);
            ScriptCompiler.writeScriptToStream(action, out, LPS.getSWFVersionNum(runtime));
            out.flush();
            out.close();
        } catch (IOException e) {
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="error compiling/writing script: " + p[0] + " :" + p[1]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Compiler.class.getName(),"051018-458", new Object[] {script, e})
);
        }
    }

    /*
     * Used by the debugger-evaluator. Compiles a standalone application which executes the javascript in SCRIPT.
     */
    public void compileAndWriteToAS3 (String script, String runtime, String seqnum, File appfile, OutputStream out) {
        try {
            Properties props = new Properties();
            props.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, runtime);
            props.put(org.openlaszlo.sc.Compiler.COMPILE_TYPE, SWF9ParseTreePrinter.Config.APP);
            props.setProperty("canvasWidth", "1000");
            props.setProperty("canvasHeight", "600");
            Map<String, Object> compileTimeConstants = new HashMap<String, Object>();
            compileTimeConstants.put("$debug", true);
            compileTimeConstants.put("$profile", false);
            compileTimeConstants.put("$backtrace", false);
            compileTimeConstants.put("$runtime", runtime);
            compileTimeConstants.put("$swf7", false);
            compileTimeConstants.put("$swf8", false);
            compileTimeConstants.put("$as2", false);
            compileTimeConstants.put("$swf9", "swf9".equals(runtime));
            compileTimeConstants.put("$swf10", "swf10".equals(runtime));
            compileTimeConstants.put("$as3", true);
            compileTimeConstants.put("$dhtml", false);
            compileTimeConstants.put("$j2me", false);
            compileTimeConstants.put("$svg", false);
            compileTimeConstants.put("$js1", false);
            props.put("compileTimeConstants", compileTimeConstants);
            props.setProperty(CompilationEnvironment.DEBUG_EVAL_PROPERTY, "true");
            props.setProperty(CompilationEnvironment.DEBUG_PROPERTY, "true");
            props.put(org.openlaszlo.sc.Compiler.SWF9_APP_CLASSNAME, SWF9Writer.DEBUG_EVAL_CLASSNAME);
            props.put(org.openlaszlo.sc.Compiler.SWF9_WRAPPER_CLASSNAME, SWF9Writer.DEBUG_EVAL_CLASSNAME);
            props.put(org.openlaszlo.sc.Compiler.SWF9_APPLICATION_PREAMBLE, 
                      "public class " + SWF9Writer.DEBUG_EVAL_CLASSNAME +
                      " extends " +  SWF9Writer.DEBUG_EVAL_SUPERCLASS + " {\n " + SWF9Writer.imports + "}\n");


            ScriptCompilerInfo compilerInfo = new ScriptCompilerInfo();
            props.put(org.openlaszlo.sc.Compiler.COMPILER_INFO, compilerInfo);

            CompilationEnvironment env = makeCompilationEnvironment(null);
            env.setProperty(CompilationEnvironment.DEBUG_PROPERTY, true);
            env.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, runtime);
            // Working directory path to place intermediate .as3 files
            env.setApplicationFile(appfile);
            compilerInfo.buildDirPathPrefix = env.getLibPrefix();

            byte[] objcode = this.compileEvalScript(script, null, runtime, props);
            FileUtils.sendToStream(new ByteArrayInputStream(objcode), out);
            out.flush();
            out.close();
        } catch (IOException e) {
            mLogger.info("error compiling/writing script: " + script);
        }
    }



    static ElementCompiler getElementCompiler(Element element,
                                              CompilationEnvironment env) {
        if (CanvasCompiler.isElement(element)) {
            return new CanvasCompiler(env);
        } else if (ImportCompiler.isElement(element)) {
            return new ImportCompiler(env);
        } else if (LibraryCompiler.isElement(element)) {
            return new LibraryCompiler(env);
        } else if (ScriptElementCompiler.isElement(element)) {
            return new ScriptElementCompiler(env);
        } else if (DataCompiler.isElement(element)) {
            return new DataCompiler(env);
        } else if (SecurityCompiler.isElement(element)) {
            return new SecurityCompiler(env);
        } else if (SplashCompiler.isElement(element)) {
            return new SplashCompiler(env);
        } else if (FontCompiler.isElement(element)) {
            return new FontCompiler(env);
        } else if (ResourceCompiler.isElement(element)) {
            return new ResourceCompiler(env);
        } else if (ClassCompiler.isElement(element)) {
            return new ClassCompiler(env);
        } else if (MixinCompiler.isElement(element)) {
            return new MixinCompiler(env);
        } else if (InterfaceCompiler.isElement(element)) {
            return new InterfaceCompiler(env);
        } else if (TypeCompiler.isElement(element)) {
            return new TypeCompiler(env);
        } else if (DebugCompiler.isElement(element)) {
            return new DebugCompiler(env);
        } else if (WrapperHeaders.isElement(element)) {
            return new WrapperHeaders(env);
        } else if (StyleSheetCompiler.isElement(element)) {
            return new StyleSheetCompiler(env);
            // The following test tests true for everything, so call
            // it last.
        } else if (ViewCompiler.isElement(element)) {
            return new ViewCompiler(env);
        } else {
            throw new CompilationError("unknown tag: " + element.getName(),
                                       element);
        }
    }

    /**
     * Compile an XML element within the compilation environment.
     * Helper function for compile.
     *
     * @param element an <code>Element</code> value
     * @param env a <code>CompilationEnvironment</code> value
     * @exception CompilationError if an error occurs
     */
    protected static void compileElement(Element element,
                                         CompilationEnvironment env)
        throws CompilationError
    {
        if (element.getAttributeValue("disabled") != null &&
            element.getAttributeValue("disabled").intern() == "true") {
            return;
        }
        if (ViewSchema.isMetaElement(element)) {
          return;
        }

        try {
            ElementCompiler compiler = getElementCompiler(element, env);
            if (mLogger.isTraceEnabled()) {
                mLogger.trace("compiling element "+element.getName() + " with compiler "+compiler.getClass().toString());
            }
            compiler.compile(element);
        } catch (CompilationError e) {
            // todo: wrap instead
            if (e.getElement() == null && e.getPathname() == null) {
                e.initElement(element);
            }
            throw e;
        }
    }

    static void updateRootSchema(Element root, CompilationEnvironment env,
                                 ViewSchema schema, Set<File> externalLibraries)
    {
        ElementCompiler ecompiler = getElementCompiler(root, env);
        if (! (ecompiler  instanceof ToplevelCompiler)) {
            throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="invalid root element type: " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Compiler.class.getName(),"051018-357", new Object[] {root.getName()})
                );
            }
        ToplevelCompiler tlc = (ToplevelCompiler) ecompiler;

        // Update schema for auto-includes
        // Note:  this call does _not_ share visited with the update
        // calls intentionally.
        List<File> libraries = ToplevelCompiler.getLibraries(env, root, env.getExplanations(), externalLibraries, new HashSet<File>());
        // Save the libraries for handleAutoincludes
        env.setLibraries(libraries);
        Set<File> visited = new HashSet<File>();
        for (File library : libraries) {
            Compiler.updateSchemaFromLibrary(library, env, schema, visited, externalLibraries);
        }
        tlc.updateSchema(root, schema, visited);
        // Now resolve the types
        schema.resolveTypes();
        // Now sort and resolve the classes
        schema.resolveClassModels();
    }

    static void updateSchema(Element element, CompilationEnvironment env,
                             ViewSchema schema, Set<File> visited)
    {
        getElementCompiler(element, env).updateSchema(element, schema, visited);
    }

    static void importLibrary(File file, CompilationEnvironment env) {
        Element root = LibraryCompiler.resolveLibraryElement(
            file, env, env.getImportedLibraryFiles());
        if (root != null) {
            compileElement(root, env);
        }
    }

    static void updateSchemaFromLibrary(File file, CompilationEnvironment env,
                                        ViewSchema schema, Set<File> visited, Set<File> externalLibraries)
    {
        Element root = LibraryCompiler.resolveLibraryElement(
            file, env, visited);
        if (root != null) {
          try {
            // Library keys are the canonical file name
            File key = file.getCanonicalFile();
            boolean old = env.getBooleanProperty(CompilationEnvironment._EXTERNAL_LIBRARY);
            boolean extern = (externalLibraries != null) ? externalLibraries.contains(key) : false;
            try {
              env.setProperty(CompilationEnvironment._EXTERNAL_LIBRARY, extern);
              Compiler.updateSchema(root, env, schema, visited);
            } finally {
              env.setProperty(CompilationEnvironment._EXTERNAL_LIBRARY, old);
            }
          } catch (java.io.IOException e) {
            throw new CompilationError(root, e);
          }
        }
    }

    @SuppressWarnings("serial")
    private static final class ProcessingInstructionFilter extends AbstractFilter {
        public static final ProcessingInstructionFilter INSTANCE = new ProcessingInstructionFilter();

        private ProcessingInstructionFilter () {}

        public boolean matches(Object obj) {
            return (obj instanceof ProcessingInstruction);
        }
    }

    protected void processCompilerInstructions(Element element,
                                               CompilationEnvironment env) {
        @SuppressWarnings("unchecked")
        List<ProcessingInstruction> list = element.getContent(ProcessingInstructionFilter.INSTANCE);
        for (ProcessingInstruction pi : list) {
            if (pi.getTarget().equals("lzc"))
                processCompilerInstruction(env, pi);
        }
    }

    protected void processCompilerInstruction(CompilationEnvironment env,
                                              ProcessingInstruction pi) {
        if (pi.getPseudoAttributeValue("class") != null)
            processClassInstruction(env, pi.getPseudoAttributeValue("class"), pi);
        if (pi.getPseudoAttributeValue("classes") != null) {
            for (String className : pi.getPseudoAttributeValue("classes").split("\\s+")) {
                processClassInstruction(env, className, pi);
            }
        }
    }

    protected void processClassInstruction(CompilationEnvironment env,
                                           String className,
                                           ProcessingInstruction pi) {
    }
}
