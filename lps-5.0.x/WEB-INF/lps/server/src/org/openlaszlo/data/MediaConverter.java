/* *****************************************************************************
 * MediaConverter.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.data;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.media.MimeType;
import org.openlaszlo.media.Transcoder;
import org.openlaszlo.media.TranscoderException;
import org.openlaszlo.utils.ChainedException;

/**
 * Media Converter
 */
public class MediaConverter extends Converter  {
    
    private static Logger mLogger  = Logger.getLogger(MediaConverter.class);

    /**
     * Convert incoming Media to SWF
     */
    @Override
    public InputStream convertToSWF(Data data, HttpServletRequest req,
                                    HttpServletResponse res)
        throws ConversionException, IOException {

        String surl = null;
        try {
            surl = DataSource.getURL(req);
        } catch (MalformedURLException e) {
            throw new ChainedException(e);
        }
        int index = surl.indexOf('?');
        String path = surl;
        if (index > 0) {
            path = surl.substring(0, index);
        }

        String mimeType = data.getMimeType();
        if (!Transcoder.canTranscode(mimeType, MimeType.SWF)) {
            // If we can't convert the content type, try from the extension
            mimeType = MimeType.fromExtension(path);
        }
    
        mLogger.debug(
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="calling transcoder on " + "mime type: " + p[0]
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                MediaConverter.class.getName(),"051018-62", new Object[] {mimeType})
);

        try {
            return Transcoder.transcode(data.getInputStream(), 
                mimeType, MimeType.SWF, /* do stream audio */ true);
        } catch (TranscoderException e) {
            throw new ConversionException(e.getMessage());
        } 
    }

    /**
     * media should be as is
     */
    @Override
    public String chooseEncoding(HttpServletRequest req) {
        return null;
    }

}
