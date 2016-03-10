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

/* Calculates width of browser scrollbar */
LzSprite._getScrollbarWidth = function () {
    // Create an offscreen div
   var div = document.createElement('div');
   div.style.width = "50px";
   div.style.height = "50px";
   div.style.overflow = "hidden";
   div.style.position = "absolute";
   div.style.top = "-200px";
   div.style.left = "-200px";

   var div2 = document.createElement('div')
   div2.style.height = '100px';
   div.appendChild(div2);

   var body = document.body;
   body.appendChild(div);
   // Compute width
   var w1 = div.clientWidth;
   // Turn on overflowY = scroll
   div.style.overflowY = 'scroll';
   // Compute new width with scrollbar visible
   var w2 = div.clientWidth;
   LzSprite.prototype.__discardElement(div);
   // return the difference
   return (w1 - w2);
}

/**
 * The canvas fills the root container.  To resize the canvas, we
 * resize the root container.
 *
 * @access private
 */
LzSprite.setRootX = function (v) {
    var rootcontainer = LzSprite.__rootSpriteContainer;
    rootcontainer.style.position = 'absolute';
    rootcontainer.style.left = LzSprite.prototype.CSSDimension(v);
    // Simulate a resize event so canvas sprite size gets updated
    setTimeoutNoArgs(LzScreenKernel.__resizeEvent, 0);
}

/**
 * The canvas fills the root container.  To resize the canvas, we
 * resize the root container.
 *
 * @access private
 */
LzSprite.setRootWidth = function (v) {
    LzSprite.__rootSpriteContainer.style.width = LzSprite.prototype.CSSDimension(v);
    // Simulate a resize event so canvas sprite size gets updated.
    setTimeoutNoArgs(LzScreenKernel.__resizeEvent, 0);
}

/**
 * The canvas fills the root container.  To resize the canvas, we
 * resize the root container.
 *
 * @access private
 */
LzSprite.setRootY = function (v) {
    var rootcontainer = LzSprite.__rootSpriteContainer;
    rootcontainer.style.position = 'absolute';
    rootcontainer.style.top = LzSprite.prototype.CSSDimension(v);
    // Simulate a resize event so canvas sprite size gets updated
    setTimeoutNoArgs(LzScreenKernel.__resizeEvent, 0);
}

/**
 * The canvas fills the root container.  To resize the canvas, we
 * resize the root container.
 *
 * @access private
 */
LzSprite.setRootHeight = function (v) {
    LzSprite.__rootSpriteContainer.style.height = LzSprite.prototype.CSSDimension(v);
    // Simulate a resize event so canvas sprite size gets updated
    setTimeoutNoArgs(LzScreenKernel.__resizeEvent, 0);
}


/**
  * @access private
  */

LzSprite.prototype.opacity = 1;
LzSprite.prototype.visible = true;
LzSprite.prototype.clip = null;


/**
  * @access private
  */
LzSprite.prototype.__LZcontext = null;

/**
  * @access protected
  */
LzSprite.prototype.initted = false;

/** Must be called when the sprite should show itself, usually after the 
  * owner is done initializing 
  */
LzSprite.prototype.init = function(v) {
    //Debug.write('init', this.visible, this.owner.getUID());
    this.setVisible(v);
    if (this.isroot) {
        if (this.quirks['css_hide_canvas_during_init']) {
            var cssname = 'display';
            if (this.quirks['safari_visibility_instead_of_display']) {
                cssname = 'visibility';
            }
            this.__LZdiv.style[cssname] = '';
            if (this.quirks['fix_clickable']) this.__LZclickcontainerdiv.style[cssname] = '';
            if (this.quirks['fix_contextmenu']) this.__LZcontextcontainerdiv.style[cssname] = '';
        }

        // Register the canvas for callbacks
        if (this._id) {
            lz.embed[this._id]._ready(this.owner);
        }
    }
    this.initted = true;
}

/**
  * @access private
  */
LzSprite.prototype.__parent = null;
/**
  * @access private
  */
LzSprite.prototype.__children = null;

/**
  * @access private
  */
if ($debug) {
    LzSprite.__warnonce = {};
}

LzSprite.prototype.addChildSprite = function(sprite) {
    if (sprite.__parent != null) return;
    //Debug.info('appendChild', sprite,'to', this);
    if ($debug) {
        if (this.stretches != null && LzSprite.__warnonce.stretches != true) {
            Debug.warn("Due to limitations in the DHTML runtime, stretches will only apply to the view %w, and doesn't affect child views.", this.owner);
            LzSprite.__warnonce.stretches = true;
        }
    }

    sprite.__parent = this;
    if (this.__children) {
        this.__children.push(sprite);
    } else {
        this.__children = [sprite];
    }

    this.__LZdiv.appendChild( sprite.__LZdiv );
    if (sprite.__LZclickcontainerdiv) {
        // if the sprite has a click container, append it to ours
        if (! this.__LZclickcontainerdiv) {
            // create a click container if we need one
            this.__LZclickcontainerdiv = this.__createContainerDivs('click');
            if (this.clip) this.__updateClip();
        }
        this.__LZclickcontainerdiv.appendChild( sprite.__LZclickcontainerdiv );
    }

    sprite.__setZ(++this.__topZ);
}

