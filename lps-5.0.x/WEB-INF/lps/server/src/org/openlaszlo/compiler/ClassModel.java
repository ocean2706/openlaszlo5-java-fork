/* -*- mode: Java; c-basic-offset: 2; -*- */
/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
/**
 * LZX Classes
 */

package org.openlaszlo.compiler;

import java.util.*;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.openlaszlo.sc.Method;
import org.openlaszlo.sc.ScriptCompiler;
import org.openlaszlo.sc.ScriptClass;
import org.openlaszlo.sc.Function;
import org.openlaszlo.xml.internal.MissingAttributeException;

public class ClassModel implements Comparable<ClassModel> {
    protected final ViewSchema schema;
    protected String kind;
    /** This is really the LZX tag name */
    public final String tagName;
    /** Classes that are created for single instances */
    public final boolean anonymous;
    /** And this is the actual class name */
    public String className;
    /** The name for debugging */
    public String debugExtends;
    public String debugWith;
    public String debugImplements;
    public boolean isbuiltin = false;
    /* If superclass is a predefined system class, just store its name. */
    protected String superTagName = null;
    // This is null for the root class
    protected ClassModel superModel;
    protected String mixinTagNames[] = null;
    protected String interfaceTagNames[] = null;
    protected ClassModel interfaceModels[] = null;
    /** The CSS tag selectors that could apply to this class */
    protected List<String> CSSTagSelectors = null;

    
    // This is null for the root class
    public final Element definition;
    protected NodeModel nodeModel;
    protected boolean modelOnly;
    
    /** Set of tags that can legally be nested in this element */
    protected Set<String> mCanContainTags = new HashSet<String>();

      /** Set of forbidden child tags of this element */
    protected Set<String> mForbiddenTags = new HashSet<String>();

    protected boolean isInputText = false;
    public Set<String> requiredAttributes = new HashSet<String>();

    /* Class or superclass has an <attribute type="text"/>  */
    protected boolean supportsTextAttribute = false;

    /* Cache of some predicates */
    protected boolean isnode = false;
    protected boolean isstate = false;
    protected boolean isdatapath = false;
    protected boolean isdataset = false;

    /** Map attribute name to type */
    public final Map<String, AttributeSpec> attributeSpecs = new LinkedHashMap<String, AttributeSpec>();
    protected final Map<String, AttributeSpec> classAttributeSpecs = new LinkedHashMap<String, AttributeSpec>();

    protected String sortkey = null;

    // True when the super and mixin names have been resolved to class
    // models
    protected boolean resolved = false;

    @Override
    public String toString() {
      if (! anonymous) {
        return "<"+kind+" name='"+tagName+"'>";
      }
      String with = "";
      if (debugWith.length() > 0) {
        with = " with='" + debugWith + "'";
      }
      // Instance classes end up with a null tagName
      if (tagName == null) {
        return "<anonymous extends='" + debugExtends + "'" + with + ">";
      } else {
        return "<" + debugExtends + with + ">";
      }
    }

    static final String DEFAULT_SUPERCLASS_NAME = "view";

    // Construct a builtin class
    public ClassModel(String kind, String tagName, ViewSchema schema, CompilationEnvironment env) {
      this(kind, tagName, null, true, schema, null, env);
    }

