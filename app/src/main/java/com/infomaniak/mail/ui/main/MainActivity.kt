/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.MainViewModel
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class MainActivity : AppCompatActivity() {

    private val testRealmViewModel: TestRealmViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val navController = setupNavController()
        mainViewModel.fetchMailboxesAndFolders()
//        findViewById<Button>(R.id.startCalls).setOnClickListener {
//            lifecycleScope.launch(Dispatchers.IO) {
////                lightlyPopulateRealm()
//                // fullyPopulateRealm()
//        callAllAPIs()
//
//            }
//        }

        navController.addOnDestinationChangedListener { _, dest, args -> onDestinationChanged(dest, args) }
    }

    private fun onDestinationChanged(destination: NavDestination, navigationArgs: Bundle?) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "Navigation"
            message = "Accessed to destination : ${destination.displayName}"
            level = SentryLevel.INFO
        })

        // TODO Matomo
//        with(destination) {
//            application.trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
//        }
    }

    private fun setupNavController(): NavController {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment
        return navHostFragment.navController.apply {
            if (currentDestination == null) navigate(graph.startDestinationId)
        }
    }
}
