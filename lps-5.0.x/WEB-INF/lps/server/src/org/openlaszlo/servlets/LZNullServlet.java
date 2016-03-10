/* *****************************************************************************
 * LZNullServlet.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.servlets;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * LZNullServlet is a simple servlet for testing Tomcat performance.
 */
@SuppressWarnings("serial")
public class LZNullServlet extends HttpServlet {

    @Override
    public void init (ServletConfig config) throws ServletException 
    {
        super.init (config);

        log("Initializing LZNullServlet!");

    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) 
        throws IOException
    {
        res.setContentType ("text/html");
        ServletOutputStream  out = res.getOutputStream();

        out.println ("<html><head><title>X</title></head><body></body></html>");
        out.close();
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse res) 
        throws IOException
    {
        doGet(req, res);
    }
}

