/*****************************************************************************
 * SWF9Writer.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
 * Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
 * Use is subject to license terms.                                            *
 * J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.openlaszlo.compiler.ResourceCompiler.Offset2D;
import org.openlaszlo.iv.flash.api.text.Font;
import org.openlaszlo.sc.SWF9ParseTreePrinter;
import org.openlaszlo.sc.ScriptCompilerInfo;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.NaturalOrderComparator;

/** Accumulates code, XML, and assets to a SWF9 object file.
 *
 * Properties documented in Compiler.getProperties.
 */
class SWF9Writer extends ObjectWriter {

    private FontManager mFontManager = new FontManager();
    private Map<String, String> mDeviceFontTable = new HashMap<String, String>();

    /** Default font */
    private String mDefaultFontName = null;
    private String mDefaultFontFileName = null;
    private String mDefaultBoldFontFileName = null;
    private String mDefaultItalicFontFileName = null;
    private String mDefaultBoldItalicFontFileName = null;
    /** Height for generated advance (width) table */
    public static final int DEFAULT_SIZE = 11;
    /** Leading for text and input text */
    private int mTextLeading = 2;

    /** Logger */
    protected static Logger mLogger = org.apache.log4j.Logger.getLogger(SWF9Writer.class);

    /** Code which will used as the contents of the application main as3 class file */
    protected String mAppPreamble;
    protected String mAppMainClassname = MAIN_APP_CLASSNAME;


    SWF9Writer(Properties props, OutputStream stream,
                CompilerMediaCache cache,
                boolean importLibrary,
               CompilationEnvironment env) {

        super(props, stream, cache, importLibrary, env);

        mAppPreamble = makeApplicationPreamble();
    }


    /**
     * Sets the canvas for the app
     *
     * @param canvas
     *
     */
    @Override
    void setCanvas(Canvas canvas, String canvasConstructor) {
        // TODO [hqm 2010-05] need to made sure canvas has been created before after any LZO initializer calls are made.
        // So how can we ensure that? When is CanvasCompiler getting called? Before any of the lzo's are processed?
        addScript(canvasConstructor);
        // Pass canvas dimensions through the script compiler to the Flex compiler
        mProperties.put("canvasWidth", Integer.toString(canvas.getWidth()));
        mProperties.put("canvasHeight", Integer.toString(canvas.getHeight()));
        mEnv.getCanvas().addInfo(mInfo);
    }

    @Override
    void setCanvasDefaults(Canvas canvas, CompilerMediaCache mc) { };



    org.openlaszlo.sc.SWF10Compiler mSCompiler = null;

    @Override
    public int addScript(String script) {
        // For swf9, this emits one or more .as3 files to the temp working dir
        mSCompiler.compileBlock(script);
        return 1;
    }

    @Override
    void addClassModel(String script, String classname) {
        mSCompiler.addClassModel(script, classname);
    }

    @Override
    public void importPreloadResource(File fileName, String name)
        throws ImportResourceError
    {
    }

    @Override
    public void importPreloadResource(String fileName, String name)
        throws ImportResourceError
    {
    }

