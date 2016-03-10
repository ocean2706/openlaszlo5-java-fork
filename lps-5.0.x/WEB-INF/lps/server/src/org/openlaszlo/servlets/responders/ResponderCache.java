/******************************************************************************
 * ResponderCache.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.log4j.Logger;
import org.openlaszlo.cache.RequestCache;
import org.openlaszlo.data.Converter;
import org.openlaszlo.data.DataSource;
import org.openlaszlo.data.HTTPDataSource;
import org.openlaszlo.data.JavaDataSource;
import org.openlaszlo.utils.LZHttpUtils;
import org.openlaszlo.xml.internal.XMLUtils;

public abstract class ResponderCache extends Responder
{
    private static boolean mIsInitialized = false;

    private static HashMap<String, DataSource> mDataSourceMap = new HashMap<String, DataSource>();
    private static DataSource mHTTPDataSource = null;

    private static Logger mLogger = Logger.getLogger(ResponderCache.class);

    protected RequestCache mCache = null;

    /**
     * Keeps track of url stats.
     */
    public class URLStat
    {
        String mName;

        final static public int ERRTYPE_NONE             = -1;
        final static public int ERRTYPE_CONVERSION       = 0;
        final static public int ERRTYPE_DATA_SOURCE      = 1;
        final static public int ERRTYPE_UNKNOWN_HOST     = 2;
        final static public int ERRTYPE_MALFORMED_URL    = 3;
        final static public int ERRTYPE_IO               = 4;
        final static public int ERRTYPE_ILLEGAL_ARGUMENT = 5;
        final static public int ERRTYPE_TIMEOUT          = 6;
        final static public int ERRTYPE_FORBIDDEN        = 7;
        final static public int ERRTYPE_OTHER            = 8;
        final static public int NUM_ERRTYPES             = 9;


        HashMap<String, int[]> mURLs = new HashMap<String, int[]>();
        HashMap<String, int[]> mErrorURLs = new HashMap<String, int[]>();

        int mSuccessCount;
        /**
         * 0: mConversionException, 1: mDataSourceException, 2: mUnknownHostException,
         * 3: mMalformedURLException, 4: mIOException, 5: mIllegalArgumentException,
         * 6: mInterrupedIOException, 7 mException
         */
        int[] mErrorCount = new int[NUM_ERRTYPES];

        
        boolean mDoURLCollection = false;
        

        /**
         * Create an URLStat with a name.
         */
        public URLStat(String name)
        {
            mName = name;
            clear();
        }
        
        /**
         * @param doCollection whether the object should collect unique url
         * info.
         */
        void doURLCollection(boolean doCollection)
        {
            if (mDoURLCollection != doCollection) {
                mDoURLCollection = doCollection;
                clear();
            }
        }

        /**
         * Increment stat on url with successful status.
         * @param url successful url.
         */
        void success(String url)
        {
            int x = url.indexOf('?');
            if (x != -1) {
                url = url.substring(0, x);
            }
            synchronized (mURLs) {
                if (mDoURLCollection) {
                    // Add unique urls
                    int[] s = (int[]) mURLs.get(url);
                    if (s == null) {
                        s = new int[1];
                        mURLStat.mURLs.put(url, s);
                    }
                    ++s[0];
                }
                ++mSuccessCount;
            }
        }


        /**
         * Increment stat on url with error status.
         *
         * @param errType see ERRTYPEs.
         * @param url error url.
         */
        void error(int errType, String url)
        {
            int x = url.indexOf('?');
            if (x != -1) {
                url = url.substring(0, x);
            }
            synchronized (mErrorURLs) {
                if (mDoURLCollection) {
                    int[] e = (int[]) mErrorURLs.get(url);
                    if (e == null) {
                        e = new int[NUM_ERRTYPES];
                        mErrorURLs.put(url, e);
                    }
                    ++e[errType];
                }
                ++mErrorCount[errType];
            }

        }

        /**
         * Clear URL stats.
         */
        void clear()
        {
            synchronized (mURLs) {
                mURLs.clear();
                mSuccessCount = 0;
            }

            synchronized (mErrorURLs) {
                mErrorURLs.clear();
                for (int i=0; i < mErrorCount.length; i++)
                    mErrorCount[i] = 0;
            }
        }

        /**
         * Return url statistics in XML string.
         * @return xml info.
         */
        public String toXML()
        {
            StringBuilder buf = new StringBuilder();
            synchronized (mURLs) {
                buf.append("<").append(mName).append("-urls ")
                    .append(" unique=\"").append(mURLs.size()).append("\"")
                    .append(">\n");

                buf.append("<success")
                    .append(" total-requests=\"").append(mSuccessCount).append("\"")
                    .append(">\n");
                if (mDoURLCollection)
                {
                    for (String k : mURLs.keySet()) {
                        int[] success = mURLs.get(k);
                        buf.append("<url")
                            .append(" requests=\"").append(success[0]).append("\"")
                            .append(" href=\"").append(XMLUtils.escapeXml(k)).append("\" />");
                    }
                }
                buf.append("</success>\n");
            }

            synchronized (mErrorURLs) {
                int errTotal = 0;
                for (int i=0; i < mErrorCount.length; i++)
                    errTotal += mErrorCount[i];
                buf.append("<errors")
                    .append(" total-errors=\"").append(errTotal).append("\"")
                    .append(" conversion=\"").append(mErrorCount[ERRTYPE_CONVERSION]).append("\"")
                    .append(" datasource=\"").append(mErrorCount[ERRTYPE_DATA_SOURCE]).append("\"")
                    .append(" unknownhost=\"").append(mErrorCount[ERRTYPE_UNKNOWN_HOST]).append("\"")
                    .append(" malformedurl=\"").append(mErrorCount[ERRTYPE_MALFORMED_URL]).append("\"")
                    .append(" ioexception=\"").append(mErrorCount[ERRTYPE_IO]).append("\"")
                    .append(" illegalargument=\"").append(mErrorCount[ERRTYPE_ILLEGAL_ARGUMENT]).append("\"")
                    .append(" timeout=\"").append(mErrorCount[ERRTYPE_TIMEOUT]).append("\"")
                    .append(" forbidden=\"").append(mErrorCount[ERRTYPE_FORBIDDEN]).append("\"")
                    .append(" uncaught-exception=\"").append(mErrorCount[ERRTYPE_OTHER]).append("\"")
                    .append(">\n");
                if (mDoURLCollection)
                {
                    for (String k : mErrorURLs.keySet()) {
                        int[] e = mErrorURLs.get(k);
                        buf.append("<url")
                            .append(" conversion=\"").append(e[ERRTYPE_CONVERSION]).append("\"")
                            .append(" datasource=\"").append(e[ERRTYPE_DATA_SOURCE]).append("\"")
                            .append(" unknownhost=\"").append(e[ERRTYPE_UNKNOWN_HOST]).append("\"")
                            .append(" malformedurl=\"").append(e[ERRTYPE_MALFORMED_URL]).append("\"")
                            .append(" ioexception=\"").append(e[ERRTYPE_IO]).append("\"")
                            .append(" illegalargument=\"").append(e[ERRTYPE_ILLEGAL_ARGUMENT]).append("\"")
                            .append(" timeout=\"").append(e[ERRTYPE_TIMEOUT]).append("\"")
                            .append(" forbidden=\"").append(e[ERRTYPE_FORBIDDEN]).append("\"")
                            .append(" uncaught-exception=\"").append(e[ERRTYPE_OTHER]).append("\"")
                            .append(" href=\"").append(XMLUtils.escapeXml(k)).append("\"")
                            .append(" />\n");
                    }
                }
                buf.append("</errors>\n");
            }

            buf.append("</").append(mName).append("-urls>\n");

            return buf.toString();
        }
    }

    public URLStat mURLStat = null;

    synchronized public void init(String reqName, ServletConfig config, 
            RequestCache cache, Properties prop)
        throws ServletException, IOException
    {
        super.init(reqName, config, prop);

        String reqProp = reqName.toLowerCase(Locale.ENGLISH) + "Request.collectURL";
        boolean doURLCollection =
            prop.getProperty(reqProp, "false").intern() == "true";

        mURLStat = new URLStat(reqName);
        mURLStat.doURLCollection(doURLCollection);

        if (! mIsInitialized) {
            //------------------------------------------------------------
            // Well-known data sources
            //------------------------------------------------------------
            mHTTPDataSource   = new HTTPDataSource();

            mDataSourceMap.put("http",   mHTTPDataSource);

            // For security reasons, we are now mapping file => http 
            mDataSourceMap.put("file",   mHTTPDataSource);

            /*
            try {
                mDataSourceMap.put("file",   new FileDataSource());
            } catch (Throwable e) {
                mLogger.warn("can't load file datasource", e);
            }
            */

            try {
                mDataSourceMap.put("java",   new JavaDataSource());
            } catch (Throwable e) {
                mLogger.warn("can't load java datasource", e);
            }

            try {
                mDataSourceMap.put("soap",   new org.openlaszlo.data.json.SOAPDataSource());
            } catch (Throwable e) {
                mLogger.warn("can't load soap datasource", e);
            }


            mIsInitialized = true;
        }

        mCache = cache; 
    }

    /**
     * @return the datasource for this request
     */
    protected DataSource getDataSource(HttpServletRequest req, 
                                       HttpServletResponse res) 
        throws MalformedURLException, URIException
    {
        String ds = "http";
        String urlstr = DataSource.getURL(req);
        if (urlstr != null) {
            mLogger.debug("urlstr " + urlstr);
            URI uri = LZHttpUtils.newURI(urlstr);
            String protocol = uri.getScheme();
            if (protocol != null && protocol.equals("https")) {
                    protocol = "http";
            }

            ds = protocol;
        }

        mLogger.debug("ds is " + ds);

        DataSource source = null;
        if (ds == null) {
            source = mHTTPDataSource;
        } else {
            source = mDataSourceMap.get(ds);
            if (source == null) 
                respondWithError(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Can't find a data source for " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-540", new Object[] {urlstr}), 0
);
        }
        return source;
    }

    public float getFlashPlayerVersion(HttpServletRequest req) {
        float fpv = (float)-1.0;
        try {
            String _fpv = req.getParameter("fpv");
            if (_fpv != null)
                fpv = Float.parseFloat(_fpv);
        } catch (NumberFormatException e) {
            mLogger.debug(e.getMessage());
        }
        return fpv;
    }

    /**
     * @return the converter to be used by this cache
     */
    public Converter getConverter() {
        return mCache.getConverter();
    }
}
