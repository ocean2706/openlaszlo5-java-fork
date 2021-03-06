/**
  * LzLibrary.as
  *
  * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic LZX
  * @subtopic Syntax
  */

/**
  * @lzxname import
  * @initarg stage
  */
class LzLibrary extends LzNode {

/** @access private
  * @modifiers override 
  */
static var tagname = 'import';
/** @access private */
static var attributes = new LzInheritedHash(LzNode.attributes);

/** @access private */
var loaded = false;
/** @access private */
var loading = false;

/**
 * A reference to a target file whose content is treated as a 
 * loadable module.
 * 
 * @keywords final
 * @type string
 * @access public 
 */
var href;

/** 
 * When set to 'defer', the library will not be loaded until its 
 * load-method has been called. Otherwise, the library loads 
 * automatically. 
 * 
 * @keywords final
 * @type string 
 * @lzxdefault "late"
 * @access public 
 */
var stage = "late";//"late|defer"

/**
 * Sent when this library has finished loading. 
 * 
 * @lzxtype event
 * @access public
 */
var onload = LzDeclaredEvent;

/**
  * @access private
  */
function construct (parent, args) {
    this.stage = args.stage;
    super.construct(parent, args);
    LzLibrary.libraries[args.name] = this;
}

/**
  * @access private
  */
function init( ) {
    super.init();
    if (this.stage == "late") {
        this.load();
    }
}

/**
  * @access private
  */
function destroy () {
    super.destroy();
}

/** @access private */
static var libraries = {};

/**
  * @access private
  */
static function findLibrary (libname){
    return LzLibrary.libraries[libname];
}

/**
  * @access private
  */
function toString (){
    return "Library " + this.href + " named " + this.name;
}

/**
  * Loads this library dynamically at runtime. Must only be called 
  * when stage was set to 'defer'.
  * 
  * @access public
  */
function load (){
    if (this.loading || this.loaded) {
        return;
    }
    this.loading = true;
    lz.embed.loadJSLib(this.href);
}

/** 
 * Called by LzLibraryCleanup when this library has finished loading.
 * 
 * @access private 
 */
function loadfinished (){
  this.loading = false;
  if (this.onload.ready) this.onload.sendEvent(true);
}

/** @access private
 */
static function stripQueryString(str:String):String {
    if (str.indexOf('?') > 0) {
        str = str.substring(0,str.indexOf('?'));
    }
    return str;
}

/**
  * Callback for runtime loaded libraries
  * @access private
  */
static function __LZsnippetLoaded (url){
    // find the lib with this url
    // Strip out query string
    url = LzLibrary.stripQueryString(url);
    var lib = null;
    var libs = LzLibrary.libraries;
    for (var l in libs ) {
        var libhref = LzLibrary.stripQueryString(libs[l].href);
        if (libhref == url) {
            lib = libs[l];
            break;
        }
    }
    
    if (lib == null) {
        if ($debug) Debug.error("could not find library with href", url);
    } else {
        lib.loaded = true;
        // The library's parent must be the canvas
        lib.parent.__LzLibraryLoaded(lib.name);
    }
}

}; // End of LzLibrary
lz[LzLibrary.tagname] = LzLibrary;  //publish
