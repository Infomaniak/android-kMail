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

import android.graphics.drawable.InsetDrawable
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.cache.mailboxContent.DraftController
import com.infomaniak.mail.data.cache.mailboxContent.ThreadController
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentMessagesBinding
import com.infomaniak.mail.ui.main.MainViewModel
import com.infomaniak.mail.ui.main.thread.MessagesFragment.QuickActionButton.*
import com.infomaniak.mail.utils.ModelsUtils.getFormattedThreadSubject
import com.infomaniak.mail.utils.context
import com.infomaniak.mail.utils.notYetImplemented
import com.infomaniak.mail.utils.toSharedFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.infomaniak.lib.core.R as RCore

class MessagesFragment : Fragment() {

    private val navigationArgs: MessagesFragmentArgs by navArgs()

    private val mainViewModel: MainViewModel by viewModels()

    private lateinit var binding: FragmentMessagesBinding
    private var messagesAdapter = MessagesAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentMessagesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        setupAdapter()
        listenToMessages()
    }

    private fun setupUi() = with(binding) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        threadSubject.text = navigationArgs.threadSubject.getFormattedThreadSubject(requireContext())
        iconFavorite.isVisible = navigationArgs.threadIsFavorite

        quickActionBar.setOnItemClickListener {
            val action = values()[it]
            when (action) {
                ANSWER -> notYetImplemented()
                TRANSFER -> notYetImplemented()
                ARCHIVE -> notYetImplemented()
                DELETE -> notYetImplemented()
                PLUS -> notYetImplemented()
            }
        }

        AppCompatResources.getDrawable(context, R.drawable.divider)?.let {
            val margin = resources.getDimensionPixelSize(RCore.dimen.marginStandardSmall)
            val divider = InsetDrawable(it, margin, 0, margin, 0)
            messagesList.addItemDecoration(DividerItemDecorator(divider))
        }
    }

    private fun setupAdapter() = with(binding) {
        messagesList.adapter = messagesAdapter.apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            onContactClicked = { contact ->
                safeNavigate(
                    MessagesFragmentDirections.actionMessagesFragmentToContactFragment(
                        contact.name,
                        contact.email,
                    )
                )
            }
            onDraftClicked = { message ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val parentUid = message.uid
                    // TODO: There shouldn't be any Api call in fragments. Move this in the ViewModel.
                    val draft = ApiRepository.getDraft(message.draftResource).data?.apply {
                        initLocalValues(parentUid)
                        // TODO: Remove this `forEachIndexed` when we have EmbeddedObjects
                        attachments.forEachIndexed { index, attachment -> attachment.initLocalValues(index, parentUid) }
                        DraftController.upsertDraft(this)
                    }
                    message.setDraftId(draft?.uuid)
                    // TODO: Open the draft in draft editor
                }
            }
            onDeleteDraftClicked = { message ->
                // TODO: Replace MailboxContentController with MailApi one when currentMailbox will be available
                mainViewModel.deleteDraft(message)
                // TODO: Delete Body & Attachments too. When they'll be EmbeddedObject, they should delete by themself automatically.
            }
        }
    }

    private fun listenToMessages() = lifecycleScope.launch(Dispatchers.IO) {
        ThreadController.getThreadAsync(navigationArgs.threadUid).firstOrNull()?.obj?.let { thread ->
            mainViewModel.openThread(thread)
            thread.messages.asFlow().toSharedFlow().collect {
                withContext(Dispatchers.Main) { displayMessages(it.list) }
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

        messagesAdapter.notifyAdapter(messages.toMutableList())
        binding.messagesList.scrollToPosition(messagesAdapter.lastIndex())
    }

    // Do not change the order of the enum, it's important
    enum class QuickActionButton {
        ANSWER,
        TRANSFER,
        ARCHIVE,
        DELETE,
        PLUS,
    }
}
