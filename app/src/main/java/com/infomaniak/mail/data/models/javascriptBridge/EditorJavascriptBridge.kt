/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.mail.data.models.javascriptBridge

import android.webkit.JavascriptInterface
import com.infomaniak.core.sentry.SentryLog
import org.json.JSONArray

class EditorJavascriptBridge(
    private val onImagesDeletedFromQuotes: (List<String>) -> Unit,
) {
    @JavascriptInterface
    fun onImagesDeletedFromQuotes(cidJson: String) {
        val jsonArray = runCatching {
            JSONArray(cidJson)
        }.onFailure {
            SentryLog.e(TAG, "Failed to parse CIDs", it)
        }.getOrNull() ?: return

        val cids = (0 until jsonArray.length()).map { jsonArray.getString(it) }
        onImagesDeletedFromQuotes(cids)
    }

    companion object {
        private const val TAG = "EditorJavascriptBridge"
    }
}
