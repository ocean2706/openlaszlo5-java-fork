/* -*- mode: Java; c-basic-offset: 2; -*- */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;
import java.util.*;
import org.openlaszlo.sc.parser.SimpleNode;
import org.openlaszlo.sc.parser.*;

public class ReferenceCollector {
  // For debugging the generator
  static public boolean DebugConstraints = false;

  // The LFC guarantees that these won't change.
  static Set<String> immutableProperties = new HashSet<String>();
  static {
    immutableProperties.add("parent");
    immutableProperties.add("immediateParent");
    immutableProperties.add("classroot");
    immutableProperties.add("canvas");
  }

  boolean computeMetaReferences = false;
  Set<ASTPropertyIdentifierReference> references;
  Set<ASTCallExpression> functions;
  Set<ASTPropertyIdentifierReference> metareferences;
  Set<ASTCallExpression> metafunctions;
  Set<String> annotations;
  String depth = "";
  static Compiler.Parser parser = new Compiler.Parser();
  static ParseTreePrinter ptp = new ParseTreePrinter();

  public ReferenceCollector() {
    this(false);
  }

  public ReferenceCollector(boolean computeMetaReferences) {
    this.computeMetaReferences = computeMetaReferences;
    // Linked Hash Set so debug info will correspond
    this.references = new LinkedHashSet<ASTPropertyIdentifierReference>();
    this.functions = new LinkedHashSet<ASTCallExpression>();
    this.annotations = new LinkedHashSet<String>();
    if (computeMetaReferences) {
      this.metareferences = new LinkedHashSet<ASTPropertyIdentifierReference>();
      this.metafunctions = new LinkedHashSet<ASTCallExpression>();
    }
  }

  // Convert a list of PropertyReference's to an ArrayLiteral of
  // alternating reference/property
  private SimpleNode rsubst(Set<ASTPropertyIdentifierReference> r) {
    List<SimpleNode> l = new ArrayList<SimpleNode>();
    Set<String> added = new HashSet<String>();
    for (SimpleNode n : r) {
      SimpleNode c = n.get(0);
      ASTIdentifier p = (ASTIdentifier)n.get(1);
      annotations.add(ptp.text(c));
      String s = ptp.text(n);
      // Eliminate redundant constraints
      if (! added.contains(s)) {
        added.add(s);
        l.add(c);
        l.add(new ASTLiteral(p.getName()));
      }
    }
    SimpleNode s = new ASTArrayLiteral(0);
    s.setChildren(l.toArray(new SimpleNode[0]));
    return s;
  }

  // callee.fn(args...) ->
  //    ('$lsc$fn_dependencies' in callee) ?
  //      callee['$lsc$fn_dependencies'](args...) : []
  // If callee doesn't exist (i.e. original expression is just fn()),
  // then 'this' is assumed for callee.  If we don't downcast the callee
  // at the point of the call, on SWF9 we'd get compile errors
  // when the dependency function doesn't exist.
  // The cast is a no-op for runtimes that don't need them.
  private SimpleNode fsubst(SimpleNode node) {
    SimpleNode fn = node.get(0);
    SimpleNode callee;
    if (fn instanceof ASTPropertyIdentifierReference) {
      callee = fn.get(0);
      fn = fn.get(1);
    } else {
      callee = new ASTThisReference(0);
    }
    String ctnm = ScriptCompiler.quote(ptp.text(callee));
    String fnnm = ScriptCompiler.quote(((ASTIdentifier)fn).getName());

    // the function uses #pragma "warnUndefinedReferences=false"
    // to avoid warnings for non-existent dependencies
    // and #pragma "throwsError=true" to avoid the debugger catching
    // them, instead they are caught by applyConstraintExpr
    Map<String, Object> map = new HashMap<String, Object>();
    map.put("_1", callee);
    SimpleNode args = new ASTArrayLiteral(0);
    args.setChildren(node.get(1).getChildren());
    map.put("_2", args);

    return parser.substitute(fn, "$lzc$getFunctionDependencies(" + fnnm + ", this, _1, _2, ($debug ? (" + ctnm + ") : null))", map);
  }

