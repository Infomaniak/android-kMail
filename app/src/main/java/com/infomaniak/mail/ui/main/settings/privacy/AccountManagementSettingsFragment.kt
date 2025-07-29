/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.settings.privacy

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.infomaniak.lib.core.BuildConfig.AUTOLOG_URL
import com.infomaniak.lib.core.BuildConfig.TERMINATE_ACCOUNT_URL
import com.infomaniak.lib.core.ui.WebViewActivity
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.databinding.FragmentAccountManagementSettingsBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.setSystemBarsColors
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AccountManagementSettingsFragment : Fragment() {

    private var binding: FragmentAccountManagementSettingsBinding by safeBinding()

    private val accountManagementSettingsViewModel: AccountManagementSettingsViewModel by viewModels()

    private val currentUser by lazy { AccountUtils.currentUser }

    private val resultActivityResultLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) currentUser?.let(accountManagementSettingsViewModel::disconnectDeletedUser)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAccountManagementSettingsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemBarsColors()

        setUi()
        setDeleteAccountClickListener()
    }

    private fun setUi() = with(binding) {
        currentUser?.let {
            username.text = it.displayName
            email.text = it.email
        }
    }

    private fun setDeleteAccountClickListener() = with(binding) {
        deleteAccountButton.setOnClickListener {
            WebViewActivity.startActivity(
                context = requireContext(),
                url = TERMINATE_ACCOUNT_FULL_URL,
                headers = mapOf("Authorization" to "Bearer ${AccountUtils.currentUser?.apiToken?.accessToken}"),
                urlToQuit = URL_REDIRECT_SUCCESSFUL_ACCOUNT_DELETION,
                activityResultLauncher = resultActivityResultLauncher,
            )
        }
    }

    companion object {
        private const val URL_REDIRECT_SUCCESSFUL_ACCOUNT_DELETION = "login.infomaniak.com"
        private const val TERMINATE_ACCOUNT_FULL_URL = "$AUTOLOG_URL/?url=$TERMINATE_ACCOUNT_URL"
    }
}
