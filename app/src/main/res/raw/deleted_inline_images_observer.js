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

    function setupObserver() {
        var rootElement = document;

        mutationObserver = new MutationObserver(function(mutationRecords) {
           var removedCids = [];

           mutationRecords.forEach(function (mutation) {
             for (var removedNode, nodeIndex = 0; removedNode = mutation.removedNodes[nodeIndex]; ++nodeIndex) {
                if (removedNode.nodeType == Node.ELEMENT_NODE) {
                   if ("img" == removedNode.tagName.toLowerCase()) {
                       removedCids.push(removedNode.src);
                   } else {
                       var childImages = removedNode.getElementsByTagName("img");
                       for (var childIndex = 0; childIndex < childImages.length; childIndex++) {
                        removedCids.push(childImages[childIndex].src);
                       }
                   }
                }
             }
           });

           // Notify if images were removed
           if (removedCids.length > 0) {
               onInlineImagesDeleted(JSON.stringify(removedCids));
           }
        });

        mutationObserver.observe(rootElement, {
          childList: true,
          subtree: true,
        })
    }

    setupObserver();
})();
