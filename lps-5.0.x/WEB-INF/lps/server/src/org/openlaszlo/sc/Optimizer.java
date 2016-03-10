/***
 * Optimizer.java
 *
 * Description: Peep-hole optimizer for the assembler
 */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.openlaszlo.sc.Instructions.ConcreteInstruction;
import org.openlaszlo.sc.Instructions.Instruction;
import org.openlaszlo.sc.Instructions.PUSHInstruction;

public class Optimizer implements Emitter {
  Emitter receiver;
  List<Instruction> instrBlock;
  StackModel stackModel;

  public Optimizer (Emitter receiver) {
    this.receiver = receiver;
    // peephole block
    this.instrBlock = new ArrayList<Instruction>();
    // point to last instruction in block, if it is a push
    // models the data on the stack
    this.stackModel = new StackModel();
  }

  public void flush() {
    if (this.instrBlock.size() != 0) {
      for (Instruction i : instrBlock) {
        this.receiver.emit(i);
      }
      this.instrBlock.clear();
      if (this.stackModel != null) {
        this.stackModel.clear();
      } else {
        this.stackModel = new StackModel();
      }
    }
  }

  public byte[] assemble(List<Instruction> instrs) {
    for (Instruction i : instrs) {
      this.emit(i);
    }
    this.flush();
    return this.receiver.assemble(Collections.<Instruction>emptyList());
  }

  public void  emit(Instruction instr) {
    // Peephole optimizations.
//     System.out.println("" + this.instrBlock);
//     System.out.println("" + this.stackModel);

    // The push optimizations create an instruction block across
    // which pushes may be moved and depend on the model of the
    // stack which records the instruction that created the data at
    // the top of the stack.  This model is created when a push
    // instruction is encountered and maintained until no further
    // optimizations can be made.  In the interim, instructions are
    // passed straight through, without modelling, for efficiency.
    StackModel model = this.stackModel;
    List<Instruction> instrBlock = this.instrBlock;

    if (instr instanceof PUSHInstruction) {
      PUSHInstruction push = (PUSHInstruction)instr;
      PUSHInstruction last = model.lastPush();
      // System.out.println("last = " + last);
      if (last != null) {
        // PUSH a; PUSH b -> PUSH a, b
        // a push of a register cannot be moved across other
        // instructions
        if (last != instrBlock.get(instrBlock.size() - 1) && push.isVolatile()) {
          // System.out.println("last: " + last + " instrBlock.get(-1): " + instrBlock.get(instrBlock.size() - 1));
          ;
        } else if (last.merge(push, model)) {
          return;
        }
      }
      // Accumulate the instruction and update the model
      // Copy push, since you will smash it
      push = new PUSHInstruction(push.args);
      instrBlock.add(push);
      this.stackModel = push.updateStackModel(model);
    } else if (instr instanceof ConcreteInstruction && 
               ((ConcreteInstruction)instr).op == Actions.DUP) {
      // PUSH a; DUP -> PUSH a,a
      PUSHInstruction last = model.lastPush();
      if (last != null) {
//         System.out.println("" + this.instrBlock);
//         System.out.println("" + this.stackModel);
        if (last.dup(model)) {
          return;
        }
      }
      // if accumulating, accumulate, else emit straight
      if (instrBlock.size() != 0) {
        // Accumulate the instruction and update the model
        instrBlock.add(instr);
        this.stackModel = instr.updateStackModel(model);
      } else {
        this.receiver.emit(instr);
      }
      // TBD: INC, DEC
    } else {
      // if accumulating, accumulate, else emit straight
      if (instrBlock.size() != 0) {
        // Accumulate the instruction and update the model
        instrBlock.add(instr);
        this.stackModel = instr.updateStackModel(model);
      } else {
        this.receiver.emit(instr);
      }
    }
    // If the stackModel becomes unknown, flush the block
    if (this.stackModel == null) {
      this.flush();
    }
  }
}
