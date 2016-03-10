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

/**
  * @access private
  */
LzSprite.prototype.__LZimg = null;

LzSprite.prototype.playing = false;

LzSprite.prototype.frame = 1;
LzSprite.prototype.frames = null;
LzSprite.prototype.is_html = false;    // true means html/rte used
LzSprite.blankimage = 'lps/includes/blank.gif';
LzSprite.prototype.resourceurl = null; // Resource url used
LzSprite.prototype.resource = null;    // Resource specified by user
LzSprite.prototype.source = null;
LzSprite.prototype.stretches = null;
LzSprite.prototype.resourceWidth = null;
LzSprite.prototype.resourceHeight = null;
LzSprite.prototype.cursor = null;
LzSprite.prototype.loading = false;


LzSprite.prototype.setResource = function ( r ){
    if (this.resource == r) return;
    this.resource = r;
    if ( r.indexOf('http:') == 0 || r.indexOf('https:') == 0){
        this.skiponload = false;
        this.setSource( r );
        return;
    }

    var res = LzResourceLibrary[r];
    var urls = this.getResourceUrls(r);
    if (res) {
        this.resourceWidth = res.width;
        this.resourceHeight = res.height;
        if (this.quirks.use_css_sprites) {
            if (this.quirks.use_css_master_sprite && res.spriteoffset != null) {
                // using master sprite
                this.__csssprite = LzSprite.__masterspriteurl;
                this.__cssspriteoffset = res.spriteoffset;
            } else if (res.sprite) {
                // multi-frame sprite resource
                this.__csssprite = this.getBaseUrl(res) + res.sprite;
                this.__cssspriteoffset = 0;
            } else if (urls && urls.length == 1) {
                // single-frame resource, reuse sprite img object
                this.__csssprite = urls[0];
            }
        } else {
            this.__csssprite = null;
            if (this.__bgimage) this.__setBGImage(null);
        }
    }

    this.owner.resourceevent('totalframes', urls.length);
    this.frames = urls;

    if (this.quirks.preload_images && ! (this.stretches == null && this.__csssprite)) {
        this.__preloadFrames();
    }

    this.skiponload = true;
    this.setSource(urls[0], true);
    // multiframe resources should play until told otherwise
    //if (urls.length > 1) this.play();
}

// @devnote also used in lz.drawview.getImage()
LzSprite.prototype.getResourceUrls = function (resourcename) {
    var urls = [];
    // look up resource name in LzResourceLibrary
    // LzResourceLibrary is in the format:
    // LzResourceLibrary.lzscrollbar_xthumbleft_rsc={ptype:"ar"||"sr",frames:["lps/components/lz/resources/scrollbar/scrollthumb_x_lft.png"],width:1,height:12,sprite:"lps/components/lz/resources/scrollbar/scrollthumb_x_lft.sprite.png"}
    var res = LzResourceLibrary[resourcename];
    if (! res) {
        if ($debug) {
            Debug.warn('Could not find resource named %#s', resourcename);
        }
        return urls;
    }

    var baseurl = this.getBaseUrl(res);
    for (var i = 0; i < res.frames.length; i++) {
        urls[i] = baseurl + res.frames[i];
    }
    return urls;
}

