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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.chip.Chip
import com.infomaniak.mail.data.MailData
import com.infomaniak.mail.data.cache.MailboxInfoController
import com.infomaniak.mail.data.models.Contact
import com.infomaniak.mail.databinding.ActivityNewMessageBinding
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.utils.setupAvailableContactItems
import com.infomaniak.mail.utils.toggleChevron
import io.realm.kotlin.ext.realmListOf

class NewMessageActivity : AppCompatActivity() {

    private val binding: ActivityNewMessageBinding by lazy { ActivityNewMessageBinding.inflate(layoutInflater) }
    private val mails = MailboxInfoController.getMailboxesSync().map { it.email }

    private val viewModel: NewMessageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?): Unit = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)

        toolbar.setNavigationOnClickListener { onBackPressed() }

        fromMailAddress.text = MailData.currentMailbox?.email
        fromMailAddress.setOnClickListener { selectAddress() }


        for (recipient in viewModel.recipients) createChip(recipient)

        val cont1 = Contact().apply { name = "Gibran"; emails = realmListOf("gib@ran.com"); id = "22132021" }
        val cont2 = Contact().apply { name = "Kevin"; emails = realmListOf("kev@in.com"); id = "233871341" }
        val cont3 = Contact().apply { name = "Fabian"; emails = realmListOf("Fab@ian.com"); id = "295232131" }
        toAutocompleteInput.setupAvailableContactItems(this@NewMessageActivity, arrayListOf(cont1, cont2, cont3)) {
            toAutocompleteInput.setText("")
            addRecipient(it)
        }

        chevron.setOnClickListener {
            advancedFields.isVisible = !advancedFields.isVisible
            chevron.toggleChevron(advancedFields.isGone)
        }
    }

    private fun ActivityNewMessageBinding.addRecipient(contact: Contact) {
        viewModel.recipients.add(contact)
        Log.e("gibran", "addRecipient - The value viewModel.recipients is: ${viewModel.recipients}")
        // TODO : Prevent from reusing same email twice
        
        createChip(contact).setOnClickListener {
            viewModel.recipients.remove(contact)
//            availableUsersAdapter.alreadyUsedContactIds.remove(contact.id) // TODO : Remove email from list of forbbiden emails
            toItemsChipGroup.removeView(it)
        }
    }

    private fun ActivityNewMessageBinding.createChip(contact: Contact): Chip {
        val chip = ChipContactBinding.inflate(layoutInflater).root.apply { text = contact.name }
        toItemsChipGroup.addView(chip)
        return chip
    }

    private fun selectAddress() {
    }
}
