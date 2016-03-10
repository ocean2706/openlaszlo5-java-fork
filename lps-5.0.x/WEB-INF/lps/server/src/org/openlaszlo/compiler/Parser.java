/* *****************************************************************************
 * Parser.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.JDOMFactory;
import org.jdom.Namespace;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.jdom.input.SAXHandler;
import org.openlaszlo.css.parser.CSSParser;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.ChainedException;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.StringUtils;
import org.openlaszlo.xml.internal.XMLUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Parses and validates an XML file.  XML elements are annotated with
 * their source locations.
 *
 * A new parser should be used for each compilation, but shared across
 * all XML file reads within a compilation.  This assures that the
 * parser caches are active during compilation, but are not reused
 * for subsequent compilations when the file may have been modified.
 */
public class Parser {
    private static Logger mLogger = Logger.getLogger(Parser.class);

    static public Namespace sNamespace =
        Namespace.getNamespace("http://www.laszlosystems.com/2003/05/lzx");
    
    protected FileResolver resolver = FileResolver.DEFAULT_FILE_RESOLVER;

    protected final Map<File, Document> fileCache = new HashMap<File, Document>();
    /** Pathnames in messages are reported relative to
     * one of these. */
    List<String> basePathnames = new Vector<String>();

    // Stylesheet templates and generators for updating old
    // namespaces, and adding namespace declarations.
    private static javax.xml.transform.Templates sPreprocessorTemplates;
    private static SAXTransformerFactory sSaxFactory;
    private static long sPreprocessorLastModified;
    
