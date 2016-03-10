/* -*- mode: Java; c-basic-offset: 2; -*- */

/***
 * InstructionCollector.java
 *
 * Description: Instruction buffer for the assembler
 *
 * Assembly consists of two passes, one to create the constant
 * pool, and another to assemble the instructions to byte sequences.
 * The InstructionBuffer holds instructions between these passes.
 *
 * The InstructionBuffer will be replaced by a FlowAnalyzer, which will
 * perform basic-block analysis.  That's the main justification for
 * keeping this class here, instead of adding a wrapper around
 * Assembler the way peep-hole optimization is done.
 *
 * During the first pass (as instructions are collected), the buffer
 * scans the instruction sequence for string arguments to PUSH
 * instructions.  It computes an occurrence count for each string, and
 * sorts the list of strings that occurred more than once by occurrence
 * count.  The first 64K of these are placed in the constant pool.
 * (The sort assures that PUSH can use one-byte indices for the most
 * frequently-referenced strings.)
 *
 * During the second pass, each instruction is passed to the assembler,
 * and the resulting bytecodes are appended to an accumulated bytecode
 * sequence.
 */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.openlaszlo.sc.Instructions.Instruction;
import org.openlaszlo.sc.Instructions.LABELInstruction;
import org.openlaszlo.sc.Instructions.PUSHInstruction;
import org.openlaszlo.sc.Instructions.TargetInstruction;

@SuppressWarnings("serial")
public class InstructionCollector extends ArrayList<Instruction> {
  public int nextLabel;
  public ConstantCollector constantCollector;
  public boolean constantsGenerated;

  public InstructionCollector(boolean disableConstantPool, boolean sortConstantPool) {
    super();
    this.nextLabel = 0;
    if (! disableConstantPool) {
      this.constantCollector = sortConstantPool ? new SortedConstantCollector() : new ConstantCollector();
    } else {
      this.constantCollector = null;
    }
    this.constantsGenerated = false;
  }

  public void emit(Instruction instr) {
    // Update the constant pool.
    if (constantCollector != null && instr instanceof PUSHInstruction) {
      PUSHInstruction push = (PUSHInstruction)instr;
      for (Object next : push.args) {
        if (next instanceof String) {
          constantCollector.add((String) next);
        }
      }
    }
    super.add(instr);
  }

  public void push(Object value) {
    emit(Instructions.PUSH.make(value));
  }

  public void push(int value) {
    push(Integer.valueOf(value));
  }

  public void push(boolean value) {
    push(Boolean.valueOf(value));
  }

  public void generateConstants() {
    // Only okay to call this once.
    assert (! constantsGenerated);
    ConstantCollector pool = constantCollector;
    // TODO: [2003-07-15 ptw] (krank) turn off the constant pool
    // for simplicity for now, but someday eliminate that
    if (pool != null  && (! pool.isEmpty())) {
      // TODO: [2003-03-06 ptw] Make CONSTANTS its own class?
      super.add(0, Instructions.CONSTANTS.make(pool.getConstants()));
      constantsGenerated = true;
    }
  }

  // Rename labels uniquely
  private Object uniqueLabel(Map<Object, String> labels, Object label)
  {
    String newLabel = labels.get(label);
    if (newLabel == null) {
      newLabel = newLabel();
      labels.put(label, newLabel);
    }
    return newLabel;
  }

  public void appendInstructions(List<Instruction> instrsList) {
    // TODO [2003-03-06 ptw] Why not relabel all instructions? (I.e.,
    // move this to emit)
    Map<Object, String> labels = new HashMap<Object, String>();
    Instruction[] instrs = instrsList.toArray(new Instruction[0]);
    for (int i = 0; i < instrs.length; i++) {
      Instruction instr = instrs[i];
      if (instr instanceof LABELInstruction) {
        Object newLabel = uniqueLabel(labels, ((LABELInstruction)instr).name);
        instr = Instructions.LABEL.make(newLabel);
      } else if (instr instanceof TargetInstruction) {
        TargetInstruction target = (TargetInstruction)instr;
        Object newLabel = uniqueLabel(labels, target.getTarget());
        instr = target.replaceTarget(newLabel);
      }
      emit(instr);
    }
  }

  public List<Instruction> getInstructions(boolean generateConstants) {
    if (! constantsGenerated && generateConstants) {
      generateConstants();
    }
    return this;
  }

  public String newLabel() {
    return "L" + nextLabel++;
  }

