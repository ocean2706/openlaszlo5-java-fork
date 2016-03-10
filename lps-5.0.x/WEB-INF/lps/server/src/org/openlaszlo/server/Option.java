/******************************************************************************
 * Option.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.log4j.Logger;
import org.jdom.Element;

/**
 * An Option contains a set of deny and allow patterns.
 * 
 * @author Eric Bloch
 * @version 1.0 
 */
@SuppressWarnings("serial")
public class Option implements Serializable {

    private static Logger mLogger  = Logger.getLogger(Option.class);

    // No need to sync; access is read only after construction. These lists hold
    // regexp objects.
    private ArrayList<Pattern> allowList = null;
    private ArrayList<Pattern> deniesList = null;

    // These lists hold the pattern for the regexp objects.
    private ArrayList<String> allowSerializableList = null;
    private ArrayList<String> deniesSerializableList = null;

    void init() {
        if (allowList == null)
            allowList  = new ArrayList<Pattern>();
        if (deniesList == null)
            deniesList = new ArrayList<Pattern>();        
        if (allowSerializableList == null) 
            allowSerializableList = new ArrayList<String>();
        if (deniesSerializableList == null)
            deniesSerializableList = new ArrayList<String>();
    }

    Option() {
        init();
    }

    /**
     * Constructs a new option
     * @param elt
     */
    Option(Element el) {
        this();
        addElement(el);
    }
    
    /**
     * Add new patterns to option
     */
    public void addElement(Element el) {
        Element a = el.getChild("allow", el.getNamespace());
        if (a != null)
            addPatterns(allowList, allowSerializableList, a);
        Element d = el.getChild("deny", el.getNamespace());
        if (d != null)
            addPatterns(deniesList, deniesSerializableList, d);
    }

    /**
     * @param l list to add REs to
     * @param element that has pattern element children
     */
    private void addPatterns(ArrayList<Pattern> list, ArrayList<String> serializableList, Element elt) {
        ListIterator<?> iter = 
            elt.getChildren("pattern", elt.getNamespace()).listIterator();

        while(iter.hasNext()) {
            String p = ((Element)iter.next()).getTextNormalize();
            mLogger.debug(elt.getName() + ": " + p);
            try {
                Pattern re = Pattern.compile(p);
                list.add(re);
                serializableList.add(p);
            } catch (PatternSyntaxException e) {
mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="ignoring bad regexp syntax: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Option.class.getName(),"051019-99", new Object[] {p})
                );
                continue;
            }
        }
    }

    /**
     * @param val value to check against
     * @param allow if true, an undefined option means that it is allowed, else
     * it is denied.
     * @return true if this option allows the given value
     */
    boolean allows(String val, boolean allow) {

mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="checking: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Option.class.getName(),"051019-120", new Object[] {val})
        );

        // If we don't specify what's allowed, allow all.
        if (!allowList.isEmpty())  {
            allow = false;
            for (Pattern p : allowList) {
                if (p.matcher(val).matches()) {
                    allow = true;
                    break;
                }
            }
        }

        if (allow) {
            for (Pattern p : deniesList) {
                if (p.matcher(val).matches()) {
                    allow = false;
                    break;
                }
            } 
        }
        return allow;
    }


    /**
     * Handle object serialization.
     */
    private void writeObject(ObjectOutputStream out)
        throws IOException {

        ListIterator<String> iter;

        // write out allow
        out.writeInt(allowSerializableList.size());
        iter = allowSerializableList.listIterator();
        while (iter.hasNext()) {
            out.writeObject(iter.next());
        }

        // write out denies
        out.writeInt(deniesSerializableList.size());
        iter = deniesSerializableList.listIterator();
        while (iter.hasNext()) {
            out.writeObject(iter.next());
        }
    }

    /**
     * Handle object deserialization.
     */
    private void readObject(ObjectInputStream in)
        throws IOException, ClassNotFoundException {
        int size, i;

        init();

        size = in.readInt();
        for (i=0; i < size; i++) {
            String p = (String)in.readObject();
            try {
                allowList.add(Pattern.compile(p));
                allowSerializableList.add(p);
            } catch (PatternSyntaxException e) {
mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="ignoring bad regexp syntax: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Option.class.getName(),"051019-99", new Object[] {p})
                );
                continue;
            }
        }

        size = in.readInt();
        for (i=0; i < size; i++) {
            String p = (String)in.readObject();
            try {
                deniesList.add(Pattern.compile(p));
                deniesSerializableList.add(p);
            } catch (PatternSyntaxException e) {
mLogger.error(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="ignoring bad regexp syntax: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Option.class.getName(),"051019-99", new Object[] {p})
                );
                continue;
            }
        }
    }
}
