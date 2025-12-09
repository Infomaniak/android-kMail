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
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.web.sugar.Web.onWebView
import androidx.test.espresso.web.webdriver.DriverAtoms.findElement
import androidx.test.espresso.web.webdriver.DriverAtoms.webClick
import androidx.test.espresso.web.webdriver.DriverAtoms.webKeys
import androidx.test.espresso.web.webdriver.Locator
import androidx.test.uiautomator.UiDevice
import org.hamcrest.Matcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Scenarios {

    fun login(email: String, password: String) {
        // Typing the email
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@data-testid='input-email']"))
            .perform(webClick())
            .perform(webKeys(email))

        // Typing the password
        onWebView()
            .withElement(findElement(Locator.XPATH, "//input[@name='password']"))
            .perform(webKeys(password))

        // Clicking on the connect button
        onWebView()
            .withElement(findElement(Locator.XPATH, "//button[@data-testid='btn-connect']"))
            .perform(webClick())

        onView(isRoot()).perform(waitFor(3.seconds))
    }

    fun waitFor(delay: Duration): ViewAction = object : ViewAction {
        override fun getConstraints(): Matcher<View?>? = isRoot()

        override fun getDescription() = "wait for " + delay + "milliseconds"

        override fun perform(uiController: UiController, view: View?) {
            uiController.loopMainThreadForAtLeast(delay.inWholeMilliseconds)
        }
    }

    fun UiDevice.toggleAnimations(activate: Boolean) {
        val enable = if (activate) 1 else 0
        executeShellCommand("settings put global window_animation_scale $enable")
        executeShellCommand("settings put global transition_animation_scale $enable")
        executeShellCommand("settings put global animator_duration_scale $enable")
        executeShellCommand("settings put global layout_animation_duration_scale $enable")
        executeShellCommand("settings put global force_animator_hardware_acceleration $activate")
    }

    fun grantPermissions(device: UiDevice, permissions: List<String>, packageName: String) {
        permissions.forEach { permission ->
            runCatching {
                device.executeShellCommand("pm grant $packageName $permission")
                Thread.sleep(500)
            }
        }
    }
}
