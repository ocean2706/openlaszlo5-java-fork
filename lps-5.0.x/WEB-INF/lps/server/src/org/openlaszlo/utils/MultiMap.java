/* *****************************************************************************
 * MultiMap.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;

import java.util.Collection;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Partial implementation of a map that stores multiple values for a single
 * key. */
public class MultiMap<K, V>
    implements Map<K, Set<V>>
{
    /** Table to store information; each node may hold multiple values */
    Hashtable<K, Set<V>> mTable = new Hashtable<K, Set<V>>();

    /** Returns a Set view of the keys contained in this Hashtable. The Set is
     * backed by the Hashtable, so changes to the Hashtable are reflected in the
     * Set, and vice-versa. The Set supports element removal (which removes the
     * corresponding entry from the Hashtable), but not element addition. */
    public Set<K> keySet()
    {
        return mTable.keySet();
    }

    /** Returns the set of values to which the specified key is mapped in this
     * multimap.
     * @param key key in the map 
     * @return set of values to which the specified key is mapped; null if none
     * is found */
    public Set<V> get(Object key)
    {
        return mTable.get(key);
    }

    /** Maps the specified key to the specified value in this map. The key
     * and value may not be null.
     * @param key the map key 
     * @param val the value
     * @return the set that val was added to */
    public Set<V> add(K key, V val)
    {
        Set<V> valSet = mTable.get(key);
        if (valSet == null) {
            valSet = new HashSet<V>();
            mTable.put(key, valSet);
        }
        valSet.add(val);
        return valSet;
    }

    /** Maps the specified key to the specified values in this map. The key
     * and values may not be null.
     * @param key the map key 
     * @param val the values
     * @return the set that val was added to */
    public Set<V> put(K key, Set<V> val)
    {
        Set<V> valSet = mTable.get(key);
        if (valSet == null) {
            valSet = new HashSet<V>();
            mTable.put(key, valSet);
        }
        valSet.addAll(val);
        return valSet;
    }

    /** Remove a particular value associated with key.
     * @param key the key to check for associated value
     * @param val the actual value to remove
     * @return the value to which the key had be mapped in this mulimap or
     * null, if the key did not have a mapping */
    public V remove(K key, V val)
    {
        V ret = null;
        Set<V> valSet = mTable.get(key);
        if (valSet != null) {
            if (valSet.remove(val))
                ret = val;
            if (valSet.isEmpty())
                mTable.remove(key);
        }
        return ret;
    }

    /** Remove all values associated with key.
     * @param key the key to remove
     * @return the set of values removed that were associated with key */
    public Set<V> remove(Object key)
    {
        return mTable.remove(key);
    }

    /** Returns a Collection view of the values contained in this Hashtable. The
     * Collection is backed by the Hashtable, so changes to the Hashtable are
     * reflected in the Collection, and vice-versa. The Collection supports
     * element removal (which removes the corresponding entry from the
     * Hashtable), but not element addition. 
     * @return a collection view of the values contained in this map */
    public Collection<Set<V>> values()
    {
        return mTable.values();
    }


    /** Returns the number of key-set mappings in this map. If the map contains
     * more than Integer.MAX_VALUE elements, returns Integer.MAX_VALUE.
     * @return the number of key-value mappings in this map */
    public int size()
    {
        return mTable.size();
    }

    /** Returns <code>true</code> if this map contains no key-value mappings.
     * @return <code>true</code> if this map contains no key-value mappings */
    public boolean isEmpty()
    {
        return mTable.isEmpty();
    }

    /** Returns true if this map contains a mapping for the specified key. 
     * @param key key whose presence in this map is to be tested
     * @return true if this map contains a mapping for the specified key.
     */
    public boolean containsKey(Object key)
    {
        return mTable.containsKey(key);
    }

    /** Returns <code>true</code> if this map maps one or more keys to the
     * specified value. More formally, returns true if and only if this map
     * contains at least one mapping to a value v such that <code>(value==null ?
     * v==null : value.equals(v))</code>. This operation will probably require
     * time linear in the map size for most implementations of the Map
     * interface.
     * @param value value whose presence in this map is to be tested
     * @return <code>true</code> if this map maps one or more keys to the
     * specified value */
    public boolean containsValue(Object value)
    {
        Collection<Set<V>> c = mTable.values();
        for (Set<V> set : c) {
            if (set.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /** Remove value from map and return keys this value was associated with.
     * @param value value to remove
     * @return set of keys associated with removed value; null, if none was
     * found */
    public Set<K> removeValue(Object value)
    {
        Set<K> keySet = new HashSet<K>();
        Iterator<Map.Entry<K, Set<V>>> iter = mTable.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<K, Set<V>> entry = iter.next();
            Set<V> set = entry.getValue();
            if (set.contains(value)) {
                keySet.add(entry.getKey());
                set.remove(value);
                // if set is empty, remove the key-value entry in table
                if (set.isEmpty())
                    iter.remove();
            }
        }
        return (!keySet.isEmpty() ? keySet : null);
    }


    /** Unsupported. */
    public void putAll(Map<? extends K, ? extends Set<V>> m)
    {
        throw new UnsupportedOperationException();
    }

    /** Unsupported. */
    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    /** Returns a set view of the mappings contained in this map. Each element
     * in the returned set is a Map.Entry. The set is backed by the map, so
     * changes to the map are reflected in the set, and vice-versa. If the map
     * is modified while an iteration over the set is in progress, the results
     * of the iteration are undefined. The set supports element removal, which
     * removes the corresponding mapping from the map, via the Iterator.remove,
     * Set.remove, removeAll, retainAll and clear operations. It does not
     * support the add or addAll operations. 
     * @return a set view of the mappings contained in this map */
    public Set<Map.Entry<K, Set<V>>> entrySet()
    {
        return mTable.entrySet();
    }

}
