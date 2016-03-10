/* ****************************************************************************
 * SecurityCompiler.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import org.jdom.Element;

/** Represents a compiler for a toplevel <code>security</code> element. */
class SecurityCompiler extends ElementCompiler {
    SecurityCompiler(CompilationEnvironment env) {
        super(env);
    }

    static boolean isElement(Element element) {
        return element.getName().equals("security");
    }

    @Override
    public void compile(Element element) {
        mEnv.getCanvas().setSecurityOptions(element);
    }
}
