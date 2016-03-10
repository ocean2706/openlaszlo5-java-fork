/* J_LZ_COPYRIGHT_BEGIN ********************************************************
 * Copyright 2011 Laszlo Systems, Inc.  All Rights Reserved.                   *
 * Use is subject to license terms.                                            *
 * J_LZ_COPYRIGHT_END *********************************************************/
package org.openlaszlo.media;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;

/**
 * Helper class to return the size of an image through the ImageIO API
 * 
 * @author André Bargull
 * 
 */
public final class ImageHelper {

    /**
     * Reads the image denoted by the <code>path</code> argument and returns its
     * size
     * 
     * @param path
     *            Path to image file
     * @param mimeType
     *            MIME-type of the image
     * @return image's size in form [width, height] or <code>null</code> if an
     *         error occured
     * @throws IOException
     */
    public static final int[] getSize(String path, String mimeType)
        throws IOException {
        int[] size = null;
        Iterator<ImageReader> iter = ImageIO
            .getImageReadersByMIMEType(mimeType);
        if (iter.hasNext()) {
            ImageReader reader = iter.next();
            try {
                ImageInputStream stream = new FileImageInputStream(new File(
                    path));
                reader.setInput(stream);
                int width = reader.getWidth(reader.getMinIndex());
                int height = reader.getHeight(reader.getMinIndex());
                size = new int[] { width, height };
            } finally {
                reader.dispose();
            }
        }
        return size;
    }
}