LzSprite.prototype.setSource = function (url, usecache){
    if (url == null || url == 'null') {
        this.unload();
        return;
    }

    // Modify the url to strip a leading http: reference (but not http://)
    // See LPP-10057
    var rawurl = url;
    if (url.indexOf('http:') == 0 && url.indexOf('http://') != 0) {
        url = url.substring(5); // Strip off http:
    }

    if (usecache == 'reset') {
        usecache = false;
    } else if (usecache != true){
        // called by a user
        this.skiponload = false;
        this.resource = rawurl;
        this.resourceurl = url;
        if (this.playing) this.stop();
        this.__updateLoadStatus(0);
        this.__csssprite = null;
        if (this.__bgimage) this.__setBGImage(null);
    }
    if (usecache == 'memorycache') {
        // use the memory cache - explictly turned on by the user
        usecache = true;
    }

    //cancel current load
    if (this.loading) {
        if (this.__ImgPool && this.source) {
            this.__ImgPool.flush(this.source);
        }
        this.__destroyImage(null, this.__LZimg);
        this.__LZimg = null;
    }

    //Debug.info('setSource ' + url)
    this.source = rawurl;

    
    if (this.backgroundrepeat) {
        this.__createIMG();
        this.__setBGImage(url);
        this.__updateBackgroundRepeat();
        this.owner.resourceload({width: this.resourceWidth, height: this.resourceHeight, resource: this.resource, skiponload: this.skiponload});
        return;
    } else if (this.stretches == null && this.__csssprite) {
        this.__createIMG();
        this.__LZimg.src = LzSprite.blankimage;
        this.__updateStretches();
        this.__setBGImage(this.__csssprite);
        //Debug.info('setSource ' + this.__LZdiv.style.backgroundImage, url);
        this.owner.resourceload({width: this.resourceWidth, height: this.resourceHeight, resource: this.resource, skiponload: this.skiponload});
        return;
    }

    if (! this.quirks.preload_images) {
        this.owner.resourceload({width: this.resourceWidth, height: this.resourceHeight, resource: this.resource, skiponload: this.skiponload});
    }

    this.loading = true;
    if (! this.__ImgPool) {
        this.__ImgPool = new LzPool(LzSprite.prototype.__getImage, LzSprite.prototype.__gotImage, LzSprite.prototype.__destroyImage, this);
    }
    var im = this.__ImgPool.get(url, usecache != true);
    this.__bindImage(im);

    if (this.loading) {
        //FIXME: [20080203 anba] if this is a single-frame resource, "loading" won't be updated,
        //also see fixme in setResource(..)
        
        //update stretches for IE6, actually only necessary for single-frame resources, 
        //because multi-frame resources get preloaded+stretched beforehand, 
        //but do we ever get a single-frame resource? see fixme in setResource(..)
        if (this.skiponload && this.quirks.ie_alpha_image_loader) this.__updateIEAlpha(im);
    } else {
        //this was a cache-hit
        if (this.quirks.ie_alpha_image_loader) {
            //always update stretches for IE6
            this.__updateIEAlpha(im);
        } else if (this.stretches) {
            this.__updateStretches();
        }
    }
}

LzSprite.prototype.__bindImage = function (im){
    if (this.__LZimg && this.__LZimg.owner) {
        //Debug.write('replaceChild', im.owner, this.__LZimg.owner);
        this.__LZdiv.replaceChild(im, this.__LZimg);
        this.__LZimg = im;
    } else {
        this.__LZimg = im;
        this.__LZdiv.appendChild(this.__LZimg);
    }
    // Set to allow clipping, when backgroundImage/__csssprite is used
    if (this.cornerradius != null) {
        this.__applyCornerRadius(im);
    }
}

LzSprite.prototype.__setBGImage = function (url){
    if (this.__LZimg) {
        var bgurl = url ? "url('" + url + "')" : 'none';
        this.__bgimage = this.__LZimg.style.backgroundImage = bgurl;
    }
    if (bgurl != null) {
        var y = -this.__cssspriteoffset || 0; 
        this.__LZimg.style.backgroundPosition = '0 ' + y + 'px';
    }
}

LzSprite.prototype.__createIMG = function (){
    if (! this.__LZimg) {
        var im = document.createElement('img');
        im.className = 'lzimg';
        im.owner = this;
        im.src = LzSprite.blankimage;
        this.__bindImage(im);
    }
}


;(function () {
/**
  * @access private
  */
if (LzSprite.quirks.ie_alpha_image_loader) {
/**
  * @access private
  */
    LzSprite.prototype.__updateIEAlpha = function(who) {
        var w = this.resourceWidth;
        var h = this.resourceHeight;
        if (this.stretches == 'both') {
            w = '100%';
            h = '100%';
        } else if (this.stretches == 'width') {
            w = '100%';
        } else if (this.stretches == 'height') {
            h = '100%';
        }

        //IE6 needs a width and a height
        if (w == null)
            w = (this.width == null) ? '100%' : this.CSSDimension(this.width);
        if (h == null)
            h = (this.height == null) ? '100%' : this.CSSDimension(this.height);

        who.style.width = w;
        who.style.height = h;
    }
}
})()


LzSprite.prototype.play = function(f) {
    if (! this.frames || this.frames.length < 2) return;
    f = f | 0;
    if (! isNaN(f)) {
        //Debug.info('play ' + f + ', ' + this.frame);
        this.__setFrame(f);
    }
    if (this.playing == true) return;
    this.playing = true;
    this.owner.resourceevent('play', null, true);
    this.owner.resourceevent('playing', true);
    LzIdleKernel.addCallback(this, '__incrementFrame');
}

