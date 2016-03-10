/* ****************************************************************************
 * Schema.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.xml.internal;

import java.util.HashMap;
import java.util.Map;


/**
 * Describes the content model of an XML document.
 *
 * @author Oliver Steele
 * @version 1.0
 */
public abstract class Schema {
    
    /** Hold mapping from Javascript type names to Types */
    protected static Map<String, Type> typeNames = new HashMap<String, Type>();

    /** Represents the type of an attribute whose type isn't known. */
    public static final Type UNKNOWN_TYPE = newType("unknown");

    /** The type of an attribute. */
    public static class Type {
        protected String name;

        public Type(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
    
    /** Returns a unique type.
     * @return a unique type corresponding to typeName
     */
    public static Type newType(String typeName) {
        assert (! typeNames.containsKey(typeName));
        Type newtype = new Type(typeName);
        typeNames.put(typeName, newtype);
        return newtype;
    }

    public static void addTypeAlias(String typeName, Type type) {
        typeNames.put(typeName, type);
    }
}
