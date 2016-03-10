/* *****************************************************************************
 * CompilerMediaCache.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.openlaszlo.cache.Cache;
import org.openlaszlo.cache.CachedInfo;
import org.openlaszlo.media.Transcoder;
import org.openlaszlo.media.TranscoderException;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZUtils;

/**
 * A class for maintaining a disk-backed cache of transcoded media
 * files for the compiler.
 *
 * @author <a href="mailto:bloch@laszlosystems.com">Eric Bloch</a>
 */
public class CompilerMediaCache extends Cache {

    /** Logger. */
    private static Logger mLogger = Logger.getLogger(CompilerMediaCache.class);

    /** Properties */
    private static Properties mProperties = null;

    /** See the constructor. */
    protected File mCacheDirectory;

    /**
     * Creates a new <code>CompilerMediaCache</code> instance.
     */
    public CompilerMediaCache(File cacheDirectory, Properties props)
        throws IOException {
        super("cmcache", cacheDirectory, props);
        this.mCacheDirectory = cacheDirectory;
        if (props == null) {
            CompilerMediaCache.mProperties = new Properties();
        } else {
            CompilerMediaCache.mProperties = props;
        }

    }

    /**
     * Return properties object
     * There is one property <code>forcetranscode</code>
     * which when set to <code>true</code> will force the
     * cache to always transcode requests.
     */
    public Properties getProperties() {
        return mProperties;
    }

    /**
     * Transcode the given input file from the fromType to toType
     * @return the transcoded file
     * @param inputFile file to be transcoded
     * @param fromType type of file to be transcoded
     * @param toType type of file to transcode into
     */
    public synchronized File transcode(
            File inputFile,
            String fromType,
            String toType)
        throws TranscoderException,
               FileNotFoundException,
               IOException {

        if (mLogger.isTraceEnabled()) {
        mLogger.trace(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="transcoding from " + p[0] + " to " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                CompilerMediaCache.class.getName(),"051018-90", new Object[] {fromType, toType})
);
        }
        if (LZUtils.equalsIgnoreCase(fromType,toType)) {
            return inputFile;
        }

        if (!inputFile.exists()) {
            throw new FileNotFoundException(inputFile.getPath());
        }

        // Key should be relative to webapp path and have
        // consistent path separator
        String key = FileUtils.relativePath(inputFile, LPS.HOME()) + ":" + toType;

        boolean lockit = false;
        Item item = findItem(key, null, lockit);

        String inputFilePath = inputFile.getAbsolutePath();
        File cacheFile = item.getFile();
        String cacheFilePath = cacheFile.getAbsolutePath();
        if (mLogger.isTraceEnabled()) {
        mLogger.trace(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="transcoding input: " + p[0] + " output: " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                CompilerMediaCache.class.getName(),"051018-118", new Object[] {inputFilePath, cacheFilePath})
                                );
        }

        InputStream input = null;

        if (!cacheFile.exists() || !inputFile.canRead() ||
            inputFile.lastModified() > cacheFile.lastModified() ||
            mProperties.getProperty("forcetranscode", "false") == "true") {

            item.markDirty();

            if (mLogger.isTraceEnabled()) {
            mLogger.trace(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="transcoding..."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                CompilerMediaCache.class.getName(),"051018-135")
);
            }

            CachedInfo info = item.getInfo();
            try {
                input = Transcoder.transcode(inputFile, fromType, toType);
                if (mLogger.isTraceEnabled()) {
                mLogger.trace(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="done transcoding"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                CompilerMediaCache.class.getName(),"051018-147")
);
                }
                item.update(input, null);
                info.setLastModified(cacheFile.lastModified());
                item.updateInfo();
                item.markClean();
            } finally {
                FileUtils.close(input);
            }
        } else {
            if (mLogger.isTraceEnabled()) {
            mLogger.trace(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="using cached transcode"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                CompilerMediaCache.class.getName(),"051018-163")
);
            }
        }

        updateCache(item);

        return cacheFile;
    }
}
