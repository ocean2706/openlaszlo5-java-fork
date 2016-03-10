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
 * Static sprite property:  Quirks that compensate for browser
 * peculiarities.  Quirks will be copied to the sprite prototype once
 * they are computed by __updateQuirks, for easy access from sprites
 *
 * @access private
 */
LzSprite.quirks = {
    // Creates a separate tree of divs for handling mouse events.
    fix_clickable: true
    ,fix_ie_background_height: false
    ,fix_ie_clickable: false
    ,ie_alpha_image_loader: false
    ,ie_leak_prevention: false
    ,prevent_selection: false
    ,ie_elementfrompoint: false
    ,invisible_parent_image_sizing_fix: false
    ,emulate_flash_font_metrics: true
    // change \n to <br/>
    ,inner_html_strips_newlines: false
    ,inner_html_no_entity_apos: false
    ,css_hide_canvas_during_init: true
    ,firefox_autocomplete_bug: false
    ,hand_pointer_for_clickable: true
    ,alt_key_sends_control: false
    ,safari_textarea_subtract_scrollbar_height: false
    ,no_cursor_colresize: false
    ,safari_visibility_instead_of_display: false
    ,preload_images_only_once: false
    ,absolute_position_accounts_for_offset: false
    ,canvas_div_cannot_be_clipped: false
    ,inputtext_parents_cannot_contain_clip: false
    ,set_height_for_multiline_inputtext: false
    ,ie_opacity: false
    ,text_measurement_use_insertadjacenthtml: false
    ,text_content_use_inner_text: false
    ,text_selection_use_range: false
    ,document_size_use_offsetheight: false
    ,text_ie_carriagereturn: false
    ,ie_paste_event: false
    ,safari_paste_event: false
    ,text_event_charcode: true
    ,keypress_function_keys: true
    ,ie_timer_closure: false
    ,keyboardlistentotop: false
    ,document_size_compute_correct_height: false
    ,ie_mouse_events: false
//    ,fix_inputtext_with_parent_resource: false // no longer needed
    ,activate_on_mouseover: true
    ,ie6_improve_memory_performance: false
    ,text_height_includes_padding: false
    ,inputtext_size_includes_margin: false
    ,listen_for_mouseover_out: true
    ,focus_on_mouseover: true
    ,textstyle_on_textdiv: false
    ,textdeco_on_textdiv: false
    ,use_css_sprites: true
    ,preload_images: true
    ,scrollbar_width: 15
    ,inputtext_strips_newlines: false
    //,dom_breaks_focus: false // no longer needed
    ,inputtext_anonymous_div: false
    ,clipped_scrollbar_causes_display_turd: false
    ,hasmetakey: true
    ,textgrabsinputtextfocus: false
    ,input_highlight_bug: false
    ,autoscroll_textarea: false
    ,fix_contextmenu: true
    ,has_dom2_mouseevents: false
    ,container_divs_require_overflow: false
    ,fix_ie_css_syntax: false
    ,match_swf_letter_spacing: false
    ,use_css_master_sprite: false
    ,write_css_with_createstylesheet: false
    ,inputtext_use_background_image: false
    ,show_img_before_changing_size: false
    ,use_filter_for_dropshadow: false
    ,keyboardlistentotop_in_frame: false
    ,forcemeasurescrollheight: false
    ,resize2dcanvas: false
    ,textmeasurementalphastring: false
    // See LPP-9101
    ,textlinksneedmouseevents:false
    // See LPP-9202
    ,explicitly_set_border_radius:false
    // See LzTextSprite.setSelectable()
    ,prevent_selection_with_onselectstart:false
    // Computed text width is off on IE9 (LPP-10148 and others)
    ,fix_ie9_textwidth: false
    // Force div to resize to changes in height (LPP-10191)
    ,fix_ie_divheight_change: false
    // Prevent div scrolling by click/drag of mouse (LPP-10237)
    ,fix_div_mouse_scrolling: false
}

LzSprite.prototype.capabilities = {
    rotation: false
    // Scale canvas to percentage values
    ,scalecanvastopercentage: false
    ,readcanvassizefromsprite: true
    ,opacity: true
    ,colortransform: false
    ,audio: false
    ,accessibility: true
    ,htmlinputtext: false
    ,advancedfonts: false
    ,bitmapcaching: false
    ,persistence: false
    ,clickregion: false
    ,minimize_opacity_changes: false
    ,history: true
    ,runtimemenus: false
    ,setclipboard: false
    ,proxypolicy: false
    ,linescrolling: false
    ,allowfullscreen: false
    ,setid: true
    ,globalfocustrap: false
    ,'2dcanvas': true
    ,dropshadows: false
    ,cornerradius: false
    ,rgba: false
    ,css2boxmodel: true
    ,medialoading: true
    ,backgroundrepeat: true
    ,touchevents: false
    ,directional_layout: false
    ,scaling: false
    ,customcontextmenu: true
    ,screenorientation: false
    ,directional_layout: true
}

