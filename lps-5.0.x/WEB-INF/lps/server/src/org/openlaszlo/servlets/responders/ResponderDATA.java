/******************************************************************************
 * ResponderDATA.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.URIException;
import org.apache.log4j.Logger;
import org.openlaszlo.cache.DataCache;
import org.openlaszlo.cache.RequestCache;
import org.openlaszlo.data.ConversionException;
import org.openlaszlo.data.DataSource;
import org.openlaszlo.data.DataSourceException;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.LZHttpUtils;

/**
 * 
 */
public final class ResponderDATA extends ResponderCache
{
    private static DataCache mCache = null;
    private static boolean mIsInitialized = false;
    private static Logger mLogger = Logger.getLogger(ResponderDATA.class);

    @Override
    synchronized public void init(String reqName, ServletConfig config, Properties prop)
        throws ServletException, IOException
    {
        // Cache should only be initialized once.
        if (! mIsInitialized) {
            // Initialize data cache
            String cacheDir = config.getInitParameter("lps.dcache.directory");
            if (cacheDir == null) {
                cacheDir = prop.getProperty("dcache.directory");
            }
            if (cacheDir == null) {
                cacheDir = LPS.getWorkDirectory() + File.separator + "dcache";
            }

            File cache = checkDirectory(cacheDir);
            mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Data Cache is at " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderDATA.class.getName(),"051018-54", new Object[] {cacheDir})
);

            //------------------------------------------------------------
            // Support for new style data response
            //------------------------------------------------------------
            try {
                mCache = new DataCache(cache, prop);
            } catch (IOException e) {
                throw new ServletException(e.getMessage());
            }

            mIsInitialized = true;
        }
        super.init(reqName, config, mCache, prop);
    }

    static public RequestCache getCache() {
        return mCache;
    }

    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res) {

        String path = req.getServletPath();
        String url;
        try {
            url  = DataSource.getURL(req);
        } catch (java.net.MalformedURLException e) {
            respondWithErrorSWF(res, "bad url: " + e.getMessage());
            if (mCollectStat) {
                mURLStat.error(URLStat.ERRTYPE_MALFORMED_URL, "bad-url");
            }
            return;
        }

        if (path.endsWith(".lzo")) {
            path = path.substring(0, path.length() - 1) + "x";
        }

        if (req.getMethod().intern() == "POST") {
            float fpv = getFlashPlayerVersion(req);
            String ua = req.getHeader(LZHttpUtils.USER_AGENT);
            mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="POST request, flash player version: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-328", new Object[] {new Float(fpv)})
);
            if (fpv < 6.47 && 
                LPS.configuration.optionAllows("disable-post-keep-alive", ua)) {
                // Prevent browser keep-alive to get around bug 4048.
                mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Disabling keep-alive for " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-339", new Object[] {ua})
);
                res.setHeader("Connection", "close");
                res.setHeader("Keep-Alive", "close");
            }
        }

        if ( ! LPS.configuration.optionAllows(path, "proxy-security-urls", url) ) {
            String err = "Forbidden url: " +  url;
            respondWithError(res, err, HttpServletResponse.SC_FORBIDDEN);
            mLogger.error(err);
            if (mCollectStat) {
                mURLStat.error(URLStat.ERRTYPE_FORBIDDEN, url);
            }
            return;
        }

        int errType = URLStat.ERRTYPE_NONE;

        try { 

            DataSource source = getDataSource(req, res);
            if (source == null) {
                return;
            }

            res.setContentType(MimeType.SWF);

            String app = LZHttpUtils.getRealPath(mContext, req);
            boolean isClientCacheable = DataSource.isClientCacheable(req);
            if (mCache.isCacheable(req)) {
                if (isClientCacheable) {
                    mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="proxying " + p[0] + ", cacheable on server and client"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-377", new Object[] {url})
);
                } else {
                    mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="proxying " + p[0] + ", cacheable on server and not client"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-386", new Object[] {url})
);
                }
                mCache.getAsSWF(app, req, res, source);
            } else {
                if (isClientCacheable) {
                    mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="proxying " + p[0] + ", not cacheable on server and cacheable on the client"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-398", new Object[] {url})
);
                } else {
                    mLogger.info(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="proxying " + p[0] + ", not cacheable on server or client"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-407", new Object[] {url})
);
                }
                source.getAsSWF(app, req, res, getConverter());
            }
        } catch (ConversionException e) {
            respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="data conversion error for " + p[0] + ": " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-419", new Object[] {url, e.getMessage()})
                                );
            errType = URLStat.ERRTYPE_CONVERSION;
        } catch (DataSourceException e) {
                respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="data source error for " + p[0] + ": " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-428", new Object[] {url, e.getMessage()})
                                );
            errType = URLStat.ERRTYPE_DATA_SOURCE;
        } catch (UnknownHostException e) {
            respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="unknown host for " + p[0] + ": " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-437", new Object[] {url, e.getMessage()})
                                );
            errType = URLStat.ERRTYPE_UNKNOWN_HOST;
        } catch (URIException e) {
            respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="bad url: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-446", new Object[] {e.getMessage()})
);
            errType = URLStat.ERRTYPE_MALFORMED_URL;
        } catch (MalformedURLException e) {
            respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="bad url: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-446", new Object[] {e.getMessage()})
);
            errType = URLStat.ERRTYPE_MALFORMED_URL;
        } catch (InterruptedIOException e) {
            respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="backend timeout for " + p[0] + ": " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-466", new Object[] {url, e.getMessage()})
                                );
            errType = URLStat.ERRTYPE_TIMEOUT;
        } catch (IOException e) {
            // Handle SocketTimeoutExceptions as timeouts instead of IO issues
            Class<?> stec = null;
            try {
                stec = Class.forName("java.net.SocketTimeoutException");
            } catch (ClassNotFoundException cfne) {
            }
            if (stec != null && stec.isAssignableFrom(e.getClass())) {
                errType = URLStat.ERRTYPE_TIMEOUT;
                respondWithErrorSWF(res, 
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="backend timeout for " + p[0] + ": " + p[1]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                ResponderCache.class.getName(),"051018-466", new Object[] {url, e.getMessage()})
                                );
            } else {
                respondWithExceptionSWF(res, e);
                errType = URLStat.ERRTYPE_IO;
            }
        } catch (IllegalArgumentException e) {
            respondWithExceptionSWF(res, e);
            errType = URLStat.ERRTYPE_ILLEGAL_ARGUMENT;
        } catch (Throwable e) { 
            // Makes much easier to debug runtime exceptions
            // but perhaps not strictly correct.
            respondWithExceptionSWF(res, e);
            errType = URLStat.ERRTYPE_OTHER;
        } 

        if (mCollectStat) {
            if (errType == URLStat.ERRTYPE_NONE)
                mURLStat.success(url);
            else 
                mURLStat.error(errType, url);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.SWF;
    }
}
