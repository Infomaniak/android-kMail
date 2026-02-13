(function() {
    function changeQuoteVisibility() {
        const button = document.getElementById('quote-toggle-btn');
        if (!button) return;

        // Attach click handler to button
        button.onclick = function(e) {
           e.preventDefault();
           const quoteElements = document.querySelectorAll('[id="ik-quotes"]');
           quoteElements.forEach(el => {
              el.style.display = 'block';
           });
           button.style.display = 'none';
        };
    }

    changeQuoteVisibility();
})();
