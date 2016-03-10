/*****************************************************************************
 * NULLWriter.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
 * Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
 * Use is subject to license terms.                                            *
 * J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.*;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * This ObjectWriter is just for gathering compilation warnings from
 * the tag compiler.  It has stubbed out all calls to the script
 * compiler or media compilers.
 *
 * Properties documented in Compiler.getProperties.
 */
class NullWriter extends DHTMLWriter {

    // Accumulate script here, to pass to script compiler
    protected PrintWriter scriptWriter = null;
    protected StringWriter scriptBuffer = null;

    /** Logger */
    protected static Logger mLogger = org.apache.log4j.Logger.getLogger(NullWriter.class);

    NullWriter(Properties props, OutputStream stream,
                CompilerMediaCache cache,
                boolean importLibrary,
                CompilationEnvironment env) {

        super(props, stream, cache, importLibrary, env);

        scriptBuffer = new StringWriter();
        scriptWriter= new PrintWriter(scriptBuffer);

    }


    /**
     * Sets the canvas for the app
     *
     * @param canvas
     *
     */
    @Override
    void setCanvas(Canvas canvas, String canvasConstructor) {
        scriptWriter.println(canvasConstructor);
    }

    @Override
    void setCanvasDefaults(Canvas canvas, CompilerMediaCache mc) { };


    @Override
    public int addScript(String script) {
        scriptWriter.println(script);
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
        importResource(new File(fileName), name);
    }

    @Override
    public void importResource(File inputFile, String name)
        throws ImportResourceError
    {
    }

    @Override
    public void importResource(List<String> sources, String sResourceName, File parent)
    {
        writeResourceLibraryDescriptor(sources, sResourceName, parent);
    }

    /* Write resource descriptor library */
    public void writeResourceLibraryDescriptor(List<String> sources, String sResourceName, File parent) { }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void openSnippet(String url) throws IOException {
        this.liburl = url;
    }

    @Override
    public void closeSnippet() throws IOException {
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
        env.warn("DHTMLWriter does not support importing fonts");
    }

    @Override
    void addPreloaderScript(String script) { } ;
    @Override
    void addPreloader(CompilationEnvironment env) { } ;

    @Override
    public void importBaseLibrary(String library, CompilationEnvironment env) {
        env.warn("DHTMLWriter does not implement importBaseLibrary");
    }

    @Override
    protected Resource getResource(String fileName, String name, boolean stop)
        throws ImportResourceError
    {
        return null;
    }

}

