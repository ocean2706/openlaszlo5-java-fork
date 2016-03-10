/* *****************************************************************************
 * UnknownAttributeException.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

/** Represents an exception when an attribute with unknown type is encountered.
 *
 * @author  Henry Minsky
 */
@SuppressWarnings("serial")
public class UnknownAttributeException extends RuntimeException {
    /** The element which contains this attribute */
    private String elementName;
    private String attrName;

    public UnknownAttributeException (String elementName, String attrName) {
        this.elementName = elementName;
        this.attrName = attrName;
    }

    public UnknownAttributeException () { }

    public String getElementName() { return elementName; }
    public String getName() { return attrName; }

    public void setElementName(String s) { elementName = s; }
    public void setName(String s) { attrName = s; }

    @Override
    public String getMessage () {
return
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Unknown attribute named " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                UnknownAttributeException.class.getName(),"051018-46", new Object[] {attrName});
    }

}
