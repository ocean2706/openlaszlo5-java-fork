/* J_LZ_COPYRIGHT_BEGIN *******************************************************
 * Copyright 2001-2006, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
 * Use is subject to license terms.                                            *
 * J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.test;

import java.io.IOException;

public class dec2utf8 {
    public static void main(String[] args) throws IOException {

        StringBuilder buf = new StringBuilder();

        for (int i = 0; i < args.length; i++) {
            String b = args[i];
            Character c = new Character ((char) Integer.parseInt(b));
            buf.append(c);
        }

        String out = buf.toString();
        byte outb[] = out.getBytes("UTF8");

        for (int i = 0; i < outb.length; i++) {
            System.out.print("0x"+Integer.toHexString(outb[i])+" ");
        }
    }
        

}
