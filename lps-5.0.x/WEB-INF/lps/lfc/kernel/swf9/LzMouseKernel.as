/**
  * LzMouseKernel.as
  *
  * @copyright Copyright 2001-2011 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic swf9
  */

// Receives mouse events from the runtime
class LzMouseKernel  {
    #passthrough (toplevel:true) {
    import flash.display.Sprite;
    import flash.display.DisplayObject;
    import flash.events.Event;
    import flash.events.MouseEvent;
    import flash.events.ContextMenuEvent;
    import flash.text.TextField;
    import flash.ui.*;
    import flash.utils.getDefinitionByName;
    }#


    //////////////////
    // MOUSE EVENTS //
    //////////////////

    // sends mouse events to the callback
    static function handleMouseEvent (view:*, eventname:String) :void {
        // workaround FF3.6 Mac bug - see LPP-8831
        if (LzSprite.quirks.ignorespuriousff36events) {
            if (eventname == 'onclick') {
                __ignoreoutover = true;
            } else if (__ignoreoutover) {
                if (eventname == 'onmouseout') {
                    // skip event
                    return;
                } else if (eventname == 'onmouseover') {
                    // allow events to propagate again
                    __ignoreoutover = false;
                    return;
                }
            }
        } 
        if (__callback) __scope[__callback](eventname, view);
        //Debug.write('LzMouseKernel event', eventname);
    }

    /**
     * Called from LzContextMenu to handle any quirks.
     */
    static function createdContextMenu(cm:ContextMenu) :void {
        // Don't set up the handler conditionally on quirks,
        // since quirks may not be set up by the time the
        // context menu is established.
        cm.addEventListener(ContextMenuEvent.MENU_SELECT, __menuSelectHandler);
    }

    /**
     * Workaround extramenuevents quirk for FF 3.6/Windows.
     * This is an extra event listener for any context menu.  If the 
     * ignoreextramenuevents quirk is on, we set __inContextMenu
     * as a state variable.
     *
     * The quirk is based on the fact that for FF 3.6/Windows,
     * opening a context menu produces these events:
     *   MOUSE_MOVE  (in the mouse kernel)   [__mouseHandler]
     *   MOUSE_OVER  (in the mouse kernel)   [LzSprite.__mouseEvent]
     *   MENU_SELECT (on the context menu)   [__menuSelectHandler:    ?->2]
     *   MOUSE_MOVE  (in the mouse kernel)   [__mouseHandler:         2->1]
     *   MOUSE_OUT   (when over a sprite)    [LzSprite.__mouseEvent:  1->1]
     *   MOUSE_LEAVE (in the mouse kernel)   [__mouseLeaveHandler:    1->0]
     *
     * Other browsers just produce MENU_SELECT.  To compound the troubles,
     * the initial MOUSE_MOVE/MOUSE_OVER occasionally have bogus coordinates.
     * Our solution is two-fold.  First, we eliminate the
     * MOUSE_MOVE, MOUSE_OUT and MOUSE_LEAVE events when they occur after
     * a MENU_SELECT.  That happens here by setting __inContextMenu.
     * In brackets[] above we show the handler where each event is
     * processed and the value of __inContextMenu at the beginning
     * and end of the handler.  See also LPP-9957.
     * See ignoreMouseEvent() for the other part of the solution.
     */
    static function __menuSelectHandler (e:ContextMenuEvent) :void {
        //Debug.write(">> menu select");
        if (LzSprite.quirks.ignoreextramenuevents) {
            __inContextMenu = 2;
        }
    }

    static var __ignoreoutover:Boolean = false;
    static var __callback:String = null;
    static var __scope:* = null;
    static var __lastMouseDown:LzSprite = null;
    static var __mouseLeft:Boolean = false;
    static var __listeneradded:Boolean = false;
    static var __inContextMenu:int = 0;     // described in __menuSelectHandler
    static var __prevStageX:int = -1;
    static var __prevStageY:int = -1;
    static var __lastStageX:int = -1;
    static var __lastStageY:int = -1;

    /**
     * Shows or hides the hand cursor for all clickable views.
     */
    static var showhandcursor:Boolean = true;

    static function setCallback (scope:*, funcname:String) :void {
        __scope = scope;
        __callback = funcname;
        if (__listeneradded == false) {
            /* TODO [hqm 2008-01] Do we want to do anything with other
            * events, like click?
            stage.addEventListener(MouseEvent.CLICK, reportClick);
            */

            LFCApplication.stage.addEventListener(MouseEvent.MOUSE_MOVE, __mouseHandler);
            LFCApplication.stage.addEventListener(MouseEvent.MOUSE_UP,   __mouseHandler);
            LFCApplication.stage.addEventListener(MouseEvent.MOUSE_DOWN, __mouseHandler);
            // handled by lz.embed.mousewheel for Windows LFCApplication.stage.addEventListener(MouseEvent.MOUSE_WHEEL, __mouseWheelHandler);
            LFCApplication.stage.addEventListener(Event.MOUSE_LEAVE, __mouseLeaveHandler);
            __listeneradded = true;
        }
    }

    /* True iff original (X0,Y0) is undefined or (X1,Y0) is 'near' to it */
    static function nearXY(X0:int, Y0:int, X1:int, Y1:int)
    {
        return ((X0 < 0 && Y0 < 0) ||
                (Math.abs(X0 - X1) < 50 && Math.abs(Y0 - Y1) < 50));
    }

    /*
     * Workaround extramenuevents quirk for FF 3.6/Windows.
     * See comment for menuSelectHandler above.  Here we eliminate the
     * MOUSE_MOVE and MOUSE_OVER events that have bad coordinates.
     * These immediately precede a MENU_SELECT event, but there's no
     * way to tell that's forthcoming.  Our best bet is to to track
     * the coordinates: most recent ones are in __lastStage{X,Y}, ones
     * before that in __prevStage{X,Y}.  Since big mouse movements are
     * possible, we tailor our checks as much as possible to the bug
     * conditions to avoid removing valid events.
     *
     * This implementation relies on the fact that MOUSE_MOVE is handled
     * exactly once (in LzMouseKernel) and MOUSE_OVER is handled exactly
     * once (in LzSprite).  If that changes, so must this.
     *
     * See also LPP-9967.
     */
    static function ignoreMouseEvent (eventname:String, event:MouseEvent) :Boolean {
         var near:Boolean = false;
         if (eventname == 'onmousemove') {
           near = nearXY(__lastStageX, __lastStageY, event.stageX, event.stageY);
         }
         else if (eventname == 'onmouseover') {
           near = nearXY(__prevStageX, __prevStageY, event.stageX, event.stageY);
         }
         else {
             return false;
         }
         __prevStageX = __lastStageX;
         __prevStageY = __lastStageY;
         __lastStageX = event.stageX;
         __lastStageY = event.stageY;
         return !near;
    }

    // Handles global mouse events
    static function __mouseHandler (event:MouseEvent) :void {
        var eventname:String = 'on' + event.type.toLowerCase();
        //if (eventname != 'onmousemove') {Debug.write(">> mouse: " + eventname);}

        // workaround FF3.6 bug: remove certain extra events
        // generated after a context menu - LPP-9957 and LPP-9967
        if (__inContextMenu > 0) {
            __inContextMenu--;
            if (eventname == 'onmousemove') {
                return;
            }
        }
        if (LzSprite.quirks.ignoreextramenuevents && ignoreMouseEvent(eventname, event)) {
            //Debug.write(">>>> removed mouse: " + eventname);
            return;
        }
        if (eventname == 'onmouseup' && __lastMouseDown != null) {
            // call mouseup on the sprite that got the last mouse down
            __lastMouseDown.__globalmouseup(event);
            __lastMouseDown = null;
        } else {
            if (__mouseLeft) {
                __mouseLeft = false;
                // Mouse reentered the app.
                if (event.buttonDown) __mouseUpOutsideHandler();

                if (LzSprite.quirks.fixtextselection) {
                    // TODO [hqm 2009-04] LPP-7957 -- this works
                    // around Firefox bug; if you are making a text
                    // selection, and then drag the mouse outside of
                    // the app while the left button is down, and then
                    // release it. When you bring the mouse back into
                    // the app, the selection is stuck dragging
                    // because Flash plugin never gets the mouse up.
                    LFCApplication.stage.focus = null;
                }

                // generate a 'onmouseenter' event
                handleMouseEvent(null, 'onmouseenter');
            }
            handleMouseEvent(null, eventname);
        }
    }

    // sends mouseup and calls __globalmouseup when the mouse goes up outside the app - see LPP-7724
    static function __mouseUpOutsideHandler () :void {
        if (__lastMouseDown != null) {
            var ev:MouseEvent = new MouseEvent('mouseup');
            __lastMouseDown.__globalmouseup(ev);
            __lastMouseDown = null;
        }
    }

    // handles MOUSE_LEAVE event
    static function __mouseLeaveHandler (event:Event = null) :void {
        //Debug.write(">> mouse leave");
        if (__inContextMenu > 0) {
           // workaround FF3.6 bug: remove extra MOUSE_LEAVE event
           // generated after a context menu - LPP-9957
           __inContextMenu = 0;
           __lastStageX = -1;
           __lastStageY = -1;
           __prevStageX = -1;
           __prevStageY = -1;
           return;
        }
        __mouseLeft = true;
        handleMouseEvent(null, 'onmouseleave');
    }

