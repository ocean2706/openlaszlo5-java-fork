/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2005, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

import org.openlaszlo.sc.parser.SimpleNode;

@SuppressWarnings("serial")
public class SemanticError extends CompilerError {
  public SemanticError (String message) {
    super(message);
  }

  public SemanticError (String message, SimpleNode node) {
    super(message, node);
  }
}
