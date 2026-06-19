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
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactViewHolder
import com.infomaniak.mail.utils.Env
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.core.AllOf.allOf
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

    fun enterEmailToField(fieldResId: Int, value: String = Env.UI_TEST_ACCOUNT_EMAIL, suggestionListResId: Int) {
        onViewWithTimeout(
            matcher = allOf(
                withId(R.id.textInput),
                isDescendantOfA(withId(fieldResId)),
            )
        ).perform(click(), typeText(value))

        onViewWithTimeout(
            matcher = withId(suggestionListResId),
        ).perform(
            RecyclerViewActions.actionOnItemAtPosition<ContactViewHolder>(
                0,
                click()
            )
        )
    }

    fun assertRecipientInField(fieldResId: Int, emailAddress: String) {
        onView(
            allOf(
                withText(emailAddress),
                isDescendantOfA(withId(fieldResId)),
            )
        ).check(matches(isDisplayed()))
    }
}
