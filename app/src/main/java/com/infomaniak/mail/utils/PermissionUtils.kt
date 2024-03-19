/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.infomaniak.lib.core.utils.hasPermissions
import com.infomaniak.mail.data.LocalSettings
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class PermissionUtils @Inject constructor(private val activity: FragmentActivity, private val localSettings: LocalSettings) {

    private var mainForActivityResult: ActivityResultLauncher<Array<String>>? = null
    private var contactsPermissionForActivityResult: ActivityResultLauncher<String>? = null
    private var notificationsPermissionForActivityResult: ActivityResultLauncher<String>? = null
    private var storageForActivityResult: ActivityResultLauncher<String>? = null

    val hasDownloadManagerPermission
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || activity.hasPermissions(arrayOf(STORAGE_PERMISSION))

    fun registerMainPermissions(onPermissionResult: ((permissions: Map<String, Boolean>) -> Unit)? = null) {
        mainForActivityResult = activity.registerForActivityResult(RequestMultiplePermissions()) { authorizedPermissions ->
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
        if (!activity.hasPermissions(mainPermissions)) mainForActivityResult?.launch(mainPermissions)
    }

    //region read contacts permissions
    private var contactsCallback: ((Boolean) -> Unit)? = null

    fun registerReadContactsPermission(fragment: Fragment) {
        contactsPermissionForActivityResult = fragment.registerForActivityResult(RequestPermission()) { hasPermission ->
            contactsCallback?.invoke(hasPermission)
        }
    }

    fun requestReadContactsPermission(contactsCallback: (Boolean) -> Unit) {
        this.contactsCallback = contactsCallback
        contactsPermissionForActivityResult?.launch(READ_CONTACTS_PERMISSION)
    }
    //endregion

    //region notifications permissions
    private var notificationsCallback: ((Boolean) -> Unit)? = null

    /**
     * Register notifications permission only for Android API above or equal 33 and if user never manually disabled it.
     * Manually disabled means the permission was granted at one point, but is no more.
     */
    fun registerNotificationsPermissionIfNeeded(fragment: Fragment) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !localSettings.hasAlreadyEnabledNotifications) {
            notificationsPermissionForActivityResult = fragment.registerForActivityResult(RequestPermission()) { hasPermission ->
                if (hasPermission) localSettings.hasAlreadyEnabledNotifications = true
                notificationsCallback?.invoke(hasPermission)
            }
        }
    }

    /**
     * Request notifications permission only for Android API above or equal 33 and if user never manually disabled it.
     * Manually disabled means the permission was granted at one point, but is no more.
     */
    fun requestNotificationsPermissionIfNeeded(notificationsCallback: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !localSettings.hasAlreadyEnabledNotifications) {
            this.notificationsCallback = notificationsCallback
            notificationsPermissionForActivityResult?.launch(POST_NOTIFICATIONS_PERMISSION)
        }
    }
    //endregion

    //region DownloadManager permissions
    private var downloadCallback: (() -> Unit)? = null

    /**
     * Register storage permission only for Android API below 29.
     */
    fun registerDownloadManagerPermission(fragment: Fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            storageForActivityResult = fragment.registerForActivityResult(RequestPermission()) { hasPermission ->
                if (hasPermission) downloadCallback?.invoke()
            }
        }
    }

    /**
     * Request storage permission only for Android API below 29.
     */
    fun requestDownloadManagerPermission(downloadCallback: () -> Unit) {
        this.downloadCallback = downloadCallback
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) storageForActivityResult?.launch(STORAGE_PERMISSION)
    }
    //endregion

    companion object {

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private const val POST_NOTIFICATIONS_PERMISSION = Manifest.permission.POST_NOTIFICATIONS
        const val READ_CONTACTS_PERMISSION = Manifest.permission.READ_CONTACTS
        @get:DeprecatedSinceApi(Build.VERSION_CODES.Q, "Only used for DownloadManager below API 29")
        private const val STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE

        /**
         * The field 'mustRequireNotification' is for when the user has manually disabled notifications permissions,
         * so we need to stop requesting it.
         * Manually disabled means the permission was granted at one point, but is no more.
         */
        fun getMainPermissions(mustRequireNotification: Boolean): Array<String> {
            val mainPermissions = mutableListOf(READ_CONTACTS_PERMISSION)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mustRequireNotification) {
                mainPermissions.add(POST_NOTIFICATIONS_PERMISSION)
            }

            return mainPermissions.toTypedArray()
        }
    }
}
