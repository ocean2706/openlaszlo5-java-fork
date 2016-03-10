/******************************************************************************
 * SWFUtils.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.openlaszlo.iv.flash.api.AlphaColor;
import org.openlaszlo.iv.flash.api.FlashFile;
import org.openlaszlo.iv.flash.api.Frame;
import org.openlaszlo.iv.flash.api.Script;
import org.openlaszlo.iv.flash.api.text.Font;
import org.openlaszlo.iv.flash.api.text.FontDef;
import org.openlaszlo.iv.flash.api.text.Text;
import org.openlaszlo.iv.flash.api.text.TextItem;
import org.openlaszlo.iv.flash.util.GeomHelper;
import org.openlaszlo.server.LPS;

/**
 * Utility class for http servlets
 */
public class SWFUtils {

    /**
     * @return stream 
     *
     * FIXME: [2003-05-04 bloch] turn color into a property.
     */
    public static InputStream getErrorMessageSWF(String message) {
        String w = LPS.getProperty("swf.error.msg.width", "400");
        String h = LPS.getProperty("swf.error.msg.height", "200");
        String p = LPS.getProperty("swf.error.msg.pixels", "14");

        int TWIP = 20;

        int wd = Integer.parseInt(w);
        int ht = Integer.parseInt(h);
        int pi = Integer.parseInt(p);

        try {
            FlashFile file = FlashFile.newFlashFile();
            Script script = new Script(1);
            script.setMain();
            file.setMainScript(script);
            Frame frame = script.newFrame();
            String fontFileName = LPS.getMiscDirectory() + 
                File.separator + "vera.fft";
                         
            Font font = FontDef.load(fontFileName, file);
            Text text = Text.newText();
            TextItem item = new TextItem(message, font, pi*TWIP, AlphaColor.black);
            text.addTextItem(item);
            text.setBounds(GeomHelper.newRectangle(0, 0, wd*TWIP, ht*TWIP));
            frame.addInstance(text, 1, new AffineTransform(), null);
            return file.generate().getInputStream();
        } catch (Exception e) {
            // If this fails, we're screwed
            return null;
        }
    }

    /** 
     * @return true if the input stream has a valid SWF header
     */
    public static boolean hasSWFHeader(InputStream is) throws IOException {
        // Code snarfed from jgenerator parser.Parser
        //
        byte[] fileHdr = new byte[8];

        if( is.read(fileHdr, 0, 8) != 8 ) {
            return false;
        }

        if( fileHdr[1] != 'W' || fileHdr[2] != 'S' ) {
            return false;
        }

        // FIXME: [2003-09-20 bloch]
        // Support compressed even though we don't fullly allow it everywhere
        if( fileHdr[0] != 'C' && fileHdr[0] != 'F' ) {
            return false;
        }

        return true;
    }
}
