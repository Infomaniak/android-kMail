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
function observeMentionClick() {
    if (document.kmailMentionClickObserved) return;
        document.kmailMentionClickObserved = true;

    document.addEventListener('click', (event) => {
        const clickedNode = event.target;
        if (!(clickedNode instanceof Element)) return;

        const mention = clickedNode.closest('a[data-ik-mention-ref]');
        if (!mention) return;

        const mentionHandler = globalThis.kmail?.openMentionContact;
        if (typeof mentionHandler !== 'function') return;

        event.preventDefault();
        event.stopPropagation();

        const email = mention.getAttribute('data-ik-mention-ref') || '';
        if (!email) return;

        let name = (mention.textContent || '').trim();
        if (name.startsWith('@')) {
            name = name.substring(1).trim();
        }

        mentionHandler.call(globalThis.kmail, email, name || null);
    }, true);
}

observeMentionClick();
