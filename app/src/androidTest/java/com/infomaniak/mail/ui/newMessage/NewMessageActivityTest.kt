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

import android.Manifest
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import androidx.test.uiautomator.UiDevice
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.Scenarios.grantPermissions
import com.infomaniak.mail.ui.Scenarios.login
import com.infomaniak.mail.ui.Scenarios.startLoginWebviewActivity
import com.infomaniak.mail.ui.Scenarios.waitFor
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.newMessage.ContactAdapter.ContactViewHolder
import org.hamcrest.Matchers.allOf
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@LargeTest
class NewMessageActivityTest {

    private lateinit var device: UiDevice
    private val packageName: String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    @get:Rule
    var loginActivityScenarioRule = activityScenarioRule<LoginActivity>()

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val permissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        grantPermissions(device, permissions, packageName)

        val email = BuildConfig.UI_TEST_ACCOUNT_EMAIL
        val password = BuildConfig.UI_TEST_ACCOUNT_PASSWORD

        loginActivityScenarioRule.scenario.onActivity { activity ->
            activity.startLoginWebviewActivity()
        }

        login(email, password)
        loginActivityScenarioRule.scenario.close()
    }

    @Test
    fun sendEmail() {
        onView(withId(R.id.newMessageFab)).perform(click())

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

        onView(withId(R.id.subjectTextField)).perform(click(), typeText(subject))
        onView(withId(R.id.editorWebView)).perform(click(), typeText("This is an email from UI test"))
        onView(withId(R.id.sendButton)).perform(click())

        // Waiting for the email to be received
        onView(isRoot()).perform(waitFor(10.seconds))

        // Checking if the email with a specific ID to be received
        onView(withId(R.id.threadsList))
            .check(matches(hasDescendant(withText(subject))))
    }

    private fun enterEmailToField(fieldResId: Int, suggestionListResId: Int) {
        onView(
            allOf(
                withId(R.id.textInput),
                isDescendantOfA(withId(fieldResId)),
            )
        ).perform(click(), typeText("test.test@ik.me"))
        onView(withId(suggestionListResId))
            .perform(RecyclerViewActions.actionOnItemAtPosition<ContactViewHolder>(0, click()))
    }
}
