/*****************************************************************************
 * ObjectWriter.java
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
import org.openlaszlo.iv.flash.api.FlashDef;
import org.openlaszlo.iv.flash.api.FlashFile;
import org.openlaszlo.iv.flash.api.FlashObject;
import org.openlaszlo.iv.flash.api.Frame;
import org.openlaszlo.iv.flash.api.Script;
import org.openlaszlo.iv.flash.api.Timeline;
import org.openlaszlo.iv.flash.api.sound.MP3Sound;
import org.openlaszlo.iv.flash.api.sound.Sound;
import org.openlaszlo.iv.flash.util.FlashBuffer;
import org.openlaszlo.iv.flash.util.FontsCollector;
import org.openlaszlo.iv.flash.util.IVException;
import org.openlaszlo.media.Transcoder;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.media.TranscoderException;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;

/** Accumulates code, XML, and assets to an object file.
 *
 * Properties documented in Compiler.getProperties.
 */
abstract class ObjectWriter {
    /** Stream to write to. */
    protected OutputStream mStream = null;

    /** True iff close() has been called. */
    protected boolean mCloseCalled = false;

    FontsCollector mFontsCollector = new FontsCollector();

    /** InfoXML */
    // [todo hqm 2006-08-02] change the name of this to something more
    // generic than 'swf'
    protected Element mInfo = new Element("swf");

    protected final CompilationEnvironment mEnv;

    /** Set of resoures we're importing into the output */
    protected final HashSet<Resource> mMultiFrameResourceSet = new HashSet<Resource>();

    /** Unique name supply for clip/js names */
    protected SymbolGenerator mNameSupply = new SymbolGenerator("$LZ");

    /** Has the preloader been added? */
    protected boolean mPreloaderAdded = false;

    /** Constant */
    protected final int TWIP = 20;

    /** Properties */
    protected Properties mProperties;

    protected String liburl = "";

    /** media cache for transcoding */
    protected CompilerMediaCache mCache = null;

    /** <String,Resource> maps resource files to the Resources
     * definition in the swf file. */
    protected Map<String, Resource> mResourceMap = new HashMap<String, Resource>();
    protected Map<String, Resource> mClickResourceMap = new HashMap<String, Resource>();
    protected Map<String, Resource> mMultiFrameResourceMap = new HashMap<String, Resource>();

    /** Logger */
    protected static Logger mLogger = org.apache.log4j.Logger.getLogger(ObjectWriter.class);

    /** Canvas Height */
    protected int mHeight = 0;

    /** Canvas Width */
    protected int mWidth = 0;

    protected int mRecursionLimit   = 0;
    protected int mExecutionTimeout = 0;

    /**
     * Initialize jgenerator
     */
    static {
        org.openlaszlo.iv.flash.util.Util.init(LPS.getConfigDirectory());
    }

    /** Logger for jgenerator */
    /**
     * Initializes a ObjectWriter with an OutputStream to which a new object file
     * will be written when <code>ObjectWriter.close()</code> is called.
     *
     * @param stream A <code>java.io.OutputStream</code> that the
     * movie will be written to.
     * @param props list of properties
     * @param cache media cache
     * @param importLibrary If true, the compiler will add in the LaszloLibrary.
     */
    ObjectWriter(Properties props, OutputStream stream,
              CompilerMediaCache cache,
              boolean importLibrary,
              CompilationEnvironment env) {
        this.mProperties   = props;
        this.mCache        = cache;
        this.mStream       = stream;
        this.mEnv          = env;
    }

    public void setOutputStream(OutputStream s) {
        this.mStream = s;
    }

    /** Initialize for compiling script to output object code */
    public void open(String compileType) { }

    /** Compiler lets us know when the all classes have been defined, and resources declared */
    public void schemaDone() throws IOException  { }

    /**
     * Sets the canvas for the app
     *
     * @param canvas
     *
     */
    abstract void setCanvas(Canvas canvas, String canvasConstructor);

    abstract void setCanvasDefaults(Canvas canvas, CompilerMediaCache mc);

    /** Compiles the specified script to bytecodes
     * and add its bytecodes to the app.
     *
     * @param script the script to be compiled
     * @return the number of bytes
     */
    public abstract int addScript(String script);

    @SuppressWarnings("serial")
    class ImportResourceError extends CompilationError {

