/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.             *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test.netsize;

import java.io.BufferedWriter;
import java.io.IOException;

public class AppSizer extends Sizer {
    public AppSizer(TotalSizer parent, String name, long prevsize) {
        super(parent, name, prevsize);
    }

    @Override
    public void generateProps(BufferedWriter bw)
        throws IOException
    {
        bw.write("\n");
        bw.write(name + ".size=" + totalBytes + "\n");
    }

    public int getChildIndex(UrlSizer url) {
        int count = 1;
        for (Sizer child : children) {
            if (child == url) {
                return count;
            }
            count++;
        }
        return -1;
    }
}