LzSprite.prototype.getBaseUrl = function (resource) {
    return LzSprite.__rootSprite.options[resource.ptype == 'sr' ? 'serverroot' : 'approot']
}

/**
 * Handy alias
 * @access private
 */
LzSprite.prototype.CSSDimension = LzKernelUtils.CSSDimension;


/**
  * @access private
  */
LzSprite.prototype.setMaxLength = function ( v ){
    //overridden by LzInputTextSprite
}

/**
  * @access private
  */
LzSprite.prototype.setPattern = function ( v ){
    //overridden by LzTextSprite
}

LzSprite.prototype.setVisible = function ( v ){
    if (this.visible === v) return;
    //Debug.info('setVisible', v, this.owner);
    this.visible = v;
    // use applyCSS to ensure cached value is set - see __processHiddenParents()
    // [2011-04-13 ptw] (LPP-9888) 0-opacity sprites don't want to
    // display, lest they intercept mouse events
    this.applyCSS('display', (this.visible && (this.opacity != 0)) ? '' : 'none');
    // although if they have a clickdiv, that should stay displayed
    var clickdisplay = v ? '' : 'none';
    
    if (this.quirks.fix_clickable) {
        if (this.quirks.fix_ie_clickable && this.__LZclick) {
            this.__LZclick.style.display = v && this.clickable ? '' : 'none'
            //this.applyCSS('display', v && this.clickable ? '' : 'none', '__LZclick');
        }
        if (this.__LZclickcontainerdiv) {
            this.__LZclickcontainerdiv.style.display = clickdisplay;
        }
        //this.applyCSS('display', clickdisplay, '__LZclickcontainerdiv');
    }
    
    if (this.__LZcontextcontainerdiv) {
        this.__LZcontextcontainerdiv.style.display = clickdisplay;
        //this.applyCSS('display', clickdisplay, '__LZcontextcontainerdiv');
    }
     
}

LzSprite.prototype.setBGColor = function ( c ){
    if (c != null && !this.capabilities.rgba) {
        switch (LzColorUtils.alphafrominternal(c)) {
            case 0:
                c = null;
                break;
            default:
                if ($debug) {
                    LzView.__warnCapability('rgba values for view.bgcolor', 'rgba');
                }
            // fall through
            case 1:
                c = LzColorUtils.colorfrominternal(c);
                break;
        }
    }

    if (this.bgcolor === c) return;
    this.bgcolor = c;
    this.__LZdiv.style.backgroundColor = c === null ? 'transparent' : LzColorUtils.torgb(c);
    if (this.quirks.fix_ie_background_height) {
        if (this.height != null && this.height < 2) {
            this.setSource(LzSprite.blankimage, true);
        } else if (! this._fontSize) {
            this.__LZdiv.style.fontSize = 0;
        }
    }
    //Debug.info('setBGColor ' + c);
}

// IE-only, used to manage multiple DX filters, e.g. shadow and opacity
LzSprite.prototype.__filters = null;
LzSprite.prototype.setFilter = function(name, value) {
    if (this.__filters == null) {
        this.__filters = {};
    }
    this.__filters[name] = value;

    var filterstr = '';
    for (var i in this.__filters) {
        filterstr += this.__filters[i];
    }
    return filterstr;
}

LzSprite.prototype.setOpacity = function ( o ){
    // LPP-10028. In IE, don't let opacity go to zero and disable display
    if (lz.embed.browser.isIE && o == 0) {
        o = 0.01;
    }
    if (this.opacity == o || o < 0) return;
    var updateVisibility = ((this.opacity == 0) || (o == 0));
    this.opacity = o;
    // factor used to compute percentage
    var factor = 100;
    if (this.capabilities.minimize_opacity_changes) {
        factor = 10;
    }
    o = ((o * factor) | 0) / factor;
    if (o != this._opacity) { 
        //Debug.info('setOpacity', o);
        this._opacity = o;

        if (this.quirks.ie_opacity) {
            this.__LZdiv.style.filter = this.setFilter('opacity', o == 1 ? '' : "alpha(opacity=" + ((o * 100) | 0) + ")" );
        } else {
            this.__LZdiv.style.opacity = o == 1 ? '' : o;
        }
        if (updateVisibility) {
            // use applyCSS to ensure cached value is set - see __processHiddenParents()
            // [2011-04-13 ptw] (LPP-9888) 0-opacity sprites don't want to
            // display, lest they intercept mouse events
            this.applyCSS('display', (this.visible && (this.opacity != 0)) ? '' : 'none');
        }
    }
}

