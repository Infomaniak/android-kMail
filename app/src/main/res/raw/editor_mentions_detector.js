let lastSentValue = null;
let mentionRestartOffset = null;
const validMentionCharsRegex = /^[A-Za-z0-9._+-]*(?:@[A-Za-z0-9.-]*)?$/;
const zeroWidthCharsRegex = /[\u200B-\u200D\uFEFF]/g;
const mentionQueryRegex = /(?:^|\s)@([A-Za-z0-9._+-]*(?:@[A-Za-z0-9.-]*)?)$/;

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
    const normalizedText = textBeforeCaret.replace(zeroWidthCharsRegex, "");

    if (mentionRestartOffset != null) {
        const mentionStartIndex = normalizedText.lastIndexOf("@");
        if (mentionStartIndex < mentionRestartOffset) return null;

        const queryAfterRestart = normalizedText.slice(mentionStartIndex + 1);
        return validMentionCharsRegex.test(queryAfterRestart) ? queryAfterRestart : null;
    }

    const match = normalizedText.match(mentionQueryRegex);
    if (!match) return null;

    const query = match[1];
    return validMentionCharsRegex.test(query) ? query : null;
}

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
    lastSentValue = null;
    onMentionQueryChanged("");
}

const notifyIfChanged = () => {
    if (isInsideMentionLink()) {
      resetMentionQuery();
      return;
    }

    const textBeforeCaret = getTextBeforeCaret();
    const query = extractMentionQuery(textBeforeCaret);

    if (query != null && mentionRestartOffset != null) mentionRestartOffset = null;

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
    document.addEventListener("keydown", (event) => {
        if (event.key === "Enter") {
            mentionRestartOffset = getTextBeforeCaret().replace(zeroWidthCharsRegex, "").length;
            resetMentionQuery();
        }
    });
};

setupMentionDetection();
