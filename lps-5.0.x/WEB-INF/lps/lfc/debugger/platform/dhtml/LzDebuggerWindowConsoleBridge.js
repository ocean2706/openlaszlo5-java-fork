/* -*- c-basic-offset: 2; -*- */

/*
 * Bridge to forward console API calls to the GUI debugger window view
 *
 * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.
 *            Use is subject to license terms.
 */

class LzDebuggerWindowConsoleBridge extends LzDebugConsole {

  var DebugWindow;

  function LzDebuggerWindowConsoleBridge (view) {
    super();
    this.DebugWindow = view;
  }

  /** @access private */
  override function canvasConsoleWindow () {
    return this.DebugWindow;
  }

  /** @access private */
  var __reNewline = RegExp('&#xa;|&#xA;|&#10;|\\n', 'g');

  /**
   * @access private
   */
  override function addHTMLText (str) {
    // Debug output expects whitespace: pre, which is not how <text> works
    // IE does not display \n in white-space: pre, so we translate...
    this.DebugWindow.addHTMLText("<span style='white-space: pre'>" + str.replace(this.__reNewline, '<br />') + "</span>");
  }

  /**
   * @access private
   */
  override function clear () {
    this.DebugWindow.clearWindow();
  };

  /**
   * @access private
   */
  override function ensureVisible () {
    this.DebugWindow.ensureVisible();
  };

  /**
   * @access private
   */
  override function echo (str, newLine:Boolean=true) {
    // This color should be a debugger constant -- maybe there should
    // be LzEcho extends LzSourceMessage
    this.addHTMLText('<span style="color: #00cc00">' + str + '</span>' + (newLine?'\n':''));
  }

  /**
   * @access private
   */
  override function makeObjectLink (rep:String, id:*, attrs=null):String {
    var tip = '';
    var onclick = '';
    var color = (attrs && attrs['color']) ? (' color: ' + attrs.color +';') : ' color: #0000ff;';
    var decoration = (attrs && attrs['type']) ? ' text-decoration: none;' : '';
    var style = (attrs && attrs['style']) ? (' style="' + attrs.style + '"') : '';
    if (id != null) {
      if (style == '') {
        style = 'style="cursor: pointer; ' + decoration + color + '"';
      }
      try {
        var tip = Debug.formatToString(' title="Inspect %0.32#w"', Debug.ObjectForID(id)).toString().toHTML();
      } catch (e) {}
      // This ends up being inserted into the debugger output div.
      // `$modules.lz` should be visible as a global there
      onclick = ' onclick="$modules.lz.Debug.objectLinkHandler(event, ' + id + ')"';
    }
    if (onclick != '' || style != '') {
      return '<span ' + tip + onclick + style + '>' + rep + '</span>';
    }
    return rep;
  };


  /**
   * @access private
   */
  override function doEval (expr:String) {
#pragma "warnUndefinedReferences=false"
    try {
      with (Debug.environment) {
        // Evaluate as expression first
        var value = eval('(' + expr + ')');
      }
      Debug.displayResult(value);
    }
    catch (e) {
      if (! (e is SyntaxError)) {
        Debug.error("%s", e);
        return;
      }

      // Not an expression, see if it is a statement
      try {
        with (Debug.environment) {
          var value = eval(expr);
        }
        Debug.displayResult(value);
      }
      catch (e) {
        Debug.error("%s", e);
      }
    }
  };

  /**
   * @access private
   */
  override function contextMenuItems (menu:LzContextMenu, target:LzView):Array {
    return this.DebugWindow.contextMenuItems(menu, target);
   }
}