/** Find parent sprites with a set value, including this sprite. 
    Nearest parents are first in the array
  * @access private
  */
LzSprite.prototype.__findParents = function(prop, value, onlyone) {
    var parents = [];
    var root = LzSprite.__rootSprite;
    var sprite = this;
    while (sprite && sprite !== root) {
        if (sprite[prop] == value) {
            parents.push(sprite);
            if (onlyone) return parents;
        }
        sprite = sprite.__parent;
    }
    return parents;
}

/**
  * Shows all hidden parent sprites, calls the method provided (with optional 
  * additional args), then restores their previous visibility state.
  * 
  * @param Function method: The method to be called after showing parents
  * @return Object: The return value for 'method' if there is one.
  * @access private
  */
LzSprite.prototype.__processHiddenParents = function(method) {
    var sprites = this.__findParents('visible', false);
    //Debug.info('LzSprite.onload', i, i.width, i.height, sprites);
    var l = sprites.length;
    // show all parents
    for (var n = 0; n < l; n++) {
        sprites[n].__LZdiv.style.display = '';
    }

    // call passed-in method with optional args
    var args = Array.prototype.slice.call(arguments, 1);
    var result = method.apply(this, args);

    // restore original display values from CSS cache
    for (var n = 0; n < l; n++) {
        var sprite = sprites[n];
        sprite.__LZdiv.style.display = sprite.__csscache.__LZdivdisplay || '';
    }
    return result;
}

LzSprite.prototype.setClip = function(c) {
    if (this.clip === c) return;
    //Debug.info('setClip', c);
    this.clip = c;
    this.__updateClip();
}

/**
  * @access private
  */
LzSprite.prototype._clip = '';
LzSprite.prototype.__updateClip = function() {
    var quirks = this.quirks;
    if (this.isroot && this.quirks.canvas_div_cannot_be_clipped) return;
    if (this.width == null || this .height == null) return;

    var clipcss = '';
    if (this.clip && this.width >= 0 && this.height >= 0) {
        // have clip, set it
        clipcss = 'rect(0px ' + this._w + ' ' + this._h + ' 0px)';
    } else if (this._clip) {
        // had clip, clear it
        clipcss = quirks.fix_ie_css_syntax ? 'rect(auto auto auto auto)' : '';
    }

    if (clipcss !== this._clip) {
        this._clip = clipcss;
        this.__LZdiv.style.clip = clipcss;
    }

    if (quirks.fix_clickable && this.__LZclickcontainerdiv) {
        this.__LZclickcontainerdiv.style.clip = clipcss;
    }

    if (quirks.fix_contextmenu && this.__LZcontextcontainerdiv) {
        this.__LZcontextcontainerdiv.style.clip = clipcss;
    }
}


LzSprite.prototype.predestroy = function() {
}

LzSprite.prototype.destroy = function( parentvalid = true ) {
    if (this.__LZdeleted == true) return;
    // To keep delegates from resurrecting us.  See LzDelegate#execute
    this.__LZdeleted = true;

    if (parentvalid) {
      // Remove from parent if the parent is not going to be GC-ed
      if (this.__parent) {
        var pc = this.__parent.__children;
        for (var i = pc.length - 1; i >= 0; i--) {
          if (pc[i] === this) {
            pc.splice(i, 1);
            break;
          }
        }
      }
    }

    // images are big...
    if (this.__ImgPool) this.__ImgPool.destroy();
    if (this.__LZimg) this.__discardElement(this.__LZimg);

    // skip discards if the parent isn't valid
    this.__skipdiscards = parentvalid != true;

    if (this.__LZclick) {
        this.__discardElement(this.__LZclick);
    }
    if (this.__LzInputDiv) {
        this.__setTextEvents(false);
        this.__discardElement(this.__LzInputDiv);
    }
    if (this.__LZdiv) {
        if (this.isroot) {
            if (this.quirks.activate_on_mouseover) {
                this.__LZdiv.onmouseover = null;
                this.__LZdiv.onmouseout = null;
                this.__LZdiv.onmouseup = null;
                this.__LZdiv.onmousedown = null;
                this.__LZdiv.onclick = null;
            }
            if (LzSprite.quirks.prevent_selection) {
                this.__LZdiv.onselectstart = null;
            }
        }
        if (LzSprite.quirks.prevent_selection_with_onselectstart) {
            if (this.selectable) {
                this.setSelectable(false);
            }
        }
        this.__discardElement(this.__LZdiv);
    }
    if (this.__LZinputclickdiv) {
        this.__discardElement(this.__LZinputclickdiv);
    }
    if (this.__LZclickcontainerdiv) {
        this.__discardElement(this.__LZclickcontainerdiv);
    }
    if (this.__LZcontextcontainerdiv) {
        this.__discardElement(this.__LZcontextcontainerdiv);
    }
    if (this.__LZcontext) {
        this.__discardElement(this.__LZcontext);
    }
    if (this.__LZtextdiv) {
        this.__discardElement(this.__LZtextdiv);
    }
    if (this.__LZcanvas) {
        if (this.quirks.ie_leak_prevention) {
            // http://blogs.msdn.com/gpde/pages/javascript-memory-leak-detector.aspx complains about these properties
            this.__LZcanvas.owner = null;
            this.__LZcanvas.getContext = null;
        }
        this.__discardElement(this.__LZcanvas);
    }
    this.__ImgPool = null;

    if (this.quirks.ie_leak_prevention) {
        delete this.__sprites[this.uid];
    }
    if (this.isroot) {
        lz.BrowserUtils.scopes = null;
    }
}

