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
package com.infomaniak.mail.ui.login

import android.view.View
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.LaunchActivity
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginActivityTest {

    @get:Rule
    var activityScenarioRule = activityScenarioRule<LaunchActivity>()

    @Test
    fun login() {
        val username = BuildConfig.UI_TEST_ACCOUNT_EMAIL
        val password = BuildConfig.UI_TEST_ACCOUNT_PASSWORD

        // Going through the onboarding
        onView(withId(R.id.nextButton)).perform(click())
        onView(withId(R.id.nextButton)).perform(click())
        onView(withId(R.id.nextButton)).perform(click())
        onView(withId(R.id.connectButton)).perform(click())

        // Typing the email
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@data-testid='input-email']"))
            .perform(webClick())
            .perform(webKeys(username))

        // Typing the password
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@name='password']"))
            .perform(webKeys(password))

        // Clicking on the connect button
        onWebView()
            .withElement(findElement(Locator.XPATH, "//button[@data-testid='btn-connect']"))
            .perform(webClick())

        // Wait for the login to be done
        onView(isRoot()).perform(waitFor(3.seconds))

        // Contacts synchronization is displayed so login worked
        onView(withId(R.id.continueButton)).check(matches(isDisplayed()))
    }
}

fun waitFor(delay: Duration): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View?>? = isRoot()

        override fun getDescription() = "wait for " + delay + "milliseconds"

        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(delay.inWholeMilliseconds)
        }
    }
}