    /** Import a multiframe resource into the current movie.  Using a
     * name that already exists clobbers the old resource (for now).
     */
    @Override
    public void importPreloadResource(List<String> sources, String name, File parent)
        throws ImportResourceError
    {
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
    public void importResource(String fileName, String name)
        throws ImportResourceError
    {
        importResource(new File(fileName), name, null);
    }

    @Override
    public void importResource(File inputFile, String name)
      throws ImportResourceError {
        importResource(inputFile, name, null);
    }

    @Override
    public void importResource(String fileName, String name, ResourceCompiler.Offset2D offset)
      throws ImportResourceError
    {
        importResource(new File(fileName), name, offset);
    }

    public void importResource(File inputFile, String name, ResourceCompiler.Offset2D offset)
        throws ImportResourceError
    {
        // Moved this conversion from below.
        try {
            inputFile = inputFile.getCanonicalFile(); //better be ok if this does not yet exist. changed from getcanonicalPath to getCanonicalFile
        } catch (java.io.IOException e) {
            throw new ImportResourceError(inputFile.toString(), e, mEnv);
        }

        File[] sources;
        List<String> outsources = new ArrayList<String>();
        if (mLogger.isTraceEnabled()) {
            mLogger.trace("SWF9Writer: Importing resource: " + name);
        }
        if (inputFile.isDirectory()) {
            //mLogger.trace("SWF9Writer Is directory: " + inputFile.toString());
            sources = inputFile.listFiles();
            Arrays.sort(sources, NaturalOrderComparator.NUMERICAL_ORDER);
            //mLogger.trace("SWF9Writer: "+inputFile.toString()+" is a directory containing "+ sources.length +" files.");
            for (int i = 0; i < sources.length; i++) {
                // Construct path from directory and file names.
                /* TODO: In theory, file resolution might get files that come from somewhere on the file system
                   that doesn't match where things will be on the server. Part of the root path may differ,
                   and this File doesn't actually have to correspond to a file on the server disk. Thus the path
                   is reconstructed here.

                   That said, the current code isn't actually abstracting the path here.
                   This will actually come into play when the '2 phase' file resolution currently occuring is
                   compacted to a single phase. To do this, more resource and file descriptor information will have
                   to be maintained, either in a global table or using a more abstract path structure, or both.
                   For now I'll leave this as it is. [pga]
                */
                String sFname = inputFile.toString() + File.separator + sources[i].getName();
                //mLogger.debug("SWF9Writer file: " + sFname + " is a file? " + new File(sFname).isFile());
                if (isFileValidForImport(sFname)) {
                    outsources.add(sFname);
                }
            }
            importResource(outsources, name, null);
            return;
        } else if (!inputFile.exists()) {
            // This case is supposed to handle multiframe resources, as is the one above.
            sources = FileUtils.matchPlusSuffix(inputFile);
            for (int i = 0; i < sources.length; i++) {
                // Construct path from directory and file names. (see comment above [pga])
                File fDir = inputFile.getParentFile();
                String sFname = fDir.toString() + File.separator + sources[i].getName();
                File f = new File(sFname); //sources[i];
                //mLogger.debug("SWF9Writer file: " + f.toString() + " is a file? " + f.isFile());

                if (f.isFile()) {
                    outsources.add(f.toString());
                }
            }
            importResource(outsources, name, null);
            return;
        }

        // Conversion to canonical path was here.
        org.openlaszlo.iv.flash.api.FlashDef def = null;

        File dirfile = mEnv.getApplicationFile().getParentFile();
        File appdir = dirfile != null ? dirfile : new File(".");
        if (mLogger.isTraceEnabled()) {
            mLogger.trace("appdir is: " + appdir + ", LPS.HOME() is: "+ LPS.HOME());
        }
        //File appHomeParent = new File(LPS.getHomeParent());
        String sHome=LPS.HOME();
        // relativePath will perform canonicalization if needed, placing
        // the current dir in front of a path fragment.
        String arPath = FileUtils.relativePath(inputFile, appdir);
        String srPath = FileUtils.relativePath(inputFile, sHome);
        String relPath;
        String pType;
        if (arPath.length() <= srPath.length()) {
            pType="ar";
            relPath = arPath;
        } else {
            pType ="sr";
            relPath = srPath;
        }
        // make it relative and watch out, it comes back canonicalized with forward slashes.
        // Comparing to file.separator is wrong on the pc.
         if (relPath.charAt(0) == '/') {
                 relPath = relPath.substring(1);
        }
         if (mLogger.isTraceEnabled()) {
             mLogger.trace("relPath is: "+relPath);
         }

         StringBuilder sbuf = new StringBuilder();

        //      [Embed(source="logo.swf")]
        // public var logoClass:Class;

        sbuf.append("#passthrough {\n");
        String rpath;
        try {
            rpath = inputFile.getCanonicalPath();
            // Fix Winblows pathname backslash lossage
            rpath = rpath.replaceAll("\\\\", "/");
        } catch (IOException e) {
            throw new ImportResourceError(inputFile.toString(), e, mEnv);
        }
        sbuf.append("[Embed(source=\""+rpath+"\")]\n");
        String assetClassname = "__embed_lzasset_" + name;
        sbuf.append("var "+assetClassname+":Class;\n");
        sbuf.append("}#\n");

        sbuf.append("LzResourceLibrary." +
                    name + "={ptype: \"" + pType + "\", assetclass: "+assetClassname +",");

        if (offset != null && offset.offsetx != null) {
            sbuf.append("offsetx: "+offset.offsetx+",");
        }

        if (offset != null && offset.offsety != null) {
            sbuf.append("offsety: "+offset.offsety+",");
        }

        sbuf.append("frames:[");
        sbuf.append("'"+relPath+"'");

        Resource res = mResourceMap.get(inputFile.toString());
        boolean oldRes = (res != null);
        if (!oldRes) {
            // Get the resource and put in the map
            res = getResource(inputFile.toString(), name);
            if (mLogger.isTraceEnabled()) {
                mLogger.trace("Building resource map; res: "+ res + ", relPath: "+ relPath +", fileName: "+inputFile.toString());
            }
            mResourceMap.put(relPath, res); //[pga] was fileName
            def = res.getFlashDef();
            if (def != null) {
                def.setName(name);
            }

            Element elt = new Element("resource");
            elt.setAttribute("name", name);
            elt.setAttribute("source", relPath); //[pga] was fileName
            elt.setAttribute("filesize", "" + FileUtils.getSize(inputFile));
            mInfo.addContent(elt);
        } else {
            def = res.getFlashDef();
            // Add an element with 0 size, since it's already there.
            Element elt = new Element("resource");
            elt.setAttribute("name", name);
            elt.setAttribute("source", relPath); //[pga] was fileName
            elt.setAttribute("filesize", "0");
            mInfo.addContent(elt);
        }

        Rectangle2D b = (def == null) ? null : def.getBounds();

        if (b != null) {
            sbuf.append("],width:" + Double.toString((b.getWidth() / 20)));
            sbuf.append(",height:" + Double.toString((b.getHeight() / 20)));
        } else {
            // could be an mp3 resource
            sbuf.append("]");
        }
        sbuf.append("};");
        addScript(sbuf.toString());
    }

    @Override
    public void importResource(List<String> sources, String sResourceName, File parent)
    {
        writeResourceLibraryDescriptor(sources, sResourceName, parent, null);
    }

    @Override
    public void importResource(List<String> sources, String sResourceName, File parent, ResourceCompiler.Offset2D offset)
    {
        writeResourceLibraryDescriptor(sources, sResourceName, parent, offset);
    }

    /* Write resource descriptor library */
    public void writeResourceLibraryDescriptor(List<String> sources, String sResourceName, File parent,
                                               ResourceCompiler.Offset2D offset)
    {
        if (mLogger.isTraceEnabled()) {
            mLogger.trace("Constructing resource library: " + sResourceName);
        }
        int width = 0;
        int height = 0;
        int fNum = 0;
        File dirfile = mEnv.getApplicationFile().getParentFile();
        String appdir = dirfile != null ? dirfile.getPath() : ".";
        if (mLogger.isTraceEnabled()) {
            mLogger.trace("appdir is: " + appdir + ", LPS.HOME() is: "+ LPS.HOME());
        }

        // Initialize the temporary buffer.
        StringBuilder sbuf= new StringBuilder();
        if (sources.isEmpty()) {
            return;
        }
        ArrayList<String> frameClassList = new ArrayList<String>();

        sbuf.append("#passthrough {\n");

        for (String sFile : sources) {
            File fFile = new File(sFile);
            String rpath;
            try {
                rpath = fFile.getCanonicalPath();
                // Fix Winblows pathname backslash lossage
                rpath = rpath.replaceAll("\\\\", "/");
            } catch (IOException err) {
                throw new ImportResourceError(fFile.toString(), err, mEnv);
            }

            sbuf.append("[Embed(source=\""+rpath+"\")]\n");
            String assetClassname = "__embed_lzasset_" + sResourceName+"_"+fNum;
            sbuf.append("var "+assetClassname+":Class;\n");
            frameClassList.add(assetClassname);
            // Definition to add to the library (without stop)
            Resource res = getMultiFrameResource(fFile.toString(), sResourceName, fNum);
            int rw = res.getWidth();
            int rh = res.getHeight();
            if (rw > width) {
                width = rw;
            }
            if (rh > height) {
                height = rh;
            }
            fNum++;
        }
        sbuf.append("}#\n");

        sbuf.append("LzResourceLibrary." + sResourceName + "={");

        if (offset != null && offset.offsetx != null) {
            sbuf.append("offsetx: "+offset.offsetx+",");
        }

        if (offset != null && offset.offsety != null) {
            sbuf.append("offsety: "+offset.offsety+",");
        }

        sbuf.append("frames: ");

        // enumerate the asset classes for each frame
        sbuf.append("[");
        String sep = "";
        for (String assetclass : frameClassList) {
            sbuf.append(sep+assetclass);
            sep = ",";
        }
        sbuf.append("]");

        mMultiFrameResourceSet.add(new Resource(sResourceName, width, height));
        sbuf.append(",width:" + Double.toString(width));
        sbuf.append(",height:" + Double.toString(height));
        sbuf.append("};");
        addScript(sbuf.toString());
    }

    ScriptCompilerInfo compilerInfo;
    Properties mScriptCompilerProps;

    @Override
    public void open(String compileType) {
        Properties props = (Properties)mProperties.clone();
        mScriptCompilerProps = props;
        props.put(org.openlaszlo.sc.Compiler.COMPILE_TYPE, compileType);

        String flexOptions = mEnv.getProperty(org.openlaszlo.sc.Compiler.FLEX_OPTIONS, "");

        props.put(org.openlaszlo.sc.Compiler.FLEX_OPTIONS, flexOptions);

        if (compileType == SWF9ParseTreePrinter.Config.SNIPPET) {
            props.setProperty(org.openlaszlo.sc.Compiler.SWF9_APPLICATION_PREAMBLE, makeLibraryPreamble());
            props.put(org.openlaszlo.sc.Compiler.SWF9_APP_CLASSNAME, LIBRARY_CLASSNAME);
            props.put(org.openlaszlo.sc.Compiler.SWF9_WRAPPER_CLASSNAME, LIBRARY_CLASSNAME);

            // This will contain a pointer to the working dir created by compiling the main app
            ScriptCompilerInfo compilerInfo = mEnv.getMainCompilationEnv().getScriptCompilerInfo();
            props.put(org.openlaszlo.sc.Compiler.COMPILER_INFO, compilerInfo);
        } else if (compileType == SWF9ParseTreePrinter.Config.LZOLIB) {
            // Set up the boilerplate code needed for the main swf9 application class
            props.put(org.openlaszlo.sc.Compiler.SWF9_APPLICATION_PREAMBLE, mAppPreamble);
            //            System.err.println("SWF9_APPLICATION_PREAMBLE="+mAppPreamble);
            props.put(org.openlaszlo.sc.Compiler.SWF9_APP_CLASSNAME, mAppMainClassname);
            props.put(org.openlaszlo.sc.Compiler.SWF9_WRAPPER_CLASSNAME, EXEC_APP_CLASSNAME);
        } else if (compileType == SWF9ParseTreePrinter.Config.APP) {
            // Set up the boilerplate code needed for the main swf9 application class
            props.put(org.openlaszlo.sc.Compiler.SWF9_APPLICATION_PREAMBLE, mAppPreamble);
            //System.err.println("SWF9_APPLICATION_PREAMBLE="+mAppPreamble);
            props.put(org.openlaszlo.sc.Compiler.SWF9_APP_CLASSNAME, mAppMainClassname);
            props.put(org.openlaszlo.sc.Compiler.SWF9_WRAPPER_CLASSNAME, EXEC_APP_CLASSNAME);

        } else {
            throw new CompilationError("unknown compileType "+compileType);
        }

        // Set snippets options into a new script compiler.
        mSCompiler = new org.openlaszlo.sc.SWF10Compiler(props);
        mSCompiler.startApp();
    }

    /** Set of lzo files which we are linking with. */
    private ArrayList<String> mLZOFiles = new ArrayList<String>();

    @Override
    public void addLZOFile(File lzo) {
        String afile = lzo.getAbsolutePath().intern();
        if (!mLZOFiles.contains(afile)) {
            mLZOFiles.add(afile);
        }
    }

    @Override
    public void finish(boolean isMainApp) throws IOException {
        //Should we emit javascript or SWF?
        //boolean emitScript = mEnv.isSWF9();

        if (mCloseCalled) {
            throw new IllegalStateException("SWF9Writer.close() called twice");
        }

        boolean debug = mProperties.getProperty("debug", "false").equals("true");

        if (isMainApp) {

            // Bring up a debug window if needed.
            if (debug) {
                boolean userSpecifiedDebugger = mEnv.getBooleanProperty(CompilationEnvironment.USER_DEBUG_WINDOW);
                // This indicates whether the user's source code already manually invoked
                // <debug> to create a debug window. If they didn't explicitly call for
                // a debugger window, instantiate one now by calling _LZDebug.makeDebugWindow()
                if (userSpecifiedDebugger) {
                    addScript(mEnv.getProperty(CompilationEnvironment.DEBUGGER_WINDOW_SCRIPT));
                } else {
                    // Create debugger window with default init options
                    addScript("Debug.makeDebugWindow()");
                }
            }


            // Put the canvas sprite on the 'stage'.
            addScript("addChild(canvas.sprite)");

            // TODO [hqm 2010-06] This is a workaround for LPP-8710,
            // can be removed when swf9 support is dropped.
            if ("swf9".equals(mEnv.getRuntime())) {
                // <resource name="__LFCSWF9handcursor" src="resources/handcursor.png"/>
                Offset2D offset = new Offset2D("-4", "0");
                importResource(LPS.getComponentsDirectory()+File.separator+
                               "lz"+File.separator+"resources"+File.separator+"handcursor.png", "__LFCSWF9handcursor",
                               offset);
            }


            // TODO [hqm 2010-05] I'm putting the calls to the lzo
            // startup methods here, but they should really go in
            // right when they are added in the addLZOFile method
            // above. The problem is if you do that they appear before
            // the script to instantiate the canvas is added.  If I
            // can find where that happens, and make it happen earlier
            // or move it, I could let the lzo script calls go in when
            // they are collected by addLZOFile.


            for (String lzo : mLZOFiles) {
                // Add in a top level all in the main app to invoke the lzo
                // lib's runtToplevel Definitions method
                addScript("(new "+ LibraryWriter.fileToSymbol(new File(lzo))+"()).runToplevelDefinitions();\n");
            }


            // Tell the canvas we're done loading.
            addScript("canvas.initDone()");


        }
        // We need to take the list of lzo/swf10 files that we found while compiling, and
        // copy their swc files to the tmp workdir.
        // Then pass that list of filenames to the backend
        compilerInfo = mEnv.getScriptCompilerInfo();

        Set<File> swcfiles = new HashSet<File>();
        for (String lzo : mLZOFiles) {
            swcfiles.add(copyFromLZO(new File(lzo), "swc", compilerInfo.workDir));
        }
        // Pass list of swc files to the compiler, so it can pass them to flex linker
        mSCompiler.setLZOLibraries(swcfiles);

        try {
            InputStream input = mSCompiler.finishApp();

            // Make a note of the location of the as3 working file dir
            // used for compiling the main app. This is needed so the
            // flex compiler can link any loadable libraries
            // (<import>'s) against it.
            File workdir = compilerInfo.workDir;
            compilerInfo.mainAppWorkDir = workdir;

            FileUtils.send(input, mStream);
        } catch (org.openlaszlo.sc.CompilerException e) {
            String solution = SolutionMessages.findSolution(e.getMessage());
            CompilationError sol = new CompilationError(e);
            sol.setSolution(solution);
            throw sol;
        } catch (Exception e) {
            throw new ChainedException(e);
        }

    }

    @Override
    public void close() throws IOException {
        mStream.close();
        mCloseCalled = true;
    }

    /** Extract the named file from an lzo zip archive, and copy to the specified directory.
        Return the newly created File
        @param lzofile location of lzo file in app source dir
        @param entryName binary lib base name in lzo, e.g., "swc" or "js"
        @param workdir destination directory
        @return new copy of extracted file
    */
    public File copyFromLZO(File lzofile, String entryName,  File workdir)
        throws IOException {
        InputStream ifs = new BufferedInputStream( new java.io.FileInputStream(lzofile) );
        // Scan for entry named entryName
        ZipInputStream zis = new ZipInputStream(ifs);
        ZipEntry entry;
        // TODO [hqm 2010-06] When the compiler no longer forces the 'public' flag when doing debug compiles,
        // we won't need to make sure that compiler options match exactly when linking against external library,
        // and we can just use any "swf10" .swc file we find in the external library.
        String nameWithOptions = ObjectWriter.addLZOCompilerOptionFlags(entryName, mEnv);
        String debugFlagName = mEnv.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY) ? "debug" : "non-debug";
        while (true) {
            entry = zis.getNextEntry();
            if (entry == null) {
                throw new CompilationError("copyFromLZO could not find entry "+entryName+" in .lzo zip file "+lzofile);
            }
            // In case this is an external LZO we're linking against,
            // we need to make sure that the compiler options
            // match. Unfortunately you cannot link a non-debug
            // version of a library against a debug version, because
            // overrides fail because methods have become 'public' in
            // debug version vs non-public in non-debug versions.
            if (entry.getName().startsWith(entryName)) {
                if (entry.getName().equals(nameWithOptions)) {
                    break;
                } else {
                    throw new CompilationError("copyFromLZO expecting to find '"+debugFlagName +"' version in lzo archive "+lzofile+
                                          ", but found '"+ entry.getName() +
                                          "'; when building a (swf10) lzo any external lzo's that it references must have matching debug flag.");
                }
            }
        }
        String fprefix = LibraryWriter.fileToSymbol(lzofile);

        //System.err.println("lzo pathname prefix = "+fprefix);
        File swc = new File(workdir, fprefix + ".swc");

        FileUtils.send(zis, new FileOutputStream(swc));
        zis.close();
        //System.err.println("swc file copied to "+swc);
        return swc;
    }

