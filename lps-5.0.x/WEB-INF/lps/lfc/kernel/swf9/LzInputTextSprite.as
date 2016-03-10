/**
  * LzInputTextSprite.as
  *
  * @copyright Copyright 2001-2012 Laszlo Systems, Inc.  All Rights Reserved.
  *            Use is subject to license terms.
  *
  * @topic Kernel
  * @subtopic swf9
  */

/**
  * @shortdesc Used for input text.
  * 
  */
public class LzInputTextSprite extends LzTextSprite {

    #passthrough (toplevel:true) {
        import flash.display.InteractiveObject;
        import flash.events.Event;
        import flash.events.FocusEvent;
        import flash.text.TextField;
        import flash.text.TextFormat;
        import flash.text.TextFieldType;
        import flash.events.FocusEvent;
        import flash.events.MouseEvent;
        import flashx.textLayout.events.CompositionCompleteEvent;

    }#

    function LzInputTextSprite (newowner:LzView = null, args:Object = null, useTLF:Boolean = false) {
        super(newowner, null, useTLF);

        this.password = args && args.password ? true : false;
        textfield.displayAsPassword = this.password;
    }

    var enabled :Boolean = true;
    var focusable :Boolean = true;
    var readReadonly :Boolean = false;

    override public function __initTextProperties (args:Object) :void {
        super.__initTextProperties(args);
        // We do not support html in input fields.  TODO [2010-07 hqm]
        // But an LzTLFTextField input type field has to be type HTML
        // right now because that's the only way a
        // TextContainerManager and EditManager will be created.
        //
        // I need to figure out how to make the escaping work
        // consistently between TextField and LzTLFTextField for this
        // input text case.
        if (textfield is LzTLFTextField)  {
            this.html = true;
        } else {
            this.html = false;
        }

        /*
          TODO [hqm 2008-01]
          these handlers need to be implemented via Flash native event listenters:
          
          focusIn , focusOut

          change -- dispatched after text input is modified

          textInput  -- dispatched before the content is modified
                        Do we need to use this for intercepting when we have
                        a pattern restriction on input?

        */
 
        textfield.addEventListener(Event.CHANGE        , __onChanged);
        //textfield.addEventListener(CompositionCompleteEvent.COMPOSITION_COMPLETE, __onChanged);
        //textfield.addEventListener(TextEvent.TEXT_INPUT, __onTextInput);


        textfield.addEventListener(FocusEvent.FOCUS_IN , __gotFocus);
        textfield.addEventListener(FocusEvent.FOCUS_OUT, __lostFocus);
    }


var __cachedSelectionPos:int = 0;
var __cachedSelectionSize:int = 0;

function __cacheSelection ():void {
    this.__cachedSelectionPos  = textfield.selectionBeginIndex;
    this.__cachedSelectionSize = textfield.selectionEndIndex - textfield.selectionBeginIndex;
}

override function getSelectionPosition() :int {
        if (LFCApplication.stage.focus == this.textfield) {
            return textfield.selectionBeginIndex;
        } else {
            return __cachedSelectionPos;
        }

}

