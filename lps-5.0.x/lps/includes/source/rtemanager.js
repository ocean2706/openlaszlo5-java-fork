/* X_LZ_COPYRIGHT_BEGIN ***************************************************
* Copyright 2001-2013 Laszlo Systems, Inc.  All Rights Reserved.          *
* Use is subject to license terms.                                        *
* X_LZ_COPYRIGHT_END ******************************************************/

// Notes:
//
// image buttons. Users expect them to be relative to where the lzx file is, not relative to
// where the rtewrapper.html file is.
//
// These objects are loaded by every iframe. I tried to load it once and reference it from the
// iframe, but dojo uses globals and I couldn't get it to work.
//
// Deferred loading dojo generates loading errors when you load a local copy of dojo. This doesn't
// happen when you load a cross-domain version from Google.
// (http://trac.dojotoolkit.org/ticket/11445)
// I found that setting debugAtAllCosts=true fixes the problem.
//
// Heap leaks!
//
// I'm loading a static html page to get the rte functionality. Should I create this page on the fly
// and load it each time?
//
// solo issues
//
// Add Loading... or icon to show that rte editor is loading
//
// A couple of IE issues. I disabled keyboard handling since this generated an error when the rte is embedded ina window that gets deleted. However, I get a permission denied error now.


// djConfig defines what dojo loads
var djConfig = {
    //isDebug: true
    //parseOnLoad: true
    afterOnLoad: true
    ,require: ['dojo.parser', 'dijit.Editor', 'dijit.layout.ContentPane', 'dijit.layout.BorderContainer', 'dijit.form.Form', 'dijit._editor.range', 'dijit._editor.plugins.AlwaysShowToolbar', 'dijit._editor.plugins.LinkDialog', 'dijit._editor.plugins.Print', 'dijit._editor.plugins.TextColor', 'dijit._editor.plugins.FontChoice','dojox.editor.plugins.Smiley','dojox.editor.plugins.ToolbarLineBreak','dojox.editor.plugins.SafePaste','dojox.editor.plugins.NormalizeIndentOutdent']
    ,debugAtAllCosts: false   // Setting this to true fixes local loading of dojo
    ,addOnLoad: function() {
        lz.rte.loader.editor_loaded();
    }
};

lz.rte = {}

lz.rte.util = {
    loadJavascript: function(doc, url, onload) {
        var s = doc.createElement('script');
        s.src  = url;
        s.type = 'text/javascript';
        if (onload) {
          s.onload = onload;
          s.onreadystatechange = onload;  //IE
        }
        doc.getElementsByTagName('head')[0].appendChild(s);
    }

    // Load css file into the top-level page
    ,loadCSS: function(doc, url) {
        var l = doc.createElement('link');
        l.rel  = 'stylesheet';
        l.type = 'text/css';
        l.href = url;
        doc.getElementsByTagName('head')[0].appendChild(l);
    }
}


