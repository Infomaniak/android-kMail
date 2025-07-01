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
package com.infomaniak.mail

import android.view.View
import androidx.core.view.isVisible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

suspend fun View.awaitOneLongClick(
    disableAfterClick: Boolean = true,
    hideAfterClick: Boolean = false,
) = try {
    if (disableAfterClick) isEnabled = true
    if (hideAfterClick) isVisible = true
    suspendCancellableCoroutine<Unit> { continuation ->
        setOnLongClickListener {
            setOnLongClickListener(null)
            continuation.resume(Unit); true
        }
    }
} finally {
    setOnLongClickListener(null)
    if (disableAfterClick) isEnabled = false
    if (hideAfterClick) isVisible = false
}
