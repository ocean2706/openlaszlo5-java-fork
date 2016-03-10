/*****************************************************************************
 * TextCompiler.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.util.Iterator;

import org.jdom.Attribute;
import org.jdom.CDATA;
import org.jdom.Element;
import org.jdom.EntityRef;
import org.jdom.Text;

/** Utility functions for measuring HTML content, and translating it into Flash strings.
 *
 * @author <a href="mailto:hminsky@laszlosystems.com">Henry Minsky</a>
 */
abstract class TextCompiler {

    /** Check if a specified font is known by the Font Manager
     *
     * @param generator
     * @param fontInfo the font spec you want to check
     *
     * This will throw an informative CompilationError if the font does not exist.
     */
     public static  void checkFontExists (SWFWriter generator, FontInfo fontInfo) {
        String fontName = fontInfo.getName();

        if (!generator.checkFontExists(fontInfo)) {
            throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Can't find font " + p[0] + " of style " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                TextCompiler.class.getName(),"051018-60", new Object[] {fontName, fontInfo.getStyle()})
                                                );
        }
    }

    /** Collect the "HTML" text from the Content of an Element
     *
     * For back compatibility, this normalizes whitespace, using rules similar to browser HTML text.
     *
     *
     * <ul>
     * <li> All text is whitespace normalized, except that which occurs between &lt;pre&gt; tags
     * <li> Linebreaks occur only when &lt;br/&gt; or &lt;p/&gt; elements occur, or when a newline
     * is present inside of a &lt;pre&gt; region.
     * </ul>
     */

    static String getHTMLContent(Element e, CompilationEnvironment env) {
        // check if the normalized text is cached
        if ((e instanceof ElementWithLocationInfo) &&
            ((ElementWithLocationInfo) e).getHTMLContent() != null) {
            return ((ElementWithLocationInfo) e).getHTMLContent();
        }

        LineMetrics lm = new LineMetrics();
        getHTMLContent(e, env, lm);
        lm.endOfLine();
        return lm.getText();
    }

    /** Return text suitable for passing to Laszlo inputtext component.
     * This means currently no HTML tags are supported except PRE
     */
    static String getInputText (Element e) {
        String text = "";
        for (Iterator<?> iter = e.getContent().iterator();
             iter.hasNext();) {
            Object node = iter.next();
            if (node instanceof Element) {
                Element child = (Element) node;
                String tagName = child.getName();
                if (tagName.equals("p") || tagName.equals("br")) {
                    text += "\n";
                } else if (tagName.equals("pre")) {
                    text +=  child.getText();
                } else {
                    // ignore everything else
                }
            } else if ((node instanceof Text) || (node instanceof CDATA)) {
                if (node instanceof Text) {
                    text += ((Text) node).getTextNormalize();
                } else {
                    text += ((CDATA) node).getTextNormalize();
                }
            }
        }
        return text;
    }


    /**
        Processes the text content of the element.  The element
        content may contain XHTML markup elements, which we will
        interpret as we map over the content. Normally, whitespace
        will be normalized away. However, preformat &lt;pre&gt; tags
        will cause the enclosed text to be treated as verbatim,
        meaning means that whitespace and linebreaks will be
        preserved.

        Supported XHTML markup is currently:
        <ul>
        <li> P, BR cause linebreaks
        <li> PRE sets verbatim (literal whitespace) mode
        <li> font face control: B, I, FONT tags modify the font
        <li> A [href] indicates a hyperlink
        </ul>

    */
    static void getHTMLContent(Element e, CompilationEnvironment env, LineMetrics lm) {
        for (Iterator<?> iter = e.getContent().iterator();
             iter.hasNext();) {
            Object node = iter.next();
            if (node instanceof Element) {
                Element child = (Element) node;
                String tagName = child.getName();

                if (tagName.equals("br")) {
                    lm.newline(); // explicit linebreak
                    getHTMLContent(child, env, lm);
                    if (!child.getText().equals("")) {
                        lm.newline();
                    }
                } else if (tagName.equals("p")) {
                    lm.paragraphBreak();
                    getHTMLContent(child, env);
                    lm.paragraphBreak();
                } else if (tagName.equals("pre")) {
                    boolean prev = lm.verbatim;
                    lm.setVerbatim(true);
                    getHTMLContent(child, env, lm);
                    lm.setVerbatim(prev);
                } else if (ViewSchema.isHTMLElement(child)) {
                    lm.addStartTag(tagName);
                    // print font-related attributes:
                    // face, size, color
                    // supported Flash HTML tags: http://www.macromedia.com/support/flash/ts/documents/htmltext.htm
                    for (Iterator<?> attrs = child.getAttributes().iterator(); attrs.hasNext(); ) {
                        Attribute attr = (Attribute) attrs.next();
                        String name = attr.getName();
                        String value = child.getAttributeValue(name);
                        // TBD: [hqm nov-15-2002] The value ought to be quoted in case it contains double quotes
                        // (but no values of currently supported HTML tags will contain double quotes)
                        lm.addFormat(" "+name+"=\""+value+"\"");
                    }
                    lm.endStartTag();
                    getHTMLContent(child, env, lm);
                    lm.addEndTag(tagName);
                } else {
                    // Ignore any tag which is not a 'known' HTML tag
                }
            } else if ((node instanceof Text) || (node instanceof CDATA)) {
                String rawtext;
                if (node instanceof Text) {
                    rawtext = ((Text) node).getText();
                } else {
                    rawtext = ((CDATA) node).getText();
                }
                if (lm.verbatim) {
                    lm.addSpan(rawtext);
                } else {
                    // Apply HTML normalization rules to the text content.
                    if (rawtext.length() > 0) {
                        // getTextNormalize turns an all-whitespace string into an empty string
                        String normalized_text;
                        if (node instanceof Text) {
                            normalized_text = ((Text) node).getTextNormalize();
                        } else {
                            normalized_text = ((CDATA) node).getTextNormalize();
                        }
                        lm.addHTML (rawtext, normalized_text);
                    }
                }
            } else if (node instanceof EntityRef) {
                // EntityRefs don't seem to occur in our JDOM, they were all resolved
                // to strings by the parser already
                throw new RuntimeException(
                    /* (non-Javadoc)
                     * @i18n.test
                     * @org-mes="encountered unexpected EntityRef node in getHTMLContent()"
                     */
                    org.openlaszlo.i18n.LaszloMessages.getMessage(
                        TextCompiler.class.getName(),"051018-418")
                                           );
            }
        }
    }


}