    @Override
    public void openSnippet(String url) throws IOException {
        this.liburl = url;
    }

    /** The user 'main' class, which extends LFCApplication */
    public final static String MAIN_APP_CLASSNAME = "LzApplication";

    /** The top level class executed first, it creates a LzApplication object */
    public final static String EXEC_APP_CLASSNAME = "LzSpriteApplication";

    public final static String LFC_CLASSNAME = "LFCApplication";

    /** The class to use when compiling a debug eval statement */
    public final static String DEBUG_EVAL_SUPERCLASS = "DebugExec";
    public final static String DEBUG_EVAL_CLASSNAME  = "DebugEvaluate";

    /** The "main" class name for 'import' (runtime loadable) libraries */
    public final static String LIBRARY_CLASSNAME = "LzRuntimeLoadableLib";

    /** List of AS3 imports needed to compile an app */
    public static final String imports =
            "#passthrough (toplevel:true) {  \n" +
            "import flash.display.*;\n" +
            "import flash.events.*;\n" +
            "import flash.utils.*;\n" +
            "import flash.text.*;\n" +
            "import flash.system.*;\n" +
            "import flash.net.*;\n" +
            "import flash.ui.*;\n" +
            "import flash.external.*;\n" +
            "}#\n";

    /** Create application boilerplate preamble as3 code
     */
    public String makeApplicationPreamble() {
        // Turn off all annotation for the preamble
        String source = "{\n#pragma 'debug=false'\n#pragma 'debugSWF9=false'\n#pragma 'debugBacktrace=false'\n";
        source += "public class " + MAIN_APP_CLASSNAME +
            " extends " +  LFC_CLASSNAME + " {\n " + imports + "\n" +
            "public function " + MAIN_APP_CLASSNAME + "(sprite:Sprite=null) {\n" +
            "super(sprite);\n" +
            "}\n" +
            "}\n";
        source += "public class " + EXEC_APP_CLASSNAME +
            " extends Sprite {\n " + imports +
              "var app:LzApplication;\n" +
              "function " + EXEC_APP_CLASSNAME + "() {" +
                "app = new " + MAIN_APP_CLASSNAME + "(this);\n" +
              "}\n" +
            "}\n";
        // TODO: should come from extern file, maybe with JSP interpolation
        // preloader code from kernel/swf9/LzPreloader.as
        // BE SURE TO KEEP THIS IN SYNC
        source += "// based on http://www.ghost23.de/blogarchive/2008/04/as3-application-1.html\n public class LzPreloader extends MovieClip {" + imports + "public function LzPreloader() { var id = stage.loaderInfo.parameters.id; try{ ExternalInterface.call('lz.embed.applications.' + id + '._sendPercLoad', 0); } catch (e) {} stop(); root.loaderInfo.addEventListener(ProgressEvent.PROGRESS,loadProgress); addEventListener(Event.ENTER_FRAME, enterFrame); } public function enterFrame(event:Event):void { if (framesLoaded == totalFrames) { root.loaderInfo.removeEventListener(ProgressEvent.PROGRESS,loadProgress); nextFrame(); var mainClass:Class = Class(loaderInfo.applicationDomain.getDefinition('LzSpriteApplication')); if(mainClass) { var main:DisplayObject = DisplayObject(new mainClass()); if (main) { removeEventListener(Event.ENTER_FRAME, enterFrame); stage.addChild(main); stage.removeChild(this); } } } } private function loadProgress(event:Event):void { var percload:Number = Math.floor(root.loaderInfo.bytesLoaded / root.loaderInfo.bytesTotal * 100); var id = stage.loaderInfo.parameters.id; if (id) { try{ ExternalInterface.call('lz.embed.applications.' + id + '._sendPercLoad', percload); } catch (e) {} } } } // end of preloader\n";
        // Close pragma block
        source += "\n}\n";
        return source;
    }

