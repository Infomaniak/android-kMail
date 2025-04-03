/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.sync.discovery

import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class SyncDiscoveryManager @Inject constructor(
    private val activity: FragmentActivity,
) : DefaultLifecycleObserver {

    private val syncDiscoveryViewModel: SyncDiscoveryViewModel by activity.viewModels()

    private var showSyncDiscovery: (() -> Unit)? = null

    fun init(showSyncDiscovery: () -> Unit) {
        activity.lifecycle.addObserver(observer = this)
        this.showSyncDiscovery = showSyncDiscovery
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        syncDiscoveryViewModel.canShowSyncDiscovery.observe(owner) { canShowSyncDiscovery ->
            if (canShowSyncDiscovery) showSyncDiscovery?.invoke()
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        syncDiscoveryViewModel.decrementAppLaunches()
    }
}
