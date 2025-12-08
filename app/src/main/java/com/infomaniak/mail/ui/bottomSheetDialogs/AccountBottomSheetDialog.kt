/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.utils.year
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackAccountEvent
import com.infomaniak.mail.MatomoMail.trackEasterEggEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.BottomSheetAccountBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.main.easterEgg.EventsEasterEgg
import com.infomaniak.mail.ui.main.user.SwitchUserAdapter
import com.infomaniak.mail.ui.main.user.SwitchUserViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ConfettiUtils
import com.infomaniak.mail.utils.ConfettiUtils.ConfettiType.COLORED_SNOW
import com.infomaniak.mail.utils.LogoutUser
import com.infomaniak.mail.utils.extensions.bindAlertToViewLifecycle
import com.infomaniak.mail.utils.extensions.launchLoginActivity
import dagger.hilt.android.AndroidEntryPoint
import io.sentry.Sentry.captureMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class AccountBottomSheetDialog : EdgeToEdgeBottomSheetDialog() {

    private var binding: BottomSheetAccountBinding by safeBinding()

    @Inject
    lateinit var logoutUser: LogoutUser

    @Inject
    lateinit var appScope: CoroutineScope

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    @Inject
    lateinit var descriptionDialog: DescriptionAlertDialog

    private val switchUserViewModel: SwitchUserViewModel by activityViewModels()

    private val accountsAdapter = SwitchUserAdapter(
        currentUserId = AccountUtils.currentUserId,
        onChangingUserAccount = { user ->
            if (user.id == AccountUtils.currentUserId) {
                ConfettiUtils.onEasterEggConfettiClicked(
                    container = (activity as? MainActivity)?.getConfettiContainer(),
                    type = COLORED_SNOW,
                    matomoValue = "Avatar",
                )
            } else {
                switchUserViewModel.switchAccount(user)
            }
        },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return BottomSheetAccountBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        recyclerViewAccount.adapter = accountsAdapter
        logout.setOnClickListener {
            trackAccountEvent(MatomoName.LogOut)
            descriptionDialog.show(
                title = getString(R.string.confirmLogoutTitle),
                description = AccountUtils.currentUser?.let { getString(R.string.confirmLogoutDescription, it.email) } ?: "",
                displayLoader = false,
                onPositiveButtonClicked = ::logoutCurrentUser,
            )
        }
        addAccount.setOnClickListener {
            trackAccountEvent(MatomoName.Add)
            context.launchLoginActivity()
        }
        observeAccounts()

        bindAlertToViewLifecycle(descriptionDialog)

        showEasterEggHalloween()
    }

    private fun logoutCurrentUser() = appScope.launch(ioDispatcher) {
        trackAccountEvent(MatomoName.LogOutConfirm)
        logoutUser(user = AccountUtils.currentUser!!)
    }

    private fun observeAccounts() = with(switchUserViewModel) {
        accounts.observe(viewLifecycleOwner) {
            binding.root.title = requireContext().resources.getQuantityString(
                R.plurals.titleMyAccount,
                it.size,
                it.size,
            )
            accountsAdapter.initializeAccounts(it)
        }
        getAccountsInDB()
    }

    private fun showEasterEggHalloween() = lifecycleScope.launch {
        val currentMailbox = switchUserViewModel.currentMailbox.first()
        if (EventsEasterEgg.Halloween(currentMailbox.kSuite).shouldTrigger().not()) return@launch

        val halloween = (activity as? MainActivity)?.getHalloweenLayout() ?: return@launch
        if (halloween.isAnimating) return@launch

        halloween.playAnimation()
        captureMessage("Easter egg Halloween has been triggered! Woohoo!")
        trackEasterEggEvent("${MatomoName.Halloween.value}${Date().year()}")
    }
}
