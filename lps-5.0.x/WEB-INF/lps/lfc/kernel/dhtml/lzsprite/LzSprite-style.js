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
 * Static sprite property:  Template of default CSS styles that will
 * be written to DOM once the quirks are evaluated and adjusted
 *
 * @access private
 */
LzSprite.__defaultStyles = {
    lzdiv: {
        position: 'absolute'
        ,borderStyle: 'solid'
        ,borderWidth: 0
        ,pointerEvents: 'none'
    },
    lzclickdiv: {
        position: 'absolute'
        ,borderStyle: 'solid'
        ,borderColor: 'transparent'
        ,borderWidth: 0
    },
    lzcanvasdiv: {
        position: 'absolute'
    },
    lzcanvasclickdiv: {
        zIndex: 100000,
        position: 'absolute'
    },
    lzcanvascontextdiv: {
        position: 'absolute'
    },
    lzappoverflow: {
        position: 'absolute',
        overflow: 'hidden'
    },
    // This container implements the swf 'gutter'
    // we only use overflow: hidden for fixed-size text divs and inputtexts
    lztextcontainer: {
        position: 'absolute',
        // By default our text is not selectable, so we don't want an
        // 'auto' cursor
        cursor: 'default',
        pointerEvents: 'none'
    },
    lzinputtextcontainer: {
        position: 'absolute',
        overflow: 'hidden'
    },
    lzinputtextcontainer_click: {
        position: 'absolute'
    },
    lztext: {
        fontFamily: 'Verdana,Vera,sans-serif',
        fontStyle: 'normal',
        fontWeight: 'normal',
        fontSize: '11px',
        whiteSpace: 'nowrap',
        position: 'absolute',
        textAlign: 'left',
        textIndent: 0,
        letterSpacing: 0,
        textDecoration: 'none',
        pointerEvents: 'none'
    },
    lzswftext: {
        fontFamily: 'Verdana,Vera,sans-serif',
        fontStyle: 'normal',
        fontWeight: 'normal',
        fontSize: '11px',
        whiteSpace: 'nowrap',
        position: 'absolute',
        // To match swf font metrics
        lineHeight: '1.2em',
        textAlign: 'left',
        textIndent: 0,
        letterSpacing: 0,
        textDecoration: 'none',
        // CSS3 browsers, for swf compatibilty
        wordWrap: 'break-word',
        MsWordWrap: 'break-word',  // IE8 See LPP-10097
        MsWordBreak: 'keep-all',
        // To create swf textfield 'gutter'
        padding: '2px',
        pointerEvents: 'none'
    },
    lzinputtext: {
        fontFamily: 'Verdana,Vera,sans-serif',
        fontStyle: 'normal',
        fontWeight: 'normal',
        fontSize: '11px',
        width: '100%',
        height: '100%',
        borderWidth: 0,
        backgroundColor: 'transparent',
        position: 'absolute',
        // There is no scrolling of input elements
        textAlign: 'left',
        textIndent: 0,
        letterSpacing: 0,
        textDecoration: 'none',
        whiteSpace: 'nowrap',
        pointerEvents: 'auto'
    },
    lzswfinputtext: {
        fontFamily: 'Verdana,Vera,sans-serif',
        fontStyle: 'normal',
        fontWeight: 'normal',
        fontSize: '11px',
        width: '100%',
        height: '100%',
        borderWidth: 0,
        backgroundColor: 'transparent',
        position: 'absolute',
        // There is no scrolling of input elements
        // To match swf font metrics
        lineHeight: '1.2em',
        textAlign: 'left',
        textIndent: 0,
        letterSpacing: 0,
        textDecoration: 'none',
        // CSS3 browsers, for swf compatibilty
        wordWrap: 'break-word',
        MsWordWrap: 'break-word',  // IE8 See LPP-10097
        MsWordBreak: 'keep-all',
        outline: 'none',
        // To create swf textfield 'gutter' and position input element correctly
        paddingTop: '1px',
        paddingBottom: '3px',
        paddingRight: '3px',
        paddingLeft: '1px',
        whiteSpace: 'nowrap',
        pointerEvents: 'auto'
    },
    lzswfinputtextmultiline: {
        fontFamily: 'Verdana,Vera,sans-serif',
        fontStyle: 'normal',
        fontWeight: 'normal',
        fontSize: '11px',
        width: '100%',
        height: '100%',
        borderWidth: 0,
        backgroundColor: 'transparent',
        position: 'absolute',
        // When scrollevents are on, this will be overridden
        overflow: 'hidden',
        // To match swf font metrics
        lineHeight: '1.2em',
        textAlign: 'left',
        textIndent: 0,
        letterSpacing: 0,
        textDecoration: 'none',
        // CSS3 browsers, for swf compatibilty
        wordWrap: 'break-word',
        MsWordWrap: 'break-word',  // IE8 See LPP-10097
        MsWordBreak: 'keep-all',
        outline: 'none',
        whiteSpace: 'pre-wrap',
        // To create swf textfield 'gutter' and position input element correctly
        paddingTop: '1px',
        paddingBottom: '3px',
        paddingRight: '3px',
        paddingLeft: '1px',
        resize:'none',
        pointerEvents: 'auto'
    },
    lztextlink: {
        cursor: 'pointer'
    },
    lzaccessibilitydiv: {
       display: 'none'
    },
    lzcontext: {
        position: 'absolute'
        ,borderStyle: 'solid'
        ,borderColor: 'transparent'
        ,borderWidth: 0
        ,pointerEvents: 'auto'
    },
    lzimg: {
        position: 'absolute'
        ,backgroundRepeat: 'no-repeat'
        ,border: '0 none'
        ,pointerEvents: 'none'
    },
    lzgraphicscanvas: {
        position: 'absolute'
    },
    '#lzTextSizeCache': {position: 'absolute', top: '-20000px', left: '-20000px'},

    
    // Blarg.  Why do we have these in here?
    writeCSS: function(isIE) {
        var rules = [];
        var css = '';
        for (var classname in this) {
            if (classname == 'writeCSS' ||
                classname == 'hyphenate' ||
                classname == '__replace' ||
                classname == '__re') continue;
            css += classname.indexOf('#') == -1 ? '.' : '';
            css += classname + '{';
            for (var n in this[classname]) {
                var v = this[classname][n];
                css += this.hyphenate(n) + ':' + v + ';';
            }
            css += '}';
        }
        css += LzFontManager.generateCSS();
        if (isIE) {
            if (!document.styleSheets['lzstyles']) {
                var ss = document.createStyleSheet();
                ss.owningElement.id = 'lzstyles';
                ss.cssText = css;
            }
        } else {
            var o = document.createElement('style');
            o.setAttribute('type', 'text/css');
            o.appendChild( document.createTextNode( css ) );
            document.getElementsByTagName("head")[0].appendChild(o);
        }
    },
    __re: new RegExp('[A-Z]', 'g'),
    hyphenate: function(n) {
        return n.replace(this.__re, this.__replace);
    },
    __replace: function(found) {
        return '-' + found.toLowerCase();
    }
}