/**
 * Static function executed once at load time to initialize quirks for
 * the browser we are loaded into
 *
 * @access private
 */
LzSprite.__updateQuirks = function () {
    var quirks = LzSprite.quirks;
    var capabilities = LzSprite.prototype.capabilities;
    var stylenames = LzSprite.__styleNames;
    var defaultStyles = LzSprite.__defaultStyles;

    if (window['lz'] && lz.embed && lz.embed.browser) {
        var browser = lz.embed.browser;

        // Divs intercept clicks if physically placed on top of an element
        // that's not a parent. See LPP-2680.
        // off for now
        //quirks['fix_clickable'] = true;
        if (browser.isIE) {
            if (browser.version < 7) {
                // Provide IE PNG/opacity support
                quirks['ie_alpha_image_loader'] = true;
                // IE 6 reports incorrect clientHeight for embedded iframes with scrollbars
                quirks['document_size_compute_correct_height'] = true;
                // prevent duplicate image loads - see http://support.microsoft.com/?scid=kb;en-us;823727&spid=2073&sid=global and http://misterpixel.blogspot.com/2006/09/forensic-analysis-of-ie6.html
                quirks['ie6_improve_memory_performance'] = true;
            } else {
                quirks['prevent_selection'] = true;
                quirks['invisible_parent_image_sizing_fix'] = true;
                if (browser.osversion >= 6 && browser.version < 9) {
                    // IE7 on Vista (osversion=6) needs the alpha image loader
                    // (Fixes LPP-3352 and others)
                    // Does Windows7 behave like Vista?
                    quirks['ie_alpha_image_loader'] = true;
                }
                if (browser.version == 8) {
                    // See LPP-8932
                    // excanvas needs a little help to show the context at first
                    quirks['resize2dcanvas'] = true;
                }
            }

            //Debug.warn("Browser version", browser.version);

            // Must force IE to update div to size changes (LPP-10191)
            quirks['fix_ie_divheight_change'] = true;

            if (browser.version >= 9) {
                quirks['fix_ie9_textwidth'] = true;

                capabilities['cornerradius'] = true;
                capabilities['dropshadows'] = true;
                // ie9 supports rotation
                capabilities['rotation'] = true;
                // https://developer.mozilla.org/en/CSS/-ms-transform
                stylenames.transform = 'MsTransform';
                stylenames.transformOrigin = 'MsTransformOrigin';
            }

            if (browser.version <= 8) {
                // IE8 and earlier doesn't support standard opacity setting
                quirks['ie_opacity'] = true;
            }

            // IE needs closure around setTimeout, setInterval in LzTimeKernel
            quirks['ie_timer_closure'] = true;

            // IE DOM leak prevention
            quirks['ie_leak_prevention'] = true;

            // Use images to force click tree to work in IE
            quirks['fix_ie_clickable'] = true;

            // workaround for IE refusing to respect divs with small heights when
            // no image is attached
            quirks['fix_ie_background_height'] = true;

            // workaround for IE not supporting &apos; in innerHTML
            quirks['inner_html_no_entity_apos'] = true;

            // workaround for IE not supporting clip in divs containing inputtext
            // Turning this off fixes LPP-8257
            //quirks['inputtext_parents_cannot_contain_clip'] = true;

            // flag for components (basefocusview for now) to minimize opacity changes
            capabilities['minimize_opacity_changes'] = true;

            // multiline inputtext height must be set directly - height: 100% does not work.  See LPP-4119
            quirks['set_height_for_multiline_inputtext'] = true;

            // text size measurement uses insertAdjacentHTML()
            quirks['text_measurement_use_insertadjacenthtml'] = true;
            quirks['text_content_use_inner_text'] = true;
            quirks['text_selection_use_range'] = true;
            
            // IE uses "\r\n" for newlines, which gives different text-lengths compared to SWF and
            // to other browsers
            quirks['text_ie_carriagereturn'] = true;
            // IE has got a special event for pasting
            quirks['ie_paste_event'] = true;
            // IE does not send onkeypress for function keys
            quirks['keypress_function_keys'] = false;
            // IE does not use charCode for onkeypress
            quirks['text_event_charcode'] = false;
            // IE requires special handling of mouse events see LPP-6027, LPP-6141
            quirks['ie_mouse_events'] = true; 
            // workaround for IE not supporting clickable resources in views containing inputtext - see LPP-5435
            //quirks['fix_inputtext_with_parent_resource'] = true;
            // IE already includes margins for inputtexts
            quirks['inputtext_size_includes_margin'] = true;
            // LPP-7229 - IE 'helpfully' scrolls focused/blurred divs into view
            quirks['focus_on_mouseover'] = false;
            // required for text-align / text-indent to work
            quirks['textstyle_on_textdiv'] = true;
            // CSS sprites conflict with ie_alpha_image_loader...
            quirks['use_css_sprites'] = ! quirks['ie_alpha_image_loader'];
            // IE needs help focusing when an lztext is in the same area - LPP-8219
            quirks['textgrabsinputtextfocus'] = true;
            // IE document.elementFromPoint() returns scrollbar div
            quirks['ie_elementfrompoint'] = true;
            quirks['fix_ie_css_syntax'] = true;
            // IE doesn't like using DOM operations with the dev console - see LPP-
            quirks['write_css_with_createstylesheet'] = true;
            // IE meta key processing interferes with control-kep processing - see LPP-8702
            quirks['hasmetakey'] = false;
            // IE inputtexts must have a background image to be selectable - see LPP-8696
            quirks['inputtext_use_background_image'] = true;
            // LPP-6009. Setting width/height doesn't always stick in IE7/dhtml 
            // I found that changing the display first fixes this.
            quirks['show_img_before_changing_size'] = true;
            if (browser.version < 9) {
                // LPP-8399 - use directx filters for dropshadows, IE9+ supports shadow
                quirks['use_filter_for_dropshadow'] = true;
            }
            // LPP-8591 when measuring text div scrollheight, need to first set it to zero
         //   quirks['forcemeasurescrollheight'] = true;
            // Add these for IE
            defaultStyles['lzswfinputtext'].resize = 'none';
            defaultStyles['lzswfinputtextmultiline'].resize = 'none';
            capabilities['dropshadows'] = true;
            // Force hasLayout for lzTextSizeCache in IE
            defaultStyles['#lzTextSizeCache'].zoom = 1;
            defaultStyles['#lzTextSizeCache'].position = 'relative';
            // See LzTextSprite.setSelectable()
            quirks['prevent_selection_with_onselectstart'] = true;
        } else if (browser.isSafari || browser.isChrome) {
            stylenames.borderRadius = 'WebkitBorderRadius';
            stylenames.borderTopLeftRadius = 'WebkitBorderTopLeftRadius';
            stylenames.borderTopRightRadius = 'WebkitBorderTopRightRadius';
            stylenames.borderBottomRightRadius = 'WebkitBorderBottomRightRadius';
            stylenames.borderBottomLeftRadius = 'WebkitBorderBottomLeftRadius';
            stylenames.boxShadow = 'WebkitBoxShadow';
            stylenames.userSelect = 'WebkitUserSelect';
            stylenames.transform = 'WebkitTransform';
            stylenames.transformOrigin = 'WebkitTransformOrigin';
            // Safari won't show canvas tags whose parent is display: none
            quirks['safari_visibility_instead_of_display'] = true;
            quirks['absolute_position_accounts_for_offset'] = true;
            if (browser.version < 525.18) {
                //Seems to work fine in Safari 3.1.1 
                quirks['canvas_div_cannot_be_clipped'] = true;
                // Fix bug in where if any parent of an image is hidden the size is 0
                quirks['invisible_parent_image_sizing_fix'] = true;
                // Safari scrollHeight needs to subtract scrollbar height
                quirks['safari_textarea_subtract_scrollbar_height'] = true;
            }
            quirks['document_size_use_offsetheight'] = true;

            // Safari 3.0.4 supports these
            if (browser.version > 523.10) {
                capabilities['rotation'] = true;
                capabilities['scaling'] = true;
                capabilities['dropshadows'] = true;
                capabilities['cornerradius'] = true;
                quirks['explicitly_set_border_radius'] = true;
                capabilities['rgba'] = true;
            }

            // Safari has got a special event for pasting
            quirks['safari_paste_event'] = true;
            // Safari does not send onkeypress for function keys
            quirks['keypress_function_keys'] = false;

            // Safari 3.x does not send global key events to apps embedded in an iframe
            // no longer true for 3.0.4 - see LPP-8355
            if (browser.version < 523.15) {
                quirks['keyboardlistentotop'] = true;
            }
            // Safari 4 needs help to get keyboard events when loaded in a frame - see LPP-8707.  Using a separate quirk to avoid perturbing the old fix
            if (browser.isSafari && browser.version < 533.16 && window.top !== window) {
                quirks['keyboardlistentotop_in_frame'] = true;
            }
            
            // If Webkit starting with 530.19.2 or Safari 530.19, 3d transforms supported
            if (browser.version >= 530.19) {
                capabilities["threedtransform"] = true;
            }

            if (browser.isIphone) {
                // turn off mouseover activation for touch browsers
                //quirks['activate_on_mouseover'] = false;
                //quirks['listen_for_mouseover_out'] = false;
                quirks['canvas_div_cannot_be_clipped'] = true;
                capabilities['touchevents'] = true;
                capabilities['screenorientation'] = true;
            }

            // required as of 3.2.1 to get test/lztest/lztest-textheight.lzx to show multiline inputtext properly
            quirks['inputtext_strips_newlines'] = true;
            quirks['prevent_selection'] = true;
            // See LPP-8402
            quirks['container_divs_require_overflow'] = true;
            // See LPP-10237
            quirks['fix_div_mouse_scrolling'] = true;
            // LPP-8591 when measuring text div scrollheight, need to first set it to zero
          // ----  quirks['forcemeasurescrollheight'] = true;

            // Webkit matches swf10 best with this
            defaultStyles.lzswfinputtext.paddingTop = '1px';
            defaultStyles.lzswfinputtext.paddingBottom = '3px';
            defaultStyles.lzswfinputtext.paddingLeft = '1px';
            defaultStyles.lzswfinputtext.paddingRight = '3px';
            defaultStyles.lzswfinputtextmultiline.paddingTop = '2px';
            defaultStyles.lzswfinputtextmultiline.paddingBottom = '2px';
            defaultStyles.lzswfinputtextmultiline.paddingLeft = '2px';
            defaultStyles.lzswfinputtextmultiline.paddingRight = '2px';
        } else if (browser.isOpera) {
            stylenames.transform = 'OTransform';
            stylenames.transformOrigin = 'OTransformOrigin';
            // doesn't help: stylenames.userSelect = 'OUserSelect';
            // Fix bug in where if any parent of an image is hidden the size is 0
            quirks['invisible_parent_image_sizing_fix'] = true;
            quirks['no_cursor_colresize'] = true;
            quirks['absolute_position_accounts_for_offset'] = true;
            quirks['canvas_div_cannot_be_clipped'] = true;
            quirks['document_size_use_offsetheight'] = true;
            // Opera does not use charCode for onkeypress
            quirks['text_event_charcode'] = false;
            quirks['textdeco_on_textdiv'] = true;
            // Opera uses "\r\n" for newlines, which gives different text-lengths
            // compared to SWF and to other browsers
            quirks['text_ie_carriagereturn'] = true;
            // Opera needs to use alphanumeric strings for text measurement
            quirks['textmeasurementalphastring'] = true;
            if (browser.version >= 10.60) {
                // CSS3 word-wrap is broken, see LPP-9177
                defaultStyles.lzswftext.wordWrap = 'normal';
                defaultStyles.lzswfinputtext.wordWrap = 'normal';
                defaultStyles.lzswfinputtextmultiline.wordWrap = 'normal';
                // See LPP-9469, clipping was not working in Opera 10.6
                quirks['container_divs_require_overflow'] = true;
            } 
            if (browser.version >= 10.6) {
                // Opera 10.6 supports rotation
                capabilities['rotation'] = true;
            }
        } else if (browser.isFirefox) {
            if ( browser.version < 13 ) {
                // Firefox 13 and higher only support unprefixed border-radius
                stylenames.borderRadius = 'MozBorderRadius';
                // Firefox 13 and higher only support unprefixed box-shadow
                stylenames.boxShadow = 'MozBoxShadow';
            }
            stylenames.userSelect = 'MozUserSelect';
            // https://developer.mozilla.org/en/CSS/-moz-transform
            stylenames.transform = 'MozTransform';
            stylenames.transformOrigin = 'MozTransformOrigin';

            // DOM operations on blurring element break focus (LPP-7786)
            // https://bugzilla.mozilla.org/show_bug.cgi?id=481468
            //quirks['dom_breaks_focus'] = true;
            // anonymous div bug on input-elements (LPP-7796)
            // see https://bugzilla.mozilla.org/show_bug.cgi?id=208427
            quirks['inputtext_anonymous_div'] = true;
            //  Display artifacts in DHTML/Windows/FF when dragging
            // scrollable text (LPP-8000)
            // see https://bugzilla.mozilla.org/show_bug.cgi?id421866
            // - unvisible fixed elements with overflow:auto or
            // overflow:scroll flicker when scrolling
            if (browser.OS == 'Windows') {
                quirks['clipped_scrollbar_causes_display_turd'] = true;
                // LPP-8121 inputtext selection highlight color depends on underlying div bgcolor
                quirks['input_highlight_bug'] = true;
            }
            if (browser.version < 2) {
                // see http://groups.google.ca/group/netscape.public.mozilla.dom/browse_thread/thread/821271ca11a1bdbf/46c87b49c026246f?lnk=st&q=+focus+nsIAutoCompletePopup+selectedIndex&rnum=1
                quirks['firefox_autocomplete_bug'] = true;
            } else if (browser.version < 3) {
                // Firefox 2.0.14 doesn't work with the correct line height of 120%
                defaultStyles.lzswftext.lineHeight = '119%';
                defaultStyles.lzswfinputtext.lineHeight = '119%';
                defaultStyles.lzswfinputtextmultiline.lineHeight = '119%';
            } else if (browser.version < 4) {
                // Firefox 3.0 does not need padding added onto field height measurements
                if (browser.subversion < 6) {
                    // no longer needed as of 3.0.6 (or maybe earlier...)
                    quirks['text_height_includes_padding'] = true;
                }
                if (browser.version < 3.5) {
                    // See LPP-8402
                    quirks['container_divs_require_overflow'] = true;
                }
            }
            quirks['autoscroll_textarea'] = true;
            if (browser.version >= 3.5) {
                capabilities['rotation'] = true;
                capabilities['scaling'] = true;
            }
            
            if (browser.version >= 3.1) {
                capabilities['dropshadows'] = true;
                capabilities['cornerradius'] = true;
                capabilities['rgba'] = true;
            }
        }

        if (browser.OS == 'Mac') {
            // see LPP-8210
            quirks['detectstuckkeys'] = true;
            // Remap alt/option key also sends control since control-click shows context menu (see LPP-2584 - Lzpix: problem with multi-selecting images in Safari 2.0.4, dhtml)
            quirks['alt_key_sends_control'] = true;
            quirks['match_swf_letter_spacing'] = true;
        }

        if (browser.OS == 'Android') {
            capabilities['touchevents'] = true;
            capabilities['screenorientation'] = true;
        }

        // Adjust styles for quirks
        if (quirks['hand_pointer_for_clickable']) {
            defaultStyles.lzclickdiv.cursor = 'pointer';
        }

        if (quirks['inner_html_strips_newlines'] == true) {
            LzSprite.prototype.inner_html_strips_newlines_re = RegExp('$', 'mg');
        }
        
        // Turn off image selection - see LPP-8311
        defaultStyles.lzimg[stylenames.userSelect] = 'none';

        if (capabilities.rotation) {
            // Rotation's origin in CSS is width/2 and height/2 as default
            defaultStyles.lzdiv[stylenames.transformOrigin] = '0 0';
        }

        // See LPP-8696
        if (quirks['inputtext_use_background_image']) {
            defaultStyles.lzinputtext['background'] = defaultStyles.lzswfinputtext['background'] = defaultStyles.lzswfinputtextmultiline['background'] = 'url(' + LzSprite.blankimage + ')';
        }

        LzSprite.prototype.br_to_newline_re = RegExp('<br/>', 'mg');

        if (lz.BrowserUtils.hasFeature('mouseevents', '2.0')) {
            quirks['has_dom2_mouseevents'] = true;
        }

        if (quirks['match_swf_letter_spacing']) {
            defaultStyles.lzswftext.letterSpacing = defaultStyles.lzswfinputtext.letterSpacing = defaultStyles.lzswfinputtextmultiline.letterSpacing = '0.01em';
        }
        
        // frank add remove clickdiv
        if (!browser.isIE) {
  //  console.log("----- set none IE -----");
            quirks['fix_clickable'] = false;
            quirks['activate_on_mouseover'] = false;
            document.oncontextmenu = LzMouseKernel.__mouseEvent;
        }
    }
    // Make quirks available as a sprite property
    LzSprite.prototype.quirks = quirks;
};

/* Update the quirks on load */
LzSprite.__updateQuirks();

}
