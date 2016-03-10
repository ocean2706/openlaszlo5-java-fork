/**
  * LzMouseKernel.js
  *
  * @copyright Copyright 2007-2013 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic DHTML
  * @author Max Carlson &lt;max@openlaszlo.org&gt;
  */

// Receives mouse events from the runtime
var LzMouseKernel = {
    // the last sprite to receive onmousedown
    __lastMouseDown: null
    // the last sprite to receive onmouseover
    ,__lastMouseOver: null
    // the last sprite to receive onmouseout
    ,__lastMouseOut: null
    // the last sprite to receive onmousedown but from context
    ,__lastMouseContext: null
    // true if Mac context menu generated using ctrl-click
    ,__maccontextshown: false
    // the last sprite to receive onmousedown only for bug LPP-10350
    ,__lastMouseDownForIEClick: null
    // the last sprite to receive onmouseup only for bug LPP-10350
    ,__lastMouseUpForIEClick: null
    ,__x: 0
    ,__y: 0
    ,owner: null
    ,__showncontextmenu: null
    // handles global mouse events
    ,__mouseEvent: function(e) {
        // capture events in IE
        e = e || window.event;

        // IE calls `target` `srcElement`
        var target = e.target || e.srcElement;
        var eventname = 'on' + e.type;

        //Debug.debug('__mouseEvent', eventname, target);
        if (eventname == 'onmousedown')
            LzMouseKernel.__applicationFocus ();
 
        // send option/shift/ctrl key events
        if (window['LzKeyboardKernel'] && LzKeyboardKernel['__updateControlKeys']) {
            LzKeyboardKernel.__updateControlKeys(e);
        }
        if (LzSprite.prototype.capabilities.touchevents) {
//            var maxfingers = 1;
//            if (eventname === 'ontouchstart') {
//                eventname = 'onmousedown';
//            } else 
            if (eventname === 'ontouchmove') {
                eventname = 'onmousemove';
            } 
//            else if (eventname === 'ontouchend') {
//                eventname = 'onmouseup';
//                maxfingers = 0;
//            }
            if (e.touches.length != 1) {
                //Debug.warn('more than %w fingers for %w', %w, e.target.owner.owner);
                return true;
            }
        }

        var lzinputproto = window['LzInputTextSprite'] && LzInputTextSprite.prototype;
        if (lzinputproto && lzinputproto.__lastshown != null) {
            if (LzSprite.quirks.fix_ie_clickable) {
                lzinputproto.__hideIfNotFocused(eventname);
            } else if (eventname != 'onmousemove') {
                lzinputproto.__hideIfNotFocused();
            }
        }

        if (eventname === 'onmousemove') {
            LzMouseKernel.__sendMouseMove(e);
            // hide any active inputtexts to allow clickable to work - see LPP-5447...
            if (lzinputproto && lzinputproto.__lastshown != null) {
                if (target && target.owner && ! (target.owner instanceof LzInputTextSprite)) {
                    if (! lzinputproto.__lastshown.__isMouseOver()) {
                        lzinputproto.__lastshown.__hide();
                    }
                }
            }

        } else if (eventname === 'oncontextmenu' || e.button == 2) {
            if (lz.embed.browser.OS == 'Mac' && e.button != 2) {
                // LPP-10331. Safari can open context with a ctrl + click. 
                // The next mouse up must be ignored to keep context menu open.
                LzMouseKernel.__maccontextshown = true;
            }
            //Debug.debug("oncontextmenu: ", e.button, e);
            return LzMouseKernel.__handleContextMenu(e);
        } else {
            LzMouseKernel.__sendEvent(eventname);
        }
        // TODO [2010-9-23 max] Should we return false here?  It might cancel 
        // the event...
        //Debug.write('LzMouseKernel event', eventname);
    }
    // sends mouse events to the callback.  
    // Also called by sprites since most events don't bubble
    ,__sendEvent: function(eventname, view, value) {
        if (eventname === 'onclick' && view == null) {
            // don't send global onclick events
            return;
        }

        if (LzMouseKernel.__callback) {
            LzMouseKernel.__scope[LzMouseKernel.__callback](eventname, view, value);
        }
        if (eventname === 'onmouseup' && LzMouseKernel.__maccontextshown) {
            // Context menu checks __maccontextshown to ignore mouse event,
            // but it is reset here.
            LzMouseKernel.__maccontextshown = false;
        }
        //Debug.write('LzMouseKernel event', eventname);
    }
    ,__callback: null
    ,__scope: null

    ,setCallback: function (scope, funcname) {
        this.__scope = scope;
        this.__callback = funcname;
    }
    ,__mousecontrol: false
    // Called to register/unregister global mouse events.
    ,setMouseControl: function (ison) {
        if (ison === LzMouseKernel.__mousecontrol) return;
        //Debug.write('mousecontrol', ison);
        LzMouseKernel.__mousecontrol = ison;
        // Send appropriate global mouse event
        LzMouseKernel.__sendEvent(ison ? 'onmouseenter' : 'onmouseleave');

        // This is safe because attachEventHandler and removeEventHandler don't 
        // use 'this'
        var method =  lz.embed[ ison ? 'attachEventHandler' : 'removeEventHandler'];
        if (LzSprite.prototype.capabilities.touchevents) {
            // register for global touch events
            //method(document, 'touchstart', LzMouseKernel, '__mouseEvent');
            method(document, 'touchmove', LzMouseKernel, '__mouseEvent');
            //method(document, 'touchend', LzMouseKernel, '__mouseupEvent');
        } else {
            method(document, 'mousemove', LzMouseKernel, '__mouseEvent');
        }

        // TODO [2010-9-23 max] is this still needed?
        // Prevent context menus in Firefox 1.5 - see LPP-2678
        document.oncontextmenu = ison ? LzMouseKernel.__mouseEvent : null;
    }    
    ,__showhand: 'pointer'

    /**
    * Shows or hides the hand cursor for all clickable views.
    * @param Boolean show: true shows the hand cursor for buttons, false hides it
    */
    ,showHandCursor: function (show) {
        var c = show === true ? 'pointer' : 'default';
        this.__showhand = c;
        LzMouseKernel.setCursorGlobal(c);
    }

    /**
    * Sets the cursor to a resource
    * @param String n: cursor ID to use, or '' for default.  See 
    * http://www.quirksmode.org/css/cursor.html for valid IDs
    */
    ,setCursorGlobal: function ( n ){
        if (LzSprite.quirks.no_cursor_colresize) {
            return;
        }
        var n = LzSprite.__defaultStyles.hyphenate(n);
        
        LzSprite.prototype.__setCSSClassProperty('.lzdiv', 'cursor', n);
        LzSprite.prototype.__setCSSClassProperty('.lzcanvasdiv', 'cursor', n);
        LzSprite.prototype.__setCSSClassProperty('.lzclickdiv', 'cursor', n);
        LzSprite.prototype.__setCSSClassProperty('.lzcanvasclickdiv', 'cursor', n);
    }
    
    /**
    * This function restores the default cursor if there is no locked cursor on
    * the screen.
    * 
    * @access private
    */
    ,restoreCursor: function ( ){
        if (LzSprite.quirks.no_cursor_colresize) {
            return;
        }
        if ( LzMouseKernel.__amLocked ) return;
        
        LzSprite.prototype.__setCSSClassProperty('.lzdiv', 'cursor', 'default');
        LzSprite.prototype.__setCSSClassProperty('.lzcanvasdiv', 'cursor', 'default');
        LzSprite.prototype.__setCSSClassProperty('.lzclickdiv', 'cursor', LzMouseKernel.__showhand);
        LzSprite.prototype.__setCSSClassProperty('.lzcanvasclickdiv', 'cursor', 'default');
    }

    /**
    * Prevents the cursor from being changed until unlock is called.
    * 
    */
    ,lock: function (){
        LzMouseKernel.__amLocked = true;
    }

    /**
    * Restores the default cursor.
    * 
    */
    ,unlock: function (){
        LzMouseKernel.__amLocked = false;
        LzMouseKernel.restoreCursor(); 
    }

    ,disableMouseTemporarily: function (){
        this.setGlobalClickable(false);
        this.__resetonmouseover = true; 
    }
    ,__resetonmouseover: false
    ,__resetMouse: function (){
        if (this.__resetonmouseover) {
            this.__resetonmouseover = false;
            this.setGlobalClickable(true);
        }
    }
    ,__globalClickable: true
    ,setGlobalClickable: function (isclickable){
        if (isclickable === this.__globalClickable) return;
        //Debug.warn('setGlobalClickable', isclickable);
        this.__globalClickable = isclickable;
        var el = document.getElementById('lzcanvasclickdiv');
        if (LzSprite.quirks.fix_ie_clickable) {
            LzSprite.prototype.__setCSSClassProperty('.lzclickdiv', 'display', isclickable ? '' : 'none');
        }
        if (el) el.style.display = isclickable ? '' : 'none';
    }
    // Returns true if the call originated from an onmousemove event
    ,__sendMouseMove: function(e, offsetx, offsety) {
        var sendmousemove = 'mousemove' === e.type;
        var returnvalue = true;
        if (LzSprite.prototype.capabilities.touchevents) {
            var touches = e.touches;
            // read the position of the first finger
            var touch = touches && touches[0];
            if (touch) {
                LzMouseKernel.__x = touch.pageX; 
                LzMouseKernel.__y = touch.pageY;
                //console.log('new pos'+ ([LzMouseKernel.__x, LzMouseKernel.__y, e.type, e.target.owner.owner]).join(', '));
            }
            // always send onmousemove for touch events
            sendmousemove = true;
            returnvalue = false;
        // see http://www.quirksmode.org/js/events_properties.html#position
        } else if (e.pageX || e.pageY) {
            LzMouseKernel.__x = e.pageX;
            LzMouseKernel.__y = e.pageY;
        } else if (e.clientX || e.clientY) {
            // IE doesn't implement pageX/pageY, instead scrollLeft/scrollTop
            // needs to be added to clientX/clientY
            var body = document.body, docElem = document.documentElement;
            LzMouseKernel.__x = e.clientX + body.scrollLeft + docElem.scrollLeft;
            LzMouseKernel.__y = e.clientY + body.scrollTop + docElem.scrollTop;
        }
        if (offsetx) {
            LzMouseKernel.__x += offsetx;
        }
        if (offsety) {
            LzMouseKernel.__y += offsety;
        }
        
        //LPP-10358, send mouseout event when mouse move out of canvas over iframe or frame 
        if( LzMouseKernel.__x < 0 || LzMouseKernel.__x > canvas.width || LzMouseKernel.__y < 0 || LzMouseKernel.__y > canvas.height ){             
            if (LzMouseKernel.__lastMouseDown) LzMouseKernel.__lastMouseDown.__globalmouseup({type:"mouseout"});   
        }

        if (sendmousemove) {
            LzMouseKernel.__sendEvent('onmousemove');
            // return true if sent
            return returnvalue;
        }
        return false;
    }
    ,__resetLastMouse: function() {
        // Reset all __lastMouse* values. Needed for context menu
        LzMouseKernel.__lastMouseContext = LzMouseKernel.__lastMouseDown;
        LzMouseKernel.__lastMouseDown = null;
        LzMouseKernel.__lastMouseOver = null;
        LzMouseKernel.__lastMouseOut = null;
    }
    ,__contextmenumouse2: false
    ,__handleContextMenu: function(e) {

        // update mouse position, required for Safari
        LzMouseKernel.__sendMouseMove(e);
        
         // IE calls `target` `srcElement`
         // restore Browser default context menu 
        var target = e.target || e.srcElement;
        if ( target instanceof HTMLInputElement || 
              target instanceof HTMLTextAreaElement ) { return true; }

        var cmenu = LzMouseKernel.__findContextMenu(e);
        if (cmenu) {
          var eventname = 'on' + e.type;
          var showbuiltins = cmenu.kernel.showbuiltins;
          var viamouse2 = false;

          //Debug.debug('__handleContextMenu', eventname, cmenu);

          // If browser supports DOM mouse events level 2 feature,
          // trigger menu on mousedown-right (button 2), and suppress
          // the native browser context menu when the oncontextmenu
          // event comes.
          if (LzSprite.prototype.quirks.has_dom2_mouseevents) {
            if (eventname === 'oncontextmenu') {
              if (LzMouseKernel.__contextmenumouse2) {
                LzMouseKernel.__contextmenumouse2 = false;
                // ignore oncontextmenu if you have already shown
                return false;
              }
            } else if (eventname === 'onmousedown' && e.button == 2) {
              viamouse2 = true;
              // Fall through
            } else {
              return true;
            }
          } else if (eventname !== 'oncontextmenu') {
            // If there is no DOM level 2 mouse event support,
            // then use the oncontextmenu event to display context
            // menu; pass other events to browser
            return true;
          }

          var target = e.target || e.srcElement;
          if (target && target.owner && showbuiltins !== true) {
            LzMouseKernel.__contextmenumouse2 = viamouse2;
            // show OL context menu
            var targetview = ((target.owner instanceof LzSprite) ? target.owner.owner : null);
            cmenu.kernel.__show(targetview);
            return false;
          }
        }
        // Default to browser
        return true;
    }
    ,__findContextMenu: function(e) {
        // show the default menu if not found...
        var cmenu = LzSprite.__rootSprite.__contextmenu;
        var quirks = LzSprite.quirks;
        if (document.elementFromPoint) {
            // elementFromPoint() expects x/y to be offset by scrolling
            // See LPP-9203
            if (window.pageXOffset) {
                var x = e.pageX - window.top.pageXOffset;
                var y = e.pageY - window.top.pageYOffset;
            } else {
                var x = e.clientX;
                var y = e.clientY;
            }
            var rootdiv = LzSprite.__rootSprite.__LZdiv;
            var clickSprite = (e.srcElement || e.target).owner;
            var arr = [];
            


            if (quirks.fix_contextmenu) {
                
                // get root div to back, so contextmenudiv will be at the front
                arr.push(rootdiv, rootdiv.style.display);
                var rootprevZ = rootdiv.style.zIndex; // save current zindex
                rootdiv.style.zIndex = -1000;
                
                // get click div to back, so contextmenudiv will be at the front
                var rootclickdiv;
                if (quirks.fix_clickable) {
                    rootclickdiv = LzSprite.__rootSprite.__LZclickcontainerdiv;
                    var clickprevZ = rootclickdiv.style.zIndex;
                    arr.push(rootclickdiv, rootclickdiv.style.display);
                    rootclickdiv.style.zIndex = -9999;
                }
            }
            do {
                var elem = document.elementFromPoint(x, y);
                if (! elem) {
                    // no element under position
                    break;
                } else {
                    var owner = elem.owner;
                    if (! owner) {
                        // no owner attached
                    } else if (owner.__contextmenu) {
                        // found a contextmenu
                        cmenu = owner.__contextmenu;
                        break;
                    } else if (quirks.ie_elementfrompoint && owner.scrolldiv === elem) {
                        // IE returns this first for text div. See LPP-8254
                    }
                    // hide this element to get next layer
                    arr.push(elem, elem.style.display);
                    elem.style.display = 'none';
                }
            } while (elem !== rootdiv && elem.tagName != 'HTML');

            // restore display
            for (var i = arr.length - 1; i >= 0; i -= 2) {
                arr[i - 1].style.display = arr[i];
            }

            if (quirks.fix_contextmenu) {
                rootdiv.style.zIndex = rootprevZ;;
                 if (quirks.fix_clickable) { rootclickdiv.style.zIndex = clickprevZ ; }
            }

        } else {
            // this is less reliable compared to elementFromPoint..
            var sprite = (e.srcElement || e.target).owner;
            if (sprite) {
                // walk up the parent chain looking for a __contextmenu
                while (sprite.__parent) {
                    if (sprite.__contextmenu) {
                        // check mouse bounds
                        var mpos = sprite.getMouse();
                        //Debug.write('pos', mpos, sprite.width, sprite.height);
                        if (mpos.x >= 0 && mpos.x < sprite.width &&
                            mpos.y >= 0 && mpos.y < sprite.height) {
                            cmenu = sprite.__contextmenu;
                            break;
                        }
                    }
                    sprite = sprite.__parent;
                }
            }
        }
        return cmenu;
    }

    ,__applicationFocused: false
    // Called with application received focus
    ,__applicationFocus: function() {
        if (!LzMouseKernel.__applicationFocused) {
            //console.log("****** Application focused");
            LzMouseKernel.__applicationFocused = true;

            if (LzSprite.quirks.fix_ie_clickable) {
                window.document.focus();
            }

        }
    }
    // Called when application receives blur
    ,__applicationBlur: function() {
        //console.log("****** Application blur");
        LzMouseKernel.__applicationFocused = false;
    }
}