/**
  * This method returns the position of the mouse relative to this sprite.
  * 
  * @return Object: The x and y position of the mouse relative to this sprite.
  */
LzSprite.prototype.getMouse = function() {
    // TODO: don't base these metrics on the mouse position
    //Debug.debug('LzSprite.getMouse', this.owner.classname, LzSprite.__rootSprite.getMouse('x'), LzSprite.__rootSprite.getMouse('y'));
    var p = this.__getPos();
    return {x: LzMouseKernel.__x - p.x, y: LzMouseKernel.__y - p.y};
}

/**
  * @access private
  */
LzSprite.prototype.__poscache = null;
/**
  * @access private
  */
LzSprite.prototype.__poscacheid = 0;
/**
  * @access private
  */
LzSprite.__poscachecnt = 0;
/**
  * @access private
  */
LzSprite.prototype.__getPos = function() {
    // Handle LPP-4357
    if (! LzSprite.__rootSprite.initted) {
        return lz.embed.getAbsolutePosition(this.__LZdiv);
    }

    // check if any this sprite or any parents are dirty 
    var dirty = false;
    var attached = true;
    var root = LzSprite.__rootSprite;
    var pp, ppmax;
    for (var p = this; p !== root; p = pp) {
        pp = p.__parent;
        if (pp) {
            if (p.__poscacheid < pp.__poscacheid) {
                // cache is too old or invalid
                dirty = true;
                ppmax = pp;
            }
        } else {
            // not yet attached to the DOM
            attached = false;
            break;
        }
    }

    if (dirty && attached) {
        var next = ++LzSprite.__poscachecnt;
        for (var p = this; p !== ppmax; p = p.__parent) {
            // invalidate all bad caches
            p.__poscache = null;
            p.__poscacheid = next;
        }
    }

    var pos = this.__poscache;
    if (! pos) {
        // compute position, temporarily showing hidden parents so they can be measured
        pos = this.__processHiddenParents(lz.embed.getAbsolutePosition, this.__LZdiv);
        if (attached) {
            // only cache position if the sprite is attached to the DOM (LPP-4357)
            this.__poscache = pos;
        }
    }
    return pos;
}

/**
  * Shows or hides the hand cursor for this view.
  * @param Boolean s: true shows the hand cursor for this view, false hides
  * it
  */
LzSprite.prototype.setShowHandCursor = function ( s ){
    if (s == true) {
        this.setCursor('pointer');
    } else {
        this.setCursor('default');
    }
}

LzSprite.prototype.getDisplayObject = function ( ){
    return this.__LZdiv;
}

// A reference to the html 5 canvas object
LzSprite.prototype.__LZcanvas = null;

/** Overridden when LzSprite.quirks.ie_leak_prevention == true
  * @access private
  */
LzSprite.prototype.__discardElement = function (element) {
    if (this.__skipdiscards) return;
    if (element.parentNode) element.parentNode.removeChild(element);
}


/**
  * @access private
  */
LzSprite.prototype.__setCSSClassProperty = function(classname, name, value) {
    var rulename = document.all ? 'rules' : 'cssRules';
    var sheets = document.styleSheets;
    var sl = sheets.length - 1;
    for (var i = sl; i >= 0; i--) {
        var rules = sheets[i][rulename];
        if (rules && rules['length']) {
            var rl = rules.length - 1;
            for (var j = rl; j >= 0; j--) {
                if (rules[j].selectorText == classname) {
                    rules[j].style[name] = value;
                }
            }
        } else { //Chrome17 may have issue in 'cssRules'.
            Debug.debug("__setCSSClassProperty----unexpected css-", classname, rulename, rules);
        }
    }
}

