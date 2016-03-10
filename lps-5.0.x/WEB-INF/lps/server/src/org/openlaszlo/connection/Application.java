/* *****************************************************************************
 * Application.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.connection;

import java.io.IOException;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.openlaszlo.auth.Authentication;
import org.openlaszlo.utils.MultiMap;

public class Application 
{
    private static final int RANGE_ALL = 0;
    private static final int RANGE_USER = 1;
    private static final int RANGE_AGENT = 2;

    private static Hashtable<String, Application> mApplications = new Hashtable<String, Application>();
    static private Logger mLogger  = Logger.getLogger(Application.class);

    static public Application getApplication(String name)
    {
        return getApplication(name, true);
    }

    synchronized static public Application getApplication(String name, boolean create)
    {
        Application application = mApplications.get(name);
        if (application == null && create) {
            application = new Application(name);
            mApplications.put(name, application);
        }
        return application;
    }

    synchronized static public void removeApplication(Application application) {
        ConnectionGroup group = application.getConnectionGroup();
        if (group != null) {
            group.unregisterApplication(application);
        }
        mApplications.remove(application);
    }

    private String mName;
    private long mLastModifiedTime = 0;
    private long mHeartbeat = 0;
    private Authentication mAuthenticator = null;
    private boolean mSendUserDisconnect = false;
    private ConnectionGroup mGroup = null;

    /** Username lookup */
    private MultiMap<String, HTTPConnection> mUsers = new MultiMap<String, HTTPConnection>();

    /** Connections */
    private Hashtable<String, HTTPConnection> mConnections = new Hashtable<String, HTTPConnection>();

    /** Agents */
    private Hashtable<String, ConnectionAgent> mAgents = new Hashtable<String, ConnectionAgent>();

    private Application(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public long getLastModifiedTime() {
        return mLastModifiedTime;
    }

    public void setLastModifiedTime(long lmt) {
        mLastModifiedTime = lmt;
    }

    public long getHeartbeat() {
        return mHeartbeat;
    }

    public void setHeartbeat(long heartbeat) {
        mHeartbeat = heartbeat;
    }

    public Authentication getAuthenticator() {
        return mAuthenticator;
    }

    public void setAuthenticator(Authentication authenticator) {
        mAuthenticator = authenticator;
    }

    public boolean doSendUserDisconnect() {
        return mSendUserDisconnect;
    }

    public void setSendUserDisconnect(boolean sud) {
        mSendUserDisconnect = sud;
    }

    public ConnectionGroup getConnectionGroup() {
        return mGroup;
    }

    public void setConnectionGroup(ConnectionGroup group) {
        if (mGroup != null) {
            mGroup.unregisterApplication(this);
        }
        group.registerApplication(this);
        mGroup = group;
    }


    //------------------------------------------------------------
    // Application wrappers for connection group method calls.
    //------------------------------------------------------------
    @SuppressWarnings("unused")
    private void checkGroup() {
        if (mGroup == null)
            throw new RuntimeException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="connection group not set"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-131")
            ); 
    }

    public void register(HTTPConnection connection) {
        synchronized (mUsers) {
            mUsers.add(connection.getUsername(), connection);
            mConnections.put(connection.getCID(), connection);
        }
    }

    public void unregister(HTTPConnection connection) {
        synchronized (mUsers) {
            mUsers.remove(connection.getUsername(), connection);
            mConnections.remove(connection.getCID());
        }
    }

    public void setAgents(Set<String> agentSet) {
        mAgents = new Hashtable<String, ConnectionAgent>();
        if (agentSet != null) {
            synchronized (mAgents) {
                for (String url : agentSet) {
                    ConnectionAgent agent = 
                        ConnectionAgent.getAgent(url);
                    mAgents.put(agent.getURL(), agent);
                }
            }
        }
    }


    /** 
     * Send message to a list of users
     * @param userList list of users to send message or '*' for everyone on the system
     * @param mesg message to send 
     * @return number of messages sent 
     */
    public int sendMessage(String users, String mesg, String range, 
                           StringBuffer xmlResult)
    {
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="sendMessage(users=" + p[0] + ",range=" + p[1] + ",mesg=" + p[2] + ")"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-179", new Object[] {users, range, mesg})
);

        if (users == null || users.equals(""))
            return 0;

        int r = RANGE_ALL;
        if (range == null || range.equals("")) {
            r = RANGE_ALL;
        } else if (range.equals("user")) {
            r = RANGE_USER;
        } else if (range.equals("agent")) {
            r = RANGE_AGENT;
        }

        if (users.equals("*")) {
            return sendAllMessage(mesg, r, xmlResult);
        } 

        int count = 0;
        StringTokenizer st = new StringTokenizer(users, ", ");
        while (st.hasMoreTokens()) {
            String username = st.nextToken();

            if (r == RANGE_ALL || r == RANGE_USER) {
                synchronized (mUsers) {
                    Set<HTTPConnection> usernameSet = mUsers.get(username);
                    if (usernameSet != null) {
                        Iterator<HTTPConnection> iter = usernameSet.iterator();

                        while (iter.hasNext()) {
                            HTTPConnection connection = iter.next();
                            try {
                                mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="send to " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-218", new Object[] {connection.getUsername()})
);
                                connection.send(mesg);
                                ++count;
                            } catch (IOException e) {
                                mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="user " + p[0] + " not connected"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-229", new Object[] {connection.getUsername()})
                                );
                                iter.remove();
                            }
                        }

                        if (usernameSet.size() == 0)
                            mUsers.remove(username);
                    }
                }
            }

            if (r == RANGE_ALL || r == RANGE_AGENT) {
                ConnectionAgent agent = null;
                try {
                    synchronized (mAgents) {
                        agent = mAgents.get(username);
                    }
                    if (agent != null) {
                        if (xmlResult != null) {
                            StringBuilder tmp = 
                                new StringBuilder("<agent url=\"" + agent.getURL() + "\" >");
                            tmp.append(agent.send(mesg));
                            tmp.append("</agent>");
                            xmlResult.append(tmp.toString());
                        } else {
                            agent.send(mesg);
                        }
                        ++count;
                    }
                } catch (IOException e) {
                    mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="IOException: agent " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-266", new Object[] {agent.getURL()})
);
                }
            }
            
        }
        return count;
    }

    private int sendAllMessage(String mesg, int range, StringBuffer xmlResult) 
    {
        int count = 0;

        if (range == RANGE_ALL || range == RANGE_USER) {
            synchronized (mUsers) {
                Iterator<Map.Entry<String, HTTPConnection>> iter = mConnections.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, HTTPConnection> entry = iter.next();
                    HTTPConnection connection = entry.getValue();
                    try {
                        connection.send(mesg);
                        ++count;
                        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes=p[0] + " sent message"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-295", new Object[] {connection.getUsername()})
);
                    } catch (IOException e) {
                        iter.remove();
                        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes=p[0] + " not connected"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-305", new Object[] {connection.getUsername()})
);
                    }
                }
            }
        }

        if (range == RANGE_ALL || range == RANGE_AGENT) {
            synchronized (mAgents) {
                for (Map.Entry<String, ConnectionAgent> entry : mAgents.entrySet()) {
                    ConnectionAgent agent = entry.getValue();
                    try {
                        if (xmlResult != null) {
                            String result = agent.send(mesg);
                            StringBuilder tmp =
                                new StringBuilder("<agent url=\"" + agent.getURL() + "\" >");
                            tmp.append(result);
                            tmp.append("</agent>");
                            xmlResult.append(tmp.toString());
                        } else {
                            agent.send(mesg);
                        }
                        ++count;
                    } catch (IOException e) {
                        mLogger.warn(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="IOException: agent " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-337", new Object[] {agent.getURL()})
);
                    }
                }
            }
        }

        if (xmlResult != null) {
            xmlResult.insert(0, "<send count=\"" + count + "\" >");
            xmlResult.append("</send>");
        }

        return count;
    }


    /** 
     * Return an xml string list of connected users. Agents are not considered
     * users.
     */
    public Set<String> list(String users) {
        synchronized (mUsers) {
            Set<String> set = new HashSet<String>();
            mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="list(users=" + p[0] + ")"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                Application.class.getName(),"051018-366", new Object[] {users})
);

            if (users.equals("*")) {
                Iterator<String> iter = mUsers.keySet().iterator();
                while (iter.hasNext()) {
                    set.add(iter.next());
                }
            } else {
                StringTokenizer st = new StringTokenizer(users, ", ");
                while (st.hasMoreTokens()) {
                    String username = st.nextToken();
                    if (mUsers.containsKey(username))
                        set.add(username);
                }
            }
            return set;
        }
    }



    public HTTPConnection getConnection(String cid) {
        synchronized (mUsers) {
            if (cid == null || cid.equals(""))
                return null;
            return mConnections.get(cid);
        }
    }


    //----------------------------------------------------------------------
    // Info functions
    //----------------------------------------------------------------------

    synchronized static public void dumpApplicationsXML(StringBuffer buf, 
                                                        boolean details) {
        buf.append("<applications>");
        for (Map.Entry<String, Application> e : mApplications.entrySet()) {
            Application app = e.getValue();
            app.dumpXML(buf, details);
        }
        buf.append("</applications>");
    }

    public void dumpXML(StringBuffer buf, boolean details) {
        Authentication a = mAuthenticator;
        ConnectionGroup g = mGroup;
        buf.append("<application ")
            .append(" name=\"").append(mName).append("\"")
            .append(" group=\"").append(( g!=null ? g.getName() : "none" )).append("\"")
            .append(" heartbeat=\"").append(mHeartbeat).append("\"")
            .append(" authenticator=\"").append(( a!=null ? a.getClass().toString() : "none" )).append("\"")
            .append(" senduserdisconnect=\"").append(mSendUserDisconnect).append("\"")
            .append(" >");
//         dumpUsersXML(buf, details);
        dumpConnectionsXML(buf, details);
        dumpAgentsXML(buf, details);
        buf.append("</application>");
    }

    public void dumpConnectionsXML(StringBuffer buf, boolean details) {
        synchronized (mUsers) {
            dumpTableXML("connection", mConnections, buf, details);
        }
    }

    public void dumpAgentsXML(StringBuffer buf, boolean details) {
        synchronized (mAgents) {
            dumpTableXML("agent", mAgents, buf, details);
        }
    }

    public void dumpUsersXML(StringBuffer buf, boolean details) {
        synchronized (mUsers) {
            dumpMultiTableXML("user", mUsers, buf, details);
        }
    }

    public static <V> void dumpMultiTableXML(String table, Map<String, Set<V>> map, 
                                         StringBuffer buf, boolean details)
    {
        buf.append("<").append(table).append("-table>");
        for (Map.Entry<String, Set<V>> e : map.entrySet()) {
            String k = e.getKey();
            if (details) {
                buf.append("<" + table + " name=\"" + k + "\">");
                Set<V> set = e.getValue();
                for (V v : set) {
                    buf.append(v.toString());
                }
                buf.append("</" + table + ">\n");
            } else {
                buf.append("<" + table + " name=\"").append(k).append("\" />\n");
            }

        }
        buf.append("</").append(table).append("-table>\n");
    }

    public static <V> void dumpTableXML(String table, Map<String, V> map, 
                                    StringBuffer buf, boolean details)
    {
        buf.append("<").append(table).append("-table>");
        for (Map.Entry<String, V> e : map.entrySet()) {
            String k = e.getKey();
            if (details) {
                V v = e.getValue();
                buf.append(v.toString());
            } else {
                buf.append("<" + table + " name=\"").append(k).append("\" />");
            }

        }
        buf.append("</").append(table).append("-table>\n");
    }
}
