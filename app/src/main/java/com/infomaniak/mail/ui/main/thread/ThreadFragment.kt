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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.mail.databinding.FragmentThreadBinding
import kotlinx.coroutines.launch

class ThreadFragment : Fragment() {

    private val navigationArgs: ThreadFragmentArgs by navArgs()
    private val threadViewModel: ThreadViewModel by viewModels()
    private lateinit var binding: FragmentThreadBinding
    private lateinit var threadAdapter: ThreadAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentThreadBinding.inflate(inflater, container, false).also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initUI()
        listenToChanges()
        threadViewModel.getMessages(navigationArgs.threadUid)
    }

    private fun initUI() {
        with(binding) {
            messagesList.adapter = ThreadAdapter().also { threadAdapter = it }
            backButton.setOnClickListener { findNavController().popBackStack() }
            threadTitle.text = navigationArgs.threadSubject
        }
    }

    private fun listenToChanges() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                threadViewModel.messages.collect { messages ->

                    Log.i("UI", "Received messages (${messages.size})")
                    messages.forEach { Log.v("UI", "Sender: ${it.from.firstOrNull()?.email}") }

                    if (messages.isNotEmpty()) {
                        threadAdapter.notifyAdapter(ArrayList(messages))
                    }
                }
            }
        }
    }
}
