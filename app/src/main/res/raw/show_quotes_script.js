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
    var style = document.getElementById("quote-visibility")

    if (style) {
        style.remove()

        // Handle CID image reload when showing
        document.querySelectorAll('.ik_mail_quote img, .forwardContentMessage img').forEach(img => {
         if (img.src.startsWith('cid:')) {
            // Store original CID, then reload
             const cid = img.src;
             img.src = '';
             setTimeout(() => img.src = cid, 0);
         }
       });
    }
})();
