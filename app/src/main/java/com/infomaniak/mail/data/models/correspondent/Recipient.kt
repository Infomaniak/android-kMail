/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.correspondent

import android.os.Parcel
import com.infomaniak.core.extensions.customReadBoolean
import com.infomaniak.core.extensions.customWriteBoolean
import com.infomaniak.mail.utils.ExternalUtils.ExternalData
import com.infomaniak.mail.utils.extensions.isEmail
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Parcelize
@Serializable
open class Recipient : EmbeddedRealmObject, Correspondent {

    override var email: String = ""
    override var name: String = ""

    //region Local data (Transient)

    // ------------- !IMPORTANT! -------------
    // Every field that is added in this Transient region should be declared in
    // `initLocalValue()` too to avoid loosing data when updating from the API.
    @Transient
    override var canBeEncrypted: Boolean? = null
    //endregion

    //region UI data (Transient & Ignore)
    // Only indicates how to display the Recipient chip when composing a new Message.
    // `isExternal()` could return true even if this value is false.
    @Transient
    @Ignore
    var isDisplayedAsExternal: Boolean = false
        private set
    @Transient
    @Ignore
    var isManuallyEntered: Boolean = true
    //endregion

    @delegate:Ignore
    override val initials by lazy { computeInitials() }

    override var contactedTimes: Int? = null
    override var other: Boolean = false

    fun initLocalValues(email: String? = null, name: String? = null, canBeEncrypted: Boolean? = null): Recipient {

        email?.let { this.email = it }
        name?.let { this.name = it }
        canBeEncrypted?.let { this.canBeEncrypted = it }

        return this
    }

    fun updateIsDisplayedAsExternal(shouldDisplayAsExternal: Boolean) {
        isDisplayedAsExternal = shouldDisplayAsExternal
    }

    // Computes if the Recipient is external, according to the required conditions.
    // Does not tell anything about how to display the Recipient chip when composing a new Message.
    fun isExternal(externalData: ExternalData): Boolean = with(externalData) {
        val isUnknownContact = email !in emailDictionary
        val isAlias = email in aliases
        val isUntrustedDomain = email.isEmail() && trustedDomains.none(email::endsWith)
        val isMailerDaemon = """mailer-daemon@(?:.+\.)?infomaniak\.ch""".toRegex(RegexOption.IGNORE_CASE).matches(email)

        return@with isUnknownContact && !isAlias && isUntrustedDomain && !isMailerDaemon
    }

    fun quotedDisplayName(): String = "${("$name ").ifBlank { "" }}<$email>"
    fun quotedEmail(): String = "<$email>"

    override fun toString(): String = "($email -> $name)"

    override fun equals(other: Any?) = other === this || (other is Recipient && other.email == email && other.name == name)

    override fun hashCode(): Int = 31 * email.hashCode() + name.hashCode()

    companion object : Parceler<Recipient> {
        override fun create(parcel: Parcel): Recipient {
            val email = parcel.readString()!!
            val name = parcel.readString()!!
            val canBeEncrypted = runCatching { parcel.customReadBoolean() }.getOrNull()

            return Recipient().initLocalValues(email, name, canBeEncrypted)
        }

        override fun Recipient.write(parcel: Parcel, flags: Int) {
            parcel.writeString(email)
            parcel.writeString(name)
            canBeEncrypted?.let(parcel::customWriteBoolean)
        }
    }
}
