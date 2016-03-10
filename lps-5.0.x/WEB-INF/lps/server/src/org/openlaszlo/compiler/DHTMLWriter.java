/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
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
import org.openlaszlo.iv.flash.util.IVException;
import org.openlaszlo.media.ImageHelper;
import org.openlaszlo.media.ImageMontageMaker;
import org.openlaszlo.media.TranscoderException;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.NaturalOrderComparator;

/** Accumulates code, XML, and assets to a DHTML object file.
 *
 * Properties documented in Compiler.getProperties.
 */
class DHTMLWriter extends ObjectWriter {
    static final String localResourceDir = "lps/resources";

    /** A PrintStream built on the raw output stream. */
    protected PrintStream mPrintStream;

    // List of declarations of resources
    protected StringBuffer mResourceDefs = new StringBuffer();

    // List of image montage paths, used to write out master CSS sprite
    private Vector<String> mImageMontages = new Vector<String>();

    // Current offset in master CSS sprite
    private int mImageMontageOffset = 0;

    /** Logger */
    protected static Logger mLogger = org.apache.log4j.Logger.getLogger(DHTMLWriter.class);


    protected org.openlaszlo.sc.DHTMLCompiler mSCompiler = null;

    DHTMLWriter(Properties props, OutputStream stream,
                CompilerMediaCache cache,
                boolean importLibrary,
                CompilationEnvironment env) {

        super(props, stream, cache, importLibrary, env);
        this.mPrintStream = new PrintStream(this.mStream);
    }

    @Override
    public void open(String compileType) {
        Properties props = (Properties)mProperties.clone();
        props.put(org.openlaszlo.sc.Compiler.COMPILE_TYPE, compileType);
        mSCompiler = new org.openlaszlo.sc.DHTMLCompiler(props);
        mSCompiler.startApp();
    }

    @Override
    public void setOutputStream(OutputStream s) {
        this.mStream = s;
        this.mPrintStream = new PrintStream(s);
    }


    /**
     * Sets the canvas for the app
     *
     * @param canvas
     *
     */
    @Override
    void setCanvas(Canvas canvas, String canvasConstructor) {
        addScript(canvasConstructor);
    }

    @Override
    void setCanvasDefaults(Canvas canvas, CompilerMediaCache mc) { };

    @Override
    public int addScript(String script) {
        mSCompiler.compileBlock(script);
        return script.length();
    }

