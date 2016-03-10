/* -*- mode: c-basic-offset: 2; -*- */

/*
 * Compiled with the flex compiler and added to WEB-INF/flexlib for
 * use by $lzc$subclassof
 *
 * @copyright Copyright 2011 Laszlo Systems, Inc.  All Rights Reserved.
 *            Use is subject to license terms.
 */
package avmplus {
  public class JSONReflection {
    use namespace AS3;
    public static function getClassInterfaces (o:*) :Array {
      return describeTypeJSON(o, INCLUDE_INTERFACES | INCLUDE_TRAITS | USE_ITRAITS).traits.interfaces;
    }
  }
}
