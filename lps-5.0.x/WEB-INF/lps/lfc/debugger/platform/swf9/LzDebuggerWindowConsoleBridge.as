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

    /**
     * Doc'd on interface
     * Bootstrap version
     * @access private
     *
     * @devnote Should probably be called addMessage
     */
    override function addText (msg) {
      this.DebugWindow.addText(msg);
    }

    /**
     * Doc'd on interface
     * Bootstrap version
     * @access private
     */
    override function clear () {
      this.DebugWindow.clearWindow()
    };

    /**
     * @access private
     */
    override function ensureVisible () {
      this.DebugWindow.ensureVisible();
    };

    /**
     * Doc'd on interface
     * Bootstrap version
     * @access private
     */
    override function echo (str, newLine:Boolean=true) {
      this.DebugWindow.echo(str, newLine);
    }

    /**
     * Doc'd on interface
     * Bootstrap version
     * @access private
     */
    override function addHTMLText (msg) {
      this.DebugWindow.addHTMLText(msg);
    };

    /**
     * Doc'd on interface
     *
     * Convert style as best we can to what AS3 text element understands
     *
     * @access private
     */
  override function makeObjectLink (rep:String, id:*, attrs=null):String {
    var open = '';
    var close = '';
    // if (attrs && attrs['type']) {
    //   open = open + '<span class="' + attrs.type + '">';
    //   close = '</span>' + close;
    // }
    var color = (attrs && attrs['color']) ? attrs.color : '';
    var style = null;
    if (attrs && attrs['style']) {
      style = lz.Type.acceptTypeValue('css', attrs.style, null, null);
      if ('color' in style) {
        color = style.color;
      }
    }
    if (id != null) {
      if (! color) { color = '#0000ff'; }
      // Format the hyperlink using the as3 textlink event API
      open = open + '<a href="event:objid=' + id + '">';
      close = '</a>' + close;
    }
    if (color || style) {
      if (color) {
        var lzu = lz.ColorUtils;
        color = ' color="' + lzu.cssfrominternal(lzu.internalfromcss(color), '#') + '"';
      }
      var face = '', size = '';
      if (style) {
        if (style.fontFace) { face = ' face="' + style.fontFace + '"'; }
        if (style.fontSize) { size = ' size="' + style.fontSize + '"'; }
        if (style.fontStyle == 'italic') {
          open = open + '<i>';
          close = '</i>' + close;
        }
        if (style.fontWeight == 'bold') {
          open = open + '<b>';
          close = '</b>' + close;
        }
      }
      open = open + '<font' + color + face + size + '>';
      close = '</font>' + close;
    }
    return open + rep + close;
  }

    /** @access private */
    var evalcount = 0;

    /**
     * Doc'd on interface
     * Bootstrap version
     * @access private
     */
    override function doEval (expr:String) {
      if (this.isSimpleExpr(expr)) {
        var simple = true;
        try {
          var val = this.evalSimpleExpr(expr);
          // If we got no value, maybe the expression was not simple
          if (val === void 0) {
            simple = false;
          }
        } catch (e) {
          // If we get an error, surely the expression must not be simple
          simple = false;
        }
      }
      if (simple) {
        Debug.displayResult(val);
      } else {
        try {
          // remoteEval includes call to displayResult
          this.DebugWindow.remoteEval(expr, this.evalcount++);
        } catch (e) {
          Debug.error("%s: evaluating %s", e, expr);
        }
      }
    }

  /**
   * @access private
   */
  override function contextMenuItems (menu:LzContextMenu, target:LzView):Array {
    return this.DebugWindow.contextMenuItems(menu, target);
   }
}
