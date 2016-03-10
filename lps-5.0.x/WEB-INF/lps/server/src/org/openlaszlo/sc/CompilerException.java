/* *****************************************************************************
 * CompilerException.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.sc;

/** Represents an input error in script compilation.
 *
 * @author  Oliver Steele
 * @version 1.0
 */
@SuppressWarnings("serial")
public class CompilerException extends RuntimeException {
    /** Constructs an instance.
     */
    public CompilerException() {
        super();
    }
    
    /** Constructs an instance.
     * @param message a string
     */
    public CompilerException(String message) {
        super(message);
    }
}