//    static function __mouseWheelHandler (event:MouseEvent) :void {
//        lz.Keys.__mousewheelEvent(event.delta);
//    }


    //////////////////
    // MOUSE CURSOR //
    //////////////////

    /**
    * Shows or hides the hand cursor for all clickable views.
    * @param Boolean show: true shows the hand cursor for buttons, false hides it
    */
    static function showHandCursor (show:Boolean) :void {
        showhandcursor = show;
        LzSprite.rootSprite.setGlobalHandCursor(show);
    }

    static var __amLocked:Boolean = false;
    static var useBuiltinCursor:Boolean = false;
    static var cursorSprite:Sprite = null;
    static var cursorOffsetX:int = 0;
    static var cursorOffsetY:int = 0;
    static var globalCursorResource:String = null;
    static var lastCursorResource:String = null;

    #passthrough {
        private static var __MouseCursor:Object = null;
        private static function get MouseCursor () :Object {
            if (__MouseCursor == null) {
                __MouseCursor = getDefinitionByName('flash.ui.MouseCursor');
            }
            return __MouseCursor;
        }

        private static var __builtinCursors:Object = null;
        static function get builtinCursors () :Object {
            if (__builtinCursors == null) {
                var cursors:Object = {};
                if ($swf10) {
                    cursors[MouseCursor.ARROW] = true;
                    cursors[MouseCursor.AUTO] = true;
                    cursors[MouseCursor.BUTTON] = true;
                    cursors[MouseCursor.HAND] = true;
                    cursors[MouseCursor.IBEAM] = true;
                }
                __builtinCursors = cursors;
            }
            return __builtinCursors;
        }

        static function get hasGlobalCursor () :Boolean {
            var gcursor:String = globalCursorResource;
            if ($swf10) {
                return ! (gcursor == null || (gcursor == MouseCursor.AUTO && useBuiltinCursor));
            } else {
                return ! (gcursor == null);
            }
        }
    }#

    /**
    * Sets the cursor to a resource
    * @param String what: The resource to use as the cursor.
    */
    static function setCursorGlobal (what:String) :void {
        globalCursorResource = what;
        setCursorLocal(what);
    }

    static function setCursorLocal (what:String) :void {
        if ( __amLocked ) { return; }
        if (what == null) {
            // null is invalid, maybe call restoreCursor()?
            return;
        } else if (lastCursorResource != what) {
            var resourceSprite:DisplayObject = getCursorResource(what);
            if (resourceSprite != null) {
                if (cursorSprite.numChildren > 0) {
                    cursorSprite.removeChildAt(0);
                }
                cursorSprite.addChild( resourceSprite );
                useBuiltinCursor = false;
            } else if (builtinCursors[what] != null) {
                useBuiltinCursor = true;
            } else {
                // invalid cursor?
                return;
            }
            lastCursorResource = what;
        }
        if (useBuiltinCursor) {
            if ($swf10) { Mouse['cursor'] = what; }
            cursorSprite.stopDrag();
            cursorSprite.visible = false;
            LFCApplication.stage.removeEventListener(Event.MOUSE_LEAVE, mouseLeaveHandler);
            LFCApplication.stage.removeEventListener(MouseEvent.MOUSE_MOVE, mouseMoveHandler);
            Mouse.show();
        } else {
            // you can only hide the Mouse when Mouse.cursor is AUTO
            if ($swf10) { Mouse['cursor'] = MouseCursor.AUTO; }
            Mouse.hide();
            cursorSprite.x = LFCApplication.stage.mouseX + cursorOffsetX;
            cursorSprite.y = LFCApplication.stage.mouseY + cursorOffsetY;
            LFCApplication.setChildIndex(cursorSprite, LFCApplication._sprite.numChildren-1);
            // respond to mouse move events
            cursorSprite.startDrag();
            LFCApplication.stage.addEventListener(Event.MOUSE_LEAVE, mouseLeaveHandler);
            LFCApplication.stage.addEventListener(MouseEvent.MOUSE_MOVE, mouseMoveHandler);
            cursorSprite.visible = true;
        }
    }

    /**
     * Triggered when cursor leaves screen or context-menu is opened, in both
     * cases we must display the system cursor
     */
    private static function mouseLeaveHandler (event:Event) :void {
        cursorSprite.visible = false;
        Mouse.show();
        // use capture-phase because most sprites call stopPropagation() for mouse-events
        LFCApplication.stage.addEventListener(MouseEvent.MOUSE_OVER, mouseEnterHandler, true);
    }

    /**
     * Triggered when cursor enters screen or context-menu is closed
     */
    private static function mouseEnterHandler (event:Event) :void {
        cursorSprite.visible = true;
        Mouse.hide();
        LFCApplication.stage.removeEventListener(MouseEvent.MOUSE_OVER, mouseEnterHandler, true);
    }

    /**
     * Hide custom cursor over selectable TextFields otherwise both the ibeam-cursor
     * and the custom cursor are displayed
     */
    private static function mouseMoveHandler (event:MouseEvent) :void {
        var target:Object = event.target;
        if (target is TextField && target.selectable) {
            cursorSprite.visible = false;
            Mouse.show();
        } else {
            Mouse.hide();
            cursorSprite.visible = true;
        }
    }

    private static function getCursorResource (resource:String) :DisplayObject {
        var resinfo:Object = LzResourceLibrary[resource];
        var assetclass:Class;
        if (resinfo == null) {
            return null;
        }
        // single frame resources get an entry in LzResourceLibrary which has
        // 'assetclass' pointing to the resource Class object.
        if (resinfo.assetclass is Class) {
            assetclass = resinfo.assetclass;
        } else {
            // Multiframe resources have an array of Class objects in frames[]
            var frames:Array = resinfo.frames;
            assetclass = frames[0];
        }

        var asset:DisplayObject = new assetclass();
        asset.scaleX = 1.0;
        asset.scaleY = 1.0;

        if (resinfo['offsetx'] != null) {
            cursorOffsetX = resinfo['offsetx'];
        } else {
            cursorOffsetX = 0;
        }
        if (resinfo['offsety'] != null) {
            cursorOffsetY = resinfo['offsety'];
        } else {
            cursorOffsetY = 0;
        }

        return asset;
    }

    /**
    * This function restores the default cursor if there is no locked cursor on
    * the screen.
    *
    * @access private
    */
    static function restoreCursor () :void {
        if ( __amLocked ) { return; }
        cursorSprite.stopDrag();
        cursorSprite.visible = false;
        LFCApplication.stage.removeEventListener(Event.MOUSE_LEAVE, mouseLeaveHandler);
        LFCApplication.stage.removeEventListener(MouseEvent.MOUSE_MOVE, mouseMoveHandler);
        globalCursorResource = null;
        if ($swf10) { Mouse['cursor'] = MouseCursor.AUTO; }
        Mouse.show();
    }

    /** Called by LzSprite to restore cursor to global value.
     */
    static function restoreCursorLocal () :void {
        if ( __amLocked ) { return; }
        if (globalCursorResource == null) {
            // Restore to system default pointer
            restoreCursor();
        } else {
            // Restore to the last value set by setCursorGlobal
            setCursorLocal(globalCursorResource);
        }
    }

    /**
    * Prevents the cursor from being changed until unlock is called.
    *
    */
    static function lock () :void {
        __amLocked = true;
    }

    /**
    * Restores the default cursor.
    *
    */
    static function unlock () :void {
        __amLocked = false;
        restoreCursor();
    }

    static function initCursor () :void {
        cursorSprite = new Sprite();
        cursorSprite.mouseChildren = false;
        cursorSprite.mouseEnabled = false;
        cursorSprite.visible = false;
        // Add the cursor DisplayObject to the root sprite
        LFCApplication.addChild(cursorSprite);
        cursorSprite.x = -10000;
        cursorSprite.y = -10000;
    }
}
