/* *****************************************************************************
 * DataCompiler.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;
import java.util.regex.Pattern;

import org.jdom.Element;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.xml.internal.XMLUtils;

/** Compiler for local data elements.
 *
 * @author  Henry Minsky
 * @author  Oliver Steele
 */
class DataCompiler extends ElementCompiler {

    /* TODO [hqm 2007-07] This is for top level datasets only. The function in the LFC,
     * lzAddLocalData, creates the dataset
     * *immediately*, it is not queued for instantiation. This happens to
     * allow forward references to datasets in LZX code. It also happens to slow
     * down initialization of an app if it has large static datasets. This could be
     * made better by queuing the data for quantized lzIdle processing, although it
     * would mean delaying the "ondata" of the datasets until they were processed. 
     */

    static final String LOCAL_DATA_FNAME = "canvas.lzAddLocalData";

    // Pattern matcher for '$once{...}' style constraints
    private static final Pattern constraintPat = Pattern.compile("^\\s*\\$(\\w*)\\s*\\{(.*)\\}\\s*");

    DataCompiler(CompilationEnvironment env) {
        super(env);
    }

    static boolean isElement(Element element) {
        if (element.getName().equals("dataset")) {
            // return type in ('soap', 'http') or src is url
            String src = element.getAttributeValue("src");
            String type = element.getAttributeValue("type");
            if (type != null && (type.equals("soap") || type.equals("http"))) {
                return false;
            }
            if (src != null && src.indexOf("http:") == 0) {
                return false;
            }
            // not an element for this compiler when the is an url or a constraint
            return ! (src != null && (XMLUtils.isURL(src) || constraintPat.matcher(src).matches()));
        }
        return false;
    }

  @Override
  public void compile(Element element) {
    try {
      String dsetname = XMLUtils.requireAttributeValue(element, "name");
      boolean trimwhitespace = "true".equals(element.getAttributeValue("trimwhitespace"));
      String content = NodeModel.getDatasetContent(element, mEnv, trimwhitespace);
      boolean nsprefix = "true".equals(element.getAttributeValue("nsprefix"));
      // Initialize the global declaration
      mEnv.compileScript(dsetname+" = "+
                         LOCAL_DATA_FNAME+"("+ScriptCompiler.quote(dsetname) +
                         ", " +content+
                         "," + trimwhitespace+
                         "," + nsprefix + ");\n");
      // For swf9, make sure the global variable is referenced or the
      // Flash class loader won't load it.
      // 
      // TODO [hqm 2008-11] Note, we could also accomplish this by
      // adding an explicit "-include dsetname" in the command line
      // options to the flex compiler when we compile the library, to
      // force it to link in this global var class, but it is simpler
      // to just make a reference to it in the code here, and doesn't
      // cost anything. I suppose some day the flex compiler may
      // optimize out this useless statement, and then this hack would
      // stop working.
      mEnv.compileScript(dsetname+" == true;");

    } catch (org.openlaszlo.xml.internal.MissingAttributeException err) {
      throw new CompilationError(element, err);
    }
  }

}
