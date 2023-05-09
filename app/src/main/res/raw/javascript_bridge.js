function reportOverScroll(clientWidth, scrollWidth, messageUid) {
    window.kmail.reportOverScroll(clientWidth, scrollWidth, messageUid);
}

function reportError(error, messageUid) {
    window.kmail.reportError(error.name, error.message, messageUid);
}
