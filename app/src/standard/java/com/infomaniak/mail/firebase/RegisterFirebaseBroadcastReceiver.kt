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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.IRegisterFirebaseBroadcastReceiver

class RegisterFirebaseBroadcastReceiver : DefaultLifecycleObserver, IRegisterFirebaseBroadcastReceiver {

    private lateinit var localBroadcastManager: LocalBroadcastManager
    private var mainViewModel: MainViewModel? = null

    private val firebaseBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (mainViewModel?.currentFolder?.value?.role == FolderRole.INBOX) {
                mainViewModel?.forceRefreshThreads(showSwipeRefreshLayout = false)
            }
        }
    }

    override fun initFirebaseBroadcastReceiver(activity: FragmentActivity, mainViewModel: MainViewModel) {
        this.mainViewModel = mainViewModel
        localBroadcastManager = LocalBroadcastManager.getInstance(activity)
        activity.lifecycle.addObserver(this)
    }

    override fun onResume(owner: LifecycleOwner) {
        val intentFilter = IntentFilter(KMailFirebaseMessagingService.ACTION_MESSAGE_RECEIVED)
        localBroadcastManager.registerReceiver(firebaseBroadcastReceiver, intentFilter)
    }

    override fun onPause(owner: LifecycleOwner) {
        localBroadcastManager.unregisterReceiver(firebaseBroadcastReceiver)
    }
}
