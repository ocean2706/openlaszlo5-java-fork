/* J_LZ_COPYRIGHT_BEGIN *******************************************************
 * Copyright 2009, 2012 Laszlo Systems, Inc.  All Rights Reserved.             *
 * Use is subject to license terms.                                            *
 * J_LZ_COPYRIGHT_END *********************************************************/
package org.openlaszlo.media;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.log4j.Logger;
import org.openlaszlo.utils.FileUtils;

public class ImageMontageMaker {
    protected static Logger mLogger = org.apache.log4j.Logger
        .getLogger(ImageMontageMaker.class);

    public static int writeStrip(List<String> files, String outfile)
        throws FileNotFoundException, IOException {
        return ImageMontageMaker.writeStrip(files, outfile, true);
    }

    public static int writeStrip(List<String> files, String outfile,
        boolean isHorizontal) throws FileNotFoundException, IOException {
        return ImageMontageMaker
            .writeStrip(files, outfile, isHorizontal, false);
    }

    public static int writeStrip(List<String> files, String outfile,
        boolean isHorizontal, boolean collapse) throws FileNotFoundException,
        IOException {
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        int numfiles = files.size();

        // read all files into images array
        Image[] images = new Image[numfiles];
        // maximum size for all images
        int maxwidth = 0, maxheight = 0;
        // cumulative total size for all images
        int totalwidth = 0, totalheight = 0;

        MediaTracker tracker = null;
        for (int i = 0; i < numfiles; ++i) {
            String infile = files.get(i);
            mLogger.debug("Adding to sprite: " + infile);
            String extension = FileUtils.getExtension(infile);
            Image img;
            if (extension.toLowerCase().equals("png")) {
                // Have to use this instead of JAI because of bugs in PNG
                // decoder :(
                if (tracker == null) {
                    tracker = createTracker();
                }
                img = toolkit.createImage(infile);
                tracker.addImage(img, 0);
            } else {
                // use JAI
                img = ImageIO.read(new File(infile));
            }
            images[i] = img;
        }

        if (tracker != null) {
            try {
                tracker.waitForAll();
            } catch (InterruptedException e) {
                throw new InterruptedIOException("interrupted while loading images");
            }

            if (tracker.isErrorAny()) {
                throw new IOException("errors while loading images");
            }
        }

        for (Image img : images) {
            int width = img.getWidth(null);
            int height = img.getHeight(null);
            assert (width >= 0 && height >= 0);

            totalwidth += width;
            totalheight += height;
            if (width > maxwidth)
                maxwidth = width;
            if (height > maxheight)
                maxheight = height;

            mLogger.debug("size: " + width + "," + height + "," + maxwidth
                + "," + maxheight + "," + totalwidth + "," + totalheight);
        }

        // use width/height to write images
        int outputwidth = maxwidth;
        int outputheight = maxheight;
        if (isHorizontal) {
            outputwidth = collapse ? totalwidth : maxwidth * numfiles;
        } else {
            outputheight = collapse ? totalheight : maxheight * numfiles;
        }

        mLogger.debug("Output size " + outputwidth + "," + outputheight);

        // combine into one large image
        BufferedImage finalImage = new BufferedImage(outputwidth, outputheight,
            BufferedImage.TYPE_INT_ARGB);
        Graphics g = finalImage.createGraphics();

        int x = 0, y = 0;
        for (Image img : images) {
            // g.drawImage(img, maxwidth * col, maxheight * row,
            // maxwidth, maxheight, null);
            mLogger.debug("Drawing image" + img + " at " + x + ", " + y);
            g.drawImage(img, x, y, null);
            if (isHorizontal) {
                if (collapse) {
                    x += img.getWidth(null);
                } else {
                    x += maxwidth;
                }
            } else {
                if (collapse) {
                    y += img.getHeight(null);
                } else {
                    y += maxheight;
                }
            }
        }

        mLogger.debug("Writing css sprite to: " + outfile);
        ImageIO.write(finalImage, "png", new File(outfile));

        // return the size used to compute the offset of the image in master
        // sprites
        return isHorizontal ? outputheight : outputwidth;
    }

    private static MediaTracker createTracker() {
        @SuppressWarnings("serial")
        MediaTracker tracker = new MediaTracker(new Component() {
        });
        return tracker;
    }

}
