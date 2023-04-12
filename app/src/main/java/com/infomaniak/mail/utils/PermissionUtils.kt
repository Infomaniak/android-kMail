/*
 * Infomaniak kMail - Android
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
package com.infomaniak.mail.utils

import android.Manifest
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.mail.data.LocalSettings

class PermissionUtils {

    private var activity: FragmentActivity
    private var localSettings: LocalSettings

    private lateinit var mainForActivityResult: ActivityResultLauncher<Array<String>>

    constructor(activity: FragmentActivity) {
        this.activity = activity
        localSettings = LocalSettings.getInstance(activity)
    }

    constructor(fragment: Fragment) : this(fragment.requireActivity())

    fun registerMainPermissions(onPermissionResult: ((permissions: Map<String, Boolean>) -> Unit)? = null) {
        mainForActivityResult =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { authorizedPermissions ->
                onPermissionResult?.invoke(authorizedPermissions)
                updateNotificationPermissionSetting()
            }
    }

    private fun updateNotificationPermissionSetting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity.hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        ) {
            localSettings.hasAlreadyEnabledNotifications = true
        }
    }

    fun requestMainPermissionsIfNeeded() {
        updateNotificationPermissionSetting()

        val mainPermissions = getMainPermissions(mustRequireNotification = !localSettings.hasAlreadyEnabledNotifications)
        if (!activity.hasPermissions(mainPermissions)) mainForActivityResult.launch(mainPermissions)
    }

    private companion object {

        /**
         * If the user has manually disabled notifications permissions, stop requesting it.
         * Manually disabled means the permission was granted at one point, but is no more.
         */
        fun getMainPermissions(mustRequireNotification: Boolean): Array<String> {
            val mainPermissions = mutableListOf(Manifest.permission.READ_CONTACTS)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mustRequireNotification) {
                mainPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            return mainPermissions.toTypedArray()
        }
    }
}