LzSprite.prototype.stop = function(f) {
    if (! this.frames || this.frames.length < 2) return;
    if (this.playing == true) {
        this.playing = false;
        this.owner.resourceevent('stop', null, true);
        this.owner.resourceevent('playing', false);
        LzIdleKernel.removeCallback(this, '__incrementFrame');
    }
    f = f | 0;
    if (! isNaN(f)) {
        //Debug.info('stop ' + f + ', ' + this.frame);
        this.__setFrame(f);
    }
}

/**
  * @access private
  */
LzSprite.prototype.__incrementFrame = function() {
    // Wrap around to the first frame
    var newframe = this.frame + 1 > this.frames.length ? 1 : this.frame + 1;
    this.__setFrame(newframe);
}

;(function () {
if (LzSprite.quirks.preload_images_only_once) {
    LzSprite.prototype.__preloadurls = {};
}
})()
/**
  * @access private
  */
LzSprite.prototype.__preloadFrames = function() {
    if (! this.__ImgPool) {
        this.__ImgPool = new LzPool(LzSprite.prototype.__getImage, LzSprite.prototype.__gotImage, LzSprite.prototype.__destroyImage, this);
    }
    var l = this.frames.length;
    for (var i = 0; i < l; i++) {
        var src = this.frames[i];
        //Debug.info('preload', src, i != 0);
        if (this.quirks.preload_images_only_once) {
            if (i > 0 && LzSprite.prototype.__preloadurls[src]) {
                continue;
            }
            LzSprite.prototype.__preloadurls[src] = true;
        }
        var im = this.__ImgPool.get(src, false, true);
        if (this.quirks.ie_alpha_image_loader) {
            this.__updateIEAlpha(im);
        }
    }
}


/**
  * @access private
  */
LzSprite.prototype.__imgonload = function(i, cacheHit) {
    if (this.loading != true) return;
    if (this.__imgtimoutid != null) {
        clearTimeout(this.__imgtimoutid);
        this.__imgtimoutid = null;
    }
    this.loading = false;
    // show image div
    if (! cacheHit) {
        if (this.quirks.ie_alpha_image_loader) {
            i._parent.style.display = '';
        } else {
            i.style.display = '';
        }
    }

    this.resourceWidth = (cacheHit && i['__LZreswidth']) ? i.__LZreswidth : i.width;
    this.resourceHeight = (cacheHit && i['__LZresheight']) ? i.__LZresheight : i.height;
    
    if (!cacheHit) {
        if (this.quirks.invisible_parent_image_sizing_fix && this.resourceWidth == 0) {
            // This or any parent divs who aren't visible measure 0x0
            // Make this and all parents visible, measure them, then restore their
            // state
            var f = function(i) {
                this.resourceWidth = i.width;
                this.resourceHeight = i.height;
            }
            this.__processHiddenParents(f, i);
        }
        //TODO: Tear down filtered image and set to new size?
    
        if (this.quirks.ie_alpha_image_loader) {
            i._parent.__lastcondition = '__imgonload';
        } else {
            i.__lastcondition = '__imgonload';
            i.__LZreswidth = this.resourceWidth;
            i.__LZresheight = this.resourceHeight;
        }
    
        //don't update stretches if this was a cache-hit, because __LZimg still points to the prev img
        if (this.quirks.ie_alpha_image_loader) {
            this.__updateIEAlpha(this.__LZimg);
        } else if (this.stretches) {
            this.__updateStretches();
        }
    }
    
    // Tell the view about the load event.
    this.owner.resourceload({width: this.resourceWidth, height: this.resourceHeight, resource: this.resource, skiponload: this.skiponload});
    if (this.skiponload != true){
        // for user-loaded media
        this.__updateLoadStatus(1);
    }
    if (this.quirks.ie_alpha_image_loader) {
        this.__clearImageEvents(this.__LZimg);
    } else {
        this.__clearImageEvents(i);
    }
}


/**
  * @access private
  */