    // Construct a user-defined class
    public ClassModel(String kind, String tagName, String className, boolean publish,
                      ViewSchema schema, Element definition, CompilationEnvironment env) {
      // Caution:  Don't install kind/tagName yet, they get adjusted below.
      this.isbuiltin = schema.loadingLFCSchema;
      mCanContainTags.addAll(ViewSchema.mMetaTags);
      // Cache some predicates
      if (ViewSchema.kindIsTag(kind)) {
        if ("node".equals(tagName)) {
          this.isnode = true;
        } else if ("inputtext".equals(tagName)) {
          this.isInputText = true;
        } else if ("state".equals(tagName)) {
          this.isstate = true;
        } else if ("datapath".equals(tagName)) {
          this.isdatapath = true;
        } else if ("dataset".equals(tagName)) {
          this.isdataset = true;
        }
      }

      this.anonymous = (! publish);
      // Compute superclass/interstitials, actual kind/tag, debug annotation
      if (definition != null) {
        String mixinSpec = definition.getAttributeValue("with");
        if (mixinSpec != null) {
          definition.removeAttribute("with");
          mixinTagNames = mixinSpec.trim().split("\\s*,\\s*");
        }
        String interfaceSpec = definition.getAttributeValue("implements");
        if (interfaceSpec != null) {
          definition.removeAttribute("implements");
          interfaceTagNames = interfaceSpec.trim().split("\\s*,\\s*");
        }
        boolean isclassdef = schema.isClassDefinition(definition);
        // Boostrap schema
        if (isclassdef) {
          mCanContainTags.addAll(ViewSchema.mClassTags);
        }
        if (isclassdef || "anonymous".equals(tagName)) {
          superTagName = definition.getAttributeValue("extends");
          if (superTagName == null) {
            superTagName = DEFAULT_SUPERCLASS_NAME;
          }
          // Save away the original extends value
          debugExtends = superTagName;
          debugWith = "";
          debugImplements = "";
          if (interfaceTagNames != null) {
            interfaceModels = new ClassModel[interfaceTagNames.length];
            for (int i = interfaceTagNames.length - 1; i >= 0; i--) {
              String interfaceTagName = interfaceTagNames[i];
              debugImplements += (debugImplements.length() > 0 ? ", " : "") + interfaceTagName;
              ClassModel interfaceModel =  schema.getClassModelUnresolved(ViewSchema.KIND_INTERFACE, interfaceTagName);
              if (interfaceModel == null) {
                throw new CompilationError(
                    "Undefined interface " + interfaceTagName + " for class " + tagName,
                    definition);
              }
              interfaceModels[i] = interfaceModel;
            }
          }
          if (mixinTagNames != null) {
            for (int i = mixinTagNames.length - 1; i >= 0; i--) {
              String mixinTagName = mixinTagNames[i];
              ClassModel mixinModel =  schema.getClassModelUnresolved(ViewSchema.KIND_MIXIN, mixinTagName);
              if (mixinModel == null) {
                throw new CompilationError(
                    "Undefined mixin " + mixinTagName + " for class " + tagName,
                    definition);
              }
              String interstitialName = env.interstitialName(mixinTagName, superTagName);
              debugWith = mixinTagName + (debugWith.length() > 0 ? ", " : "") + debugWith;
              // Avoid adding the same interstitial to the schema twice - LPP-8234
              if (schema.getClassModelUnresolved(ViewSchema.KIND_CLASS, interstitialName) == null) {
                // See LPP-8828:  LZX mixins are implemented not as
                // lzs mixins but by expanding into interstitial LZX
                // <class>es, so that the expected <node> semantics
                // with regard to merging/overriding children and
                // attributes occur.
                //
                // These interstitial <class>es will not be published
                // as tags, although they are inserted into the schema
                // for the benefit of the class and node compilers.
                //
                // We duplicate the mixin definition, but turn it into
                // a class definition, inheriting from the previous
                // superTagName and implementing the mixin
                Element interstitial = (Element)mixinModel.definition.clone();
                interstitial.setName(ViewSchema.KIND_CLASS);
                interstitial.setAttribute("name", interstitialName);
                interstitial.setAttribute("extends", superTagName);
                interstitial.setAttribute("implements", mixinTagName);

                // Add it to the schema, but don't define a tag
                schema.addClassModel(interstitial, ViewSchema.KIND_CLASS, interstitialName, null, env, false);
                // Give it a mnemonic debugName
                ClassModel im = schema.getClassModelUnresolved(ViewSchema.KIND_CLASS, interstitialName);
                // We'd like to be able to share interstitial chains
                // across libraries, but at present, we can't tell
                // whether an interstitial chain will be emitted in a
                // library or not.  For now, we have to make our
                // interstitials be emitted with us (by making their
                // model status agree with ours, rather than that of
                // the mixin they are derived from).
                if (! modelOnly) { im.modelOnly = false; }
                im.debugExtends = debugExtends;
                im.debugWith = debugWith;
                // Bad modularity here.  We need the real mixin this
                // implements, for LZO interfaces, not the
                // user-specified implements.
                im.debugImplements = mixinTagName;
              }

              // Update the superTagName
              superTagName = interstitialName;
            }
            // Now adjust this DOM element to refer to the
            // interstitial superclass
            definition.setAttribute("extends", superTagName);
          }
          if ("anonymous".equals(tagName)) {
            ClassModel sm = schema.getClassModelUnresolved(ViewSchema.KIND_CLASS, superTagName);
            debugExtends = sm.debugExtends;
            debugWith = sm.debugWith;
            tagName = null;
          }
        } else {
          // Instance classes are not published
          assert (! publish);
          assert tagName.equals(definition.getName());
          assert (! "anonymous".equals(tagName));
          kind = ViewSchema.KIND_INSTANCE_CLASS;
          ClassModel sm = schema.getClassModelUnresolved(kind, tagName);
          debugExtends = tagName;
          debugWith = sm.debugWith;
          // The superclass of an instance class is the tag that
          // creates the instance
          superTagName = tagName;
          // No tag for instance classes
          tagName = null;
        }
      } else {
        // The root class
        resolved = true;
      }
      // NOTE: [2009-01-31 ptw] If the class is in an import, or
      // external to the library you are linking, modelOnly is set to true to prevent class
      // models that were created to compute the schema and
      // inheritance from being emitted.  Classes that are actually
      // in the library or application being compiled will be
      // emitted because the are compiled with the `force` option,
      // which overrides `modelOnly`.  See ClassCompiler.compile
      this.modelOnly = env.getBooleanProperty(CompilationEnvironment._EXTERNAL_LIBRARY);
      this.definition = definition;
      this.schema = schema;
      if (tagName != null) {
        if (className != null) {
          this.className = className;
        }
        else {
          this.className = LZXTag2JSClass(tagName, kind);
        }
      } else {
        this.className = LZXTag2JSClass("_" + env.methodNameGenerator.next().substring(1), kind);
      }
      // Install the computed kind and tagName
      this.kind = kind.intern();
      this.tagName = tagName != null ? tagName.intern() : null;
    }

  String sortKey() {
    if (sortkey != null) { return sortkey; }
    sortkey = (tagName != null) ? tagName : "anonymous";
    if (superTagName != null) {
      ClassModel sm = schema.getClassModelUnresolved(ViewSchema.KIND_CLASS, superTagName);
      if (sm != null) {
        sortkey = sm.sortKey() + "." + sortkey;
      }
    }
    return sortkey;
  }


    public int compareTo(ClassModel other) throws ClassCastException {
      if (this == other) { return 0; }
      String sk = sortKey();
      String osk = other.sortKey();
      if (sk.startsWith(osk)) {
        // O is in my superclass chain
        return +1;
      } else if (osk.startsWith(sk)) {
        // I am in O's superclass chain
        return -1;
      } else {
        // Unordered, but not equal!
        return sk.compareTo(osk);
      }
    }

    /**
     * Check that the 'allocation' attribute of a tag is either "instance" or "class".
     * The return value defaults to "instance".
     * @param element a method or attribute element
     * @return an AttributeSpec allocation type (either 'instance' or 'class')
     */
    String getAllocation(Element element) {
        // allocation type defaults to 'instance'
        String allocation = element.getAttributeValue("allocation");
        if (allocation == null) {
            allocation = NodeModel.ALLOCATION_INSTANCE;
        } else if (!(allocation.equals(NodeModel.ALLOCATION_INSTANCE) ||
                     allocation.equals(NodeModel.ALLOCATION_CLASS))) {
          throw new CompilationError(
              "the value of the 'allocation' attribute must be either 'instance', or 'class'" , element);
        }
        return allocation;

    }

