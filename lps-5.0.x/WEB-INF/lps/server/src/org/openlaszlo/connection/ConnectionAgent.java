/******************************************************************************
 * ConnectionAgent.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.connection;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Hashtable;

import org.apache.log4j.Logger;
import org.openlaszlo.data.Data;
import org.openlaszlo.data.DataSourceException;
import org.openlaszlo.data.HTTPDataSource;

public class ConnectionAgent
{
    private static Logger mLogger = Logger.getLogger(ConnectionAgent.class);

    String mURL;

    private static Hashtable<String, ConnectionAgent> mAgents = new Hashtable<String, ConnectionAgent>();

    private ConnectionAgent(String url) 
    {
        try{
            mURL = url;

            String host = new URL(url).getHost();
            if (host == null || host.equals(""))
                throw new RuntimeException(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="bad host in url"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ConnectionAgent.class.getName(),"051018-46")
);

            mLogger.debug("Agent " + url);

        } catch (MalformedURLException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    synchronized static public ConnectionAgent getAgent(String url)
    {
        return getAgent(url, true);
    }

    synchronized static public ConnectionAgent getAgent(String url, boolean create)
    {
        ConnectionAgent agent = mAgents.get(url);
        if (agent == null && create) {
            agent = new ConnectionAgent(url);
            mAgents.put(url, agent);
        }
        return agent;
    }


    public String getURL()
    {
        return mURL;
    }

    public String send(String msg) throws IOException {
        String surl = mURL + "?xml=" + URLEncoder.encode(msg, "UTF-8"); 
        Data data = null;
        try {
            data = HTTPDataSource.getHTTPData(null, null, surl, -1);
            return data.getAsString();
        } catch (DataSourceException e) {
            throw new IOException(e.getMessage());
        } finally {
            if (data != null)
                data.release();
        }
    }

    synchronized static public void dumpAgentsXML(StringBuffer buf, boolean details)
    {
        Application.dumpTableXML("agent", mAgents, buf, details);
    }

    @Override
    public String toString() {
        return new StringBuilder("<agent ")
            .append(" url=\"").append(mURL).append("\"")
            .append(" />")
            .toString();
    }
}
