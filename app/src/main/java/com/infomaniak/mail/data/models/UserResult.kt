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
package com.infomaniak.mail.data.models

import com.google.gson.annotations.SerializedName

data class UserResult(
    val infos: UserInfos,
    val preferences: UserPreferences
) {
    data class UserInfos(
        val name: String,
        @SerializedName("firstname")
        val firstName: String,
        val login: String,
        val email: String,
        @SerializedName("avatar_url")
        val avatarUrl: String,
        @SerializedName("hosting_url")
        val hostingUrl: String,
        @SerializedName("manager_url")
        val managerUrl: String,
        @SerializedName("workspace_url")
        val workspaceUrl: String,
        @SerializedName("drive_url")
        val driveUrl: String,
        @SerializedName("workspace_only")
        val workspaceOnly: Boolean,
        @SerializedName("double_auth")
        val doubleAuth: Boolean,
        @SerializedName("old_user")
        val oldUser: Boolean,
        val locale: String,
        val country: String,
        @SerializedName("is_restricted")
        val isRestricted: Boolean,
        @SerializedName("from_webmail1")
        val fromWebmail1: Boolean,
    )

    data class UserPreferences(
        @SerializedName("thread_mode")
        val threadMode: ThreadMode,
        @SerializedName("read_pos")
        val readPosition: String,
        val density: String,
        // TODO other preferences
    ) {
        enum class ThreadMode {
            MESSAGES,
            THREADS,
        }
    }
}