/** A hash mapping style names to browser-specific versions - see __updateQuirks
    @access private */
LzSprite.__styleNames = {borderRadius: 'borderRadius', userSelect: 'userSelect', transformOrigin: 'transformOrigin', transform: 'transform', boxShadow: 'boxShadow'};


/**
  * Copies relevant styles from one div to another, e.g. a container div
  * @access private
  */
LzSprite.prototype.__copystyles = function(from, to) {
    var sprite = from.owner;
    var left = sprite._x;
    if (left) {
        to.style.left = left;
    }
    var top = sprite._y;
    if (top) {
        to.style.top = top;
    }
    var display = sprite.__csscache.__LZdivdisplay || '';
    if (display) {
        to.style.display = display;
    }
    to.style.zIndex = sprite._z || from.style.zIndex;
    if (sprite._transform) {
        // copy transform
        var stylename = LzSprite.__styleNames.transform;
        to.style[stylename] = sprite._transform;
    }
    // TODO: Don't we need to copy margin/padding/border, so our
    // children clickcontainers are correctly positioned?
}


// A hash of CSS values, stored with the key divname + stylename, e.g.
// this.__csscache.__LZdivheight === '550px';
LzSprite.prototype.__csscache;
LzSprite.prototype.setCSS = function(name, value, isdimension) {
    if (isdimension) value = this.CSSDimension(value);
    var callback = this['set_' + name];
    //Debug.warn('setCSS', name, value, callback, this);
    if (callback) {
        //Debug.warn('setCSS', value);
        callback.call(this, value);
    } else {
        this.applyCSS(name, value);
        if (this.__LZclickcontainerdiv) {
            this.applyCSS(name, value, '__LZclickcontainerdiv');
        }
        if (this.__LZcontextcontainerdiv) {
            this.applyCSS(name, value, '__LZcontextcontainerdiv');
        }
    }
}

//LzSprite.prototype.csshit = {count: 0};
//LzSprite.prototype.cssmiss = {count: 0};

// Applies a style value to a div.
// TODO: consider batching misses and setting all at once
LzSprite.prototype.applyCSS = function(name, value, divname) {
    if (! divname) divname = '__LZdiv';

    var key = divname + name;
    var cache = this.__csscache;
    if (cache[key] === value) {
//        var csshit = LzSprite.prototype.csshit;
//        if (csshit[key] == null) {
//            csshit[key] = 1;
//        } else {
//            csshit[key]++;
//        }
//        csshit.count++;
        return;
    }

//    var cssmiss = LzSprite.prototype.cssmiss;
//    if (cssmiss[key] == null) {
//        cssmiss[key] = 1;
//    } else {
//        cssmiss[key]++;
//    }
//    cssmiss.count++;
    
    // Look the div up by name, then get its style object
    var styleobject = this[divname].style;
    // update cache and div style object
    cache[key] = styleobject[name] = value;
}


}
