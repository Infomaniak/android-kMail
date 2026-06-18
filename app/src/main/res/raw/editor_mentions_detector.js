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
const validMentionCharsRegex = /^[A-Za-z0-9._+-]*$/;
const zeroWidthCharsRegex = /[\u200B-\u200D\uFEFF]/g;
const mentionQueryRegex = /(?:^|\s)@(\S*)$/;

const getBlockParent = (node) => {
    const editor = getEditor();
    const blockTags = new Set(["DIV", "P", "H1", "H2", "H3", "H4", "H5", "H6", "LI", "BLOCKQUOTE", "TD", "TR"]);
    let current = node;
    
    while (current && current !== editor && current.nodeType !== Node.DOCUMENT_NODE) {
        if (current.nodeType === Node.ELEMENT_NODE && blockTags.has(current.tagName.toUpperCase())) {
            return current;
        }
        current = current.parentNode;
    }
    return editor;
};

const getTextBeforeCaret = () => {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return "";

    const range = selection.getRangeAt(0);
    if (!range.collapsed) return "";

    const block = getBlockParent(range.startContainer);
    const preRange = range.cloneRange();
    
    try {
        preRange.setStart(block, 0);
    } catch (e) {
        preRange.selectNodeContents(getEditor());
    }
    
    preRange.setEnd(range.endContainer, range.endOffset);

    const fragment = preRange.cloneContents();

    const mentions = fragment.querySelectorAll("a[data-ik-mention-ref]");
    mentions.forEach(mention => mention.replaceWith(" "));

    const brs = fragment.querySelectorAll("br");
    brs.forEach(br => br.replaceWith("\n"));

    const blocks = fragment.querySelectorAll("div, p, li, blockquote");
    blocks.forEach(b => {
        b.prepend(document.createTextNode("\n"));
    });

    return fragment.textContent;
};

const extractMentionQuery = (textBeforeCaret) => {
    const normalizedText = textBeforeCaret.replace(zeroWidthCharsRegex, "");
    
    const match = normalizedText.match(mentionQueryRegex);
    if (!match) return null;

    const parts = match[1].split('@');
    const query = parts[parts.length - 1];

    return validMentionCharsRegex.test(query) ? query : null;
};

const isInsideMentionLink = () => {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return false;

    const range = selection.getRangeAt(0);
    const node = range.startContainer.nodeType === Node.ELEMENT_NODE
        ? range.startContainer
        : range.startContainer.parentElement;

    return !!node?.closest("a[data-ik-mention-ref]");
};

const resetMentionQuery = () => {
    if (lastSentValue === null) return;
    lastSentValue = null;
    onMentionQueryChanged("");
};

const notifyIfChanged = () => {
    if (isInsideMentionLink()) {
        resetMentionQuery();
        return;
    }

    const textBeforeCaret = getTextBeforeCaret();
    const query = extractMentionQuery(textBeforeCaret);

    if (query === lastSentValue) return;
    lastSentValue = query;

    onMentionQueryChanged(query != null ? query : "");
};

const observeMention = () => {
    if (globalThis.__kmailMentionDetectionInitialized) return;
    globalThis.__kmailMentionDetectionInitialized = true;

    document.addEventListener("selectionchange", notifyIfChanged);
    document.addEventListener("input", notifyIfChanged);
};

observeMention();
