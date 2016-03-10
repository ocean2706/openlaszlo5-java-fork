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


LzSprite.prototype.x = null;
LzSprite.prototype.y = null;
LzSprite.prototype.width = null;
LzSprite.prototype.height = null;

/** Set to a value that won't match the cache but is still a sensible default
  * @access private
  */
LzSprite.prototype._w = '0pt';
/** Set to a value that won't match the cache but is still a sensible default
  * @access private
  */
LzSprite.prototype._h = '0pt';

/**
  * @access private
  */
LzSprite.prototype.__topZ = 1;


LzSprite.prototype.xoffset = 0;
LzSprite.prototype._xoffset = 0;
LzSprite.prototype.setX = function ( x ){
    if (x == null || (x == this.x && this._xoffset == this.xoffset)) return;
    this.__poscacheid = -1;
    this._xoffset = this.xoffset;
    this.x = x;
    x = this.CSSDimension(x + this.xoffset);
    if (this._x != x) {
        this._x = x;
        this.__LZdiv.style.left = x;
        if (this.__LZclickcontainerdiv) {
            this.__LZclickcontainerdiv.style.left = x;
        }
        if (this.__LZcontextcontainerdiv) {
            this.__LZcontextcontainerdiv.style.left = x;
        }
    }
}

LzSprite.prototype.setWidth = function ( w ) {
    if (w < 0 || this.width == w) return;

    //Debug.info('setWidth', w);
    this.width = w;
    w = this.CSSDimension(w);
    if (this._w != w) {
        this._w = w;
        var size = w;
        var quirks = this.quirks;
        this.applyCSS('width', size);
        if (this.__LZcontext) this.__LZcontext.style.width = w; //applyCSS('width', w, '__LZcontext');
        if (this.clip) this.__updateClip();
        if (this.isroot) {
            if (quirks.container_divs_require_overflow) {
                LzSprite.__rootSpriteOverflowContainer.style.width = w;
            }
        } else {
            // only regular sprites have these
            if (this.stretches) this.__updateStretches();
            if (this.backgroundrepeat) this.__updateBackgroundRepeat();
            if (this.__LZclick) this.__LZclick.style.width = w; //applyCSS('width', w, '__LZclick');
            if (this.__LZcanvas) this.__resizecanvas();
        }
        return w;
    }
}

LzSprite.prototype.yoffset = 0;
LzSprite.prototype._yoffset = 0;
LzSprite.prototype.setY = function ( y ){
    //Debug.info('setY', y);
    if (y == null || (y == this.y && this._yoffset == this.yoffset)) return;
    this.__poscacheid = -1;
    this.y = y;
    this._yoffset = this.yoffset;
    y = this.CSSDimension(y + this.yoffset);
    if (this._y != y) {
        this._y = y;
        this.__LZdiv.style.top = y;
        if (this.__LZclickcontainerdiv) {
            this.__LZclickcontainerdiv.style.top = y;
        }
        if (this.__LZcontextcontainerdiv) {
            this.__LZcontextcontainerdiv.style.top = y;
        }
    }
}

LzSprite.prototype.setHeight = function ( h ){
    if (h < 0 || this.height == h) return;

    this.height = h;
    //Debug.info('setHeight', h, this.height, this.owner);
    h = this.CSSDimension(h);
    if (this._h != h) {
        this._h = h;
        var size = h;
        var quirks = this.quirks;
        this.applyCSS('height', size);
        if (this.__LZcontext) this.__LZcontext.style.height = h; //applyCSS('height', h, '__LZcontext');
        if (this.clip) this.__updateClip();
        if (this.isroot) {
            if (quirks.container_divs_require_overflow) {
                LzSprite.__rootSpriteOverflowContainer.style.height = h;
            }
        } else {
            // only regular sprites have these
            if (this.stretches) this.__updateStretches();
            if (this.backgroundrepeat) this.__updateBackgroundRepeat();
            if (this.__LZclick) this.__LZclick.style.height = h;
            if (this.__LZcanvas) this.__resizecanvas();
        }
        return h;
    }
}


LzSprite.prototype.getWidth = function() {
    var w = this.__LZdiv.clientWidth;
    //Debug.info('LzSprite.getWidth', w, this.width, this.owner);
    return w == 0 ? this.width : w;
}

LzSprite.prototype.getHeight = function() {
    var h = this.__LZdiv.clientHeight;
    //Debug.info('LzSprite.getHeight', h, this.height, this.owner);
    return h == 0 ? this.height : h;
}


LzSprite.prototype.bringToFront = function() {
    if (! this.__parent) {
        if ($debug) {
            Debug.warn('bringToFront with no parent');
        }
        return;
    }

    var c = this.__parent.__children;
    if (c.length < 2) return;
    c.sort(LzSprite.prototype.__zCompare);

    this.sendInFrontOf(c[c.length - 1]);
}

/**
  * @access private
  */
LzSprite.prototype.__setZ = function(z) {
    this.__LZdiv.style.zIndex = z;
    var quirks = this.quirks;
    if (quirks.fix_clickable && this.__LZclickcontainerdiv) {
        this.__LZclickcontainerdiv.style.zIndex = z;
    }
    if (quirks.fix_contextmenu && this.__LZcontextcontainerdiv) {
        this.__LZcontextcontainerdiv.style.zIndex = z;
    }
    this.__z = z;
}

/**
  * @access private
  */
LzSprite.prototype.__zCompare = function(a, b) {
   if (a.__z < b.__z)
      return -1
   if (a.__z > b.__z)
      return 1
   return 0
}

LzSprite.prototype.sendToBack = function() {
    if (! this.__parent) {
        if ($debug) {
            Debug.warn('sendToBack with no parent');
        }
        return;
    }

    var c = this.__parent.__children;
    if (c.length < 2) return;
    c.sort(LzSprite.prototype.__zCompare);

    this.sendBehind(c[0]);
}

LzSprite.prototype.sendBehind = function ( behindSprite ){
    if (! behindSprite || behindSprite === this) return;
    if (! this.__parent) {
        if ($debug) {
            Debug.warn('sendBehind with no parent');
        }
        return;
    }

    var c = this.__parent.__children;
    if (c.length < 2) return;
    c.sort(LzSprite.prototype.__zCompare);

    var behindZ = false
    for (var i = 0; i < c.length; i++) {
        var s = c[i];
        if (s == behindSprite) behindZ = behindSprite.__z;
        if (behindZ != false) {
            // bump up everyone including behindSprite
            s.__setZ( ++s.__z );
        }
    }
    // insert where behindSprite used to be
    this.__setZ(behindZ);
}

LzSprite.prototype.sendInFrontOf = function ( frontSprite ){
    if (! frontSprite || frontSprite === this) return;
    if (! this.__parent) {
        if ($debug) {
            Debug.warn('sendInFrontOf with no parent');
        }
        return;
    }

    var c = this.__parent.__children;
    if (c.length < 2) return;
    c.sort(LzSprite.prototype.__zCompare);

    var frontZ = false
    for (var i = 0; i < c.length; i++) {
        var s = c[i];
        if (frontZ != false) {
            // bump up everyone after frontSprite
            s.__setZ( ++s.__z );
        }
        if (s == frontSprite) frontZ = frontSprite.__z + 1;
    }
    // insert after frontSprite
    this.__setZ(frontZ);
}


/**
  * Get the current z order of the sprite
  * @return Integer: A number representing z orderin
  */
LzSprite.prototype.getZ = function () {
    return this.__z;
}

}
