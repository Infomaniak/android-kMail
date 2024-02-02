/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.viewModels
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NewMessageActivity : BaseActivity() {

    private val binding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val newMessageViewModel: NewMessageViewModel by viewModels()

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.newMessageHostFragment) as NavHostFragment).navController
    }

    @Inject
    lateinit var snackbarManager: SnackbarManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContentView(binding.root)

        if (!isAuth()) {
            finish()
            return
        }

        setupSnackbar()
        setupNavController()
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == AppSettings.DEFAULT_ID) {
            startActivity(Intent(this, LaunchActivity::class.java))
            return false
        }
        return true
    }

    private fun setupSnackbar() {
        fun getAnchor(): View? = when (navController.currentDestination?.id) {
            R.id.newMessageFragment -> findViewById(R.id.editor)
            R.id.aiPropositionFragment -> findViewById(R.id.aiPropositionBottomBar)
            else -> null
        }

        snackbarManager.setup(view = binding.root, activity = this, getAnchor = ::getAnchor)
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
}
