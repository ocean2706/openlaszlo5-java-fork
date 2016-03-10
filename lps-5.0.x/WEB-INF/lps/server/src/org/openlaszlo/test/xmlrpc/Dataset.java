/* ****************************************************************************
 * Dataset.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test.xmlrpc;

import java.io.IOException;
import java.io.StringReader;

import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.openlaszlo.xml.DataEncoder;
import org.openlaszlo.xml.DataEncoderException;

public class Dataset
{
    DataEncoder mEncoder = null;
    SAXBuilder mBuilder = new SAXBuilder();

    public DataEncoder getDataset() {
        return mEncoder;
    }

    public void setDataset(String xml)
        throws JDOMException, IOException, DataEncoderException {
        mEncoder = new DataEncoder();
        mEncoder.buildFromDocument(mBuilder.build(new StringReader(xml)));
    }
}

