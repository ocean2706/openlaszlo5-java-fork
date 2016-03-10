/* ****************************************************************************
 * ViewSchema_Test.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.IOException;

import junit.framework.TestCase;

import org.jdom.Element;
import org.jdom.JDOMException;


public class ViewSchema_Test extends TestCase {
    public ViewSchema_Test (String name) {
        super(name);
    }

    public void testParseColors () {
        assertEquals("parseColor #FFFFFF", 0XFFFFFF, ViewSchema.parseColor("#FFFFFF"));
        assertEquals("parseColor #FF0000", 0xFF0000, ViewSchema.parseColor("#FF0000"));
        assertEquals("parseColor #800080", 0x800080, ViewSchema.parseColor("#800080"));
        assertEquals("parseColor #FF00FF", 0xFF00FF, ViewSchema.parseColor("#FF00FF"));
        assertEquals("parseColor #123456", 0x123456, ViewSchema.parseColor("#123456"));
        assertEquals("parseColor #DEADBE", 0xDEADBE, ViewSchema.parseColor("#DEADBE"));
        assertEquals("parseColor #000000", 0x000000, ViewSchema.parseColor("#000000"));
        assertEquals("parseColor black", 0x000000, ViewSchema.parseColor("black"));
        assertEquals("parseColor green", 0x008000, ViewSchema.parseColor("green"));
        assertEquals("parseColor silver", 0xC0C0C0, ViewSchema.parseColor("silver"));
        assertEquals("parseColor lime", 0x00FF00, ViewSchema.parseColor("lime"));
        assertEquals("parseColor gray", 0x808080, ViewSchema.parseColor("gray"));
        assertEquals("parseColor olive", 0x808000, ViewSchema.parseColor("olive"));
        assertEquals("parseColor white", 0xFFFFFF, ViewSchema.parseColor("white"));
        assertEquals("parseColor yellow", 0xFFFF00, ViewSchema.parseColor("yellow"));
        assertEquals("parseColor maroon", 0x800000, ViewSchema.parseColor("maroon"));
        assertEquals("parseColor navy", 0x000080, ViewSchema.parseColor("navy"));
        assertEquals("parseColor red", 0xFF0000, ViewSchema.parseColor("red"));
        assertEquals("parseColor blue", 0x0000FF, ViewSchema.parseColor("blue"));
        assertEquals("parseColor purple", 0x800080, ViewSchema.parseColor("purple"));
        assertEquals("parseColor teal", 0x008080, ViewSchema.parseColor("teal"));
        assertEquals("parseColor fuchsia", 0xFF00FF, ViewSchema.parseColor("fuchsia"));
        assertEquals("parseColor aqua", 0x00FFFF, ViewSchema.parseColor("aqua"));

    }


    public void testHTMLContent () {
        ViewSchema schema = new ViewSchema();
        CompilationEnvironment env = new CompilationEnvironment();
        try {
            schema.loadSchema(env);
        } catch (JDOMException e) {
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }

        assertTrue("hasHTMLContent text",       schema.hasHTMLContent(new Element("text")));
        assertTrue("hasHTMLContent class",    !schema.hasHTMLContent(new Element("class")));
        assertTrue("hasHTMLContent method",   !schema.hasHTMLContent(new Element("method")));
        assertTrue("hasHTMLContent property", !schema.hasHTMLContent(new Element("property")));
        assertTrue("hasHTMLContent script",   !schema.hasHTMLContent(new Element("script")));
    }

    public void testSetAttributes () {

      /* Obsolete */

//         ViewSchema schema = new ViewSchema();
//         CompilationEnvironment env = new CompilationEnvironment();
//         try {
//             schema.loadSchema(env);
//         } catch (JDOMException e) {
//             throw new RuntimeException(e.getMessage());
//         } catch (IOException e) {
//             throw new RuntimeException(e.getMessage());
//         }

//         String class1 = "mynewclass";
//         String subclass = "mynewsubclass";

//         Element elt1 = new Element("classdef1");
//         Element elt2 = new Element("classdef2");

//         schema.addElement(elt1, "mynewclass", "view", new ArrayList(), env);
//         schema.addElement(elt2, "mynewsubclass", "mynewclass", new ArrayList(), env);

//         assertEquals("undefined class superclass",
//                      "Object",
//                      schema.getBaseClassname("view"));

//         assertEquals(" superclass",
//                      "view",
//                      schema.getSuperTagName("mynewclass"));

//         assertEquals(" superclass",
//                      "mynewclass",
//                      schema.getSuperTagName("mynewsubclass"));

//         assertEquals("mynewclass superclass",
//                      "Object",
//                      schema.getBaseClassname("mynewclass"));

//         assertEquals("mynewsubclass superclass",
//                      "Object",
//                      schema.getBaseClassname("mynewsubclass"));


//         schema.setAttributeType(elt1, "mynewclass", "foo-width", new AttributeSpec("foo-width", schema.SIZE_EXPRESSION_TYPE, null, null));
//         schema.setAttributeType(elt1, "mynewclass", "barbaz", new AttributeSpec("barbaz", schema.STRING_TYPE, null, null));

//         schema.setAttributeType(elt1, "mynewsubclass", "baz-width", new AttributeSpec("baz-width", schema.SIZE_EXPRESSION_TYPE, null, null));
//         schema.setAttributeType(elt1, "mynewsubclass", "barbaz", new AttributeSpec("barbaz", schema.EVENT_HANDLER_TYPE, null, null));

//         assertEquals("mynewclass foo-width type",
//                      schema.SIZE_EXPRESSION_TYPE,
//                      schema.getAttributeType(class1, "foo-width", NodeModel.ALLOCATION_INSTANCE));

//         assertEquals("mynewclass barbaz type",
//                      schema.STRING_TYPE,
//                      schema.getAttributeType(class1, "barbaz", NodeModel.ALLOCATION_INSTANCE));

//         // Check subclass attribute types
//         assertEquals("mynewsubclass foo-width type",
//                      schema.SIZE_EXPRESSION_TYPE,
//                      schema.getAttributeType(subclass, "foo-width", NodeModel.ALLOCATION_INSTANCE));

//         assertEquals("mynewsubclass baz-width type",
//                      schema.SIZE_EXPRESSION_TYPE,
//                      schema.getAttributeType(subclass, "baz-width", NodeModel.ALLOCATION_INSTANCE));

//         // Attribute type of subclass should override superclass type
//         assertEquals("mynewsubclass barbaz type",
//                      schema.EVENT_HANDLER_TYPE,
//                      schema.getAttributeType(subclass, "barbaz", NodeModel.ALLOCATION_INSTANCE));

//         // test for duplicate attributes, undefined superclass, redefined class, attr inheritance


    }

}

