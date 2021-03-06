/**
  * LzFontManager.as
  *
  * @copyright Copyright 2009-2013 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic AS2
  */

/** Manages the font dictionary.
  *
  * @devnote In addition to any fields documented in the section below, these fields 
  * are also available:
  *
  * dictionary <attribute>fonts</attribute>: A hash holding named font objects.
  * Each font object contains slots for the styles that are available
  * (plain, bold, italic, and/or bolditalic).
  */
var LzFontManager = new Object;

LzFontManager.fonts = {};

/**
  * Creates an <class>LzFont</class> with the given parameters and adds it to
  * the <attribute>fonts</attribute> list.
  * 
  * @param fontname: The name of the font.
  * @param fontstyle: The style of the font, "normal" | "italic"
  * @param fontweight: The weight of the font, "normal" | "bold"
  * @param path: The path to the font.  See LzResourceLibrary for details
  * @param ptype: The path type for the font.  See LzResourceLibrary for details
  * @access private
  */
LzFontManager.addFont = function ( fontname, fontstyle, fontweight, path, ptype ){
    var fontobj = {name: fontname, style: fontstyle, weight: fontweight, url: path, ptype: ptype}
    this.fonts[fontname +'_' + fontstyle + '_' + fontweight] = fontobj;
}

// Generates a CSS include for each font added
// See http://randsco.com/index.php/2009/07/04/p680 for details about the IE hack
LzFontManager.generateCSS = function() {
    var fonts = this.fonts;
    var output = '';
    for (var i in fonts) {
        var font = fonts[i];
        var url = this.getURL(font);
        var i = (url.lastIndexOf('.ttf') != -1) ? url.lastIndexOf('.ttf') : url.lastIndexOf('.otf');
        // TODO: check actual extension of url, in case url isn't ttf
        var ttf = url;
        // eot is referenced twice below to workaround IE bug (IE<9)
        var eot = url.substring(0, i) + '.eot';
        var woff = url.substring(0, i) + '.woff';
        output += '@font-face{'
                  + 'font-family:' + font.name + ';'
                  + 'src:url(' + eot + ');'
                  + 'src:local("' + font.name + '"),'
                    + 'url(' + woff + ') format("woff"),'
                    + 'url(' + eot + ') format("embedded-opentype"),'
                    + 'url(' + ttf + ') format("truetype");'
                  + 'font-weight:' + font.weight + ';'
                  + 'font-style:' + font.style + ';'
                  + '}';
    }
    return output;
}

LzFontManager.getURL = function(font) {
    return LzSprite.prototype.getBaseUrl(font) + font.url;
}

// tracks load state for each font url
LzFontManager.__fontloadstate = {counter: 0};
// callbacks for when fonts finish loading
LzFontManager.__fontloadcallbacks = {};
// Returns true if the font is available and loaded
LzFontManager.isFontLoaded = function(sprite, fontname, fontstyle, fontweight) {
    var font = this.fonts[fontname + '_' + fontstyle + '_' + fontweight];
    // No font to load, return true
    if (! font) return true;

    // Check loading state
    var url = this.getURL(font);
    var fontloadstate = this.__fontloadstate[url];
    if (fontloadstate) {
        var loadingstatus = fontloadstate.state;
        if (loadingstatus >= 2) {
            // done loading or timed out
            return true;
        }
    } else {
        // Load font...

        // Create measurement div and measure its initial size
        var style = 'font-family:' + fontname + ';font-style:' + fontstyle + ';font-weight:' + fontweight + ';width:auto;height:auto;';
        var mdiv = this.__createMeasureDiv('lzswftext', style);
        this.__setTextContent(mdiv, 'div', 'Yq_gy"9;ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789-=abcdefghijklmnopqrstuvwxyz');
        mdiv.style.display = 'inline';
        var width = mdiv.clientWidth;
        var height = mdiv.clientHeight;
        mdiv.style.display = 'none';

        // Init loading state
        var fontloadstate = {state: 1, timer: (new Date()).valueOf()};
        this.__fontloadstate[url] = fontloadstate;
        this.__fontloadstate.counter++;

        // Create callback for each font url
        var cstr = lz.BrowserUtils.getcallbackfunc(LzFontManager, '__measurefontdiv', [mdiv, width, height, url]);
        fontloadstate.TID = setIntervalNoArgs(cstr, (Math.random() * 20) + 30);
    }

    // Add sprite to callbacks table
    this.__fontloadcallbacks[sprite.uid] = sprite;
}

// Time before a font load is canceled
LzFontManager.fontloadtimeout = 15000;

LzFontManager.__measurefontdiv = function(mdiv, width, height, url){
    mdiv.style.display = 'inline';
    var newwidth = mdiv.clientWidth;
    var newheight = mdiv.clientHeight;
    mdiv.style.display = 'none';

    var fontloadstate = this.__fontloadstate[url];
    if (newwidth == width && newheight == height) {
        // Size didn't change...
        var timediff = (new Date()).valueOf() - fontloadstate.timer;
        if (timediff < this.fontloadtimeout) {
            // keep loading until timout is reached
            return;
        }
        // Mark as timed out and warn
        fontloadstate.state = 3;
        if ($debug) {
            Debug.warn("Timeout loading font %w: the font size didn't change.", url);
        }
    } else {
        // Mark as loaded
        fontloadstate.state = 2;
    }

    // Finished loading this font
    clearInterval(fontloadstate.TID);
    this.__fontloadstate.counter--;

    // Don't call back until all fonts finish loading
    if (this.__fontloadstate.counter != 0) return;

    // Clear text measurement cache once
    this.__clearMeasureCache();

    // Call back each sprite
    var callbacks = this.__fontloadcallbacks;
    for (var i in callbacks) {
        var sprite = callbacks[i];
        if (sprite) {
            sprite.__fontLoaded();
        }
    }
    delete this.__fontloadcallbacks;
}

