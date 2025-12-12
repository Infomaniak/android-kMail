/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.inappupdate.updaterequired.ui.UpdateRequiredActivity.Companion.startUpdateRequiredActivity
import com.infomaniak.core.legacy.applock.LockActivity
import com.infomaniak.core.twofactorauth.front.TwoFactorAuthApprovalAutoManagedBottomSheet
import com.infomaniak.core.twofactorauth.front.addComposeOverlay
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.di.ActivityModule
import com.infomaniak.mail.twoFactorAuthManager
import com.infomaniak.mail.utils.AccountUtils
import dagger.hilt.android.EntryPointAccessors
import io.sentry.Sentry
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.jvm.java

open class BaseActivity : AppCompatActivity() {
    private val hiltEntryPoint by lazy { EntryPointAccessors.fromActivity(this, ActivityModule.ActivityEntrypointInterface::class.java) }

    // TODO: Try to replace this with a dependency injection.
    //  Currently, it crashes because the lateinit value isn't initialized when the `MainActivity.onCreate()` calls its super.
    protected val localSettings by lazy { LocalSettings.getInstance(context = this) }

    /**
     * Enables the auto-managed 2 factor authentication challenge overlay for View-based Activities.
     *
     * ### 2 important things:
     *
     * 1. **Always call this after [setContentView].**
     * 2. If you need to use it inside a compose-based Activity (i.e. w/ `setContent`), use [TwoFactorAuthAutoManagedBottomSheet]
     */
    protected fun addTwoFactorAuthOverlay() {
        addComposeOverlay { TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager) }
    }

    /**
     * Enables the auto-managed 2 factor authentication challenge overlay for Compose-based Activities.
     */
    @Composable
    protected fun TwoFactorAuthAutoManagedBottomSheet() {
        TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(localSettings.accentColor.theme)

        if (AccountUtils.currentUser == null) runBlocking {
            AccountUtils.requestCurrentUser()
            if (AccountUtils.currentUser == null) {
                Sentry.captureMessage("BaseActivity> the current user is null") { scope ->
                    scope.setExtra("has been fixed", "false")
                }
            }
        }

        super.onCreate(savedInstanceState)
        checkUpdateIsRequired()
        trackScreen()

        LockActivity.scheduleLockIfNeeded(
            targetActivity = this,
            primaryColor = localSettings.accentColor.getPrimary(this),
            isAppLockEnabled = { localSettings.isAppLocked }
        )
    }

    fun getCurrentFragment(@IdRes fragmentContainerViewId: Int) = supportFragmentManager
        .findFragmentById(fragmentContainerViewId)
        ?.childFragmentManager
        ?.primaryNavigationFragment

    private fun checkUpdateIsRequired() {
        lifecycleScope.launch {
            hiltEntryPoint.inAppUpdateManager().isUpdateRequired
                .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
                .collect { isUpdateRequired ->
                    if (isUpdateRequired) {
                        startUpdateRequiredActivity(
                            this@BaseActivity,
                            BuildConfig.APPLICATION_ID,
                            BuildConfig.VERSION_CODE,
                            localSettings.accentColor.theme
                        )
                    }
                }
        }
    }

}
