/* X_LZ_COPYRIGHT_BEGIN ***************************************************
* Copyright 2010 Laszlo Systems, Inc.  All Rights Reserved.               *
* Use is subject to license terms.                                        *
* X_LZ_COPYRIGHT_END ******************************************************/
    
// List of arguments, used for callbacks after iframemanager.js loads
lz.embed.__iframemanager_callbacks = [];

// Stub method, stores arguments and loads iframemanager.js
lz.embed.iframemanager = {
    create: function(owner, name, scrollbars, appendto, defaultz, canvasref) {
        var embed = lz.embed;
        var frames = embed.__iframemanager_callbacks;
        // store callback args on stack
        frames.push([].slice.call(arguments, 0));

        var iframeid = '__lz' + (frames.length - 1);
        var url = embed.getServerRoot() + 'iframemanager.js';
        // If we haven't registered a callback for this url
        if (! embed.jscallbacks[url]) {
            // Create callback
            var callback = function() {
                //console.log('loaded iframemanager.js');
                var embed = lz.embed;
                // Create iframes
                for (var i = 0, l = frames.length; i < l; i++) {
                    var args = frames[i];
                    var id = embed.iframemanager.create.apply(embed.iframemanager, args);
                    //console.log('created iframe', id, args, embed.iframemanager.create);
                }
                // destroy queue now that iframemanager.js is loaded
                delete embed.__iframemanager_callbacks;
            }

            //console.log('loading', url, iframeid);
            // Load iframemanager.js
            embed.loadJSLib(url, callback);
        }

        return iframeid;
    }
}
