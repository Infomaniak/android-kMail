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

function insertMention(userMail, userName, query) {
    if (!userMail) return;

    const selection = globalThis.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const caretRange = selection.getRangeAt(0);
    if (!caretRange.collapsed) return;

    const block = getBlockParent(caretRange.startContainer);
    const textBeforeCaret = getTextBeforeCaret(false);

    const searchText = "@" + query;
    const searchIndex = textBeforeCaret.lastIndexOf(searchText);
    if (searchIndex < 0) return;

    const mentionStartOffset = searchIndex;
    const startPos = getDomPositionForTextOffset(mentionStartOffset, block);

    const replaceRange = document.createRange();
    replaceRange.setStart(startPos.node, startPos.offset);
    replaceRange.setEnd(caretRange.endContainer, caretRange.endOffset);
    replaceRange.deleteContents();

    // Replace by the anchor with the mention information
    const anchor = document.createElement("a");
    anchor.dataset.ikMentionRef = userMail;
    anchor.setAttribute("href", `mailto:${userMail}`);
    anchor.setAttribute("contenteditable", "false");

    const mentionText = `@${userName || userMail}`;
    // Add non-breaking space after emoji to fix Chrome deletion bug
    anchor.textContent = endsWithEmoji(mentionText) ? mentionText + "\u00A0" : mentionText;

    replaceRange.insertNode(anchor);

    // Add a non-breaking space after the mention
    const trailingSpace = document.createTextNode("\u00A0");
    anchor.after(trailingSpace);

    // We replace the cursor after the space of the mention
    const newCaretRange = document.createRange();
    newCaretRange.setStartAfter(trailingSpace);
    newCaretRange.collapse(true);

    selection.removeAllRanges();
    selection.addRange(newCaretRange);
}

const endsWithEmoji = (text) => {
    // Matches an emoji at the end (supports ZWJ sequences like 👨‍👩‍👧‍👦)
    return /(?:\p{Extended_Pictographic}(?:[\uFE0E\uFE0F])?(?:\u200D\p{Extended_Pictographic}(?:[\uFE0E\uFE0F])?)*)$/u.test(text);
};

// Converts a plain-text character offset into a DOM position.
// Walks all text nodes inside block, subtracting their lengths until it finds the node containing that character position
const getDomPositionForTextOffset = (targetOffset, block) => {
    const walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT);
    let node;

    while ((node = walker.nextNode())) {
        const length = node.textContent.length;
        if (targetOffset <= length) return { node, offset: targetOffset };
        targetOffset -= length;
    }

    return { node: block, offset: 0 };
};
