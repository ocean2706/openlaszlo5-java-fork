/******************************************************************************
 * ResponderPROFILE.java
 * ****************************************************************************/

package org.openlaszlo.servlets.responders;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;

import javax.servlet.SingleThreadModel;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.openlaszlo.utils.LZHttpUtils;

// We don't expect a lot of use of this, for simplicity, we make it
// single-threaded (so the open file is shared without collisions)
public final class ResponderPROFILE extends Responder implements SingleThreadModel
{
  private static Logger mLogger = Logger.getLogger(ResponderPROFILE.class);
  private static OutputStream output;

  private HashMap<Integer, ByteArrayOutputStream> datachunks;

  @Override
  protected void respondImpl(HttpServletRequest req, HttpServletResponse res) throws IOException {
      synchronized(this) {
          String command = req.getParameter("command");
          String seqparam = req.getParameter("seqnum");

          mLogger.info("PROFILE_OUTPUT = " + command+ " [" +seqparam != null ? seqparam : "] " + output);
          if (output != null) {
              if ("data".equals(command)) {
                  mLogger.info("PROFILE_DATA");

                  Integer seqnum = 0;
                  try {
                      seqnum = Integer.parseInt(seqparam);
                  } catch (NumberFormatException e) {
                      mLogger.info("PROFILE couldn't parse seqnum: "+seqparam);
                  }

                  ByteArrayOutputStream dout = new ByteArrayOutputStream();
                  // Create a data chunk for this sequence number
                  this.datachunks.put(seqnum, dout);
                  String pdata = req.getParameter("pdata");
                  byte data[] = pdata.getBytes();
                  // Copy the data into the chunk 
                  mLogger.info("PROFILE_DATA storing chunk #"+seqnum+", write len: " + data.length);
                  dout.write(data, 0, data.length);
                  respondWithMessage(res, "Data");
              } else if ("close".equals(command)) {
                  mLogger.info("PROFILE_CLOSE");

                  String nitems = req.getParameter("nchunks");
                  int nchunks = 0;

                  try {
                      nchunks = Integer.parseInt(nitems);
                  } catch (NumberFormatException e) {
                      mLogger.info("PROFILE couldn't parse nchunks arg: "+nitems);
                  }
                  mLogger.info("PROFILE client close expects "+nitems+" data chunks");

                  // Copy data chunks in sequence order. Count index from zero
                  // up to the number of expected messages.
                  int k = 0;
                  for (k = 0; k < nchunks; k++) {
                      Integer i = Integer.valueOf(k);
                      if (!datachunks.containsKey(i)) {
                          mLogger.info("PROFILE missing data chunk "+k+", expected "+nchunks);
                          continue;
                      }
                      ByteArrayOutputStream b = datachunks.get(i);
                      byte bdata[] = b.toByteArray();
                      // copy message to output stream
                      mLogger.info("PROFILE_DATA write chunk #"+i+", length:" + bdata.length);
                      output.write(bdata, 0, bdata.length);
                  }

                  output.flush();
                  output.close();
                  output = null;
                  respondWithMessage(res, "Closed");
              }
          } else if ("open".equals(command)) {
              // getRealPath?
              String uri = LZHttpUtils.getRealPath(mContext, req);
              this.datachunks = new HashMap<Integer, ByteArrayOutputStream>();

              // try to clean up a little 
              try {
                  if (output != null) {
                      output.close();
                  }
              } catch (Exception e) {
              }
              
              // Modicum of security: Only profile apps that exist
              if ((new File(uri)).exists()) {
                  uri = uri.substring(0, uri.lastIndexOf('.'));
                  File file = new File (uri + ".profiler");
                  mLogger.info("PROFILE_OPEN " + file);
                  file.createNewFile();
                  output = new FileOutputStream(file);
                  respondWithMessage(res, "Open");
              }
          }
      }
  }

  @Override
  public MIME_Type getMimeType() {
    return MIME_Type.XML;
  }
}

/*
 * Copyright 2006, 2011 Laszlo Systems, Inc.  All Rights Reserved.  Use is
 * subject to license terms.
 */


