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
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.annotations.Ignore

class MailboxLocalValues : EmbeddedRealmObject {
    var userId: Int = AppSettings.DEFAULT_ID
    var quotas: Quotas? = null
    var unreadCountLocal: Int = 0
    var permissions: MailboxPermissions? = null
    var signatures = realmListOf<Signature>()
    private var _featureFlags = realmSetOf<String>()
    var externalMailFlagEnabled: Boolean = false
    var trustedDomains = realmListOf<String>()

    //region UI data (Transient & Ignore)
    @Ignore
    val featureFlags = FeatureFlags()
    //endregion

    inner class FeatureFlags {

        fun contains(featureFlag: FeatureFlag): Boolean = _featureFlags.contains(featureFlag.apiName)

        fun setFeatureFlags(featureFlags: List<String>) = with(_featureFlags) {
            clear()
            addAll(featureFlags)
        }
    }
}
