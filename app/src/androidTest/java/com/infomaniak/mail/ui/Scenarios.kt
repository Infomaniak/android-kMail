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

import android.os.Build
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.NavHostFragment
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
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.login.LoginFragment
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

        onView(isRoot()).perform(waitFor(3.seconds))

        // Clicking on the connect button
        onWebView()
            .withElement(findElement(Locator.XPATH, "//button[@data-testid='btn-connect']"))
            .perform(webClick())

        onView(isRoot()).perform(waitFor(3.seconds))
    }

    fun FragmentActivity.startLoginWebviewActivity() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.loginHostFragment) as NavHostFragment
        val loginFragment = navHostFragment.childFragmentManager.fragments.first() as LoginFragment
        loginFragment.openLoginWebView()
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

    fun UiDevice.deactivateAnimations() {
        executeShellCommand("settings put global window_animation_scale 0")
        executeShellCommand("settings put global transition_animation_scale 0")
        executeShellCommand("settings put global animator_duration_scale 0")
        executeShellCommand("settings put global layout_animation_duration_scale 0")
        executeShellCommand("settings put global force_animator_hardware_acceleration false")
    }

    fun grantPermissions(device: UiDevice, permissions: List<String>, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permissions.forEach { permission ->
                try {
                    device.executeShellCommand("pm grant $packageName $permission")
                    Thread.sleep(500) // Give system time to process
                } catch (e: Exception) { // CatKotlinch generic Exception
                    Log.e("GrantPermissionRule", "Error granting permission: $permission", e)
                }
            }
        }
    }
}
