function cmdStart() {
    hostWebViewTextController.onStart();
}
function cmdEnd() {
    hostWebViewTextController.onEnd();
}
function cmdText(text, extras, range, top, bottom) {
    hostWebViewTextController.onText(text, extras, JSON.stringify(range), top, bottom);
}
function cmdImg(altText, title, src, range, top, bottom) {
    hostWebViewTextController.onImage(altText, title, src, JSON.stringify(range), top, bottom);
}
function cmdRangeInfo(requestId, top, bottom) {
    hostWebViewTextController.onRangeInfoResponse(requestId, top, bottom);
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

function prepareTextAndExtras(s, extras, emphasisStarts, limit) {
    var values = trim(s);
    s = values[0];
    var trimmedFromStart = values[1];

    var relevantExtras = null;
    if (s.length !== 0) {
        relevantExtras = getRelevantExtras(extras, emphasisStarts, s, trimmedFromStart, limit);
    }

    return [s, relevantExtras];
}

function trim(s) {
    var len = s.length;
    s = s.replace(/^[\s\uFEFF\xA0]+/g, '');
    len -= s.length; // num of characters trimmed from start
    s = s.trim(); // trim the rest
    return [s, len];
}

function getRelevantExtras(extras, emphasisStarts, s, offset, limit) {
    var result = null;

    for (var i = 0; i < extras.length; i++) {
        var e = extras[i];
        if (e.start < limit) {
            var copy = JSON.parse(JSON.stringify(e));
            copy.start -= offset;
            copy.end -= offset;

            if (result === null) result = [];
            result.push(copy);
        }
    }

    if (emphasisStarts.length > 0) {
        if (result === null) result = [];
        result.push({type: 'emphasis', start: emphasisStarts[0] - offset, end: s.length});
    }

    return result;
}

function serializeExtras(extras) {
    if (extras === null) return null;

    return JSON.stringify(extras);
}

function shiftExtras(extras, emphasisStarts, amount) {
    if (amount === 0) return;

    for (var i = 0; i < emphasisStarts.length; i++) {
        emphasisStarts[i] -= amount;
        if (emphasisStarts[i] < 0) emphasisStarts[i] = 0;
    }

    for (var i = extras.length - 1; i >= 0; i--) {
        var e = extras[i];

        e.start -= amount;
        if (e.start < 0) e.start = 0;

        e.end -= amount;
        if (e.end <= 0) extras.splice(i, 1);
    }
}

function serializeRange(range, root) {
    // uses the `xpath-range` library
    return xpathRange.fromRange(range, root);
}

function parseDocumentText() {
    var root = document.getElementById('article');

    var range = document.createRange();

//    var stack = [];

    var accumulatedText = '';
    var extras = [];

    var emphasisStarts = [];

    var checkForSentenceEnd = function(currentElement) {
        if (!accumulatedText || accumulatedText.trim().length === 0) return;

        var currentElementText = currentElement.textContent;
        var currentElementLength = currentElementText.length;

        var regex = /[.?!\u2026]+\s/g;

        var match;
        while ((match = regex.exec(currentElementText)) !== null) {
            var index = match.index + match[0].length;

            var end = accumulatedText.length - (currentElementLength - index);
            var s = accumulatedText.substring(0, end);

            var values = prepareTextAndExtras(s, extras, emphasisStarts, end);
            s = values[0];
            var relevantExtras = values[1];

            if (s.length !== 0) {
                range.setEnd(currentElement, index); // TODO: should subtract trimmed from end
//                console.log('checkForSentenceEnd()');
//                console.log('Range: ' + range);
                var rect = range.getBoundingClientRect();
                cmdText(s, serializeExtras(relevantExtras), serializeRange(range, root), rect.top, rect.bottom);
            }

            accumulatedText = accumulatedText.substring(end);
            shiftExtras(extras, emphasisStarts, end);

            range.setStart(currentElement, index);
        }

        range.setEnd(currentElement, currentElementLength);

        if (accumulatedText.trim().length === 0) accumulatedText = '';
    };

    var flushCurrentText = function() {
        if (accumulatedText && accumulatedText.trim().length > 0) {
//            console.log('flushCurrentText()');
//            console.log('Range: ' + range);

            var values = prepareTextAndExtras(accumulatedText, extras, emphasisStarts,
                    accumulatedText.length);
            var s = values[0];
            var relevantExtras = values[1];

            var rect = range.getBoundingClientRect();
            cmdText(s, serializeExtras(relevantExtras), serializeRange(range, root), rect.top, rect.bottom);
        }

        accumulatedText = '';
        extras = [];
        emphasisStarts = [];
    };

    var handleFormatting = function(element, start) {
        if (['B', 'I', 'STRONG', 'EM'].indexOf(element.nodeName) !== -1) {
            if (start) {
                emphasisStarts.push(accumulatedText.length);
            } else {
                var lastStart = emphasisStarts.pop();
                if (lastStart !== undefined && accumulatedText.length > 0) {
                    extras.push({type: 'emphasis', start: lastStart, end: accumulatedText.length});
                }
            }
        }
    };

    var parserCallback = {
        enterNode: function(element) {
//            console.log('enterNode ' + element);
//            stack.push(element);
//            console.log('enterNode, stack: ' + stackToString(stack));

            if (shouldBreak(element)) {
                flushCurrentText();
            } else {
                handleFormatting(element, true);
            }
        },

        processLeaf: function(element) {
//            console.log('processLeaf ' + element);

            if (element.nodeType === Node.TEXT_NODE) {
                if (!accumulatedText || accumulatedText.trim().length === 0) {
                    accumulatedText = '';
                    range.setStart(element, 0);
                }

                accumulatedText += element.textContent
                        .replace(/[\r\n\x0B\x0C\u0085\u2028\u2029]/g, " ");

                range.setEnd(element, element.textContent.length);

                checkForSentenceEnd(element);
            } else if (element.nodeName === 'IMG') {
                flushCurrentText();

                range.selectNode(element);
                var rect = range.getBoundingClientRect();
                cmdImg(element.alt, element.title, element.src, serializeRange(range, root), rect.top, rect.bottom);
            } else if (shouldBreak(element)) {
                flushCurrentText();
            }
        },

        leaveNode: function(element) {
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

    traverse(root, parserCallback);

    cmdEnd();
}

function shouldBreak(element) {
    return ['BR', 'P', 'OL', 'UL', 'LI'].indexOf(element.nodeName) !== -1
            || window.getComputedStyle(element).display === 'block';
}

//function stackToString(stack) {
//    return stack.map(e => e.nodeName.toLowerCase()).join(', ');
//}


function deserializeRange(rangeString, root) {
    var rangeObj = JSON.parse(rangeString);
    return xpathRange.toRange(rangeObj.start, rangeObj.startOffset,
            rangeObj.end, rangeObj.endOffset, root);
}

function getRangeInfo(requestId, rangeString) {
    var range = deserializeRange(rangeString, document.getElementById('article'));

    var rect = range.getBoundingClientRect();
    cmdRangeInfo(requestId, rect.top, rect.bottom);
}

function highlightRange(rangeString) {
    var range = deserializeRange(rangeString, document.getElementById('article'));

    document.getSelection().removeAllRanges();
    document.getSelection().addRange(range);
}


/*
// for debugging in a web-browser:
// just uncomment this and copy-paste everything into the browser console

//function serializeRange(range, root) {
//    return null;
//}

function cmdStart() {
    console.log('parse start');
}
function cmdEnd() {
    console.log('parse end');
}
function cmdText(text, extras, range, top, bottom) {
    console.log('TEXT: ' + text);
    if (range !== null) console.log('RANGE: ' + JSON.stringify(range));
    if (extras !== null) console.log('EXTRAS: ' + extras);
}
function cmdImg(altText, title, src, range, top, bottom) {
    console.log('IMG: ' + altText + ', title: ' + title + ', src: ' + src);
    if (range !== null) console.log('RANGE: ' + JSON.stringify(range));
}


parseDocumentText();
*/
