/* *****************************************************************************
 * JS2Doc.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2006-2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.js2doc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**

 */
public class ConditionalState {

    public static enum Value {
        Indeterminate, False, True
    }
    public Value inferredValue;

    public HashSet<String> trueCases;
    public HashSet<String> falseCases;
    public Set<String> exclusiveOptions;
    public List<String> independentOptions;

    public ConditionalState(Value value, Set<String> exclusiveOptions, List<String> independentOptions) {
        this.inferredValue = value;
        this.trueCases = new HashSet<String>();
        this.falseCases = new HashSet<String>();
        this.exclusiveOptions = exclusiveOptions;
        this.independentOptions = independentOptions;
    }

    public ConditionalState(ConditionalState origState) {
        this(Value.False, origState.exclusiveOptions, origState.independentOptions);
        if (origState != null) {
            this.inferredValue = origState.inferredValue;
            this.trueCases.addAll(origState.trueCases);
            this.falseCases.addAll(origState.falseCases);
        }
    }

    public void addTrueCase(String option) {
        this.inferredValue = Value.Indeterminate;
        this.trueCases.add(option);
        this.falseCases.remove(option);
    }

    public void addFalseCase(String option) {
        this.inferredValue = Value.Indeterminate;
        this.trueCases.remove(option);
        this.falseCases.add(option);
    }

    public ConditionalState or(ConditionalState operand) {
        ConditionalState newState = new ConditionalState(Value.False, this.exclusiveOptions, this.independentOptions);
        switch (this.inferredValue) {
            case Indeterminate:
                switch (operand.inferredValue) {
                    case Indeterminate:
                        newState.inferredValue = Value.Indeterminate;
                        newState.trueCases.addAll(this.trueCases);
                        newState.trueCases.addAll(operand.trueCases);
                        newState.falseCases.addAll(this.falseCases);
                        newState.falseCases.addAll(operand.falseCases);
                        break;
                    case True:
                        newState.inferredValue = Value.True;
                        break;
                    case False:
                        newState.inferredValue = Value.Indeterminate;
                        newState.trueCases.addAll(this.trueCases);
                        newState.falseCases.addAll(this.falseCases);
                        break;
                }
                break;
            case True:
                newState.inferredValue = Value.True;
                break;
            case False:
                switch (operand.inferredValue) {
                    case Indeterminate:
                        newState.inferredValue = Value.Indeterminate;
                        newState.trueCases.addAll(operand.trueCases);
                        newState.falseCases.addAll(operand.falseCases);
                        break;
                    case True:
                        newState.inferredValue = Value.True;
                        break;
                    case False:
                        newState.inferredValue = Value.False;
                        break;
                }
                break;
        }
        return newState;
    }

    public ConditionalState and(ConditionalState operand) {
        ConditionalState newState = new ConditionalState(Value.False, this.exclusiveOptions, this.independentOptions);
        switch (this.inferredValue) {
            case Indeterminate:
                switch (operand.inferredValue) {
                    case Indeterminate:
                        newState.inferredValue = Value.Indeterminate;
                        newState.trueCases.addAll(this.trueCases);
                        newState.trueCases.addAll(operand.trueCases);
                        newState.falseCases.addAll(this.falseCases);
                        newState.falseCases.addAll(operand.falseCases);
                        // TO DO [jgrandy 11/07/2006]: handle logical inconsistency:
                        // trueCases intersect falseCases != {}
                        break;
                    case True:
                        newState.inferredValue = Value.Indeterminate;
                        newState.trueCases.addAll(this.trueCases);
                        newState.falseCases.addAll(this.falseCases);
                        break;
                    case False:
                        newState.inferredValue = Value.False;
                        break;
                }
                break;
            case True:
                switch (operand.inferredValue) {
                    case Indeterminate:
                        newState.inferredValue = Value.Indeterminate;
                        newState.trueCases.addAll(operand.trueCases);
                        newState.falseCases.addAll(operand.falseCases);
                        break;
                    case True:
                        newState.inferredValue = Value.True;
                        break;
                    case False:
                        newState.inferredValue = Value.False;
                        break;
                }
                break;
            case False:
                newState.inferredValue = Value.False;
                break;
        }
        return newState;
    }

    public ConditionalState not() {
        ConditionalState newState = new ConditionalState(Value.False, this.exclusiveOptions, this.independentOptions);
        switch (this.inferredValue) {
            case Indeterminate:
                newState.inferredValue = Value.Indeterminate;
                newState.trueCases.addAll(this.falseCases);
                newState.falseCases.addAll(this.trueCases);
                break;
            case True:
                newState.inferredValue = Value.False;
                break;
            case False:
                newState.inferredValue = Value.True;
                break;
        }
        return newState;
    }

    public void describeExclusiveConditions(Set<String> includeSet) {
        if (this.inferredValue == Value.Indeterminate) {
            if (this.trueCases.isEmpty()) {
                includeSet.addAll(this.exclusiveOptions);
                includeSet.removeAll(this.falseCases);
            } else {
                includeSet.addAll(this.exclusiveOptions);
                includeSet.retainAll(this.trueCases);
            }
        }
    }

    public void describeIndependentConditions(Set<String> includeSet, Set<String> excludeSet) {
        if (this.inferredValue == Value.Indeterminate) {
            includeSet.addAll(this.independentOptions);
            includeSet.retainAll(this.trueCases);
            excludeSet.addAll(this.independentOptions);
            excludeSet.retainAll(this.falseCases);
        }
    }

    public String toString () {
        if (this.inferredValue == Value.Indeterminate) {
            List<String> stateElements = new ArrayList<String>();

            Set<String> includes = new HashSet<String>();
            Set<String> excludes = new HashSet<String>();

            this.describeExclusiveConditions(includes);

            for (String include : includes)
                stateElements.add("+" + include);

            includes.clear();

            this.describeIndependentConditions(includes, excludes);

            for (String include : includes)
                stateElements.add("+" + include);

            for (String exclude : excludes)
                stateElements.add("-" + exclude);

            Collections.sort(stateElements);

            String s = "";
            for (String stateElement : stateElements) {
                s += stateElement;
            }

            return s;

        } else
            return (this.inferredValue == Value.True) ? "true" : "false";
    }
}