    public ClassModel resolve(CompilationEnvironment env) {
        if (resolved) { return this; }

        List<String> csstags = new ArrayList<String>();
        if ((! anonymous) && (tagName != null)) { csstags.add(ScriptCompiler.quote(tagName)); }
        // NOTE:  The mixin tag names will be in the superclass,
        // because we have been linearized by now
        if (interfaceTagNames != null) {
          for (int i = 0, l = interfaceTagNames.length; i < l; i++) {
            csstags.add(ScriptCompiler.quote(interfaceTagNames[i]));
          }
        }

        // Find superclass and mixins
        if (superTagName != null) {
            superModel = schema.getClassModel(ViewSchema.KIND_CLASS, superTagName);
            if (superModel == null) {
                throw new CompilationError(
                    /* (non-Javadoc)
                     * @i18n.test
                     * @org-mes="undefined superclass " + p[0] + " for class " + p[1]
                     */
                    org.openlaszlo.i18n.LaszloMessages.getMessage(
                      ViewSchema.class.getName(),"051018-417", new Object[] {superTagName, tagName}),
                                           definition);
            }
            isInputText |= superModel.isInputText;
            supportsTextAttribute |= superModel.supportsTextAttribute;
            isnode |= superModel.isnode;
            isstate |= superModel.isstate;
            isdatapath |= superModel.isdatapath;
            isdataset |= superModel.isdataset;

            // merge in superclass requiredAttributes list to make scanning the set more efficient
            requiredAttributes.addAll(superModel.requiredAttributes);
            // ditto for contains and forbidden tags
            mCanContainTags.addAll(superModel.mCanContainTags);
            mForbiddenTags.addAll(superModel.mForbiddenTags);

            if (superModel.CSSTagSelectors != null) { csstags.addAll(superModel.CSSTagSelectors); }
        }

        CSSTagSelectors = csstags;

        // Process the definition (Note that the root class does not
        // have a definition).
        if ((definition != null) && (tagName != null)) {
          // Loop over containsElements tags, adding to containment table in classmodel
          for (Iterator<?> iterator = definition.getChildren().iterator(); iterator.hasNext(); ) {
            Element child = (Element) iterator.next();
            if (child.getName().equals("containsElements")) {
              // look for <element>tagname</element>
              Iterator<?> iter1 = child.getChildren().iterator();
              while (iter1.hasNext()) {
                Element etag = (Element) iter1.next();
                if (etag.getName().equals("element")) {
                  String tagname = etag.getText();
                  addContainsElement(tagname);
                } else {
                  throw new CompilationError(
                    "containsElement block must only contain <element> tags", etag);
                }
              }
            } else if (child.getName().equals("forbiddenElements")) {
              // look for <element>tagname</element>
              Iterator<?> iter1 = child.getChildren().iterator();
              while (iter1.hasNext()) {
                Element etag = (Element) iter1.next();
                if (etag.getName().equals("element")) {
                  String tagname = etag.getText();
                  addForbiddenElement(tagname);
                } else {
                  throw new CompilationError(
                    "containsElement block must only contain <element> tags", etag);
                }
              }
            }
          }

          // Collect up the attribute defs, if any, of this class
          List<AttributeSpec> attributeDefs = new ArrayList<AttributeSpec>();
          for (Iterator<?> iterator = definition.getContent().iterator(); iterator.hasNext(); ) {
            Object o = iterator.next();
            if (o instanceof Element) {
              Element child = (Element) o;
              if (child.getName().equals("method")) {
                String attrName = child.getAttributeValue("name");
                String attrEvent = child.getAttributeValue("event");
                if (attrEvent == null) {
                  if (! isbuiltin) {
                    try {
                      attrName = ElementCompiler.requireIdentifierAttributeValue(child, "name");
                    } catch (MissingAttributeException e) {
                      throw new CompilationError(
                        "'name' is a required attribute of <" + child.getName() + "> and must be a valid identifier", child);
                    }
                  }
                  String allocation = getAllocation(child);
                  ViewSchema.Type attrType = ViewSchema.METHOD_TYPE;
                  AttributeSpec attrSpec = 
                    new AttributeSpec(attrName, attrType, null, null, child);
                  attrSpec.isfinal = "true".equals(child.getAttributeValue("final"));
                  attrSpec.allocation = allocation;
                  attributeDefs.add(attrSpec);
                }
              } else if (child.getName().equals("setter")) {
                String attrName = child.getAttributeValue("name");
                if (! isbuiltin) {
                  try {
                    attrName = ElementCompiler.requireIdentifierAttributeValue(child, "name");
                  } catch (MissingAttributeException e) {
                    throw new CompilationError(
                      "'name' is a required attribute of <" + child.getName() + "> and must be a valid identifier", child);
                  }
                }
                // Setter is shorthand for a specially-named method
                attrName = "$lzc$set_" + attrName;
                String allocation = getAllocation(child);
                ViewSchema.Type attrType = ViewSchema.METHOD_TYPE;
                AttributeSpec attrSpec =
                  new AttributeSpec(attrName, attrType, null, null, child);
                attrSpec.allocation = allocation;
                attributeDefs.add(attrSpec);
              } else if (child.getName().equals("attribute")) {
                // Is this an element named ATTRIBUTE which is a
                // direct child of this CLASS or INTERFACE tag?

                String attrName = child.getAttributeValue("name");
                if (! isbuiltin) {
                  try {
                    attrName = ElementCompiler.requireIdentifierAttributeValue(child, "name");
                  } catch (MissingAttributeException e) {
                    throw new CompilationError(
                      /* (non-Javadoc)
                       * @i18n.test
                       * @org-mes="'name' is a required attribute of <" + p[0] + "> and must be a valid identifier"
                       */
                      org.openlaszlo.i18n.LaszloMessages.getMessage(
                        ClassCompiler.class.getName(),"051018-131", new Object[] {child.getName()})
                      , child);
                  }
                }

                String attrTypeName = child.getAttributeValue("type");
                String attrDefault = child.getAttributeValue("value");
                String attrSetter = child.getAttributeValue("setter");
                String attrRequired = child.getAttributeValue("required");
                String allocation = getAllocation(child);

                if (attrDefault != null && attrRequired != null &&
                    attrRequired.equals("true") && !attrDefault.equals("null")) {
                  env.warn("An attribute cannot both be declared required and also have a non-null default value", child);
                }

                ViewSchema.Type attrType = null;
                if (attrTypeName == null) {
                  if (NodeModel.ALLOCATION_INSTANCE.equals(allocation)) {
                    // Check if this attribute exists in ancestor classes,
                    // and if so, default to that type.
                    AttributeSpec attrSpec = superModel.getAttribute(attrName, allocation);
                    if (attrSpec != null) {
                      attrType = attrSpec.type;
                    }
                  }
                  if (attrType == null) {
                    // The default attribute type
                    attrType = ViewSchema.EXPRESSION_TYPE;
                  }
                } else {
                  attrType = schema.getTypeForName(attrTypeName, child);
                }

                if (attrType == null) {
                  throw new CompilationError(
                    /* (non-Javadoc)
                     * @i18n.test
                     * @org-mes="In class " + p[0] + " type '" + p[1] + "', declared for attribute '" + p[2] + "' is not a known data type."
                     */
                    org.openlaszlo.i18n.LaszloMessages.getMessage(
                      ClassCompiler.class.getName(),"051018-160", new Object[] {tagName, attrTypeName, attrName})
                    , definition);
                }

                AttributeSpec attrSpec =
                  new AttributeSpec(attrName, attrType, attrDefault,
                                    attrSetter, "true".equals(attrRequired), child);
                attrSpec.allocation = allocation;
                attrSpec.style = child.getAttributeValue("style");
                attrSpec.inherit = child.getAttributeValue("inherit");
                attrSpec.expander = child.getAttributeValue("expander");
                attrSpec.isfinal = "true".equals(child.getAttributeValue("final"));
                if (attrName.equals("text") && attrTypeName != null) {
                  if ("text".equals(attrTypeName))
                    attrSpec.contentType = AttributeSpec.TEXT_CONTENT;
                  else if ("html".equals(attrTypeName))
                    attrSpec.contentType = AttributeSpec.HTML_CONTENT;
                }
                attributeDefs.add(attrSpec);
              } else if (child.getName().equals("event")) {
                String attrName = child.getAttributeValue("name");
                if (! isbuiltin) {
                  try {
                    attrName = ElementCompiler.requireIdentifierAttributeValue(child, "name");
                  } catch (MissingAttributeException e) {
                    throw new CompilationError(
                      "'name' is a required attribute of <" + child.getName() + "> and must be a valid identifier", child);
                  }
                }

                ViewSchema.Type attrType = ViewSchema.EVENT_HANDLER_TYPE;
                AttributeSpec attrSpec =
                  new AttributeSpec(attrName, attrType, null, null, child);
                attributeDefs.add(attrSpec);
              } else if (child.getName().equals("doc")) {
                // Ignore documentation nodes
              } else {
                // We'd like to warn about unknown attributes, but
                // we can't tell if a child is a view here...
              }
            }
          }
          // Add in the attribute declarations.
          addAttributeDefs(attributeDefs, env);
        }
        resolved = true;
        return this;
    }