    /** Create import library  boilerplate preamble as3 code
     */
    public String makeLibraryPreamble() {
        String source = "public class " + LIBRARY_CLASSNAME +
            " extends LzBaseLib {\n " + imports + "\n" +
            "}\n";

        return source;
    }

    @Override
    public void closeSnippet() throws IOException {

        // Define the base app class for an import library. Must be
        // structured this way to conform to what the as3 script
        // compiler expects for constructing the main app class.
        addScript("class LzBaseLib extends Sprite { \n" +
                  imports +  "\n" +
                  "public function runToplevelDefinitions() {}\n" +
                  "public function exportClassDefs(link:Object) {\n" +
                  "this.runToplevelDefinitions();\n" +
                  "}\n" +
                  "}\n");

        // Callback to let library know we're done loading
        //
        addScript("LzLibrary.__LZsnippetLoaded('"+this.liburl+"')");

        if (mCloseCalled) {
            throw new IllegalStateException("SWF9Writer.close() called twice");
        }

        try {
            InputStream input = mSCompiler.finishApp();
            FileUtils.send(input, mStream);
        } catch (org.openlaszlo.sc.CompilerException e) {
            throw new CompilationError(e);
        } catch (Exception e) {
            throw new ChainedException(e);
        }

        mCloseCalled = true;
    }

