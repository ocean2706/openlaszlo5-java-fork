/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.             *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test.netsize;

import java.io.BufferedWriter;
import java.io.IOException;

public class TotalSizer extends Sizer {
    public TotalSizer(String name, long prevsize) {
        super(null, name, prevsize);
    }

    @Override
    public void generateProps(BufferedWriter bw)
        throws IOException
    {
        String namelist = "";
        for (Sizer child : children) {
            if (namelist.length() > 0) {
                namelist += ",";
            }
            namelist += child.name;
        }
        bw.write("apps=" + namelist + "\n");
        bw.write("totalsize=" + totalBytes + "\n");
    }
}
