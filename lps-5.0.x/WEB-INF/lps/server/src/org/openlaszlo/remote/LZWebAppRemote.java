/* ****************************************************************************
 * LZWebAppRemote.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2004, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.remote;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

public class LZWebAppRemote
{
    public static Object getAttribute(String name, HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        return context.getAttribute(name);
    }

    public static Vector<String> getAttributeNames(HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        Vector<String> v = new Vector<String>();
        Enumeration<?> e = context.getAttributeNames();
        while (e.hasMoreElements()) {
            v.add((String) e.nextElement());
        }
        return v;
    }

    public static int getMajorVersion(HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        return context.getMajorVersion();
    }

    public static int getMinorVersion(HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        return context.getMinorVersion();
    }

    public static String getMimeType(String file, HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        return context.getMimeType(file);
    }

    public static String getServerInfo(HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        return context.getServerInfo();
    }

    public static String getServletContextName(HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        return context.getServletContextName();
    }

    public static void log(String msg, HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        context.log(msg);
    }

    public static void removeAttribute(String name, HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        context.removeAttribute(name);
    }

    public static void setAttribute(String name, String value, HttpServletRequest req) {
        _setAttribute(name, value, req);
    }

    public static void setAttribute(String name, int value, HttpServletRequest req) {
        _setAttribute(name, value, req);
    }

    public static void setAttribute(String name, double value, HttpServletRequest req) {
        _setAttribute(name, new Double(value), req);
    }

    public static void setAttribute(String name, float value, HttpServletRequest req) {
        _setAttribute(name, new Float(value), req);
    }

    public static void setAttribute(String name, Vector<?> value, HttpServletRequest req) {
        _setAttribute(name, value, req);
    }

    public static void setAttribute(String name, Hashtable<?, ?> value, HttpServletRequest req) {
        _setAttribute(name, value, req);
    }

    static void _setAttribute(String name, Object value, HttpServletRequest req) {
        ServletContext context = req.getSession().getServletContext();
        context.setAttribute(name, value);
    }
}
