/* J_LZ_COPYRIGHT_BEGIN *******************************************************
* Copyright 2007-2012, 2011 Laszlo Systems, Inc.  All Rights Reserved.              *
* Use is subject to license terms.                                            *
* J_LZ_COPYRIGHT_END *********************************************************/

// Load fake dom
load("3rd-party/tools/BUFakeDom.js"); 

// some setup to fake what the html embed does 
lz = {};
lz.ClassAttributeTypes = {};
lz.ClassAttributeTypes["Object"] = {};
lz.embed = {__propcache: { appenddiv: document.createElement() },
            options: {serverroot: "RHINO_SERVERROOT", 
                      cancelkeyboardcontrol: false,
                      approot: '', // for DHTML, the root url to load app resources from 
                      usemastersprite: true // if true, dhtml use a single 'master sprite' where possible
            }};

lz.embed.browser = {};

//set up canvas width/height
lz.embed.__propcache.appenddiv.offsetWidth = 800;
lz.embed.__propcache.appenddiv.offsetHeight = 600;
lz.embed.__propcache.options = lz.embed.options;


// Fake the browser DOM
// Laszlo app expects to run in an iframe
window = this;

self = window;

// And share a parent with the debugger
window.parent = {};
// document.write is missing from BUFakeDom, DHTML Sprite wants this
// to write the CSS into the document
document.write = function(args) { }
document.body = document.createElement();
this._root = this;
var top = this; 
var __nexttimerid = 1;
var setTimeout = function() { return __nexttimerid++; }
var setInterval = function() { return __nexttimerid++; }
var clearTimeout = function() { }
var clearInterval = function() { }
var LzIdle = {};
function getVersion() {
    return "rhino-dhtml,linux linux fun"; 
}
var navigator = {userAgent: "lztest",
                appVersion: "1.5R3",
                vendor: "openlaszlo",
                platform: "rhino"}; // This is expected to be in the top level namespace. 
                

lz.embed.attachEventHandler = function () {
  // Just a stub
}                
lz.embed.__setAttr = function () {
  // Just a stub
}                

// Load the LFC!
load("lps/includes/lfc/LFCdhtml-debug.js"); 

// Cover up a few functions that trouble us
LzIdle.update = function() { }

// Make a log file for us to write results to
var lzjumReportWriter = new java.io.FileWriter("lzjum.log", true);
// Spoof the debugger into thinking we have a console with a log
// function
global.console = {
  log: function (str) { lzjumReportWriter.write(str); lzjumReportWriter.flush(); }
}
// Lose the noise
Debug.console.makeObjectLink = function (rep) { return rep; };
// Enable logging
Debug.log_all_writes = true;






