/******************************************************************************
 * ConversionException.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.data;

@SuppressWarnings("serial")
public class ConversionException extends Exception {

    public ConversionException()
    {
        super();
    }

    public ConversionException(String s)
    {
        super(s);
    }
}
