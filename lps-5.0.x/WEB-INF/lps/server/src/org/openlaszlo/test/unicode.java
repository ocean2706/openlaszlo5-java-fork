/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test;

import org.openlaszlo.xml.internal.XMLUtils;

public class unicode {

    public static void main(String args[]) {
        String s = "\u0001\u0009\u000b\u000c\u000e\u0021\u004a\u004b\u004d";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            System.out.println("char at "+i+" = "+((int) c));
        }

        System.out.println("escaped XML: "+XMLUtils.escapeXml(s));
    }

}