        ImportResourceError(String fileName, CompilationEnvironment env) {
            super("Can't import " + stripBaseName(fileName, env));
        }
        ImportResourceError(String fileName, String type, CompilationEnvironment env) {
            super("Can't import " + type + " " + stripBaseName(fileName, env));
        }
        ImportResourceError(String fileName, Exception e, CompilationEnvironment env) {
            super("Can't import " + stripBaseName(fileName, env) + ": " + e.getMessage());
        }
        ImportResourceError(String fileName, Exception e, String type, CompilationEnvironment env) {
            super("Can't import " + type + " " + stripBaseName(fileName, env) + ": " + e.getMessage());
        }

    }

    public static String stripBaseName (String fileName, CompilationEnvironment env) {
        try {
            fileName = (new File(fileName)).getCanonicalFile().toString();
        } catch (java.io.IOException e) {
        }
        String base = env.getErrorHandler().fileBase;
        if (base == null || "".equals(base)) {
            return fileName;
        }
        base = (new File(base)).getAbsolutePath() + File.separator;
        int i = 1;
        // Find longest common substring
        while (i < base.length() && fileName.startsWith(base.substring(0, i))) { i++; }
        // remove base string prefix
        return fileName.substring(i-1);
    }

    /**
     * Returns the mime-type of the input file and throws an error for invalid
     * mime-types
     *
     * @param fileName
     * @return
     * @throws IOException
     */
    protected final String getAndCheckMimeType(String fileName)
        throws IOException {
        String mimeType = MimeType.fromExtension(fileName);
        if (!Transcoder.canTranscode(mimeType, MimeType.SWF)
            && !mimeType.equals(MimeType.SWF)) {
            mimeType = Transcoder.guessSupportedMimeTypeFromContent(fileName);
            if (mimeType == null || mimeType.equals("")) {
                throw new ImportResourceError(fileName, new Exception(
                /* (non-Javadoc)
                * @i18n.test
                * @org-mes="bad mime type"
                */
                org.openlaszlo.i18n.LaszloMessages.getMessage(
                    ObjectWriter.class.getName(), "051018-549")), mEnv);
            }
        }

        return mimeType;
    }

    /**
     *
     * @param name
     * @param mimeType
     * @param fileName
     * @param fileSize
     * @return
     */
    protected final Element createResourceInfoElement(String name,
        String mimeType, String fileName, long fileSize) {
        Element elt = new Element("resource");
        elt.setAttribute("name", name);
        elt.setAttribute("mime-type", mimeType);
        elt.setAttribute("source", fileName);
        elt.setAttribute("filesize", "" + fileSize);

        return elt;
    }

   /** Find a resource for importing into a movie and return a flashdef.
     * Includes stop action.
     *
     * @param fileName file name of the resource
     * @param name name of the resource
     */
    protected Resource getResource(String fileName, String name)
        throws ImportResourceError
    {
        return getResource(fileName, name, true);
    }

