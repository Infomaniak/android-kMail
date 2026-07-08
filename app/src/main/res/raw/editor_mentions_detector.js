/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

let lastSentValue = null;
const zeroWidthCharsRegex = /[\u200B-\u200D\uFEFF]/g;

const extractMentionQuery = (textBeforeCaret) => {
    const normalizedText = textBeforeCaret.replace(zeroWidthCharsRegex, "");
    // Search backwards for @ preceded by space, non-breaking space, or start of string
    const lastAtPos = [...normalizedText].reduceRight((pos, char, i) => {
        if (pos !== -1) return pos;
        return char === "@" && (i === 0 || normalizedText[i - 1] === " " || normalizedText[i - 1] === "\u00A0") ? i : -1;
    }, -1);

    if (lastAtPos < 0) return null;

    // If there are 2 empty spaces we restart the query. One empty space is possible for the name, but not two.
    let query = normalizedText.slice(lastAtPos + 1);
    if (/\s{2,}/.test(query)) {
        return null;
    }

    return query.length > 0 ? query : null;
};

const notifyIfChanged = () => {
    const textBeforeCaret = globalThis.getTextBeforeCaret(true);
    const query = extractMentionQuery(textBeforeCaret);
    if (query === lastSentValue) return;

    lastSentValue = query;
    onMentionQueryChanged(query == null ? "" : query);
};

// Check if the caret is right before a mention node
const isCaretBeforeMention = () => {
    const selection = globalThis.getSelection();
    if (!selection || selection.rangeCount === 0) return false;

    const range = selection.getRangeAt(0);
    if (!range.collapsed) return false;

    const node = range.startContainer;
    const offset = range.startOffset;
    if (offset !== node.textContent.length) return false;

    const next = node.nextSibling;
    if (next?.nodeType !== Node.ELEMENT_NODE) return false;

    return next.matches?.("a[data-ik-mention-ref]") || false;
}

// Handle Enter key to split the block if the caret is before a mention.
// This is used to avoid creating an extra empty line in between the mentions.
const handleEnter = (event) => {
    if (event.key !== "Enter") return;
    if (!isCaretBeforeMention()) return;

    event.preventDefault();

    const selection = globalThis.getSelection();
    const range = selection.getRangeAt(0);
    const block = getBlockParent(range.startContainer);

    const newBlock = moveSiblingsToNewLine(range, block);

    const newRange = document.createRange();
    newRange.setStart(newBlock, 0);
    newRange.collapse(true);

    selection.removeAllRanges();
    selection.addRange(newRange);
}

// We move all the existing elements (after the caret) on the current line to the new line
function moveSiblingsToNewLine(range, block) {
    const newBlock = document.createElement('div');
    const mentionNode = range.startContainer.nextSibling;
    let current = mentionNode;
    while (current) {
        const next = current.nextSibling;
        newBlock.appendChild(current);
        current = next;
    }

    block.parentNode.insertBefore(newBlock, block.nextSibling);
    return newBlock;
}

const observeMention = () => {
    document.addEventListener("selectionchange", notifyIfChanged);
    document.addEventListener("input", notifyIfChanged);
    document.addEventListener("keydown", handleEnter);
};

observeMention();
