/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

(function() {
    var mutationObserver;
    var QUOTE_SELECTORS = '.ik_mail_quote, .forwardContentMessage';

    function setupObserver() {
        // Find all quotes containers to observe
        var quoteElements = document.querySelectorAll(QUOTE_SELECTORS);
        if (quoteElements.length === 0) return ;

        mutationObserver = new MutationObserver(function(mutationRecords) {
           var removedCids = [];

           mutationRecords.forEach(function (mutation) {
             for (var i, nodeIndex = 0; i = mutation.removedNodes[nodeIndex]; ++nodeIndex) {
                if (i.nodeType == Node.ELEMENT_NODE) {
                   if ("img" == i.tagName.toLowerCase()) {
                       removedCids.push(i.src);
                   } else {
                       i = i.getElementsByTagName("img");
                       for (var j = 0; j < i.length; j++) {
                        removedCids.push(i[j].src)
                       }
                   }
                }
             }
           });

           // Notify if images were removed
           if (removedCids.length > 0) {
               onImagesDeletedFromQuotes(JSON.stringify(removedCids));
           }
        });

        // Observe quotes containers
        quoteElements.forEach(function(quoteElement) {
           mutationObserver.observe(quoteElement, {
              childList: true,
              subtree: true,
           })
        })
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', setupObserver)
    } else {
        setupObserver();
    }
})();