    @Override
    FontManager getFontManager() {
        return mFontManager;
    }

    @Override
    public boolean isDeviceFont(String face) {
        return (mDeviceFontTable.get(face) != null);
    }

    @Override
    public void setDeviceFont(String face) {
        mDeviceFontTable.put(face,"true");
    }

    @Override
    public void setFontManager(FontManager fm) {
        this.mFontManager = fm;
    }


    /**
     * Import a font of a given style into the SWF we are writing.
     *
     * @param fileName filename for font in LZX
     * @param face face name of font
     * @param style style of font
     * @param embedAsCFF TTF or CFF font embedding (CFF for Text Layout Framework / Flash Player 10)
     * @param CompilationEnvironment compilation environment
     */
    @Override
    void importFontStyle(String fileName, String face, String style, String embedAsCFF,
            CompilationEnvironment env)
        throws FileNotFoundException, CompilationError {

        int styleBits = FontInfo.styleBitsFromString(style);

        if (mLogger.isTraceEnabled()) {
            mLogger.trace(
            /* (non-Javadoc)
             * @i18n.test
             * @org-mes="importing " + p[0] + " of style " + p[1]
             */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                SWFWriter.class.getName(),"051018-1225", new Object[] {face, style})
                      );
        }

        FontInfo fontInfo = mEnv.getCanvas().getFontInfo();
        boolean isDefault = false;

