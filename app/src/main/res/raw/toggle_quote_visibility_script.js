(function() {
    function changeQuoteVisibility() {
        // Hide quotes initially
        const quoteElements = document.querySelectorAll('[id="ik-quotes"]');
        quoteElements.forEach(el => {
            el.style.display = 'none';
        });

        // Attach click handler to button
        const button = document.getElementById('quote-toggle-btn');

        if (button) {
            button.onclick = function(e) {
                e.preventDefault();
                const quoteElements = document.querySelectorAll('[id="ik-quotes"]');
                quoteElements.forEach(el => {
                    el.style.display = 'block';
                });
                button.style.display = 'none';
            };

//            button.addEventListener('click', function(e) {
//                e.preventDefault();
//                const quoteElements = document.querySelectorAll('[id="ik-quotes"]');
//                quoteElements.forEach(el => {
//                    el.style.display = 'block';
//                });
//                button.style.display = 'none';
//            });
        }
    }

    changeQuoteVisibility();
})();
