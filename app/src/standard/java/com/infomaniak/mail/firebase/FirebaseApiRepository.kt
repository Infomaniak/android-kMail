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
package com.infomaniak.mail.firebase

import com.infomaniak.lib.core.BuildConfig.INFOMANIAK_API_V1
import com.infomaniak.lib.core.api.ApiController.ApiMethod.POST
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.mail.data.api.ApiRepository
import okhttp3.OkHttpClient

object FirebaseApiRepository {

    private const val registerDevice = "$INFOMANIAK_API_V1/devices/register"

    fun registerForNotifications(registrationInfo: RegistrationInfo, okHttpClient: OkHttpClient): ApiResponse<Boolean> {
        return ApiRepository.callApi(registerDevice, POST, registrationInfo, okHttpClient)
    }
}
