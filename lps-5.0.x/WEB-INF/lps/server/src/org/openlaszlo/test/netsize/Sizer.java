/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.             *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test.netsize;

import java.io.BufferedWriter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public abstract class Sizer {
    long totalBytes = 0;
    Sizer parent;
    List<Sizer> children = new ArrayList<Sizer>();
    String name;
    long prevsize;

    public Sizer(Sizer parent, String name, long prevsize) {
        this.parent = parent;
        this.name = name;
        this.prevsize = prevsize;
        if (parent != null) {
            this.parent.children.add(this);
        }
    }
    
    void addSize(long n) {
        totalBytes += n;
        if (parent != null) {
            parent.addSize(n);
        }
    }

    public void connect()
        throws IOException
    {
        for (Sizer child : children) {
            child.connect();
        }
    }

    void report(String prefix) {
        String pctstr = "";
        if (prevsize > 0) {
            NumberFormat format = NumberFormat.getPercentInstance();
            double pct = (double)totalBytes / prevsize;
            pctstr = " (" + format.format(pct) + ")";
        }
        System.out.println(prefix + name + ": " + totalBytes + pctstr);
        for (Sizer child : children) {
            child.report(prefix + "  ");
        }
    }

    void report() {
        report("");
    }

    void generatePropertiesFile(BufferedWriter bw)
        throws IOException
    {
        generateProps(bw);
        for (Sizer child : children) {
            child.generatePropertiesFile(bw);
        }
    }

    public abstract void generateProps(BufferedWriter bw)
        throws IOException;
}
