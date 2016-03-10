/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openlaszlo.sc.parser.*;

// This class extends common elements of the variable analyzer.  This
// analyzer's purpose is to transform uses of variables from var =>
// this.var .  The set of variables to transform is a constructor arg,
// already determined via a regular variable analyzer run and other
// analysis.
//     
public class WithThisAnalyzer extends GenericVariableAnalyzer {
  Set<String> bound;
  String functionName;
  // stack of nodes seen while visiting
  List<SimpleNode> nodestack = new ArrayList<SimpleNode>();
  // Maps variable name to List of UseSite, unordered
  Map<String, List<UseSite>> used = new HashMap<String, List<UseSite>>();

  // If we see a a 'with' statement, we flag it here
  boolean hasWith = false;

  // For a use site, we save the node (always an ASTIdentifier?)
  // and its parent, so we can patch the AST in place.
  public class UseSite {
    SimpleNode node;
    SimpleNode parent;

    // Apply the 'this.' binding to this site
    public void apply() {
      int index = parent.indexOf(node);
      if (index < 0) {
        throw new CompilerException("withThis binding: parent cannot find child");
      }
      SimpleNode[] refchildren = { new ASTThisReference(0), node };
      SimpleNode ref = new ASTPropertyIdentifierReference(0);
      ref.setChildren(refchildren);
      parent.getChildren()[index] = ref;
    }
  }

  public WithThisAnalyzer(SimpleNode params, boolean ignoreFlasm, boolean hasSuper, ASTVisitor visitor, Set<String> bound, String fcnname) {
    super(ignoreFlasm, hasSuper, visitor);
    this.bound = bound;
    this.functionName = fcnname;
    visitParams(params);
  }

  @Override
  public void addUse(String variable, SimpleNode node) {
    if (bound.contains(variable)) {
      UseSite us = new UseSite();
      us.node = node;
      // The last item in the nodestack is the node currently visited,
      // the one before that is its parent.  This is an accurate way
      // to get the parent, using the SimpleNode's parent link is not
      // always correct.
      us.parent = nodestack.get(nodestack.size() - 2);
      addUseSite(variable, us);
    }
  }

  public void addUseSite(String variable, UseSite us) {
    List<UseSite> list = used.get(variable);
    if (list == null) {
      list = new ArrayList<UseSite>();
      used.put(variable, list);
    }
    list.add(us);
  }

  // Should never be called, nobody calls computeReferences
  @Override
  public Set<String> usedVariables() {
    return null;
  }

  // Should never be called, nobody calls computeReferences
  @Override
  public Set<String> innerFreeVariables() {
    return null;
  }

  // uses within the closure should not be transformed, so
  // no need to copy them.  Also, no need to update innerFree,
  // as computeReferences is never called for this analyzer.
  @Override
  public void subsumeUses(GenericVariableAnalyzer analyzer) {
  }

  public void apply() {
    for (String v : used.keySet()) {
      List<UseSite> list = used.get(v);
      if (list != null) {
        for (UseSite us : list) {
          us.apply();
        }
      }
    }
  }

  @Override
  public GenericVariableAnalyzer newAnalyzer(SimpleNode params) {
    return new WithThisAnalyzer(params, ignoreFlasm, hasSuper, visitor, bound, functionName);
  }

  @Override
  public void visit(SimpleNode node) {
    // If there is a with statement, then we just note that, and go no deeper,
    // we'll just punt and emit with(this) at the top level.
    // A potential improvement is to emit the with(this)
    // right at this point, but frankly, if the function has 'with',
    // then they probably aren't concerned about performance.
    if (node instanceof ASTWithStatement) {
      hasWith = true;
      // no need to look deeper
    }
    else {
      nodestack.add(node);
      super.visit(node);
      nodestack.remove(nodestack.size() - 1);
    }
  }
}
