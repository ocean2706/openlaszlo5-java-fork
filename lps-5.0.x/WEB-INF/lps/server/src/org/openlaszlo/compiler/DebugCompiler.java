/******************************************************************************
 * DebugCompiler.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.util.Map;

import org.jdom.Element;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.utils.ChainedException;

/** 
 * Compiler for <code>debug</code> element.
 *
 * @author  Henry Minsky
 */
class DebugCompiler extends ViewCompiler {
    DebugCompiler(CompilationEnvironment env) {
        super(env);
    }

    static final String DEBUG_WINDOW_CLASSNAME = "LzDebugWindow";

    /** Returns true iff this class applies to this element.
     * @param element an element
     * @return see doc
     */
    public static boolean isElement(Element element) {
        return element.getName().equals("debug");
    }

    @Override
    public void compile(Element element) throws CompilationError
    {
        element.setName(DEBUG_WINDOW_CLASSNAME);
        // If the canvas does not have the debug flag, or if we have already instantiated a debugger,
        // return now.
        // Or, if lzconsoledebug=true, don't compile the debugger window view, we will be using
        // a remote connection instead. 
        if (!mEnv.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY)
            || mEnv.getBooleanProperty(CompilationEnvironment.CONSOLEDEBUG_PROPERTY)) {
            return;
        } else if (mEnv.getBooleanProperty(CompilationEnvironment.USER_DEBUG_WINDOW)) {
            mEnv.warn("Duplicate <debug> tag.  There can be only one <debug> tag in an application.", element);
            return;
        } else {
            mEnv.setProperty(CompilationEnvironment.USER_DEBUG_WINDOW, true);
            // inlined from ViewCompiler.compile()
            NodeModel model = NodeModel.elementAsModel(element, mEnv.getSchema(), mEnv);
            Map<String, Object> map = model.asMap(mEnv);
            String classname = (String) map.get("class");
            try {
                StringBuilder writer = new StringBuilder();
                ScriptCompiler.writeObject(map, writer);
                String nodejs = writer.toString();
                String script = "new "+classname + "(canvas, " + nodejs +".attrs" + ");\n";
                // Store the script to construct the debugger window
                mEnv.setProperty(CompilationEnvironment.DEBUGGER_WINDOW_SCRIPT, script);
            } catch (java.io.IOException e) {
                throw new ChainedException(e);
            }

        }
    }
}