   /** Find a resource for importing into a movie and return a flashdef.
     *
     * @param name name of the resource
     * @param fileName file name of the resource
     * @param stop include stop action if true
     */
    protected Resource getResource(String fileName, String name, boolean stop)
      throws ImportResourceError
    {
        try {
            String mimeType = getAndCheckMimeType(fileName);
            // No need to get these from the cache since they don't need to be
            // transcoded and we usually keep the cmcache on disk.
            if (mimeType.equals(MimeType.SWF)) {
                long fileSize =  FileUtils.getSize(new File(fileName));
                mInfo.addContent(createResourceInfoElement(name, mimeType, fileName, fileSize));

                return importSWF(fileName, name, false);
            } else if (mimeType.equals(MimeType.MP3)
                || mimeType.equals(MimeType.XMP3)) {
                // TODO: [2002-12-3 bloch] use cache for mp3s; for now we're skipping it
                // arguably, this is a fixme
                return importMP3(fileName, name);
            } else {
                return importImage(fileName, name, mimeType, stop);
            }
        } catch (Exception e) {
            mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Can't get resource " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ObjectWriter.class.getName(),"051018-604", new Object[] {fileName})
);
            throw new ImportResourceError(fileName, e, mEnv);
        }

    }

    abstract void addPreloaderScript(String script);
    abstract void addPreloader(CompilationEnvironment env);

    /** Import a resource file into the preloader movie.
     * Using a name that already exists clobbers the
     * old resource (for now).
     *
     * @param fileName file name of the resource
     * @param name name of the MovieClip/Sprite
     * @throws CompilationError
     */
    abstract public void importPreloadResource(String fileName, String name)
      throws ImportResourceError;

    abstract public void importPreloadResource(File fileName, String name)
      throws ImportResourceError;

    /** Import a multiframe resource into the current movie.  Using a
     * name that already exists clobbers the old resource (for now).
     */
    abstract public void importPreloadResource(List<String> sources, String name, File parent)
      throws ImportResourceError;



    /** Imports file, if it has not previously been imported, and
     * returns in any case the name of the clip that refers to it.
     * File should refer to a graphical asset. */
    public String importResource(File file)
    {
        if (mLogger.isTraceEnabled()) {
        mLogger.trace("ObjectResource:importResource(File) "+file.getPath());
        }
        Resource res;

        try {
            file = file.getCanonicalFile();
            res = mResourceMap.get(file.getPath());
        } catch (java.io.IOException e) {
            throw new CompilationError(e);
        }
        if (res == null) {
            String clipName = createName();
            importResource(file.getPath(), clipName);
            return clipName;
        } else {
            return res.getName();
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
    abstract public void importResource(String fileName, String name)
      throws ImportResourceError;

    abstract public void importResource(File fFile, String name)
      throws ImportResourceError;

    abstract public void importResource(List<String> sources, String name, File parent);



      abstract public void importResource(String fileName, String name, ResourceCompiler.Offset2D offset)
      throws ImportResourceError;
    /*
    abstract public void importResource(File fFile, String name, ResourceCompiler.Offset2D offset)
      throws ImportResourceError;
    */

    abstract public void importResource(List<String> sources, String name, File parent, ResourceCompiler.Offset2D offset);


    /** Returns a new unique js name. */
    String createName() {
        return mNameSupply.next();
    }


    /**
     * collect fonts for later use
     */
    private void collectFonts(Script s) {

        Timeline tl = s.getTimeline();
        // If preloader is added, skip frame 0. Assume that preloader is only
        // one frame long starting at frame 0.
        for(int i=0; i<tl.getFrameCount(); i++ ) {
            Frame frame = tl.getFrameAt(i);
            for( int k=0; k<frame.size(); k++ ) {
                FlashObject fo = frame.getFlashObjectAt(k);
                fo.collectFonts( mFontsCollector );
                //mLogger.trace("FONTS size "
                         //+ mFontsCollector.getFonts().size());
            }
        }
    }

    /**
     *
     *
     * @param fileName
     * @param name
     * @param mimeType
     * @param addStop
     * @return
     * @throws IOException
     * @throws TranscoderException
     * @throws IVException
     */
    protected Resource importImage(String fileName, String name,
        String mimeType, boolean addStop) throws IOException,
        TranscoderException, IVException {
        File outputFile;
        File inputFile = new File(fileName);
        if (mCache != null) {
            outputFile = mCache.transcode(inputFile, mimeType, MimeType.SWF);
        } else {
            outputFile = File.createTempFile("lzmtranscode", null);
            InputStream input = Transcoder.transcode(inputFile, mimeType,
                MimeType.SWF);
            FileOutputStream fout = new FileOutputStream(outputFile);
            FileUtils.send(input, fout);
            fout.close();
        }

        if (mLogger.isTraceEnabled()) {
            mLogger.trace(
            /* (non-Javadoc)
            * @i18n.test
            * @org-mes="importing: " + p[0] + " as " + p[1] + " from cache; size: " + p[2]
            */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                ObjectWriter.class.getName(), "051018-584", new Object[] {
                    fileName, name, outputFile.length() }));
        }

        long fileSize = FileUtils.getSize(outputFile);
        mInfo.addContent(createResourceInfoElement(name, mimeType, fileName,
            fileSize));

        Resource res = importSWF(outputFile.getPath(), name, addStop);
        if (mCache == null) {
            // clean up transcoded file if not being used by the cache
            outputFile.delete();
        }
        return res;
    }

    /**
     * @param fileName
     * @param name
     * @param addStop if true, add stop action to last frame
     */
    protected Resource importSWF(String fileName, String name, boolean addStop)
        throws IVException, FileNotFoundException  {

        // JGenerator can only read Flash version <= 8.  The as3
        // compiler only needs the pathname in order to embed a swf,
        // so if the target runtime is AS3, we will just return an
        // empty Flash def.

        FlashFile f = FlashFile.parse(fileName);

        if (mEnv.isAS3() && f.getVersion() >= 9) {
            // For AS3, return empty resource for Flash 9/10 resources
            return new Resource(name, 0, 0);
        }

        // Also, add warning if the Flash version of the resource is > than the
        // target runtime we're compiling to.
        if (mEnv.isSWF() && mEnv.getSWFVersionInt() < f.getVersion()) {
            warn(mEnv, "The Flash version of the resource '"+name+"' ["+fileName+"] is " + f.getVersion() + ", which is greater than the application target runtime, Flash version "+mEnv.getSWFVersionInt()+", this will cause unpredictable behavior.");
        }


        try {
            Script s = f.getMainScript();


            collectFonts(s);
            if (addStop) {
                Frame frame = s.getFrameAt(s.getFrameCount() - 1);
                frame.addStopAction();
            }

            Rectangle2D rect = s.getBounds();
            int mw = (int)(rect.getMaxX()/TWIP);
            int mh = (int)(rect.getMaxY()/TWIP);

            Resource res = new Resource(name, s, mw, mh);

            // Add multi-frame SWF resources that have bounds that
            // are different than their first frame to the resource table.
            if (s.getFrameCount() > 1) {

                Rectangle2D f1Rect = new Rectangle2D.Double();
                s.getFrameAt(0).addBounds(f1Rect);
                int f1w = (int)(f1Rect.getMaxX()/TWIP);
                int f1h = (int)(f1Rect.getMaxY()/TWIP);
                if (f1w < mw || f1h < mh) {
                    mMultiFrameResourceSet.add(res);
                }
            }
            return res;
        } catch (java.lang.ArrayIndexOutOfBoundsException e) {
            CompilationError sol = new CompilationError(e);
            sol.setSolution("Error parsing the swf file, maybe it's Flash version > 8?");
            throw sol;
        }
    }



    /**
     * @param fileName
     * @param name
     */
    protected Resource importMP3(String fileName, String name)
        throws IVException, IOException {

        long fileSize =  FileUtils.getSize(new File(fileName));
        FileInputStream stream = new FileInputStream(fileName);
        FlashBuffer buffer = new FlashBuffer(stream);
        Sound sound = MP3Sound.newMP3Sound(buffer);

        Element elt = new Element("resource");
            elt.setAttribute("name", name);
            elt.setAttribute("mime-type", MimeType.MP3);
            elt.setAttribute("source", fileName);
            elt.setAttribute("filesize", "" + fileSize);
        mInfo.addContent(elt);

        stream.close();

        return new Resource(sound);
    }

    /** Writes the object code to the <code>OutputStream</code> that was
     * supplied to the ObjectWriter's constructor.
     * @param isMainApp true if building and linking an app, false if compiling a library
     * @throws IOException if an error occurs
     */
    abstract public void finish(boolean isMainApp) throws IOException;

    public void finish() throws IOException { finish(true); }

    /** Close the  <code>OutputStream</code> that was
     * supplied to the ObjectWriter's constructor.
     * @throws IOException if an error occurs
     */
    abstract public void close() throws IOException;

    abstract public void openSnippet(String url) throws IOException;

    abstract public void closeSnippet() throws IOException;

    /**
     * Generate a warning message
     */
    void warn(CompilationEnvironment env, String msg) {
        env.warn(msg);
    }

    /**
     * A resource we've imported
     */
    class Resource implements Comparable<Resource> {
        /** Name of the resource */
        protected String mName = "";
        /** width of the resource in pixels */
        protected int mWidth = 0;
        /** height of the resource in pixels */
        protected int mHeight = 0;
        /** Flash definition of this resource */
        protected FlashDef mFlashDef = null;

        /** Create a resource that represents this flash def
         * @param def
         */
        public Resource(FlashDef def) {
            mFlashDef = def;
        }

        /** Create a resource
         */
        public Resource(String name, FlashDef def, int width, int height) {
            mName = name;
            mFlashDef = def;
            mWidth = width;
            mHeight = height;
        }

        public Resource(String name, int width, int height) {
            mName = name;
            mWidth = width;
            mHeight = height;
        }

        public String getName()  { return mName; }
        public int getWidth()  { return mWidth; }
        public int getHeight() { return mHeight; }
        public FlashDef getFlashDef() { return mFlashDef; }
        public Rectangle2D getBounds () {
            if (mFlashDef != null) {
                return mFlashDef.getBounds();
            } else {
                return new Rectangle2D.Float(0, 0, mWidth * TWIP, mHeight * TWIP);
            }
        }

        public int compareTo(Resource other) {
          return mName.compareTo(other.mName);
        }
    }

    abstract public void importBaseLibrary(String library, CompilationEnvironment env);

   /** Find a resource for importing into a movie and return a flashdef.
     * Doesn't includes stop action.
     *
     * @param fileName file name of the resource
     * @param name name of the resource
     */
    protected Resource getMultiFrameResource(String fileName, String name, int fNum)
        throws ImportResourceError
    {
        Resource res = mMultiFrameResourceMap.get(fileName);
        if (res != null) {
            return res;
        }

        res = getResource(fileName, "$" + name + "_lzf_" + (fNum+1), false);

        mMultiFrameResourceMap.put(fileName, res);
        return res;
    }


    /* [todo 2006-02-09 hqm] These methods are to be compatible with
       SWF font machinery -- this should get factored away someday so that the FontCompiler
       doesn't try to do anything with <font> tags in DHTML, (except maybe make aliases for them?)
    */
    abstract FontManager getFontManager();

    abstract public boolean isDeviceFont(String face);
    abstract public void setDeviceFont(String face);
    abstract public void setFontManager(FontManager fm);

    abstract void importFontStyle(String fileName, String face, String style, String embedAsCFF,
                                  CompilationEnvironment env)
      throws FileNotFoundException, CompilationError;


    public void setScriptLimits(int recursion, int timeout) {
        this.mRecursionLimit = recursion;
        this.mExecutionTimeout = timeout;
    }


    public CompilationEnvironment getCompilationEnvironment () {
        return mEnv;
    }

    /** Validate a given filename is good for importing
     *
     * @param fileName file name of the resource
     * @return true if it is valid
     */
    public boolean isFileValidForImport(String fileName) {
        // skip auto-generated CSS sprites
        if (fileName.indexOf(CompilationEnvironment.IMAGEMONTAGE_STRING) > -1) {
            //mLogger.trace("skipping css sprite: "+fileName);
            return false;
        }

        File f = new File(fileName);
        //mLogger.trace("file: " + fileName + " is a file? " + f.isFile());
        return f.isFile();
    }



    /** This is how binary library files with runtime-specific code
        are added to the output.  This will be overridden by
        runtime-specific writers */
    public void addLZOFile(File lzo) {
    }

    /** Tell an ObjectWriter to insert the contents of the lzo right now.
        Only used by DHTMLWriter currently.
    */
    public void outputLZO(String pathname) {

    }

        /** Return true if the .lzo archive contains a runtime-specific
     * library object file for the target runtime, and the compiler switches
     * match (debug,backtrace,and profile);
     */
    public static boolean lzoFileContainsMatchingTargetRuntime(File lzo, CompilationEnvironment env)
    throws IOException {

        String runtime = env.getRuntime();
        String entryname;
        if (runtime.equals("swf10") || runtime.equals("swf9")) {
            entryname = "swc";
        } else if (runtime.equals("dhtml")) {
            entryname = "js";
        } else {
            return false;
        }

        String nameWithOptions = addLZOCompilerOptionFlags(entryname, env);

        ZipInputStream zis = new ZipInputStream(new FileInputStream(lzo));
        ZipEntry entry;
        while (true) {
            entry = zis.getNextEntry();
            if (entry == null) {
                return false;
            }
            if (nameWithOptions.equals(entry.getName())) {
                return true;
            }
        }
    }


    public static String addLZOCompilerOptionFlags(String s, CompilationEnvironment env) {
        s += env.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY)  ? "-debug" : "";
        s += env.getBooleanProperty(CompilationEnvironment.BACKTRACE_PROPERTY)  ? "-backtrace" : "";
        s += env.getBooleanProperty(CompilationEnvironment.PROFILE_PROPERTY)  ? "-profile" : "";
        return s;
    }

    void addClassModel(String script, String classname) { }
}

