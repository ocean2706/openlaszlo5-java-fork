/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.openlaszlo.sc.parser.SimpleNode;

public class VariableAnalyzer extends GenericVariableAnalyzer {
  Set<String> innerFree;
  public Map<String, Integer> used;

  public VariableAnalyzer(SimpleNode params, boolean ignoreFlasm, boolean hasSuper, ASTVisitor visitor) {
    super(ignoreFlasm, hasSuper, visitor);
    used = new HashMap<String, Integer>();
    innerFree = new HashSet<String>();
    visitParams(params);
  }

  // Can be used for special variables like 'this', for which we don't need to track a use site
  public void incrementUsed(String variable) {
    Integer num = used.get(variable);
    used.put(variable, ((num != null ? num : 0) + 1));
  }

  @Override
  public GenericVariableAnalyzer newAnalyzer(SimpleNode params) {
    return new VariableAnalyzer(params, ignoreFlasm, hasSuper, visitor);
  }

  @Override
  public void addUse(String variable, SimpleNode node) {
    incrementUsed(variable);
  }

  @Override
  public Set<String> usedVariables() {
    return used.keySet();
  }

  @Override
  public Set<String> innerFreeVariables() {
    return innerFree;
  }

  @Override
  public void subsumeUses(GenericVariableAnalyzer analyzer) {
    analyzer.computeReferences();
    for (String v : analyzer.free) {
      if (! innerFree.contains(v)) {
        innerFree.add(v);
      }
    }
  }
}
