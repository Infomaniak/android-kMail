/**
 * Only revert transforms that do an imperfect job of shrinking content if they fail
 * to shrink by this much. Expressed as a ratio of:
 * (original width difference : width difference after transforms);
 */

var TRANSFORM_MINIMUM_EFFECTIVE_RATIO = 0.7;

// Don't ship with this on.
var DEBUG_DISPLAY_TRANSFORMS = false;

var gTransformText = {};

// TODO
//var WEBVIEW_WIDTH = 400;
var NORMALIZE_MESSAGE_WIDTHS = true;
var ENABLE_MUNGE_IMAGES = true;
var ENABLE_MUNGE_TABLES = true;

function normalizeAllMessageWidths() {
    var expandedBodyDivs;

    expandedBodyDivs = document.querySelectorAll("#kmail-message-content");
    normalizeElementWidths(expandedBodyDivs);
}

/*
 * Normalizes the width of all elements supplied to the document body's overall width.
 * Narrower elements are zoomed in, and wider elements are zoomed out.
 * This method is idempotent.
 */
function normalizeElementWidths(elements) {
    var i;
    var el;
    var documentWidth;
    var goalWidth;
    var origWidth;
    var newZoom, oldZoom;
    var outerZoom;

    documentWidth = document.body.offsetWidth;
    goalWidth = WEBVIEW_WIDTH;

    for (i = 0; i < elements.length; i++) {
        el = elements[i];
        oldZoom = el.style.zoom;
        // reset any existing normalization
        if (oldZoom) {
            el.style.zoom = 1;
        }
        origWidth = el.style.width;
        el.style.width = goalWidth + "px";
        transformContent(el, goalWidth, el.scrollWidth);
        newZoom = documentWidth / el.scrollWidth;
        if (NORMALIZE_MESSAGE_WIDTHS) {
            outerZoom = 1;
            el.style.zoom = newZoom / outerZoom;
        }
        el.style.width = origWidth;
    }
}

function transformContent(el, docWidth, elWidth) {
    var nodes;
    var i, len;
    var newWidth = elWidth;
    var touched;
    // the format of entries in this array is:
    // entry := [ undoFunction, undoFunctionThis, undoFunctionParamArray ]
    var actionLog = [];
    var done = false;
    var msgId;
    var transformText;
    var existingText;
    var textElement;
    var start;
    var beforeWidth;
    var tmpActionLog = [];
    if (elWidth <= docWidth) {
        return;
    }

    start = Date.now();

    if (el.parentElement.classList.contains("mail-message")) {
        msgId = el.parentElement.id;
        transformText = "[origW=" + elWidth + "/" + docWidth;
    }

    // Try munging all divs or textareas with inline styles where the width
    // is wider than docWidth, and change it to be a max-width.
    touched = false;
    nodes = ENABLE_MUNGE_TABLES ? el.querySelectorAll("div[style], textarea[style]") : [];
    touched = transformBlockElements(nodes, docWidth, actionLog);
    if (touched) {
        newWidth = el.scrollWidth;
        console.log("ran div-width munger on el=" + el + " oldW=" + elWidth + " newW=" + newWidth
            + " docW=" + docWidth);
        if (msgId) {
            transformText += " DIV:newW=" + newWidth;
        }
        if (newWidth <= docWidth) {
            done = true;
        }
    }

    if (!done) {
        // OK, that wasn't enough. Find images with widths and override their widths.
        nodes = ENABLE_MUNGE_IMAGES ? el.querySelectorAll("img") : [];
        touched = transformImages(nodes, docWidth, actionLog);
        if (touched) {
            newWidth = el.scrollWidth;
            console.log("ran img munger on el=" + el + " oldW=" + elWidth + " newW=" + newWidth
                + " docW=" + docWidth);
            if (msgId) {
                transformText += " IMG:newW=" + newWidth;
            }
            if (newWidth <= docWidth) {
                done = true;
            }
        }
    }

    if (!done) {
        // OK, that wasn't enough. Find tables with widths and override their widths.
        // Also ensure that any use of 'table-layout: fixed' is negated, since using
        // that with 'width: auto' causes erratic table width.
        nodes = ENABLE_MUNGE_TABLES ? el.querySelectorAll("table") : [];
        touched = addClassToElements(nodes, shouldMungeTable, "munged",
            actionLog);
        if (touched) {
            newWidth = el.scrollWidth;
            console.log("ran table munger on el=" + el + " oldW=" + elWidth + " newW=" + newWidth
                + " docW=" + docWidth);
            if (msgId) {
                transformText += " TABLE:newW=" + newWidth;
            }
            if (newWidth <= docWidth) {
                done = true;
            }
        }
    }

    if (!done) {
        // OK, that wasn't enough. Try munging all <td> to override any width and nowrap set.
        beforeWidth = newWidth;
        nodes = ENABLE_MUNGE_TABLES ? el.querySelectorAll("td") : [];
        touched = addClassToElements(nodes, null /* mungeAll */, "munged",
            tmpActionLog);
        if (touched) {
            newWidth = el.scrollWidth;
            console.log("ran td munger on el=" + el + " oldW=" + elWidth + " newW=" + newWidth
                + " docW=" + docWidth);
            if (msgId) {
                transformText += " TD:newW=" + newWidth;
            }
            if (newWidth <= docWidth) {
                done = true;
            } else if (newWidth == beforeWidth) {
                // this transform did not improve things, and it is somewhat risky.
                // back it out, since it's the last transform and we gained nothing.
                undoActions(tmpActionLog);
            } else {
                // the transform WAS effective (although not 100%)
                // copy the temporary action log entries over as normal
                for (i = 0, len = tmpActionLog.length; i < len; i++) {
                    actionLog.push(tmpActionLog[i]);
                }
            }
        }
    }

    // If the transformations shrank the width significantly enough, leave them in place.
    // We figure that in those cases, the benefits outweight the risk of rendering artifacts.
    if (!done && (elWidth - newWidth) / (elWidth - docWidth) >
            TRANSFORM_MINIMUM_EFFECTIVE_RATIO) {
        console.log("transform(s) deemed effective enough");
        done = true;
    }

    if (done) {
        if (msgId) {
            transformText += "]";
            existingText = gTransformText[msgId];
            if (!existingText) {
                transformText = "Message transforms: " + transformText;
            } else {
                transformText = existingText + " " + transformText;
            }
            gTransformText[msgId] = transformText;
            window.mail.onMessageTransform(msgId, transformText);
            if (DEBUG_DISPLAY_TRANSFORMS) {
                textElement = el.firstChild;
                if (!textElement.classList || !textElement.classList.contains("transform-text")) {
                    textElement = document.createElement("div");
                    textElement.classList.add("transform-text");
                    textElement.style.fontSize = "10px";
                    textElement.style.color = "#ccc";
                    el.insertBefore(textElement, el.firstChild);
                }
                textElement.innerHTML = transformText + "<br>";
            }
        }
        console.log("munger(s) succeeded, elapsed time=" + (Date.now() - start));
        return;
    }

    // reverse all changes if the width is STILL not narrow enough
    // (except the width->maxWidth change, which is not particularly destructive)
    undoActions(actionLog);
    if (actionLog.length > 0) {
        console.log("all mungers failed, changes reversed. elapsed time=" + (Date.now() - start));
    }
}

