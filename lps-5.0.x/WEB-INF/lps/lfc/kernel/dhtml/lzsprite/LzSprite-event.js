/* -*- c-basic-offset: 4; -*- */


/**
  * LzSprite.js
  *
  * @copyright Copyright 2007-2013 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic DHTML
  * @author Max Carlson &lt;max@openlaszlo.org&gt;
  */

{
#pragma "warnUndefinedReferences=false"

LzSprite.prototype.__LZclick = null;
LzSprite.prototype.clickable = false;
/**
  * @access private
  * tracks whether the mouse is currently down on this sprite for onmouseupoutside events
  */
LzSprite.prototype.__mouseisdown = false;

LzSprite.prototype.setClickable = function(c) {
    // coerce to boolean
    c = !!c;
    if (this.clickable === c) return;

    if ( this.quirks.fix_clickable && !this.__LZclickcontainerdiv) {
         this.__LZclickcontainerdiv = this.__createContainerDivs('click');
     }
    
    if (!this.quirks.fix_clickable) {
       if (c) {
           this.__LZdiv.style.pointerEvents = 'auto';
           // LPP-10247. Cursor style set in stylesheet, not explicitly
           // this.__LZdiv.style.cursor = 'pointer';
           // LPP-10320. Rollback for lpp10247 and restore this code by setting class css insteadof inline css
           if (this.__LZdiv.style.removeProperty instanceof Function) this.__LZdiv.style.removeProperty('cursor'); //remove inline style for cursor
           if (document.styleSheets) this.__setCSSClassProperty('.lzdiv', 'cursor', 'pointer');
       } else {
           this.__LZdiv.style.pointerEvents = 'none';
           this.__LZdiv.style.cursor = 'default';
       }
    }

    //Debug.info('setClickable', c);
    if ( this.quirks.fix_clickable && this.__LZimg != null) {
        if (! this.__LZclick) {
            if (this.quirks.fix_ie_clickable) {
                this.__LZclick = document.createElement('img');
                this.__LZclick.src = LzSprite.blankimage;
            } else {
                this.__LZclick = document.createElement('div');
            }
            this.__LZclick.owner = this;
            this.__LZclick.className = 'lzclickdiv';
            this.__LZclick.style.width = this._w;
            //this.applyCSS('width', this._w, '__LZclick');
            this.__LZclick.style.height = this._h;
            //this.applyCSS('height', this._h, '__LZclick');
            if (this.quirks.fix_clickable) {
                this.__LZclickcontainerdiv.appendChild(this.__LZclick);
            } else {
                this.__LZdiv.appendChild(this.__LZclick);
            }
        }
        //Debug.info('clickable', this.__LZclick, c, this.__LZclick.style.width, this.__LZclick.style.height);
        if (this.quirks.fix_clickable) {
            if (this.quirks.fix_ie_clickable) {
                //note: views with resources (__LZimg!) cannot have subviews (SWF-policy)
                var clickstyle = c && this.visible ? '' : 'none'
                this.__LZclickcontainerdiv.style.display = clickstyle;
                //this.applyCSS('display', clickstyle, '__LZclickcontainerdiv');
                this.__LZclick.style.display = clickstyle;
                //this.applyCSS('display', clickstyle, '__LZclick');
            } else {
                this.__LZclick.style.display = c ? '' : 'none';
                //this.applyCSS('display', c ? '' : 'none', '__LZclick');
            }
        }
    } else {
        if (this.quirks.fix_clickable) {
            if (! this.__LZclick) {
                if (this.quirks.fix_ie_clickable) {
                    this.__LZclick = document.createElement('img');
                    this.__LZclick.src = LzSprite.blankimage;
                } else {
                    this.__LZclick = document.createElement('div');
                }
                this.__LZclick.owner = this;
                this.__LZclick.className = 'lzclickdiv';
                this.__LZclick.style.width = this._w;
                //this.applyCSS('width', this._w, '__LZclick');
                this.__LZclick.style.height = this._h;
                //this.applyCSS('height', this._h, '__LZclick');
                //this.__LZclick.style.backgroundColor = '#ff00ff';
                //this.__LZclick.style.opacity = .2;
                this.__LZclickcontainerdiv.appendChild(this.__LZclick);
            }
            if (this.quirks.fix_ie_clickable) {
                this.__LZclick.style.display = c && this.visible ? '' : 'none';
                //this.applyCSS('display', c && this.visible ? '' : 'none', '__LZclick');
            } else {
                this.__LZclick.style.display = c ? '' : 'none';
                //this.applyCSS('display', c ? '' : 'none', '__LZclick');
            }
        }
    }
    this.clickable = c;
}

/** Register/unregister for mouse/touch events
  * @access private
  */
LzSprite.__setClickable = function(c, div) {
    if (div._clickable === c) return;
    div._clickable = c;
    var f = c ? LzSprite.__clickDispatcher : null;

    if (LzSprite.prototype.capabilities.touchevents) {
        // touch events are mutually exclusive with mouse events...
        div.ontouchstart = f;
        div.ongesturestart = f;
    } else {
        div.onclick = f;
        // Prevent context menus in Firefox 1.5 - see LPP-2678
        div.onmousedown = f;
        div.onmouseup = f;
        div.onmousemove = f;
        if (LzSprite.quirks.listen_for_mouseover_out) {
            div.onmouseover = f;
            div.onmouseout = f;
        } 
        if (LzSprite.quirks.ie_mouse_events) {
            // special events for IE
            div.ondrag = f;
            div.ondblclick = f;
        }
    }
}

/** Register/unregister for touchmove/end events
  * @access private
  */
LzSprite.__touchstart = function(c, div) {
    var f = c ? LzSprite.__clickDispatcher : null;
    div.ontouchmove = f;
    div.ontouchend = f;
    // can happen when more than 5 fingers go down in iOS
    div.ontouchcancel = f;
}

/** Register/unregister for gesturechange/end events
  * @access private
  */
LzSprite.__gesturestart = function(c, div) {
    var f = c ? LzSprite.__clickDispatcher : null;
    div.ongesturechange = f;
    div.ongestureend = f;
}



/**
  * @access private
  * dispatches global click events, called from the scope of the application 
  * container div
  */
LzSprite.__globalClickDispatcher = function(e) {
    // capture events in IE
    e = e || window.event;


    if (! e) return;

    // Context menu events are handled by LzMouseKernel
    // In particular, if button-2 is down, we darn well don't want to
    // fall through and trigger non-button-2 events of the same name
    if (lz.embed.browser.isIE && e.button == 2) {
        return LzMouseKernel.__handleContextMenu(e);
    }

    // IE calls `target` `srcElement`
    var target = e.target || e.srcElement;
    var owner = target && target.owner;

    var returnvalue;
    if (! owner){ 
        //console.log('no owner', e.type, target, e);
        // we don't have an owner, so we're not a sprite
        return;
    } else if (owner.selectable && (owner instanceof LzTextSprite)) {
        // text for selectable sprites is forwarded to the sprite
        //console.log('text', e.type, owner);
        returnvalue = LzSprite.__clickDispatcher(e);
        return returnvalue;
//    } else if (! owner.isroot) {
        // we're not the root sprite, ignore
        //console.log('skipping', e.type, owner, e);
        //return;
    }

    var eventname = 'on' + e.type;

    //console.log('__globalclickDispatcher: ', eventname, ',', document.activeElement);

    // Handle global/canvas onmouseup
    if (eventname == 'onmouseup') {
        // Send onmouseup/upoutside if needed
        var lastmousedown = LzMouseKernel.__lastMouseDown;
        if (lastmousedown && lastmousedown !== owner) {
            // tell sprite that got the last mouse down about onmouseup
            // allows sprites to send onmouseupoutside
            lastmousedown.__globalmouseup(e);
        }
        LzMouseKernel.__lastMouseDown = null;
        LzMouseKernel.__lastMouseContext = null;
    } else if (eventname == 'onmousedown') {
        // Blur any focused inputtexts - see LPP-8475
        var focusedsprite = LzInputTextSprite.prototype.__focusedSprite;
        if (focusedsprite && focusedsprite !== owner) {
            focusedsprite.deselect();
        }
        // Duplicate the functionality in ClickDispatcher (LPP-10148)
        LzMouseKernel.__lastMouseDown = owner;
        owner.__mouseisdown = true;
    } else if (eventname == 'onmouseover') {
        if (LzMouseKernel.__lastMouseOut == null && LzMouseKernel.__lastMouseOver == null) return;
        LzMouseKernel.__lastMouseOver = null
    } else if (eventname == 'onmouseout') {
        if (LzMouseKernel.__lastMouseOut == null && LzMouseKernel.__lastMouseOver == null) return;
        LzMouseKernel.__lastMouseOut = null
    }

    //Debug.info('__globalClickDispatcher', eventname, owner.owner);

    // Send global event
    LzMouseKernel.__sendEvent(eventname, owner.owner);

    // return a value, if any
    return returnvalue;
}

/**
  * @access private
  * dispatches click events, called from the scope of the click div
  */
LzSprite.__clickDispatcher = function(e) {
    // capture events in IE
    e = e || window.event;
    if (! e) return;

    // cancel bubbling by default
    // TODO [2010-9-23 max] Should we allow events to bubble?  It seemed safest
    // because they might interfere with the activation div (our parent) but it 
    // would be nice to allow the window/document to receive them also!
    e.cancelBubble = true;

    // Context menu events are handled by LzMouseKernel
    // In particular, if button-2 is down, we darn well don't want to
    // fall through and trigger non-button-2 events of the same name
    if (lz.embed.browser.isIE && e.button == 2) {
        return LzMouseKernel.__handleContextMenu(e);
    }

    if (LzKeyboardKernel && LzKeyboardKernel['__updateControlKeys']) {
        LzKeyboardKernel.__updateControlKeys(e);

        if (LzKeyboardKernel.__cancelKeys) {
            e.cancelBubble = true;
        }
    }

    // Update the mouse position for any event
    if (LzMouseKernel.__sendMouseMove(e)) {
        // If we got here, onmousemove was already sent
        // Returning false prevents text selection in IE
        return;
    }

    // IE calls `target` `srcElement`
    var target = e.target || e.srcElement;
    var owner = target && target.owner;
    if (!owner) return;
    var eventname = 'on' + e.type;
//console.log("--__clickDispatcher--e=",e,target,eventname);
    //Debug.debug('__clickDispatcher', owner, eventname);
    
    if (eventname == 'onmousedown')
        LzMouseKernel.__applicationFocus ();

    // Rename ie-specific events
    if (LzSprite.quirks.ie_mouse_events) {
        // enter/leave are used by LzInputTextSprite
        if (eventname == 'onmouseenter') {
            eventname = 'onmouseover';
        } else if (eventname == 'onmouseleave') {
            eventname = 'onmouseout';
        } else if (eventname == 'ondblclick') {
            // Send artificial events to mimic other browsers (pre IE9)
            if (lz.embed.browser.version < 9) {
                owner.__mouseEvent('onmousedown');
                owner.__mouseEvent('onmouseup');
                owner.__mouseEvent('onclick');
            }
            return false;
        } else if (eventname == 'ondrag') {
            // ignore these
            return false;
        }
    } 

    // Adapt touch events so dragging works
    if (LzSprite.prototype.capabilities.touchevents) {
        // TODO: [max 4-30-2010] Send true over/out events by detecting 
        // when the global mouse position is over/out
        if (eventname == 'ongesturestart') {
            // Register for gesturechange/end events
            LzSprite.__gesturestart(true, target);
            return;
        } else if (eventname == 'ongesturechange') {
            // Process gesturechange event 
            // Useful properties are scale and rotation
            //console.log(e.scale + ', ' + e.rotation)
            LzMouseKernel.__sendEvent('ongesture', owner.owner, {scale: e.scale, rotation: e.rotation});
            return;
        } else if (eventname == 'ongestureend') {
            // Unregister for gesturemove/end events
            LzSprite.__gesturestart(false, target);
            return;
        } else if (eventname == 'ontouchstart') {
            if (e.touches.length != 1) {
                //Debug.warn('more than 1 finger', e.owner.owner);
                LzMouseKernel.__sendEvent('ontouch', owner.owner, e.touches);
                return true;
            }
            // Register for touchemove/end events
            LzSprite.__touchstart(true, target);
            eventname = 'onmousedown';
            // Send artificial mouseover event now
            owner.__mouseEvent('onmouseover');
        } else if (eventname == 'ontouchmove') {
            if (e.touches.length != 1) {
                //Debug.warn('more than 1 finger', e.owner.owner);
                LzMouseKernel.__sendEvent('ontouch', owner.owner, e.touches);
                return true;
            }
            LzMouseKernel.__sendMouseMove(e);
            // update mouseover/out
            var mouseisover = owner.__isMouseOver()
            var lastmouseisover = LzMouseKernel.__lastMouseOver === owner;
            if ((! mouseisover) && lastmouseisover) {
                owner.__mouseEvent('onmouseout')
            } else if (mouseisover && (! lastmouseisover)) {
                owner.__mouseEvent('onmouseover')
            }
            //Debug.info('ontouchmove', e.owner);
            eventname = 'onmousemove';
        } else if (eventname == 'ontouchend' || eventname == 'ontouchcancel') {
            // NOTE: touchend events don't have touches - see http://www.sitepen.com/blog/2008/07/10/touching-and-gesturing-on-the-iphone/
            // Unregister for touchemove/end events
            LzSprite.__touchstart(false, target);
            eventname = 'onmouseup';
            e.cancelBubble = false;
            //Debug.info('ontouchend', e.owner);
            // Send artificial events later, so the mouseup event fires first
            if (! owner.__isMouseOver()) {
                var callback = lz.BrowserUtils.getcallbackfunc(owner, '__mouseEvent', ['onmouseupoutside']);
                setTimeoutNoArgs(callback, 0);
            }
            var callback = lz.BrowserUtils.getcallbackfunc(owner, '__mouseEvent', ['onmouseout']);
            setTimeoutNoArgs(callback, 0);
            callback = lz.BrowserUtils.getcallbackfunc(owner, '__mouseEvent', ['onclick']);
            setTimeoutNoArgs(callback, 0);
        } else {
            // some other touch event, allow
            return true;
        }
    } 



    //console.log('owner', owner, eventname);

    // inputtexts have special handling
    if (owner instanceof LzInputTextSprite && owner.selectable == true) {
        if (eventname == 'onmousedown') {
            // This ensures the owner stays shown, so the next click works
            LzInputTextSprite.prototype.__focusedSprite = owner;
        } else if (eventname == 'onmouseout') {
            if (! owner.__isMouseOver()) {
                owner.__hide();
            }
        } else {
            owner.__show();
        }
        
        // Don't send onmouseout events for inputtexts: they're sent by __textEvent
        if (eventname !== 'onmouseout') {
            owner.__mouseEvent(eventname);
        }
        
        //LPP-10365, add this to avoide missing mouseup when drag a view into input
        if (eventname == 'onmouseup') {
            var lastmousedown = LzMouseKernel.__lastMouseDown;
            if (lastmousedown && lastmousedown !== owner) {
                lastmousedown.__globalmouseup(e);
            }
        }
        return;
    }

    if (eventname == 'onmousedown' ) {
        if (lz.embed.browser.isIE) LzMouseKernel.__lastMouseDownForIEClick = owner;
        if ( e.button == 2) return ;
        owner.__mouseisdown = true;

        // track which sprite the mouse went down on for onmouseupoutside
        LzMouseKernel.__lastMouseDown = owner;

        // Blur any focused inputtexts - see LPP-8475
        var focusedsprite = LzInputTextSprite.prototype.__focusedSprite;
        if (focusedsprite && focusedsprite !== owner) {
            focusedsprite.deselect();
        }
    } else if (eventname == 'onmouseup' ) {
        if (lz.embed.browser.isIE) LzMouseKernel.__lastMouseUpForIEClick = owner;
        if ( e.button == 2) return ;
        // Send onmouseup/upoutside if needed
        var lastmousedown = LzMouseKernel.__lastMouseDown;
        if (LzMouseKernel.__lastMouseContext) {
            // Prevent onmouseup event from already closed context (LPP-10300)
            LzMouseKernel.__lastMouseContext.__mouseisdown = false;
            LzMouseKernel.__lastMouseContext.__globalmouseup(e);
        }
        else if (lastmousedown && lastmousedown !== owner) {
            // tell sprite that got the last mouse down about onmouseup
            // allows sprites to send onmouseupoutside
            lastmousedown.__globalmouseup(e);
        }

        LzMouseKernel.__lastMouseContext = null;
        // Skip onmouseup if mouse button didn't go down on this sprite
        if (lastmousedown !== owner) {
            LzMouseKernel.__lastMouseDown = null;
            if (owner) owner.__mouseisdown = false;
            return;
        } else {
            // the mouse went up on this sprite
            owner.__mouseisdown = false;
            LzMouseKernel.__lastMouseDown = null;
        }

        // allow bubbling to the canvas so LzSprite.__globalmouseup() can find out about onmouseupoutside
        e.cancelBubble = false;
    } else if (lz.embed.browser.isIE && eventname == 'onclick') {
        // avoid to call __mouseEvent if the targets of mousedown and mouseup are not same for LPP-10350 
        var _isSameTarget = (LzMouseKernel.__lastMouseDownForIEClick == LzMouseKernel.__lastMouseUpForIEClick);
        LzMouseKernel.__lastMouseDownForIEClick = null;
        LzMouseKernel.__lastMouseUpForIEClick = null;
        if (!_isSameTarget) return;
    }

    //console.log('__clickDispatcher', owner, eventname);
    return owner.__mouseEvent(eventname) || false;
}



/**
  * Processes mouse events and forwards into the view system
  * @access private
  */
LzSprite.prototype.__mouseEvent = function(eventname){
    //console.log('__mouseEvent', eventname);
    
    if (eventname == 'onmousedown') {
        // blur any focused inputtexts - see LPP-8475
        var focusedsprite = LzInputTextSprite.prototype.__focusedSprite;
        if (focusedsprite && focusedsprite != this) {
            focusedsprite.deselect();
        }
    } else if (eventname == 'onmouseover') {
        LzMouseKernel.__lastMouseOver = this;

        if (this.quirks.activate_on_mouseover) {
            var activationdiv = LzSprite.__mouseActivationDiv;
            if (! activationdiv.mouseisover) {
                // enable keyboard/mouse events
                activationdiv.onmouseover();
            }
        }
    } else if (eventname == 'onmouseout') {
        LzMouseKernel.__lastMouseOut = this;
    }

    //Debug.write('__mouseEvent', eventname, this.owner);
    if (this.owner.mouseevent) {
        // handle onmouseover/out/dragin/dragout events differently if the mouse button is currently down
        if (LzMouseKernel.__lastMouseDown) {

            if (eventname == 'onmouseover' || eventname == 'onmouseout') {
                // send events if the mouse went down on this sprite
                var sendevents = LzMouseKernel.__lastMouseDown === this;

                // stored so we can send onmouseover after the mouse button goes up - see LPP-8445
                if (eventname == 'onmouseover') {
                    LzMouseKernel.__lastMouseOver = this;
                } else if (sendevents && LzMouseKernel.__lastMouseOver === this) {
                    LzMouseKernel.__lastMouseOver = null;
                }

                if (sendevents) {
                    LzMouseKernel.__sendEvent(eventname, this.owner);
                    var dragname = eventname == 'onmouseover' ? 'onmousedragin' : 'onmousedragout';
                    LzMouseKernel.__sendEvent(dragname, this.owner);
                }
                return;
            }
        }

        if (this.quirks.fix_clickable && (! LzMouseKernel.__globalClickable)) {
            // NOTE: [2008-08-17 ptw] (LPP-8375) When the mouse goes
            // over an html clickdiv, globalClickable gets disabled,
            // which generates a mouseout -- we want to ignore that.
            // Simlutaneously, the mouse enters the associated iframe,
            // which will forward a mouseover to us, but we already
            // got one, so, we want to ignore that too.  The global
            // mouseout handler will synthesize a mouseout event for
            // the html sprite when the mouse leaves the iframe and
            // re-enables the clickdiv.
            // NOTE: [2010-01-11 max] (LPP-8308) This change was preventing
            // inputtexts from getting onmouseover events, so test to 
            // be sure the mouse event isn't from an inputtext-generated
            // event.
            if (lz['html'] && this.owner && (this.owner is lz.html) && ((eventname == 'onmouseout') || (eventname == 'onmouseover'))) {
                //Debug.error('skipping', eventname);
                return;
            }
        }

        // Send the event
        LzMouseKernel.__sendEvent(eventname, this.owner);
    }
}

LzSprite.prototype.__isMouseOver = function ( e ){
    var p = this.getMouse();
    // Note pixels are 0-based, so width and height are exclusive limits
    var visible = this.__findParents('visible', false, true);
    if (visible.length) return false;
    return p.x >= 0 && p.y >= 0 && p.x < this.width && p.y < this.height;
}

/**
  * Called by by the global click handler when mouse goes up on another sprite
  * @access private
  */
LzSprite.prototype.__globalmouseup = function ( e ){
    if (this.__mouseisdown) {
        this.__mouseisdown = false;

        LzMouseKernel.__sendMouseMove(e);

        // send onmouseup
        this.__mouseEvent('onmouseup');
        // send artificial onmouseupoutside event
        this.__mouseEvent('onmouseupoutside');
    }

    if (LzMouseKernel.__lastMouseOver) {
        // send artificial onmouseover event - see LPP-8445
        LzMouseKernel.__lastMouseOver.__mouseEvent('onmouseover');
    }

    //Debug.info('__globalmouseup', LzMouseKernel.__lastMouseOver, e);
    LzMouseKernel.__lastMouseDown = null;
}



/**
  * Set the main sprite's div focus() so a screen reader will read it.
  */
LzSprite.prototype.aafocus = function( ){
    try {
        if  (this.__LZdiv != null) {
            this.__LZdiv.blur();
            this.__LZdiv.focus();
        }
    } catch (e) {
    }
}


}
