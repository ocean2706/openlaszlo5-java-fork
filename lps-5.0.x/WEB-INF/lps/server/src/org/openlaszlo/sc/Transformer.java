/* -*- mode: Java; c-basic-offset: 2; -*- */

/**
 * Javascript 'Transformer'
 *
 * @author ptw@pobox.com
 * @description: JavaScript -> JavaScript translator
 *
 * Rewites JavaScript with transformations common to all platforms.
 *
 * Initially, all this does is expand includes, walk the AST, process
 * directives and move the class declarations to the front of the
 * program in dependency order.
 *
 * Eventually, we could implement platform-independent source-source
 * transformations and inlining here.
 *
 */

package org.openlaszlo.sc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openlaszlo.sc.parser.*;

public class Transformer extends GenericVisitor  {
  Map<String, ClassDeclaration> allDeclarations = new HashMap<String, ClassDeclaration>();
  Set<String> emitted = new HashSet<String>();
  Compiler.OptionMap options = new Compiler.OptionMap();

  @Override
  public void setOptions(Compiler.OptionMap options) {
    this.options = options;
  }

  // Entry point
  public SimpleNode transform (SimpleNode program) {
    SimpleNode[] children = program.getChildren();
    program = visitProgram(program, children);
    // May have been transformed, so refresh
    children = program.getChildren();
    // Resolve dependecies
    for (ClassDeclaration decl : allDeclarations.values()) {
      decl.resolve();
    }
    
    // Now emit classes in dependecy order
    List<SimpleNode> defs = new ArrayList<SimpleNode>();
    Set<ClassDeclaration> remaining = new HashSet<ClassDeclaration>(allDeclarations.values());
    while (! remaining.isEmpty()) {
      for (ClassDeclaration next : new HashSet<ClassDeclaration>(remaining)) {
        if (emitted.containsAll(next.requiredSet)) {
          System.err.println("script: " + next.name);
          remaining.remove(next);
          emitted.add(next.name);
          defs.add(next.AST);
        }
      }
    }

    // Emit
    defs.addAll(Arrays.asList(children));
    program.setChildren(defs.toArray(children));
    return program;
  }

  // Support for gathering class declarations
  public class ClassDeclaration {
    String name;
    public SimpleNode AST;
    Set<String> requiredSet;
    boolean resolved = false;

    public ClassDeclaration (String name, Set<String> requiredSet, SimpleNode AST) {
      this.name = name;
      this.requiredSet = requiredSet;
      this.AST = AST;
      // assert (! allDeclarations.containsKey(name)) : "Duplicate class: " + name;
      allDeclarations.put(name, this);
    }

    public void resolve() {
      if (resolved) return;
      for (String reqname : new HashSet<String>(requiredSet)) {
        ClassDeclaration reqclass = allDeclarations.get(reqname);
        // May be null if built-in superclass from runtime
        if (reqclass != null) {
          if (! reqclass.resolved) {
            reqclass.resolve();
          }
          requiredSet.addAll(reqclass.requiredSet);
        } else {
          System.err.println(name + " removed: " + reqname);
          requiredSet.remove(reqname);
        }
      }
      System.err.println(name + ": " + requiredSet.toString());
      resolved = true;
    }
  }

  // NOTE [2009-11-16 ptw] We have to override this because of the
  // unfortunate way modifiers are handled in the AST.  Why are they
  // not just slots on the node they modify?
  @Override
  public SimpleNode visitModifiedDefinition(SimpleNode node, SimpleNode[] children) {
    assert children.length == 1;
    SimpleNode child = children[0];
    if (child instanceof ASTClassDefinition) {
      return visitClassDefinition(child, child.getChildren());
    }
    return super.visitModifiedDefinition(node, children);
  }


  // We don't descend into these, just gather them up and reorder them
  @Override
  public SimpleNode visitClassDefinition(SimpleNode node, SimpleNode[] children) {
    int len = children.length;
    assert len >= 5;
    ASTIdentifier kindID = (ASTIdentifier)children[0];
    String kindName = kindID.getName();
    assert "class".equals(kindName) || "mixin".equals(kindName) || "interface".equals(kindName);

    ASTIdentifier classID = (ASTIdentifier)children[1];
    String className = classID.getName().intern();

    Set<String> requiredSet = new HashSet<String>();

    // Gather classes that this class requires
    SimpleNode superID = children[2];
    if (! (superID instanceof ASTEmptyExpression)) {
      requiredSet.add(((ASTIdentifier)superID).getName().intern());
    }
    SimpleNode mixinsList = children[3];
    if (! (mixinsList instanceof ASTEmptyExpression)) {
      assert mixinsList instanceof ASTMixinsList;
      SimpleNode[] mixins = mixinsList.getChildren();
      for (int j = 0, jlim = mixins.length; j < jlim; j++) {
        ASTIdentifier mixinID = (ASTIdentifier)mixins[j];
        requiredSet.add(((ASTIdentifier)mixinID).getName().intern());
      }
    }
    SimpleNode interfacesList = children[4];
    if (! (interfacesList instanceof ASTEmptyExpression)) {
      assert interfacesList instanceof ASTMixinsList;
      SimpleNode[] interfaces = interfacesList.getChildren();
      for (int k = 0, klim = interfaces.length; k < klim; k++) {
        ASTIdentifier interfaceID = (ASTIdentifier)interfaces[k];
        requiredSet.add(((ASTIdentifier)interfaceID).getName().intern());
      }
    }

    // See visitModifiedDefinition above -- we collude to move the
    // modified definition instead of just the class.
    SimpleNode parent = node.getParent();
    if (parent instanceof ASTModifiedDefinition) {
      node = parent;
    }
    new ClassDeclaration(className, requiredSet, node);
    // We'll visit this in dependency-order Not AST-order
    return new ASTEmptyExpression(0);
  }

  @Override
  public Boolean evaluateCompileTimeConditional(SimpleNode node) {
    return CompileTimeEvaluator.evaluate(node, options);
  }

  @Override
  public SimpleNode visitIfDirective(SimpleNode directive, SimpleNode[] children) {
    Boolean value = evaluateCompileTimeConditional(children[0]);
    if (value == null) {
      if (directive instanceof ASTIfDirective) {
        throw new CompilerError("undefined compile-time conditional " + Compiler.nodeString(directive.get(0)));
      } else {
        // only top-level if-directives must be resolvable at compile-time
        return visitIfStatement(directive, children);
      }
    } else if (value.booleanValue()) {
      SimpleNode clause = children[1];
      return visitStatement(clause, clause.getChildren());
    } else if (directive.size() > 2) {
      SimpleNode clause = children[2];
      return visitStatement(clause, clause.getChildren());
    }

    System.err.println("Nulling: " + directive.toString());
    return visitIfStatement(directive, children);
  }
}

/**
 * @copyright Copyright 2009, 2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */

