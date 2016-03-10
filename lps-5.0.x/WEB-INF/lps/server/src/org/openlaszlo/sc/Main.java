/* *****************************************************************************
 * Main.java
 * ****************************************************************************/

package org.openlaszlo.sc;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

public class Main {
    public static void main(String args[])
    {

        Logger logger = Logger.getRootLogger();
        // Configure logging
        logger.setLevel(Level.ERROR);
        PatternLayout layout = new PatternLayout("%m%n");
        logger.removeAllAppenders();
        logger.addAppender(new ConsoleAppender(layout));

        int status = new lzsc().compile(args);
        if (status != 0) {
          java.lang.System.exit(status);
        }
    }
}

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/
