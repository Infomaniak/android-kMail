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

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import androidx.test.runner.AndroidJUnit4
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.Scenarios
import com.infomaniak.mail.ui.Scenarios.waitFor
import com.infomaniak.mail.utils.Env
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@LargeTest
class LoginActivityTest : BaseActivityTest(startingActivity = LaunchActivity::class, loginDirectly = false) {

    @Test
    fun login() {
        // Going through the onboarding
        composeTestRule.onNodeWithTag("button_next_onboarding").performClick()
        composeTestRule.onNodeWithTag("button_next_onboarding").performClick()
        composeTestRule.onNodeWithTag("button_next_onboarding").performClick()
        composeTestRule.onNodeWithTag("button_login_onboarding").performClick()

        Scenarios.login(Env.UI_TEST_ACCOUNT_EMAIL, Env.UI_TEST_ACCOUNT_PASSWORD)

        onView(isRoot()).perform(waitFor(3.seconds))

        // Contacts synchronization is displayed so login worked
        onView(withId(R.id.newMessageFab)).check(matches(isDisplayed()))
    }
}
