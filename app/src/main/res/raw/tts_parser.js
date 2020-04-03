function cmdStart() {
    hostWebViewTextController.onStart();
}
function cmdEnd() {
    hostWebViewTextController.onEnd();
}
function cmdText(text, top, bottom, extras) {
    hostWebViewTextController.onText(text, top, bottom, extras);
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

function prepareTextAndExtras(s, extras, limit) {
    var values = trim(s);
    s = values[0];
    var trimmedFromStart = values[1];

    return [s, s.length !== 0 ? getRelevantExtras(extras, trimmedFromStart, limit) : null];
}

function trim(s) {
    var len = s.length;
    s = s.replace(/^[\s\uFEFF\xA0]+/g, '');
    len -= s.length; // num of characters trimmed from start
    s = s.trim(); // trim the rest
    return [s, len];
}

function getRelevantExtras(extras, offset, limit) {
    var result = null;

    for (var e of extras) {
        if (e.start < limit) {
            var copy = Object.assign({}, e);
            copy.start -= offset;
            copy.end -= offset;

            if (result === null) result = [];
            result.push(copy);
        }
    }

    return result;
}

function serializeExtras(extras) {
    if (extras === null) return null;

    return JSON.stringify(extras);
}

function shiftExtras(extras, amount) {
    if (amount === 0) return;

    for (var i = extras.length - 1; i >= 0; i--) {
        var e = extras[i];

        e.start -= amount;
        if (e.start < 0) e.start = 0;

        e.end -= amount;
        if (e.end <= 0) extras.splice(i, 1);
    }
}

function parseDocumentText() {
    var range = document.createRange();

//    var stack = [];

    var accumulatedText = '';
    var extras = [];
    var currentElement = null;

    var emphasisStarts = [];

    var checkForSentenceEnd = function() {
        if (!accumulatedText || accumulatedText.trim().length === 0) return;

        var currentElementText = currentElement.textContent;
        var currentElementLength = currentElementText.length;

        var regex = new RegExp(/[.?!]+\s/, 'g');

        var match;
        while ((match = regex.exec(currentElementText)) !== null) {
            var index = match.index + match[0].length;

            var end = accumulatedText.length - (currentElementLength - index);
            var s = accumulatedText.substring(0, end);

            var values = prepareTextAndExtras(s, extras, end);
            s = values[0];
            var relevantExtras = values[1];

            if (s.length !== 0) {
                range.setEnd(currentElement, index);
//                console.log('checkForSentenceEnd()');
//                console.log('Range: ' + range);
                var rect = range.getBoundingClientRect();
                cmdText(s, rect.top, rect.bottom, serializeExtras(relevantExtras));
            }

            accumulatedText = accumulatedText.substring(end);
            shiftExtras(extras, end);

            range.setStart(currentElement, index);
        }

        range.setEnd(currentElement, currentElementLength);
    };

    var flushCurrentText = function() {
        if (accumulatedText && accumulatedText.trim().length > 0) {
//            console.log('flushCurrentText()');
//            console.log('Range: ' + range);
            var rect = range.getBoundingClientRect();

            var values = prepareTextAndExtras(accumulatedText, extras, accumulatedText.length);
            var s = values[0];
            var relevantExtras = values[1];

            cmdText(s, rect.top, rect.bottom, serializeExtras(relevantExtras));
        }

        accumulatedText = '';
        extras = [];
        currentElement = null;
        emphasisStarts = [];
    };

    var handleFormatting = function(element, start) {
        if (['B', 'I', 'STRONG', 'EM'].indexOf(element.nodeName) !== -1) {
            if (start) {
                emphasisStarts.push(accumulatedText.length);
            } else {
                var lastStart = emphasisStarts.pop();
                if (lastStart !== undefined) {
                    extras.push({type: 'emphasis', start: lastStart, end: accumulatedText.length});
                }
            }
        }
    };

    var parserCallback = {
        enterNode(element) {
//            console.log('enterNode ' + element);
//            stack.push(element);
//            console.log('enterNode, stack: ' + stackToString(stack));

            if (shouldBreak(element)) {
                flushCurrentText();
            } else {
                handleFormatting(element, true);
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
            } else {
                handleFormatting(element, false);
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
