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
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Mailbox
import com.infomaniak.mail.data.models.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startCalls).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) { getMailboxes() }
        }
    }

    private fun getMailboxes() {
        val getMailboxes = ApiRepository.getMailboxes()
        Log.d("API", "getMailboxes: $getMailboxes")

        getMailboxes.data?.first()?.let { mailbox ->
            getQuotas(mailbox)
            getFolders(mailbox)
        }
    }

    private fun getQuotas(mailbox: Mailbox) {
        val getQuotas = ApiRepository.getQuotas(mailbox)
        Log.d("API", "getQuotas: $getQuotas")
    }

    private fun getFolders(mailbox: Mailbox) {
        val getFolders = ApiRepository.getFolders(mailbox)
        Log.d("API", "getFolders: $getFolders")

        getFolders.data?.get(2)?.let { getThreads(mailbox, it) }
    }

    private fun getThreads(mailbox: Mailbox, folder: Folder) {
        val getThreads = ApiRepository.getThreads(mailbox, folder, null)
        Log.d("API", "getThreads: $getThreads")

        getThreads.data?.threads?.first()?.messages?.first()?.let(::getMessage)
    }

    private fun getMessage(message: Message) {
        val getMessage = ApiRepository.getMessage(message)
        Log.d("API", "getMessage: $getMessage")
    }
}