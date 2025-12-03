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
package com.infomaniak.mail.ui.login

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.core.legacy.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.core.twofactorauth.front.TwoFactorAuthApprovalAutoManagedBottomSheet
import com.infomaniak.core.twofactorauth.front.addComposeOverlay
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.MatomoMail.trackScreen
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.databinding.ActivityLoginBinding
import com.infomaniak.mail.twoFactorAuthManager
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.Utils.openShortcutHelp
import com.infomaniak.mail.utils.extensions.getInfomaniakLogin
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private val binding by lazy { ActivityLoginBinding.inflate(layoutInflater) }

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.loginHostFragment) as NavHostFragment).navController
    }

    lateinit var infomaniakLogin: InfomaniakLogin

    private val navigationArgs: LoginActivityArgs? by lazy { intent?.extras?.let { LoginActivityArgs.fromBundle(it) } }

    private val localSettings by lazy { LocalSettings.getInstance(context = this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockOrientationForSmallScreens()
        setTheme(localSettings.accentColor.theme)

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false

        setContentView(binding.root)
        addComposeOverlay { TwoFactorAuthApprovalAutoManagedBottomSheet(twoFactorAuthManager) }

        infomaniakLogin = getInfomaniakLogin()

        setupNavController()

        handleHelpShortcut()

        trackScreen()
    }

    private fun setupNavController() {
        navController.addOnDestinationChangedListener { _, destination, arguments ->
            onDestinationChanged(destination, arguments)
        }
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    private fun onDestinationChanged(destination: NavDestination, arguments: Bundle?) {
        SentryDebug.addNavigationBreadcrumb(destination.displayName, arguments)
        trackDestination(destination)
    }

    private fun handleHelpShortcut() {
        navigationArgs?.isHelpShortcutPressed?.let { isHelpShortcutPressed ->
            if (isHelpShortcutPressed) {
                openShortcutHelp(context = this)
            }
        }
    }
}