lz.rte.loader = {
    __loading: false    // true if the loading process is running
    ,__loaded: false    // true if dijit Editor is loaded
    ,__callbacks: []    // object names requesting notification when editor has loaded

    // The default load path is from goolespis.com. This is a cross-domain version od dojo
    ,__dojoroot: 'http://ajax.googleapis.com/ajax/libs/dojo/1.8.0/'
    ,__csspath: 'dijit/themes/'
    ,__jspath: 'dojo/dojo.js'

    ,loadDojoCSS: function(doc, theme) {
        // Don't load the CSS if the theme is '(local)'. It is assumed you
        // have already loaded the css in the wrapper file.
        if (theme != '(local)')
            lz.rte.util.loadCSS (doc, lz.rte.loader.__dojoroot + lz.rte.loader.__csspath + theme + '/' + theme + '.css');

        // Manually load the smiley css so the icon displays
        lz.rte.util.loadCSS (doc, lz.rte.loader.__dojoroot + '/dojox/editor/plugins/resources/css/Smiley.css');

        // Manually load the css used by SafePaste
        lz.rte.util.loadCSS (doc, lz.rte.loader.__dojoroot + '/dojox/editor/plugins/resources/css/SafePaste.css');

        // If '(local)', specify the them class in the body tag.
        if (theme != '(local)')
            document.body.className += document.body.className ? ' ' + theme : theme;
    }

    // Load dojo/dijit.Editor into the specified document. Call obj.editor_loaded when complete. "(local)" is a special value for dojo_js. No loading of dojo occurs by rtemanager. It is expected these files are loaded directly in the wrapper file.
    ,loadDojo: function(doc, callback, extrarequire) {

        // If modulePaths is defined, use it rather than the default.
        if (typeof(modulePaths) != 'undefined') {
            djConfig.modulePaths = modulePaths;
            djConfig.baseUrl = './'; 
        }

        if (lz.rte.loader.__loading) {
            // Already loading. Add to callback
            lz.rte.loader.__callbacks.push (callback);
            return;
        }
        else if (lz.rte.loader.__loaded) {
            callback.call ();
            return;
        }
        else {
            lz.rte.loader.__loading = true;

            // Add additional requires
            if (extrarequire) {
                for (var i in extrarequire)
                    djConfig.require.push (extrarequire[i]);
            }

            lz.rte.loader.__callbacks.push (callback);
            // If the path to the javascript files is '(local)', no loading
            // is performed. It is expected that the javascript has been
            // loaded directly by the wrapper file. The editor_loaded event
            // is generated immediately.
            if (lz.rte.loader.__jspath != '(local)')
                lz.rte.util.loadJavascript (doc, lz.rte.loader.__dojoroot + lz.rte.loader.__jspath);
            else
                lz.rte.loader.editor_loaded();
        }
    }


    // Called when dojo/dijit.Editor is loaded. This runs once per iframe.
    ,editor_loaded: function() {
        dojo.parser.parse (); // Manually parse the DOM

        lz.rte.loader.__loaded = true;
        lz.rte.loader.__loading = false;

        // Inform anyone waiting that dojo/dijit.Editor is loaded
        while (lz.rte.loader.__callbacks.length > 0) {
            var callback = lz.rte.loader.__callbacks.shift ();
            callback.call ();
        }
    }
}


