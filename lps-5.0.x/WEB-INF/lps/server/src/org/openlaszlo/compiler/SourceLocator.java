/* *****************************************************************************
 * SourceLocator.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.io.Serializable;

/**  Holds XML Element source meta-information; start and end line-number, source file
 *
 * @author Henry Minsky
 */
@SuppressWarnings("serial")
public class SourceLocator implements Serializable {
    String pathname;
    /** Name to use in user messages. */
    String messagePathname;
    int startLineNumber;
    int startColumnNumber;
    int endLineNumber;
    int endColumnNumber;

    /** A string that shouldn't occur in a filename. */
    private static String serializationSeparator = "[]";

    static SourceLocator fromString(String string) {
        SourceLocator locator = new SourceLocator();
        java.util.StringTokenizer st = new java.util.StringTokenizer(string, serializationSeparator);
        locator.pathname = st.nextToken();
        locator.messagePathname = st.nextToken();
        locator.startLineNumber = Integer.parseInt(st.nextToken());
        locator.startColumnNumber = Integer.parseInt(st.nextToken());
        locator.endLineNumber = Integer.parseInt(st.nextToken());
        locator.endColumnNumber = Integer.parseInt(st.nextToken());
        return locator;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(pathname);
        buffer.append(serializationSeparator);
        buffer.append(messagePathname);
        buffer.append(serializationSeparator);
        buffer.append(startLineNumber);
        buffer.append(serializationSeparator);
        buffer.append(startColumnNumber);
        buffer.append(serializationSeparator);
        buffer.append(endLineNumber);
        buffer.append(serializationSeparator);
        buffer.append(endColumnNumber);
        return buffer.toString();
    }

    public void setPathname(String pathname, String messagePathname) {
        if (this.pathname != null && !this.pathname.equals(pathname)) {
            throw new RuntimeException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="element start and end don't match"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SourceLocator.class.getName(),"051018-64")
);
        }
        if (this.messagePathname != null && !this.messagePathname.equals(messagePathname)) {
            throw new RuntimeException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="element start and end don't match"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SourceLocator.class.getName(),"051018-64")
);
        }
        this.pathname = pathname;
        this.messagePathname = messagePathname;
    }
}

