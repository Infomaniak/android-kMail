/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import com.infomaniak.mail.utils.MessageBodyUtils.EDITOR_LOCAL_SIGNATURE_ID
import com.infomaniak.mail.utils.MessageBodyUtils.INFOMANIAK_SIGNATURE_HTML_CLASS_NAME
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureUtils @Inject constructor() {
    fun encapsulateSignatureContentWithInfomaniakClass(signatureContent: String): String {
        return """<div id="$EDITOR_LOCAL_SIGNATURE_ID" class="$INFOMANIAK_SIGNATURE_HTML_CLASS_NAME">$signatureContent</div>"""
    }
}
