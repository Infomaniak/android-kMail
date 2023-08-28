const scriptFirstLine = new RegExp(/:([\d]+)/).exec(Error().stack)[1] // Android only

/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

// MESSAGE_SELECTOR = "#kmail-message-content";
const PREFERENCES = {
    normalizeMessageWidths: true,
    mungeImages: true,
    mungeTables: true,
    minimumEffectiveRatio: 0.7
};

// Functions

/**
 * Normalize the width of the mail displayed
 * @param webViewWidth Width of the webview
 * @param messageUid Id of the displayed mail
 */
function normalizeMessageWidth(webViewWidth, messageUid) {
    // We want to report any thrown error that our script may encounter
    try {
        normalizeElementWidths(document.querySelectorAll(MESSAGE_SELECTOR), webViewWidth, messageUid);
    } catch (error) {
        reportError(error, scriptFirstLine, messageUid); // Android only has a `scriptFirstLine` argument
    }
    return true;
}

/**
 * Normalizes the width of elements supplied to the document body's overall width.
 * Narrower elements are zoomed in, and wider elements are zoomed out.
 * This method is idempotent.
 * @param elements DOM elements to normalize
 * @param webViewWidth Width of the webview
 * @param messageUid Id of the displayed mail
 */
function normalizeElementWidths(elements, webViewWidth, messageUid) {
    const documentWidth = document.body.offsetWidth;
    logInfo(`Starts to normalize elements. Document width: ${documentWidth}. WebView width: ${webViewWidth}.`);

    for (const element of elements) {
        logInfo(`Current element: ${elementDebugName(element)}.`);

        // Reset any existing normalization
        const originalZoom = element.style.zoom;
        if (originalZoom) {
            element.style.zoom = 1;
            logInfo(`Initial zoom reset to 1. Old zoom: ${originalZoom}.`);
        }

        const originalWidth = element.style.width;
        element.style.width = `${webViewWidth}px`;
        transformContent(element, webViewWidth, element.scrollWidth);

        if (PREFERENCES.normalizeMessageWidths) {
            const newZoom = documentWidth / element.scrollWidth;
            logInfo(`Zoom updated: documentWidth / element.scrollWidth -> ${documentWidth} / ${element.scrollWidth} = ${newZoom}.`);
            element.style.zoom = newZoom;
        }

        element.style.width = originalWidth;

        if (document.documentElement.scrollWidth > document.documentElement.clientWidth) {
            logInfo(`After zooming the mail it can still scroll: found clientWidth / scrollWidth -> ${document.documentElement.clientWidth} / ${document.documentElement.scrollWidth}`);
            reportOverScroll(document.documentElement.clientWidth, document.documentElement.scrollWidth, messageUid);
        }
    }
}

/**
 * Transform the content of a DOM element to munge its children if they are too wide
 * @param element DOM element to inspect
 * @param documentWidth Width of the overall document
 * @param elementWidth Element width before any action is done
 */
