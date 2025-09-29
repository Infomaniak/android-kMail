/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import android.content.pm.PackageInfo
import androidx.webkit.WebViewCompat
import com.infomaniak.core.sentry.SentryLog

object WebViewVersionUtils {
    private val TAG = WebViewVersionUtils::class.java.simpleName
    private const val DEFAULT_WEBVIEW_VERSION = 0

    fun getWebViewVersionData(context: Context): WebViewVersionData? {
        val webViewPackage = WebViewCompat.getCurrentWebViewPackage(context) ?: return null
        val webViewPackageName = webViewPackage.packageName
        val (versionName, majorVersion) = webViewPackage.getWebViewVersions() ?: return null

        return WebViewVersionData(versionName, majorVersion, webViewPackageName)
    }

    private fun PackageInfo.getWebViewVersions(): Pair<String, Int>? {
        val versionName = this.versionName ?: run {
            SentryLog.e(TAG, "PackageInfo.versionName is null")
            return null
        }
        val majorVersion = runCatching {
            versionName.substringBefore('.').toInt()
        }.getOrDefault(defaultValue = DEFAULT_WEBVIEW_VERSION)

        return versionName to majorVersion
    }

    data class WebViewVersionData(
        val versionName: String,
        val majorVersion: Int,
        val webViewPackageName: String,
    )
}