    /**
     * Add this list of attribute name/type info to the in-core model of the class definitions.
     *
     * @param sourceElement the user's LZX source file element that holds class LZX definition
     * @param classname the class we are defining
     * @param attributeDefs list of AttributeSpec attribute info to add to the Schema
     *
     */
    void addAttributeDefs (List<AttributeSpec> attributeDefs, CompilationEnvironment env)
    {
        if (!attributeDefs.isEmpty()) {
            for (AttributeSpec attr : attributeDefs) {
                // If this attribute does not already occur someplace
                // in an ancestor, then let's add it to the schema.
                //
                // While we're here, we need to check that we aren't
                // redefining an attribute of a parent class with a
                // different type.
                if (superTagName != null && NodeModel.ALLOCATION_INSTANCE.equals(attr.allocation)) {
                  // We're going to warn if the types mismatch.
                  AttributeSpec parentAttrSpec = superModel.getAttribute(attr.name, NodeModel.ALLOCATION_INSTANCE);
                  if (parentAttrSpec != null) {
                    ViewSchema.Type parentType = parentAttrSpec.type;
                    if (parentType != attr.type) {
                      env.warn(/* (non-Javadoc)
                                * @i18n.test
                                * @org-mes="In class '" + p[0] + "' attribute '" + p[1] + "' with type '" + p[2] + "' is overriding superclass attribute with same name but different type: " + p[3]
                                */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                          ViewSchema.class.getName(),"051018-364", new Object[] {tagName, attr.name, attr.type.toString(), parentType.toString()}),
                        definition);
                    }
                  }
                }
                if (attr.type == ViewSchema.METHOD_TYPE) {
                  checkMethodDeclaration(attr.name, attr, env);
                }
                // Update the in-memory attribute type table
                setAttribute(attr.name, attr);
            }
        }
    }

    /** Checks to do when declaring a method on a class:
     * <ol>
     * <li>Does the class exist?</li>
     * <li>Is this a duplicate of another method declaration on this class?</li>
     * <li>Does the superclass allow overriding of this method?</li>
     * </ol>
     */
  void checkMethodDeclaration (String methodName, AttributeSpec attrspec, CompilationEnvironment env) {
    String allocation = attrspec.allocation;
    // check for a duplicate local definition, setAttribute() does the same
    // test again, but it throws additionally a CompilationError
    AttributeSpec localAttr = getLocalAttribute(methodName, allocation);
    if ( localAttr != null) {
      if (localAttr.type == ViewSchema.METHOD_TYPE) {
        env.warn(
          "Method "+this+"/"+methodName+": duplicate definition",
          definition);
      } else {
        env.warn(
          "Method "+this+"/"+methodName+
          ": conflicts with attribute named "+methodName+" of type "+localAttr.type,
          definition);
      }
    }

    // check the inheritance whether you can override this method, only
    // applies to methods with allocation='instance', because class-
    // allocated methods aren't inherited
    if (NodeModel.ALLOCATION_INSTANCE.equals(allocation) && (superModel != null)) {
      AttributeSpec supermethod = superModel.getAttribute(methodName, allocation);
      if (supermethod != null && supermethod.isfinal) {
        env.warn("Method "+this+"/"+methodName+": attempt to override final method", definition);
      }
    }
  }

    /** Checks to do when declaring a method on an instance:
     * <ol>
     * <li>Does the class exist?</li>
     * <li>Is there an attribute with the same name, but a different type on a superclass?</li>
     * <li>Does the superclass allow overriding of this method?</li>
     * </ol>
     */
  void checkInstanceMethodDeclaration (String methodName, CompilationEnvironment env) {
        // Search on the inheritance chain for an attribute with the same name.
        // If such an attribute exists and it's not a method or it's a final method,
        // emit a warning.
        AttributeSpec supermethod = getAttribute(methodName, NodeModel.ALLOCATION_INSTANCE);
        if (supermethod != null) {
            if (supermethod.type != ViewSchema.METHOD_TYPE) {
                env.warn(
                  "Method "+this+"/"+methodName+
                  ": conflicts with attribute named "+methodName+" of type "+supermethod.type,
                  definition);
            } else if (supermethod.isfinal) {
                env.warn(
                  "Method "+this+"/"+methodName+": attempt to override final method",
                  definition);
            }
        }
    }

  public String toLZX() {
    return toLZX("");
  }

  public String toLZX(String indent) {
    String lzx = "";
    // Mixins need to retain their implementation, so they can be
    // cloned when included.
    // TODO: [2010-08-03 ptw] Figure out some way to obfuscate at
    // least the bodies of the mixin's methods
    if (ViewSchema.KIND_MIXIN.equals(kind)) {
      Format format = Format.getCompactFormat();
      // NOTE: [2010-08-10 ptw] (LPP-9288) Method bodies need to
      // preserve whitespace because they may contain single-line
      // comments
      format.setTextMode(Format.TextMode.TRIM_FULL_WHITE);
      XMLOutputter out = new XMLOutputter(format);
      lzx += indent + out.outputString(this.definition);
      lzx += "\n";
      // We'll also output an interface of the same name.  This is our
      // way of telling the linker that it does not need to compile
      // the mixin to an interface (because the interface will already
      // be compiled into the lzo).
      assert declarationEmitted;
    }
    // NOTE: This will output references to "anonymous" interstitials,
    // which is what is necessary to be able to link with LZO's.
    lzx += indent + "<interface name='" + tagName + "'"
      + ((superModel != null)
         ? (" extends='" + superModel.tagName + "'")
         : "")
      + ((debugImplements.length() > 0)
         ? (" implements='" + debugImplements + "'")
         : "")
      + ">";

    for (AttributeSpec spec : attributeSpecs.values()) {
      String specLZX = spec.toLZX(indent + "  ", superModel);
      if (specLZX != null) {
        lzx += "\n";
        lzx += specLZX;
      }
    }

    // We need to know at least whether or not we have subviews, so
    // that the `inheritsChildren` computation is accurate for
    // emitClassDeclaration
    for (NodeModel child : nodeModel.getChildren()) {
      String childLZX = child.toLZX(indent + "  ");
      if (childLZX != null) {
        lzx += "\n";
        lzx += childLZX;
      }
    }

    lzx += "\n" + indent + "</interface>";
    return lzx;
  }

  // Map of LFC tag names
  static HashMap<String, String> LFCTag2JSClass = new HashMap<String, String>();
  static {
    LFCTag2JSClass.put("node", "LzNode");
    LFCTag2JSClass.put("view", "LzView");
    LFCTag2JSClass.put("text", "LzText");
    //    LFCTag2JSClass.put("tlftext", "LzTLFText");
    //    LFCTag2JSClass.put("tlfinputtext", "LzTLFInputText");
    LFCTag2JSClass.put("inputtext", "LzInputText");
    LFCTag2JSClass.put("canvas", "LzCanvas");
    LFCTag2JSClass.put("script", "LzScript");
    LFCTag2JSClass.put("animatorgroup", "LzAnimatorGroup");
    LFCTag2JSClass.put("animator", "LzAnimator");
    LFCTag2JSClass.put("layout", "LzLayout");
    LFCTag2JSClass.put("state", "LzState");
    LFCTag2JSClass.put("datapointer", "LzDatapointer");
    LFCTag2JSClass.put("dataprovider", "LzDataProvider");
    LFCTag2JSClass.put("datapath", "LzDatapath");
    LFCTag2JSClass.put("dataset", "LzDataset");
    LFCTag2JSClass.put("datasource", "LzDatasource");
    LFCTag2JSClass.put("lzhttpdataprovider", "LzHTTPDataProvider");
    LFCTag2JSClass.put("import", "LzLibrary");
    LFCTag2JSClass.put("contextmenu", "LzContextMenu");
    LFCTag2JSClass.put("contextmenuitem", "LzContextMenuItem");
    LFCTag2JSClass.put("DataText", "LzDataText");
    LFCTag2JSClass.put("DataElement", "LzDataElement");
    LFCTag2JSClass.put("DataNode", "LzDataNode");
    LFCTag2JSClass.put("DataProvider", "LzDataProvider");
    LFCTag2JSClass.put("HTTPDataProvider", "LzHTTPDataProvider");
    LFCTag2JSClass.put("HTTPDataRequest", "LzHTTPDataRequest");
    LFCTag2JSClass.put("ReplicationManager", "LzReplicationManager");
    LFCTag2JSClass.put("LazyReplicationManager", "LzLazyReplicationManager");
    LFCTag2JSClass.put("Param", "LzParam");
    LFCTag2JSClass.put("DataNodeMixin", "LzDataNodeMixin");
    LFCTag2JSClass.put("DataElementMixin", "LzDataElementMixin");
    LFCTag2JSClass.put("CSSStyleRule", "LzCSSStyleRule");
    LFCTag2JSClass.put("CSSStyle", "LzCSSStyleClass");
    LFCTag2JSClass.put("CSSStyleDeclaration", "LzCSSStyleDeclaration");
    LFCTag2JSClass.put("CSSStyleSheet", "LzCSSStyleSheet");
  }

  public static String LZXTag2JSClass(String s) {
    return LZXTag2JSClass(s, ViewSchema.KIND_CLASS);
  }

  public static String LZXTag2JSClass(String s, String k) {
    String lzcPackagePrefix = "$lzc$";
    if (ViewSchema.kindIsTag(k)) {
      if (LFCTag2JSClass.containsKey(s)) {
        return LFCTag2JSClass.get(s);
      }
      lzcPackagePrefix += "class_";
    } else if (ViewSchema.kindIsType(k)) {
      lzcPackagePrefix += "type_";
    } else {
      assert false : "Unknown kind " + k;
    }
    return lzcPackagePrefix + s;
  }


  protected boolean declarationEmitted = false;

  /**
   * Emits a class model as a JS2 class declaration.  This is used
   * both by the class compiler and the instance compiler (when an
   * instance has methods, either explicit or implicit).
   */
  void emitClassDeclaration(CompilationEnvironment env, boolean modelOnly) {
    // System.err.println("Emit: " + this.toString()
    //                    + ((nodeModel != null && nodeModel.parentClassModel != null) ? (" Parent: " + nodeModel.parentClassModel.tagName) : ""));
    if (isbuiltin && "Object".equals(tagName)) {
      return;
    }

    // Last chance for resolution
    boolean prev = env.compilingExternalLibrary;
    try {
      env.compilingExternalLibrary = modelOnly;

      resolve(env);
      //    declarationEmitted = (! modelOnly);
      declarationEmitted = true;
      // Should the package prefix be in the model?  Should the
      // model store class and tagname separately?
      if (!isbuiltin) {
        assert superModel != null : "Unknown superclass " + superTagName + " for " + this;
      }
      if (superModel != null) {
        // Allow forward references.
        // System.err.println("  super: " + superModel.kind + " " + superTagName + " emitted: " + superModel.declarationEmitted);
        superModel.compile(env);
      }
      String interfaceClassNames[] = null;
      if (interfaceModels != null) {
        interfaceClassNames = new String[interfaceModels.length];
        for (int i = interfaceModels.length - 1; i >= 0; i--) {
          ClassModel interfaceModel =  interfaceModels[i];
          interfaceClassNames[i] = interfaceModel.className;
          // System.err.println("  interface: " + interfaceModel.kind + " " + interfaceModel.tagName + " emitted: " + interfaceModel.declarationEmitted);
          interfaceModel.compile(env);
        }
      }
      // If we are an interface, and not builtin, we're done now
      if ((! isbuiltin) && ViewSchema.KIND_INTERFACE.equals(kind)) { return; }

      String superClassName = null;
      if (superModel != null && !"$lzc$class_Object".equals(superModel.className)) {
        superClassName = superModel.className;
      }
      // className will be a global
      if (! isbuiltin) {
        env.addId(className, definition);
      }

      getNodeModel(env);
      if (nodeModel.isclassdef) {
        nodeModel.assignClassRoot(0);
      }

      String classBody = "";

      if (! isbuiltin) {
        // LZX classes are not allowed to define their constructor, but
        // since LZX classes have required constructor arguments, we need
        // to define a constructor that passes those arguments to the
        // superclass.
        String body = "";
        body += "super(parent, attrs, children, async);\n";
        nodeModel.setAttribute(
            className,
            new Method(
                className,
                // All nodes get these args when constructed
                "parent:LzNode? = null, attrs:Object? = null, children:Array? = null, async:Boolean = false",
                body));

        // Build the class body
        String block = nodeModel.passthroughBlock;
        if (block != null) {
          block = "#passthrough (toplevel:true) {" + block + "}#\n";
          if (nodeModel.passthroughBlockWhen != null) {
            block = "if (" + nodeModel.passthroughBlockWhen + ") {\n"+block+"}\n";
          }
          classBody += block;
        }

        // Various serializers depend on getting the tagname from the
        // class constructor, so we emit this for all published classes
        if (! anonymous) {
          assert tagName != null;
          // Set the tag name
          nodeModel.setClassAttribute("tagname",  ScriptCompiler.quote(tagName));
        } else {
          nodeModel.setClassAttribute(Function.FUNCTION_NAME, ScriptCompiler.quote(this.toString()));
        }

        // TODO: [2008-06-02 ptw] This should only be done for LZX classes that are
        // subclasses of LzNode
        //
        // Before you output this, see if it is necessary:  will this node
        // end up with children at all?
        boolean hasChildren = (! nodeModel.getChildren().isEmpty());
        boolean inheritsChildren = inheritsChildren();
        if (hasChildren || inheritsChildren) {
          String children = ScriptCompiler.objectAsJavascript(nodeModel.childrenMaps(env));
          if (inheritsChildren) {
            // NOTE: [2009-04-01 ptw] We don't compute the merged children
            // at compile time, because we may not know them (superclass
            // may be loaded from a library).  It might be possible to
            // optimize this when we know the superclass.
            nodeModel.setClassAttribute("children", "LzNode.mergeChildren(" + children + ", " + superClassName + "['children'])");
          } else {
            nodeModel.setClassAttribute("children", children);
          }
        }
      }

      // Declare all instance vars and methods, save initialization
      // in <class>.attributes
      Map<String, Object> attrs = nodeModel.getAttrs();
      Map<String, Object> decls = new LinkedHashMap<String, Object>();
      Map<String, Object> inits = new LinkedHashMap<String, Object>();
      boolean needsCSE = false;
      for (Map.Entry<String, Object> entry : attrs.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        boolean redeclared = (superModel.getAttribute(key, NodeModel.ALLOCATION_INSTANCE) != null);
        if ((value instanceof NodeModel.BindingExpr)) {
          // Bindings always have to be installed as an init
          if (! redeclared) {
            decls.put(key, null);
          }
          String expr = ((NodeModel.BindingExpr)value).getExpr();
          // Hand-rolled CSE
          if ("LzStyleConstraintExpr.StyleConstraintExpr".equals(expr)) {
            needsCSE = true;
            inits.put(key, "$lzc$sce");
          } else {
            inits.put(key, expr);
          }
        } else if (value instanceof Method &&
                   ((! isstate) ||
                    className.equals(key))) {
          // Methods are just decls.  Except in states, because they
          // have to be applied to the parent, except for the
          // constructor!
          decls.put(key, value);
        } else if (value != null) {
          // If there is a setter for this attribute, or this is a
          // state, or this is an Array or Map argument that needs
          // magic merging, the value has to be installed as an init,
          // otherwise it should be installed as a decl
          //
          // TODO: [2008-03-15 ptw] This won't work until we know (in
          // the classModel) the setters for all the superclasses
          // (built-in and in libraries), so we install as an init for
          // now and this is fixed up in LzNode by installing inits that
          // have no setters when the arguments are merged
          if (true) { // (! (value instanceof String))  || setters.containsKey(key) || isstate) {
            // If this is a re-declared attribute, we just init it,
            // don't re-declare it
            if (! redeclared) {
              decls.put(key, null);
            }
            inits.put(key, value);
          } else {
            if (! redeclared) {
              decls.put(key, value);
              // If there is a property that would have been shadowed,
              // you have to hide that from applyArgs, or you will get
              // clobbered!
              inits.put(key, "LzNode._ignoreAttribute");
            } else {
              inits.put(key, value);
            }
          }
        } else {
          // Just a declaration
          if (! redeclared) {
            decls.put(key, value);
          }
        }
      }
       // Add the attribute and CSS descriptors
    //  Map<String, Map<String, String>> attributeDescriptor = nodeModel.getAttributeDescriptor();
      Map<String, Object> attributeDescriptor = nodeModel.getClassAttributeDescriptor(this);
      if (attributeDescriptor != null) {
        inits.put("$attributeDescriptor", attributeDescriptor);
      }
      Map<String, Map<String, ? extends Object>> CSSDescriptor = nodeModel.getCSSDescriptor();
      if (CSSDescriptor != null) {
        inits.put("$CSSDescriptor", CSSDescriptor);
      }
      // Add the CSS tag selectors
      if (CSSTagSelectors != null) {
        nodeModel.setClassAttribute("__LZCSSTagSelectors", ScriptCompiler.objectAsJavascript(CSSTagSelectors));
      }

     // Create inits list, merged with superclass inits
      if (!isbuiltin) {
        nodeModel.setClassAttribute("attributes", "new LzInheritedHash(" + superClassName + ".attributes)");
        // NOTE: [2008-06-02 ptw] As an optimization, we don't do this if
        // this class has no inits of its own.  (Unlike LFC classes, an LZX
        // class should not be manipulating its attributes directly, so we
        // ought not to have to make the copy above, but that mysteriously
        // breaks things.)
        if (! inits.isEmpty()) {
          if (needsCSE) {
            // Hand-rolled CSE
            classBody += "((function ($lzc$sce, $lzc$attr) {\n"
              + "LzNode.mergeAttributes(" + ScriptCompiler.objectAsJavascript(inits) + ", $lzc$attr);\n"
              + " })(LzStyleConstraintExpr.StyleConstraintExpr, " + env.getGlobalPrefix() + className + ".attributes));\n";
          } else {
            classBody += "LzNode.mergeAttributes(" +
              ScriptCompiler.objectAsJavascript(inits) +
              ", " + env.getGlobalPrefix() + className + ".attributes);\n";
          }
        }
      }
      // Emit the class decl
      ScriptClass scriptClass =
        new ScriptClass(className,
                        superClassName,
                        interfaceClassNames,
                        decls,
                        nodeModel.getClassAttrs(),
                        classBody,
                        kind);

      String script = scriptClass.toString();
      if (isbuiltin) {
        script = "#pragma 'referenceClass=true'\n" + script + "\n#pragma 'referenceClass=false'\n";
      }
      
      // frank add to declare global attributes type 
      script =  getClassTypeDescription()+script;
      
      if ((! modelOnly) || isbuiltin) {
        env.compileScript(script, definition);
        if ((! anonymous) && (tagName != null) && (! isbuiltin)) {
          env.addTag(tagName, className);
        }
      } else {
        env.addClassModel(script, className, definition);
      }
    } finally {
      env.compilingExternalLibrary = prev;
    }
  }
  
  
  /**
   *  Output a class ClassAttributeTypes.  
   *  passthrough if classname is preloadresource
   */
  
 private String getClassTypeDescription(){
      StringBuffer sctript = new StringBuffer();
      sctript.append("lz.ClassAttributeTypes[\"");
      String cname = "";
      
      if (this.tagName!=null){
          cname = this.tagName;
      } else {
          cname = this.className;
      }
      
      if (cname.equals("preloadresource")) return "";
      
      sctript.append(cname);
      sctript.append("\"]=");
      sctript.append(nodeModel.getClassTypeDefine());
      sctript.append(";\n");
      
      return sctript.toString();
  }


  /**
   * Output a class.  Called after schema processing, but may be
   * compiled out of order, so that forward references to classes work
   */
  public void compile(CompilationEnvironment env) {
    this.compile(env, false);
  }

  /**
   * This may be called to emit a class defintion, or to resolve
   * either a forward or external (to this compilation unit)
   * reference.  In all cases, we create the node model, which is used
   * for calculating other compile-time optimizations.  For external
   * references, we do not generate any code.
   */
  protected void compile(CompilationEnvironment env, boolean force) {
    // We compile a class declaration just like a view, and then
    // add attribute declarations and perhaps some other stuff that
    // the runtime wants.

    boolean linking = env.linking;
    assert (force ? (! modelOnly) : true) : "Forcing compile of model-only class " + tagName;

    if (!isCompiled()) {
      if (isbuiltin) {
        // emit 'reference' classes, but never when creating for lzo
        if ((! modelOnly) && linking) {
          emitClassDeclaration(env, modelOnly);
        }
      }
      else {
        // some backends need the class model in order to create a native lzo library
        if (force || (! linking) || (! modelOnly)) {
          emitClassDeclaration(env, modelOnly);
        }
      }
    }
  }

    /** Returns true if this is equal to or a subclass of
     * superclass. */
    boolean isSubclassOf(ClassModel superclass) {
        if (this == superclass) return true;
        if (this.superModel == null) return false;
        return this.superModel.isSubclassOf(superclass);
    }

    boolean hasNodeModel() {
        // Classes that have generated code will have a nodeModel
        return nodeModel != null;
    }

  NodeModel getNodeModel(CompilationEnvironment env) {
    if (nodeModel != null) { return nodeModel; }
    if (definition != null) {
      return nodeModel = NodeModel.elementAsModel(definition, schema, env);
    }
    return null;
  }

    void setNodeModel(NodeModel model) {
        this.nodeModel = model;
    }



  private boolean isCompiled() {
    // Classes that have been compiled
    return declarationEmitted;
  }

  public ClassModel getSuperclassModel() {
      return superModel;
  }

  // A class needs to merge its children if its superclass has
  // children, or its superclass is in a library (in which case, we
  // just don't know)
  boolean inheritsChildren() {
    // No LFC class has children
    if (superModel.isbuiltin) { return false; }
    // If we don't know, we have to assume true
    if (superModel.nodeModel == null) { return true; }
    // Otherwise ask them
    return ((! superModel.nodeModel.getChildren().isEmpty()) ||
            superModel.inheritsChildren());
  }

  private Map<String, Method> mergedMethods;

  // NOTE: [2009-03-31 ptw] This information is incomplete.  It only
  // knows the methods the tag compiler has added.  It does _not_ know
  // methods that may have come from a library (only the schema knows
  // those, assuming it has parsed the interface).
  Map<String, Method> getMergedMethods() {
    if (mergedMethods != null) { return mergedMethods; }
    if (nodeModel == null) { return mergedMethods = new LinkedHashMap<String, Method>(); }
    Map<String, Method> merged = mergedMethods = new LinkedHashMap<String, Method>(superModel.getMergedMethods());
    // Merge in the our methods
    for (Map.Entry<String, Object> entry : nodeModel.getAttrs().entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();
      if (value instanceof Method) {
        merged.put(key, (Method) value);
      }
    }
    return merged;
  }

    /** This is really the LZX tag name */
    public String getClassName () {
     return this.tagName;
    }
    
    /** This is really the LZX tag name */
    public String getSuperTagName() {
        if (superTagName != null) {
            return superTagName; 
        } else if (superModel == null) {
            return null;
        }  else {
            return superModel.tagName;
        }
    }
    
    public void setSuperTagName(String name) {
        this.superTagName = name;
    }
    
    void setSuperclassModel(ClassModel superclass) {
        this.superModel = superclass;
    }
    
    /** Return the AttributeSpec for the attribute named attrName.
        Only returns locally defined attribute, does not follow up the
        class hierarchy.
    */
    AttributeSpec getLocalAttribute(String attrName, String allocation) {
      if (allocation.equals(NodeModel.ALLOCATION_INSTANCE)) {
        return attributeSpecs.get(attrName);
      } else {
        return classAttributeSpecs.get(attrName);
      }
    }

  void setAttribute(String attrName, AttributeSpec attrspec) {
    String allocation = attrspec.allocation;
    Map<String, AttributeSpec> attrtable = allocation.equals(NodeModel.ALLOCATION_INSTANCE) ? attributeSpecs : classAttributeSpecs;
    AttributeSpec otherattr = attrtable.get(attrName);
    if (otherattr != null) {
      throw new CompilationError(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="duplicate definition of attribute " + p[0] + "." + p[1]
 */
        org.openlaszlo.i18n.LaszloMessages.getMessage(
          ViewSchema.class.getName(),"051018-178", new Object[] {tagName, attrName})
        , definition);
    }

    attrtable.put(attrName, attrspec);
    if (attrspec.required) {
      requiredAttributes.add(attrName);
    }
    if (attrName.equals("text")) {
      supportsTextAttribute = true;
    }
  }

    /** Return the AttributeSpec for the attribute named attrName.  If
     * the attribute is not defined on this class, look up the
     * superclass chain.
     */
  AttributeSpec getAttribute(String attrName, String allocation) {
        assert resolved : "Attempt to getAttribute on " + this + " before it is resolved";
        Map<String, AttributeSpec> attrtable = allocation.equals(NodeModel.ALLOCATION_INSTANCE) ? attributeSpecs : classAttributeSpecs;
        AttributeSpec attr = attrtable.get(attrName);
        if (attr != null) {
            return attr;
        } else if (superModel != null) {
          return(superModel.getAttribute(attrName, allocation));
        } else {
            return null;
        }
    }

    /** Find an attribute name which is similar to attrName, or return
     * null.  Used in compiler warnings. */
    AttributeSpec findSimilarAttribute(String attrName) {
        attrName = attrName.toLowerCase();
        for (AttributeSpec attr : attributeSpecs.values()) {
            String otherName = attr.name.toLowerCase();
            // TODO: Use something like EditDistance.java
            if ((attrName.indexOf(otherName) != -1) ||
                (otherName.indexOf(attrName) != -1)) {
                return attr;
            }
        }
        // if that didn't work, try the supeclass
        if (superModel == null) {
            return null;
        } else {
            return superModel.findSimilarAttribute(attrName);
        }
    }

    protected boolean descendantDefinesAttribute(NodeModel model, String name) {
        for (NodeModel child : model.getChildren()) {
            if (child.hasAttribute(name) || descendantDefinesAttribute(child, name))
                return true;
        }
        return false;
    }

    protected void setChildrenClassRootDepth(NodeModel model, int depth) {
        final String CLASSROOTDEPTH_ATTRIBUTE_NAME = "$classrootdepth";
        for (NodeModel child : model.getChildren()) {
            // If it has already been set, this child is the result of
            // a previous inline class expansion with a different
            // classroot.
            if (child.hasAttribute(CLASSROOTDEPTH_ATTRIBUTE_NAME))
                continue;
            child.setAttribute(CLASSROOTDEPTH_ATTRIBUTE_NAME, depth);
            int childDepth = depth;
            ClassModel childModel = child.parentClassModel;
            // If this is an undefined class, childModel will be null.
            // This is an error, and other code signals a compiler
            // warning. This test keeps it from resulting in a stack
            // trace too.
            if (childModel != null && childModel.isstate) { childDepth++; }
            setChildrenClassRootDepth(child, childDepth);
        }
    }


      /** Add an entry to the table of legally containable tags for a
     * given tag */
    public void addContainsElement (String childtag) {
      mCanContainTags.add(childtag.intern());
    }

    public Set<String> getContainsSet () {
      return mCanContainTags;
    }

      /** Add an entry to the table of forbidden tags for a
       * given tag */
    public void addForbiddenElement (String childtag) {
      mForbiddenTags.add(childtag.intern());
    }

    public Set<String> getForbiddenSet () {
      return mForbiddenTags;
    }

}

/**
 * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights
 * Reserved.  Use is subject to license terms.
 */
