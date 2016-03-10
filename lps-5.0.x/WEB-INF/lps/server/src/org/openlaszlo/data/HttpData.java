/* *****************************************************************************
 * HTTPData.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethodBase;
import org.openlaszlo.utils.FileUtils;
import org.openlaszlo.utils.LZHttpUtils;



/**
 * A class for holding on to results of an Http fetch.
 *
 * @author <a href="mailto:bloch@laszlosystems.com">Eric Bloch</a>
 */

public class HttpData extends Data {

    /** response code */
    public final int code;
    
    /** Http request */
    public final HttpMethodBase  request;
    
    HttpMethodBase getRequest() {
        return this.request;
    }

    private static final Pattern charsetPattern;
    static {
        charsetPattern = Pattern.compile(";charset=([^ ]*)");
    }

    /** 
     * @param r filled request
     * @param c response code
     */
    public HttpData(HttpMethodBase r, int c) {
        code = c;
        request = r;
    }
    
    /**
     * @return true if the data was "not modified"
     */
    @Override
    public boolean notModified() {
        return code == HttpServletResponse.SC_NOT_MODIFIED;
    }
    
    /**
     * @return the lastModified time of the data
     */
    @Override
    public long lastModified() {
    
        Header lastModifiedHdr = request.getResponseHeader(
            LZHttpUtils.LAST_MODIFIED);
                        
        if (lastModifiedHdr != null) {
            String lm = lastModifiedHdr.getValue();
            long l = LZHttpUtils.getDate(lm);
            // Truncate to nearest second
            return ((l)/1000L) * 1000L;
        } else {
            return -1;
        }
    }
    
    /**
     * append response headers
     */
    /**
     * release any resources associated with this data
     */
    @Override
    public void release() {
        request.releaseConnection();
    }

    /**
     * @return mime type 
     */
    @Override
    public String getMimeType() {
        Header hdr = request.getResponseHeader(LZHttpUtils.CONTENT_TYPE);
        String contentType = "";
        if (hdr != null) {
            contentType = hdr.getValue();
        }
        return contentType;
    }

    /**
     * @return string
     */
    @Override
    public String getAsString() throws IOException {
        String encoding = "UTF-8";
        String content = getMimeType();
        // search for ;charset=XXXX in Content-Type header
        Matcher pMatcher = charsetPattern.matcher(content);
        if (pMatcher.matches()) {
            encoding = pMatcher.group(1);
        }

        InputStream body = getInputStream();
        Reader reader = FileUtils.getXMLStreamReader(body, encoding);
        String response = FileUtils.readString(reader);

        return response;
    }

    /**
     * @return input stream
     */
    @Override
    public InputStream getInputStream() throws IOException {
        InputStream str = request.getResponseBodyAsStream();
        if (str == null) {
            throw new IOException(
                /* (non-Javadoc)
                 * @i18n.test
                 * @org-mes="http response body is null"
                 */
                org.openlaszlo.i18n.LaszloMessages.getMessage(
                    HTTPDataSource.class.getName(),"051018-774")
                                  );
        }
        return str;
    }

    /**
     * @return size, if known
     */
    @Override
    public long size() {
        Header hdr = request.getResponseHeader(LZHttpUtils.CONTENT_LENGTH);
        if (hdr != null) {
            String contentLength = hdr.getValue();
            if (contentLength != null) {
                int cl = Integer.parseInt(contentLength);
                return cl;
            }
        }
        return -1;
    }
}
