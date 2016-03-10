/******************************************************************************
 * ComparisonMap.java
 *****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.util.*;

/**
 * Hash table that stores keys case-sensitively, but compares them
 * case-insensitively
 *
 * This differs from
 * org.apache.commons.collections.map.CaseInsensitiveMap in that it
 * preserves the first case used for a key (rather than converting all
 * keys to lower case)
 *
 */
@SuppressWarnings("serial")
public class ComparisonMap<K, V> extends LinkedHashMap<K, V> {

    /** canonical lower-case map */
    private HashMap<K, K> keyMap;

    public Set<K> normalizedKeySet() {
        return keyMap.keySet();
    }

    public ComparisonMap() {
        super();
        keyMap = new HashMap<K, K>();
    }

    public ComparisonMap(int initialCapacity) {
        super(initialCapacity);
        keyMap = new HashMap<K, K>(initialCapacity);
    }

    public ComparisonMap(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        keyMap = new HashMap<K, K>(initialCapacity, loadFactor);
    }

    public ComparisonMap(Map<? extends K, ? extends V> m) {
        this(m.size());
        putAll(m);
    }

    private Object caseInsensitiveKey(Object key) {
        return key instanceof String ? ((String)key).toLowerCase() : key;
    }

    @Override
    public void clear() {
        keyMap.clear();
        super.clear();
    }

    @Override
    public Object clone() {
        return new ComparisonMap<K, V>(this);
    }

    @Override
    public boolean containsKey(Object key) {
        return keyMap.containsKey(caseInsensitiveKey(key));
    }

    public boolean containsKey(Object key, boolean caseSensitive) {
        if (caseSensitive) {
            return keyMap.containsKey(key);
        } else {
            return keyMap.containsKey(caseInsensitiveKey(key));
        }
    }

    /* containsValue just works */

    /* entrySet just works, keys have original case */

    @Override
    public V get(Object key) {
        key = caseInsensitiveKey(key);
        if (keyMap.containsKey(key)) {
            return super.get(keyMap.get(key));
        } else {
            return null;
        }
    }

    /* isEmpty just works */

    /* keySet just works, keys have original case */

    @Override
    public V put (K key, V value) {
        @SuppressWarnings("unchecked")
        K mod = (K) caseInsensitiveKey(key);
        keyMap.put(mod, key);
        return super.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        key = caseInsensitiveKey(key);
        if (keyMap.containsKey(key)) {
            return super.remove(keyMap.remove(key));
        } else {
            return null;
        }
    }

    /* size just works */

    /* values just works */

}