lz.rte.manager = {
    __id:         null     // DOM id of <div> element containing text
    ,__frameid:   null     // id of iframe in the parent document
    ,__loading:   false    // true if the loading process is running
    ,__loaded:    false    // true if dijit Editor is loaded
    ,__text:      ''       // Text to display when the editor first loads. Also the current text
    ,__onclick:   null     // lz delegate to fire when a button is clicked
    ,__editing:   false    // true if the editor is running
    ,__editor:    null     // dijit.Editor object
    ,button_counter: 0     // unique id of button
    ,__theme:     'tundra' // editor theme (tundra,soria,nihilo,claro)
    ,__locale:    null     // Override of locale

    ,__editor_class: null  // Name of class to instantiate for editor. See editor_loaded

    ,__safepasting: false    // true if safepaste plugin is enabled
    ,__safepaste:   {}       // Default configuration for safepaste

    // Constants to indicate how focus was obtained when startfocus() is called
    // These constants must match values in iframemanager.js and lz.Focus.
    ,FOCUS_KEYBOARD_PREV: -2
    ,FOCUS_KEYBOARD: -1
    ,FOCUS_MANUAL: 0
    ,FOCUS_MOUSE: 1

    // Order that plugins are displayed in the editor
    ,__plugins:   ['undo','redo','|','cut','copy','paste','|','bold','italic','underline','strikethrough','|','insertOrderedList','insertUnorderedList','indent','outdent','|','justifyLeft','justifyRight','justifyCenter','justifyFull','|', 'foreColor', 'hiliteColor', '|', 'createLink', 'unlink', 'insertImage', '|', 'print', 'smiley', '||' , 'fontName', 'fontSize']

    // Should the rte be focused when loaded
    ,__focusonload: false

    // List of extra plugins that are loaded
    ,__extraplugins: []

    // Create the rte, but do not display it
    ,create: function(id_name, frame_id) {
        lz.rte.manager.__id = id_name;
        lz.rte.manager.__frameid = frame_id;
    }

    // Cleanup
    ,__destroy: function () {
        lz.rte.manager.rte_stop ();
        
        if (lz.rte.manager.__editor) {
            lz.rte.manager.__editor.destroyRecursive ();
            lz.rte.manager.__editor = null;
            lz.rte.manager.__editing = false;

            lz.rte.manager.removeAllButtons ();
        }
    }

    // Run the RTE for the specified id
    ,__rte_start: function(initial_text) {
        lz.rte.manager.initialize(); // nop if already initialized
        lz.rte.manager.__editing = true;

        var id = dojo.byId(lz.rte.manager.__id);

        if (initial_text && id)
          id.innerHTML = initial_text;

        if (lz.rte.manager.__editor)
          lz.rte.manager.__editor.open ();
        else {
            var plugins = lz.rte.manager.__plugins;
            // TODO. This has issues in IE when rte is in a window that gets removed
            //            plugins.push ('dijit._editor.plugins.EnterKeyHandling');
            var extraplugins = lz.rte.manager.__extraplugins;
            extraplugins.push ('dijit._editor.plugins.AlwaysShowToolbar');
            extraplugins.push ('normalizeindentoutdent');

            if (lz.rte.manager.__safepasting) {
                var sp = lz.rte.manager.__safepaste;
                try {
                    var plugin = eval('(' + sp + ')');
                    plugin['name'] = 'safepaste';
                    //console.log('adding safepaste:', plugin);
                    extraplugins.push (plugin);
                }
                catch (e) {
                    //console.log("safepaste exception caught", e, sp);
                }
            }

            var editor_class = lz.rte.manager.__editor_class;

            try {
                lz.rte.manager.__editor = new editor_class({height: '100%', focusOnLoad: lz.rte.manager.__focusonload, plugins: plugins, extraPlugins: extraplugins}, id);
                // __rte_complete handles the rest of the editor initialization
                // once the editor is fully loaded.
                lz.rte.manager.__editor.onLoadDeferred.addCallback (lz.rte.manager.__rte_complete);
                //console.log('editor class is ', editor_class);
            }
            catch (e) {
                //console.log('Error caught: ', e);
            }
        }
    }

    // The rte_complete is called from __rte_start when the editor is 
    // fully loaded. This is not the same as dojo.addOnLoad() since some of
    // the editor initialization happens in its iframe.
    ,__rte_complete: function() {

        dijit.byId('rte_div').resize();

        // focus events
        dojo.connect(lz.rte.manager.__editor, 'onFocus', lz.rte.manager.onfocus);
        dojo.connect(lz.rte.manager.__editor, 'onfocusin', lz.rte.manager.onfocus);

        // blur events
        dojo.connect(lz.rte.manager.__editor, 'onBlur', lz.rte.manager.onblur);
        //        dojo.connect(lz.rte.manager.__editor, 'onfocusout', lz.rte.manager.onblur);

        // In addition fo focus events, listen for mouse-related events to clear focus
        // from other components. onClick catches mouse in the body. onMouseDown catches
        // mouse in the buttons.
        dojo.connect(lz.rte.manager.__editor, 'onMouseDown', lz.rte.manager.onmousedown);
        dojo.connect(lz.rte.manager.__editor, 'onClick', lz.rte.manager.onmousedown);

        // Capturing mouse clicks and key presses is enough to find out when
        // the text changes. The onChange event in dijit.Editor doesn't fire
        // until the editor loses focus. It is still useful to capture this
        // event.
        dojo.connect(lz.rte.manager.__editor, 'onChange', lz.rte.manager.onchange);
        dojo.connect(lz.rte.manager.__editor, 'onClick', lz.rte.manager.onchange);
        dojo.connect(lz.rte.manager.__editor, 'onKeyUp', lz.rte.manager.onchange);

        // Firefox/Mac has focus issues when you click to gain editor focus.
        // (LPP-10036)
        if (dojo.isFF && dojo.isMac) {
            dojo.connect(lz.rte.manager.__editor, 'onClick', lz.rte.manager.onffmacclick);
        }

        // Generate the ready events
        lz.rte.manager.editor_is_ready();

    }

    ,rte_start: function(initial_text) {
        if (lz.rte.manager.isEditing() || lz.rte.manager.__loading)
            return;
        if (lz.rte.manager.isLoaded()) {
            lz.rte.manager.__rte_start(initial_text);
        }
        else {
            // Load the editor and then show editor
            lz.rte.manager.__text = initial_text;
            lz.rte.manager.initialize();
        }
    }

    // Stop RTE and return the content
    ,rte_stop: function() {
        var contents;
        if (lz.rte.manager.__editor) {
            contents = lz.rte.manager.getText();
            if (lz.rte.manager.isEditing())
                lz.rte.manager.__editor.close ();
            //console.debug("rte_stop", contents);
        }

        return contents;
    }

    // Set the editor contents. Use replaceValue() so undo history is kept
    // (on Mozilla at least). There's a 'feature' in dojo that an empty
    // string will not erase all the text. Set to a single space instead.
    // If the string has never been set it must be set directly.
    // Note. If the editor is invisible, there is no selection and dojo does
    // the wrong thing (it will append rather than erase)
    ,setText: function(s) {
        if (lz.rte.manager.__editor) {
            var empty = (lz.rte.manager.__editor.get('value').length == 0);
            // <p>..</p> was added to the string for LPP-9371. This change is
            // not very useful. It also causes an issue with LPP-10039.
            //console.log('setText:','"'+s+'"');
            if (empty)
                lz.rte.manager.__editor.set('value', s);
            else{
                if (lz.rte.manager.__editor.window && lz.rte.manager.__editor.window.getSelection && lz.rte.manager.__editor.window.getSelection() == null)
                    lz.rte.manager.__editor.set('value', s);
                else
                    lz.rte.manager.__editor.replaceValue(s);
            }
        }
    }

    // Insert html
    ,insertHtml: function(html) {
        if (lz.rte.manager.__editor) {
          lz.rte.manager.__editor.execCommand("inserthtml", html);
        }
    }

    // Execute any editor command. Most commands are called only by editor
    // plugins, but this allows lzx code to emulate a plugin.
    // Nothing happens if the command is not supported.
    // Examples include 'bold', 'undo', 'inserttable'
    ,execCommand: function(cmd, arg) {
        if (lz.rte.manager.__editor) {
            if (lz.rte.manager.__editor.queryCommandAvailable(cmd))
                lz.rte.manager.__editor.execCommand(cmd, arg);
        }
    }
 
    // Retrieve the editor contents
    ,getText: function() {
        var contents;
        if (lz.rte.manager.__editor) {
          contents = lz.rte.manager.__editor.get('value');
          // Strip out leading/trailing <p> and </p> but leave <p... intact
          if ((contents.indexOf('<p>') == 0 || contents.indexOf('<P>') == 0) &&
              (contents.lastIndexOf('</p>') == contents.length-4 || contents.lastIndexOf('</P>') == contents.length-4)) {
              contents = contents.substr (3, contents.length-7);
          }
        }
        return contents;
    }

    // Callback method when the editor content changes. Nothing is generated if the text doesn't change
    // Sends ontext event to lzx
    ,onchange: function(e) {
        var txt = lz.rte.manager.getText();
        if (txt != lz.rte.manager.__text) {
            //console.debug("onchange:", txt);
            lz.rte.manager.__text = txt;
            lz.sendEvent('_text', txt);
        }
    }

    // blur/focus the editor on mouse click for FF/Mac (LPP-10036)
    ,onffmacclick: function(e) {
        var editor = lz.rte.manager.__editor;
        if (editor) {
            editor.blur ();
            editor.focus();
        }
    }

    // Remove focus from the rte for IE and reset the focus on the
    // current active element.
    ,blurIE: function() {
        if (document.all) {
            // Focus will be reset on the active element.
            //console.log('*****blurIE*****');
            var active = window.parent.document.activeElement;

            // For IE7 we need to tell the editor that we are blurred
            if (lz.rte.manager.__editor)
                lz.rte.manager.__editor.blur();

            if (active && active.focus)
                active.focus();
        }
    }

    // Callback method when the editor loses focus. Needed only for IE7
    ,onblur: function(e) {
        // No blur needed for IE. IE7 might be a little quirky still.
        return;

        if (document.all) {
            // IE doesn't like changing focus within an event
            setTimeout('lz.rte.manager.blurIE()', 0);
        }
    }

    // Callback method when the editor gets focus.
    ,onfocus: function(e) {
        // Make sure the outer iframe has focus to help tabbing (LPP-10283)
        var frameid = lz.rte.manager.__frameid;
        if (frameid) {
            var iframe = window.parent.document.getElementById(frameid);
            if (iframe)
                try {
                    iframe.focus();
                } catch (e) {
                    //do nothing to avoid script error for LPP-10314
                }
            // IE needs focus on the editor again (LPP-10296)
            if (lz.rte.browser.browser == 'Explorer' && lz.rte.manager.__editor) {
                lz.rte.manager.__editor.focus();
            }
        }

        //DEBUG
        //TODO Verify no blur is needed and remove this method.
        return;

        // Make sure the rte component has focus (so other cursors get removed)
        lz.sendEvent('_focus', null);
        //console.log('onfocus ran');
    }

    // Callback method when when mouse is clicked. This makes sure focus moves to the
    // rte component. (Needed for IE)
    ,onmousedown: function(e) {
        //console.log("onmousedown");
        lz.sendEvent('_focus', null);
    }

    // Called when the rte javascript is completely loaded. You can't use the
    // iframe onload event because that can occur before the javascript is
    // loaded
    ,rte_loaded: function() {
        //console.log("rte_loaded", window.name, lz.rte.manager);
        lz.sendEvent ('_rte_loaded');
    }

    // This method is called automatically when the editor is initialized.
    // However, if you define rte_before_ready you are responsible for
    // calling editor_initialized() when custom initialization is complete.
    ,editor_initialized: function() {
        // addOnLoad makes sure all the javascript is loaded.
        dojo.addOnLoad(lz.rte.manager.editor_post_loaded);
    }

    // Callback method when dijit is loaded
    ,editor_loaded: function() {
        // Prevent this from firing more than once
        if (lz.rte.manager.isLoaded())
            return;

        lz.rte.manager.__editor_class = dijit.Editor; // Default editor object created
        lz.rte.manager.__loaded = true;
        lz.rte.manager.__loading = false;

        // Before instantiating the rte_editor, call rte_before_ready so additional
        // components can be added.
        if (lz.rtewrapper && lz.rtewrapper.rte_before_ready) {
            lz.rtewrapper.rte_before_ready();
        }
        else {
            // Do final initialization and show the editor.
            lz.rte.manager.editor_initialized ();
        }
    }

    // This is called by editor_initialized(). This will cause the editor
    // to display. If you define lz.rtewrapper.rte_before_ready,
    // you must call editor_initialized() when finished.
    ,editor_post_loaded: function() {
        //__text is the initial text to show
        lz.rte.manager.__rte_start (lz.rte.manager.__text);
    }

    // Called when the editor is completely ready. Events are generated to
    // tell lzx and the wrapper that the editor is ready.
    ,editor_is_ready: function() {
        // Generate an on_editorready event. The rte component will send the oneditorready event
        lz.sendEvent ('_editorready');

        // Call lz.rtewrapper.rte_ready if it exists. This is useful to 
        // functionality to the wrapper when the rte is loaded.
        if (lz.rtewrapper && lz.rtewrapper.rte_ready) {
            setTimeout ('lz.rtewrapper.rte_ready ()', 5);        
        }

        // Call a method in the application-level wrapper page to say that
        // the rte is ready.
        var wrapper = window.parent;
        if (wrapper && wrapper.rte_ready) {
            setTimeout ('window.parent.rte_ready ()', 10);
        }
    }


    // Return true if editor is loaded
    ,isLoaded: function() {
        return lz.rte.manager.__loaded;
    }

      // Return true if the editor is enabled and running
    ,isEditing: function() {
        return lz.rte.manager.__editing;
    }

    // Install a delegate used whenever a button is clicked. The argument
    // to the delegate is the button_id.
    ,set_onclick: function(callback) {
        lz.rte.manager.__onclick = callback;
    }

    // Set or remove focus from the editor.
    ,editor_focus: function(focused) {
        //DEBUG
        //TODO Verify no blur is needed and remove this method.
        return;

        if (!lz.rte.manager.__editor) return;
     
        var editor = lz.rte.manager.__editor;
        console.log('rtemanager.editor_focus', focused, editor, editor._focused);
        var ed = dijit.byId('rte');
        if (focused) {
            // Make sure editor has the focus. Move focus to the container
            if (!editor._focused) {
                setTimeout('lz.rte.manager.__editor.focus()', 2);
            }

            if (editor.get('value').length <= 7) {
                // Empty element. Put focus at start. This is for FF so the cursor displays at the start.
                setTimeout ('var ed=lz.rte.manager.__editor; dijit.focus(ed.iframe); ed.placeCursorAtStart();', 20);      
            }
            else {
                setTimeout ('var ed=lz.rte.manager.__editor; dijit.focus(ed.iframe); ed.placeCursorAtEnd();', 20);      
            }
        }
        else {
            // Make sure the editor blurs
            if (editor._focused) {
                dijit.blur (editor);
            }
        }
    }

    // Call to initialize() package.
    ,initialize: function() {
        //console.debug("initialize");
        if (lz.rte.manager.isLoaded())
            return;
        // Install the css and theme
        lz.rte.loader.loadDojoCSS (document, lz.rte.manager.__theme);
        // Load dojo
        lz.rte.loader.loadDojo (document, lz.rte.manager.editor_loaded, lz.rte.manager.__extraplugins);
    }

    // Manage plugins

    // Set a location of dojo root. The relative offset to the js and css is also
    // specified. The css path probably will not change, but the js path will specify either
    // a compressed or uncompressed version
    ,setDojoPath: function(root, js, css) {
        if (root)
            lz.rte.loader.__dojoroot = root;
        if (js)
            lz.rte.loader.__jspath   = js;
        if (css)
            lz.rte.loader.__csspath  = css;

        // Set debugAtAllCosts to true becaue I found deferred loading of dojo of a local version
        // of dojo will generate errors on Firefox
        djConfig.debugAtAllCosts = true;
    }

    // You can change the dojo theme. Current values are tundra, soria, nihilo
    ,setDojoTheme: function(theme) {
        lz.rte.manager.__theme = theme;
    }

    // You can also specify a locale (that overrides the browser locale)
    ,setDojoLocale: function(locale) {
        lz.rte.manager.__locale = locale;
        djConfig.locale = lz.rte.manager.__locale;
    }

    // State of focus when the rte editor loads
    ,setFocusOnLoad: function(state) {
        lz.rte.manager.__focusonload = state;
    }

    // Set the list of extra plugins to load
    ,setExtraPlugins: function(list) {
        var extraplugins = list.split (',');
        lz.rte.manager.__extraplugins = extraplugins;
    }

    // Set the list and order of toolbar icons to show in the editor.
    ,setPlugins: function(list) {
        var plugins = list.split (',');
        lz.rte.manager.__plugins = plugins;
    }

    // Add a button (or image button) and put at the end of the buttons

    // Remove all buttons from above the rte
    ,removeAllButtons: function() {
        dojo.empty('rte_buttons');
        dijit.byId('rte_div').resize();
    }

    // Attributes are passed as a json object
    ,addButton: function(attributes) {
        var id = window.name + '_rte_button_' + lz.rte.manager.button_counter++;
        //console.log('addButton', id);

        if (dojo.byId(id))
          dojo.destroy(id);  // We already have a button of this name. Delete it

        // Default attributes
        var attr = {id: id, type: 'button', onclick: function(){ lz.sendEvent('buttonclick', id);}};
        for (var a in attributes) {
          attr[a] = attributes[a];
        }

        // Make sure the button bar is visible
        var rte_buttons = dojo.byId('rte_buttons');
        if (rte_buttons)
            rte_buttons.style.display = '';

        // Create the button and resize the layout
        dojo.create('input', attr, dojo.byId('rte_buttons'), 'last');
        dijit.byId('rte_div').resize();

        return id;
    }

    // Enable/disable safepaste plugin. This must be called before the rte
    // editor is displayed. Attributes is passed as a string which is later
    // converted into a hash.
    // Ex: "{width:'200px', height:'200px', stripTags:['img']}"
    ,addSafePaste: function(attributes) {
        lz.rte.manager.__safepasting = true;
        lz.rte.manager.__safepaste = attributes;
        //console.log('addSafePaste:', attributes);
    }

    // Disable safepaste plugin
    ,removeSafePaste: function() {
        lz.rte.manager.__safepasting = false;
    }
}



// If startfocus() is defined in a frame loaded by the html component, it is executed
// when the iframe is focused. If you do not like this behavior you can define 
// startfocus() in the rtewrapper file. A copy of the browser object is stored.
lz.rte.browser = {};

function startfocus (focusmethod, browser)
{
    lz.rte.browser = browser;

    // Do not touch the rte cursor if the user got here via mouse click.
    if (focusmethod == lz.rte.manager.FOCUS_MOUSE) {
        // Chrome needs to be focused
        if (lz.rte.browser.browser == 'Chrome' || lz.rte.browser.browser == 'Safari') {
            var editor = lz.rte.manager.__editor;
            dijit.focus(editor.iframe);
        } 

        return;
    }

    var editor = lz.rte.manager.__editor;
    if (editor) {
        if (editor.get('value').length <= 7) {
            // Empty element. Put focus at start.
            // This is for FF so the cursor displays at the start.
            dijit.focus(editor.iframe);
            editor.placeCursorAtStart();
        }
        else {
            dijit.focus(editor.iframe);
            editor.placeCursorAtEnd();
        }
    }
}