function transformContent(element, documentWidth, elementWidth) {
    if (elementWidth <= documentWidth) {
        logInfo(`Element doesn't need to be transformed. Current size: ${elementWidth}, DocumentWidth: ${documentWidth}.`);
        return;
    }
    logInfo(`Element will be transformed.`);

    let newWidth = elementWidth;
    let isTransformationDone = false;
    /** Format of entries : { function: fn, object: object, arguments: [list of arguments] } */
    let actionsLog = [];

    // Try munging all divs or textareas with inline styles where the width
    // is wider than `documentWidth`, and change it to be a max-width.
    if (PREFERENCES.normalizeMessageWidths) {
        const nodes = element.querySelectorAll('div[style], textarea[style]');
        const areNodesTransformed = transformBlockElements(nodes, documentWidth, actionsLog);
        if (areNodesTransformed) {
            newWidth = element.scrollWidth;
            logTransformation('munge div[style] and textarea[style]', element, elementWidth, newWidth, documentWidth);
            if (newWidth <= documentWidth) {
                isTransformationDone = true;
                logInfo('Munging div[style] and textarea[style] is enough.');
            }
        }
    }

    if (!isTransformationDone && PREFERENCES.mungeImages) {
        // OK, that wasn't enough. Find images with widths and override their widths.
        const images = element.querySelectorAll('img');
        const areImagesTransformed = transformImages(images, documentWidth, actionsLog);
        if (areImagesTransformed) {
            newWidth = element.scrollWidth;
            logTransformation('munge img', element, elementWidth, newWidth, documentWidth);
            if (newWidth <= documentWidth) {
                isTransformationDone = true;
                logInfo('Munging img is enough.');
            }
        }
    }

    if (!isTransformationDone && PREFERENCES.mungeTables) {
        // OK, that wasn't enough. Find tables with widths and override their widths.
        // Also ensure that any use of 'table-layout: fixed' is negated, since using
        // that with 'width: auto' causes erratic table width.
        const tables = element.querySelectorAll('table');
        const areTablesTransformed = addClassToElements(tables, shouldMungeTable, 'munged', actionsLog);
        if (areTablesTransformed) {
            newWidth = element.scrollWidth;
            logTransformation('munge table', element, elementWidth, newWidth, documentWidth);
            if (newWidth <= documentWidth) {
                isTransformationDone = true;
                logInfo('Munging table is enough.');
            }
        }
    }

    if (!isTransformationDone && PREFERENCES.mungeTables) {
        // OK, that wasn't enough. Try munging all <td> to override any width and nowrap set.
        const beforeTransformationWidth = newWidth;
        const tds = element.querySelectorAll('td');
        const tmpActionsLog = [];
        const areTdsTransformed = addClassToElements(tds, null, 'munged', tmpActionsLog);
        if (areTdsTransformed) {
            newWidth = element.scrollWidth;
            logTransformation('munge td', element, elementWidth, newWidth, documentWidth);

            if (newWidth <= documentWidth) {
                isTransformationDone = true;
                logInfo('Munging td is enough.');
            } else if (newWidth === beforeTransformationWidth) {
                // This transform did not improve things, and it is somewhat risky.
                // Back it out, since it's the last transform and we gained nothing.
                undoActions(tmpActionsLog);
                logInfo('Munging td did not improve things, we undo these actions.');
            } else {
                // The transform WAS effective (although not 100%).
                // Copy the temporary action log entries over as normal.
                actionsLog.push(...tmpActionsLog);
                logInfo('Munging td is not enough but is effective.');
            }
        }
    }

    // If the transformations shrank the width significantly enough, leave them in place.
    // We figure that in those cases, the benefits outweight the risk of rendering artifacts.
    const transformationRatio = (elementWidth - newWidth) / (elementWidth - documentWidth);
    if (!isTransformationDone && transformationRatio > PREFERENCES.minimumEffectiveRatio) {
        logInfo('Transforms deemed effective enough.');
        isTransformationDone = true;
    }

    if (!isTransformationDone) {
        // Reverse all changes if the width is STILL not narrow enough.
        // (except the width->maxWidth change, which is not particularly destructive)
        undoActions(actionsLog);
        if (actionsLog.length > 0) {
            logInfo(`All mungers failed, we will reverse ${actionsLog.length} changes.`);
        } else {
            logInfo(`No mungers applied, width is still too wide.`);
        }
        return;
    }

    logInfo(`Mungers succeeded. We did ${actionsLog.length} changes.`);
}

/**
 * Transform blocks : a div or a textarea
 * @param nodes Array of blocks to inspect
 * @param documentWidth Width of the overall document
 * @param actionsLog Array with all the actions performed
 * @returns true if any modification is performed
 */
