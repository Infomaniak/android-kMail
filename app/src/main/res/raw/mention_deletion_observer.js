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

observeMentionDeletion();

function observeMentionDeletion() {
    const extractRemovedRefs = (node, refsCollection) => {
        if (node.nodeType === Node.ELEMENT_NODE && node.hasAttribute("data-ik-mention-ref")) {
            refsCollection.push(node.getAttribute("data-ik-mention-ref"));
        }

        if (node.nodeType === Node.ELEMENT_NODE) {
            const mentions = node.querySelectorAll("[data-ik-mention-ref]");
            mentions.forEach((mention) => {
                refsCollection.push(mention.getAttribute("data-ik-mention-ref"));
            });
        }
    };

    let pendingRefs = new Set();
    let debounceTimer = null;

    const setupObserver = () => {
        const rootElement = document.body;
        if (!rootElement) return;

        const mutationObserver = new MutationObserver((mutationRecords) => {
            const removedRefs = mutationRecords
                .flatMap(({ removedNodes }) => [...removedNodes])
                .filter((node) => node.nodeType === Node.ELEMENT_NODE)
                .reduce((refs, node) => {
                    extractRemovedRefs(node, refs);
                    return refs;
                }, []);

            removedRefs.forEach((ref) => pendingRefs.add(ref));

            clearTimeout(debounceTimer);

            debounceTimer = setTimeout(() => {
                const actuallyRemovedRefs = [...pendingRefs].filter((ref) => {
                    return !document.querySelector(`[data-ik-mention-ref="${ref}"]`);
                });

                pendingRefs.clear();

                if (actuallyRemovedRefs.length > 0) {
                    onMentionsDeleted(JSON.stringify(actuallyRemovedRefs));
                }
            }, 300);
        });

        mutationObserver.observe(rootElement, { childList: true, subtree: true });
    };

    if (document.body) {
        setupObserver();
    } else {
        document.addEventListener("DOMContentLoaded", setupObserver);
    }
}
