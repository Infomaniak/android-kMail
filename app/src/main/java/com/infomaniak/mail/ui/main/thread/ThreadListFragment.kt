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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.threads.Thread
import com.infomaniak.mail.databinding.FragmentThreadListBinding
import com.infomaniak.mail.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ThreadListFragment : DialogFragment() {

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var binding: FragmentThreadListBinding
    private lateinit var mailbox: Mailbox // TODO Replace with realm mailBox
    private val threadAdapter = ThreadListAdapter()


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentThreadListBinding.inflate(inflater, container, false)
        with(mainViewModel) {
            threadList = MutableLiveData()
            threadList.observe(viewLifecycleOwner) { list ->
                if (list?.threads != null) {
                    threadAdapter.apply {
                        clean()
                        context?.let { binding.threadList.post { addAll(formatList(list.threads, it)) } }
                    }
                }
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.threadList.adapter = threadAdapter
        lifecycleScope.launch(Dispatchers.IO) {
            mailbox = ApiRepository.getMailboxes().data!![0]
            with(mainViewModel) {
                val foldersResponse = ApiRepository.getFolders(mailbox).data!!
                folders.postValue(foldersResponse)
                val inboxFolder = foldersResponse.find { it.getRole() == Folder.FolderRole.INBOX }
                threadList.postValue(ApiRepository.getThreads(mailbox, inboxFolder!!, Thread.ThreadFilter.ALL).data)
            }
        }
        binding.openMultiselectButton.setOnClickListener {
            // TODO multiselection
        }
    }
}