    protected static synchronized SAXTransformerFactory getSaxFactory() {
        String stylePath = LPS.HOME().replace('\\', '/') + "/" +
            "WEB-INF" + "/" + 
            "lps" + "/" + 
            "schema"  + "/" + "preprocess.xsl";
        File styleFile = new File(stylePath);
        long lastModified = styleFile.lastModified();
        
        if (sSaxFactory != null && sPreprocessorLastModified == lastModified)
            return sSaxFactory;
        
        // name the class instead of using
        // TransformerFactory.newInstance(), to insure that we get
        // saxon and thereby work around a failure on Tomcat 5 w/ jdk
        // 1.4.2 Linux, and w/ Sun 1.4.1_05
        javax.xml.transform.TransformerFactory factory = 
            new com.icl.saxon.TransformerFactoryImpl();
        
        javax.xml.transform.Templates templates = null;
        try {
            templates = factory.newTemplates(
                new StreamSource("file:///" + stylePath));
        } catch (TransformerConfigurationException e) {
            throw new ChainedException(e);
        }
        
        if (!factory.getFeature(SAXTransformerFactory.FEATURE))
            throw new RuntimeException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="TransformerFactory doesn't implement SAXTransformerFactory"
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Parser.class.getName(),"051018-107")
);
        
        SAXTransformerFactory saxFactory = (SAXTransformerFactory) factory;
        
        sSaxFactory = saxFactory;
        sPreprocessorTemplates = templates;
        sPreprocessorLastModified = lastModified;
        return saxFactory;
    }
    
    public Parser() {
    }
    
    public void setResolver(FileResolver resolver) {
        this.resolver = resolver;
    }
    
    /** Returns the pathname to use in user error messages.  This is
     * the shortest pathname relative to a directory on the search
     * path for this application.
     */
    public String getUserPathname(String pathname) {
        String sourceDir = new File(pathname).getParent();
        if (sourceDir == null)
            sourceDir = "";
        sourceDir = sourceDir.replace(File.separatorChar, '/');
        String best = pathname.replace(File.separatorChar, '/');
        int bestLength = StringUtils.split(best, "/").length;
        for (String item : basePathnames) {
            String base = item.replace(File.separatorChar, '/');
            try {
                String candidate = FileUtils.adjustRelativePath(
                    new File(pathname).getName(),
                    base,
                    sourceDir);
                int candidateLength =
                    StringUtils.split(candidate, "/").length;
                if (candidateLength < bestLength) {
                    best = candidate;
                    bestLength = candidateLength;
                }
            } catch (FileUtils.RelativizationError e) {
                // If it can't be relativized, it simply doesn't produce
                // a candidate, and we do nothing.
            }
        }
        return best;
    }
    
    /** Reads an XML document and adds source location information to
     * the elements. */
    public Document read(File file)
        throws JDOMException, IOException
    {
        // See if we've already read the file.  This is an
        // optimization, and also assures that the same content is
        // used across passes.  We don't need to (and shouldn't) check
        // the date, since the cache is an instance variable, and each
        // compiler invocation uses a fresh parser.
        File key = file.getCanonicalFile();
        if (fileCache.containsKey(key)) {
            return fileCache.get(key);
        }
        
        // Use a custom subclass of SAXBuilder to build a JDOM tree
        // containing custom Elements which contain source file
        // location info (ElementWithLocationInfo).
        
        // The following variables are used to add source location
        // that reflects the input name, while the system identifier
        // has been made absolute.
        final String pathname = file.getPath();
        final String messagePathname = getUserPathname(pathname);

        // This is a ContentHandler which adds source location info to
        // our own custom class of jdom.Element
        class SourceLocatorHandler extends org.jdom.input.SAXHandler {
            org.xml.sax.Locator locator;
            
            SourceLocatorHandler(JDOMFactory factory)
                throws IOException
            {
                super(factory);
            }

            @Override
            public void characters(char[] ch, int start, int length)
                throws SAXException
            {
                super.characters(ch, start, length);
            }

            @Override
            public void endElement(String namespaceURI, String localName,
                                   String qName)
                throws SAXException
            {
                // You can only call this.getCurrentElement() before
                // super.endElement

                // Save source location info for reporting compilation errors
                saveEndLocation(this.getCurrentElement(),
                                pathname,
                                messagePathname,
                                locator.getLineNumber(),
                                locator.getColumnNumber());

                super.endElement(namespaceURI, localName, qName);
            }

            @Override
            public void setDocumentLocator(org.xml.sax.Locator locator) {
                this.locator = locator;
            }

            @Override
            public void startElement(String namespaceURI, String localName,
                                     String qName,
                                     org.xml.sax.Attributes atts)
                throws SAXException
            {
                super.startElement(namespaceURI, localName, qName, atts);
                    
                // You can only call this.getCurrentElement() after
                // super.startElement

                // Save source location info for reporting compilation errors
                saveStartLocation(this.getCurrentElement(),
                                  pathname,
                                  messagePathname,
                                  locator.getLineNumber(),
                                  locator.getColumnNumber());
            }
        }

        /* We need a SAXBuilder that uses our custom Factory and
           ContentHandler classes, but the stock
           org.jdom.input.SAXBuilder has no API for setting which
           ContentHandler is used.

           To get what we need, we create a subclass of SAXBuilder
           and override the createContentHandler method to
           instantiate our custom handler with our custom factory . 
        */
        class SourceLocatorSAXBuilder extends SAXBuilder {
            SourceLocatorSAXBuilder () {
                super();
            }

            // We need to create our own special contentHandler,
            // and you *must* pass the factory to the SaxHandler
            // constructor, or else you get a default JDOM
            // factory, which is not what you want!
            @Override
            protected org.jdom.input.SAXHandler createContentHandler() {
                try {
                    return new SourceLocatorHandler(getFactory());
                } catch (IOException e) {
                    throw new ChainedException(e);
                }
            }
        }

        //SAXBuilder builder = new SourceLocatorSAXBuilder("org.apache.crimson.parser.XMLReaderImpl");
        SAXBuilder builder = new SourceLocatorSAXBuilder();
        builder.setFactory(new SourceLocatorJDOMFactory());

        // ignore DOCTYPE declarations TODO [2004-25-05 ows]: parse
        // entity references from internal declarations, and either
        // warn about external declarations or add them to the
        // dependency information.  If the latter, use a library to
        // cache and resolve non-file sources against a catalog file.
        builder.setEntityResolver(
            new org.xml.sax.helpers.DefaultHandler() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) {
                    return new InputSource(new StringReader(""));
                }
            });
        
        // Parse the document
        java.io.Reader reader = FileUtils.makeXMLReaderForFile(
            key.getPath(), "UTF-8");
        InputSource source = new InputSource(reader);
        source.setPublicId(messagePathname);
        source.setSystemId(key.getPath());
        Document doc = builder.build(source);
        reader.close();
        fileCache.put(key, doc);
        return doc;
    }
    
    protected Document preprocess(final Document sourceDoc)
        throws java.io.IOException, org.jdom.JDOMException
    {
        // Fills location information from the metasource attribute.
        class SourceLocatorHandler extends org.jdom.input.SAXHandler {
            SourceLocatorHandler(JDOMFactory factory)
                throws IOException
            {
                super(factory);
            }

            @Override
            public void endElement(String namespaceURI, String localName,
                                   String qName)
                throws SAXException
            {
                ElementWithLocationInfo element =
                    (ElementWithLocationInfo) this.getCurrentElement();
                Attribute attr = element.getAttribute(
                    SourceLocatorSAXOutputter.SOURCEINFO_ATTRIBUTE_NAME);
                if (attr != null) {
                    SourceLocator locator = SourceLocator.fromString(attr.getValue());
                    element.initSourceLocator(locator);
                    element.removeAttribute(attr);
                }
                super.endElement(namespaceURI, localName, qName);
            }
        }

        // Create a transformer that implements the 'preprocess'
        // transformation.
        TransformerHandler handler;
        try {
            handler = getSaxFactory().
                newTransformerHandler(sPreprocessorTemplates);
        } catch (TransformerConfigurationException e) {
            throw new ChainedException(e);
        }
        
        SAXHandler resultHandler =
            new SourceLocatorHandler(new SourceLocatorJDOMFactory());
        SAXResult result =
            new javax.xml.transform.sax.SAXResult(resultHandler);
        handler.setResult(result);
        
        SourceLocatorSAXOutputter outputter = new SourceLocatorSAXOutputter();
        outputter.setWriteMetaData(true);
        outputter.setContentHandler(handler);
        outputter.output(sourceDoc);
        
        Document resultDoc = resultHandler.getDocument();
        
        /*
          if (mPostTransformationLogger.isDebugEnabled()) {
            org.jdom.output.XMLOutputter xmloutputter =
                new org.jdom.output.XMLOutputter();
            mPostTransformationLogger.debug(
                xmloutputter.outputString(resultDoc));
        }
        */
        return resultDoc;
    }

    /** Reads the XML document, and modifies it by replacing each
     * include element by the root of the expanded document named by
     * the include's href attribute. Pathames are resolved relative to
     * the element's source location.  If a pathname resolves to the
     * current file or a file in the set that's passed as the second
     * argument, a compilation error is thrown.  A copy of the set of
     * pathnames that additionally includes the current file is passed
     * to each recursive call.  (Since the set has stacklike behavior,
     * a cons list would be appropriate, but I don't think Java has
     * one.)
     *
     * This is a helper function for parse(), which is factored out
     * so that expandIncludes can be apply it recursively to included
     * files. */
    private Document readExpanded(File file, Set<File> currentFiles, CompilationEnvironment env)
        throws IOException, JDOMException
    {
        File key = file.getCanonicalFile();
        if (currentFiles.contains(key)) {
            throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes=p[0] + " includes itself."
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Parser.class.getName(),"051018-394", new Object[] {file})
);
        }

        Document doc = read(file);
        Element root = doc.getRootElement();

        // Override passed in runtime target properties 'debug' and 'profile' with 
        // canvas values, if any.
        if (root.getName().equals("canvas")) {
            Map<Object, Object> cprops = env.getProperties();
            
            String copts = root.getAttributeValue("compileroptions");
            if (copts != null) {
                parseCompilerOptions(root, env);
            }

            // "debug" and "profile" are here for back
            // compatibility. But the preferred way to set compiler
            // options is compileroptions="debug: true;backtrace: true"
            String dbg = root.getAttributeValue("debug");
            if (dbg != null) {
                cprops.put("$debug", Boolean.valueOf(dbg));
                env.setProperty(CompilationEnvironment.DEBUG_PROPERTY,  dbg.equals("true"));
            }

            String prof = root.getAttributeValue("profile");
            if (prof != null) {
                cprops.put("$profile", Boolean.valueOf(prof));
                env.setProperty(CompilationEnvironment.PROFILE_PROPERTY,  prof.equals("true"));
            }
        }

        currentFiles.add(key);
        expandIncludes(root, currentFiles, env);
        currentFiles.remove(key);

        return doc;
    }

    // Set compiler options
    void parseCompilerOptions(Element element, CompilationEnvironment env) {
        Map<Object, Object> cprops = env.getProperties();

        try {
            Map<String, Object> properties = new CSSParser
                (new AttributeStream(element, "compileroptions")).Parse();

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                mLogger.info("parseCompilerOptions key="+key+" value="+value + " typeof(value)="+value.getClass().getName());

                if (key.equals("debug")) {
                    if (! (value instanceof Boolean)) {
                        throw new CompilationError("value of compileroptions.debug must be a boolean", element);
                    }
                    cprops.put("$debug", value);
                    env.setProperty(CompilationEnvironment.DEBUG_PROPERTY, ((Boolean) value).booleanValue());
                } else if (key.equals("profile")) {
                    if (! (value instanceof Boolean)) {
                        throw new CompilationError("value of compileroptions.profile must be a boolean", element);
                    }
                    cprops.put("$profile", value);
                    env.setProperty(CompilationEnvironment.PROFILE_PROPERTY, ((Boolean) value).booleanValue());
                } else if (key.equals("backtrace")) {
                    if (! (value instanceof Boolean)) {
                        throw new CompilationError("value of compileroptions.backtrace must be a boolean", element);
                    }
                    cprops.put("$backtrace", value);
                    env.setProperty(CompilationEnvironment.BACKTRACE_PROPERTY, ((Boolean) value).booleanValue());
                } else if (key.equals("runtime")) {
                    env.setProperty(CompilationEnvironment.RUNTIME_PROPERTY, (String)value);
                    CompilationEnvironment.setRuntimeConstants((String)value, env.getProperties(), env);
                }
            }
        } catch (org.openlaszlo.css.parser.ParseException e) {
            throw new CompilationError(e);
        } catch (org.openlaszlo.css.parser.TokenMgrError e) {
            throw new CompilationError(e);
        }
    }


    /**
       usage: 

       <when property="$as3">  // checks for Boolean true 

       <when property="$runtime" value="swf9"> // checks for string equality

       for back compatibility:

       <when runtime="swf9"> 

    */
    protected boolean evaluateConditions(Element element, CompilationEnvironment env) {
        String propname = element.getAttributeValue("property");
        if ( propname != null) {
            Map<Object, Object> cprops = env.getProperties();
            Object prop = cprops.get(propname);
            if (prop == null) {
                return false;
            } else if (prop instanceof Boolean) {
                // Attempt to get the boolean value
                return ((Boolean)prop).booleanValue();
            } else if (prop instanceof String) {
                String value = element.getAttributeValue("value");
                if (value != null) {
                    return prop.equals(value);
                } else {
                    return false;
                }
            }
        } else if (element.getAttribute("runtime") != null) {
            String val = element.getAttributeValue("runtime");
            return env.getRuntime().equals(val);
        } else {
            throw new CompilationError("The 'when' tag requires a 'property' or 'runtime' attribute, e.g., <when property=\"$dhtml\">", element);
        }
        return false;
    }

  @SuppressWarnings("serial")
  private class NotAllowedInBinarySwitch extends ElementFilter {
    ViewSchema schema;
    NotAllowedInBinarySwitch (ViewSchema schema) {
      this.schema = schema;
    }

    @Override
    public boolean matches (Object o) {
      if (! (o instanceof Element)) return false;
      return schema.isClassDefinition((Element) o);
    }
  }

  private static final String WHEN = "when";
  private static final String UNLESS = "unless";
  private static final String OTHERWISE = "otherwise";

    /*
      <switch>  
         <when runtime="swf8">
          <view .../>
        </when>
        <otherwise >
          <view .../>
        </otherwise>
      </switch>

      returns the child list of the active arm of the <switch>

    */
    protected List<Content> evaluateSwitchStatement(Element elt, CompilationEnvironment env) {
        Element selected = null;
        for (Iterator<?> iter = elt.getChildren().iterator();
             iter.hasNext(); ) {
            Element child = (Element) iter.next();
            String name = child.getName();
            if (! (WHEN.equals(name) || UNLESS.equals(name) || OTHERWISE.equals(name))) {
                throw new CompilationError("unknown clause for a switch statement: " + child.getName(), child);
            }
        }

        if (env.linking) {
          for (Iterator<?> iter = elt.getChildren(WHEN, elt.getNamespace()).iterator();
               iter.hasNext(); ) {
            Element when = (Element) iter.next();
            if (evaluateConditions(when, env)) {
              selected = when;
              break;
            }
          }
          for (Iterator<?> iter = elt.getChildren(UNLESS, elt.getNamespace()).iterator();
               iter.hasNext(); ) {
            Element when = (Element) iter.next();
            if (!evaluateConditions(when, env)) {
              selected = when;
              break;
            }
          }
        } else {
            if (! ("true".equals(env.getProperty(CompilationEnvironment.ALLOW_LZO_SWITCH_BLOCK)))) {
                if (env.isExternal(new File(getSourcePathname(elt)))) {
                    // Classes in switch blocks will not be parsed when not
                    // linking (compiling a binary library).
                    if (elt.getDescendants(new NotAllowedInBinarySwitch(env.getSchema())).hasNext()) {
                        env.warn("Compile may fail due to conditional class definitions in <switch>", elt);
                    }
                    return Collections.emptyList();
                }
                // You can't library-compile a <switch> block, since it will
                // not necessarily be platform-neutral
                env.warn("<switch> not allowed in binary libraries", elt);
                return Collections.emptyList();
            }
        }

        if (selected == null) {
            selected = elt.getChild(OTHERWISE, elt.getNamespace());
        }

        if (selected == null) {
            return Collections.emptyList();
        } else {
            @SuppressWarnings("unchecked")
            List<Content> clone = (List<Content>) selected.cloneContent();
            return clone;
        }
    }

    /** Replaces include statements by the content of the included
     * file.
     *
     * This is a helper function for readExpanded, which is factored
     * out so that readExpanded can apply it recursively to included
     * files. */
    private void expandIncludes(Element element, Set<File> currentFiles, CompilationEnvironment env)
        throws IOException, JDOMException
    {
        {
            @SuppressWarnings("unchecked")
            List<Content> content = element.getContent();
            for (int i = 0, size = content.size(); i < size; ++i) {
                Content c = content.get(i);
                if (! (c instanceof Element)) { continue; }
                Element child = (Element) c;
                String name = child.getName();
                if ("dataset".equals(name)) {
                    // don't descend into datasets
                } else if ("switch".equals(name)) {
                    // expand (replace) any <switch> elements with only the selected arm of the
                    // <switch>
                    List<Content> goodies = evaluateSwitchStatement(child, env);
                    // splice these in place of the <switch>
                    content.remove(i);
                    content.addAll(i, goodies);
                    // adjust size
                    size += goodies.size() - 1;
                    // revisit same index once again
                    i -= 1;
                } else if ("include".equals(name)) {
                    // Ensure there are no child elements
                    if (! child.getChildren().isEmpty()) {
                        throw new CompilationError("'include' tag must not contain child elements", child);
                    }
                    // Ensure there are no illegal attributes
                    env.getSchema().checkValidAttributeNames(child);
                    String base = new File(getSourcePathname(element)).getParent();
                    String type = XMLUtils.getAttributeValue(child, "type", "xml");
                    String href = child.getAttributeValue("href");
                    if (href == null) {
                        throw new CompilationError(
    /* (non-Javadoc)
     * @i18n.test
     * @org-mes="The <include> element requires an \"href\" attribute."
     */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                            Parser.class.getName(), "051018-438"), child);
                    }
                    File target = resolver.resolve(env, href, base, true);
                    // If this file is already implicitly included, just
                    // leave it, don't try to expand it.  It will be
                    // skipped at the compilation stage.
                    if (resolver.getBinaryIncludes().contains(target)) {
                      continue;
                    }
                    if ("text".equals(type)) {
                        String text = FileUtils.readFileString(target, "UTF-8");
                        content.set(i, new org.jdom.Text(text));
                    } else if ("xml".equals(type)) {
                        // Pass the target, not the key, so that source
                        // location information is correct.
                        //System.err.println("including xml "+target);
                        Document doc = read(target);
                        // If it's a top-level library, the compiler will
                        // process it during the compilation phase.  In
                        // that case change it to a <library href=""/>
                        // element, where the href is the <include>'s
                        // href, so that LibraryCompiler can resolve it.
                        //
                        // Otherwise replace the <include> element with
                        // the included file.
                        if (CompilerUtils.isAtToplevel(child) &&
                            LibraryCompiler.isElement(doc.getRootElement())) {
                            // Modify the existing element instead of
                            // creating a new one, so that source location
                            // information is preserved.
                            child.setName(doc.getRootElement().getName());
                        } else {
                            doc = readExpanded(target, currentFiles, env);

                            // replace the <include> child element with the expanded file contents
                            content.set(i, doc.getRootElement().detach());

                            File key = target.getCanonicalFile();
                            fileCache.remove(key);
                        }
                    } else {
                        throw new CompilationError(
    /* (non-Javadoc)
     * @i18n.test
     * @org-mes="include type must be xml or text"
     */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                            Parser.class.getName(), "051018-485"));
                    }
                } else {
                    expandIncludes(child, currentFiles, env);
                }
            }
        }
    }

    /** Reads an XML file, expands includes, and validates it.
     */
    public Document parse(File file, CompilationEnvironment env)
      throws CompilationError
    {
        String pathname = file.getPath();
        try {
            Document doc = readExpanded(file, new HashSet<File>(), env);
            // Apply the stylesheet
            doc = preprocess(doc);
            return doc;
        } catch (IOException e) {
            CompilationError ce = new CompilationError(e);
            ce.initPathname(pathname);
            throw ce;
        } catch (JDOMException e) {
            String solution = SolutionMessages.findSolution(e.getMessage(), SolutionMessages.PARSER);
            CompilationError ce = new CompilationError(e, solution);
            throw ce;

        } 
    }
    
    void saveStartLocation (Element elt,
                            String pathname,
                            String messagePathname,
                            int startLineNumber,
                            int startColumnNumber) {
        SourceLocator info = ((ElementWithLocationInfo) elt).locator;
        info.setPathname(pathname, messagePathname);
        info.startLineNumber   = startLineNumber;
        info.startColumnNumber = startColumnNumber;
    }

    void saveEndLocation (Element elt,
                          String pathname,
                          String messagePathname,
                          int endLineNumber,
                          int endColumnNumber) {
        SourceLocator info = ((ElementWithLocationInfo) elt).locator;
        info.setPathname(pathname, messagePathname);
        info.endLineNumber   = endLineNumber;
        info.endColumnNumber = endColumnNumber;
    }
    
    /* Implement source location, on top of metadata */
    static final int LINENO = 1;
    static final int COLNO = 2;
    
    static String getSourcePathname(Element elt) {
        SourceLocator info = ((ElementWithLocationInfo) elt).locator;
        return info.pathname;
    }

    static String getSourceMessagePathname(Element elt) {
        SourceLocator info = ((ElementWithLocationInfo) elt).locator;
        return info.messagePathname;
    }

    static Integer getSourceLocation(Element elt, int coord, boolean start) {
        if (elt == null) {
            return null;
            // +++ should we throw an error if elt == null?
        } 

        SourceLocator info = ((ElementWithLocationInfo) elt).locator;


        if (coord == LINENO) {
            return (start ? info.startLineNumber : info.endLineNumber);
        } else {
            return (start ? info.startColumnNumber : info.endColumnNumber);
        }
    }
    
    static Integer getSourceLocation(Element elt, int coord) {
        return getSourceLocation(elt, coord, true);
    }


    class SourceLocatorJDOMFactory extends org.jdom.DefaultJDOMFactory {
                
        public SourceLocatorJDOMFactory () {
            super();
        }

        @Override
        public Element element(String name) {
            return new ElementWithLocationInfo(name);
        }

        @Override
        public Element element(String name, Namespace namespace) {
            return new ElementWithLocationInfo(name, namespace);
        }

        @Override
        public Element element(String name, String uri) {
            return new ElementWithLocationInfo(name, uri);
        }

        @Override
        public Element element(String name, String prefix, String uri) {
            return new ElementWithLocationInfo(name, prefix, uri);
        }
    }
}
