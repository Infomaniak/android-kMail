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
const validMentionCharsRegex = /^[A-Za-z0-9._+-]*(?:@[A-Za-z0-9.-]*)?$/;
const zeroWidthCharsRegex = /[\u200B-\u200D\uFEFF]/g;
const mentionQueryRegex = /(?:^|\s)@(\S*)$/;

// We get the parent block to get the correct range to look for the @
const getBlockParent = (node) => {
    const editor = getEditor();
    let current = node;
    
    while (current && current !== editor && current.nodeType !== Node.DOCUMENT_NODE) {
        if (current.nodeType === Node.ELEMENT_NODE) {
            return current;
        }
        current = current.parentNode;
    }
    return editor;
};

const getTextBeforeCaret = () => {
    const selection = globalThis.getSelection();
    if (!selection || selection.rangeCount === 0) return "";

    const range = selection.getRangeAt(0);
    if (!range.collapsed) return "";

    const block = getBlockParent(range.startContainer);
    const preRange = range.cloneRange();
    
   if (block && block.nodeType === Node.ELEMENT_NODE) {
       preRange.setStart(block, 0);
   } else {
       preRange.selectNodeContents(getEditor());
   }
    
    preRange.setEnd(range.endContainer, range.endOffset);

    const fragment = preRange.cloneContents();
    // Replace all the existing mentions with a space to ignore them when extracting the query
    const mentions = fragment.querySelectorAll("a[data-ik-mention-ref]");
    mentions.forEach(mention => mention.replaceWith(" "));

    return fragment.textContent;
};

const extractMentionQuery = (textBeforeCaret) => {
    const normalizedText = textBeforeCaret.replace(zeroWidthCharsRegex, "");
    const match = normalizedText.match(mentionQueryRegex);
    if (!match) return null;

    const query = match[1];
    return validMentionCharsRegex.test(query) ? query : null;
};

const notifyIfChanged = () => {
    const textBeforeCaret = getTextBeforeCaret();
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

    const newBlock = document.createElement('div');

    const mentionNode = range.startContainer.nextSibling;
    let current = mentionNode;
    while (current) {
        const next = current.nextSibling;
        newBlock.appendChild(current);
        current = next;
    }

    block.parentNode.insertBefore(newBlock, block.nextSibling);

    const newRange = document.createRange();
    newRange.setStart(newBlock, 0);
    newRange.collapse(true);

    selection.removeAllRanges();
    selection.addRange(newRange);
}

const observeMention = () => {
    if (globalThis.__kmailMentionDetectionInitialized) return;
    globalThis.__kmailMentionDetectionInitialized = true;

    document.addEventListener("selectionchange", notifyIfChanged);
    document.addEventListener("input", notifyIfChanged);
    document.addEventListener("keydown", handleEnter);
};

observeMention();
