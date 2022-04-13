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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.startCalls).setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {

                val getMailboxes = ApiRepository.getMailboxes()
                Log.e("TOTO", "getMailboxes: $getMailboxes")

                val mailbox = getMailboxes.data!!.first()
                val getFolders = ApiRepository.getFolders(mailbox)
                Log.e("TOTO", "getFolders: $getFolders")

                val folder = getFolders.data!![2]
                val getThreads = ApiRepository.getThreads(mailbox, folder, null)
                Log.e("TOTO", "getThreads: $getThreads")

                val message = getThreads.data!!.threads.first().messages.first()
                val getMessage = ApiRepository.getMessage(message)
                Log.e("TOTO", "getMessage: $getMessage")

                val getQuotas = ApiRepository.getQuotas(mailbox)
                Log.e("TOTO", "getQuotas: $getQuotas")
            }
        }
    }
}