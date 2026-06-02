const getTextBeforeCaret = () => {
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0) return "";

    const range = selection.getRangeAt(0);
    if (!range.collapsed) return "";

    const preRange = range.cloneRange();
    preRange.selectNodeContents(getEditor());
    preRange.setEnd(range.endContainer, range.endOffset);

    return preRange.toString();
}

const extractMentionQuery = (textBeforeCaret) => {
    const match = textBeforeCaret.match(/(?:^|\s)@([A-Za-z0-9._+-]*)$/);
    return match ? match[1] : null;
}

const notifyIfChanged = () => {
    const textBeforeCaret = getTextBeforeCaret();
    const query = extractMentionQuery(textBeforeCaret);

    if (query === lastSentValue) return;
    lastSentValue = query;

    if (query != null) {
        onMentionQueryChanged(query);
    } else {
        onMentionQueryChanged("");
    }
}

const setupMentionDetection = () => {
    if (globalThis.__kmailMentionDetectionInitialized) return;
    globalThis.__kmailMentionDetectionInitialized = true;

    document.addEventListener("selectionchange", notifyIfChanged);
    document.addEventListener("input", notifyIfChanged);
};

setupMentionDetection();
