function removeElementsById(...ids) {
    ids.forEach((id) => {
        document.getElementById(id)?.remove();
    });
}
