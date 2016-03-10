/* *****************************************************************************
 * ToplevelCompiler.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.apache.log4j.Logger;
import org.jdom.Element;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;

/** Compiler for <code>canvas</code> and <code>library</code> elements.
 */
abstract class ToplevelCompiler extends ElementCompiler {
    /** Logger */
    private static Logger mLogger = Logger.getLogger(ToplevelCompiler.class);

    ToplevelCompiler(CompilationEnvironment env) {
        super(env);
    }
    
    /** Returns true if the element is capable of acting as a toplevel
     * element.  This is independent of whether it's positioned as a
     * toplevel element; CompilerUtils.isTopLevel() tests for position
     * as well. */
    static boolean isElement(Element element) {
        return CanvasCompiler.isElement(element)
            || LibraryCompiler.isElement(element);
    }

    @Override
    public void compile(Element element) {
        // Check if children are valid tags to be contained 
        mEnv.checkValidChildContainment(element);

        for (Iterator<?> iter = element.getChildren().iterator();
             iter.hasNext(); ) {
            Element child = (Element) iter.next();
            if (!NodeModel.isPropertyElement(child)) {
                Compiler.compileElement(child, mEnv);
            }
        }
    }

  /**
   * Computes the global declarations defined by the tags in this
   * top-level form
   */
  void computePropertiesAndGlobals (Element element, NodeModel model, ViewSchema schema) {
        Set<File> visited = new HashSet<File>();
        for (File file : getLibraries(element)) {
            Element library = LibraryCompiler.resolveLibraryElement(file, mEnv, visited);
            if (library != null) {
              collectObjectProperties(library, model, schema, visited);
            }
        }
        collectObjectProperties(element, model, schema, visited);
        // Output declarations for all globals so they can be
        // resolved at compile time.
        String globals = "";
        String globalPrefix = mEnv.getGlobalPrefix();
        // TODO: [2008-04-16 ptw] The '= null' is to silence the
        // swf7/swf8 debugger, it should be conditional
        for (String id : mEnv.getIds().keySet()) {
          if (!("".equals(globalPrefix))) {
            // For SWF7,SWF8, we need to set a binding for the instance's ID in the main app's namespace
            globals += (globalPrefix+id + " = null;\n");
          } else {
            globals += ("var " +id + " = null;\n");
          }
        }
        mEnv.compileScript(globals);
  }

  void collectObjectProperties(Element element, NodeModel model, ViewSchema schema, Set<File> visited) {
    computeDeclarations(element, schema);
    for (Iterator<?> iter = element.getChildren().iterator();
         iter.hasNext(); ) {
      Element child = (Element) iter.next();
      if (NodeModel.isPropertyElement(child)) {
        model.addPropertyElement(child);
      } else if ( (LibraryCompiler.isElement(child)) ||
                  (ImportCompiler.isElement(child))){
        Element libraryElement = LibraryCompiler.resolveLibraryElement(
          child, mEnv, visited);
        if (libraryElement != null) {
          collectObjectProperties(libraryElement, model, schema, visited);
        }
      }
    }
  }

  void computeDeclarations(Element element, ViewSchema schema) {
    // Gather and check id's and global names now, so declarations
    // for them can be emitted.
    ClassModel classModel = schema.getInstanceClassModel(element, false);
    // Only process nodes
    if (classModel != null && classModel.isnode) {
        String id = element.getAttributeValue("id");
        String globalName = null;
        if (! schema.isClassDefinition(element)) {
          if (CompilerUtils.topLevelDeclaration(element)) {
            globalName = element.getAttributeValue("name");
          }
        }
        if (id != null) {
          mEnv.addId(id, element);
        }
        if (globalName != null) {
          mEnv.addId(globalName, element);
        }
        // Don't descend into datasets
        if (! classModel.isdataset) {
          Iterator<?> iterator = element.getChildren().iterator();
          while (iterator.hasNext()) {
            Element child = (Element) iterator.next();
            computeDeclarations(child, schema);
          }
        }
    }
  }


  /**
   * Outputs the tag map entries for the tags defined in this
   * top-level form
   *
   * NOTE: [2009-02-18 ptw] Called once from CanvasCompiler.compile
   * for whole program compile, or from LibraryCompiler.compile when
   * not linking (creating a binary library).
   */
  public void outputTagMap(CompilationEnvironment env) {
        // Output the tag->class map.
      StringBuilder tagmap = new StringBuilder();
      for (Map.Entry<String, String> entry : env.getTags().entrySet()) {
          String tagName = entry.getKey();
          String className = entry.getValue();
          // Install in constructor map
          tagmap.append(("lz[" + ScriptCompiler.quote(tagName) + "] = " + className + ";\n"));
        }
      if (tagmap.length() > 0) {
          env.compileScript(tagmap.toString());
      }
  }

