/* X_LZ_COPYRIGHT_BEGIN ***************************************************
* Copyright 2001-2010 Laszlo Systems, Inc.  All Rights Reserved.          *
* Use is subject to license terms.                                        *
* X_LZ_COPYRIGHT_END ******************************************************/
// Only IE 8 needs json2
#include "json2.js"
#include "pmrpc.js"

try {
    if (lz) {}
} catch (e) {
    lz = {};
}    

// send an event to the html component controlling this frame
lz.sendEvent = function(name, value) {
    // Send arg as JSON
    if (value) {
        value = JSON.stringify(value)
    }
    //console.log('calling iframemanager.asyncCallback with args',window.name, name, value);
    try {
        // Try to call directly...
        window.parent.lz.embed.iframemanager.asyncCallback(window.name, name, value);
    } catch (e) {
        // then fall back to using pmrpc
        (function () {
        var callobj = {
            destination: window.parent,
            publicProcedureName: 'asyncCallback',
            params: [window.name, name, value]
        }
        if (window.console && console.error) {
            callobj.onError = function(statusObj) { 
                console.error('sendEvent error: ', statusObj, 'with call', callobj); 
            }
        }
        pmrpc.call(callobj); 
        })();
    }
}

