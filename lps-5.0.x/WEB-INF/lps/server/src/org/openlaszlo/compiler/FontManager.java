/*****************************************************************************
 * FontManager.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.util.Enumeration;
import java.util.Hashtable;

import org.openlaszlo.iv.flash.api.text.Font;


public class FontManager {
    /** Font name map */
    private final Hashtable<String, FontFamily> mFontTable = new Hashtable<String, FontFamily>();
        
    public FontFamily getFontFamily(String fontName) {
        if (fontName == null) return null;
        return mFontTable.get(fontName);
    }
        
    public FontFamily getFontFamily(String fontName, boolean create) {
        FontFamily family = mFontTable.get(fontName);
        if (family == null && create) {
            family = new FontFamily();
            mFontTable.put(fontName, family);
        }
        return family;
    }
        
    public Enumeration<String> getNames() {
        return mFontTable.keys();
    }

    public boolean checkFontExists (FontInfo fontInfo) {
        String fontName = fontInfo.getName();
        int    style    = fontInfo.styleBits;
        FontFamily family = getFontFamily(fontName);
        if (family == null) {
            return false;
        }
        Font font = family.getStyle(style);
        if (font == null) {
            return false;
        }
        return true;
    }

}
