/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2005, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;
import java.util.*;

// Each entry has three parts:
// - a key, used to store and retrieve it
// - a checksum, which tells whether the value is current
// - a value
public final class ScriptCompilerCache<K, C, V> {
  private HashMap<K, ScriptCompilerCacheEntry<C, V>> cache;
  
  public ScriptCompilerCache() {
    super();
    this.cache = new HashMap<K, ScriptCompilerCacheEntry<C, V>>();
  }

  static class ScriptCompilerCacheEntry<C, V> {
    C checksum;
    V value;

    ScriptCompilerCacheEntry(C checksum, V value) {
      this.checksum = checksum;
      this.value = value;
    }
  }

  public boolean containsKey(K key, C checksum) {
    if (cache.containsKey(key)) {
      ScriptCompilerCacheEntry<C, V> entry = cache.get(key);
      if (entry.checksum.equals(checksum)) {
        return true;
      }
    }
    return false;
  }

  public V get(K key, C checksum) {
    ScriptCompilerCacheEntry<C, V> entry = cache.get(key);
    if (entry != null && entry.checksum.equals(checksum)) {
      return entry.value;
    }
    return null;
  }

  public void put(K key, C checksum, V value) {
    cache.put(key, new ScriptCompilerCacheEntry<C, V>(checksum, value));
  }
}