LzSprite.prototype.__imgonerror = function(i, cacheHit) {
    if (this.loading != true) return;
    if (this.__imgtimoutid != null) {
        clearTimeout(this.__imgtimoutid);
        this.__imgtimoutid = null;
    }
    this.loading = false;
    this.resourceWidth = 1;
    this.resourceHeight = 1;
    
    if (!cacheHit) {
        if (this.quirks.ie_alpha_image_loader) {
            i._parent.__lastcondition = '__imgonerror';
        } else {
            i.__lastcondition = '__imgonerror';
        }
    
        //don't update stretches if this was a cache-hit, because __LZimg still points to the prev img
        if (this.quirks.ie_alpha_image_loader) {
            this.__updateIEAlpha(this.__LZimg);
        } else if (this.stretches) {
            this.__updateStretches();
        }
    }
    
    this.owner.resourceloaderror();
    if (this.skiponload != true){
        // for user-loaded media
        this.__updateLoadStatus(0);
    }
    if (this.quirks.ie_alpha_image_loader) {
        this.__clearImageEvents(this.__LZimg);
    } else {
        this.__clearImageEvents(i);
    }
}

/**
  * @access private
  */
LzSprite.prototype.__imgontimeout = function(i, cacheHit) {
    if (this.loading != true) return;
    this.__imgtimoutid = null;
    this.loading = false;
    this.resourceWidth = 1;
    this.resourceHeight = 1;
    
    if (!cacheHit) {
        if (this.quirks.ie_alpha_image_loader) {
            i._parent.__lastcondition = '__imgontimeout';
        } else {
            i.__lastcondition = '__imgontimeout';
        }
    
        //don't update stretches if this was a cache-hit, because __LZimg still points to the prev img
        if (this.quirks.ie_alpha_image_loader) {
            this.__updateIEAlpha(this.__LZimg);
        } else if (this.stretches) {
            this.__updateStretches();
        }
    }
    
    this.owner.resourceloadtimeout();
    if (this.skiponload != true){
        // for user-loaded media
        this.__updateLoadStatus(0);
    }
    if (this.quirks.ie_alpha_image_loader) {
        this.__clearImageEvents(this.__LZimg);
    } else {
        this.__clearImageEvents(i);
    }
}

/**
  * @access private
  */
LzSprite.prototype.__updateLoadStatus = function(val) {
    this.owner.resourceevent('loadratio', val);
    this.owner.resourceevent('framesloadratio', val);
}

/*
 * @devnote: These three methods are called by the image pool.
 * So "this" refers to an LzPool instance and not to this LzSprite.
 * To get the actual sprite use "this.owner".
 */
 
/**
  * @access private
  */
LzSprite.prototype.__destroyImage = function (url, img) {
    if (img) {
        if (img.owner) {
            var owner = img.owner;//= the sprite
            if (owner.__imgtimoutid != null) {
                clearTimeout(owner.__imgtimoutid);
                owner.__imgtimoutid = null;
            }
            //@devnote: remember, this will remove all callback-functions for this sprite!
            lz.BrowserUtils.removecallback(owner);
        }
        LzSprite.prototype.__clearImageEvents(img);
        LzSprite.prototype.__discardElement(img);
    }
    if (LzSprite.quirks.preload_images_only_once) {
        LzSprite.prototype.__preloadurls[url] = null;
    }
}

/**
  * @access private
  * Clears events registered on an image
  */
LzSprite.prototype.__clearImageEvents = function (img) {
    if (! img || img.__cleared) return;
    if (LzSprite.quirks.ie_alpha_image_loader) {
        var sizer = img.sizer;
        if (sizer) {
            //Debug.write('__clearImageEvents'+ sizer.src);
            if (sizer.tId) clearTimeout(sizer.tId);
            sizer.onerror = null;
            sizer.onload = null;
            sizer.onloadforeal = null;
            sizer._parent = null;
            // create dummy object with image properties
            var dummyimg = {width: sizer.width, height: sizer.height, src: sizer.src}
            LzSprite.prototype.__discardElement(sizer);
            img.sizer = dummyimg;
        }
    } else {
        img.onerror = null;
        img.onload = null
    }
    img.__cleared = true;
}

/**
  * @access private
  */
LzSprite.prototype.__gotImage = function(url, obj, skiploader) {
    //Debug.info('got', url, this.owner.resourceWidth, this.owner.resourceHeight);
    // this is calling the sprite
    if (this.owner.skiponload || skiploader == true) {
        //loading a resource (non-http)
        this.owner[obj.__lastcondition]({width: this.owner.resourceWidth, height: this.owner.resourceHeight}, true);
    } else {
        if (LzSprite.quirks.ie_alpha_image_loader) {
            this.owner[obj.__lastcondition](obj.sizer, true);
        } else {
            this.owner[obj.__lastcondition](obj, true);
        }
    }
}

/**
  * @access private
  */
