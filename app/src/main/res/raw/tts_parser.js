function nextDomElem(elem) {
    var result;
    if (elem.hasChildNodes() && elem.tagName != 'SCRIPT') {
        result = elem.firstChild;
    } else {
        result = elem.nextSibling;
        while((result == null) && (elem != null)) {
            elem = elem.parentNode;
            if (elem != null) {
                result = elem.nextSibling;
            }
        }
    }
    return result;
}

function nextTextElem(elem) {
    while(elem = nextDomElem(elem)) {
        if ((elem.nodeType == 3) && (elem.textContent.trim().length > 0)) {
            break;
        }
    }
    return elem;
}

function cmdStart() {
        console.log('%1$sstart');
}
function cmdEnd() {
        console.log('%1$send');
}
function cmdText(text, top, bottom) {
        console.log('%1$s' + top + ':' + bottom + ':' + text);
}

function parseDocumentText() {
    var elem = document.getElementsByTagName('body')[0];
    var range = document.createRange();
    cmdStart();
    while(elem = nextTextElem(elem)) {
        range.selectNode(elem);
        var rect = range.getBoundingClientRect();
        var text = elem.textContent.trim();
        cmdText(text, rect.top, rect.bottom);
    }
    cmdEnd();
}