  // Used to seed the pureFunctions table to avoid trying to get dependencies
  // unnecessarily
  private String[] javaSucks = {
        "Math.abs",
        "Math.acos",
        "Math.asin",
        "Math.atan",
        "Math.atan2",
        "Math.ceil",
        "Math.cos",
        "Math.exp",
        "Math.floor",
        "Math.log",
        "Math.max",
        "Math.min",
        "Math.pow",
        "Math.random",          // You could argue otherwise...
        "Math.round",
        "Math.sin",
        "Math.sqrt",
        "Math.tan"
  };
  private HashSet<String> pureFunctions = new HashSet<String>(Arrays.asList(javaSucks));

  // Concatenate references array with any results from dependeny
  // functions
  private String build(Set<ASTPropertyIdentifierReference> references, Set<ASTCallExpression> functions) {
    SimpleNode a = rsubst(references);
    Map<String, Object> map = new HashMap<String, Object>();
    Set<String> added = new HashSet<String>();
    for (SimpleNode n : functions) {
      String f = ptp.text(n.get(0));
      if (! pureFunctions.contains(f)) {
        String s = ptp.text(n);
        // Eliminate redundant constraints
        if (! added.contains(s)) {
          added.add(s);
          SimpleNode b = fsubst(n);
          map.put("_1", a);
          map.put("_2", b);
          a = parser.substitute(n,"_1.concat(_2)", map);
        }
      }
    }
    return ptp.text(a);
  }

  // TODO: [2006-02-22 dda] Do we want to keep computeMetaReferences?
  // ptw says it's a failed experiment.  It's hard to test any
  // new code that keeps it alive.

  public String computeReferencesAsExpression() {
    return build(references, functions);
  }

  public String computeReferencesDebugAnnoration() {
    String debugNames = "[";
    for (Iterator<String> i = annotations.iterator(); i.hasNext(); ) {
      debugNames += ScriptCompiler.quote(i.next());
      if (i.hasNext()) { debugNames += ", "; }
    }
    debugNames += "]";
    return debugNames;
  }

  public void visit(SimpleNode node) {
    visitInternal(node, references, functions);
  }

  // Imagine how easy this would be to write with generic functions!
  private void visitInternal(SimpleNode node, Set<ASTPropertyIdentifierReference> references, Set<ASTCallExpression> functions) {
    if (DebugConstraints) {
      System.out.println(depth + node);
    }
    try {
      if (DebugConstraints) {
        depth = depth + "    ";
      }
      if (node instanceof ASTFunctionDeclaration ||
          node instanceof ASTFunctionExpression) {
        // Don't traverse inside nested functions
        ;
      } else if (node instanceof ASTIdentifier) {
        SimpleNode p = new ASTPropertyIdentifierReference(0);
        SimpleNode[] c = {new ASTThisReference(0), node};
        p.setChildren(c);
        references.add((ASTPropertyIdentifierReference) p);
      } else if (node instanceof ASTPropertyIdentifierReference) {
        SimpleNode base = node.get(0);
        if (computeMetaReferences) {
          // Visit the base for meta-dependencies
          visitInternal(base, metareferences, metafunctions);
        }
        // If the base is a function, collect its dependency function
        if (base instanceof ASTCallExpression) {
          functions.add((ASTCallExpression) base);
        }
        references.add((ASTPropertyIdentifierReference) node);
      } else if (node instanceof ASTCallExpression) {
        // Optimization: ignore setAttribute(...),
        // since it's used in every constraint expression
        // and doesn't have a dependency function (and if
        // it did, it would always return null).
        SimpleNode base = node.get(0);
        if (base instanceof ASTPropertyIdentifierReference &&
            base.get(0) instanceof ASTThisReference &&
            base.get(1) instanceof ASTIdentifier &&
            "setAttribute".equals(((ASTIdentifier)base.get(1)).getName())) {
          ;
        } else {
          if (computeMetaReferences) {
            // Visit the function for meta-dependencies
            visitInternal(base, metareferences, metafunctions);
          }
          // Collect the function's dependency function
          functions.add((ASTCallExpression) node);
        }
        // Visit the arguments.
        visitInternal(node.get(1), references, functions);
      } else {
        for (int i = 0, len = node.size(); i < len; i++) {
          visitInternal(node.get(i), references, functions);
        }
      }
    }
    finally {
      if (DebugConstraints) {
        depth = depth.substring(0, depth.length() - 4);
        System.out.println(depth + "references: " + references);
        System.out.println(depth + "functions: " + functions);
        if (computeMetaReferences) {
          System.out.println(depth + "metareferences: " + metareferences);
          System.out.println(depth + "metafunctions: " + metafunctions);
        }
      }
    }
  }
}