// build up container tree lazily
LzSprite.prototype.__createContainerDivs = function(typestring){
    // find parents with a null container div
    // e.g. __LZcontextcontainerdiv == null
    var propname = '__LZ' + typestring + 'containerdiv';
    var copyclip = true;

    if (this[propname]) {
        //console.warn(this, 'already has a', propname, this[propname]);
        // we already have one...
        return this[propname];
    }

    var sprites = this.__findParents(propname, null);
    //console.log('found sprites', sprites, 'for', this);

    // create containers root first
    for (var i = sprites.length - 1; i >= 0; i--) {
        var sprite = sprites[i];
        //console.log('found sprite', sprite);

        // create container
        var newdiv = document.createElement('div');
        // text sprites need to use a special class
        newdiv.className = sprite instanceof LzTextSprite ? 'lztextcontainer' : 'lzdiv';

        // Append to parent container div when possible.  Sprites can be created
        // and attached into their parent later.  See addChildSprite() which
        // ensures there is a valid parent container div.
        var parentcontainer = sprite.__parent && sprite.__parent[propname];
        if (parentcontainer) {
            parentcontainer.appendChild(newdiv);
        }

        this.__copystyles(sprite.__LZdiv, newdiv);

        // set id, if we have one...
        if (sprite._id && !newdiv.id) {
            // e.g. 'context' + sprite._id
            newdiv.id = typestring + sprite._id;
        }
        newdiv.owner = sprite;

        // Store the newdiv in the sprite
        sprite[propname] = newdiv;
    }

    // return the last div we created/found
    return newdiv;
}

LzSprite.prototype.rotation = 0;
LzSprite.prototype.setRotation = function(r) {    
    if (this.rotation == r) return;
    this.rotation = r;
    this._rotation = 'rotate(' + r + 'deg) ';
    this.__updateTransform();
}

LzSprite.prototype._transform = '';
LzSprite.prototype.__updateTransform = function(r) {    
    var css = (this._xscale || '') + (this._yscale || '') +  (this._rotation || '');
    
    //ie9 require us to convert to css form manually.
    var browser = lz.embed.browser;
    if (browser.isIE && (browser.version >= 9) ) this.__LZdiv.style['-ms-transform'] = css;
    
    if (css === this._transform) return;
    this._transform = css;

    var stylename = LzSprite.__styleNames.transform;
    this.__LZdiv.style[stylename] = css;
    if (this.__LZclickcontainerdiv) {
        this.__LZclickcontainerdiv.style[stylename] = css;
    }
    if (this.__LZcontextcontainerdiv) {
        this.__LZcontextcontainerdiv.style[stylename] = css;
    }
}

;(function () {
if (LzSprite.quirks.ie_leak_prevention) {
    LzSprite.prototype.__sprites = {};

    // Make sure all references to code inside DIVs are cleaned up to prevent leaks in IE
    function __cleanUpForIE() {
        LzTextSprite.prototype.__cleanupdivs();
        LzTextSprite.prototype._sizecache = {};

        var obj = LzSprite.prototype.__sprites;
        for (var i in obj) {
            obj[i].destroy();
            obj[i] = null;
        }
        LzSprite.prototype.__sprites = {};
    }
    lz.embed.attachEventHandler(window, 'beforeunload', window, '__cleanUpForIE');

    // Overridden 'specially ie_leak_prevention
    LzSprite.prototype.__discardElement = function (element) {
        // Used instead of node.removeChild to eliminate 'pseudo-leaks' in IE - see http://outofhanwell.com/ieleak/index.php?title=Fixing_Leaks
        //alert('__discardElement' + element.nodeType);
        if (! element || ! element.nodeType) return;
        if( ( element.nodeType >= 1 ) && ( element.nodeType < 13 ) )  {
            // ensures element is valid node 
            if (element.owner) element.owner = null;
            var garbageBin = document.getElementById('__LZIELeakGarbageBin');
            if (!garbageBin) {
                garbageBin = document.createElement('DIV');
                garbageBin.id = '__LZIELeakGarbageBin';
                garbageBin.style.display = 'none';
                document.body.appendChild(garbageBin);
            }

            // move the element to the garbage bin
            garbageBin.appendChild(element);
            garbageBin.innerHTML = '';
            //garbageBin.outerHTML = '';
        }
    }
}
})()

// Get any selected text
LzSprite.prototype.getSelectedText = function () {
    if (window.getSelection) { // FF/Safari/Opera/Chrome
        return window.getSelection().toString();
    } else if (document.selection) { // IE7
        return document.selection.createRange().text.toString();
    } else if (document.getSelection) { // others
        return document.getSelection();
    }
}

