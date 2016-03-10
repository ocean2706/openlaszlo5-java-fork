/***
 * StackModel.java
 *
 * Description: Model Flash engine stack for peep-hole optimizer
 */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

import org.openlaszlo.sc.Instructions.PUSHInstruction;


/***
 * StackModel models the stack in the peephole block.  It obeys
 * List operations and is manipulated as a List by all
 * instructions other than PUSH.  PUSH uses a special entry to annotate
 * the data it pushes with the push responsible for the data.  This
 * annotation is used by the push merging logic.  Any modification of
 * the data in the model by a normal instruction will clear the
 * associated push annotation
 */
@SuppressWarnings("serial")
public class StackModel extends ArrayList<Object> {
  // Why protected is stupid!
  private static class RemovableArrayList extends ArrayList<PUSHInstruction> {
    public RemovableArrayList(Collection<? extends PUSHInstruction> c) {
      super(c);
    }

    @Override
    public void removeRange(int fromIndex, int toIndex) {
      super.removeRange(fromIndex, toIndex);
    }
  }
  
  RemovableArrayList pushes;

  public StackModel(Collection<? extends Object> c) {
    // model of stack for computing arity of vararg instr's
    super(c);
    // push instruction that created corresponding data
    // Note that a push is not cleared until the stack is
    // popped below it
    this.pushes = new RemovableArrayList(Collections.<PUSHInstruction>nCopies(c.size() + 1, null));
  }

  public StackModel() {
    this(Collections.<Object>emptyList());
  }

  @Override
  public void add(int index, Object value) {
    if (index < 0) index += this.size();
    // don't clobber the push at i, to permit the prepend
    // optimization
    this.pushes.add(index + 1, null);
    super.add(index, value);
  }

  @Override
  public boolean add(Object value) {
    this.add(this.size(), value);
    return true;
  }

  @Override
  public boolean addAll(int index, Collection<? extends Object> c) {
    if (index < 0) index += this.size();
    // don't clobber the push at i, to permit the prepend
    // optimization
    int l = c.size();
    // Interior add should not clobber successor
    if (index != this.size()) l -= 1;
    this.pushes.addAll(index + 1, Collections.<PUSHInstruction>nCopies(l, null));
    return super.addAll(index, c);
  }

  @Override
  public boolean addAll(Collection<? extends Object> c) {
    return this.addAll(this.size(), c);
  }

  @Override
  public void clear() {
    this.pushes.clear();
    super.clear();
  }

  @Override
  public Object clone() {
    return new StackModel(this);
  }

  // contains just works

  @Override
  public void ensureCapacity(int minCapacity) {
    super.ensureCapacity(minCapacity);
    this.pushes.ensureCapacity(minCapacity + 1);
  }

  @Override
  public Object get(int index) {
    if (index < 0) index += this.size();
    return super.get(index);
  }

  // indexOf just works

  // isEmpty just works

  // lastIndexOf just works

  @Override
  public Object remove(int index) {
    if (index < 0) index += this.size();
    // don't clobber the push at i, to permit the prepend
    // optimization
    this.pushes.remove(index + 1);
    return super.remove(index);
  }

  @Override
  protected void removeRange(int fromIndex, int toIndex) {
    int l = this.size();
    if (fromIndex < 0) fromIndex += l;
    if (fromIndex > l) fromIndex = l;
    if (toIndex < 0) toIndex += l;
    if (toIndex > l) toIndex = l;
    // don't clobber the push at i, to permit the prepend
    // optimization
    this.pushes.removeRange(fromIndex + 1, toIndex + 1);
    super.removeRange(fromIndex, toIndex);
  }

  @Override
  public Object set(int index, Object value) {
    if (index < 0) index += this.size();
    // don't clobber the push at i, to permit the prepend
    // optimization
    this.pushes.set(index + 1, null);
    return super.set(index, value);
  }

  // toArray just works

  @Override
  public void trimToSize() {
    this.pushes.trimToSize();
    super.trimToSize();
  }

  public void  notePush(PUSHInstruction instr, List<? extends Object> data) {
    // System.out.println("notePush: " + instr + " || " + data);
    // Replace the last instruction noted with this instruction
    this.pushes.removeRange(this.size(), this.pushes.size());
    this.pushes.addAll(this.size(), Collections.nCopies(data.size() + 1, instr));
    super.addAll(data);
  }

  public PUSHInstruction lastPush() {
    if (this.pushes.size() > this.size()) {
      return (this.pushes.get(this.size()));
    } else {
      return null;
    }
  }

  // How many arguments from this instruction are still on the stack.
  // (instr must be the last instruction)
  public int pushDepth(PUSHInstruction instr) {
    // --- slow for deep models
//     return this.size() - this.pushes.indexOf(instr);
    int l = this.size();
    List<PUSHInstruction> p = this.pushes;
    // search from top of stack down
    for (int i = l - 1; i >= 0; i--) {
      if (p.get(i) != instr) {
        return l - (i + 1);
      }
    }
    return l;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    int i = -1;
    for (ListIterator<Object> li = super.listIterator(); li.hasNext(); ) {
      i = li.nextIndex();
      Object v = li.next();
      b.append(v != null ? v.toString() : "None");
      b.append("(");
      Object p = this.pushes.get(i);
      b.append(p != null ? "" + this.pushes.indexOf(p) : "None");
      b.append(")");
      if (li.hasNext()) b.append(", ");
    }
    for (i++; i < this.pushes.size(); i++) {
      if (i > 0) b.append(", ");
      b.append("None (");
      Object p = this.pushes.get(i);
      b.append(p != null ? "" + this.pushes.indexOf(p) : "None");
      b.append(")");
    }
    return b.toString();
  }
}

