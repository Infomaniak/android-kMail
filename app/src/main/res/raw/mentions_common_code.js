const MENTION_ANCHOR_SELECTOR = "a[data-ik-mention-ref]";

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

const defineSelectionRange = (block, range, editor, stripMentions) => {
    const selectionRange = range.cloneRange();

    // Set start of range to the start of the parent block if there is, and if not to the start of the editor
    if (block?.nodeType === Node.ELEMENT_NODE) {
        selectionRange.setStart(block, 0);
    } else {
        selectionRange.selectNodeContents(editor);
    }

    // Get the last element of the range, if it is a text context get the parent element
    const endElement = range.endContainer.nodeType === Node.ELEMENT_NODE
            ? range.endContainer
            : range.endContainer.parentElement;

    // Find if the closest element to endElement that is a mentionAnchor, returns itself if the endElement it's a mention anchor
    const activeMention = endElement?.closest?.(MENTION_ANCHOR_SELECTOR);

    // In the case we want to strip mentions and there is an active mention, we make sure the mention is included in the range.
    if (stripMentions && activeMention && block.contains(activeMention)) {
        selectionRange.setEndAfter(activeMention);
    } else {
        selectionRange.setEnd(range.endContainer, range.endOffset);
    }

    return selectionRange;
}

const stripMentionsFromSelection = (selection) => {
    selection.querySelectorAll(MENTION_ANCHOR_SELECTOR).forEach(mention => mention.replaceWith(" "));
}

function getTextBeforeCaret (stripMentions = true) {
    const selection = globalThis.getSelection();
    if (!selection || selection.rangeCount === 0) return "";

    const range = selection.getRangeAt(0);
    if (!range.collapsed) return "";

    const editor = getEditor();
    if (!editor.contains(range.startContainer)) return "";

    const block = getBlockParent(range.startContainer);
    const selectionRange = defineSelectionRange(block, range, editor, stripMentions);
    const clonedSelection = selectionRange.cloneContents();

    if (stripMentions) {
        // Replace all the existing mentions with a space to ignore them when extracting the query
        stripMentionsFromSelection(clonedSelection);
    }

    return clonedSelection.textContent;
}
