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
package com.infomaniak.mail.ui.main.thread

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.R
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.api.MailApi
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentThreadBinding
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import com.infomaniak.mail.utils.context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadFragment : Fragment() {

    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val threadViewModel: ThreadViewModel by viewModels()

    private lateinit var binding: FragmentThreadBinding
    private var threadAdapter = ThreadAdapter()

    private var messagesJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentThreadBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()
        threadViewModel.setup()
    }

    private fun setupUi() = with(binding) {
        setupAdapter()
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        threadSubject.text = navigationArgs.threadSubject.getFormattedThreadSubject(requireContext())
        iconFavorite.isVisible = navigationArgs.threadIsFavorite

        AppCompatResources.getDrawable(context, R.drawable.divider)?.let {
            messagesList.addItemDecoration(DividerItemDecorator(it))
        }
    }

    private fun setupAdapter() = with(binding) {
        messagesList.adapter = threadAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            onContactClicked = { contact, isExpanded ->
                if (isExpanded) {
                    safeNavigate(ThreadFragmentDirections.actionThreadFragmentToContactFragment(contact.name, contact.email))
                }
            }
            onDraftClicked = { message ->
                threadViewModel.viewModelScope.launch(Dispatchers.IO) {
                    val draft = MailApi.fetchDraft(message.draftResource, message.uid)
                    message.setDraftId(draft?.uuid)
                    // TODO: Open the draft in draft editor
                }
            }
            onDeleteDraftClicked = { message ->
                // TODO: Replace MailboxContentController with MailApi one when currentMailbox will be available
                lifecycleScope.launch(Dispatchers.IO) {
                    MailData.deleteDraft(message)
                    // TODO: Delete Body & Attachments too. When they'll be EmbeddedObject, they should delete by themself automatically.
                }
                threadAdapter.removeMessage(message)
            }
        }
    }

    override fun onResume() {
        super.onResume()

        listenToMessages()
        threadViewModel.loadMessages(navigationArgs.threadUid)
    }

    override fun onPause() {
        messagesJob?.cancel()
        super.onPause()
    }

    private fun listenToMessages() {
        with(threadViewModel) {
            messagesJob?.cancel()
            messagesJob = viewModelScope.launch(Dispatchers.Main) {
                uiMessagesFlow.filterNotNull().collect(::displayMessages)
            }
        }
    }

    private fun displayMessages(messages: List<Message>) {
        Log.i("UI", "Received messages (${messages.size})")

        // messages.forEach {
        //     val displayedBody = with(it.body?.value) {
        //         this?.length?.let { length -> if (length > 42) this.substring(0, 42) else this } ?: this
        //     }
        //     Log.v("UI", "Message: ${it.from.firstOrNull()?.email} | ${it.attachments.size}")// | $displayedBody")
        // }

        threadAdapter.notifyAdapter(messages.toMutableList())
        binding.messagesList.scrollToPosition(threadAdapter.itemCount - 1)
    }
}
