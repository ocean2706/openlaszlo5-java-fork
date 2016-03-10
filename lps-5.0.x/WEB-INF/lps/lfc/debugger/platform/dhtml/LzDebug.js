/* -*- mode: JavaScript; c-basic-offset: 2; -*- */

//* A_LZ_COPYRIGHT_BEGIN ******************************************************
//* Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.            *
//* Use is subject to license terms.                                          *
//* A_LZ_COPYRIGHT_END ********************************************************

/**
 ** Platform-specific implementation of debug I/O
 **/

class LzDHTMLDebugConsole extends LzBootstrapDebugConsole {
  /** The HTML debug window
   * @access private
   */
  var DebugWindow = null;

  /** @access private */
  var __reNewline = RegExp('&#xa;|&#xA;|&#10;|\\n', 'g');

  function LzDHTMLDebugConsole (iframe) {
    super();
    this.DebugWindow = iframe;
  };

  /**
   * @access private
   */
  override function addHTMLText (str) {
    var dw = this.DebugWindow;
    var dwd = dw.document;
    var span = dwd.createElement('span');
    var dwdb = dwd.body;
    // IE does not display \n in white-space: pre, so we translate...
    span.innerHTML = '<span class="OUTPUT">' + str.replace(this.__reNewline, '<br />') + '</span>';
    //console.log('addHTMLText',dwdb, str, span);
    dwdb.appendChild(span);
    // Scroll to end
    dw.scrollTo(0, dwdb.scrollHeight);
  };

  /**
   * Clear the console
   */
  override function clear () {
    var dw = this.DebugWindow;
    dw.document.body.innerHTML = '';
  };

  /**
   * Echo to the console in the debugger font
   *
   * @param String str: what to echo
   * @param Boolean newLine: whether to echo a trailing newline,
   * default true
   *
   * @access private
   */
  override function echo (str:String, newLine:Boolean=true) {
    this.addHTMLText('<span class="DEBUG">' + str + '</span>' + (newLine?'\n':''));
  }

  /**
   * Evaluate an expression
   *
   * This is part of the console protocol because it may require using
   * the console link to compile the expression to be evaluated, which
   * is then loaded and executed.  The result is then displayed by
   * calling back to the debugger `displayResult` method
   *
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
  }

  /**
   * @access private
   */
  override function makeObjectLink (rep:String, id:*, attrs=null) {
    var type = (attrs && attrs['type']) ? (' class="' + attrs.type + '"') : ' class="INSPECT"';
    var tip = '';
    var onclick = '';
    var style = (attrs && attrs['style']) ? (' style="' + style + '"') : '';
    if (id != null) {
      try {
        var tip = Debug.formatToString(' title="Inspect %0.32#w"', Debug.ObjectForID(id)).toString().toHTML();
      } catch (e) {}
      // This ends up being inserted into the debugger output iframe.
      // We look up $modules in the parent it shares with the app.
      onclick = ' onclick="window.parent.$modules.lz.Debug.objectLinkHandler(event, ' + id + ')"';
    }
    if (onclick != '' || style != '') {
      return '<span' + type + tip + onclick + style + '>' + rep +"</span>";
    }
    return rep;
  };
};

/**
 ** Platform-specific DebugService
 **/

class LzDHTMLDebugService extends LzDebugService {
  /** @access private */
  var __reHTMLElement = RegExp("^\\[object HTML.+?Element\\]$");

  /**
   * Preserve any state created in the base service
   *
   * @access private
   */
  function LzDHTMLDebugService(base:LzDebugService) {
    super(base);
    var copy = {backtraceStack: true, uncaughtBacktraceStack: true, logger: true, console: true};
    for (var k in copy) {
      this[k] = base[k];
    }
  };

  /**
   * If the app has a custom wrapper that does not include the
   * debugger frame, create it on the fly.
   *
   * @access private
   */
  function createDebugIframe() {
    var debugurl =  lz.embed.options.serverroot + 'lps/includes/laszlo-debugger.html';
    var form = '<form id="dhtml-debugger-input" onsubmit="$modules.lz.Debug.doEval(document.getElementById(\'LaszloDebuggerInput\').value); return false" action="#">'
    var iframe = '<iframe id="LaszloDebugger" name="LaszloDebugger" src="' + debugurl + '" width="100%" height="200"></iframe>';
    var inputdiv = '<div><input id="LaszloDebuggerInput" style="width:78%;" type="text"/><input type="button" onclick="$modules.lz.Debug.doEval(document.getElementById(\'LaszloDebuggerInput\').value); return false" value="eval"/><input type="button" onclick="$modules.lz.Debug.clear(); return false" value="clear"/><input type="button" onclick="$modules.lz.Debug.bugReport(); return false" value="bug report"/></div></form>';
    var debugdiv = document.createElement('div');
    debugdiv.innerHTML = form + iframe + inputdiv;
    debugdiv.onmouseover = function (e) { 
        if (!e) e = global.window.event;
        e.cancelBubble = true;
        LzKeyboardKernel.setKeyboardControl(false, true); 
        return false;
    }
    var y = canvas.height - 230;
    // firefox likes the style applied this way
    debugdiv.setAttribute( 'style', 'position:absolute;z-index:10000000;top:' + y + 'px;width:100%;');
    canvas.sprite.__LZdiv.appendChild(debugdiv);
    // IE insists the style be applied this way
    var style = debugdiv.style;
    style.position = 'absolute';
    style.top = y;
    style.zIndex = 10000000;
    style.width = '100%';
    return global.window.frames['LaszloDebugger'];
  };

