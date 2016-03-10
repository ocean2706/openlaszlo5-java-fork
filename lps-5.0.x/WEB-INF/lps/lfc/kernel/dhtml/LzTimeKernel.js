/**
  * LzTimeKernel.lzs
  *
  * @copyright Copyright 2001-2009, 2011-2013 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic JS1
  */

// Receives and sends timing events


// setTimeout for method with no arguments. Use this instead of setTimeout
// because it will eliminate a closure on IE
setTimeoutOrig = window.setTimeout;
setTimeoutNoArgs = window.setTimeout;
setTimeoutOneArg = function(callback, delay, arg)
{
    var cb = function() { callback(arg);}
    return setTimeoutOrig(cb, delay);
}

setIntervalNoArgs = window.setInterval;


;(function () {
// On IE6/7 window.setTimeout, window.setInterval cannot be called using
// apply(). A workaround can be found here:
// http://webreflection.blogspot.com/2007/06/simple-settimeout-setinterval-extra.html

if (LzSprite.quirks.ie_timer_closure) {
  (function(f){
    window.setTimeout = f(window.setTimeout);
    window.setInterval = f(window.setInterval);
  })(function(f){
    return function(c,t){
      // console.log("v1: timer_closure running ", t, c);
      var a = Array.prototype.slice.call(arguments,2);
      if(typeof c != "function")
        c = new Function(c);
        return f(function(){
        c.apply(this, a)
      }, t)
    }
  });
}
})()

var LzTimeKernel = {
    setTimeout: function() {
        return window.setTimeout.apply(window, arguments);
    }
    ,setInterval: function() {
        return window.setInterval.apply(window, arguments);
    }
    ,clearTimeout: function(id) {
        return window.clearTimeout(id);
    }
    ,clearInterval: function(id) {
        return window.clearInterval(id);
    }

    ,setTimeoutOneArg: function(callback, delay, arg) {
        var cb = function() { callback(arg);}
        return setTimeoutNoArgs(cb, delay);
    }

    ,setTimeoutTwoArg: function(callback, delay, arg1, arg2) {
        var cb = function() { callback(arg1, arg2);}
        return setTimeoutNoArgs(cb, delay);
    }

    ,setTimeoutNoArgs: function(callback, delay) {
        return setTimeoutNoArgs(callback, delay);
    }

    // Implement actionscript API to get ms since startup time 
    ,startTime: (new Date()).valueOf()   
    ,getTimer: function() {
        return (new Date()).valueOf() - LzTimeKernel.startTime;
    }    
}