    /** Parses out user class definitions.
     *
     * <p>
     * Iterates the direct children of the top level of the DOM tree and
     * look for non-property elements which might extend the schema
     * (e.g., classes and types) and calls their updateSchema methods
     * to give them a chance to do so
     *
     * @param visited {canonical filenames} for libraries whose
     * schemas have been visited; used to prevent recursive
     * processing.
     * 
     */
    @Override
    void updateSchema(Element element, ViewSchema schema, Set<File> visited) {
        Iterator<?> iterator = element.getChildren().iterator();
        while (iterator.hasNext()) {
            Element child = (Element) iterator.next();
            if (!NodeModel.isPropertyElement(child)) {
                Compiler.updateSchema(child, mEnv, schema, visited);
            }
        }
    }

    /** This also collects "attribute", "method", and HTML element
     * names, but that's okay since none of them has an autoinclude
     * entry.
     */
    static void collectReferences(CompilationEnvironment env,
                                  Element element, Set<String> defined,
                                  Set<String> referenced, Map<File, Set<File>> libsVisited) {
        ElementCompiler compiler = Compiler.getElementCompiler(element, env);
        ViewCompiler.collectLayoutElement(element, referenced);
        if (compiler instanceof ToplevelCompiler) {
            Set<File> libStart = null;
            Set<File> libFound = null;
            Element library = null;
            File libFile = null;
            if (compiler instanceof LibraryCompiler || compiler instanceof ImportCompiler) {
                libStart = new LinkedHashSet<File>(libsVisited.keySet());
                libFound = new LinkedHashSet<File>(libStart);
                library = LibraryCompiler.resolveLibraryElement(element, env, libFound);
                if (library == element) {
                    // Not an external library
                    library = null;
                }
                if (library != null) {
                    element = library;
                    try {
                        libFile = new File(Parser.getSourcePathname(library)).getCanonicalFile();
                        libsVisited.put(libFile, null);
                    } catch (IOException f) {
                        assert false : "Can't happen";
                    }
                }
            }
            for (Iterator<?> iter = element.getChildren().iterator(); iter.hasNext(); ) {
                collectReferences(env, (Element) iter.next(), defined, referenced,
                                  libsVisited);
            }
            if (library != null) {
                Set<File> includes = new LinkedHashSet<File>(libsVisited.keySet());
                includes.removeAll(libStart);
                libsVisited.put(libFile, includes);
            }
        } else if (compiler instanceof ClassCompiler) {
            String name = element.getAttributeValue("name");
            if (name != null) {
                defined.add(name);
            }
            String superclass = element.getAttributeValue("extends");
            if (superclass != null) {
                referenced.add(superclass);
            }
            ViewCompiler.collectElementNames(element, referenced);
        } else if (compiler instanceof ViewCompiler) {
            ViewCompiler.collectElementNames(element, referenced);
        }
    }

    static List<File> getLibraries(CompilationEnvironment env, Element element,
            Map<String, String> explanations, Map<File, Set<File>> autoIncluded, Map<File, Set<File>> visited) {
        String librariesAttr = element.getAttributeValue("libraries");
        assert librariesAttr == null : "unsupported attribute `libraries`";
        List<File> libraryFiles = new ArrayList<File>();
        File library = new File(Parser.getSourcePathname(element));
        String base = library.getParent();

        // figure out which tags are referenced but not defined, and
        // look up their libraries in the autoincludes file
        {
            Set<String> defined = new HashSet<String>();
            Set<String> referenced = new HashSet<String>();
            // keep the keys sorted so the order is deterministic for qa
            Set<File> additionalLibraries = new TreeSet<File>();
            Map<Object, Object> autoincludes = ViewSchema.sAutoincludes;
            Map<String, File> canonicalAuto = new HashMap<String, File>();
            try {
              for (Iterator<Object> iter = autoincludes.keySet().iterator(); iter.hasNext(); ) {
                String key = (String) iter.next();
                canonicalAuto.put(key, env.resolveLibrary((String)autoincludes.get(key), base).getCanonicalFile());
              }
            } catch (IOException e) {
              throw new CompilationError(element, e);
            }
            collectReferences(env, element, defined, referenced, visited);
            // iterate undefined references
            for (String key : referenced) {
                if (autoincludes.containsKey(key)) {
                  File canonical = canonicalAuto.get(key);
                  // Ensure that a library that was explicitly
                  // included that would have been auto-included is
                  // emitted where the auto-include would have been.
                  // (unless you are library-compiling _that_
                  // auto-include!)
                  String value = (String)autoincludes.get(key);
                  if (defined.contains(key)) {
                    if (visited.containsKey(canonical)) {
                      // Annotate as explicit
                      if (explanations != null) {
                        explanations.put(value, "explicit include");
                      }
                      // but include as auto
                      additionalLibraries.add(canonical);
                    }
                  } else {
                    if (explanations != null) {
                      explanations.put(value, "reference to <" + key + "> tag");
                    }
                    additionalLibraries.add(canonical);
                  }
                }
            }
            // If not linking, consider all external libraries as
            // 'auto'
            if (autoIncluded != null) {
              try {
                for (File file : visited.keySet()) {
                  if (env.isExternal(file)) {
                    autoIncluded.put(file, visited.get(file));
                    additionalLibraries.add(file.getCanonicalFile());
                  }
                }
              } catch (IOException e) {
                throw new CompilationError(element, e);
              }
            }
            libraryFiles.addAll(additionalLibraries);
        }
        // Build result file lists
        List<File> libraries = new ArrayList<File>();
        for (File file : libraryFiles) {
            libraries.add(file);
            if (autoIncluded != null) {
              autoIncluded.put(file, visited.get(file));
            }
        }
        // If linking and canvas debug=true, and we're not running a
        // remote debugger, add the debugger-window component library
        if (env.linking &&
             (includesDebuggerWindow(env))) {
            if (explanations != null) {
                explanations.put("debugger", "the canvas debug attribute is true");
            }
            String pathname = LPS.getComponentsDirectory() +
                File.separator + "debugger" +
                File.separator + "library.lzx";
            libraries.add(new File(pathname));
        }
        return libraries;
    }
    