LzSprite.prototype.__getImage = function(url, skiploader) {
    if (LzSprite.quirks.ie_alpha_image_loader) {
        var im = document.createElement('div');
        //im.className = 'lzdiv';//FIXME: LPP-5422
        im.style.overflow = 'hidden';

        if (this.owner && skiploader != true) {
            //Debug.info('sizer', skiploader, skiploader != true);
            im.owner = this.owner;
            if (! im.sizer) {
                im.sizer = document.createElement('img');
                im.sizer._parent = im;
            }
            im.sizer.onload = function() {
                // This resolves all sorts of timing-related image loading bugs
                im.sizer.tId = setTimeoutNoArgs(this.onloadforeal, 1);
            }
            im.sizer.onloadforeal = lz.BrowserUtils.getcallbackfunc(this.owner, '__imgonload', [im.sizer]);
            im.sizer.onerror = lz.BrowserUtils.getcallbackfunc(this.owner, '__imgonerror', [im.sizer]);
            var callback = lz.BrowserUtils.getcallbackfunc(this.owner, '__imgontimeout', [im.sizer]);
            this.owner.__imgtimoutid = setTimeoutNoArgs(callback, LzSprite.medialoadtimeout);
            im.sizer.src = url;
        }
        // show again in onload
        if (! skiploader) im.style.display = 'none'
        if (this.owner.stretches) {
            im.style.filter = "progid:DXImageTransform.Microsoft.AlphaImageLoader(src='" + url + "',sizingMethod='scale')";
        } else {
            im.style.filter = "progid:DXImageTransform.Microsoft.AlphaImageLoader(src='" + url + "')";
        }
    } else {
        var im = document.createElement('img');
        im.className = 'lzimg';
        // show again in onload
        if (! skiploader) im.style.display = 'none'
        if (this.owner && skiploader != true) {
            //Debug.info('sizer', skiploader == true, skiploader != true, skiploader);
            im.owner = this.owner;
            im.onload = lz.BrowserUtils.getcallbackfunc(this.owner, '__imgonload', [im]);
            im.onerror = lz.BrowserUtils.getcallbackfunc(this.owner, '__imgonerror', [im]);
            var callback = lz.BrowserUtils.getcallbackfunc(this.owner, '__imgontimeout', [im]);
            this.owner.__imgtimoutid = setTimeoutNoArgs(callback, LzSprite.medialoadtimeout);

        }
        im.src = url;
    }
    if (im) im.__lastcondition = '__imgonload';
    return im;
}


/**
  * Sets the view so that it stretches its resource in the given axis so that
  * the resource is the same size as the view. The has the effect of distorting
  * the coordinate system for all children of this view, so use this method
  * with care.
  * 
  * @param String s: Set the resource to stretch only in the given axis ("width" or
  * "height").  Otherwise set the resource to stretch in both axes ("both") or 
  * none (any other value).
  */
LzSprite.prototype.stretchResource = function(s) {
    s = (s != "none" ? s : null);//convert "none" to null
    if (this.stretches == s) return;
    this.stretches = s;
    if (! (s == null && this.__csssprite) && this.__bgimage) {
        if (this.quirks.preload_images) this.__preloadFrames();
        // clear out the bgimage
        this.__setBGImage(null);
        // set up default image/imagepool
        this.__setFrame(this.frame, true);
    }
    //TODO: update 'sizingMethod' for IE6
    this.__updateStretches();
}

/**
  * @access private
  */
LzSprite.prototype.__updateStretches = function() {
    if ( this.loading ) return;
    var quirks = this.quirks;
    if (quirks.ie_alpha_image_loader) return;
    var img = this.__LZimg;
    if (img) {
        if (quirks.show_img_before_changing_size) {
            var imgstyle = img.style;
            var olddisplay = imgstyle.display;
            imgstyle.display = 'none';
        }
        if (this.stretches == 'both') {
            img.width = this.width;
            img.height = this.height;
        } else if (this.stretches == 'height') {
            img.width = this.resourceWidth;
            img.height = this.height;
        } else if (this.stretches == 'width') {
            img.width = this.width;
            img.height = this.resourceHeight;
        } else {
            img.width = this.resourceWidth;
            img.height = this.resourceHeight;
        }
        if (quirks.show_img_before_changing_size) {
            imgstyle.display = olddisplay;
        }
    }

}


