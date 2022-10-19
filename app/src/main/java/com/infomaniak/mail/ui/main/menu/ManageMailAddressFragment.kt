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
package com.infomaniak.mail.ui.main.menu

import android.app.Application
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.databinding.FragmentManageMailAddressBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.notYetImplemented
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ManageMailAddressFragment : Fragment() {

    private lateinit var binding: FragmentManageMailAddressBinding
    private val mainViewModel: MainViewModel by activityViewModels()
    private val manageMailAddressViewModel: ManageMailAddressViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentManageMailAddressBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        root.setNavigationOnClickListener { findNavController().popBackStack() }

        AccountUtils.currentUser?.let { user ->
            avatar.loadAvatar(user)
            mail.text = user.email
        }

        changeAccountButton.setOnClickListener { safeNavigate(ManageMailAddressFragmentDirections.actionManageMailAddressFragmentToSwitchUserFragment()) }
        associatedEmailAddresses.setOnClickListener { notYetImplemented() }
        disconnectAccountButton.setOnClickListener { logout() }
    }

    private fun logout() {
        mainViewModel.resetAllCurrentLiveData()
        manageMailAddressViewModel.removeCurrentUser()
    }

    class ManageMailAddressViewModel(application: Application) : AndroidViewModel(application) {
        fun removeCurrentUser() = viewModelScope.launch(Dispatchers.IO) {
            AccountUtils.removeUser(getApplication(), AccountUtils.currentUser!!)
        }
    }
}
