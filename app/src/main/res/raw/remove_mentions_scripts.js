function removeMentionScripts() {
    const ids = [
        "mention_observer_script",
        "insert_mention_script",
        "mention_deletion_observer_script",
    ];

    ids.forEach((id) => {
        document.getElementById(id)?.remove();
    });
}

removeMentionScripts();
