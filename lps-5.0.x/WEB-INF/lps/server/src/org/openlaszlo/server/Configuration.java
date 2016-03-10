/******************************************************************************
 * Configuration.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.filter.ElementFilter;
import org.jdom.input.SAXBuilder;
import org.openlaszlo.utils.ChainedException;

/**
 * Configuration contains global server configuration information.
 * It reads an array of options from an xml file.
 * 
 * @author Eric Bloch
 * @version 1.0 
 */
public class Configuration {

    Map<String, Option> mOptions      = new Hashtable<String, Option>();
    Map<String, Map<String, Option>> mAppPaths = new Hashtable<String, Map<String, Option>>();
    Map<Pattern, Map<String, Option>> mAppPatterns = new Hashtable<Pattern, Map<String, Option>>();
    ArrayList<Pattern> mAppRegexps = new ArrayList<Pattern>();
    Map<String, Map<String, Option>> mAppOptions = new Hashtable<String, Map<String, Option>>(); // options set in application LZX
    
    private static Logger mLogger  = Logger.getLogger(Configuration.class);

    /**
     * Constructs a new configuration
     */
    public Configuration() 
    {
        try {
            mLogger.debug("building configuration");
            SAXBuilder builder = new SAXBuilder();
    
            String fileName = LPS.getConfigDirectory() + File.separator + "lps.xml";
            Document doc = builder.build(new File(fileName));
            Element root = doc.getRootElement();
            List<?> elts = root.getContent(new ElementFilter()); 
            ListIterator<?> iter = elts.listIterator();

            while(iter.hasNext()) {
                Element elt = (Element)iter.next();

                // Check for global default options. 
                if (elt.getName().equals("option")) {
                    String name = elt.getAttributeValue("name");
                    if (name != null && !name.equals("")) {
                        Option option = new Option(elt);
                        mOptions.put(name, option);
                    }
                }

                // Check for application specific options
                if (elt.getName().equals("application")) {
                    String path    = elt.getAttributeValue("path");
                    String pattern = elt.getAttributeValue("pattern");

                    if (path    != null  && ! path.equals("") &&
                        pattern != null  && ! pattern.equals("")) {
                        String msg = 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Can't have attributes path (" + p[0] + ")" + " and pattern (" + p[1] + ") " + " defined in an application element together."
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Configuration.class.getName(),"051019-80", new Object[] {path, pattern});
                            
                        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Exception reading configuration: " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Configuration.class.getName(),"051019-88", new Object[] {msg})
                            );
                        throw new ChainedException(msg);
                    }

                    if (path != null && !path.equals("")) {
                        Hashtable<String, Option> appOpts = getOptions(elt, "option", "name");
                        if (appOpts.size() != 0)
                            mAppPaths.put(path, appOpts);
                    } 

                    if (pattern != null && !pattern.equals("")) {
                        Hashtable<String, Option> appOpts = getOptions(elt, "option", "name");
                        if (appOpts.size() != 0) {
                            Pattern re = Pattern.compile(pattern);
                            mAppRegexps.add(re);
                            mAppPatterns.put(re, appOpts);
                        }
                    }
                }
            }
        } catch (PatternSyntaxException e) {
mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="RE exception reading configuration " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Configuration.class.getName(),"051019-116", new Object[] {e.getMessage()})
            );
            throw new ChainedException(e.getMessage());
        } catch (JDOMException e) {
mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="jdom exception reading configuration " + p[0]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Configuration.class.getName(),"051019-126", new Object[] {e.getMessage()})
            );
            throw new ChainedException(e.getMessage());
        } catch (IOException e) {
            mLogger.error("io exception reading configuration " + e.getMessage());
            throw new ChainedException(e.getMessage());
        }
    }


    /**
     * @param app element
     * @param tagname tag name
     * @param optname option name
     * @return hashtable of options
     */
    public static Hashtable<String, Option> getOptions(Element app, String tagname, String optname)
    {
        Hashtable<String, Option> options = new Hashtable<String, Option>();
        List<?> elts = app.getContent(new ElementFilter());
        ListIterator<?> iter = elts.listIterator();
        while (iter.hasNext()) {
            Element elt = (Element)iter.next();
            if (elt.getName().equals(tagname)) {
                String name = elt.getAttributeValue(optname);
                if (name != null && !name.equals("")) {
                    addOptionNamed(options, elt, name);
                }
            }
        }
        return options;
    }

    public static void addOption(Map<String, Option> options, Element elt) {
        String name = elt.getName();
        Option option = options.get(name);
        if (option == null) {
            option = new Option(elt);
            options.put(name, option);
        } else {
            option.addElement(elt);
        }
    }

    public static void addOptionNamed(Map<String, Option> options, Element elt, String name) {
        Option option = options.get(name);
        if (option == null) {
            option = new Option(elt);
            options.put(name, option);
        } else {
            option.addElement(elt);
        }
    }


    /**
     * @param path application path
     * @return table of application options
     */
    public Map<String, Option> getApplicationOptions(String path) {
        return mAppOptions.get(path);
    }

    /**
     * @param path application path
     * @param opts application option hashtable from LZX
     */
    public void setApplicationOptions(String path, Map<String, Option> opts) {
        mAppOptions.put(path, opts);
    }


    /**
     * @return true if the option is allowed for given value of the 
     * given key
     */
    public boolean optionAllows(String key, String value) {
        return optionAllows(key, value, true);
    }

    /**
     * @param allow if true, an undefined option means that it is
     * allowed, else it is denied.
     * @return true if the option is allowed for given value of the 
     * given key
     */
    public boolean optionAllows(String key, String value, boolean allow) {
        Option opt = mOptions.get(key);
        if (opt != null) {
            return opt.allows(value, allow);
        } else {
mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="No option for " + p[0] + "; is allowed? " + p[1]
 */
            org.openlaszlo.i18n.LaszloMessages.getMessage(
                Configuration.class.getName(),"051019-215", new Object[] {key, allow})
            );
            return allow;
        }
    }


    /**
     * @param path application path relative to webapp.
     * @return true if the option is allowed for given value of the 
     * given key
     */
    public boolean optionAllows(String path, String key, String value) {
        return optionAllows(path, key, value, true);
    }

    /**
     * @param path application path relative to webapp.
     * @param allow if true, an undefined option means that it is
     * allowed, else it is denied.
     * @return true if the option is allowed for given value of the 
     * given key
     */
    public boolean optionAllows(String path, String key, String value,
                                boolean allow) {
        Option opt;
        Map<String, Option> appOptions;

        // Check LZX configured application options.
        appOptions = mAppOptions.get(path);
        if (appOptions != null) {
            opt = appOptions.get(key);
            if (opt != null) {
                return opt.allows(value, allow);
            }
        }

        // Check for server configured application option.
        appOptions = mAppPaths.get(path);
        if (appOptions != null) {
            opt = appOptions.get(key);
            if (opt != null) {
                return opt.allows(value, allow);
            }
        }

        // Check regexp patterns
        if (! mAppRegexps.isEmpty()) {
            for (Pattern re : mAppRegexps) {
                if (re.matcher(path).matches()) {
                    appOptions = mAppPatterns.get(re);
                    if (appOptions != null) {
                        opt = appOptions.get(key);
                        if (opt != null) {
                            return opt.allows(value, allow);
                        }
                    }
                }
            }
        }

        // Check for global option.
        return optionAllows(key, value, allow);
    }
}