/**
  * Set accessibility description
  * @param string s: Sets the accessibility name for this view
  */
LzSprite.prototype.setAADescription = function( s ) {
    var aadiv = this.aadescriptionDiv;
    if (aadiv == null) {
        // If not already created, create a <label> element, nested in
        // a <div style='display:none'> to make it invisible
        this.aadescriptionDiv = aadiv = document.createElement('LABEL');
        aadiv.className = 'lzaccessibilitydiv';
        // annotate divs with sprite IDs, but don't override existing IDs!
        if (!this.__LZdiv.id) this.__LZdiv.id = 'sprite_' + this.uid;
        // Safari reader only speaks labels which have a 'for' attribute
        aadiv.setAttribute('for', this.__LZdiv.id);
        this.__LZdiv.appendChild(aadiv);
    }
    aadiv.innerHTML = s;
}


/** Turns accessibility on/off if accessible == true and a screen reader is active 
  * @param Boolean accessible
  */
LzSprite.prototype.setAccessible = function(accessible) {
    // TODO [hqm 2009-06] also need to check LzBrowserKernel.isAAActive() when it is working
    LzSprite.__rootSprite.accessible = accessible;
}

    
/**
 * @access private
 * A cache of accessibility properties
 */
LzSprite.prototype._accProps = null;
    
    
/**
* Activate/inactivate children for accessibility
* @param Boolean s: If true, activate the current view and all of its children
*/
LzSprite.prototype.setAAActive = function( s ){
    this.__LzAccessibilityActive = s;
}


/**
  * Set accessibility silencing/unsilencing
  * @param string s: If true, this view is made silent to the screen reader.  
  * If false, it is active to the screen reader.
  */
LzSprite.prototype.setAASilent = function( s ){
    // Not yet implemented
}


/**
  * Set accessibility name
  * @param string s: Sets the accessibility name for this view
  */
LzSprite.prototype.setAAName = function( s ){
    // Not yet implemented
}

/**
 * Set accessibility tab order
 * @param number s: The tab order index for this view.  Must be a unique number.
 */
LzSprite.prototype.setAATabIndex = function( s ){
    // Not yet implemented
}

/**
  * See view.sendAAEvent()
  */
LzSprite.prototype.sendAAEvent = function(childID, eventType, nonHTML){
    try {
        if  (this.__LZdiv != null) {
            this.__LZdiv.focus();
        }
    } catch (e) {
    }
}

LzSprite.prototype.setID = function(id){
    if (!this._id) this._id = id;
    if (!this.__LZdiv.id) this.__LZdiv.id = this._dbg_typename + id;
    if (this.__LZclickcontainerdiv && !this.__LZclickcontainerdiv.id) this.__LZclickcontainerdiv.id = 'click' + id;
    if (this.__LZcontextcontainerdiv && ! this.__LZcontextcontainerdiv.id) this.__LZcontextcontainerdiv.id = this.__LZcontextcontainerdiv.id = 'context' + id;
}

LzSprite.prototype.__resizecanvas = function() {
    if (this.width > 0 && this.height > 0) {
        if (this.__LZcanvas) {
            this.__LZcanvas.setAttribute('width', this.width);
            this.__LZcanvas.setAttribute('height', this.height);
            // resize, which will clear the canvas
            this.__docanvascallback();
        }
        if (this.__LZcanvas && this['_canvashidden']) {
            this._canvashidden = false;
            this.applyCSS('display', '', '__LZcanvas');
        }
    } else if (this.__LZcanvas && this['_canvashidden'] != true) {
        this._canvashidden = true;
        this.applyCSS('display', 'none', '__LZcanvas');
    }
}

LzSprite.prototype.__docanvascallback = function() {
    var callback = this.__canvascallbackscope[this.__canvascallbackname];
    if (callback) {
        callback.call(this.__canvascallbackscope, this.__LZcanvas.getContext("2d"));
        if (LzSprite.quirks.resize2dcanvas) {
            var canvassize = this.__LZcanvas.firstChild;
            canvassize.style.width = this._w;
            canvassize.style.height = this._h;
        }
    }
}

// IE can take a while to init...
LzSprite.prototype.__initcanvasie = function() {
    // IE can take a while to start up.
    if (this.__canvasTId) clearTimeout(this.__canvasTId);
    try {
        if (this.__LZcanvas && this.__LZcanvas.parentNode != null) {
            this.__LZcanvas = G_vmlCanvasManager.initElement(this.__LZcanvas);
            this.__docanvascallback();
            return;
        }
    } catch (e) {
    }
    if (--this.__maxTries > 0) {
        var callback = lz.BrowserUtils.getcallbackstr(this, '__initcanvasie');
        this.__canvasTId = setTimeoutNoArgs(callback, 50);
    }
}

