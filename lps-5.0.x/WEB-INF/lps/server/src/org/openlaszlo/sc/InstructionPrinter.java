/* -*- mode: Java; c-basic-offset: 2; -*- */

/***
 * InstructionPrinter.java
 * Author: Oliver Steele, P T Withington
 * Description: Implements an Emitter that just prints the instructions
 */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2005, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/


package org.openlaszlo.sc;

import java.io.PrintStream;
import java.util.List;
import java.util.Stack;

import org.openlaszlo.sc.Actions.Action;
import org.openlaszlo.sc.Instructions.ConcreteInstruction;
import org.openlaszlo.sc.Instructions.Instruction;
import org.openlaszlo.sc.Instructions.LABELInstruction;

public class InstructionPrinter implements Emitter {
  Stack<StackEntry> labelStack = new Stack<StackEntry>();
  int nextLabel = 1;
  String linePrefix = "";
  PrintStream writer;

  public InstructionPrinter(PrintStream writer, String prefix) {
    this.linePrefix = prefix;
    this.writer = writer;
  }

  public InstructionPrinter(PrintStream writer) {
    this(writer, "");
  }

  public InstructionPrinter() {
    this(System.out);
  }

  public byte[] assemble(List<Instruction> instrs) {
    for (Instruction i : instrs) {
      emit(i);
    }
    return null;
  }

  public static class StackEntry {
    Action sourceType;
    Object label;

    StackEntry(Action sourceType, Object label) {
      this.sourceType = sourceType;
      this.label = label;
    }
  }

  public void emit(Instruction instr) {
    if (instr instanceof LABELInstruction) {
      Object name = ((LABELInstruction)instr).name;
      StackEntry entry;
      if (!labelStack.empty() &&
          (labelStack.peek()).label == name) {
        while (!labelStack.empty() &&
               (entry = labelStack.peek()).label == name) {
          Action sourceType = entry.sourceType;
          labelStack.pop();
          linePrefix = linePrefix.substring(0, linePrefix.length() - 2);
          String comment = "";
          if (sourceType == Actions.DefineFunction ||
              sourceType == Actions.DefineFunction2) {
            comment = " // of function\n";
          }
          writer.print(linePrefix);
          writer.print("end");
          writer.println(comment);
        }
      } else {
        writer.println(instr.toString());
      }
      return;
    }
    if (instr instanceof ConcreteInstruction) {
      ConcreteInstruction cInstr = ((ConcreteInstruction)instr);
      Action op = cInstr.op;
      List<Object> args = cInstr.args;
      if (op == Actions.DefineFunction ||
          op == Actions.DefineFunction2) { 
        String target = (String)args.get(0);
        labelStack.push(new StackEntry(op, target));
        writer.print(linePrefix);
        writer.println(cInstr.toString());
        linePrefix += "  ";
      } else if (op == Actions.WITH) {
        labelStack.push(new StackEntry(op, (String)args.get(0)));
        writer.print(linePrefix);
        writer.println(cInstr.toString());
        linePrefix += "  ";
      }
    } else {
      // TODO: [2005-11-15 ptw] Why do we need this?
//       if (instr instanceof TargetInstruction) {
//         instr = instr.replaceTarget(instr.getTarget());
//       }
      writer.print(linePrefix);
      writer.println(instr.toString());
    }
  }
}
