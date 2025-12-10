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
package com.infomaniak.mail.ui.newMessage

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.swipeDown
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.Scenarios.waitFor
import com.infomaniak.mail.ui.Utils.onViewWithTimeout
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.BaseActivityTest
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactViewHolder
import com.infomaniak.mail.utils.Env
import org.hamcrest.core.AllOf.allOf
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@LargeTest
class NewMessageActivityTest : BaseActivityTest(startingActivity = LoginActivity::class) {

    @Test
    fun sendEmail() {
        onView(withId(R.id.newMessageFab)).perform(click())

        // Waiting for the view to settle
        onView(isRoot()).perform(waitFor(3.seconds))

        // Dismissing the AI BottomSheet. Clicking on "Later" is not working for some reason
        device.click(0, 0)

        // Just clicking on the toField does not work ...
        onView(
            allOf(
                withId(R.id.chevron),
                isDescendantOfA(withId(R.id.toField))
            )
        ).perform(click())

        enterEmailToField(R.id.toField, R.id.autoCompleteTo)
        enterEmailToField(R.id.ccField, R.id.autoCompleteCc)
        enterEmailToField(R.id.bccField, R.id.autoCompleteBcc)

        val subject = "UI test mail #${UUID.randomUUID()}"

        onView(withId(R.id.subjectTextField)).perform(click(), typeText(subject), closeSoftKeyboard())
        onView(withId(R.id.editorWebView)).perform(click(), typeText("This is an email from UI test"), closeSoftKeyboard())
        onView(withId(R.id.sendButton)).perform(click())

        onView(withId(R.id.threadsList)).perform(swipeDown())

        // Checking if the email with a specific ID to be received
        onViewWithTimeout(
            retryInterval = 5_000.milliseconds,
            matcher = withId(R.id.threadsList),
            assertion = matches(hasDescendant(withText(subject))),
        )
    }

    private fun enterEmailToField(fieldResId: Int, suggestionListResId: Int) {
        onView(
            allOf(
                withId(R.id.textInput),
                isDescendantOfA(withId(fieldResId)),
            )
        ).perform(click(), typeText(Env.UI_TEST_ACCOUNT_EMAIL))
        onView(withId(suggestionListResId))
            .perform(RecyclerViewActions.actionOnItemAtPosition<ContactViewHolder>(0, click()))
    }
}
