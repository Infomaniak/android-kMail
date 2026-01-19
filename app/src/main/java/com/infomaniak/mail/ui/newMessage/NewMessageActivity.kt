/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
@file:OptIn(ExperimentalSplittiesApi::class)

package com.infomaniak.mail.ui.newMessage

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail.trackDestination
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.AppSettings
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.LaunchActivity
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.SentryDebug
import com.infomaniak.mail.utils.Utils.Shortcuts
import com.infomaniak.mail.workers.DraftsActionsWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import splitties.experimental.ExperimentalSplittiesApi
import javax.inject.Inject

@AndroidEntryPoint
class NewMessageActivity : BaseActivity() {

    private val binding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val newMessageViewModel: NewMessageViewModel by viewModels()
    private val aiViewModel: AiViewModel by viewModels()

    private val navController by lazy {
        (supportFragmentManager.findFragmentById(R.id.newMessageHostFragment) as NavHostFragment).navController
    }

    @Inject
    lateinit var globalCoroutineScope: CoroutineScope

    @Inject
    lateinit var snackbarManager: SnackbarManager

    @Inject
    lateinit var draftsActionsWorkerScheduler: DraftsActionsWorker.Scheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        enableEdgeToEdge()

        ShortcutManagerCompat.reportShortcutUsed(this@NewMessageActivity, Shortcuts.NEW_MESSAGE.id)
        setContentView(binding.root)
        addTwoFactorAuthOverlay()
        setupNagGraphStartDestination()

        if (!isAuth()) {
            finish()
            return
        }

        lifecycleScope.launch {
            // This awaits indefinitely until the currentMailbox in the viewModel is null.
            // If this is the case, we quit the activity
            newMessageViewModel.awaitNoMailboxSignal()
            finish()
        }

        setupSnackbar()
        setupNavController()
        setupFeatureFlagIfMailTo()
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == AppSettings.DEFAULT_ID) {
            startActivity(Intent(this, LaunchActivity::class.java))
            return false
        }

        return true
    }

    private fun setupNagGraphStartDestination() {
        val navGraph = navController.navInflater.inflate(R.navigation.new_message_navigation)
        lifecycleScope.launch {
            when (intent.action) {
                Intent.ACTION_SEND,
                Intent.ACTION_SEND_MULTIPLE,
                Intent.ACTION_VIEW,
                Intent.ACTION_SENDTO -> {
                    navGraph.setStartDestination(
                        if (newMessageViewModel.hasMultiMailboxes()) {
                            R.id.selectMailboxFragment
                        } else {
                            R.id.newMessageFragment
                        }
                    )
                }
                else -> {
                    navGraph.setStartDestination(R.id.newMessageFragment)
                }
            }
            navController.graph = navGraph
        }
    }

    private fun setupSnackbar() {
        fun getAnchor(): View? = when (navController.currentDestination?.id) {
            R.id.newMessageFragment -> findViewById(R.id.editorToolbar)
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

    private fun setupFeatureFlagIfMailTo() = lifecycleScope.launch {
        when (intent.action) {
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
            Intent.ACTION_VIEW,
            Intent.ACTION_SENDTO -> with(newMessageViewModel.currentMailbox()) {
                aiViewModel.updateFeatureFlag(objectId, uuid)
            }
        }
    }

    // This `SuppressLint` seems useless, but it's for the CI. Don't remove it.
    @SuppressLint("RestrictedApi")
    private fun onDestinationChanged(destination: NavDestination, arguments: Bundle?) {
        SentryDebug.addNavigationBreadcrumb(destination.displayName, arguments)
        trackDestination(destination)
    }

    override fun onStop() {
        saveDraft()
        super.onStop()
    }

    private fun saveDraft() {
        val draftSaveConfiguration = DraftSaveConfiguration(
            action = newMessageViewModel.draftAction,
            isFinishing = isFinishing,
            isTaskRoot = isTaskRoot,
            startWorkerCallback = ::startWorker,
        )

        newMessageViewModel.waitForBodyAndSubjectToExecuteDraftAction(draftSaveConfiguration)
    }

    private fun startWorker() {
        globalCoroutineScope.launch {
            draftsActionsWorkerScheduler.scheduleWork(
                draftLocalUuid = newMessageViewModel.draftLocalUuid(),
                mailboxId = newMessageViewModel.currentMailbox().mailboxId,
            )
        }
    }

    data class DraftSaveConfiguration(
        val action: DraftAction,
        val isFinishing: Boolean,
        val isTaskRoot: Boolean,
        val startWorkerCallback: () -> Unit,
    ) {
        var subjectValue: String = ""
            private set
        var uiBodyValue: String = ""
            private set

        fun addSubjectAndBody(subject: String, body: String) {
            subjectValue = subject
            uiBodyValue = body
        }
    }
}
