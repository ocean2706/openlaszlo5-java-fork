/* *****************************************************************************
 * CompilationErrorHandler.java
 * ****************************************************************************/

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2006, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

package org.openlaszlo.compiler;

import java.util.Iterator;
import java.util.List;
import java.util.Vector;

/** Hold a list of errors generated during compilation of an lzx file.
 *
 * The list of Errors are all wrapped by CompilationError
 */
public class CompilationErrorHandler {
    /** List of errors, these should all be of type CompilationError  */
    private List<CompilationError> errors = new Vector<CompilationError>();
    protected String fileBase = "";
    
    public CompilationErrorHandler() {
    }
    
    /** Set the base directory relative to which pathnames are
     * reported. */
    public void setFileBase(String fileBase) {
        this.fileBase = fileBase;
    }
    
    /** Append an error to list of errors.
     * @param e the error which occurred
     */
    void addError(CompilationError e) {
        e.setFileBase(fileBase);
        errors.add(e);
    }

    /** Returns the list of errors
     * @return the list of errors
     */
    public List<CompilationError> getErrors()
    {
        return errors;
    }

    /**
     * @return length of error list */
    public int size()
    {
        return errors.size();
    }

    public boolean isEmpty() {
        return errors.isEmpty();
    }

    /**
     * Appends all the errors from ERRS into our list of errors.
     * @param other a list of errors to append from
     */
    public void appendErrors(CompilationErrorHandler other) {
        for (CompilationError error : other.getErrors()) {
            errors.add(error);
        }
    }

    /**  @return a single consolidated error which holds list of error
        message strings which were collected during a compile.
     */
    public CompilationError toCompilationError() {
        StringBuilder buffer = new StringBuilder();
        for (Iterator<CompilationError> iter = errors.iterator(); iter.hasNext(); ) {
            CompilationError error = iter.next();
            buffer.append(error.getMessage());
            if (iter.hasNext()) {
                buffer.append('\n');
            }
        }
        return new CompilationError(buffer.toString());
    }
    
    public String toXML() {
        StringBuilder buffer = new StringBuilder();
        for (Iterator<CompilationError> iter = errors.iterator(); iter.hasNext(); ) {
            CompilationError error = iter.next();
            buffer.append(error.toXML());
            if (iter.hasNext()) {
                buffer.append("<br/>");
            }
        }
        return buffer.toString();
    }
}