    /** Decide whether to include the application GUI debugger component.
     *
     * Include the debugger window if debug=true no remote-debugging
     * modes are enabled
     */
    static boolean includesDebuggerWindow(CompilationEnvironment env) {
        return (env.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY) &&
                !env.getBooleanProperty(CompilationEnvironment.CONSOLEDEBUG_PROPERTY) &&
                !env.getBooleanProperty(CompilationEnvironment.REMOTEDEBUG_PROPERTY) &&
                !env.getBooleanProperty(CompilationEnvironment.INTERMEDIATE_PROPERTY));
    }

    static List<File> getLibraries(CompilationEnvironment env, Element element,
            Map<String, String> explanations, Set<File> autoIncluded, Set<File> visited) {
        Map<File, Set<File>> externalMap = null;
        Map<File, Set<File>> visitedMap = null;
        if (autoIncluded != null) {
            externalMap = new LinkedHashMap<File, Set<File>>();
        }
        if (visited != null) {
            visitedMap = new LinkedHashMap<File, Set<File>>();
        }
        List<File> libs = getLibraries(env, element, explanations, externalMap, visitedMap);
        if (autoIncluded != null) {
            autoIncluded.addAll(externalMap.keySet());
        }
        if (visited != null) {
            visited.addAll(visitedMap.keySet());
        }
        return libs;
    }

    List<File> getLibraries(Element element) {
        return getLibraries(mEnv, element, null, null, new HashSet<File>());
    }


    static String getBaseLibraryName (CompilationEnvironment env) {
      return LPS.getLFCname(env.getRuntime(),
                            env.getBooleanProperty(CompilationEnvironment.DEBUG_PROPERTY),
                            env.getBooleanProperty(CompilationEnvironment.PROFILE_PROPERTY),
                            env.getBooleanProperty(CompilationEnvironment.BACKTRACE_PROPERTY),
                            env.getBooleanProperty(CompilationEnvironment.SOURCE_ANNOTATIONS_PROPERTY));
    }

    static void handleAutoincludes(CompilationEnvironment env, Element element) {
        // import required libraries, and collect explanations as to
        // why they were required
        Canvas canvas = env.getCanvas();

        List<File> libraries = env.getLibraries();
        for (File file : libraries) {
            Compiler.importLibrary(file, env);
        }

        Element info;
        // canvas info += <include name= explanation= [size=]/> for LFC
        if (env.isSWF() || env.isAS3()) {
          String baseLibraryName = getBaseLibraryName(env);
          String baseLibraryBecause = "Required for all applications";
          info = new Element("include");
          info.setAttribute("name", baseLibraryName);
          info.setAttribute("explanation", baseLibraryBecause);
          try {
              info.setAttribute("size", "" + 
                                FileUtils.getSize(env.resolveLibrary(baseLibraryName, "")));
          } catch (Exception e) {
              mLogger.error(
  /* (non-Javadoc)
   * @i18n.test
   * @org-mes="exception getting library size"
   */
                          org.openlaszlo.i18n.LaszloMessages.getMessage(
                                  ToplevelCompiler.class.getName(),"051018-228")
                                  , e);
          }
          canvas.addInfo(info);
        }

        // canvas info += <include name= explanation=/> for each library
        Map<String, String> explanations = env.getExplanations();
        for (Map.Entry<String, String> entry : explanations.entrySet()) {
            info = new Element("include");
            info.setAttribute("name", entry.getKey().toString());
            info.setAttribute("explanation", entry.getValue().toString());
            canvas.addInfo(info);
        }
    }

}