function transformBlockElements(nodes, documentWidth, actionsLog) {
    let elementsAreModified = false;
    for (const node of nodes) {
        const widthString = node.style.width || node.style.minWidth;
        const index = widthString ? widthString.indexOf('px') : -1;
        if (index >= 0 && widthString.slice(0, index) > documentWidth) {
            saveStyleProperty(node, 'width', actionsLog);
            saveStyleProperty(node, 'minWidth', actionsLog);
            saveStyleProperty(node, 'maxWidth', actionsLog);

            node.style.width = '100%';
            node.style.minWidth = '';
            node.style.maxWidth = widthString;

            elementsAreModified = true;
        }
    }

    return elementsAreModified;
}

/**
 * Transform images
 * @param images Array of images to inspect
 * @param documentWidth Width of the overall document
 * @param actionsLog Array with all the actions performed
 * @returns true if any modification is performed
 */
function transformImages(images, documentWidth, actionsLog) {
    let imagesAreModified = false;
    for (const image of images) {
        if (image.offsetWidth > documentWidth) {
            saveStyleProperty(image, 'width', actionsLog);
            saveStyleProperty(image, 'maxWidth', actionsLog);
            saveStyleProperty(image, 'height', actionsLog);

            image.style.width = '100%';
            image.style.maxWidth = `${documentWidth}px`;
            image.style.height = 'auto';

            imagesAreModified = true;
        }
    }

    return imagesAreModified;
}

/**
 * Add a class to a DOM element if a condition is fulfilled
 * @param nodes Array of elements to inspect
 * @param conditionFunction Function allowing to test a condition with respect to an element. If it is null, the condition is considered true.
 * @param classToAdd Class to be added
 * @param actionsLog Array with all the actions performed
 * @returns true if the class was added to at least one element
 */
function addClassToElements(nodes, conditionFunction, classToAdd, actionsLog) {
    let classAdded = false;
    for (const node of nodes) {
        if (!conditionFunction || conditionFunction(node)) {
            if (node.classList.contains(classToAdd)) { continue; }
            node.classList.add(classToAdd);
            classAdded = true;
            actionsLog.push({ function: node.classList.remove, object: node.classList, arguments: [classToAdd] });
        }
    }
    return classAdded;
}

/**
 * Save a CSS property and its value as a ´data-´ property
 * @param node DOM element for which the property will be saved
 * @param property Name of the property to save
 * @param actionsLog Array with all the actions performed
 */
function saveStyleProperty(node, property, actionsLog) {
    const savedName = `data-${property}`;
    node.setAttribute(savedName, node.style[property]);
    actionsLog.push({ function: undoSetProperty, object: node, arguments: [property, savedName] });
}

/**
 * Undo a previously changed property
 * @param property Property to undo
 * @param savedProperty Saved property
 */
function undoSetProperty(property, savedProperty) {
    this.style[property] = savedProperty ? this.getAttribute(savedProperty) : '';
}

/**
 * Undo previous actions
 * @param actionsLog Previous actions done
 */
function undoActions(actionsLog) {
    for (const action of actionsLog) {
        action['function'].apply(action['object'], action['arguments']);
    }
}

/**
 * Checks if a table should be munged
 * @param table Table HTML object
 * @returns true if the object has a width as an attribute or in its style
 */
function shouldMungeTable(table) {
    return table.hasAttribute('width') || table.style.width;
}

// Logger

function logInfo(text) {
    console.info(`[MUNGER_LOG] ${text}`);
}

function logTransformation(action, element, elementWidth, newWidth, documentWidth) {
    logInfo(`Ran ${action} on ${elementDebugName(element)}. OldWidth=${elementWidth}, NewWidth=${newWidth}, DocWidth=${documentWidth}.`);
}

function elementDebugName(element) {
    const id = element.id !== '' ? ` #${element.id}` : '';
    const classes = element.classList.length != 0 ? ` (classes: ${element.classList.value})` : '';
    return `<${element.tagName}${id}${classes}>`;
}