  /**
   * @access private
   * Instantiates an instance of the user Debugger window
   * Called last thing by the compiler when the app is completely loaded.
   */
  function makeDebugWindow () {
    for (var n in __ES3Globals) {
      var p = __ES3Globals[n];
      try {
        if (! (p is Function)) {
          if (! p._dbg_name) {
            p._dbg_name = n;
          }
        } else if (! Debug.functionName(p)) {
          p[Debug.FUNCTION_NAME] = n;
        }
      }
      catch (e) {
        //        Debug.debug("Can't name %w", name);
      }
    }
    // Make the real console.  This is only called if the user code
    // did not actually instantiate a <debug /> tag
    if (LzBrowserKernel.getInitArg('lzconsoledebug') == 'true') {
      // Create a DHTML iframe console
      this.attachDebugConsole(new LzDHTMLDebugConsole(this.createDebugIframe()));
    } else {
      // This will attach itself, once it is fully initialized.
      new lz.LzDebugWindow();
    }
//     // If we didn't succeed in attaching the debug console in
//     // construct, try now
//     if ((! (this.console is LzDHTMLDebugConsole)) &&
//         (navigator.platform != 'rhino')) {
//       this.attachDebugConsole(new LzDHTMLDebugConsole(this.createDebugIframe()));
//     }
  };

  /**
   ** Platform-specific extensions to presentation and inspection
   **/

  /**
   * Ensures inspection event stops bubbling
   *
   * @access private
   */
  function objectLinkHandler (event, id) {
    event.cancelBubble = true;
    if (event.stopPropagation) { event.stopPropagation(); }
    this.displayObj(id);
    return false;
  }

  /** @access private */
  function hasFeature(feature:String, level:String) {
    return (document.implementation &&
            document.implementation.hasFeature &&
            document.implementation.hasFeature(feature, level));
  }

  /**
   * tip o' the pin to osteele.com for the notation format
   * @param node: html-element
   * @return String: xpath-like string for the html-element
   * @access private
   */
  function nodeToString(node:*) :String {
    var name = node.nodeName || '';
    var type = node.nodeType;
    var path = name.toLowerCase();
    // If this is a sprite implementation node, use the sprite's
    // LZX path rather than the DOM path
    var sprite = node.owner;
    var spritedivpath;
    if ((sprite instanceof LzSprite) && (sprite.owner.sprite === sprite)) {
      for (var key in sprite) {
        if (sprite[key] === node) {
          spritedivpath = Debug.formatToString("%w/@sprite/@%s", sprite.owner, key);
          break;
        }
      }
    }
    if (type == 1) { // Node.ELEMENT_NODE
      var id = node.id;
      var cn = node.className;
      // Sprite div id's are redundant here, they are really for
      // browser debuggers
      if (id && (! spritedivpath)) {
        path += '#' + encodeURIComponent(id);
      } else if (cn) {
        var more = cn.indexOf(' ');
        if (more == -1) { more = cn.length; }
        path += '.' + cn.substring(0, more);
      }
    }
    if (spritedivpath) {
      return spritedivpath + ((path.length > 0) ? ('/' + path) : '');
    }
    var parent = node.parentNode;
    if (parent) {
        // See if there are any siblings of the same kind, at the same
        // time, calculate your (xpath) index
        var index, count = 0;
        for (var sibling = parent.firstChild; sibling; sibling = sibling.nextSibling) {
          if (type == sibling.nodeType && name == sibling.nodeName) {
            count++;
            // Optimization:  you can stop if your index is non-zero
            if (index) break;
          }
          if (node === sibling) { index = count; }
        }
        // Only need the index if name is ambiguous
        if (count > 1) {
          path += '[' + index + ']';
        }
      try {
        return this.nodeToString(parent) + '/' + path;
      } catch (e) {
        return '\u2026/' + path;
      }
    }
    return path;
  }

