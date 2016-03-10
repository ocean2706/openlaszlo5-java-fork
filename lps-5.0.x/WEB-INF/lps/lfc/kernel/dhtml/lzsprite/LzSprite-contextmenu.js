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


LzSprite.prototype.getContext = function (){
    if (this.__LZcanvas && this.__LZcanvas.getContext) {
        return this.__LZcanvas.getContext("2d");
    }

    var canvas = document.createElement('canvas');
    canvas.owner = this;
    // do we need to do this?
    //canvas.setAttribute('id', canvasuid);

    this.__LZcanvas = canvas;
    canvas.className = 'lzgraphicscanvas';
    // make sure we're behind any children
    if (this.__LZdiv.firstChild) {
        this.__LZdiv.insertBefore(canvas, this.__LZdiv.firstChild);
    } else {
        this.__LZdiv.appendChild(canvas);
    }
    canvas.setAttribute('width', this.width);
    canvas.setAttribute('height', this.height);
    
    // update the cornerradius of the canvas object, to allow clipping
    if (this.cornerradius != null) {
        this.__applyCornerRadius(canvas);
    }

    if (lz.embed.browser.isIE && lz.embed.browser.version < 9) {
        // IE can take a while to init
        this.__maxTries = 10;
        this.__initcanvasie();
    } else {
        return canvas.getContext("2d");
    }
  }
  
// Set the callback for canvas initialization
LzSprite.prototype.setContextCallback = function (callbackscope, callbackname){
    this.__canvascallbackscope = callbackscope;
    this.__canvascallbackname = callbackname;
}


/**
  * LzView.setDefaultContextMenu
  * Install default menu items for the right-mouse-button 
  * @param LzContextMenu cmenu: LzContextMenu to install on this view
  */
LzSprite.prototype.setDefaultContextMenu = function( cmenu ){
    LzSprite.__rootSprite.__contextmenu = cmenu;
}



/**
  * LzView.setContextMenu
  * Install menu items for the right-mouse-button 
  * @param LzContextMenu cmenu: LzContextMenu to install on this view
  */
LzSprite.prototype.setContextMenu = function( cmenu ){
    this.__contextmenu = cmenu;
    if (! this.quirks.fix_contextmenu || this.__LZcontext) return;
    // create contextmenu div
    var cxdiv = document.createElement('div');
    cxdiv.className = 'lzcontext';
    
    if (! this.quirks.fix_clickable) {
       cxdiv.onmousedown = LzSprite.__contextClickDispatcher;  
    }

    if (! this.__LZcontextcontainerdiv) {
        this.__LZcontextcontainerdiv = this.__createContainerDivs('context');
    }
    this.__LZcontextcontainerdiv.appendChild(cxdiv);
    this.__LZcontext = cxdiv;
    //this.applyCSS('width', this._w, '__LZcontext');
    this.__LZcontext.style.width = this._w;
    //this.applyCSS('height', this._h, '__LZcontext');
    this.__LZcontext.style.height = this._h;
    cxdiv.owner = this;
}



/**
* LzView.getContextMenu
* Return the current context menu
*/
LzSprite.prototype.getContextMenu = function() {
    return this.__contextmenu;
}

/**
  * @access private
  * dispatches context view click events, called from the scope of the click div
  */
LzSprite.__contextClickDispatcher = function(e) {
   
    e = e || window.event;
    if (! e) return;

    if (lz.embed.browser.isIE && e.button == 2) {
        return LzMouseKernel.__handleContextMenu(e);
    }
     
    var cmenu = LzMouseKernel.__showncontextmenu;
   
    if (cmenu) {         
      LzContextMenuKernel.lzcontextmenu.hide();
    }
}

}
