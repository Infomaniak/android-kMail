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

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.ui.Scenarios.grantPermissions
import com.infomaniak.mail.ui.Scenarios.login
import com.infomaniak.mail.ui.Scenarios.logout
import com.infomaniak.mail.ui.Scenarios.toggleAnimations
import com.infomaniak.mail.ui.Scenarios.waitFor
import com.infomaniak.mail.utils.Env
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.time.Duration.Companion.seconds

open class BaseActivityTest(startingActivity: KClass<out ComponentActivity>, private val loginDirectly: Boolean = true) {

    private val composeRule = createAndroidComposeRule(startingActivity.java)
    private val timeoutRule = Timeout(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(timeoutRule).around(composeRule)

    val composeTestRule get() = composeRule

    private val packageName: String = InstrumentationRegistry.getInstrumentation().targetContext.packageName

    lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.toggleAnimations(activate = false)

        val permissions = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
        )

        grantPermissions(device, permissions, packageName)

        // Disable AI discovery bottom sheet for tests to avoid interruptions
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LocalSettings.getInstance(context).showAiDiscoveryBottomSheet = false

        if (loginDirectly) {
            composeTestRule.onNodeWithTag("button_login_onboarding").performClick()
            login(Env.UI_TEST_ACCOUNT_EMAIL, Env.UI_TEST_ACCOUNT_PASSWORD)

            // Waiting a little bit to let the app load
            onView(isRoot()).perform(waitFor(3.seconds))
        }
    }

    @After
    fun cleanUp() {
        runCatching { logout(composeTestRule) }
        device.toggleAnimations(activate = true)
    }

    companion object {
        private const val TEST_TIMEOUT_SECONDS = 180L
    }
}
