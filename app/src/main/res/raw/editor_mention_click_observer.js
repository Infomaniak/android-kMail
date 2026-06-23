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

function observeEditorMentionClicks() {
    if (document.kmailEditorMentionClickObserved) return;
    document.kmailEditorMentionClickObserved = true;

    const closestMention = (node) =>
        node instanceof Element ? node.closest("a[data-ik-mention-ref]") : null;

    // Tapping a mention must not blur the editor, otherwise the Android keyboard (IME) closes.
    // Preventing the default on the pointer-down event stops the WebView from moving the
    // focus/caret onto the non-editable mention, keeping the editor focused and the IME open.
    const handleMentionPointerEvent = (event) => {
        const mention = closestMention(event.target);

        if (mention) {
            event.preventDefault();

            // move the caret to the right of the mention
            const selection = window.getSelection();
            const range = document.createRange();
            range.setStartAfter(mention);
            range.collapse(true);
            selection.removeAllRanges();
            selection.addRange(range);
        }
    };
    document.addEventListener("mousedown", handleMentionPointerEvent, true);
    document.addEventListener("touchstart", handleMentionPointerEvent, { capture: true, passive: false });

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
