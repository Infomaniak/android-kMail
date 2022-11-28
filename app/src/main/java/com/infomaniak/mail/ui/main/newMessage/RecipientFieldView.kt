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
package com.infomaniak.mail.ui.main.newMessage

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.ViewRecipientFieldBinding

class RecipientFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewRecipientFieldBinding.inflate(LayoutInflater.from(context), this, true) }

    private var contactAdapter: ContactAdapter2? = null
    private val recipients = mutableSetOf<Recipient>()
    private var onAutoCompletionToggled: ((hasOpened: Boolean) -> Unit)? = null

    private var isAutocompletionOpened
        get() = binding.autoCompletedContacts.isVisible
        set(value) {
            binding.autoCompletedContacts.isVisible = value
        }

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.RecipientFieldView) {
                prefix.text = getText(R.styleable.RecipientFieldView_title)
            }
        }
    }

    fun initContacts(allContacts: List<MergedContact>, usedContacts: MutableList<String>) = with(binding) {
        Log.e("gibran", "initContacts: Initializing with contacts: $allContacts")

        contactAdapter = ContactAdapter2(
            allContacts = allContacts,
            usedContacts = usedContacts,
            onContactClicked = {
                addContact(it)
                autocompleteInput.setText("")
            },
            onAddUnrecognizedContact = {
                TODO()
            }
        )

        autoCompletedContacts.adapter = contactAdapter!!

        autocompleteInput.apply {
            doOnTextChanged { text, _, _, _ ->
                Log.e("gibran", "doOnTextChanged - text: ${text}")
                if (text?.isNotEmpty() == true) {
                    if ((text.trim().count()) > 0) contactAdapter!!.filterField(text) else contactAdapter!!.clear()
                    if (!isAutocompletionOpened) openAutoCompletion()
                } else if (isAutocompletionOpened) {
                    closeAutoCompletion()
                }
            }

            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) contactAdapter!!.addFirstAvailableItem()
                true // Keep keyboard open
            }
        }
    }

    private fun openAutoCompletion() {
        isAutocompletionOpened = true
        onAutoCompletionToggled?.invoke(isAutocompletionOpened)
    }

    private fun closeAutoCompletion() {
        isAutocompletionOpened = false
        onAutoCompletionToggled?.invoke(isAutocompletionOpened)
    }

    private fun addContact(contact: MergedContact) {
        val recipient = Recipient().initLocalValues(contact.email, contact.name)
        val recipientIsNew = recipients.add(recipient)
        if (recipientIsNew) createChip(recipient)
    }

    private fun createChip(recipient: Recipient) {
        ChipContactBinding.inflate(LayoutInflater.from(context)).root.apply {
            text = recipient.getNameOrEmail()
            setOnClickListener { removeRecipient(recipient) }
            binding.itemsChipGroup.addView(this)
        }
    }

    private fun removeRecipient(recipient: Recipient) {
        val index = recipients.indexOf(recipient)
        contactAdapter?.removeEmail(recipient.email)
        val successfullyRemoved = recipients.remove(recipient)
        if (successfullyRemoved) binding.itemsChipGroup.removeViewAt(index)
    }

    fun onAutoCompletionToggled(callback: (hasOpened: Boolean) -> Unit) {
        onAutoCompletionToggled = callback
    }

    fun clearField() {
        binding.autocompleteInput.setText("")
    }

    // TODO : fun onContactRemoved() {}

    // TODO : fun onContactAdded() {}

    // fun getRecipients(): Set<Recipient> = recipients
}
