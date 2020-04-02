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
    var range = document.createRange();

//    var stack = [];

    var currentText = '';
    var currentStartElement = null;
    var currentEndElement = null;

    var processCurrentText = function() {
        if (currentText && currentText.trim().length > 0) {
            range.setStartBefore(currentStartElement);
            range.setEndAfter(currentEndElement);
            var text = currentText.trim();
            var rect = range.getBoundingClientRect();
            cmdText(text, rect.top, rect.bottom);
        }

        currentText = '';
        currentStartElement = null;
        currentEndElement = null;
    };

    var parserCallback = {
        enterNode(element) {
//            console.log('enterNode ' + element);
//            stack.push(element);
//            console.log('enterNode, stack: ' + stackToString(stack));

            if (isBlock(element)) {
                processCurrentText();
            }
        },

        processLeaf(element) {
//            console.log('processLeaf ' + element);

            if (element.nodeType == Node.TEXT_NODE) {
                if (currentStartElement === null) currentStartElement = element;
                currentText += element.textContent;
                currentEndElement = element;
            } else if (element.nodeName === 'BR' || isBlock(element)) {
                processCurrentText();
            }
        },

        leaveNode(element) {
//            console.log('leaveNode ' + element);
//            if (element !== stack.pop()) console.log('POP DID NOT MATCH');
//            console.log('leaveNode, stack: ' + stackToString(stack));

            if (isBlock(element)) {
                processCurrentText();
            }
        }
    };

    cmdStart();

    var elem = document.getElementsByTagName('body')[0];
    traverse(elem, parserCallback);

    cmdEnd();
}

function isBlock(element) {
    return element.nodeName === 'P' || window.getComputedStyle(element).display === 'block';
}

//function stackToString(stack) {
//    return stack.map(e => e.nodeName.toLowerCase()).join(', ');
//}
