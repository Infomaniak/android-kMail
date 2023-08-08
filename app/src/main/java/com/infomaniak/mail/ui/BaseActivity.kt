/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.applock.Utils.isKeyguardSecure
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.getMainApplication
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking

open class BaseActivity : AppCompatActivity() {

    // TODO: Try to replace this with a dependency injection.
    //  Currently, it crashes because the lateinit value isn't initialized when the `MainActivity.onCreate()` calls its super.
    protected val localSettings by lazy { LocalSettings.getInstance(context = this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(localSettings.accentColor.theme)

        if (AccountUtils.currentUser == null) runBlocking {
            AccountUtils.requestCurrentUser()
            if (AccountUtils.currentUser == null) {
                Sentry.withScope { scope ->
                    scope.setExtra("has been fixed", "false")
                    Sentry.captureMessage("BaseActivity> the current user is null")
                }
            }
        }

        super.onCreate(savedInstanceState)
        trackScreen()
    }

    override fun onResume() {
        super.onResume()

        if (localSettings.isAppLocked && isKeyguardSecure()) with(getMainApplication()) {
            lastAppClosingTime?.let {
                LockActivity.lockAfterTimeout(
                    context = this@BaseActivity,
                    destinationClass = this::class.java,
                    lastAppClosingTime = it,
                    primaryColor = localSettings.accentColor.getPrimary(this),
                )
            }

            resetLastAppClosing()
        }
    }
}
