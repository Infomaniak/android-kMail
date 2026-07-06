// We get the parent block to get the correct range to look for the @
function getBlockParent(node) {
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

function getTextBeforeCaret(stripMentions = true) {
    const selection = globalThis.getSelection();
    if (!selection || selection.rangeCount === 0) return "";

    const range = selection.getRangeAt(0);
    if (!range.collapsed) return "";

    const block = getBlockParent(range.startContainer);
    const preRange = range.cloneRange();

   if (block?.nodeType === Node.ELEMENT_NODE) {
       preRange.setStart(block, 0);
   } else {
       preRange.selectNodeContents(getEditor());
   }

    preRange.setEnd(range.endContainer, range.endOffset);

    const fragment = preRange.cloneContents();
    if (stripMentions) {
        // Replace all the existing mentions with a space to ignore them when extracting the query
        const mentions = fragment.querySelectorAll("a[data-ik-mention-ref]");
        mentions.forEach(mention => mention.replaceWith(" "));
    }

    return fragment.textContent;
};