    public int addScript(StringBuffer script) {
        mSCompiler.compileBlock(script.toString());
        return script.length();
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
    public void importResource(String fileName, String name, ResourceCompiler.Offset2D offset)
        throws ImportResourceError
    {
        importResource(new File(fileName), name, offset);
    }

    @Override
    public void importResource(File inputFile, String name)
      throws ImportResourceError
    {
        importResource(inputFile, name, null);
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
            mLogger.trace("DHTMLWriter: Importing resource: " + name);
        }
        if (inputFile.isDirectory()) {
            //mLogger.debug("DHTMLWriter Is directory: " + inputFile.toString());
            sources = inputFile.listFiles();
            Arrays.sort(sources, NaturalOrderComparator.NUMERICAL_ORDER);
            //mLogger.debug("DHTMLWriter: "+inputFile.toString()+" is a directory containing "+ sources.length +" files.");
            for (int i = 0; i < sources.length; i++) {
                // Construct path from directory and file names.
                /* TODO: In theory, file resolution might get files that come from somewhere on the file system that doesn't match where
                        things will be on the server. Part of the root path may differ, and this File doesn't actually have to correspond
                        to a file on the server disk. Thus the path is reconstructed here.
                        That said, the current code isn't actually abstracting the path here.
                        This will actually come into play when the '2 phase' file resolution currently occuring is
                        compacted to a single phase. To do this, more resource and file descriptor information will have
                        to be maintained, either in a global table or using a more abstract path structure, or both.
                        For now I'll leave this as it is. [pga]
                */
                String sFname = inputFile.toString() + File.separator + sources[i].getName();
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
                if (isFileValidForImport(sFname)) {
                    outsources.add(sFname);
                }
            }
            importResource(outsources, name, null);
            return;
        }

        // Conversion to canonical path was here.
        File dirfile = mEnv.getApplicationFile().getParentFile();
        String[] fileInfo = getRelPath(inputFile);
        String pType = fileInfo[0];
        String relPath = fileInfo[1];

        // make it relative and watch out, it comes back canonicalized with forward slashes.
        // Comparing to file.separator is wrong on the pc.
        if (relPath.charAt(0) == '/') {
            relPath = relPath.substring(1);
        }

         // If this is a "external" resource and copy-resources flag
         // is set, make a copy of the resource file to bundle with the app.
         // (Note: the SOLO DHTML deploy script used to be responsible or this).
         if (pType.equals("sr") && mEnv.getBooleanProperty(CompilationEnvironment.COPY_RESOURCES_LOCAL)) {
             // If this is a "sr" (server-root-relative) path, make a local copy in the
             // localResourceDir
             copyResourceFile(inputFile, dirfile, relPath);
         }

        StringBuilder sbuf = new StringBuilder("LzResourceLibrary." + name
            + "={ptype:\"" + pType + "\",frames:[");
        sbuf.append("'" + relPath + "'");

        Resource res = mResourceMap.get(inputFile.toString());
        boolean oldRes = (res != null);
        if (!oldRes) {
            // Get the resource and put in the map
            res = getResource(inputFile.toString(), name);
            if (mLogger.isTraceEnabled()) {
                mLogger.trace("Building resource map; res: "+ res + ", relPath: "+ relPath +", fileName: "+inputFile.toString());
            }
            mResourceMap.put(relPath, res); //[pga] was fileName
        } else {
            // Add an element with 0 size, since it's already there.
            Element elt = new Element("resource");
            elt.setAttribute("name", name);
            // elt.setAttribute("mime-type", MimeType.MP3);
            elt.setAttribute("source", relPath); //[pga] was fileName
            elt.setAttribute("filesize", "0");
            mInfo.addContent(elt);
        }

        Rectangle2D b = res.getBounds();
        if (b != null) {
            sbuf.append("],width:" + (b.getWidth() / TWIP));
            sbuf.append(",height:" + (b.getHeight() / TWIP));

            int spriteoffset = addToMasterSprite(inputFile.toString(), (int) Math.round(b.getHeight() / TWIP));
            sbuf.append(",spriteoffset:" + spriteoffset);
            //mImageMontageOffset += Math.round(b.getHeight() / TWIP);
            //mImageMontages.add(inputFile.toString());
        } else {
            // could be an mp3 resource
            sbuf.append("]");
        }
        sbuf.append("};");
        mResourceDefs.append(sbuf);
    }