function undoActions(actionLog) {
    for (i = 0, len = actionLog.length; i < len; i++) {
        actionLog[i][0].apply(actionLog[i][1], actionLog[i][2]);
    }
}

function addClassToElements(nodes, conditionFn, classToAdd, actionLog) {
    var i, len;
    var node;
    var added = false;
    for (i = 0, len = nodes.length; i < len; i++) {
        node = nodes[i];
        if (!conditionFn || conditionFn(node)) {
            if (node.classList.contains(classToAdd)) {
                continue;
            }
            node.classList.add(classToAdd);
            added = true;
            actionLog.push([node.classList.remove, node.classList, [classToAdd]]);
        }
    }
    return added;
}

function transformBlockElements(nodes, docWidth, actionLog) {
    var i, len;
    var node;
    var wStr;
    var index;
    var touched = false;

    for (i = 0, len = nodes.length; i < len; i++) {
        node = nodes[i];
        wStr = node.style.width || node.style.minWidth;
        index = wStr ? wStr.indexOf("px") : -1;
        if (index >= 0 && wStr.slice(0, index) > docWidth) {
            saveStyleProperty(node, "width", actionLog);
            saveStyleProperty(node, "minWidth", actionLog);
            saveStyleProperty(node, "maxWidth", actionLog);
            node.style.width = "100%";
            node.style.minWidth = "";
            node.style.maxWidth = wStr;
            touched = true;
        }
    }
    return touched;
}

function transformImages(nodes, docWidth, actionLog) {
    var i, len;
    var node;
    var w, h;
    var touched = false;

    for (i = 0, len = nodes.length; i < len; i++) {
        node = nodes[i];
        w = node.offsetWidth;
        h = node.offsetHeight;
        // shrink w/h proportionally if the img is wider than available width
        if (w > docWidth) {
            saveStyleProperty(node, "maxWidth", actionLog);
            saveStyleProperty(node, "width", actionLog);
            saveStyleProperty(node, "height", actionLog);
            node.style.maxWidth = docWidth + "px";
            node.style.width = "100%";
            node.style.height = "auto";
            touched = true;
        }
    }
    return touched;
}

function saveStyleProperty(node, property, actionLog) {
    var savedName = "data-" + property;
    node.setAttribute(savedName, node.style[property]);
    actionLog.push([undoSetProperty, node, [property, savedName]]);
}

function undoSetProperty(property, savedProperty) {
    this.style[property] = savedProperty ? this.getAttribute(savedProperty) : "";
}

function shouldMungeTable(table) {
    return table.hasAttribute("width") || table.style.width;
}

//normalizeAllMessageWidths();
