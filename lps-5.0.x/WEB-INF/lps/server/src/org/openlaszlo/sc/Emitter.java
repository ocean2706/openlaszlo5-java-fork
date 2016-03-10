/***
 * Emitter.java
 */

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import java.util.List;

import org.openlaszlo.sc.Instructions.Instruction;

public interface Emitter {

  public byte[] assemble(List<Instruction> instrs);

  public void emit(Instruction instr);
}
