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

const extractRemovedRefs = (node, refsCollection) => {
    if (node.nodeType === Node.ELEMENT_NODE && node.dataset.ikMentionRef) {
        refsCollection.push(node.dataset.ikMentionRef);
    }

    if (node.nodeType === Node.ELEMENT_NODE) {
        const mentions = node.querySelectorAll("[data-ik-mention-ref]");
        mentions.forEach((mention) => {
            refsCollection.push(mention.dataset.ikMentionRef);
        });
    }
};

const handleMutationRecords = (mutationRecords) => {
    const removedRefs = mutationRecords
        .flatMap(({ removedNodes }) => [...removedNodes])
        .filter((node) => node.nodeType === Node.ELEMENT_NODE)
        .reduce((refs, node) => {
            extractRemovedRefs(node, refs);
            return refs;
        }, []);

    if (removedRefs.length > 0) {
        onMentionsDeleted(JSON.stringify(removedRefs));
    }
};

const setupObserver = () => {
    const rootElement = document.body;
    if (!rootElement) return;

    const mutationObserver = new MutationObserver(handleMutationRecords);
    mutationObserver.observe(rootElement, { childList: true, subtree: true });
};

setupObserver();
