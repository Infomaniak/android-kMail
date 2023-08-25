/*
 * Infomaniak ikMail - Android
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
package com.infomaniak.mail.data.models.correspondent

import android.os.Parcel
import com.infomaniak.mail.utils.MergedContactDictionary
import com.infomaniak.mail.utils.isEmail
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

    //region UI data (Transient & Ignore)
    @Transient
    @Ignore
    // Only indicates how to display the recipient chip when in composing a new message. isExternal could return true event if
    // this value is false
    var displayAsExternal: Boolean = false
        private set
    //endregion

    @delegate:Ignore
    override val initials by lazy { computeInitials() }

    fun initLocalValues(email: String? = null, name: String? = null, displayAsExternal: Boolean = false): Recipient {

        email?.let { this.email = it }
        name?.let { this.name = it }
        initDisplayAsExternal(displayAsExternal)

        return this
    }

    fun initDisplayAsExternal(shouldDisplayAsExternal: Boolean) {
        displayAsExternal = shouldDisplayAsExternal
    }

    // Computes if the recipient is external according to the required conditions. Does not tell anything about how to display the
    // recipient when composing an email
    fun isExternal(emailDictionary: MergedContactDictionary, aliases: List<String>): Boolean {
        val isUnknownContact = email !in emailDictionary
        val isMailerDaemon = """mailer-daemon@(?:.+\.)?infomaniak\.ch""".toRegex(RegexOption.IGNORE_CASE).matches(email)
        val trustedDomains = listOf("@infomaniak.com", "@infomaniak.event", "@swisstransfer.com")
        val isUntrustedDomain = email.isEmail() && trustedDomains.none { email.endsWith(it) }
        val isAlias = email in aliases

        return isUnknownContact && !isMailerDaemon && isUntrustedDomain && !isAlias
    }

    override fun toString(): String = "($email -> $name)"

    override fun equals(other: Any?) = other === this || (other is Recipient && other.email == email && other.name == name)

    override fun hashCode(): Int {
        var result = email.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    companion object : Parceler<Recipient> {
        override fun create(parcel: Parcel): Recipient {
            val email = parcel.readString()!!
            val name = parcel.readString()!!

            return Recipient().initLocalValues(email, name)
        }

        override fun Recipient.write(parcel: Parcel, flags: Int) {
            parcel.writeString(email)
            parcel.writeString(name)
        }
    }
}
