/******************************************************************************
 * TranscoderException.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.media;

@SuppressWarnings("serial")
public class TranscoderException extends Exception {

    public TranscoderException()
    {
        super();
    }

    public TranscoderException(String s)
    {
        super(s);
    }
}
