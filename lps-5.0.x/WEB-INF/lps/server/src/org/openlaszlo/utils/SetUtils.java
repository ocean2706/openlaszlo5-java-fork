/* *****************************************************************************
 * SetUils.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.utils;
import java.util.*;

/**
 * A utility class containing set utility functions.
 *
 * @author Oliver Steele
 */
public abstract class SetUtils {
    public static <E> boolean containsAny(Set<E> a, Set<E> b) {
        for (E e : b) {
            if (a.contains(e)) {
                return true;
            }
        }
        return false;
    }

    public static <E> Set<E> intersection(Set<E> a, Set<E> b) {
        Set<E> c = new HashSet<E>();
        for (E e : b) {
            if (a.contains(e)) {
                c.add(e);
            }
        }
        return c;
    }
}
