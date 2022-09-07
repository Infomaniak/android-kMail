/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.user

import com.infomaniak.lib.core.utils.Utils.enumValueOfOrNull
import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
class UserPreferences : RealmObject {

    //region API data
    @SerialName("thread_mode")
    private var threadMode: String? = null
    @SerialName("read_pos")
    var readPosition: String = ""
    var density: String = ""
    // TODO: Add other preferences.
    //endregion

    //region Local data (Transient)
    @Transient
    private var intelligentMode: String = IntelligentMode.DISABLED.name
    //endregion

    fun getThreadMode(): ThreadMode? = enumValueOfOrNull<ThreadMode>(threadMode)

    fun getIntelligentMode(): IntelligentMode? = enumValueOfOrNull<IntelligentMode>(intelligentMode)

    enum class ThreadMode {
        MESSAGES,
        THREADS,
    }

    enum class IntelligentMode {
        ENABLED,
        DISABLED,
    }
}