  /**
   * Adds handling of DOM nodes and MouseEvents to describer for DHTML
   *
   * @access private
   */
  override function __StringDescription (thing:*, escape:Boolean, limit:Number, readable:Boolean, depth:Number):Object {
    try {
      // Handle three cases:
      // 1) use HTMLElement if available (DOM Level 2 HTML conforming browsers)
      // 2) use Element if available and check constructor.toString() (IE8)
      //   (note: do not change to Object.prototype.toString.call(...) which
      //    is the common way to retrieve [[Class]], in IE8 this just returns
      //    '[object Object]', see LPP-9237 for a complete comparison)
      // 3) apply a simple heuristic to catch host objects (IE<8)
      //   (note: typeof returns 'object', but the 'constructor' property is not
      //    set on host objects in IE)
      if (thing != null
            && (!!global.window.HTMLElement ? (thing instanceof HTMLElement)
              : !!global.window.Element ? (thing instanceof Element && this.__reHTMLElement.test(thing.constructor.toString()))
              : (typeof(thing) == 'object' && !thing.constructor))
            && (! isNaN(Number(thing['nodeType'])))
          ) {
        // If this has local style, add that
        var style = thing.style.cssText;
        if (style != '') { style = '[@style="' + style + '"]'; }
        return {readable: false, description: this.nodeToString(thing) + style};
      } else if (this.hasFeature('mouseevents', '2.0') && (thing is global['MouseEvent'])) {
        var desc = thing.type;
        if (thing.shiftKey) {
          desc = 'shift-' + desc;
        }
        if (thing.ctrlKey) {
          desc = 'ctrl-' + desc;
        }
        if (thing.metaKey) {
          desc = 'meta-' + desc;
        }
        if (thing.altKey) {
          desc = 'alt-' + desc;
        }
        switch (thing.detail) {
          case 2: desc = 'double-' + desc; break;
          case 3: desc = 'triple-' + desc; break;
        }
        switch (thing.button) {
          case 1: desc += '-middle'; break;
          case 2: desc += '-right'; break;
        }
        return {readable: false, description: desc};
      }
    } catch (e) {}
    return super.__StringDescription(thing, escape, limit, readable, depth);
  };

  /**
   * @access private
   *
   * @devnote TODO: [2008-09-23 ptw] (LPP-7034) Remove public
   * declaration after 7034 is resolved
   */
  public override function functionName (fn, mustBeUnique) {
    if (fn is Function) {
      // JS1 constructors are Function
      if (fn.hasOwnProperty('tagname')) {
        var n = fn.tagname;
        if ((! mustBeUnique) || (fn === lz[n])) {
          return '<' + n + '>';
        } else {
          return null;
        }
      }
    }
    return super.functionName(fn, mustBeUnique);
  };


  /**
   * Adds unenumerable object properties for DHMTL runtime
   *
   * @access private
   */
  override function objectOwnProperties (obj:*, names:Array=null, indices:Array=null, limit:Number=Infinity, nonEnumerable:Boolean=false) {
    super.objectOwnProperties(obj, names, indices, limit, nonEnumerable);

    var proto = false;
    try {
      proto = ((obj['constructor'] && (typeof obj.constructor['prototype'] == 'object')) ?
               obj.constructor.prototype : false);
    } catch (e) {};
    if (names && nonEnumerable && proto) {
      // Unenumerable properties of some ECMA objects
      // TODO: [2006-04-11 ptw] enumerate Global/Number/Math/Regexp
      // object properties
      var unenumerated = {callee: true, length: true, constructor: true, prototype: true};

      for (var key in unenumerated) {
        var isown = false;
        try {
          isown = obj.hasOwnProperty(key);
        } catch (e) {};
        if (! isown) {
          var pk;
          try {
            pk = proto[key];
          } catch (e) {};
          isown = (obj[key] !== pk);
        }
        if (isown) {
          for (var i = 0, l = names.length; i < l; i++) {
            if (names[i] == key) {
              isown = false;
              break;
            }
          }
        }
        if (isown) { names.push(key); }
      }
    }
  }

  /**
   ** Platform-specific features
   **/

  /** @access private */
  var showingDivs = false;
  /**
   * Display outlines of all view sprite bounding boxes, clicking
   * on a view will print the view and parent chain to the debugger.
   * 
   * @param Boolean enable : enable or disable outlines
   * @access public
   */
  function showDivs (enable):void {
    if (enable == null) { enable = (! this.showingDivs); }
    LzSprite.prototype.__setCSSClassProperty('.lzdiv', 'outline', (this.showingDivs = enable) ? '1px dotted green' : '');
  };

  /** @access private */
  var showingClickDivs = false;
  /**
   * Make the DHTML clickdiv's visible (for debugging)
   */
  function showClickDivs (enable:Boolean):void {
    if (enable == null) { enable = (! this.showingClickDivs); }
    LzSprite.prototype.__setCSSClassProperty('.lzdiv', 'outline', (this.showingClickDivs = enable) ? '1px dashed red' : '');
  }
};

/**
 * The Debug singleton is created in compiler/LzBootstrapDebugService so
 * a primitive debugger is available during bootstrapping.  It is
 * replaced here with the more capable debugger
 *
 * @access private
 */
var Debug = new LzDHTMLDebugService(Debug);
/**
  * TODO: [2006-04-20 ptw] Remove when compiler no longer references
  * @access private
  */
var __LzDebug = Debug;

;(function () {
  if (lz.embed.browser.isIE) {
    Error.prototype.toString = function() { return (this.name +": "+this.message); }
  }
})()


