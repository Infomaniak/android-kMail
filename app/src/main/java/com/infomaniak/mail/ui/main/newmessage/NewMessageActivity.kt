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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.databinding.ActivityNewMessageBinding

class NewMessageActivity : AppCompatActivity() {

    private val binding: ActivityNewMessageBinding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val mails = MailboxInfoController.getMailboxesSync().map { it.email }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)

        fromMailAddress.text = MailData.currentMailbox?.email
        fromMailAddress.setOnClickListener { selectAddress() }

        chevron.setOnClickListener { /*TODO toggleChevron()*/ advancedFields.isVisible = !advancedFields.isVisible }
    }

    private fun selectAddress() {
    }
}