        Font font = importFont(fileName, face, styleBits, embedAsCFF, false);

        if (fontInfo.getName().equals(face)) {
            if (styleBits == FontInfo.PLAIN) {
                isDefault = true;
            }
        }

        FontFamily family = mFontManager.getFontFamily(face, true);

        switch (styleBits) {
          case FontInfo.PLAIN:
            if (family.plain != null) {
                if (!isDefault) {
                    warn(env,
                         /* (non-Javadoc)
                          * @i18n.test
                          * @org-mes="Redefined plain style of font: " + p[0]
                          */
                         org.openlaszlo.i18n.LaszloMessages.getMessage(
                             SWFWriter.class.getName(),"051018-1252", new Object[] {face})
                         );
                }
            }
            family.plain = font; break;
          case FontInfo.BOLD:
            if (family.bold != null) {
                warn(env,
                     /* (non-Javadoc)
                      * @i18n.test
                      * @org-mes="Redefined bold style of font: " + p[0]
                      */
                     org.openlaszlo.i18n.LaszloMessages.getMessage(
                         SWFWriter.class.getName(),"051018-1265", new Object[] {face})
                     );
            }
            family.bold = font; break;
          case FontInfo.ITALIC:
            if (family.italic != null) {
                warn(env,
                     /* (non-Javadoc)
                      * @i18n.test
                      * @org-mes="Redefined italic style of font: " + p[0]
                      */
                     org.openlaszlo.i18n.LaszloMessages.getMessage(
                         SWFWriter.class.getName(),"051018-1277", new Object[] {face})
                     );
            }
            family.italic = font; break;
          case FontInfo.BOLDITALIC:
            if (family.bitalic != null) {
                warn(env,
                     /* (non-Javadoc)
                      * @i18n.test
                      * @org-mes="Redefined bold italic style of font: " + p[0]
                      */
                     org.openlaszlo.i18n.LaszloMessages.getMessage(
                         SWFWriter.class.getName(),"051018-1289", new Object[] {face})
                     );
            }
            family.bitalic = font; break;
          default:
            throw new ChainedException(
                /* (non-Javadoc)
                 * @i18n.test
                 * @org-mes="Unexpected style"
                 */
                org.openlaszlo.i18n.LaszloMessages.getMessage(
                    SWFWriter.class.getName(),"051018-1300")
                                       );
        }

