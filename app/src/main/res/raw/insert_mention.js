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

function insertMention(userMail, userName) {
    if (!userMail || !userName) return;

    const selection = globalThis.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const caretRange = selection.getRangeAt(0);
    if (!caretRange.collapsed) return;

    const editor = getEditor();

    const getBlockParent = (node) => {
        let current = node;
        while (current && current !== editor && current.nodeType !== Node.DOCUMENT_NODE) {
            if (current.nodeType === Node.ELEMENT_NODE) {
                return current;
            }
            current = current.parentNode;
        }
        return editor;
    };

    const block = getBlockParent(caretRange.startContainer);

    const preRange = caretRange.cloneRange();
    if (block && block.nodeType === Node.ELEMENT_NODE) {
        preRange.setStart(block, 0);
    } else {
        preRange.selectNodeContents(getEditor());
    }
    preRange.setEnd(caretRange.endContainer, caretRange.endOffset);

    const textBeforeCaret = preRange.toString();

    const extractRegex = /@([A-Za-z0-9._+-]*(?:@[A-Za-z0-9.-]*)?)$/;
    const match = extractRegex.exec(textBeforeCaret);
    if (!match) return;

    const deleteCount = match[0].length;
    const mentionStartOffset = textBeforeCaret.length - deleteCount;

    const getDomPositionForTextOffset = (targetOffset) => {
        const walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT);
        let traversed = 0;
        let currentNode = walker.nextNode();
        let lastNode = null;

        while (currentNode) {
            lastNode = currentNode;
            const currentLength = currentNode.textContent.length;

            if (traversed + currentLength >= targetOffset) {
                return {
                    node: currentNode,
                    offset: targetOffset - traversed,
                };
            }
            traversed += currentLength;
            currentNode = walker.nextNode();
        }

        if (lastNode) {
            return {
                node: lastNode,
                offset: lastNode.textContent.length,
            };
        }

        return {
            node: block,
            offset: 0,
        };
    };

    const startPos = getDomPositionForTextOffset(mentionStartOffset);

    const replaceRange = document.createRange();
    replaceRange.setStart(startPos.node, startPos.offset);
    replaceRange.setEnd(caretRange.endContainer, caretRange.endOffset);
    replaceRange.deleteContents();

    const anchor = document.createElement("a");
    anchor.dataset.ikMentionRef = userMail;
    anchor.setAttribute("href", `mailto:${userMail}`);
    anchor.setAttribute("contenteditable", "false");
    anchor.textContent = `@${userName}`;

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