// Shared by LzSprite and LzTextSprite
LzSprite.prototype.__getShadowCSS = function(shadowcolor, shadowdistance, shadowangle, shadowblurradius) {
    if (shadowcolor == null || (shadowdistance == 0 && shadowblurradius == 0)) {
        return '';
    }
    if (this.capabilities.minimize_opacity_changes) {
        shadowdistance = Math.round(shadowdistance);
        shadowblurradius = Math.round(shadowblurradius);
        shadowangle = Math.round(shadowangle);
    }

    var inset = shadowblurradius < 0 ? 'inset ' : '';
    if (inset !== '') {
        // Flip the distance (inset uses a positive value)
        shadowblurradius = -shadowblurradius;
    }

    if (this.quirks.use_filter_for_dropshadow) {
        // IE doesn't support shadow alpha, see http://msdn.microsoft.com/en-us/library/ms533086%28v=vs.85%29.aspx 
        var rgbcolor = LzColorUtils.inttohex(LzColorUtils.colorfrominternal(shadowcolor));
        if (shadowdistance == 0) {
            // update x and y offsets to compensate for glow
            this.applyCSS('left', this.x - shadowblurradius);
            this.applyCSS('top', this.y - shadowblurradius);

            // Use glow filter when shadowdistance == 0
            return "progid:DXImageTransform.Microsoft.Glow(Color='" + rgbcolor + "',Strength=" + shadowblurradius + ")";
        } else {
            this.applyCSS('left', this.x);
            this.applyCSS('top', this.y);
            // adjust angle to match Flash
            shadowangle += 90;
            return "progid:DXImageTransform.Microsoft.Shadow(Color='" + rgbcolor + "',Direction=" + shadowangle + ",Strength=" + shadowdistance + ")";
        }
    } else {
        // CSS3 doesn't use angle, but x/y offset. So we need to
        // translate from angle and distance to x and y offset for CSS3.
        // Math.cos and Math.cos are based on radians, not degrees
        var radians = shadowangle * Math.PI/180;
        var xoffset = this.CSSDimension(Math.cos(radians) * shadowdistance);
        var yoffset = this.CSSDimension(Math.sin(radians) * shadowdistance);
        // convert to rgb(x,x,x);
        var rgbcolor = LzColorUtils.cssfrominternal(shadowcolor);
        return inset + rgbcolor + " " + xoffset + " " + yoffset + " " + this.CSSDimension(shadowblurradius);
    }
}

LzSprite.prototype.shadow = null;
LzSprite.prototype.updateShadow = function(shadowcolor, shadowdistance, shadowangle, shadowblurradius) {
    var newshadow = this.__getShadowCSS(shadowcolor, shadowdistance, shadowangle, shadowblurradius);
    if (newshadow === this.shadow) return;
    this.shadow = newshadow;

    if (this.quirks.use_filter_for_dropshadow) {
        this.__LZdiv.style.filter = this.setFilter('shadow', newshadow);
    } else {
        var cssname = LzSprite.__styleNames.boxShadow;
        // use the canvas div where available
        if (this.__LZcanvas) {
            // clear out any old shadow style
            this.__LZdiv.style[cssname] = '';
            this.__LZcanvas.style[cssname] = newshadow;
        } else {
            this.__LZdiv.style[cssname] = newshadow;
        }
    }

}

LzSprite.prototype.cornerradius = null;
LzSprite.prototype.cornerradius_h = null;
LzSprite.prototype.cornerradius_v = null;

LzSprite.prototype.setCornerRadius = function(radii) {
    var css = '';
    // The eight values for each radii are given in the order top-left, top-right, bottom-right, bottom-left. horizontal radii followed by vertical radii.
    for (var i = 0, l = radii.length; i < l; i++) {
        radii[i] = this.CSSDimension(radii[i]);
    }
    var css1 = radii.slice(0, 4).join(' ');  // Horizontal radii
    var css2 = radii.slice(4).join(' '); // Vertical radii
    css = css1 + ' / ' + css2;
    if (css == this.cornerradius) return;
    this.cornerradius = css;
    this.cornerradius_h = css1;
    this.cornerradius_v = css2;
    //Debug.info('setCornerRadius %w', css);
    this.__applyCornerRadius(this.__LZdiv);
    if (this.__LZclick) {
        this.__applyCornerRadius(this.__LZclick);
    }
    if (this.__LZcontext) {
        this.__applyCornerRadius(this.__LZcontext);
    }
    if (this.__LZcanvas) {
        this.__applyCornerRadius(this.__LZcanvas);
    }
    if (this.__LZimg) {
        this.__applyCornerRadius(this.__LZimg);
    }
}