LzFontManager.__sizecache = {counter: 0}
LzFontManager.__sizecacheupperbound = 50;
LzFontManager.__rootdiv = null;
LzFontManager.__clearMeasureCache = function() {
  this.__sizecache = {counter: 0}
  if (LzSprite.quirks.ie_leak_prevention) {
    LzTextSprite.prototype.__cleanupdivs();
  }
  if (this.__rootdiv) { this.__rootdiv.innerHTML = ''; }
}

// Return the current size of the cache
LzFontManager.getCacheSize = function() {
    return LzFontManager.__sizecache['counter'];
}

// Get the upper size of the size cache
LzFontManager.getMaxCacheSize = function() {
    return LzFontManager.__sizecacheupperbound;
}

// Set the upper size of the size cache
LzFontManager.setMaxCacheSize = function(size) {
    LzFontManager.__sizecacheupperbound = size;
}



// create container for text size cache
LzFontManager.__createContainerDiv = function() {
    var textsizecache = document.createElement('div');
    textsizecache.setAttribute( 'id', 'lzTextSizeCache');
    document.body.appendChild(textsizecache);
    this.__rootdiv = document.getElementById('lzTextSizeCache');
}

// Compute the width, height or lineheight of a string with a specific style
LzFontManager.getSize = function(dimension, className, style, tagname, string){
    // Full key always includes style and text, even the sample text
    // used to measure line height
    var cacheFullKey = className + "/" + style + "{" + string + "}";
    var __sizecache = this.__sizecache;
    var cv = __sizecache[cacheFullKey];
    if (cv && (dimension in cv)) {
        return cv;
    }
    // Otherwise, compute from scratch
    if ((__sizecache.counter > 0) && ((__sizecache.counter % this.__sizecacheupperbound) == 0)) {
        this.__clearMeasureCache();
        cv = null;
    }
    if (! cv) {
        cv = __sizecache[cacheFullKey] = {};
    }
    // [2010-04-14 max] this.__setTextContent conditionalizes setting the 
    // content based on node type - but we're still using plain old <divs/> for 
    // measurement...
    var divCacheKey = className + "/" + style +  "/" + tagname;
    var mdiv = __sizecache[divCacheKey];
    if (! mdiv) {
        var mdiv = this.__createMeasureDiv(className, style);
        // store measurement div to reuse later...
        __sizecache[divCacheKey] = mdiv;
    }
    this.__setTextContent(mdiv, tagname, string);
    mdiv.style.display = 'inline';
    // NOTE: clientHeight for both height and lineheight
    cv[dimension] = (dimension == 'width') ? mdiv.clientWidth : mdiv.clientHeight;
    mdiv.style.display = 'none';
    //   Debug.debug("%w %w %d", this, cacheFullKey, lineHeight);
    return cv;
}

LzFontManager.__createMeasureDiv = function(className, style) {
  var tagname = 'div';
  var __sizecache = this.__sizecache;
  if (LzSprite.prototype.quirks['text_measurement_use_insertadjacenthtml']) {
    var html = '<' + tagname + ' id="testSpan' + __sizecache.counter + '"';
    html += ' class="' + className + '"';
    html += ' style="' + style + '">';
    html += '</' + tagname + '>';
    this.__rootdiv.insertAdjacentHTML('beforeEnd', html);
    var mdiv = document.all['testSpan' + __sizecache.counter];
    if (LzSprite.prototype.quirks.ie_leak_prevention) {
      LzTextSprite.prototype.__divstocleanup.push(mdiv);
    }
  } else {
    var mdiv = document.createElement(tagname)
    // NOTE: [2009-03-25 ptw] setAttribute needs the real attribute
    // name, i.e., `class` not `classname`!
    mdiv.setAttribute( 'class', className);
    mdiv.setAttribute( 'style', style);
    this.__rootdiv.appendChild(mdiv);
  } 
  __sizecache.counter++;
  return mdiv;
}

LzFontManager.__setTextContent = function(mdiv, tagname, string) {
  // NOTE: [2009-03-29 ptw] For now, DHTML does not support HTML in
  // input text, so we must measure accordingly
  // see LPP-8917
  switch (tagname) {
    case 'div':
      mdiv.innerHTML = string;
      break;
    case 'input': case 'textarea':
      // IE only supports innerText, FF only supports textContent.
      if (LzSprite.prototype.quirks['text_content_use_inner_text']) {
        mdiv.innerText = string;
      } else {
        mdiv.textContent = string;
      }
      break;
    default:
      if ($debug) {
        Debug.error("Unknown tagname: %w", tagname);
      }
  }
}
