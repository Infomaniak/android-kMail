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

import io.realm.kotlin.types.RealmObject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class UserInfos : RealmObject {
    var name: String = ""
    @SerialName("firstname")
    var firstName: String = ""
    var login: String = ""
    var email: String = ""
    @SerialName("hosting_url")
    var hostingUrl: String = ""
    @SerialName("manager_url")
    var managerUrl: String = ""
    @SerialName("workspace_url")
    var workspaceUrl: String = ""
    @SerialName("drive_url")
    var driveUrl: String = ""
    @SerialName("workspace_only")
    var workspaceOnly: Boolean = false
    @SerialName("double_auth")
    var doubleAuth: Boolean = false
    @SerialName("old_user")
    var oldUser: Boolean = false
    var locale: String = ""
    var country: String = ""
    @SerialName("is_restricted")
    var isRestricted: Boolean = false
    @SerialName("from_webmail1")
    var fromWebmail1: Boolean = false
}
