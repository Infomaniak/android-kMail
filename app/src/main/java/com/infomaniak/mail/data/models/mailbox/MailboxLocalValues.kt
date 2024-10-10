/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.mailbox

import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.signature.Signature
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore

class MailboxLocalValues : EmbeddedRealmObject {

    var userId: Int = AppSettings.DEFAULT_ID
        private set
    var unreadCountLocal: Int = 0
        private set
    var quotas: Quotas? = null
        private set
    var permissions: MailboxPermissions? = null
        private set
    var signatures = realmListOf<Signature>()
        private set
    var externalMailFlagEnabled: Boolean = false
        private set
    var trustedDomains = realmListOf<String>()
        private set
    private var _featureFlags = realmSetOf<String>()

    @Ignore
    val featureFlags = FeatureFlags()

    fun setUserId(userId: Int, bypassRealmCopy: Boolean = false): MailboxLocalValues {

        fun updateUserId(local: MailboxLocalValues) {
            local.userId = userId
        }

        return if (bypassRealmCopy)
            this.also(::updateUserId)
        else {
            update(::updateUserId)
        }
    }

    fun setUnreadCountLocal(unreadCount: Int): MailboxLocalValues {
        return update { it.unreadCountLocal = unreadCount }
    }

    fun setQuotas(quotas: Quotas?): MailboxLocalValues {
        return update { it.quotas = quotas }
    }

    fun setPermissions(permissions: MailboxPermissions?): MailboxLocalValues {
        return update { it.permissions = permissions }
    }

    fun setSignatures(signatures: List<Signature>): MailboxLocalValues {
        return update {
            it.signatures.apply {
                clear()
                addAll(signatures)
            }
        }
    }

    fun setExternalMailInfo(externalMailInfo: MailboxExternalMailInfo): MailboxLocalValues {
        return update {
            it.externalMailFlagEnabled = externalMailInfo.externalMailFlagEnabled
            it.trustedDomains.apply {
                clear()
                addAll(externalMailInfo.trustedDomains)
            }
        }
    }

    private fun update(onUpdate: (MailboxLocalValues) -> Unit): MailboxLocalValues = copyFromRealm().also(onUpdate)

    inner class FeatureFlags {

        fun contains(featureFlag: FeatureFlag): Boolean = _featureFlags.contains(featureFlag.apiName)

        fun setFeatureFlags(featureFlags: List<String>) = with(_featureFlags) {
            clear()
            addAll(featureFlags)
        }
    }
}
