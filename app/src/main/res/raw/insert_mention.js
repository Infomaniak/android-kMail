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

    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return;

    const caretRange = selection.getRangeAt(0);
    if (!caretRange.collapsed) return;

    const editor = getEditor();

    const getBlockParent = (node) => {
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

    const block = getBlockParent(caretRange.startContainer);

    const preRange = caretRange.cloneRange();
    try {
        preRange.setStart(block, 0);
    } catch (e) {
        preRange.selectNodeContents(editor);
    }
    preRange.setEnd(caretRange.endContainer, caretRange.endOffset);

    const textBeforeCaret = preRange.toString();

    const extractRegex = /@([^@\s]*)$/;
    const match = textBeforeCaret.match(extractRegex);
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
    anchor.setAttribute("data-ik-mention-ref", userMail);
    anchor.setAttribute("href", `mailto:${userMail}`);
    anchor.setAttribute("contenteditable", "false");
    anchor.setAttribute(
        "style",
        "padding: 0 4px; border-radius: 100px; color: var(--mail-content-mention-text-color, #333); " +
            "background-color: var(--mail-content-mention-background-color, #f1f1f1); " +
            "font-weight: var(--mail-content-mention-font-weight, inherit);"
    );
    anchor.textContent = `@${userName}`;

    const trailingSpace = document.createTextNode(" ");
    replaceRange.insertNode(trailingSpace);
    replaceRange.insertNode(anchor);

    // We replace the cursor after the space of the mention
    const newCaretRange = document.createRange();
    newCaretRange.setStartAfter(trailingSpace);
    newCaretRange.collapse(true);

    selection.removeAllRanges();
    selection.addRange(newCaretRange);
}

function observeEditorMentionClicks() {
    if (document.kmailEditorMentionClickObserved) return;
    document.kmailEditorMentionClickObserved = true;

    const closestMention = (node) =>
        node instanceof Element ? node.closest("a[data-ik-mention-ref]") : null;

    // Tapping a mention must not blur the editor, otherwise the Android keyboard (IME) closes.
    // Preventing the default on the pointer-down event stops the WebView from moving the
    // focus/caret onto the non-editable mention, keeping the editor focused and the IME open.
    const keepEditorFocused = (event) => {
        if (closestMention(event.target)) event.preventDefault();
    };
    document.addEventListener("mousedown", keepEditorFocused, true);
    document.addEventListener("touchstart", keepEditorFocused, { capture: true, passive: false });

    // Never navigate to the mailto link when a mention is tapped.
    document.addEventListener(
        "click",
        (event) => {
            if (closestMention(event.target)) {
                event.preventDefault();
                event.stopPropagation();
            }
        },
        true
    );
}

observeEditorMentionClicks();
