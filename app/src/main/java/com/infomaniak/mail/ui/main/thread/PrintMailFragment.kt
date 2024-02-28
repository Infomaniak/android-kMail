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
package com.infomaniak.mail.ui.main.thread

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.get
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentPrintMailBinding
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadAdapterCallbacks
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PrintMailFragment : Fragment() {

    private var binding: FragmentPrintMailBinding by safeBinding()

    private val navigationArgs: PrintMailFragmentArgs by navArgs()
    private val threadViewModel: ThreadViewModel by viewModels()
    private val printMailViewModel: PrintMailViewModel by viewModels()
    private val threadAdapter inline get() = binding.messagesList.adapter as ThreadAdapter

    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPrintMailBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(navigationArgs) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()

        threadViewModel.messagesLive.observe(viewLifecycleOwner) { (items, _) ->
            threadAdapter.submitList(items)
        }

        openThreadUid?.let {
            threadViewModel.reassignMessagesLive(it, messageUid, withSuperCollapsedBlock = false)
        }
    }

    private fun setupAdapter() {
        binding.messagesList.adapter = ThreadAdapter(
            shouldLoadDistantResources = true,
            isForPrinting = true,
            threadAdapterState = object : ThreadAdapterState {
                override var isExpandedMap by threadViewModel::isExpandedMap
                override var isThemeTheSameMap by threadViewModel::isThemeTheSameMap
                override var hasSuperCollapsedBlockBeenClicked by threadViewModel::hasSuperCollapsedBlockBeenClicked
                override var verticalScroll by threadViewModel::verticalScroll
            },
            threadAdapterCallbacks = ThreadAdapterCallbacks(
                onBodyWebViewFinishedLoading = { startPrintingView() },
            ),
        )
    }

    private fun startPrintingView() {
        printMailViewModel.startPrintingService(
            activityContext = requireActivity(),
            subject = (threadAdapter.items.single() as Message).subject,
            webView = getWebViewToPrint(),
            onFinish = findNavController()::popBackStack,
        )
    }

    private fun getWebViewToPrint(): WebView = with(binding.messagesList[0]) { findViewById(R.id.bodyWebView) }
}
