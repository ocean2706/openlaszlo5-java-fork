/******************************************************************************
 * DataCache.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.cache;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.openlaszlo.data.DataSource;
import org.openlaszlo.data.XMLGrabber;

/**
 * A media cache
 *
 * @author <a href="mailto:hminsky@laszlosystems.com">Henry Minsky</a>
 */
public class XMLDataCache extends RequestCache {

    public XMLDataCache(File cacheDirectory, Properties props)
        throws IOException {

        super("dxcache", cacheDirectory, new XMLGrabber(), props);
    }

    /**
     * @return a serializable cache key for the given request
     */
    @Override
    public Serializable getKey(HttpServletRequest req) 
        throws MalformedURLException {

        // This is a nice readable cache key
        
        // FIXME: [2003-04-17 bloch] someday this won't be needed
        // when sendheaders is true, a request should not 
        // be cacheable
        String hds = req.getParameter("sendheaders");
        // note: space not allowed in URLS so it's good to 
        // use here as a separator to distinguish encoded keys
        if (hds == null || hds.equals("true")) {
            hds = " h=1";
        } else {
            hds = " h=0";
        }
        String enc = mConverter.chooseEncoding(req);
        if (enc == null) 
            enc = "";
        StringBuilder key = new StringBuilder();
        key.append(DataSource.getURL(req));
        key.append(hds);

        String nsprefix = req.getParameter("nsprefix");
        key.append(" " + nsprefix);

        String wtrim = req.getParameter("trimwhitespace");
        key.append(" " + wtrim);

        key.append(" ");
        key.append(enc);
        return key.toString();
    }
    
    /**
     * @return true if the request is cacheable.
     * FIXME: [2003-04-17 bloch] someday enable this
     * code when we have sendheaders default to false
    public boolean isCacheable(HttpServletRequest req) {
        boolean is = super.isCacheable(req);
        if (is) {
            String hds = req.getParameter("sendheaders");
            if (hds == null || hds.equals("false")) {
                return true;
            }
            return false;
        }  else {
            return false;
        }
    }
     */
}
