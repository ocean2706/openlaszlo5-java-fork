/**
 * Parser support for literals
 */

package org.openlaszlo.sc.parser;

public class ASTLiteral extends SimpleNode {

    private Object mValue = null;

    public SimpleNode deepCopy() {
        ASTLiteral result = (ASTLiteral)super.deepCopy();
        result.mValue = this.mValue; // we expect that mValue is immutable once configured
        return result;
    }

    public ASTLiteral(int id) {
        super(id);
    }

    public ASTLiteral(Parser p, int id) {
        super(p, id);
    }

    public static Node jjtCreate(int id) {
        return new ASTLiteral(id);
    }

    public static Node jjtCreate(Parser p, int id) {
        return new ASTLiteral(p, id);
    }

    // Added
    public ASTLiteral(Object value) {
        mValue = value;
    }

    public Object getValue() {
        return mValue;
    }

    static final int hexval(char c) {
        switch(c) {
        case '0' :
            return 0;
        case '1' :
            return 1;
        case '2' :
            return 2;
        case '3' :
            return 3;
        case '4' :
            return 4;
        case '5' :
            return 5;
        case '6' :
            return 6;
        case '7' :
            return 7;
        case '8' :
            return 8;
        case '9' :
            return 9;

        case 'a' :
        case 'A' :
            return 10;
        case 'b' :
        case 'B' :
            return 11;
        case 'c' :
        case 'C' :
            return 12;
        case 'd' :
        case 'D' :
            return 13;
        case 'e' :
        case 'E' :
            return 14;
        case 'f' :
        case 'F' :
            return 15;
        }

        throw new RuntimeException("Illegal hex or unicode constant");
        // Should never come here
    }

    static final int octval(char c) {
        switch(c) {
        case '0' :
            return 0;
        case '1' :
            return 1;
        case '2' :
            return 2;
        case '3' :
            return 3;
        case '4' :
            return 4;
        case '5' :
            return 5;
        case '6' :
            return 6;
        case '7' :
            return 7;
        }

        throw new RuntimeException("Illegal octal constant");
        // Should never come here
    }

    public void setStringValue(String image) {
        int l = image.length();
        StringBuilder sb = new StringBuilder(l);
        for (int i=0; i<l; i++) {
            char c = image.charAt(i);
            if ((c == '\\') && (i+1<l)){
                i++;
                c = image.charAt(i);
                if (c=='n') c='\n';
                else if (c=='b') c = '\b';
                else if (c=='f') c = '\f';
                else if (c=='r') c = '\r';
                else if (c=='t') c = '\t';
                else if (c=='v') c = '\u000B';
                else if (c =='x') {
                    c = (char)(hexval(image.charAt(i+1)) << 4 |
                               hexval(image.charAt(i+2)));
                    i +=2;
                } else if (c =='u') {
                    c = (char)(hexval(image.charAt(i+1)) << 12 |
                               hexval(image.charAt(i+2)) << 8 |
                               hexval(image.charAt(i+3)) << 4 |
                               hexval(image.charAt(i+4)));
                    i +=4;
                } else if (c >='0' && c <= '7') {
                    // Accepts anything from 0 - 377
                    c = (char)(octval(image.charAt(i)));
                    i++;
                    for (int j = i + ((int)c <= 3 ? 2 : 1);
                         (i < j) &&
                             (i < l) &&
                             (image.charAt(i)>='0') && (image.charAt(i)<='7');
                         i++) {
                        c = (char) ((c<<3) | octval(image.charAt(i)));
                    }
                } else if (c == '\n') {
                    continue;
                } else if (c == '\r') {
                    if ((i + 1 < l) && image.charAt(i + 1) == '\n') {
                        i++;
                    }
                    continue;
                }
            }
            sb.append(c);
        }
        mValue = sb.toString();
    }

    public void setRegexpValue(String image) {
        mValue = "" + image;
    }

    public void setDecimalValue(String image) {
        try {
            mValue = Long.valueOf(image);
        } catch (NumberFormatException e) {
            mValue = Double.valueOf(image);
        }
    }

    public void setOctalValue(String image) {
        try {
            String imageWithout0 = image.substring(1);
            mValue = Long.valueOf(imageWithout0,8);
        } catch (NumberFormatException e) {
            // FIXME: Double.valueOf() doesn't accept octal values, but OTOH
            // ES5 strict prohibits octal integer literals, so I won't bother
            // fixing this for now
            mValue = Double.valueOf(image);
        }
    }

    public void setHexValue(String image) {
        try {
            String imageWithout0x = image.substring(2);
            mValue = Long.valueOf(imageWithout0x,16);
        } catch (NumberFormatException e) {
            String imageWithBinExp = image + "p0";
            mValue = Double.valueOf(imageWithBinExp);
        }
    }

    public void setFloatingPointValue(String image) {
        mValue = Double.valueOf(image);
    }

    public void setBooleanValue(boolean value) {
        mValue = Boolean.valueOf(value);
    }

    public void setNullValue() {
        mValue = null;
    }

    public String toString() {
        if (mValue == null) {
            return "null";
        }
        return "Literal(" + mValue.toString() + ")";
    }

    /** Accept the visitor */
    public Object jjtAccept(ParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

}

/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2001-2008, 2011 Laszlo Systems, Inc.  All Rights Reserved.        *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

