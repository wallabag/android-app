(function(f){if(typeof exports==="object"&&typeof module!=="undefined"){module.exports=f()}else if(typeof define==="function"&&define.amd){define([],f)}else{var g;if(typeof window!=="undefined"){g=window}else if(typeof global!=="undefined"){g=global}else if(typeof self!=="undefined"){g=self}else{g=this}g.xpathRange = f()}})(function(){var define,module,exports;return (function(){function r(e,n,t){function o(i,f){if(!n[i]){if(!e[i]){var c="function"==typeof require&&require;if(!f&&c)return c(i,!0);if(u)return u(i,!0);var a=new Error("Cannot find module '"+i+"'");throw a.code="MODULE_NOT_FOUND",a}var p=n[i]={exports:{}};e[i][0].call(p.exports,function(r){var n=e[i][1][r];return o(n||r)},p,p.exports,r,e,n,t)}return n[i].exports}for(var u="function"==typeof require&&require,i=0;i<t.length;i++)o(t[i]);return o}return r})()({1:[function(require,module,exports){
'use strict';

exports.__esModule = true;
exports.fromRange = fromRange;
exports.toRange = toRange;

var _getDocument = require('get-document');

var _getDocument2 = _interopRequireDefault(_getDocument);

var _domSeek = require('dom-seek');

var _domSeek2 = _interopRequireDefault(_domSeek);

var _simpleXpathPosition = require('simple-xpath-position');

var xpath = _interopRequireWildcard(_simpleXpathPosition);

function _interopRequireWildcard(obj) { if (obj && obj.__esModule) { return obj; } else { var newObj = {}; if (obj != null) { for (var key in obj) { if (Object.prototype.hasOwnProperty.call(obj, key)) newObj[key] = obj[key]; } } newObj['default'] = obj; return newObj; } }

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

var SHOW_TEXT = 4;

/**
 * Convert a `Range` to a pair of XPath expressions and offsets.
 *
 * If the optional parameter `root` is supplied, the computed XPath expressions
 * will be relative to it.
 *
 * @param {Range} range The Range to convert.
 * @param {Node} [root] The root context for the XPath expressions.
 * @returns {{start, startOffset, end, endOffset}}
 */
function fromRange(range, root) {
  var sc = range.startContainer;
  var so = range.startOffset;
  var ec = range.endContainer;
  var eo = range.endOffset;

  var start = xpath.fromNode(sc, root);
  var end = xpath.fromNode(ec, root);

  return {
    start: start,
    end: end,
    startOffset: so,
    endOffset: eo
  };
}

/**
 * Construct a `Range` from the given XPath expressions and offsets.
 *
 * If the optional parameter `root` is supplied, the XPath expressions are
 * evaluated as relative to it.
 *
 * @param {string} startPath An XPath expression for the start container.
 * @param {Number} startOffset The textual offset within the start container.
 * @param {string} endPath An XPath expression for the end container.
 * @param {Number} endOffset The textual offset within the end container.
 * @param {Node} [root] The root context for the XPath expressions.
 * @returns Range
 */
function toRange(startPath, startOffset, endPath, endOffset, root) {
  var document = (0, _getDocument2['default'])(root);

  var sc = xpath.toNode(startPath, root);
  if (sc === null) throw notFound('start');

  var si = document.createNodeIterator(sc, SHOW_TEXT);
  var so = startOffset - (0, _domSeek2['default'])(si, startOffset);

  sc = si.referenceNode;
  if (!si.pointerBeforeReferenceNode) {
    if (so > 0) throw indexSize('start');
    so += sc.length;
  }

  var ec = xpath.toNode(endPath, root);
  if (ec === null) throw notFound('end');

  var ei = document.createNodeIterator(ec, SHOW_TEXT);
  var eo = endOffset - (0, _domSeek2['default'])(ei, endOffset);

  ec = ei.referenceNode;
  if (!ei.pointerBeforeReferenceNode) {
    if (eo > 0) throw indexSize('end');
    eo += ec.length;
  }

  var range = document.createRange();
  range.setStart(sc, so);
  range.setEnd(ec, eo);

  return range;

  function notFound(which) {
    var error = new Error('The ' + which + ' node was not found.');
    error.name = 'NotFoundError';
    return error;
  }

  function indexSize(which) {
    var error = new Error('There is no text at the requested ' + which + ' offset.');
    error.name = 'IndexSizeError';
    return error;
  }
}

},{"dom-seek":3,"get-document":5,"simple-xpath-position":7}],2:[function(require,module,exports){
module.exports = parents

function parents(node, filter) {
  var out = []

  filter = filter || noop

  do {
    out.push(node)
    node = node.parentNode
  } while(node && node.tagName && filter(node))

  return out.slice(1)
}

function noop(n) {
  return true
}

},{}],3:[function(require,module,exports){
module.exports = require('./lib')['default'];

},{"./lib":4}],4:[function(require,module,exports){
'use strict';

exports.__esModule = true;
exports['default'] = seek;

var _ancestors = require('ancestors');

var _ancestors2 = _interopRequireDefault(_ancestors);

var _indexOf = require('index-of');

var _indexOf2 = _interopRequireDefault(_indexOf);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

var E_SHOW = 'Argument 1 of seek must use filter NodeFilter.SHOW_TEXT.';
var E_WHERE = 'Argument 2 of seek must be a number or a Text Node.';

var SHOW_TEXT = 4;
var TEXT_NODE = 3;

function seek(iter, where) {
  if (iter.whatToShow !== SHOW_TEXT) {
    throw new Error(E_SHOW);
  }

  var count = 0;
  var node = iter.referenceNode;
  var predicates = null;

  if (isNumber(where)) {
    predicates = {
      forward: function forward() {
        return count < where;
      },
      backward: function backward() {
        return count > where;
      }
    };
  } else if (isText(where)) {
    var forward = before(node, where) ? function () {
      return false;
    } : function () {
      return node !== where;
    };
    var backward = function backward() {
      return node != where || !iter.pointerBeforeReferenceNode;
    };
    predicates = { forward: forward, backward: backward };
  } else {
    throw new Error(E_WHERE);
  }

  while (predicates.forward() && (node = iter.nextNode()) !== null) {
    count += node.nodeValue.length;
  }

  while (predicates.backward() && (node = iter.previousNode()) !== null) {
    count -= node.nodeValue.length;
  }

  return count;
}

function isNumber(n) {
  return !isNaN(parseInt(n)) && isFinite(n);
}

function isText(node) {
  return node.nodeType === TEXT_NODE;
}

function before(ref, node) {
  if (ref === node) return false;

  var common = null;
  var left = [ref].concat((0, _ancestors2['default'])(ref)).reverse();
  var right = [node].concat((0, _ancestors2['default'])(node)).reverse();

  while (left[0] === right[0]) {
    common = left.shift();
    right.shift();
  }

  left = left[0];
  right = right[0];

  var l = (0, _indexOf2['default'])(common.childNodes, left);
  var r = (0, _indexOf2['default'])(common.childNodes, right);

  return l > r;
}

},{"ancestors":2,"index-of":6}],5:[function(require,module,exports){

/**
 * Module exports.
 */

module.exports = getDocument;

// defined by w3c
var DOCUMENT_NODE = 9;

/**
 * Returns `true` if `w` is a Document object, or `false` otherwise.
 *
 * @param {?} d - Document object, maybe
 * @return {Boolean}
 * @private
 */

function isDocument (d) {
  return d && d.nodeType === DOCUMENT_NODE;
}

/**
 * Returns the `document` object associated with the given `node`, which may be
 * a DOM element, the Window object, a Selection, a Range. Basically any DOM
 * object that references the Document in some way, this function will find it.
 *
 * @param {Mixed} node - DOM node, selection, or range in which to find the `document` object
 * @return {Document} the `document` object associated with `node`
 * @public
 */

function getDocument(node) {
  if (isDocument(node)) {
    return node;

  } else if (isDocument(node.ownerDocument)) {
    return node.ownerDocument;

  } else if (isDocument(node.document)) {
    return node.document;

  } else if (node.parentNode) {
    return getDocument(node.parentNode);

  // Range support
  } else if (node.commonAncestorContainer) {
    return getDocument(node.commonAncestorContainer);

  } else if (node.startContainer) {
    return getDocument(node.startContainer);

  // Selection support
  } else if (node.anchorNode) {
    return getDocument(node.anchorNode);
  }
}

},{}],6:[function(require,module,exports){
/*!
 * index-of <https://github.com/jonschlinkert/index-of>
 *
 * Copyright (c) 2014-2015 Jon Schlinkert.
 * Licensed under the MIT license.
 */

'use strict';

module.exports = function indexOf(arr, ele, start) {
  start = start || 0;
  var idx = -1;

  if (arr == null) return idx;
  var len = arr.length;
  var i = start < 0
    ? (len + start)
    : start;

  if (i >= arr.length) {
    return -1;
  }

  while (i < len) {
    if (arr[i] === ele) {
      return i;
    }
    i++;
  }

  return -1;
};

},{}],7:[function(require,module,exports){
module.exports = require('./lib/xpath')

},{"./lib/xpath":9}],8:[function(require,module,exports){
"use strict";

exports.__esModule = true;

function _classCallCheck(instance, Constructor) { if (!(instance instanceof Constructor)) { throw new TypeError("Cannot call a class as a function"); } }

var DOMException = function DOMException(message, name) {
  _classCallCheck(this, DOMException);

  this.message = message;
  this.name = name;
  this.stack = new Error().stack;
};

exports["default"] = DOMException;


DOMException.prototype = new Error();

DOMException.prototype.toString = function () {
  return this.name + ": " + this.message;
};

},{}],9:[function(require,module,exports){
'use strict';

exports.__esModule = true;
exports.fromNode = fromNode;
exports.toNode = toNode;

var _getDocument = require('get-document');

var _getDocument2 = _interopRequireDefault(_getDocument);

var _domException = require('./dom-exception');

var _domException2 = _interopRequireDefault(_domException);

function _interopRequireDefault(obj) { return obj && obj.__esModule ? obj : { 'default': obj }; }

// https://developer.mozilla.org/en-US/docs/XPathResult
var FIRST_ORDERED_NODE_TYPE = 9;

// Default namespace for XHTML documents
var HTML_NAMESPACE = 'http://www.w3.org/1999/xhtml';

/**
 * Compute an XPath expression for the given node.
 *
 * If the optional parameter `root` is supplied, the computed XPath expression
 * will be relative to it. Otherwise, the root element is the root of the
 * document to which `node` belongs.
 *
 * @param {Node} node The node for which to compute an XPath expression.
 * @param {Node} [root] The root context for the XPath expression.
 * @returns {string}
 */
function fromNode(node) {
  var root = arguments.length <= 1 || arguments[1] === undefined ? null : arguments[1];

  if (node === undefined) {
    throw new Error('missing required parameter "node"');
  }

  root = root || (0, _getDocument2['default'])(node);

  var path = '/';
  while (node !== root) {
    if (!node) {
      var message = 'The supplied node is not contained by the root node.';
      var name = 'InvalidNodeTypeError';
      throw new _domException2['default'](message, name);
    }
    path = '/' + nodeName(node) + '[' + nodePosition(node) + ']' + path;
    node = node.parentNode;
  }
  return path.replace(/\/$/, '');
}

/**
 * Find a node using an XPath relative to the given root node.
 *
 * The XPath expressions are evaluated relative to the Node argument `root`.
 *
 * If the optional parameter `resolver` is supplied, it will be used to resolve
 * any namespaces within the XPath.
 *
 * @param {string} path An XPath String to evaluate.
 * @param {Node} root The root context for the XPath expression.
 * @returns {Node|null} The first matching Node or null if none is found.
 */
function toNode(path, root) {
  var resolver = arguments.length <= 2 || arguments[2] === undefined ? null : arguments[2];

  if (path === undefined) {
    throw new Error('missing required parameter "path"');
  }
  if (root === undefined) {
    throw new Error('missing required parameter "root"');
  }

  // Make the path relative to the root, if not the document.
  var document = (0, _getDocument2['default'])(root);
  if (root !== document) path = path.replace(/^\//, './');

  // Make a default resolver.
  var documentElement = document.documentElement;
  if (resolver === null && documentElement.lookupNamespaceURI) {
    (function () {
      var defaultNS = documentElement.lookupNamespaceURI(null) || HTML_NAMESPACE;
      resolver = function resolver(prefix) {
        var ns = { '_default_': defaultNS };
        return ns[prefix] || documentElement.lookupNamespaceURI(prefix);
      };
    })();
  }

  return resolve(path, root, resolver);
}

// Get the XPath node name.
function nodeName(node) {
  switch (node.nodeName) {
    case '#text':
      return 'text()';
    case '#comment':
      return 'comment()';
    case '#cdata-section':
      return 'cdata-section()';
    default:
      return node.nodeName.toLowerCase();
  }
}

// Get the ordinal position of this node among its siblings of the same name.
function nodePosition(node) {
  var name = node.nodeName;
  var position = 1;
  while (node = node.previousSibling) {
    if (node.nodeName === name) position += 1;
  }
  return position;
}

// Find a single node with XPath `path`
function resolve(path, root, resolver) {
  try {
    // Add a default value to each path part lacking a prefix.
    var nspath = path.replace(/\/(?!\.)([^\/:\(]+)(?=\/|$)/g, '/_default_:$1');
    return platformResolve(nspath, root, resolver);
  } catch (err) {
    return fallbackResolve(path, root);
  }
}

// Find a single node with XPath `path` using the simple, built-in evaluator.
function fallbackResolve(path, root) {
  var steps = path.split("/");
  var node = root;
  while (node) {
    var step = steps.shift();
    if (step === undefined) break;
    if (step === '.') continue;

    var _step$split = step.split(/[\[\]]/);

    var name = _step$split[0];
    var position = _step$split[1];

    name = name.replace('_default_:', '');
    position = position ? parseInt(position) : 1;
    node = findChild(node, name, position);
  }
  return node;
}

// Find a single node with XPath `path` using `document.evaluate`.
function platformResolve(path, root, resolver) {
  var document = (0, _getDocument2['default'])(root);
  var r = document.evaluate(path, root, resolver, FIRST_ORDERED_NODE_TYPE, null);
  return r.singleNodeValue;
}

// Find the child of the given node by name and ordinal position.
function findChild(node, name, position) {
  for (node = node.firstChild; node; node = node.nextSibling) {
    if (nodeName(node) === name && --position === 0) break;
  }
  return node;
}

},{"./dom-exception":8,"get-document":5}]},{},[1])(1)
});
