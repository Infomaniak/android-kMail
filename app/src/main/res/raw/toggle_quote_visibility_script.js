(function() {
    function changeQuoteVisibility() {
        const button = document.getElementById('quote-toggle-btn');
        console.log("BUTTON", button)
        if (!button) return;

        // Hide quotes initially and show toggle button
        const quoteElements = document.querySelectorAll('.ik_mail_quote');
        console.log("QUOTES ELEMENTS", quotesElements)
        if (quoteElements.style != 'block') {
            quoteElements.forEach(el => {
                el.style.display = 'none';
            });
            button.style.display = 'block';
        }

        // Attach click handler to button
        button.onclick = function(e) {
           e.preventDefault();
           quoteElements.forEach(el => {
              el.style.display = 'block';
           });
           button.style.display = 'none';
        };
    }

    changeQuoteVisibility();
})();
