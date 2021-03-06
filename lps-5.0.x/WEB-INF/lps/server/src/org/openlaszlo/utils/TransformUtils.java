/* *****************************************************************************
 * TransformUtils.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2009, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.io.*;
import java.util.*;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamSource;
import org.openlaszlo.server.LPS;

public abstract class TransformUtils {
    /** {Pathname -> Templates} */
    static private Map<String, Templates> sTemplatesMap = new HashMap<String, Templates>();
    /** {Pathname -> lastModified} */
    static private Map<String, Long> sTemplatesLastModified = new HashMap<String, Long>();
    
    static public Templates getTemplates(String styleSheetPathname)
        throws IOException
    {
        synchronized (sTemplatesMap) {
            Templates templates = sTemplatesMap.get(styleSheetPathname);
            Long lastModified = Long.valueOf(new File(styleSheetPathname).lastModified());
            if (templates != null &&
                !sTemplatesLastModified.get(styleSheetPathname).equals(lastModified))
                templates = null;
            if (templates == null) {
                CollectingErrorListener errorListener =
                    new CollectingErrorListener();
                // name the class instead of using
                // TransformerFactory.newInstance(), to insure that we
                // get saxon and thereby work around bug 3924
                TransformerFactory factory =
                    new com.icl.saxon.TransformerFactoryImpl();
                factory.setURIResolver(new TemplateResolver());
                factory.setErrorListener(errorListener);
                java.io.InputStream xslInput = 
                    new java.net.URL("file", "", styleSheetPathname).openStream();
                try {
                    Source xslSource = 
                        new javax.xml.transform.stream.StreamSource(xslInput);
                    templates = factory.newTemplates(xslSource);
                } catch (TransformerConfigurationException e) {
                    if (errorListener.isEmpty())
                        throw new ChainedException(e);
                    else
                        throw new ChainedException(errorListener.getMessage(), e);
                } finally {
                    xslInput.close();
                }
                sTemplatesMap.put(styleSheetPathname, templates);
                sTemplatesLastModified.put(styleSheetPathname, lastModified);
            }
            return templates;
        }
    }

    public static void applyTransform(String styleSheetPathname,
                                      String xmlString,
                                      OutputStream out)
        throws IOException
    {
        applyTransform(styleSheetPathname, new Properties(), xmlString, out);
    }
    
    // http://xml.apache.org/xalan-j/usagepatterns.html#multithreading
    // describes this usage pattern.
    static public void applyTransform(String styleSheetPathname,
                                      Properties properties,
                                      String xmlString,
                                      OutputStream out)
        throws IOException 
    {
        PrintWriter writer = new PrintWriter(out);
        CollectingErrorListener errorListener =
            new CollectingErrorListener();
        try {
            Templates template = getTemplates(styleSheetPathname);
            Transformer transformer = template.newTransformer();
            transformer.setErrorListener(errorListener);
            Source xmlSource =
                new javax.xml.transform.stream.StreamSource(
                    new StringReader(xmlString));
            for (Iterator<Object> iter = properties.keySet().iterator();
                 iter.hasNext(); ) {
                String key = (String) iter.next();
                String value = properties.getProperty(key);
                transformer.setParameter(key, value);
            }
            // Perform the transformation, sending the output to the response.
            transformer.transform(xmlSource,
                                  new javax.xml.transform.stream.StreamResult(writer));
            writer.close();
        } catch (TransformerConfigurationException e) {
            if (errorListener.isEmpty())
                throw new ChainedException(e);
            else
                throw new ChainedException(errorListener.getMessage(), e);
        } catch (TransformerException e) {
            if (errorListener.isEmpty())
                throw new ChainedException(e);
            else
                throw new ChainedException(errorListener.getMessage(), e);
        }
    }
}

class CollectingErrorListener implements javax.xml.transform.ErrorListener {
    protected StringBuffer messageBuffer = new StringBuffer();
    protected String separator = "";
    protected int messageCount = 0;
    
    private void appendErrorString(TransformerException exception) {
        messageBuffer.append(separator);
        separator = "\n";
        messageBuffer.append(exception.getMessageAndLocation());
        messageCount++;
    }

    public void error(TransformerException exception) {
        appendErrorString(exception);
    }
    
    public void warning(TransformerException exception) {
        appendErrorString(exception);
    }
    
    public void fatalError(TransformerException exception) {
        appendErrorString(exception);
    }
    
    boolean isEmpty() {
        return messageCount == 0;
    }

    String getMessage() {
        return messageBuffer.toString();
    }
}

class TemplateResolver implements URIResolver {
  String base_path;
  public TemplateResolver() {
    this.base_path = LPS.getTemplateDirectory();
  }

  public Source resolve(String href,String base) {
    StringBuilder path = new StringBuilder(this.base_path);
    path.append(File.separator);
    path.append(href);
    //System.out.println("resolving " + href + ", " + base + " to: " + path.toString());
    File file = new File(path.toString());
    if(file.exists()) return new StreamSource(file);
    return null;
  }
}