        if (mLogger.isTraceEnabled()) {
            mLogger.trace(
            /* (non-Javadoc)
             * @i18n.test
             * @org-mes="Adding font family: " + p[0]
             */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                SWFWriter.class.getName(),"051018-1502", new Object[] {face})
                      );
        }


        // TODO [ hqm 2008-01 ]
        // Add entry to LFC font manager table. Do we actually need to add font metrics ?
        // Does anybody use them?

        StringBuilder sbuf = new StringBuilder();
        sbuf.append("LzFontManager.addFont('" + face + "', " );
        appendFont(sbuf, family.plain, family.getBounds(FontInfo.PLAIN));
        sbuf.append(",");
        appendFont(sbuf, family.bold, family.getBounds(FontInfo.BOLD));
        sbuf.append(",");
        appendFont(sbuf, family.italic, family.getBounds(FontInfo.ITALIC));
        sbuf.append(",");
        appendFont(sbuf, family.bitalic, family.getBounds(FontInfo.BOLDITALIC));
        sbuf.append("\n)\n");
        addScript(sbuf.toString());
    }


    /**
     * Import a font into the SWF we are writing
     *
     * @param fileName name of font file
     * @param face font name of font in LZX
     * @param styleBits
     * @param embedAsCFF if "true", the font embedding will the property embedAsCFF='true'
     */
    private Font importFont(String fileName, String face, int styleBits, String embedAsCFF,
        boolean replace)
        throws FileNotFoundException, CompilationError {

        if (isDeviceFont(face)) {
            return Font.createDummyFont(face);
        }

        File fontFile = new File(fileName);

        Font font = Font.createDummyFont(face);
        long fileSize =  FileUtils.getSize(fontFile);

        // TODO [hqm 2008-01] Do we need to parse out the font metrics
        // here? Code is in SWFWriter, I haven't copied it over here yet.
        // That requires the JGenerator transcoder, which only does the first
        // 256 chars in the font, kind of useless.

        Element elt = new Element("font");
        elt.setAttribute("face", face);
        elt.setAttribute("style", FontInfo.styleBitsToString(styleBits, true));
        elt.setAttribute("location", fontFile.getAbsolutePath());
        elt.setAttribute("source", fileName);
        elt.setAttribute("filesize", "" + fileSize);
        mInfo.addContent(elt);

        // Put in our face name
        font.fontName = face;

        // Clean out existing styles.
        font.flags &= ~(Font.BOLD | Font.ITALIC);

        // Write in ours.
        if ((styleBits & FontInfo.BOLD) != 0) {
            font.flags |= Font.BOLD;
        }
        if ((styleBits & FontInfo.ITALIC) != 0) {
            font.flags |= Font.ITALIC;
        }

        /*
          [Embed(mimeType='application/x-font', source='../assets/BELLB.TTF',
          fontName='myBellFont', fontWeight='bold')]
          // This variable is not used. It exists so that the compiler will link
          // in the font.
          private var myFontClass:Class;

        */

        StringBuilder sbuf = new StringBuilder();
        sbuf.append("#passthrough {\n");
        String rpath;
        File inputFile = fontFile;
        try {
            rpath = inputFile.getCanonicalPath();
            // Fix Winblows pathname backslash lossage
            rpath = rpath.replaceAll("\\\\", "/");
        } catch (IOException e) {
            throw new ImportResourceError(inputFile.toString(), e, mEnv);
        }
        String weight = "plain";
        String style = "plain";
        /* weight := plain|normal, bold
           style := plain|normal, italic
        */

        if ((styleBits & FontInfo.BOLD) != 0) {
            weight = "bold";
        }
        if ((styleBits & FontInfo.ITALIC) != 0) {
            style = "italic";
        }

        sbuf.append("[Embed(mimeType='application/x-font', source='"+rpath+"', fontName='"+
                    face+"', fontWeight='"+weight+"'," +
                    "fontStyle='" + style + "'," +
                    "advancedAntiAliasing='true'" +"," +
                    "embedAsCFF='" + embedAsCFF + "'" +
                    ")]\n");


        String assetClassname = "__embed_lzfont_" + faceCounter++;
        sbuf.append("var "+assetClassname+":Class;\n");
        sbuf.append("Font.registerFont("+assetClassname+");\n");

        sbuf.append("}#\n");
        addScript(sbuf.toString());
        return font;
    }

    // Gensym for embedded font classname
    static int faceCounter = 1;


    /**
     * @return height of fontinfo in pixels
     * @param fontInfo
     */
    double getFontHeight (FontInfo fontInfo) {
        return fontHeight(getFontFromInfo(fontInfo));
    }

    /**
     * @return lineheight which lfc LzInputText expects for a given fontsize
     */
    double getLFCLineHeight (FontInfo fontInfo, int fontsize) {
        return lfcLineHeight(getFontFromInfo(fontInfo), fontsize);
    }

    /**
     * Convert em units to pixels, truncated to 2 decimal places.
     * Slow float math... but simple code to read.
     *
     * @param units number of 1024 em units
     * @return pixels
     */
    private static double emUnitsToPixels(int units) {
        int x = (100 * units * DEFAULT_SIZE) / 1024;
        return (double)(x / 100.0);
    }

    /**
     * Compute font bounding box
     *
     * @param font
     */
    static double fontHeight(Font font) {
        if (font == null) { return 0; }
        double ascent  = emUnitsToPixels(font.ascent);
        double descent = emUnitsToPixels(font.descent);
        double leading = emUnitsToPixels(font.leading);
        double lineheight = ascent+descent+leading;
        return lineheight;
    }

    /**
     * Compute font bounding box
     *
     * @param font
     */
    double lfcLineHeight(Font font, int fontsize) {
        double ascent  = emUnitsToPixels(font.ascent);
        double descent = emUnitsToPixels(font.descent);
        //double leading = emUnitsToPixels(font.leading);
        double lineheight = mTextLeading + ((ascent+descent) * ((double)fontsize) / DEFAULT_SIZE);
        return lineheight;
    }

    /**
     * Appends font to actionscript string buffer
     * @param actions string
     * @param font font
     */
    private static void appendFont(StringBuilder actions, Font font,
        Rectangle2D[] bounds) {

        final String newline = "\n  ";
        actions.append(newline);

        if (font == null) {
            actions.append("null");
            return;
        }

        double ascent  = emUnitsToPixels(font.ascent);
        double descent = emUnitsToPixels(font.descent);
        double leading = emUnitsToPixels(font.leading);

        final String comma = ", ";

        actions.append("{");
        actions.append("ascent:");
        actions.append(ascent);
        actions.append(comma);
        actions.append("descent:");
        actions.append(descent);
        actions.append(comma);
        actions.append("leading:");
        actions.append(leading);
        actions.append(comma);
        actions.append("advancetable:");

        int idx, adv;

        actions.append(newline);
        actions.append("[");
        // FIXME: [2003-03-19 bloch] We only support ANSI 8bit (up to
        // 255) char encodings.  We lose the higher characters is
        // UNICODE and we don't support anything else.

        for(int i = 0; i < 256; i++) {
            idx = font.getIndex(i);
            adv = font.getAdvanceValue(idx);

            // Convert to pixels rounded to nearest 100th
            double advance = emUnitsToPixels(adv);
            actions.append(advance);
            if (i != 255) {
                actions.append(comma);
            }

            if (i%10 == 9) {
                actions.append(newline);
            }
        }
        actions.append("],");
        actions.append(newline);

        actions.append("lsbtable:");
        actions.append(newline);
        actions.append("[");

        int m;
        int adj;
        for(int i = 0; i < 256; i++) {
            idx = font.getIndex(i);
            try {
                m = (int)bounds[idx].getMinX();
                //max = (int)bounds[idx].getMaxX();
            } catch (Exception e) {
                m = 0;
                //max = 0;
            }
            adv = font.getAdvanceValue(idx);
            adj = m;
            if (adj < 0) adj = 0;

            /* The following makes the lsb bigger
               but is strictly wrong */
            /*max = max - adv;
            if (max < 0) max = 0;

            if (max > adj) {
                adj = max;
            }*/

            // Convert to pixels rounded to nearest 100th
            double lsb = emUnitsToPixels(adj);
            actions.append(lsb);
            if (i != 255) {
                actions.append(comma);
            }

            if (i%10 == 9) {
                actions.append(newline);
            }
        }

        actions.append("],");

        actions.append(newline);
        actions.append("rsbtable:");
        actions.append(newline);
        actions.append("[");

        for(int i = 0; i < 256; i++) {
            idx = font.getIndex(i);
            try {
                m = (int)bounds[idx].getMaxX();
            } catch (Exception e) {
                m = 0;
            }
            adv = font.getAdvanceValue(idx);
            adj = m - adv;
            if (adj < 0) adj = 0;

            // Convert to pixels rounded to nearest 100th
            double rsb = emUnitsToPixels(adj);
            actions.append(rsb);
            if (i != 255) {
                actions.append(comma);
            }

            if (i%10 == 9) {
                actions.append(newline);
            }
        }

        actions.append("]}");
    }



    /**
     * @return font given a font info
     */
    private Font getFontFromInfo(FontInfo fontInfo) {
        // This will bring in the default bold font if it's not here yet
        checkFontExists(fontInfo);
        String fontName = fontInfo.getName();
        FontFamily family   = mFontManager.getFontFamily(fontName);
        String style = fontInfo.getStyle();

        if (family == null) {
            return null;
            /*
            throw new CompilationError("Font '" + fontName +
                "' used but not defined");
            */
        }
        Font font = family.getStyle(fontInfo.styleBits);
        if (font == null) {
            throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Font '" + p[0] + "' style ('" + p[1] + "') used but not defined"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SWFWriter.class.getName(),"051018-2089", new Object[] {fontName, style})
                                        );
        }
        return font;
    }

    /**
     * @return true if the font exists
     *
     * If this is the default bold font and it hasn't been
     * declared, import it.
     */
    boolean checkFontExists(FontInfo fontInfo) {

        // Bulletproofing...
        if (fontInfo.getName() == null) {
            return false;
        }

        boolean a = mFontManager.checkFontExists(fontInfo);
        if (a) {
            return a;
        }

        if (fontInfo.getName().equals(mDefaultFontName) &&
            fontInfo.styleBits == FontInfo.PLAIN) {
            try {
                    File f = mEnv.resolve(mDefaultFontFileName, null);
                    importFontStyle(f.getAbsolutePath(), mDefaultFontName, "plain", "false", mEnv);
                } catch (FileNotFoundException fnfe) {
                    throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="default font " + p[0] + " missing " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SWFWriter.class.getName(),"051018-2125", new Object[] {mDefaultFontFileName, fnfe})
                                        );
            }
            return true;
        }

        if (fontInfo.getName().equals(mDefaultFontName) &&
            fontInfo.styleBits == FontInfo.BOLD) {
            try {
                File f = mEnv.resolve(mDefaultBoldFontFileName, null);
                importFontStyle(f.getAbsolutePath(), mDefaultFontName, "bold", "false", mEnv);
            } catch (FileNotFoundException fnfe) {
                throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="default bold font " + p[0] + " missing " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SWFWriter.class.getName(),"051018-2143", new Object[] {mDefaultBoldFontFileName, fnfe})
                                                );
            }
            return true;
        }

        if (fontInfo.getName().equals(mDefaultFontName) &&
            fontInfo.styleBits == FontInfo.ITALIC) {
            try {
                File f = mEnv.resolve(mDefaultItalicFontFileName, null);
                importFontStyle(f.getAbsolutePath(), mDefaultFontName, "italic", "false", mEnv);
            } catch (FileNotFoundException fnfe) {
                throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="default italic font " + p[0] + " missing " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SWFWriter.class.getName(),"051018-2161", new Object[] {mDefaultItalicFontFileName, fnfe})
                                                );
            }
            return true;
        }

        if (fontInfo.getName().equals(mDefaultFontName) &&
            fontInfo.styleBits == FontInfo.BOLDITALIC) {
            try {
                File f = mEnv.resolve(mDefaultBoldItalicFontFileName, null);
                importFontStyle(f.getAbsolutePath(), mDefaultFontName, "bold italic", "false", mEnv);
            } catch (FileNotFoundException fnfe) {
                throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="default bold italic font " + p[0] + " missing " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SWFWriter.class.getName(),"051018-2179", new Object[] {mDefaultBoldItalicFontFileName, fnfe})
                                                );
            }
            return true;
        }

        return false;
    }

    @Override
    void addPreloaderScript(String script) { } ;
    @Override
    void addPreloader(CompilationEnvironment env) { } ;

    @Override
    public void importBaseLibrary(String library, CompilationEnvironment env) {
        env.warn("SWF9Writer does not implement importBaseLibrary");
    }
}

