/**
  * LzKeyboardKernel.as
  *
  * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic swf9
  */

// Receives keyboard events from the runtime
class LzKeyboardKernel {
    #passthrough (toplevel:true) {
    import flash.events.KeyboardEvent;
    }#

    // __keyboardEvent requies a special case for IE (LPP-9999)
    // (Set in LzSprite)
    static var __isIE:Boolean = false;
    static var __lastdown:Number = 0;

    // Event processor, may receive KeyboardEvents or artificial event objects
    static function __keyboardEvent (e) :void {   
        var t:String = 'on' + e.type.toLowerCase();
        var delta:Object = {};
        var k:uint = e.keyCode;
        var keyisdown:Boolean = (t == 'onkeydown');
        // workaround for LPP-9869 [FP-6157]
        if ((k == 0 || k == 65) && e.charCode == 0) {
          if (keyisdown) {
            k = (e.shiftKey && __keyState[16] == null) ? 16 :
              (e.ctrlKey && __keyState[17] == null) ? 17 : k;
          } else {
            k = (! e.shiftKey && __keyState[16] != null) ? 16 :
              (! e.ctrlKey && __keyState[17] != null) ? 17 : k;
          }
        }
        var s:String = String.fromCharCode(k).toLowerCase();

       // Remember the shift state (LPP-10063)
       __shiftState = e.shiftKey;

        // prevent duplicate onkeydown events - see LPP-7432 
        if ((__keyState[k] != null) == keyisdown) {
            // IE doesn't always get a keyup (LPP-9999). This ruins tabbing
            if (!__isIE || k != 9) {
                return; // Ignore the key 
            }
            else {
                // Prevent too many tabs that can skip over components.
                var now:Number = new Date().getTime();
                var elapsed = now - __lastdown;
                __lastdown = now;
                // Ignore the keydown if 200 msec has not elapsed
                if (elapsed < 200) {
                    return;
                }
            }
        }
        __keyState[k] = keyisdown?k:null;
        __lastdown = new Date().getTime();

        delta[s] = keyisdown;
        var ctrl:Boolean = e.ctrlKey;
        var shft:Boolean = e.shiftKey; // LPP-10082

        //Debug.info('__keyboardEvent', e, 'alt=', e.altKey, 'control', e.ctrlKey, e.shiftKey);

        if (__callback) __scope[__callback](delta, k, t, ctrl, shft);
    }

    static var __callback:String = null;
    static var __scope:* = null;
    static var __keyState:Object = {};
    static var __shiftState:Boolean = false;
    static var __listeneradded:Boolean = false;

    static function setCallback (scope:*, funcname:String) :void {
        if (__listeneradded == false) {
            __scope = scope;
            __callback = funcname;
            LFCApplication.stage.addEventListener(KeyboardEvent.KEY_DOWN, __keyboardEvent);
            LFCApplication.stage.addEventListener(KeyboardEvent.KEY_UP, __keyboardEvent);
            __listeneradded = true;
        }
    }    

    // Called by lz.embed when the browser window regains focus
    static function __allKeysUp () {
        var delta = null;
        var stuck = false;
        var keys = null;
        for (var key in __keyState) {
          if (__keyState[key] != null) {
            stuck = true;
            if (! delta) { delta = {}; }
            delta[key] = false;
            if (! keys) { keys = []; }
            keys.push(__keyState[key]);
            __keyState[key] = null;
          }
        }
//         Debug.info("[%6.2f] All keys up: %w", (new Date).getTime() % 1000000, delta);
        if (stuck && __scope && __scope[__callback]) {
          if (!keys) {
            __scope[__callback](delta, 0, 'onkeyup');
          } else for (var i = 0, l = keys.length; i < l; i++) {
            __scope[__callback](delta, keys[i], 'onkeyup');
          }
        }
    }

    // Called by lz.Keys when the last focusable element was reached.
    static function gotLastFocus() :void {
    }
    // Called to turn on/off restriction of focus to this application
    static function setGlobalFocusTrap(ignore) :void {
    }

    // Simplified keyboard event sender for external browser tab key events
    static function __browserTabEvent (shiftdown) :void {   
        //Debug.warn('__browserTabEvent', shiftdown);
        if (shiftdown) {
            LzKeyboardKernel.__keyboardEvent({type: 'keyDown', keyCode: 16, altKey: false, ctrlKey: false}); 
        }

        LzKeyboardKernel.__keyboardEvent({type: 'keyDown', keyCode: 9, altKey: false, ctrlKey: false});
        // For IE, do not send a key up event. There is a workaround in this file to
        // address LPP-9999/LPP-10006 and a key up might cause focus to skip twice depending
        // upon the order that events run.
        if (!__isIE) {
            LzKeyboardKernel.__keyboardEvent({type: 'keyUp', keyCode: 9, altKey: false, ctrlKey: false});
        }

        if (shiftdown) {
            // Sending a key up event now prevents subsequent shift-tabs from working...
            //LzKeyboardKernel.__keyboardEvent({type: 'keyUp', keyCode: 16, altKey: false, ctrlKey: false}); 
        }
    }
} // End of LzKeyboardKernel
