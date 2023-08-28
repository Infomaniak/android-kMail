/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.infomaniak.lib.core.api.ApiController
import io.sentry.Sentry
import kotlinx.coroutines.CancellationException

abstract class BaseCoroutineWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    abstract suspend fun launchWork(): Result
    open fun onFinish() = Unit

    final override suspend fun doWork(): Result {
        if (runAttemptCount > MAX_RETRIES) return Result.failure()

        return runCatching {
            launchWork()
        }.getOrElse { exception ->
            exception.printStackTrace()
            when (exception) {
                is CancellationException -> Result.failure()
                is ApiController.NetworkException -> Result.retry()
                else -> {
                    Sentry.captureException(exception)
                    Result.failure()
                }
            }
        }.also {
            onFinish()
        }
    }

    protected fun Data.getIntOrNull(key: String) = getInt(key, 0).run { if (this == 0) null else this }

    companion object {
        private const val MAX_RETRIES = 3
    }
}
