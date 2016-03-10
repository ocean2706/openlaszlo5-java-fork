/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * LZX Fonts
 */

package org.openlaszlo.compiler;

import java.io.FileNotFoundException;
import java.util.Iterator;

import org.jdom.Element;
import org.openlaszlo.xml.internal.XMLUtils;

/**
 * Compiler for <code>font</code> elements.
 *
 * @author  Eric Bloch
 */
class FontCompiler extends ElementCompiler {
    FontCompiler(CompilationEnvironment env) {
        super(env);
    }

    /** Returns true iff this class applies to this element.
     * @param element an element
     * @return see doc
     */
    public static boolean isElement(Element element) {
        return element.getName().equals("font");
    }

    @Override
    public void compile(Element element)
        throws CompilationError
    {
        // Can't use <font> in a loadable library
        String name = XMLUtils.requireAttributeValue(element, "name");
        String src = element.getAttributeValue("src");

        if (!mEnv.getEmbedFonts() ||
            "true".equals(element.getAttributeValue("device"))) {
            mEnv.getGenerator().setDeviceFont(name);
        } else {

            if (src != null) {
                compileFont(name, element);
            }
            // Check if children are valid tags to be contained
            mEnv.checkValidChildContainment(element);

            for (Iterator<?> iter = element.getChildren("face", element.getNamespace()).iterator();
                 iter.hasNext(); ) {
                Element faceElement = (Element) iter.next();
                if (element.getAttributeValue("embedascff") != null) {
                    faceElement.setAttribute("embedascff", element.getAttributeValue("embedascff"));
                }
                compileFont(name, faceElement);
            }
        }
    }

    private void compileFont(String name, Element element) {
        String style = element.getAttributeValue("style");
        try {
            String path = mEnv.resolveReference(element).getAbsolutePath();
            String embedAsCFF = null;
            if (mEnv.isCanvas()) {
              Element info = new Element("resolve");
              info.setAttribute("src", element.getAttributeValue("src"));
              info.setAttribute("pathname", mEnv.resolveReference(element).toString());
              embedAsCFF = element.getAttributeValue("embedascff");
              if (embedAsCFF == null) {
                  // Default value should be false to support embedded fonts in Flash TextField components.
                  // See http://jira.openlaszlo.org/jira/browse/LPP-9140
                  embedAsCFF = "false";
              }
              info.setAttribute("embedascff", embedAsCFF);
              mEnv.getCanvas().addInfo(info);
            }
            mEnv.getGenerator().importFontStyle(path, name, style, embedAsCFF, mEnv);
        } catch (FileNotFoundException e) {
            throw new CompilationError(element, e);
        }
    }
}

/**
 * @copyright Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
