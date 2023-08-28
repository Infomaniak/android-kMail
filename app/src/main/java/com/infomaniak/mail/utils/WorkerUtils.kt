/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery

object WorkerUtils {

    fun flushWorkersBefore(context: Context, lifecycleOwner: LifecycleOwner, block: () -> Unit) {
        WorkManager.getInstance(context).pruneWork().state.observe(lifecycleOwner) {
            if (it !is Operation.State.IN_PROGRESS) block()
        }
    }

    fun getWorkInfoLiveData(
        tag: String,
        workManager: WorkManager,
        states: List<WorkInfo.State>,
    ): LiveData<MutableList<WorkInfo>> {
        val workQuery = WorkQuery.Builder.fromTags(listOf(tag)).addStates(states).build()
        return workManager.getWorkInfosLiveData(workQuery)
    }
}
