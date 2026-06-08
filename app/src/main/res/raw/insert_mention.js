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

/**
 * Replace the incomplete mention (@query) with a mention anchor.
 * Example output: <a data-ik-tag href="mailto:user@ik.me">User name</a>
 */
function insertMention(userMail, userName) {
    if (!userMail || !userName) return;

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const caretRange = selection.getRangeAt(0);
    if (!caretRange.collapsed) return;

    const mentionQueryRegex = /(?:^|\s)@([A-Za-z0-9._+-]*(?:@[A-Za-z0-9.-]*)?)$/;
    const editor = getEditor();

    const preRange = caretRange.cloneRange();
    preRange.selectNodeContents(editor);
    preRange.setEnd(caretRange.endContainer, caretRange.endOffset);
    const textBeforeCaret = preRange.toString();

    const mentionMatch = textBeforeCaret.match(mentionQueryRegex);
    if (!mentionMatch) return;

    const deleteCount = mentionMatch[1].length + 1; // +1 for '@'
    const mentionStartOffset = textBeforeCaret.length - deleteCount;

    const getDomPositionForTextOffset = (targetOffset) => {
        const walker = document.createTreeWalker(editor, NodeFilter.SHOW_TEXT);
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
            node: editor,
            offset: editor.childNodes.length,
        };
    };

    const startPos = getDomPositionForTextOffset(mentionStartOffset);

    const replaceRange = document.createRange();
    replaceRange.setStart(startPos.node, startPos.offset);
    replaceRange.setEnd(caretRange.endContainer, caretRange.endOffset);
    replaceRange.deleteContents();

    const anchor = document.createElement("a");
    anchor.setAttribute("data-ik-tag", "");
    anchor.setAttribute("href", `mailto:${userMail}`);
    anchor.setAttribute("contenteditable", "false");
    anchor.textContent = userName;

    const trailingSpace = document.createTextNode(" ");
    replaceRange.insertNode(trailingSpace);
    replaceRange.insertNode(anchor);

    const newCaretRange = document.createRange();
    newCaretRange.setStartAfter(trailingSpace);
    newCaretRange.collapse(true);

    selection.removeAllRanges();
    selection.addRange(newCaretRange);
}

