function cmdStart() {
    hostWebViewTextController.onStart();
}
function cmdEnd() {
    hostWebViewTextController.onEnd();
}
function cmdText(text, top, bottom) {
    hostWebViewTextController.onText(text, top, bottom);
}

function traverse(element, callback) {
    var rootElement = element;

    while (element !== null) {
        if (!shouldSkip(element) && element.hasChildNodes()) {
            callback.enterNode(element);
            element = element.firstChild;
        } else {
            var next = element.nextSibling;
            while (next === null && element !== null) {
                element = element.parentNode;
                if (element === rootElement) {
                    element = null;
                }
                if (element !== null) {
                    callback.leaveNode(element);
                    next = element.nextSibling;
                }
            }
            element = next;
        }

        if (element !== null && !shouldSkip(element) && !element.hasChildNodes()) {
            callback.processLeaf(element);
        }
    }
}

function shouldSkip(element) {
    return element.tagName === 'SCRIPT' || element.tagName === 'NOSCRIPT';
}

function parseDocumentText() {
    var elem = document.getElementsByTagName('body')[0];

    var parserCallback = {
        enterNode(element) {
//            console.log('enterNode ' + element);
        },

        processLeaf(element) {
//            console.log('processLeaf ' + element);
            if (element.nodeType == Node.TEXT_NODE && element.textContent.trim().length > 0) {
//                console.log('TEXT: ' + element.textContent.trim());
                range.selectNode(element);
                var rect = range.getBoundingClientRect();
                var text = element.textContent.trim();
                cmdText(text, rect.top, rect.bottom);
            }
        },

        leaveNode(element) {
//            console.log('leaveNode ' + element);
        }
    };

    var range = document.createRange();
    cmdStart();
    traverse(elem, parserCallback);
    cmdEnd();
}
