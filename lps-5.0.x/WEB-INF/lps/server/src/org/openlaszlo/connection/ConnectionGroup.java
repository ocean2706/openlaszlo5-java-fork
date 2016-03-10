/* *****************************************************************************
 * ConnectionGroup.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.connection;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.apache.log4j.Logger;

public class ConnectionGroup 
{
    private static Hashtable<String, ConnectionGroup> mGroups = new Hashtable<String, ConnectionGroup>();
    static private Logger mLogger  = Logger.getLogger(ConnectionGroup.class);

    /** Group name */
    private String mName;

    /** Applications in group */
    private Set<Application> mApplications = new HashSet<Application>();

    static public ConnectionGroup getGroup(String name) {
        return getGroup(name, true);
    }

    synchronized static public ConnectionGroup getGroup(String name, boolean create) {
        ConnectionGroup group = mGroups.get(name);
        if (group == null && create) {
            group = new ConnectionGroup(name);
            mGroups.put(name, group);
        }
        return group;
    }

    private ConnectionGroup(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void registerApplication(Application application) {
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="register(" + p[0] + ")"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ConnectionGroup.class.getName(),"051018-63", new Object[] {application})
);
        synchronized (mApplications) {
            mApplications.add(application);
        }
    }

    public void unregisterApplication(Application application) {
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="unregisterApplication(" + p[0] + ")"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ConnectionGroup.class.getName(),"051018-77", new Object[] {application})
);
        synchronized (mApplications) {
            mApplications.remove(application);
        }
    }


    public Set<String> list(String users) {
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="list(users)"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ConnectionGroup.class.getName(),"051018-92")
);

        Set<String> set = new HashSet<String>();
        for (Application app : mApplications) {
            set.addAll(app.list(users));
        }
        return set;
    }

    public int sendMessage(String users, String mesg, String range, 
                           StringBuffer xmlResult) {
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="sendMesage(users, mesg, range, xmlResult)"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ConnectionGroup.class.getName(),"051018-112")
);

        int count = 0;
        for (Application app : mApplications) {
            count += app.sendMessage(users, mesg, range, xmlResult);
        }
        if (xmlResult != null) {
            xmlResult.insert(0, "<send count=\"" + count + "\" >");
            xmlResult.append("</send>");
        }
        return count;
    }


    synchronized static public void dumpGroupsXML(StringBuffer buf, boolean details)
    {
        Application.dumpTableXML("group", mGroups, buf, details);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("<group ")
            .append(" name=\"").append(mName).append("\"")
            .append(" >");
            for (Application app : mApplications) {
                buf.append("<application name=\"" + app.getName() + "\" />");
            }
        buf.append("</group>");
        return buf.toString();
    }
}
