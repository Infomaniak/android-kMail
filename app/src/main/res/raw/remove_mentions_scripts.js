function removeMentionScripts(...ids) {
    ids.forEach((id) => {
        document.getElementById(id)?.remove();
    });
}
