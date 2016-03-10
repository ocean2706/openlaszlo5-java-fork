/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.             *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

/***
 * SourceFile
 * Author: Don Anderson
 */

package org.openlaszlo.sc;

import java.util.HashMap;

public class SourceFileMap {
  HashMap<String, SourceFile> names = new HashMap<String, SourceFile>();
  HashMap<Integer, SourceFile> ids = new HashMap<Integer, SourceFile>();

  public SourceFile byName(String name) {
    SourceFile s = names.get(name);
    if (s == null) {
      s = new SourceFile();
      s.name = name;
      s.id = names.size();
      names.put(name, s);
      ids.put(s.id, s);
    }
    return s;
  }

  public SourceFile byId(int id) {
    SourceFile s = ids.get(id);
    return s;
  }
}
