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
package com.infomaniak.mail.ui.main

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRepository
import com.infomaniak.mail.data.api.ApiRepository.deleteDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startCalls).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {

                val getMailboxes = ApiRepository.getMailboxes()
                Log.d("API", "getMailboxes: $getMailboxes")

                val mailbox = getMailboxes.data!!.first()
                val getFolders = ApiRepository.getFolders(mailbox)
                Log.d("API", "getFolders: $getFolders")

                val folder = getFolders.data!![2]
                val getThreads = ApiRepository.getThreads(mailbox, folder, null)
                Log.d("API", "getThreads: $getThreads")

                val message = getThreads.data!!.threads.first().messages.first()
                val getMessage = ApiRepository.getMessage(message)
                Log.d("API", "getMessage: $getMessage")

                val getQuotas = ApiRepository.getQuotas(mailbox)
                Log.e("API", "getQuotas: $getQuotas")

                val getAddressBooks = ApiRepository.getAddressBooks()
                Log.e("API", "getAddressBooks: $getAddressBooks")

                val getContacts = ApiRepository.getContacts()
                Log.e("API", "getContacts: $getContacts")

                val getUser = ApiRepository.getUser()
                Log.e("API", "getUser: $getUser")

                val getSignature = ApiRepository.getSignatures(mailbox)
                Log.e("API", "getSignature: $getSignature")

                val draftUuid = getThreads.data?.threads?.find { it.hasDrafts }?.messages?.find { it -> it.isDraft }?.uid ?: ""

                val draft = ApiRepository.getDraft(mailbox, draftUuid).data

                val emptyResponse = deleteDraft(mailbox, draftUuid)
            }
        }
    }
}