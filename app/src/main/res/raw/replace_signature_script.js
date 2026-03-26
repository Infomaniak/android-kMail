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
    const signatureElement = document.getElementById('%s');
    const newSigHtml = %s;
    if (signatureElement) {
        if (newSigHtml === "") {
            signatureElement.remove();
        } else {
            signatureElement.outerHTML = newSigHtml;
        }
    } else if (newSigHtml !== "") {
        const quotes = document.querySelector('%s');
        if (quotes) {
            quotes.insertAdjacentHTML('beforebegin', newSigHtml);
        } else {
            document.body.insertAdjacentHTML('beforeend', newSigHtml);
        }
    }
})()
