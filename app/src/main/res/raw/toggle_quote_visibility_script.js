(function() {
    function changeQuoteVisibility() {
        const button = document.getElementById('quote-toggle-btn');
        if (!button) return;

        // Hide quotes initially and show toggle button
        const quoteElements = document.querySelectorAll('.ik_mail_quote');

        if (quoteElements.style != 'block') {
            quoteElements.forEach(el => {
                el.style.display = 'none';
            });
            button.style.display = 'block';
        }

        button.onclick = function(e) {
           e.preventDefault();
           quoteElements.forEach(el => {
                el.style.display = 'block';
                // Force reload images inside quotes
                el.querySelectorAll('img').forEach(img => {
                    if (img.src.startsWith('cid:')) {
                    // Store original CID, then reload
                    const cid = img.src;
                    img.src = '';

                    setTimeout(() => img.src = cid, 0);
                    }
                });
           });
           button.style.display = 'none';
        };
    }

    changeQuoteVisibility();
})();
