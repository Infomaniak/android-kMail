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
package com.infomaniak.mail.ui

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object Utils {

    fun onViewWithTimeout(
        numberOfRetries: Int = 10,
        retryInterval: Duration = 500.milliseconds,
        assertion: ViewAssertion = matches(withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)),
        matcher: Matcher<View>,
    ): ViewInteraction {
        var viewInteraction: ViewInteraction? = null

        repeat(numberOfRetries) { i ->
            runCatching {
                viewInteraction = onView(matcher).apply {
                    check(assertion)
                }
            }.onFailure { exception ->
                if (i < numberOfRetries && exception is AssertionFailedError) Thread.sleep(retryInterval.inWholeMilliseconds)
            }
        }

        if (viewInteraction == null) throw AssertionError("View matcher is broken for $matcher")

        return viewInteraction
    }
}