override function getSelectionSize() :int {
        if (LFCApplication.stage.focus == this.textfield) {
            return textfield.selectionEndIndex - textfield.selectionBeginIndex;
        } else {
            return __cachedSelectionSize;
        }
}



    /**
     * Called from LzInputText#_gotFocusEvent() after focus was set by lz.Focus
     * @access private
     */
    public function gotFocus () :void {
        if (LFCApplication.stage.focus === this.textfield) { return; }
        // assign keyboard control
        this.select();

        // N.B. [hqm 2010-07] For bidi text (LzTLFTextField), I had to
        // put this call to set focus *after* the select() above,
        // otherwise the focus gets blurred when you set the
        // selection. This doesn't happen for native TextField. 
        // JIRA LPP-9257 tracks this.
        LFCApplication.stage.focus = this.textfield;
    }

    /**
     * Called from LzInputText#_gotBlurEvent() after focus was cleared by lz.Focus
     * @access private
     */
    function gotBlur () :void {
        //this.deselect();
        if (LFCApplication.stage.focus === this.textfield) {
            // remove keyboard control
            LFCApplication.stage.focus = LFCApplication.stage;
        }
    }

    function select () :void {
        // We don't want the selection to cause scrolling
        var h = textfield.scrollH;
        textfield.setSelection(0, textfield.text.length);
        // NOTE: [2010-03-15 ptw] This only takes effect if we defer
        // it to the next frame
        LzTimeKernel.setTimeout(function() {  textfield.scrollH = h; }, 0);
    }

    function deselect () :void {
        textfield.setSelection(0, 0);
    }

    /**
     * TODO [hqm 2008-01] I have no idea whether this comment
     * still applies:
     * Register for update on every frame when the text field gets the focus.
     * Set the behavior of the enter key depending on whether the field is
     * multiline or not.
     * 
     * @access private
     */
    function __gotFocus (event:FocusEvent) :void {
        if (owner) owner.inputtextevent('onfocus');
        if (LFCApplication.stage.focus !== this.textfield) {
            // stage-focus was changed within focus-in handler,
            // need to defer reassigning focus to next frame
            // https://bugs.adobe.com/jira/browse/FP-5021
            LzTimeKernel.setTimeout(this.updateStageFocus, 1);
        }
    }

    /**
     * This looks like a NOP, but it isn't, see __gotFocus()
     * @access private
     */
    private function updateStageFocus () :void {
        LFCApplication.stage.focus = LFCApplication.stage.focus;
    }

    /**
     * @access private
     */
    function __lostFocus (event:FocusEvent) :void {
        __cacheSelection();
        // Only send 'onblur' if there is _not_ an object gaining
        // focus.  If there is, let that even send the 'onblur' so it
        // can pass the related Object
        if (event.relatedObject == null) {
          if (owner) owner.inputtextevent('onblur');
        }
    }


    /**
     * Register to be called when the text field is modified. Convert this
     * into a LFC ontext event. 
     * @access private
     */
    function __onChanged (event:Event) :void {
        this.text = this.textfield.text;
        if (owner) owner.inputtextevent('onchange', this.text);
        /*        LzTimeKernel.setTimeout(function () {
                if (owner) owner.inputtextevent('onchange', this.text);
            }, 0);
        */
    }


    /**
     * Get the current text for this inputtext-sprite.
     * @protected
     */
    override public function getText() :String {
        // We normalize swf's \r to \n
      #passthrough {
        var pattern = /\r/g;
      }#
        return this.textfield.text.replace(pattern, '\n');
    }

    /**
     * Sets whether user can modify input text field
     * @param Boolean enabled: true if the text field can be edited
     */
    function setEnabled (enabled:Boolean) :void {
        this.enabled = enabled;
        
        if (this.readReadonly) {
            this.setFieldType(false); 
        } else {
            this.setFieldType(this.enabled);        
        } 
    }

    /**
     * If a mouse event occurs in an input text field, find the focused view
     */
    static function findSelection() :LzInputText {
        var f:InteractiveObject = LFCApplication.stage.focus;
        if ((f is TextField || f is LzTLFTextField) && f.parent is LzInputTextSprite) {
            return (f.parent cast LzInputTextSprite).owner;
        }
        return null;
    }

    override function setSelection(start:Number, end:Number) :void {
        LFCApplication.stage.focus = this.textfield;
        super.setSelection(start, end);

    }
    
    /**
     * add an empty function
     * @param Boolean enabled: true if the text field can be edited
     */
    function setReadOnly (readReadonly:Boolean) :void {        
        this.readReadonly = readReadonly;
        this.setFieldType(!this.readReadonly);        
    }
    
    function setFieldType(input: Boolean){
        if (input) {
            textfield.type = TextFieldType.INPUT;
        } else {
            textfield.type = TextFieldType.DYNAMIC;
        }

        // reset textformat to workaround flash player bug (FP-77)
        textfield.defaultTextFormat = this.textformat;   
    }

} // End of LzInputTextSprite
