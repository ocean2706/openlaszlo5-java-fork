/* *****************************************************************************
 * VersionMap.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Map to retrieve and store an object based on some key. For example, could
 * be used to store different versions of the same SWF bytecode. Not
 * thread-safe.
 */
public class VersionMap {

    Map<Object, Map<Object, Object>> mMap = new HashMap<Object, Map<Object, Object>>();

    /**
     * Put a value based on version and key.
     */
    public void put(Object version, Object key, Object value) {
        Map<Object, Object> m = mMap.get(key);
        if (m == null) {
            m = new HashMap<Object, Object>();
            mMap.put(key, m);
        }
        m.put(version, value);
    }

    /**
     * Get value based on version and key.
     */
    public Object get(Object version, Object key) {
        Map<Object, Object> m = mMap.get(key);
        if (m == null) return null;
        return m.get(version);
    }

    /**
     * @return set of keys.
     */
    public Set<Object> keySet() {
        return mMap.keySet();
    }

    /**
     * @return map of versions.
     */
    public Map<Object, Object> getVersions(Object key) {
        return mMap.get(key);
    }

    /**
     * @return number of items in this map.
     */
    public int size() {
        return mMap.size();
    }
}
