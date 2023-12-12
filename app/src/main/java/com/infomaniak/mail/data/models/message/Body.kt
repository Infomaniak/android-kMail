/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.message

import com.infomaniak.mail.data.api.FlatteningSubBodiesSerializer
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.Utils.TEXT_PLAIN
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Serializable
class Body : EmbeddedRealmObject {

    //region Remote data
    var value: String = ""
    var type: String = ""

    @Serializable(FlatteningSubBodiesSerializer::class)
    @SerialName("subBody")
    var subBodies: RealmList<SubBody> = realmListOf()
    //endregion

    @Transient
    @Ignore
    val html: Document
        get() {
            var mainBody = if (type == TEXT_PLAIN) wrapPlainTextInsideHtml(value) else Jsoup.parse(value)
            // TODO : Should I modify in place instead of sending the value back for no reason
            mainBody = mergeSplitBodyAndSubBodies(mainBody, subBodies, "")

            // TODO : Sanitize

            return mainBody
        }


    companion object {
        private fun wrapPlainTextInsideHtml(textPlain: String): Document {
            return Document("").apply {
                body().appendElement("pre").text(textPlain).attr("style", "word-wrap: break-word; white-space: pre-wrap;")
            }
        }

        private fun mergeSplitBodyAndSubBodies(mainBody: Document, subBodies: List<SubBody>, messageUid: String): Document {
            return mainBody.apply {
                body().appendSubBodies(subBodies, messageUid)
            }
        }

        private fun Element.appendSubBodies(subBodies: List<SubBody>, messageUid: String) {
            if (subBodies.isEmpty()) return
            SentryDebug.sendSubBodiesTrigger(messageUid)

            subBodies.forEach { subBody ->
                subBody.bodyValue?.let { subBodyHtml ->
                    appendElement("br")
                    appendElement("blockquote").append(subBodyHtml)
                }
            }
        }
    }
}
