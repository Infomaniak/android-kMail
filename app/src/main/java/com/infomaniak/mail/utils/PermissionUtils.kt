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
package com.infomaniak.mail.utils

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.mail.data.LocalSettings

class PermissionUtils {

    private var activity: FragmentActivity

    private lateinit var mainForActivityResult: ActivityResultLauncher<Array<String>>

    constructor(activity: FragmentActivity) {
        this.activity = activity
    }

    constructor(fragment: Fragment) {
        this.activity = fragment.requireActivity()
    }

    fun registerMainPermissions(onPermissionResult: ((permissions: Map<String, Boolean>) -> Unit)? = null) {
        mainForActivityResult =
            activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { authorizedPermissions ->
                onPermissionResult?.invoke(authorizedPermissions)
            }
    }

    fun requestMainPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mainForActivityResult.launch(getTiramisuPermissions())
        } else {
            if (!activity.hasPermissions(mainPermissions)) mainForActivityResult.launch(mainPermissions)
        }
    }

    /**
     * If the user has manually disabled notifications permissions, stop requesting it.
     * Manually disabled means the permission was granted at one point, but is no more.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun getTiramisuPermissions(): Array<String> {
        return if (activity.hasNotificationPermission() || LocalSettings.getInstance(activity).hasEnabledNotifications) {
            mainPermissions.filterNot { permission -> permission == Manifest.permission.POST_NOTIFICATIONS }.toTypedArray()
        } else {
            mainPermissions
        }
    }

    companion object {
        val mainPermissions = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_CONTACTS
            )
            else -> arrayOf(Manifest.permission.READ_CONTACTS)
        }

        fun Activity.hasNotificationPermission(): Boolean {
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    hasPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }
}
