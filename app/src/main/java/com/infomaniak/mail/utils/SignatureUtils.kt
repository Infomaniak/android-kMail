/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.content.Context
import com.infomaniak.mail.R
import com.infomaniak.mail.data.cache.mailboxContent.SignatureController
import com.infomaniak.mail.data.models.draft.Draft
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SignatureUtils @Inject constructor(appContext: Context) {

    private val signatureMargins by lazy { appContext.readRawResource(R.raw.signature_margins) }

    fun encapsulateSignatureContentWithInfomaniakClass(signatureContent: String): String {
        val verticalMarginsCss = signatureMargins
        val verticalMarginAttributes = extractAttributesFromMarginCss(verticalMarginsCss)
        return """<div class="${MessageBodyUtils.INFOMANIAK_SIGNATURE_HTML_CLASS_NAME}" style="$verticalMarginAttributes">$signatureContent</div>"""
    }

    private fun extractAttributesFromMarginCss(verticalMarginsCss: String): String {
        return Regex("""\{(.*)\}""").find(verticalMarginsCss)!!.groupValues[1]
    }

    fun addMissingSignatureData(draft: Draft, realm: TypedRealm) {
        initSignature(draft, realm, addContent = false)
    }

    fun initSignature(draft: Draft, realm: TypedRealm, addContent: Boolean) = with(draft) {
        val signature = SignatureController.getSignature(realm)

        identityId = signature.id.toString()

        if (addContent && signature.content.isNotEmpty()) {
            body += encapsulateSignatureContentWithInfomaniakClass(signature.content)
        }
    }
}
