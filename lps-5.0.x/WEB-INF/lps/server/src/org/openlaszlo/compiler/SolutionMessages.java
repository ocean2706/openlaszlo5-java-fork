/* *****************************************************************************
 * SolutionMessages.java
* ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2009, 2011 Laszlo Systems, Inc.  All Rights Reserved.  *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**  
 * Looks up error messages in a database of known errors, and suggests solutions
 *
 * @author Henry Minsky
 */

class SolutionMessages {

    /**
     * Return true if the regexp pattern is found to occur in the input string.
     * @param input the input string
     * @param p the pattern
     */
    private static boolean regexp (String input, String p) {
        try {
            Pattern pattern = Pattern.compile(p);
            return pattern.matcher(input).find();
        } catch (PatternSyntaxException e) {
            // We should probably print something to a debug log or something to mention that the pattern we tested
            // threw an error and probably has some bogus regexp syntax.
            return false;
        }
    }

    // We classify the error messages into several areas, to make it
    // easier to give a specific solution that might apply.
    static final String PARSER       = "PARSER";
    static final String VALIDATOR    = "VALIDATOR";
    static final String VIEWCOMPILER = "VIEWCOMPILER";

    static final ArrayList<SolutionMessage> errs = new ArrayList<SolutionMessage>();

    static {
        errs.add(new SolutionMessage(PARSER,
                                     // This error message indicates a stray XML escape character
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="The content of elements must consist of well-formed character data or markup"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-101")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Look for stray or unmatched XML escape chars ( <, >, or & ) in your source code. When using '<' in a Script, XML requires wrapping the Script content with '<![CDATA[' and ']]>'. "
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-108")
                                                                        ));

        errs.add(new SolutionMessage(PARSER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="entity name must immediately follow the '&' in the entity reference"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-117")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Look for unescaped '&' characters in your source code."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-124")
                                                                        ));
                                     
        errs.add(new SolutionMessage(VIEWCOMPILER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Was expecting one of: instanceof"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-133")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="The element and attribute names in .lzx files are case-sensitive; Look for a mistake in the upper/lower case spelling of attribute names, i.e., \"onClick\" instead of \"onclick\""
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-140")
                                                                        ));


        errs.add(new SolutionMessage(PARSER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="\"\" is not a valid local name"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-150")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Make sure that every <class> and <attribute> tag element contains a non-empty 'name' attribute, and each <method> element contains a non-empty 'name' or 'event' attribute."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-157")
                                                                        ));

        errs.add(new SolutionMessage(PARSER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="The root element is required in a well-formed document"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-166")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Check for invalid UTF8 characters in your source file. LZX XML files may contain only legal UTF-8 character codes. If you see a character with an accent over it, it might be the problem."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-173")
                                                                        ));

        errs.add(new SolutionMessage(PARSER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Content is not allowed in prolog"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-182")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Some text editors may insert a Byte-Order Mark (the sequence of characters 0xEFBBBF) at the start of your source file without your knowledge. Please remove any non-whitespace characters before the start of the first '<' character."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-189")
                                                                        ));

        errs.add(new SolutionMessage(PARSER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="The reference to entity.*must end with the"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-198")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Look for a misplaced or unescaped ampersand ('&') character in your source code."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-205")
                                                                        ));


        errs.add(new SolutionMessage(VIEWCOMPILER, 
                                     "unable to build font",
                                     "The font may be a bold or italic style, try adding style=\"bold\" or \"italic\" attribute to font tag"));

        errs.add(new SolutionMessage(VIEWCOMPILER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Lexical error.  The source location is for the element that contains the erroneous script"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-215")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Examine the compiler warnings for warnings about undefined class or attribute names."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-222")
                                     ));

        errs.add(new SolutionMessage(PARSER,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Error in building"
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-231")
,
/* (non-Javadoc)
 * @i18n.test
 * @org-mes="Check for invalid UTF8 characters in your source file. LZX XML files may contain only legal UTF-8 character codes. If you see a character with an accent over it, it might be the problem."
 */
                        org.openlaszlo.i18n.LaszloMessages.getMessage(
                                SolutionMessages.class.getName(),"051018-238")
                                                                        ));




    }


    /** Look through the list of known error message strings, looking for a match,
     * and return the suggested solution.
     *
     * @param err an error message you want to look up the solution for
     * @param type the class of error (parser, validator, viewcompiler), or null for any match
     *
     * @return a solution message if available, otherwise the empty string
    */
    static String findSolution (String err, String type) {
        for (int i = 0; i < errs.size(); i++) {
            SolutionMessage msg = errs.get(i);
            // Look for match of the err message within our error string
            if ((type == null || msg.errType.equals(type)) && regexp(err, msg.errMessage)) {
                return msg.solution;
            }
        }
        return "";
    }

    /** Find matching solution from the first  matching solution pattern */
    static String findSolution (String err) {
        return findSolution(err, null);
    }

    private static class SolutionMessage {
        String errType;
        String errMessage;
        String solution;

        SolutionMessage(String type, String err, String sol) {
            errType = type;
            errMessage = err;
            solution = sol;
        }
    }
}



