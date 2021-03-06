/* *****************************************************************************
 * LZSOAPMessage.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.remote.json.soap;

import java.util.*;

public class LZSOAPMessage
{
    String mName;
    String mMode;
    String mUse = null;
    Set<String> mPartNames = null;
    List<LZSOAPPart> mParts = null;

    /**
     * @param name name of soap message.
     * @param type one of input or output.
     */
    public LZSOAPMessage(String name, String mode) {
        mName = name;
        mMode = mode;
    }

    public String getName() {
        return mName;
    }

    public String getMode() {
        return mMode;
    }

    public void setUse(String use) {
        mUse = use;
    }

    public String getUse() {
        return mUse;
    }

    public Set<String> getPartNames() {
        return mPartNames;
    }

    public void setPartNames(Set<String> partNames) {
        mPartNames = partNames;
    }

    public void setParts(List<LZSOAPPart> parts) {
        mParts = parts;
    }

    public List<LZSOAPPart> getParts() {
        return mParts;
    }

    void toPartXML(StringBuffer sb) {
        sb.append("<parts>");
        if (mParts != null) {
            for (int i=0; i < mParts.size(); i++) {
                LZSOAPPart part = mParts.get(i);
                part.toXML(sb);
            }
        }
        sb.append("</parts>");
    }

    public void toXML(StringBuffer sb) {
        sb.append("<message")
            .append(" name=\"").append(mName).append("\"")
            .append(" mode=\"").append(mMode).append("\"")
            .append(" use=\"").append(mUse).append("\"")
            .append(" partnames=\"").append(mPartNames).append("\"")
            .append(">");
        toPartXML(sb);
        sb.append("</message>");
    }
}
