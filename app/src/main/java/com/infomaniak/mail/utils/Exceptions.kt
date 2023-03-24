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
package com.infomaniak.mail.utils

import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.serialization.decodeFromString

class ApiErrorException(override val message: String?) : Exception() {

    inline val apiResponse get() = ApiController.json.decodeFromString<ApiResponse<Any>>(message!!)

    object ErrorCodes {
        const val DRAFT_DOES_NOT_EXIST = "draft__not_found"
        const val DRAFT_HAS_MANY_RECIPIENTS = "draft__to_many_recipients"
        const val FOLDER_ALREADY_EXISTS = "folder__destination_already_exists"
        const val FOLDER_DOES_NOT_EXIST = "folder__not_exists"
    }
}
