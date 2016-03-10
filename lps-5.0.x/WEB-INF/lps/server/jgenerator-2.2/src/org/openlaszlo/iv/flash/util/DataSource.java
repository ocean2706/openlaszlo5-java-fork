/*
 * $Id: DataSource.java,v 1.3 2002/04/26 05:23:11 skavish Exp $
 *
 * ===========================================================================
 *
 * The JGenerator Software License, Version 1.0
 *
 * Copyright (c) 2000 Dmitry Skavish (skavish@usa.net). All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *    "This product includes software developed by Dmitry Skavish
 *     (skavish@usa.net, http://www.flashgap.com/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The name "The JGenerator" must not be used to endorse or promote
 *    products derived from this software without prior written permission.
 *    For written permission, please contact skavish@usa.net.
 *
 * 5. Products derived from this software may not be called "The JGenerator"
 *    nor may "The JGenerator" appear in their names without prior written
 *    permission of Dmitry Skavish.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL DMITRY SKAVISH OR THE OTHER
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.openlaszlo.iv.flash.util;

import java.io.*;

/**
 * Simple text datasource.<P>
 * Reads data from given {@link LineReader} line by line
 * and construct 2 dimensional array of Strings
 *
 * @author Dmitry Skavish
 * @see DataSourceHelper
 * @see XMLDataSource
 * @see Tokenizer
 */
public class DataSource {

    private LineReader reader;

    public DataSource() {}

    public DataSource( LineReader reader ) {
        setReader( reader );
    }

    protected void setReader( LineReader reader ) {
        this.reader = reader;
    }

    public String[][] getData() throws IOException {
        IVVector lines = new IVVector();
        IVVector curLine = new IVVector();

        String line = reader.readLine();
        while( line != null ) {
            curLine.reset();

            String token;
            Tokenizer t = new Tokenizer(line);
            while( (token=t.getToken()) != null ) {
                curLine.addElement( Util.processEscapes(token) );
            }

            if( curLine.size() > 0 ) {
                int size = curLine.size();
                if( lines.size() > 0 ) {
                    int s = ((String[])lines.elementAt(0)).length;
                    if( s > size ) size = s;
                }
                String[] sa = new String[size];
                curLine.copyInto(sa);
                for( int k=curLine.size(); k<size; k++ ) { sa[k] = ""; }
                lines.addElement(sa);
            }

            line = reader.readLine();
        }

        String[][] data = new String[lines.size()][];
        lines.copyInto(data);
        return data;
    }

/*    public static void main( String[] args ) {
        try {
            DataSource ds = new DataSource( new InputStreamLineReader( new FileInputStream(args[0]) ) );
            String[][] data = ds.getData();
            for( int i=0; i<data.length; i++ ) {
                String[] row = data[i];
                for( int j=0; j<row.length; j++ ) {
                    System.out.print( "'"+row[j]+"'" );
                    if( j+1<row.length ) System.out.print( "," );
                    else System.out.println();
                }
            }
        } catch( Exception e ) {
            System.out.println( e.getMessage() );
            e.printStackTrace();
        }
    }
*/
}