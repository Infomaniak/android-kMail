/*
 * Infomaniak kMail - Android
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
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.resetLastAppClosing
import io.sentry.Sentry
import kotlinx.coroutines.runBlocking

open class ThemedActivity : AppCompatActivity() {

    protected val localSettings by lazy { LocalSettings.getInstance(this) }

    var hasLocked = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(LocalSettings.getInstance(this).accentColor.theme)

        if (AccountUtils.currentUser == null) runBlocking {
            AccountUtils.requestCurrentUser()
            Sentry.withScope { scope ->
                scope.setExtra("has been fixed", "${AccountUtils.currentUser != null}")
                Sentry.captureMessage("ThemedActivity> the current user is null")
            }
        }

        super.onCreate(savedInstanceState)
        trackScreen()
    }

    override fun onResume() {
        super.onResume()

        val lastAppClosing = (application as MainApplication).lastAppClosing

        if (lastAppClosing != null && isKeyguardSecure() && localSettings.isAppLocked) {
            LockActivity.lockAfterTimeout(
                lastAppClosing = lastAppClosing,
                context = this,
                destinationClass = this::class.java,
                primaryColor = localSettings.accentColor.getPrimary(this),
            )
            application.resetLastAppClosing()
            hasLocked = true
        }
    }
}
