/* -*- c-basic-offset: 4; -*- */


/**
  * LzSprite.js
  *
  * @copyright Copyright 2007-2012 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic DHTML
  * @author Max Carlson &lt;max@openlaszlo.org&gt;
  */

{
#pragma "warnUndefinedReferences=false"

var LzSprite = function(owner, isroot) {
    if (owner == null) return;
    this.constructor = arguments.callee;
    this.owner = owner;
    this.uid = LzSprite.prototype.uid++;
    this.aadescriptionDiv = null;
    this.__csscache = {};
    var quirks = this.quirks;

    if (isroot) {        
        this.__setupRoot();    
    } else {
        this.__LZdiv = document.createElement('div');
        this.__LZdiv.className = 'lzdiv';
    }

    this.__LZdiv.owner = this;

    if (quirks.ie_leak_prevention) {
        this.__sprites[this.uid] = this;
    }
    //Debug.debug('new LzSprite', this.__LZdiv, this.owner);
}

/* Debug-only annotation */
if ($debug) {
    /** @access private */
    LzSprite.prototype._dbg_typename = 'LzSprite';
    /** @access private */
    LzSprite.prototype._dbg_name = function () {
        // Tip 'o the pin to
        // http://www.quirksmode.org/js/findpos.html
        var div = this.__LZdiv;
        var d = div;
        var x = 0, y = 0;
        if (d.offsetParent) {
            do {
                x += d.offsetLeft;
                y += d.offsetTop;
            } while (d = d.offsetParent);
        }
        return Debug.formatToString("%w/@sprite [%s x %s]*[1 0 %s, 0 1 %s, 0 0 1]",
                                    this.owner.sprite === this ? this.owner : '(orphan)',
                                    div.offsetWidth || 0, div.offsetHeight || 0,
                                    x || 0,
                                    y || 0);
    };
}


/** @access private */
LzSprite.prototype.uid = 0;

/**
  * @access private
  */
LzSprite.prototype.__LZdiv = null;

/**
  * @access private
  */
LzSprite.prototype.__setupRoot = function(e) {
    
        var quirks = this.quirks;
        this.isroot = true;
        LzSprite.__rootSprite = this;
        var div = document.createElement('div');
        div.className = 'lzcanvasdiv';

        quirks['scrollbar_width'] = LzSprite._getScrollbarWidth();

        if (quirks.ie6_improve_memory_performance) {
            try { document.execCommand("BackgroundImageCache", false, true); } catch(err) {}
        }

        // grab values stored by lz.embed.dhtml()
        var p = lz.embed.__propcache;
        var rootcontainer = LzSprite.__rootSpriteContainer = p.appenddiv;

        // appcontainer is the root container for lzcanvascontextdiv, lzcanvasdiv and lzcanvasclickdiv 
        var appcontainer = rootcontainer;

        // Ensure we do not hang out of the container div
        rootcontainer.style.margin = 0;
        rootcontainer.style.padding = 0;
        rootcontainer.style.border = "0 none";
        rootcontainer.style.overflow = "hidden";
        // For IE 7, in case the container inherits another value
        rootcontainer.style.textAlign = "left";

        if (quirks['container_divs_require_overflow']) {
            // create a container div that has overflow: hidden and a physical pixel size that lives inside the app container div. 
            // append lzcanvascontextdiv, lzcanvasdiv and lzcanvasclickdiv to the overflowdiv
            // See LPP-8402
            appcontainer = document.createElement('div');
            appcontainer.className = 'lzappoverflow';
            rootcontainer.appendChild(appcontainer);
            appcontainer.owner = this;

            // On Webkit browsers, mouse scrolling (ie. click/drag) can still
            // cause the window to scroll. Reset the scroll to stop this, but
            // only for lzappoverflow. (LPP-10237)
            if (quirks['fix_div_mouse_scrolling']) {
                appcontainer.addEventListener('scroll', function(e) { if (e && e.target && e.target.className && e.target.className == 'lzappoverflow') {e.target.scrollTop = 0; e.target.scrollLeft = 0;}}, true);
            }
            

            // so the height and width can be set later
            LzSprite.__rootSpriteOverflowContainer = appcontainer;
        }

        if (quirks.fix_contextmenu) {
            var cxdiv = document.createElement('div');
            cxdiv.className = 'lzcanvascontextdiv';
            cxdiv.id = 'lzcanvascontextdiv';
            appcontainer.appendChild(cxdiv);
            cxdiv.owner = this;
            this.__LZcontextcontainerdiv = cxdiv;
        }

        if (p.bgcolor) {
            div.style.backgroundColor = p.bgcolor; 
            this.bgcolor = p.bgcolor; 
        }
        if (p.id) {
            this._id = p.id;
        }
        if (p.url) {
            //also see LzBrowserKernel.getLoadURL()
            this._url = p.url;
        }

        // Store a reference to this app's options hash
        var options = p.options;
        if (options) {
            this.options = options;
        }
        // concatenate the serverroot
        LzSprite.blankimage = options.serverroot + LzSprite.blankimage;

        // process master sprites
        if (quirks.use_css_sprites && options.usemastersprite) {
            quirks.use_css_master_sprite = options.usemastersprite;
            var mastersprite = LzResourceLibrary && LzResourceLibrary.__allcss && LzResourceLibrary.__allcss.path;
            if (mastersprite) {
                LzSprite.__masterspriteurl = LzSprite.__rootSprite.options.approot + mastersprite;
                if ($debug) {
                    var masterspriteimg = new Image();
                    masterspriteimg.src = mastersprite;
                    masterspriteimg.onerror = function() {
                        Debug.warn('Error loading master sprite:', mastersprite);
                    }
                }
            }
        }

        /* Install the styles, now that quirks have been accounted for */
        LzSprite.__defaultStyles.writeCSS(quirks.write_css_with_createstylesheet);

        appcontainer.appendChild(div);
        this.__LZdiv = div;

        if (quirks.fix_clickable) {
            var cdiv = document.createElement('div');
            cdiv.className = 'lzcanvasclickdiv';
            cdiv.id = 'lzcanvasclickdiv';
            appcontainer.appendChild(cdiv);
            cdiv.owner = this;
            this.__LZclickcontainerdiv = cdiv;
            LzSprite.__setClickable(true, cdiv);
        } else {    
           LzSprite.__setClickable(true, div);
           LzSprite.__setActivateOnMouseoverForNoClickDiv(div);
        }


        if (quirks['css_hide_canvas_during_init']) {
            var cssname = 'display';
            var cssval = 'none';
            if (quirks['safari_visibility_instead_of_display']) {
                cssname = 'visibility';
                cssval = 'hidden';
            }
            this.__LZdiv.style[cssname] = cssval;
            if (quirks['fix_clickable']) this.__LZclickcontainerdiv.style[cssname] = cssval;
            if (quirks['fix_contextmenu']) this.__LZcontextcontainerdiv.style[cssname] = cssval;
        }
        
        if (quirks.fix_clickable) {
            if (quirks.activate_on_mouseover) {
                LzSprite.__setActivateOnMouseover(div);
            }
        }

        // create container div for text size cache
        LzFontManager.__createContainerDiv();

        // prevent text selection in IE and Safari
        if (quirks.prevent_selection) {
            // can't use lz.embed.attachEventHandler because we need to cancel 
            // events
            document.onselectstart = function (e) {
                if (!e) {
                    // capture events in IE
                    e = window.event;
                    var targ = e.srcElement; 
                } else {
                    // Safari gives the text node as the srcElement
                    var targ = e.srcElement.parentNode; 
                }

                //Debug.warn('onselectstart', targ, targ.owner, targ.owner instanceof LzTextSprite);
                if (targ.owner instanceof LzTextSprite) {
                    if (! targ.owner.selectable) {
                        //Debug.write("prevent selection on non-selectable text")
                        return false;
                    }
                } else {
                    //Debug.write("prevent selection on non-text")
                    return false;
                }
            }
        }
}


LzSprite.__setActivateOnMouseover = function(div) { 
    
    // Mouse detection for activation/deactivation of keyboard/mouse events
    div.mouseisover = false;
    div.onmouseup = LzSprite.__globalClickDispatcher;
    div.onmousedown = LzSprite.__globalClickDispatcher;
    div.onclick = LzSprite.__globalClickDispatcher;
    
    div.onmouseover = function(e) {
        if (LzSprite.quirks.keyboardlistentotop_in_frame) {
            if (LzSprite.__rootSprite.options.cancelkeyboardcontrol != true) {
                LzSprite.quirks.keyboardlistentotop = true;
                LzKeyboardKernel.setKeyboardControl(true);
            }
        }
        if (LzSprite.quirks.focus_on_mouseover) {
            if (LzSprite.prototype.getSelectedText() == "") {
                div.focus();
            }
        }
        if (LzInputTextSprite.prototype.__focusedSprite == null) LzKeyboardKernel.setKeyboardControl(true);
        LzMouseKernel.setMouseControl(true);
        this.mouseisover = true;
        LzSprite.__globalClickDispatcher(e);
        //console.log('onmouseover', e, this.mouseisover);
    }

    div.onmouseout = function(e) {
        // capture events in IE
        e = e || window.event;

        // IE calls `target` `srcElement`
        var el = e.target || e.srcElement;

        var quirks = LzSprite.quirks;
        if (quirks.inputtext_anonymous_div) {
            try {
                // Only try to access parentNode to workaround a Firefox bug,
                // where relatedTarget may point to an anonymous div of an
                // input-element (in which case accessing parentNode throws
                // a security exception). (LPP-7796)
                el && el.parentNode;
            } catch (e) {
                return;
            }
        }
        var mousein = false;
        var owner = el.owner;
        if (el) {
            var cm = LzContextMenuKernel.lzcontextmenu;
            if (owner && el.className.indexOf('lz') == 0) {
                // lzdiv, lzclickdiv, etc.
                mousein = true;
            } else if (cm && (el === cm || el.parentNode === cm)) {
                // context-menu resp. context-menu item
                mousein = true;
            }
        }

        // On IE, after a context-menu paste which ends up over an html
        // component, you stop getting onkeyevents. Setting mousein=true
        // keeps setKeyboardControl to true  (LPP-10299)
        if (lz.embed.browser.isIE && el instanceof HTMLIFrameElement) {
            mousein = true;
            //console.log(" mousein forced to false LPP-10299");
        }

        if (mousein) {
            var wasClickable = LzMouseKernel.__globalClickable;
            if (quirks.fix_ie_clickable) {
                LzMouseKernel.setGlobalClickable(true);
            }
            if (quirks.focus_on_mouseover) {
                if (LzInputTextSprite.prototype.__lastshown == null) {
                    if (LzSprite.prototype.getSelectedText() == "") {
                        div.focus();
                    }
                }
            }
            LzKeyboardKernel.setKeyboardControl(true);
            LzMouseKernel.setMouseControl(true);
            LzMouseKernel.__resetMouse();
            this.mouseisover = true;
            // NOTE: [2008-08-17 ptw] (LPP-8375) Forward the
            // event to the associated view (if any), if it
            // would have gotten it without the quirk
            // clickability diddling
            if (quirks.fix_clickable && (! wasClickable) && LzMouseKernel.__globalClickable) {
                // Was there an owner for the target div?
                if (owner) {
                    // In the kernel, a div's owner is
                    // typically the sprite, and the sprite's
                    // owner is the view.  The <html> element,
                    // though creates its own <iframe> and
                    // sets itself as the owner, hence this
                    // little two-step
                    if (owner is LzSprite) {
                        owner = owner['owner'];
                    }
                    // Was the target associated with a <view>?
                    if (owner is LzView) {
                        LzMouseKernel.__sendEvent('onmouseout', owner);
                    }
                }
            }
        } else {
            if (quirks.focus_on_mouseover) {
                if (LzInputTextSprite.prototype.__lastshown == null) {
                    if (LzSprite.prototype.getSelectedText() == "") {
                        div.blur();
                    }
                }
            }
            LzKeyboardKernel.setKeyboardControl(false);
            LzMouseKernel.setMouseControl(false);
            this.mouseisover = false;
        }
        LzSprite.__globalClickDispatcher(e);
        //Debug.write('onmouseout', this.mouseisover, el.className, e);
    }

    if (LzSprite.quirks.keyboardlistentotop_in_frame) {
        // listen for window focus events if we're in an iframe
        window.onfocus = function(e) {
            if (LzSprite.__rootSprite.options.cancelkeyboardcontrol != true) {
                div.onmouseover();
            }
        }
    }

    // Store a reference to the div that handles mouse activation
    LzSprite.__mouseActivationDiv = div;    
}


LzSprite.__setActivateOnMouseoverForNoClickDiv = function(div) { 
    
    // Mouse detection for activation/deactivation of keyboard/mouse events
    div.mouseisover = false;
  //  div.onmouseup = LzSprite.__globalClickDispatcher;
  //  div.onmousedown = LzSprite.__globalClickDispatcher;
  //  div.onclick = LzSprite.__globalClickDispatcher;
    
    div.onmouseover = function(e) {
        if (LzSprite.quirks.keyboardlistentotop_in_frame) {
            if (LzSprite.__rootSprite.options.cancelkeyboardcontrol != true) {
                LzSprite.quirks.keyboardlistentotop = true;
                LzKeyboardKernel.setKeyboardControl(true);
            }
        }
        if (LzSprite.quirks.focus_on_mouseover) {
            if (LzSprite.prototype.getSelectedText() == "") {
                div.focus();
            }
        }
        if (LzInputTextSprite.prototype.__focusedSprite == null) LzKeyboardKernel.setKeyboardControl(true);
        LzMouseKernel.setMouseControl(true);
        this.mouseisover = true;
        LzSprite.__clickDispatcher(e);
        //console.log('onmouseover', e, this.mouseisover);
    }

    div.onmouseout = function(e) {
        // capture events in IE
        e = e || window.event;

        // IE calls `target` `srcElement`
        var el = e.target || e.srcElement;

        var quirks = LzSprite.quirks;
        if (quirks.inputtext_anonymous_div) {
            try {
                // Only try to access parentNode to workaround a Firefox bug,
                // where relatedTarget may point to an anonymous div of an
                // input-element (in which case accessing parentNode throws
                // a security exception). (LPP-7796)
                el && el.parentNode;
            } catch (e) {
                return;
            }
        }
        var mousein = false;
        var owner = el.owner;
        if (el) {
            var cm = LzContextMenuKernel.lzcontextmenu;
            if (owner && el.className.indexOf('lz') == 0) {
                // lzdiv, lzclickdiv, etc.
                mousein = true;
            } else if (cm && (el === cm || el.parentNode === cm)) {
                // context-menu resp. context-menu item
                mousein = true;
            }
        }
        if (el instanceof HTMLIFrameElement) {
            mousein = true;
            //console.log(" mousein forced to false LPP-10299");
        }

        if (mousein) {
            var wasClickable = LzMouseKernel.__globalClickable;
            if (quirks.fix_ie_clickable) {
                LzMouseKernel.setGlobalClickable(true);
            }
            if (quirks.focus_on_mouseover) {
                if (LzInputTextSprite.prototype.__lastshown == null) {
                    if (LzSprite.prototype.getSelectedText() == "") {
                        div.focus();
                    }
                }
            }
            LzKeyboardKernel.setKeyboardControl(true);
            LzMouseKernel.setMouseControl(true);
            LzMouseKernel.__resetMouse();
            this.mouseisover = true;
            // NOTE: [2008-08-17 ptw] (LPP-8375) Forward the
            // event to the associated view (if any), if it
            // would have gotten it without the quirk
            // clickability diddling
            if (quirks.fix_clickable && (! wasClickable) && LzMouseKernel.__globalClickable) {
                // Was there an owner for the target div?
                if (owner) {
                    // In the kernel, a div's owner is
                    // typically the sprite, and the sprite's
                    // owner is the view.  The <html> element,
                    // though creates its own <iframe> and
                    // sets itself as the owner, hence this
                    // little two-step
                    if (owner is LzSprite) {
                        owner = owner['owner'];
                    }
                    // Was the target associated with a <view>?
                    if (owner is LzView) {
                        LzMouseKernel.__sendEvent('onmouseout', owner);
                    }
                }
            }
        } else {
            if (quirks.focus_on_mouseover) {
                if (LzInputTextSprite.prototype.__lastshown == null) {
                    if (LzSprite.prototype.getSelectedText() == "") {
                        div.blur();
                    }
                }
            }
            LzKeyboardKernel.setKeyboardControl(false);
            LzMouseKernel.setMouseControl(false);
            this.mouseisover = false;
        }
        LzSprite.__clickDispatcher(e);
        //Debug.write('onmouseout', this.mouseisover, el.className, e);
    }

    if (LzSprite.quirks.keyboardlistentotop_in_frame) {
        // listen for window focus events if we're in an iframe
        window.onfocus = function(e) {
            if (LzSprite.__rootSprite.options.cancelkeyboardcontrol != true) {
                div.onmouseover();
            }
        }
    }

    // Store a reference to the div that handles mouse activation
    LzSprite.__mouseActivationDiv = div;    
}

}
