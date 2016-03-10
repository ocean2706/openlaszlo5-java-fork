/* *****************************************************************************
 * Comment.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2007, 2010-2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.js2doc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Comment {

    private static Logger logger = Logger.getLogger("org.openlaszlo.js2doc.Comment");

    private static final Pattern lineSeparatorPattern = Pattern.compile("\r\n?|\n");

    static private final Pattern commentLineStart = Pattern.compile("^\\s*/\\*((?:.)*)$");
    static private final Pattern commentLineEnd   = Pattern.compile("^((?:.)*)\\*/.*$");

    static private final Pattern contentsPattern = Pattern.compile("^\\s*\\*+(.*)$", Pattern.MULTILINE);
    static private final Pattern fieldLinePattern = Pattern.compile("^\\s*@(\\w+)\\s+(.*)$");
    static private final Pattern fieldPattern = Pattern.compile("^\\s*@(\\w+)\\s+(.*)$", Pattern.DOTALL);

    static public ArrayList<String> extractRawComments(String sourceCommentSequence) {

        String cr = System.getProperty("line.separator");

        Scanner sc = new Scanner(sourceCommentSequence);

        ArrayList<String> results = new ArrayList<String>();

        try {
            while (sc.hasNextLine()) {
                String sl = sc.nextLine();

                Matcher startMatcher = commentLineStart.matcher(sl);
                if (startMatcher.matches()) {

                    boolean inside = true;
                    String comment = "";
                    sl = startMatcher.group(1);

                    do {
                        Matcher stopMatcher = commentLineEnd.matcher(sl);
                        if (stopMatcher.matches()) {
                            comment += stopMatcher.group(1);
                            inside = false;
                        } else if (sc.hasNextLine()) {
                            comment += sl + cr;
                            sl = sc.nextLine();
                        } else {
                            throw new RuntimeException("Java comment not properly terminated");
                        }
                    } while (inside);

                    results.add(comment);
                }
            }
        } catch (RuntimeException exc) {

        }
        return results;
    }

    static public Comment extractJS2DocComment(String sourceComment) {
        Comment parsedComment = new Comment();

        if (sourceComment != null) {
            String content = contentsPattern.matcher(sourceComment).replaceAll("$1");
            String[] lines = lineSeparatorPattern.split(content);

            int i=0;
            String textContent = "";
            while (i < lines.length && ! fieldLinePattern.matcher(lines[i]).matches()) {
                textContent += "\n" + lines[i];
                i++;
            }
            textContent = textContent.trim();
            if (textContent.compareTo("") != 0) {
                parsedComment.textContent = textContent;
            }

            while (i < lines.length) {
                // lines[i] now matches fieldLinePattern, so it is the first line
                // of our new field string
                String field = lines[i];
                i++;

                // now collect any followup lines
                while (i < lines.length && ! fieldLinePattern.matcher(lines[i]).matches()) {
                    field += "\n" + lines[i];
                    i++;
                }

                // finally, extract the field name and value and add them to the doc node
                Matcher fieldMatcher = fieldPattern.matcher(field);
                boolean found = fieldMatcher.find();
                if (! found)
                    logger.warning("field text didn't match fieldPattern regex. Shouldn't happen");

                String fieldName = fieldMatcher.group(1);
                String fieldValue = fieldMatcher.group(2).trim();

                if (fieldName.equals("keywords") ||
                    fieldName.equals("keyword")) {
                    StringTokenizer keywordTokens = new StringTokenizer(fieldValue);
                    while (keywordTokens.hasMoreTokens()) {
                        parsedComment.keywords.add(keywordTokens.nextToken());
                    }
                } else {
                    parsedComment.addField(fieldName, fieldValue);
                }
            }
        }
        return parsedComment;
    }

    static public ArrayList<Comment> extractStructuredComments(String sourceCommentSequence) {

        ArrayList<String> rawComments = extractRawComments(sourceCommentSequence);

        ArrayList<Comment> processedComments = new ArrayList<Comment>();

        final int n = rawComments.size();
        for (int i=0; i<n; i++) {
            String thisComment = rawComments.get(i);

            // see if it matches the special structured comment format
            if (thisComment.charAt(0) == '*') {
                // it does -- extract the contents and save them.
                String js2docComment = thisComment.substring(1);
                Comment c = extractJS2DocComment(js2docComment);
                processedComments.add(c);
            }
        }

        return processedComments;
    }

    static public void appendStructuredCommentsAsXML(String sourceCommentSequence, org.w3c.dom.Node parentNode) {

        ArrayList<Comment> structuredComments = extractStructuredComments(sourceCommentSequence);

        final int n = structuredComments.size();
        for (int i=0; i<n; i++) {
            Comment thisComment = structuredComments.get(i);

            thisComment.appendAsXML(parentNode);
        }
    }

    static public Comment extractLastJS2DocFromCommentSequence(String sourceSequence) {

        String content = sourceSequence.trim();

        ArrayList<String> comments = extractRawComments(content);

        // find the properly-formatted comment that is closest to the end
        String lastComment = null;
        final int n = comments.size();
        for (int i=0; i<n; i++) {
            String thisComment = comments.get(i);

            if (lastComment != null) {
                logger.fine("Skipping js2doc-formatted comment without adjacent target");
                lastComment = null;
            }

            // see if it matches the special js2doc format
            if (thisComment.charAt(0) == '*') {
                // it does -- extract the contents and save them.
                String js2docComment = thisComment.substring(1);
                lastComment = js2docComment;
            }
        }

        return extractJS2DocComment(lastComment);
    }


    static public Comment extractFirstJS2DocFromCommentSequence(String sourceSequence) {

        String content = sourceSequence.trim();

        ArrayList<String> comments = extractRawComments(content);

        // find the first properly-formatted comment
        String firstComment = null;
        final int n = comments.size();
        for (int i=0; i<n; i++) {
            String thisComment = comments.get(i);

            // see if it matches the special js2doc format
            if (thisComment.charAt(0) == '*') {
                // it does -- extract the contents and save them.
                String js2docComment = thisComment.substring(1);
                firstComment = js2docComment;
                break;
            }
        }

        return extractJS2DocComment(firstComment);
    }

    String textContent;
    Set<String> keywords;
    List<FieldMap> fields;

    public class FieldMap {
        String key;
        String value;

        public FieldMap(String key, String value) {
            super();
            this.key = key;
            this.value = value;
        }

        public String getKey() { return this.key; }
        public String getValue() { return this.value; }
    };

    public Comment() {
        super();
        this.textContent = "";
        this.keywords = new HashSet<String>();
        this.fields = new ArrayList<FieldMap>();
    }

    public boolean isEmpty() {
        return (textContent.length() == 0) && keywords.isEmpty() && fields.isEmpty();
    }

    public void addField(String name, String value) {
        fields.add(new FieldMap(name, value));
    }

    public boolean hasField(String name) {
        boolean found = false;
        for (FieldMap fieldEntry : fields) {
            if (fieldEntry.getKey().compareTo(name) == 0) {
                found = true;
                break;
            }
        }
        return found;
    }

    public org.w3c.dom.Element appendAsXML(org.w3c.dom.Node parentNode) {
        org.w3c.dom.Document ownerDocument = parentNode.getOwnerDocument();

        org.w3c.dom.Element doc = (org.w3c.dom.Element) JS2DocUtils.firstChildNodeWithName(parentNode, "doc");
        if (doc == null) {
            doc = parentNode.getOwnerDocument().createElement("doc");
            parentNode.appendChild(doc);
        }

        if (this.textContent.length() > 0) {
            org.w3c.dom.Element textNode = (org.w3c.dom.Element) JS2DocUtils.firstChildNodeWithName(doc, "text");
            if (textNode == null) {
                textNode = ownerDocument.createElement("text");
                doc.appendChild(textNode);
            }
            JS2DocUtils.setXMLContent(textNode, this.textContent);
        }

        for (Comment.FieldMap entry : this.fields) {
            String fieldName = entry.getKey(),
                   fieldValue = entry.getValue();

            org.w3c.dom.Element fieldNode = ownerDocument.createElement("tag");
            doc.appendChild(fieldNode);
            fieldNode.setAttribute("name", fieldName.trim());
            org.w3c.dom.Element textNode = ownerDocument.createElement("text");
            fieldNode.appendChild(textNode);
            JS2DocUtils.setXMLContent(textNode, fieldValue);
        }

        String keywordsToAdd = "";
        for (String keyword : this.keywords) {
            if (keywordsToAdd.length() == 0) {
                keywordsToAdd = keyword;
            } else {
                keywordsToAdd += " " + keyword;
            }
        }
        if (keywordsToAdd.length() != 0) {
            doc.setAttribute("keywords", keywordsToAdd);
        }

        parentNode.appendChild(doc);
        return doc;
    }

};