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

import com.infomaniak.lib.core.utils.SentryLog
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object JsoupParserUtil {

    private const val SENTRY_LOG_TAG = "Jsoup memory usage"
    private const val BYTE_TO_MEGABYTE_DIVIDER: Float = 1024f * 1024f

    fun jsoupParseWithLog(value: String): Document {
        val (usedMemoryBefore, maxMemoryBefore) = getMemoryUsage()
        SentryLog.i(SENTRY_LOG_TAG, "Before parsing, used / available: $usedMemoryBefore / $maxMemoryBefore MB")

        val doc = Jsoup.parse(value)

        val (usedMemoryAfter, maxMemoryAfter) = getMemoryUsage()
        SentryLog.i(SENTRY_LOG_TAG, "After parsing, used / available: $usedMemoryAfter / $maxMemoryAfter MB")

        return doc
    }

    fun jsoupParseBodyFragmentWithLog(value: String): Document {
        val (usedMemoryBefore, maxMemoryBefore) = getMemoryUsage()
        SentryLog.i(SENTRY_LOG_TAG, "Before parsing body fragment, used / available: $usedMemoryBefore / $maxMemoryBefore MB")

        val doc = Jsoup.parseBodyFragment(value)

        val (usedMemoryAfter, maxMemoryAfter) = getMemoryUsage()
        SentryLog.i(SENTRY_LOG_TAG, "After parsing body fragment, used / available: $usedMemoryAfter / $maxMemoryAfter MB")

        return doc
    }

    private fun getMemoryUsage(): Pair<Float, Float> {
        val runtime: Runtime = Runtime.getRuntime()
        val usedMemInMegaBytes = (runtime.totalMemory() - runtime.freeMemory()) / BYTE_TO_MEGABYTE_DIVIDER
        val maxHeapSizeInMegaBytes = runtime.maxMemory() / BYTE_TO_MEGABYTE_DIVIDER
        return usedMemInMegaBytes to maxHeapSizeInMegaBytes
    }
}
