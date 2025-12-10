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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.infomaniak.mail.ui.Scenarios.grantPermissions
import com.infomaniak.mail.ui.Scenarios.login
import com.infomaniak.mail.ui.Scenarios.toggleAnimations
import com.infomaniak.mail.utils.Env
import org.junit.After
import org.junit.Before
import org.junit.Rule
import kotlin.reflect.KClass

open class BaseActivityTest(startingActivity: KClass<out ComponentActivity>, private val loginDirectly: Boolean = true) {

    @get:Rule
    val composeTestRule = createAndroidComposeRule(startingActivity.java)

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

        if (loginDirectly) {
            composeTestRule.onNodeWithTag("button_login_onboarding").performClick()
            login(Env.UI_TEST_ACCOUNT_EMAIL, Env.UI_TEST_ACCOUNT_PASSWORD)
        }
    }

    @After
    fun cleanUp() {
        device.toggleAnimations(activate = true)
    }
}
