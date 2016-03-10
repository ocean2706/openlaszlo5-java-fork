<%@ page pageEncoding="UTF-8"%>
<%@ page import="org.openlaszlo.utils.LZHttpUtils"%>
<%@ page contentType="text/html;charset=UTF-8" %><%
    String actionParam = request.getParameter("action");
    if (actionParam == null || actionParam.equals("undefined")){ actionParam = "";}
    String srcParam = request.getParameter("src");
    if (srcParam == null || srcParam.equals("undefined")){ srcParam = "/laszlo-explorer/loading_swf.html";}
    String lzrParam = LZHttpUtils.getLzOption("runtime", request);
    if (lzrParam == null || lzrParam.equals("undefined")){ lzrParam = "dhtml";}

    String appName = request.getContextPath();

    // Redirect instead of using a noop iframe, see LPP-9238
    if (actionParam.equals("")) {
        String redirectURL = appName + srcParam + "?lzt=html&lzr=" + lzrParam;
        response.sendRedirect(redirectURL); 
    }

    String dParam = "";
    dParam += request.getParameter("display");
    String qParams ="";

    Enumeration e = request.getParameterNames();
    while (e.hasMoreElements()) {
        String name = (String)e.nextElement();
        String value = request.getParameter(name);
            if (value == null ){ value = "";}
        qParams += "&" + name + "=" + value;
    }
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Frameset//EN"
        "http://www.w3.org/TR/html4/frameset.dtd">
<!-- * X_LZ_COPYRIGHT_BEGIN ***************************************************
* Copyright 2001-2010 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* X_LZ_COPYRIGHT_END ****************************************************** -->
    <%@ page import="java.util.*" %>
      <%@ page import="java.io.*" %>
      <html>
  <head>
    <title>OpenLaszlo</title>
    <META http-equiv="Content-Type" content="text/html; charset=UTF-8">
  </head>
	  <%
	    if (actionParam.equals("edit")){
	      if (dParam.equals("enhanced")){%>
	        <frameset rows="*" frameborder="0" border="0" framespacing="2">
	      <% } else {%>
	        <frameset rows="*" frameborder="1" border="4" framespacing="2">
		  <%}%>
		    <frame name="src" src="editor.jsp?<%= qParams %>"/>
		  </frameset>
	    <%} else if (actionParam.equals("explore_welcome")){ %>
                  <frameset rows="445,*" frameborder="1" border="4" framespacing="2">
                      <frameset cols="515,*" frameborder="1" border="4" framespacing="2">
                            <frame name="src" src="welcome.html?<%= qParams %>"/>
                            <frame name="swf" src="welcome_swf.html"/>
                      </frameset>
                      <frame name="doc" src="welcome_doc.html?<%= qParams %>">
                  </frameset>
	    <%} else if (actionParam.equals("explore")){   %>
                  <frameset rows="445,*" frameborder="1" border="4" framespacing="2">
                      <frameset cols="515,*" frameborder="1" border="4" framespacing="2">
                            <frame name="src" src="source.jsp?<%= qParams %>"/>
                            <frame name="swf" src="loading_swf.html"/>
                      </frameset>
                      <frame name="doc" src="doc.jsp?<%= qParams %>">
                  </frameset>
		<%} else if (actionParam.equals("source")){%>
		    <frameset rows="*" frameborder="0" border="0" framespacing="0">
		      <frame src="<%= appName %>/lps/utils/viewer/viewer.jsp?file=<%=srcParam %>"/>
		    </frameset>
		<%}%>
</html>