/**
  * Sets the cursor to the specified cursor ID.  
  * @param String c: cursor ID to use, or '' for default.  See 
  * http://www.quirksmode.org/css/cursor.html for valid IDs 
  * Note that the name should be camelcased, e.g. colResize for the style 
  * col-resize.
  */
LzSprite.prototype.setCursor = function ( c ){
    if (this.quirks.no_cursor_colresize) {
        return;
    }
    if (c === this.cursor) return;
    if (this.clickable !== true) this.setClickable(true);
    this.cursor = c;
    //Debug.write('setting cursor to', c, 'on', this.__LZclick.style); 
    if (this.quirks.fix_clickable) {
        this.__LZclick.style.cursor = LzSprite.__defaultStyles.hyphenate(c);
    } else {
        this.__LZdiv.style.cursor = LzSprite.__defaultStyles.hyphenate(c);
    }
}


/**
  * @access private
  */
LzSprite.prototype.__setFrame = function (f, force){
    if (f < 1) {
        f = 1;
    } else if (f > this.frames.length) {
        f = this.frames.length;
    } 

    var skipevent = false;
    if (force) {
        skipevent = f == this.frame;
    } else if (f == this.frame) {
        return;
    }
    //Debug.info('LzSprite.__setFrame', f);
    this.frame = f;

    var url = this.frames[this.frame - 1];
    if (this.backgroundrepeat) {
        this.__setBGImage(url);
        this.__updateBackgroundRepeat();
    } else if (this.stretches == null && this.__csssprite) {
        // use x axis for now...
        if (! this.__bgimage) {
            this.__createIMG();
            this.__setBGImage(this.__csssprite);
        }
        var x = (this.frame - 1) * (- this.resourceWidth);
        var y = -this.__cssspriteoffset || 0; 
        this.__LZimg.style.backgroundPosition = x + 'px ' + y + 'px';
        //Debug.write('frame', f, x, y, this.__LZdiv.style.backgroundPosition)
    } else {
        // from __updateFrame()    
        this.setSource(url, true);
    }
    if (skipevent) return;
    this.owner.resourceevent('frame', this.frame);
    if (this.frames.length == this.frame) {
        this.owner.resourceevent('lastframe', null, true);
    }
}


LzSprite.prototype.updateResourceSize = function () {
    this.owner.resourceload({width: this.resourceWidth, height: this.resourceHeight, resource: this.resource, skiponload: true});
}

LzSprite.prototype.unload = function () {
    this.resourceurl = null;
    this.resource = null;
    this.source = null;
    this.resourceWidth = null;
    this.resourceHeight = null;
    if (this.__ImgPool) {
        this.__ImgPool.destroy();
        this.__ImgPool = null;
    }
    if (this.__LZimg) {
        this.__destroyImage(null, this.__LZimg);
        this.__LZimg = null;
    }
    this.__updateLoadStatus(0);
}


LzSprite.prototype.backgroundrepeat = null;
LzSprite.prototype.tilex = false;
LzSprite.prototype.tiley = false;
LzSprite.prototype.setBackgroundRepeat = function(backgroundrepeat) {
    if (this.backgroundrepeat == backgroundrepeat) return;
    var x = false;
    var y = false;
    if (backgroundrepeat == 'repeat') {
        x = y = true;
    } else if (backgroundrepeat == 'repeat-x') {
        x = true;
    } else if (backgroundrepeat == 'repeat-y') {
        y = true;
    }
    this.tilex = x;
    this.tiley = y;
    this.backgroundrepeat = backgroundrepeat;
    if (! this.__LZimg) this.__createIMG();
    this.__updateBackgroundRepeat();
    if (backgroundrepeat) {
        this.__setBGImage(this.source);
        this.__LZimg.src = LzSprite.blankimage;
    } else {
        if (this.__bgimage) this.__setBGImage(null);
        // reset to default 
        backgroundrepeat = '';
        this.skiponload = true;
        this.setSource(this.source, 'reset');
    }
    this.__LZdiv.style.backgroundRepeat = backgroundrepeat;
}

LzSprite.prototype.__updateBackgroundRepeat = function() {
    if (this.__LZimg) {
        this.__LZimg.style.backgroundRepeat = this.backgroundrepeat;
        this.__LZimg.style.backgroundPosition = '0 0';
        this.__LZimg.width = this.backgroundrepeat ? this.width : this.resourceWidth;
        this.__LZimg.height = this.backgroundrepeat ? this.height : this.resourceHeight;
    }
}


}
