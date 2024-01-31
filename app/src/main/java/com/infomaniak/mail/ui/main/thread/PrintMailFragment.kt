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

import android.content.Context
import android.os.Bundle
import android.print.PrintManager
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
import com.infomaniak.mail.databinding.FragmentPrintMailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PrintMailFragment : Fragment() {

    private var binding: FragmentPrintMailBinding by safeBinding()

    private val navigationArgs: PrintMailFragmentArgs by navArgs()
    private val threadViewModel: ThreadViewModel by viewModels()
    private val threadAdapter inline get() = binding.messagesList.adapter as ThreadAdapter


    @Inject
    lateinit var localSettings: LocalSettings

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPrintMailBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(threadViewModel) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapter()
        messagesLive.observe(viewLifecycleOwner) { messages ->
            threadAdapter.submitList(messages.filter { it.uid == navigationArgs.messageUid })
        }
        navigationArgs.openThreadUid?.let {
            reassignThreadLive(it)
            reassignMessagesLive(it)
        }
    }

    override fun onPause() {
        super.onPause()
        findNavController().popBackStack()
    }

    private fun setupAdapter() = with(binding.messagesList) {
        adapter = ThreadAdapter(
            shouldLoadDistantResources = true,
            isForPrinting = true,
            ThreadAdapter.ThreadAdapterCallbacks(
                onBodyWebViewFinishedLoading = { startPrintingView() },
            ),
        )
    }

    private fun startPrintingView() {
        CoroutineScope(Dispatchers.Main).launch {
            threadViewModel.threadLive.value?.subject?.let { subject ->
                val webViewPrintAdapter = getWebViewToPrint().createPrintDocumentAdapter(subject)
                val printManager = requireContext().getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(subject, webViewPrintAdapter, null)
            }
        }
    }

    private fun getWebViewToPrint(): WebView = with(binding.messagesList[0]) { findViewById(R.id.bodyWebView) }
}
