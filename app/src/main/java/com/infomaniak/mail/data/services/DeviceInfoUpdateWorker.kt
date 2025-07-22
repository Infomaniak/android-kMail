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
package com.infomaniak.mail.data.services

import android.content.Context
import androidx.work.WorkerParameters
import com.infomaniak.core.login.crossapp.internal.deviceinfo.AbstractDeviceInfoUpdateWorker
import com.infomaniak.mail.utils.AccountUtils
import okhttp3.OkHttpClient

class DeviceInfoUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : AbstractDeviceInfoUpdateWorker(appContext, params) {

    override suspend fun getConnectedHttpClient(userId: Int): OkHttpClient {
        return AccountUtils.getHttpClient(userId = userId)
    }
}
