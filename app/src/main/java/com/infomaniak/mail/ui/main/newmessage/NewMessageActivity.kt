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
package com.infomaniak.mail.ui.main.newmessage

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityNewMessageBinding

class NewMessageActivity : AppCompatActivity() {

    private val binding: ActivityNewMessageBinding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val viewModel: NewMessageViewModel by viewModels()
    private val newMessageFragment = NewMessageFragment()

    override fun onCreate(savedInstanceState: Bundle?): Unit = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)

        toolbar.setNavigationOnClickListener { onBackPressed() }
        toolbar.setOnMenuItemClickListener {
            if (sendMail()) finish()
            true
        }

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, newMessageFragment)
            }
        }
    }

    private fun sendMail(): Boolean {
        // TODO : Replace logs with actual API call
        Log.d("sendingMail", "FROM: ${newMessageFragment.getFromMailbox().email}")
        Log.d("sendingMail", "TO: ${viewModel.recipients.map { it.name }}")
        Log.d("sendingMail", "SUBJECT: ${newMessageFragment.getSubject()}")
        Log.d("sendingMail", "BODY: ${newMessageFragment.getBody()}")

        if (viewModel.recipients.isEmpty() || newMessageFragment.getSubject().isBlank()) return false
        return true
    }
}
