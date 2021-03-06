/* ****************************************************************************
 * CompilerUtils.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.  *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import org.jdom.Element;

public class CompilerUtils  {
    /** Return a string that can be included in a script to tell the
     * script compiler that subsequent lines should be numbered
     * relative to the beginning or end position of the source text
     * for this element.
     *
     * @param start true if the location should be relative to the
     * start of the element (otherwise it is relative to the end)
     */
    public static String sourceLocationDirective(Element elt, boolean start) {
        // Parser adds these attributes.
        String pathname = Parser.getSourceMessagePathname(elt);
        Integer lineno = Parser.getSourceLocation(elt, Parser.LINENO, start);
        Integer colno = Parser.getSourceLocation(elt, Parser.COLNO, start);
        return sourceLocationDirective(pathname, lineno, colno);
    }

    /** Return a string that can be used as a unique name for the
     * element for compiler generated function names.
     *
     * @param start true if the location should be relative to the
     * start of the element (otherwise it is relative to the end)
     */
    public static String sourceUniqueName(Element elt, boolean start) {
        // Parser adds these attributes.
        String pathname = Parser.getSourceMessagePathname(elt);
        Integer lineno = Parser.getSourceLocation(elt, Parser.LINENO, start);
        Integer colno = Parser.getSourceLocation(elt, Parser.COLNO, start);
        // When parsing with crimson, the column number isn't defined.
        // TODO: [2004-11-10] This won't generate unique names, so it
        // will fail with krank.  Krank isn't used in this environment
        // so that's okay for now.
        if (colno.intValue() < 0)
            colno = 0;
        
        if (pathname == null) {
            pathname = "unknown_file";
        } else {
            pathname = encodeJavaScriptIdentifier(pathname);
        }
        return "$" + pathname + '_' + lineno + '_' + colno;
    }
    
    /** Returns a string that is a valid JavaScript identifier.
     * Characters in the input string that are not valid in a
     * JavaScript identifier are replaced by "$xx", where xx is the
     * hex code of the character.  '$' is also replaced.  This is
     * similar to URL encoding, except '$' is used as the quote
     * character. */
    public static String encodeJavaScriptIdentifier(String s) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Java and JavaScript identifiers have the same lexical
            // specification, so the Java methods work for this
            if (!(Character.isJavaIdentifierPart(c) ||
                  (i == 0 && Character.isJavaIdentifierStart(c))) ||
                c == '$') {
                String hex = Integer.toHexString((int) c);
                if (hex.length() < 2)
                    hex = "0" + hex;
                buffer.append('$');
                buffer.append(hex.toUpperCase());
            } else {
                buffer.append(c);
            }
        }
        return buffer.toString();
    }
    
    /* TODO: [2002-12-20 hqm] we need a better way to locate where
     * an attribute occurs in a source file.
     */
    public static String attributeLocationDirective(Element elt,
                                                    String attrname)
    {
        return sourceLocationDirective(elt, true);
    }
    
    /** TODO: [2003-03-14 ptw] ditto */
    public static String attributeUniqueName(Element elt, String attrname) {
        return sourceUniqueName(elt, true);
    }
    
    /** Return a string that can be included in a script to tell the
     * script compiler that subsequent lines should be numbered
     * relative to the beginning or end position of the source text
     * for this element.
     *
     */
    public static String sourceLocationDirective(String pathname,
                                                 Integer lineno,
                                                 Integer colno)
    {
        StringBuilder buffer = new StringBuilder();
        if (pathname != null) {
            buffer.append("\n#file ").append(pathname).append('\n');
        }
        if (lineno != null) {
            // Set the line number of the following line.  Prepend a
            // line feed, in case this directive is being added after
            // other text.
            if (buffer.length() == 0) {
                buffer.append('\n');
            }
            buffer.append("#line ").append(lineno).append('\n');
            // Add enough spaces to line the column of the following
            // text up to the same position it had within the source
            // text, so that the column numbers in source reporting
            // are correct.
            if (colno != null) {
                for (int i = colno.intValue(); i > 0; --i) {
                    buffer.append(' ');
                }
            }
        }
        return buffer.toString();
    }
    
    public static String endSourceLocationDirective = sourceLocationDirective("", null, null);

    public static String sourceLocationPrettyString(Element elt) {
        // Parser adds these attributes.
        String pathname = Parser.getSourceMessagePathname(elt);
        Integer lineno = Parser.getSourceLocation(elt, Parser.LINENO, true);
        Integer colno = Parser.getSourceLocation(elt, Parser.COLNO, true);
        return pathname+":"+lineno+":"+colno;
    }
    
    /** Returns true if the argument is at the top level of an lzx
     * program: it is immediately within a canvas element, or within a
     * library element that is itself at the top level. */
    static boolean isAtToplevel(Element element) {
        // To simplify the implementation, root canvas and library
        // elements are also considered to be at the top level, as is
        // a canvas inside a top level element.
        Element elt = element;
        while ((elt = elt.getParentElement()) != null) {
            if (! ToplevelCompiler.isElement(elt)) {
                return false;
            }
        }
        return true;
    }

    /** Is this element a direct child of the canvas?
     *
     * TODO: [2008-06-17 ptw] Reconcile this with the above.  Why do
     * we have two different algorithms?
     */
    static boolean topLevelDeclaration(Element element) {
        Element elt = element;
        while ((elt = elt.getParentElement()) != null) {
            String name = elt.getName();
            if ("canvas".equals(name) || "library".equals(name)) {
                return true;
            } else if ("switch".equals(name) ||
                "when".equals(name) ||
                "otherwise".equals(name)) {
                // Pass up through any <switch> clauses  
            } else {
                return false;
            }
        }
        return false;
    }
}
