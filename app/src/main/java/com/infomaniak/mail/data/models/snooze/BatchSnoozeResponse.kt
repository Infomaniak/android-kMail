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
package com.infomaniak.mail.data.models

import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.utils.SharedUtils.Companion.BatchSnoozeResult
import com.infomaniak.mail.utils.extensions.getFirstTranslatedError

interface BatchSnoozeResponse {
    val processedUuids: List<String>

    companion object {
        fun <T: BatchSnoozeResponse> List<ApiResponse<T>>.computeSnoozeResult(impactedFolderIds: ImpactedFolders) = when {
            all { it.isSuccess().not() } -> {
                val translatedError = getFirstTranslatedError()
                if (translatedError == null) BatchSnoozeResult.Error.Unknown else BatchSnoozeResult.Error.ApiError(translatedError)
            }
            any { it.isSuccess() && it.data?.processedUuids?.isNotEmpty() == true } -> {
                BatchSnoozeResult.Success(impactedFolderIds)
            }
            else -> BatchSnoozeResult.Error.NoneSucceeded
        }
    }
}