LzSprite.prototype.__applyCornerRadius = function(div) {
    var stylenames = LzSprite.__styleNames;
    if (this.quirks.explicitly_set_border_radius) {
        var hradii = this.cornerradius_h.split(' ');
        var vradii = this.cornerradius_v.split(' ');
        //explicitly set the four corners
        div.style[stylenames.borderTopLeftRadius] = hradii[0] + ' ' + vradii[0];
        div.style[stylenames.borderTopRightRadius] = hradii[1] + ' ' + vradii[1];
        div.style[stylenames.borderBottomRightRadius] = hradii[2] + ' ' + vradii[2];
        div.style[stylenames.borderBottomLeftRadius] = hradii[3] + ' ' + vradii[3];
    } else {
        // apply as-is
        div.style[stylenames.borderRadius] = this.cornerradius;
    }
}

LzSprite.prototype.set_borderColor = function(color) {
    if (color == null) color = '';
    this.__LZdiv.style.borderColor = color;
}

LzSprite.prototype.set_borderWidth_internal = function(side, width) {
    if (width == 0) {
        width = '';
    }
    var stylename = 'border' + side + 'Width';
    this.__LZdiv.style[stylename] = width;
    // make sure these match the size of the __LZdiv to catch clicks
    if (this.__LZclick) {
        this.__LZclick.style[stylename] = width;
    }
    if (this.__LZcontext) {
        this.__LZcontext.style[stylename] = width;
    }
    // Don't apply to __LZcanvas or the __LZimg because they're contained by 
    // __LZdiv
}
LzSprite.prototype.set_borderTopWidth = function (width) { this.set_borderWidth_internal('Top', width); }
LzSprite.prototype.set_borderRightWidth = function (width) { this.set_borderWidth_internal('Right', width); }
LzSprite.prototype.set_borderBottomWidth = function (width) { this.set_borderWidth_internal('Bottom', width); }
LzSprite.prototype.set_borderLeftWidth = function (width) { this.set_borderWidth_internal('Left', width); }

LzSprite.prototype.set_padding_internal = function(side, padding) {
    if (padding == 0) {
        padding = '';
    }
    var stylename = 'padding' + side;
    this.__LZdiv.style[stylename] = padding;
    // make sure these match the size of the __LZdiv to catch clicks
    if (this.__LZclick) {
        this.__LZclick.style[stylename] = padding;
    }
    if (this.__LZcontext) {
        this.__LZcontext.style[stylename] = padding;
    }
    // Don't apply to __LZcanvas or the __LZimg because they're contained by 
    // __LZdiv
}
LzSprite.prototype.set_paddingTop = function (padding) { this.set_padding_internal('Top', padding); }
LzSprite.prototype.set_paddingRight = function (padding) { this.set_padding_internal('Right', padding); }
LzSprite.prototype.set_paddingBottom = function (padding) { this.set_padding_internal('Bottom', padding); }
LzSprite.prototype.set_paddingLeft = function (padding) { this.set_padding_internal('Left', padding); }

LzSprite.prototype.set_margin_internal = function(side, margin) {
    if (margin == 0) {
        margin = '';
    }
    var stylename = 'margin' + side;
    this.__LZdiv.style[stylename] = margin;
    // make sure these match the size of the __LZdiv to catch clicks
    if (this.__LZclick) {
        this.__LZclick.style[stylename] = margin;
    }
    if (this.__LZcontext) {
        this.__LZcontext.style[stylename] = margin;
    }
    // Don't apply to __LZcanvas or the __LZimg because they're contained by 
    // __LZdiv
}
LzSprite.prototype.set_marginTop = function (margin) { this.set_margin_internal('Top', margin); }
LzSprite.prototype.set_marginRight = function (margin) { this.set_margin_internal('Right', margin); }
LzSprite.prototype.set_marginBottom = function (margin) { this.set_margin_internal('Bottom', margin); }
LzSprite.prototype.set_marginLeft = function (margin) { this.set_margin_internal('Left', margin); }

LzSprite.medialoadtimeout = 30000;
LzSprite.setMediaLoadTimeout = function(ms){
    LzSprite.medialoadtimeout = ms;
}

LzSprite.setMediaErrorTimeout = function(ms){
    // not needed since we reliably get load errors for images
}

LzSprite.prototype.xscale = 1;
LzSprite.prototype.setXScale = function(xscale) {
    if (this.xscale == xscale) return;
    this.xscale = xscale;
    this._xscale = 'scaleX(' + xscale + ') ';
    this.__updateTransform();
}

LzSprite.prototype.yscale = 1;
LzSprite.prototype.setYScale = function(yscale) {
    if (this.yscale == yscale) return;
    this.yscale = yscale;
    this._yscale = 'scaleY(' + yscale + ') ';
    this.__updateTransform();
}

// End pragma
}
