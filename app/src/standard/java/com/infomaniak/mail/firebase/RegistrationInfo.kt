/*
 * Infomaniak ikMail - Android
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

class RegistrationInfo private constructor(
    private val token: String,
    private val name: String,
    private val os: String = OS_NAME,
    private val model: String = DEVICE_MODEL,
) {

    constructor(context: Context, token: String) : this(token = token, name = getDeviceName(context.contentResolver))

    private companion object {
        const val OS_NAME = "android"
        val DEVICE_MODEL: String = android.os.Build.MODEL

        fun getDeviceName(contentResolver: ContentResolver): String {
            return Settings.Global.getString(contentResolver, "device_name").ifBlank { DEVICE_MODEL }
        }
    }
}
