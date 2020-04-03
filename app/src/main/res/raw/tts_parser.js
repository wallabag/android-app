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

    var accumulatedText = '';
    var currentElement = null;

    var checkForSentenceEnd = function() {
        if (!accumulatedText || accumulatedText.trim().length === 0) return;

        var currentElementText = currentElement.textContent;
        var currentElementLength = currentElementText.length;

        var regex = new RegExp(/[.?!]+\s/, 'g');

        var match;
        while ((match = regex.exec(currentElementText)) !== null) {
            var index = match.index + match[0].length;

            var end = accumulatedText.length - (currentElementLength - index);
            var s = accumulatedText.substring(0, end).trim();
            if (s.length !== 0) {
                range.setEnd(currentElement, index);
//                console.log('checkForSentenceEnd()');
//                console.log('Range: ' + range);
                var rect = range.getBoundingClientRect();
                cmdText(s, rect.top, rect.bottom);
            }

            accumulatedText = accumulatedText.substring(end);

            range.setStart(currentElement, index);
        }

        range.setEnd(currentElement, currentElementLength);
    };

    var flushCurrentText = function() {
        if (accumulatedText && accumulatedText.trim().length > 0) {
//            console.log('flushCurrentText()');
//            console.log('Range: ' + range);
            var rect = range.getBoundingClientRect();
            cmdText(accumulatedText.trim(), rect.top, rect.bottom);
        }

        accumulatedText = '';
        currentElement = null;
    };

    var parserCallback = {
        enterNode(element) {
//            console.log('enterNode ' + element);
//            stack.push(element);
//            console.log('enterNode, stack: ' + stackToString(stack));

            if (shouldBreak(element)) {
                flushCurrentText();
            }
        },

        processLeaf(element) {
//            console.log('processLeaf ' + element);

            if (element.nodeType == Node.TEXT_NODE) {
                if (!accumulatedText || accumulatedText.trim().length === 0) {
                    range.setStart(element, 0);
                }

                accumulatedText += element.textContent;

                currentElement = element;
                range.setEnd(element, element.textContent.length);

                checkForSentenceEnd();
            } else if (shouldBreak(element)) {
                flushCurrentText();
            }
        },

        leaveNode(element) {
//            console.log('leaveNode ' + element);
//            if (element !== stack.pop()) console.log('POP DID NOT MATCH');
//            console.log('leaveNode, stack: ' + stackToString(stack));

            if (shouldBreak(element)) {
                flushCurrentText();
            }
        }
    };

    cmdStart();

    traverse(document.getElementById('article'), parserCallback);

    cmdEnd();
}

function shouldBreak(element) {
    return ['BR', 'P', 'OL', 'UL', 'LI'].indexOf(element.nodeName) !== -1
            || window.getComputedStyle(element).display === 'block';
}

//function stackToString(stack) {
//    return stack.map(e => e.nodeName.toLowerCase()).join(', ');
//}
