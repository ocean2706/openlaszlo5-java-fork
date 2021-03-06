/******************************************************************************
 * ResponderMEDIA.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2008-2011 Laszlo Systems, Inc.  All Rights Reserved.   *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.data.DataSource;
import org.openlaszlo.data.HTTPDataSource;
import org.openlaszlo.data.HttpData;
import org.openlaszlo.server.LPS;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;

/* The Media responder will just request the data from the target URL,
 * and forward the data verbatim to the client, preserving the content-type MIME
 * header
 *
 */
public final class ResponderMEDIA extends Responder
{
    private static Logger mLogger = Logger.getLogger(ResponderMEDIA.class);

    private static DataSource mHTTPDataSource = new HTTPDataSource();


    @Override
    protected void respondImpl(HttpServletRequest req, HttpServletResponse res) {

        String path = req.getServletPath();
        String url;
        try {
            url  = DataSource.getURL(req);
        } catch (java.net.MalformedURLException e) {
            respondWithErrorSWF(res, "bad url: " + e.getMessage());
            return;
        }

        if ( ! LPS.configuration.optionAllows(path, "proxy-security-urls", url) ) {
            String err = "Forbidden url: " +  url;
            respondWithError(res, err, HttpServletResponse.SC_FORBIDDEN);
            mLogger.error(err);
            return;
        }

        try { 

            String app = LZHttpUtils.getRealPath(mContext, req);
            HttpData mdata = (HttpData) mHTTPDataSource.getData(app, req, res, -1);
            res.setContentType(mdata.getMimeType());
            long size = mdata.size();
            if (size != -1) {
                mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="setting content length: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                DataSource.class.getName(),"051018-266", new Object[] {size})
);
                res.setContentLength((int)size);
            }

            InputStream instream = mdata.getInputStream();
            OutputStream outstream = res.getOutputStream();
            try {
                FileUtils.sendToStream(instream, outstream);
            } catch (FileUtils.StreamWritingException e) {
                mLogger.warn(
                    /* (non-Javadoc)
                     * @i18n.test
                     * @org-mes="StreamWritingException while responding: " + p[0]
                     */
                    org.openlaszlo.i18n.LaszloMessages.getMessage(
                        DataSource.class.getName(),"051018-201", new Object[] {e.getMessage()})
                             );
            } finally {
                if (mdata != null) {
                    mdata.release();
                }
                FileUtils.close(outstream);
                FileUtils.close(instream);
            }
        } catch (Throwable e) { 
            if (url.toLowerCase(Locale.ENGLISH).endsWith(".mp3")) {
                // See LPP-7880 We can only indicate an error to the
                // Flash client sound loader by sending a HTTP 404
                // error.
                try {
                    res.sendError(404);
                } catch (Exception err) {
                    mLogger.error(e.getMessage());
                }
            } else {
                respondWithErrorSWF(res, e.getMessage());
            }
        } 
    }

}
