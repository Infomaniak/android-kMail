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
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.FragmentThreadBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ThreadFragment : Fragment() {

    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val threadViewModel: ThreadViewModel by viewModels()

    private lateinit var binding: FragmentThreadBinding
    private lateinit var threadAdapter: ThreadAdapter

    private var jobMessagesFromAPI: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentThreadBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()

        displayMessagesFromRealm()
        displayMessagesFromAPI()
    }

    private fun setupUi() {
        with(binding) {
            messagesList.adapter = ThreadAdapter().also { threadAdapter = it }
            backButton.setOnClickListener { findNavController().popBackStack() }
            threadTitle.text = navigationArgs.threadSubject
        }
    }

    private fun displayMessagesFromRealm() {
        val messages = with(threadViewModel) {
            messagesFromAPI.value = null
            getMessagesFromRealmThenFetchFromAPI(navigationArgs.threadUid)
        }
        displayMessages(messages)
    }

    private fun displayMessagesFromAPI() {
        if (jobMessagesFromAPI != null) jobMessagesFromAPI?.cancel()

        jobMessagesFromAPI = with(threadViewModel) {
            viewModelScope.launch(Dispatchers.Main) {
                messagesFromAPI.filterNotNull().collect { displayMessages(it) }
            }
        }
    }

    private fun displayMessages(messages: List<Message>) {
        Log.i("UI", "Received messages (${messages.size})")
        messages.forEach {
            val displayedBody = with(it.body?.value) {
                this?.length?.let { length -> if (length > 42) this.substring(0, 42) else this } ?: this
            }
            Log.v("UI", "Sender: ${it.from.firstOrNull()?.email} | $displayedBody")
        }

        threadAdapter.notifyAdapter(ArrayList(messages))
    }
}
