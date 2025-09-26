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

import com.infomaniak.core.sentry.SentryLog
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object JsoupParserUtil {

    private const val SENTRY_LOG_TAG = "Jsoup memory usage"
    private const val BYTE_TO_MEGABYTE_DIVIDER = 1_024.0f * 1_024.0f

    fun jsoupParseWithLog(value: String): Document {
        return measureAndLogMemoryUsage(SENTRY_LOG_TAG, actionName = "parsing") {
            Jsoup.parse(value)
        }
    }

    fun jsoupParseBodyFragmentWithLog(value: String): Document {
        return measureAndLogMemoryUsage(SENTRY_LOG_TAG, actionName = "parsing body fragment") {
            Jsoup.parseBodyFragment(value)
        }
    }

    /**
     * Measures and logs to sentry the used and available RAM of the JVM
     */
    fun <R> measureAndLogMemoryUsage(tag: String, actionName: String, block: () -> R): R {
        val (usedMemoryBefore, maxMemoryBefore) = getMemoryUsage()
        SentryLog.i(tag, "Before $actionName, used / available - $usedMemoryBefore / $maxMemoryBefore MB")

        val result = block()

        val (usedMemoryAfter, maxMemoryAfter) = getMemoryUsage()
        SentryLog.i(tag, "After $actionName, used / available - $usedMemoryAfter / $maxMemoryAfter MB")

        return result
    }

    private fun getMemoryUsage(): Pair<Float, Float> {
        val runtime: Runtime = Runtime.getRuntime()
        val usedMemInMegaBytes = (runtime.totalMemory() - runtime.freeMemory()) / BYTE_TO_MEGABYTE_DIVIDER
        val maxHeapSizeInMegaBytes = runtime.maxMemory() / BYTE_TO_MEGABYTE_DIVIDER
        return usedMemInMegaBytes to maxHeapSizeInMegaBytes
    }
}
