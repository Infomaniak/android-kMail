/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.firebase

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

class RegistrationInfos private constructor(
    val token: String,
    val name: String,
    val os: String = "android",
    val model: String = android.os.Build.MODEL,
) {

    constructor(context: Context, token: String) : this(token = token, name = getDeviceName(context.contentResolver))

    companion object {
        private fun getDeviceName(contentResolver: ContentResolver): String {
            return Settings.Global.getString(contentResolver, "device_name")
        }
    }
}