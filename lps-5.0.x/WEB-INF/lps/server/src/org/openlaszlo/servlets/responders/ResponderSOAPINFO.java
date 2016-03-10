/******************************************************************************
 * ResponderSOAPINFO.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2007, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets.responders;

import java.io.IOException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.openlaszlo.data.json.SOAPDataSource;
import org.openlaszlo.utils.FileUtils;

public final class ResponderSOAPINFO extends ResponderAdmin
{

    boolean ok(String opt) {
        return opt != null && opt.equals("1");
    }

    /**
     * If "service" param exists, returns service information. An url encoded
     * name for the service is included in the standard XML lzt=soapinfo under
     * <service name="NAME" urlname="URL_ENCODED_NAME" />.
     */
    @Override
    protected void respondAdmin(HttpServletRequest req, HttpServletResponse res)
        throws IOException {
        res.setContentType ("text/xml");
        ServletOutputStream out = res.getOutputStream();
        try {
            if (ok(req.getParameter("clear"))) {
                SOAPDataSource.clearLoadInfo();
            }
            String service = req.getParameter("service");
            StringBuffer sb = new StringBuffer();
            if (service != null && ! service.equals("")) {
                SOAPDataSource.serviceXML(sb, service);
            } else {
                SOAPDataSource.toXML(sb);
            }
            out.println(sb.toString());
        } finally {
            FileUtils.close(out);
        }
    }

    @Override
    public MIME_Type getMimeType()
    {
        return MIME_Type.XML;
    }
}