  public String newLabel(String prefix) {
    return prefix + "$" + nextLabel++;
  }

  public static class ConstantCollector extends ArrayList<String> {
    public Object[] getConstants() {
      return toArray();
    }
  }


  // Long way to go for a closure
  public static class ConstantSorter implements Comparator<Map.Entry<String, Integer>> {
    public int compare(Map.Entry<String, Integer> o1, Map.Entry<String, Integer> o2) {
      int n1 = o1.getValue();
      int n2 = o2.getValue();
      // Sort larger to the front (higher usage)
      // Longer string wins in a tie
      if (n1 == n2) {
        int l1 = (o1.getKey()).length();
        int l2 = (o2.getKey()).length();
        return l2 - l1;
      } else {
        return n2 - n1;
      }
    }

    @Override
    public boolean equals (Object other) {
      // Too specific?  Do we care?
      return this == other;
    }
  }

  // There is probably some idiom for singletons that I don't know
  private static ConstantSorter sorter = new ConstantSorter();

  // This is kind of like a sorted set, but delays the sorting until
  // you ask for values from the set, and has a special limit on the
  // number of values that can be in the set.
  public static class SortedConstantCollector extends ConstantCollector {
    public Map<String, Integer> usageCount;
    public boolean updated;
    public boolean frozen;

    SortedConstantCollector() {
      super();
      usageCount = new HashMap<String, Integer>();
      updated = false;
      frozen = false;
    }

    @Override
    public void add(int index, String value) {
      assert (! frozen) : "Add after constant pool frozen";
      updated = false;
      if (usageCount.containsKey(value)) {
        int n = usageCount.get(value);
        usageCount.put(value, n + 1);
      } else {
        usageCount.put(value, 1);
      }
    }

    @Override
    public boolean add(String value) {
      add(size(), value);
      return true;
    }

    @Override
    public boolean addAll(int index, Collection<? extends String> c) {
      for (Iterator<? extends String> i = c.iterator(); i.hasNext(); index++) {
        add(index, i.next());
      }
      return true;
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
      return addAll(size(), c);
    }

    @Override
    public void clear() {
      assert (! frozen) : "Clear after constant pool frozen";
      updated = false;
      usageCount.clear();
      super.clear();
    }

    @Override
    public boolean contains(Object value) {
      // Should this return if value was ever added, or if value will
      // be in the permitted subset?
      return usageCount.containsKey(value);
    }

    @Override
    public int indexOf(Object value) {
      update();
      return super.indexOf(value);
    }

    @Override
    public boolean isEmpty() {
      return usageCount.size() == 0;
    }

    @Override
    public int lastIndexOf(Object value) {
      update();
      return super.lastIndexOf(value);
    }

    private String removeInternal(int index) {
      assert (! frozen) : "removeInternal after constant pool frozen";
      updated = false;
      String value = super.remove(index);
      usageCount.remove(value);
      return value;
    }

    @Override
    public String remove(int index) {
      update();
      return removeInternal(index);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
      update();
      for (int i = fromIndex; i < toIndex; i++) {
        removeInternal(i);
      }
    }

    @Override
    public String set(int index, String value) {
      update();
      remove(index);
      add(value);
      return value;
    }

    @Override
    public Object[] toArray() {
      update();
      return super.toArray();
    }

    @Override
    public <T> T[] toArray(T[] array) {
      update();
      return super.toArray(array);
    }

    @Override
    public String toString() {
      update();
      return super.toString();
    }

    private void update() {
      if (! updated) {
        assert (! frozen) : "update after constant pool frozen";
        super.clear();
        ArrayList<Map.Entry<String, Integer>> sorted = new ArrayList<Map.Entry<String, Integer>>();
        for (Map.Entry<String, Integer> entry : usageCount.entrySet()) {
          sorted.add(entry);
        }
        Collections.sort(sorted, sorter);
        // Total size of an action must be < 65535, opcode + length
        // field is 3 bytes, also must account for encoding of strings
        int room = 65535 - 3;
        String encoding = "UTF-8";
        try {
          for (Map.Entry<String, Integer> entry : sorted) {
            String symbol = entry.getKey();
            room -= (symbol.getBytes(encoding).length + 1);
            if (room <= 0) break;
            super.add(symbol);
          }
        } catch (UnsupportedEncodingException e) {
          assert false : "this can't happen";
        }
        updated = true;
      }
    }

    @Override
    public Object[] getConstants() {
      Object [] constants = toArray();
      frozen = true;
      return constants;
    }
  }
}