    public void addResourceDefs () {
        mPrintStream.print(mResourceDefs);
        mPrintStream.flush();
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

    @Override
    protected Resource importImage(String fileName, String name,
        String mimeType, boolean addStop) throws IOException,
        TranscoderException, IVException {
        // disable media-cache for dhtml to avoid unnessary transcoding to swf
        final boolean USE_MEDIA_CACHE = false;

        if (mCache == null || ! USE_MEDIA_CACHE) {
            int[] size = ImageHelper.getSize(fileName, mimeType);
            if (size != null) {
                long fileSize = FileUtils.getSize(new File(fileName));
                mInfo.addContent(createResourceInfoElement(name, mimeType, fileName,
                    fileSize));

                Resource res = new Resource(name, size[0], size[1]);
                return res;
            }
        }
        // fallback to super class method
        return super.importImage(fileName, name, mimeType, addStop);
    }

    /* Write resource descriptor library */
    public void writeResourceLibraryDescriptor(List<String> sources, String sResourceName, File parent,
                                               ResourceCompiler.Offset2D offset)
    {
        if (sources.isEmpty()) {
            if (mLogger.isTraceEnabled()) {
                mLogger.trace("Skipping empty source list: " + sResourceName);
            }
            return;
        }
        if (mLogger.isTraceEnabled()) {
            mLogger.trace("Constructing resource library: " + sResourceName);
        }
        int width = 0;
        int height = 0;
        int fNum = 0;
        String sep = "";
        boolean first = true;
        // Initialize the temporary buffer.
        StringBuilder sbuf = new StringBuilder();
        String pType;
        String relPath;
        File dirfile = mEnv.getApplicationFile().getParentFile();

        for (String sFile : sources) {
            File fFile = new File(sFile);
            String[] fileInfo = getRelPath(fFile);
            pType = fileInfo[0];
            relPath = fileInfo[1];

            if (first == true) {
                sbuf.append("LzResourceLibrary." + sResourceName + "={ptype:\"" + pType + "\",frames:[");
                first = false;
            }

            if (mLogger.isTraceEnabled()) {
                mLogger.trace("relFile is: "+relPath);
            }

            // If this is a "external" resource and copy-resources flag
            // is set, make a copy of the resource file to bundle with the app.
            // (Note: the SOLO DHTML deploy script used to be responsible or this).
            if (pType.equals("sr") && mEnv.getBooleanProperty(CompilationEnvironment.COPY_RESOURCES_LOCAL)) {
                // If this is a "sr" (server-root-relative) path, make a local copy in the
                // localResourceDir
                copyResourceFile(fFile, dirfile, relPath);
            }

            sbuf.append(sep+"'"+relPath+"'");
            sep = ",";
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
        mMultiFrameResourceSet.add(new Resource(sResourceName, width, height));
        sbuf.append("],width:" + width);
        sbuf.append(",height:" + height);

        // create a montage for multiframe resources
        if (sources.size() > 1) {
            // use first resource to generate filename
            String filename = getSpritePath(sources.get(0));
            try {
                // build across horizontal axis
                int size = ImageMontageMaker.writeStrip(sources, filename, true, false);
                String[] fileInfo = getRelPath(new File(filename));
                relPath = fileInfo[1];
                pType = fileInfo[0];

                if (pType.equals("sr") && mEnv.getBooleanProperty(CompilationEnvironment.COPY_RESOURCES_LOCAL)) {
                    // If this is a "sr" (server-root-relative) path, make a local copy in the
                    // localResourceDir
                    copyResourceFile(new File(filename), dirfile, relPath);
                }
                sbuf.append(",sprite:'" + relPath);

                int spriteoffset = addToMasterSprite(filename, size);
                sbuf.append("',spriteoffset:" + spriteoffset);
                //mImageMontageOffset += size;
                //mImageMontages.add(filename);
            } catch (Exception e) {
                mLogger.error("Assembling CSS sprite: " + sources + ", " + e);
            }
        } else if (sources.size() == 0) {
            int spriteoffset = addToMasterSprite(sources.get(0), height);
            sbuf.append(",spriteoffset:" + spriteoffset);
            //mImageMontageOffset += height;
            //mImageMontages.add(sources.get(0));
        }
        sbuf.append("};");
        mResourceDefs.append(sbuf);
    }

    // compute sprite path, e.g. rightbtn0001.png -> rightbtn0001.sprite.png
    protected String getSpritePath(String path) {
        // offset of one character to the left of the extension, e.g. '.png'
        int extoffset = path.lastIndexOf(FileUtils.getExtension(path)) - 1;
        return path.substring(0, extoffset) + CompilationEnvironment.IMAGEMONTAGE_STRING + "png";
    }

    // Add resource to the master sprite and return its offset
    private int addToMasterSprite(String resourcepath, int size) {
        int offset = mImageMontageOffset;
        mImageMontageOffset += size;
        mImageMontages.add(resourcepath);
        return offset;
    }

    // create master sprite based on all resources collected
    protected void writeMasterSprite(String filename) {
        if (mImageMontages.size() == 0) return;
        try {
            // build horizontally, collapsing height
            ImageMontageMaker.writeStrip(mImageMontages, filename, false, true);
            String[] fileInfo = getRelPath(new File(filename));
            String relPath = fileInfo[1];
            mResourceDefs.append("LzResourceLibrary.__allcss={path:'" + relPath + "'};");
        } catch (Exception e) {
            mLogger.error("Exception in writeMasterSprite() master CSS sprite: " + e + ", " + mImageMontages);
        }
    }

    private String[] getRelPath(File fFile) {
        try {
            File dirfile = mEnv.getApplicationFile().getParentFile();
            String appdir = new File(dirfile != null ? dirfile.getPath() : ".").getCanonicalPath();
            //File appHomeParent = new File(LPS.getHomeParent());
            String sHome = LPS.HOME();
            String pType;
            String relPath;

            String appRelativePrefix = FileUtils.findMaxCommonPrefix(fFile.toString(), appdir);
            String LPSRelativePrefix = FileUtils.findMaxCommonPrefix(fFile.toString(), sHome);

            String prefix;

            //appRelativePrefix.length() > LPSRelativePrefix.length() &&
            if (fFile.toString().startsWith(appdir)) {
                prefix = appRelativePrefix;
                pType = "ar";}
            else {
                prefix = LPSRelativePrefix;
                pType = "sr";
            }

            relPath = FileUtils.relativePath(fFile, prefix);

            // make it relative and watch out, it comes back canonicalized with forward slashes.
            // Comparing to file.separator is wrong on the pc.
            if (relPath.charAt(0) == '/') {
                relPath = relPath.substring(1);
            }
            if (mLogger.isTraceEnabled()) {
                mLogger.trace("relPath is: "+relPath);
            }

            String[] outval = {pType, relPath};
            return outval;
        } catch (java.io.IOException e) {
            throw new ImportResourceError(fFile.toString(), e, mEnv);
        }
    }

    @Override
    public void finish(boolean isMainApp) throws IOException {
        //Should we emit javascript or SWF?
        //boolean emitScript = mEnv.isDHTML();

        if (mCloseCalled) {
            throw new IllegalStateException("DHTMLWriter.close() called twice");
        }

        if (isMainApp) {
            // special case for IE7, need to copy lps/includes/blank.gif to lps/resources/lps/includes/blank.gif
            if (mEnv.getBooleanProperty(CompilationEnvironment.COPY_RESOURCES_LOCAL)) {
                File inputFile = new File(LPS.HOME() + "/lps/includes/blank.gif");
                File dirfile = mEnv.getApplicationFile().getParentFile();
                copyResourceFile(inputFile, dirfile, "lps/includes/blank.gif");
            }

            if (mEnv.linking) {
                String filename = getSpritePath(mEnv.getApplicationFile().toString());
                writeMasterSprite(filename);
            }
        }
        addResourceDefs();

        boolean debug = mProperties.getProperty("debug", "false").equals("true");

        // Bring up a debug window if needed.
        if (isMainApp) {
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

            // Tell the canvas we're done loading.
            addScript("canvas.initDone()");
        }

        try {
            InputStream input = mSCompiler.finishApp();
            FileUtils.send(input, mStream);
        } catch (org.openlaszlo.sc.CompilerException e) {
            throw new CompilationError(e);
        } catch (Exception e) {
            throw new ChainedException(e);
        }

    }

    @Override
    public void close() throws IOException {
        mStream.close();
        mCloseCalled = true;
    }


    /** Set of lzo files which we are linking with. */
    private HashSet<String> mLZOFiles = new HashSet<String>();

    /** Add file to list of loaded lzo's */
    @Override
    public void addLZOFile(File lzo) {
        mLZOFiles.add(lzo.getAbsolutePath());
    }

    @Override
    public void outputLZO(String pathname) {
        if (!mLZOFiles.contains(pathname)) {
            throw new CompilationError("Attempting to output unknown .lzo file "+pathname);
        }
        try {
            copyJSLibToObjectFile(new File(pathname));
        } catch (IOException e) {
            throw new CompilationError(e, "Error outputting .lzo file "+pathname);
        }
    }

    /** Extract the named file from an lzo zip archive, and pipe it to the script compiler's input stream
        @param lzofile location of lzo file in app source dir
    */
    public void copyJSLibToObjectFile(File lzofile)
      throws IOException {
        // Scan for entry named 'js'+compiler options
        InputStream ifs = new BufferedInputStream( new java.io.FileInputStream(lzofile) );
        ZipInputStream zis = new ZipInputStream(ifs);
        ZipEntry entry;
        String nameWithOptions = addLZOCompilerOptionFlags("js", mEnv);
        while (true) {
            entry = zis.getNextEntry();
            if (entry == null) {
                throw new IOException("copyJSLibToOutstream could not find "+nameWithOptions+" js file entry in .lzo zip file "+lzofile);
            }
            if (nameWithOptions.equals(entry.getName())) {
                break;
            }
        }
        mSCompiler.copyRawFromInputStream(zis);
        zis.close();
    }

    @Override
    public void openSnippet(String url) throws IOException {
        this.liburl = url;
    }

    @Override
    public void closeSnippet() throws IOException {
        addResourceDefs();

        // Callback to let library know we're done loading
        addScript("LzLibrary.__LZsnippetLoaded('"+this.liburl+"')");

        if (mCloseCalled) {
            throw new IllegalStateException("DHTMLWriter.close() called twice");
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

    /* [todo 2006-02-09 hqm] These methods are to be compatible with
       SWF font machinery -- this should get factored away someday so that the FontCompiler
       doesn't try to do anything with <font> tags in DHTML, (except maybe make aliases for them?)
    */
    @Override
    FontManager getFontManager() {
        //        mEnv.warn("DHTML runtime doesn't support FontManager API");
        return null;
    }

    @Override
    public boolean isDeviceFont(String face) {
        return true;
    }

    @Override
    public void setDeviceFont(String face) {}
    @Override
    public void setFontManager(FontManager fm) {}

    @Override
    public void importFontStyle(String fileName, String face, String style, String embedAsCFF,
                                CompilationEnvironment env)
        throws FileNotFoundException, CompilationError {
        if (mLogger.isTraceEnabled()) {
            mLogger.trace("DHTMLWriter: Importing font: " + fileName + ", face: " + face + ", style: " + style);
        }

        if (style == null || style.equals("")) {
            style = "plain";
        }

        File inputFile = mEnv.resolve(fileName, null);

        File dirfile = mEnv.getApplicationFile().getParentFile();
        String[] fileInfo = getRelPath(inputFile);
        String pType = fileInfo[0];
        String relPath = fileInfo[1];

        // make it relative and watch out, it comes back canonicalized with forward slashes.
        // Comparing to file.separator is wrong on the pc.
        if (relPath.charAt(0) == '/') {
            relPath = relPath.substring(1);
        }

        // If this is a "external" resource and copy-resources flag
        // is set, make a copy of the resource file to bundle with the app.
        // (Note: the SOLO DHTML deploy script used to be responsible or this).
        if (pType.equals("sr") && mEnv.getBooleanProperty(CompilationEnvironment.COPY_RESOURCES_LOCAL)) {
            // If this is a "sr" (server-root-relative) path, make a local copy in the
            // localResourceDir
            copyResourceFile(inputFile, dirfile, relPath);
        }

        // Convert to the CSS style/weight
        String weight = "normal";
        if (style.equals("plain")) {
            weight = "normal";
            style = "normal";
        } else if (style.equals("bold")) {
            weight = "bold";
            style = "normal";
        } else if (style.equals("italic")) {
            weight = "normal";
            style = "italic";
        } else if (style.equals("bold italic") || style.equals("bolditalic")) {
            weight = "bold";
            style = "italic";
        }

        String fontresource = "LzFontManager.addFont('" + face + "','" + style + "','" + weight + "','" + relPath + "','" + pType + "');";
        mResourceDefs.append(fontresource);
    }

    @Override
    void addPreloaderScript(String script) { } ;
    @Override
    void addPreloader(CompilationEnvironment env) { } ;

    @Override
    public void importBaseLibrary(String library, CompilationEnvironment env) {
        env.warn("DHTMLWriter does not implement importBaseLibrary");
    }

    /**
       Copies a file, INPUTFILE, to the file RELPATH relative to the
       system-generated application local resources directory in
       DESTDIR/lps/resources.
     */
    void copyResourceFile(File inputFile, File destdir, String relPath) {
        try {
            File resourceDirRoot = new File(destdir, localResourceDir);
            File resCopy = new File(resourceDirRoot, relPath);
            // Ensure that parent directories exist down to the file
            File dir = resCopy.getParentFile();
            if (dir != null)
                dir.mkdirs();
            // Make copy of file
            FileUtils.send(new FileInputStream(inputFile), new FileOutputStream(resCopy));

        }
        catch (IOException e) {
            mLogger.error("Error copying resource file in DHTMLWriter "+e.getMessage());
            throw new ImportResourceError(inputFile.toString(), e, mEnv);
        }
    }

}

