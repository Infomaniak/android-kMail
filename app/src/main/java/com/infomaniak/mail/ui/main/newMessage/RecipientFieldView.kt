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
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ChipContactBinding
import com.infomaniak.mail.databinding.ViewRecipientFieldBinding
import com.infomaniak.mail.utils.isEmail
import com.infomaniak.mail.utils.toggleChevron

class RecipientFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val binding by lazy { ViewRecipientFieldBinding.inflate(LayoutInflater.from(context), this, true) }

    private var contactAdapter: ContactAdapter? = null
    private val recipients = mutableSetOf<Recipient>()

    private var onAutoCompletionToggled: ((hasOpened: Boolean) -> Unit)? = null
    private var onToggle: ((isCollapsed: Boolean) -> Unit)? = null
    private var onFocusNext: (() -> Unit)? = null
    private var onFocusPrevious: (() -> Unit)? = null
    private var onContactRemoved: ((Recipient) -> Unit)? = null
    private var onContactAdded: ((Recipient) -> Unit)? = null

    private var isToggleable = false
    private var isCollapsed = true
        set(value) {
            if (value == field) return
            field = value
            updateCollapsedUiState(value)
        }

    private lateinit var autoCompletedContacts: RecyclerView

    private var isAutoCompletionOpened
        get() = autoCompletedContacts.isVisible
        set(value) {
            autoCompletedContacts.isVisible = value
            binding.chevron.isGone = value || !isToggleable
        }

    // TODO : Think about the textfield focus rather than the linearLayout focus
    // override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
    //     Log.e("gibran", "onFocusChanged: ${binding.prefix.text}, gainFocus: $gainFocus", );
    //     super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    //     // binding.autocompleteInput.requestFocus()
    // }
    //
    // override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
    //     Log.e("gibran", "onRequestFocusInDescendants: ${binding.prefix.text}", );
    //     return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    // }
    //
    // override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
    //     Log.e("gibran", "onWindowFocusChanged: ${binding.prefix.text}, hasWindowFocus: $hasWindowFocus", );
    //     super.onWindowFocusChanged(hasWindowFocus)
    // }

    init {
        // isFocusable = true
        // isClickable = true

        with(binding) {
            attrs?.getAttributes(context, R.styleable.RecipientFieldView) {
                prefix.text = getText(R.styleable.RecipientFieldView_title)
                isToggleable = getBoolean(R.styleable.RecipientFieldView_toggleable, isToggleable)

                // autocompleteInput.nextFocusForwardId = getResourceId(R.styleable.RecipientFieldView_nextFocusForward, NO_ID)
                // autocompleteInput.nextFocusDownId = getResourceId(R.styleable.RecipientFieldView_nextFocusForward, NO_ID)
            }

            chevron.isVisible = isToggleable

            if (isToggleable) {
                chevron.setOnClickListener {
                    isCollapsed = !isCollapsed
                    if (isCollapsed) autoCompleteInput.hideKeyboard()
                }

                plusChip.setOnClickListener {
                    isCollapsed = !isCollapsed
                }

                transparentButton.setOnClickListener {
                    isCollapsed = !isCollapsed
                    autoCompleteInput.showKeyboard()
                }

                singleChip.root.setOnClickListener {
                    removeRecipient(recipients.first())
                    updateCollapsedChipValues(isCollapsed)
                }
            }

            if (isInEditMode) {
                singleChip.root.isVisible = isToggleable
                plusChip.isVisible = isToggleable
            }
        }
    }

    private fun updateCollapsedUiState(isCollapsed: Boolean) = with(binding) {
        chevron.toggleChevron(isCollapsed)

        updateCollapsedChipValues(isCollapsed)
        itemsChipGroup.isGone = isCollapsed

        onToggle?.invoke(isCollapsed)
    }

    private fun updateCollapsedChipValues(isCollapsed: Boolean) = with(binding) {
        val isTextInputAccessible = !isCollapsed || recipients.isEmpty()

        singleChip.root.apply {
            isGone = isTextInputAccessible
            text = recipients.firstOrNull()?.getNameOrEmail() ?: ""
        }
        plusChip.apply {
            isGone = !isCollapsed || recipients.count() <= 1
            text = "+${recipients.count() - 1}"
        }

        transparentButton.isGone = isTextInputAccessible
        autoCompleteInput.isVisible = isTextInputAccessible
    }

    fun updateContacts(autoComplete: RecyclerView?, allContacts: List<MergedContact>, usedContacts: MutableSet<String>) {
        with(binding) {
            // TODO : Init once but update mutiple times
            // TODO
            // TODO
            // TODO
            contactAdapter = ContactAdapter(
                allContacts = allContacts,
                usedContacts = usedContacts,
                onContactClicked = { addRecipient(it.email, it.name) },
                onAddUnrecognizedContact = {
                    val input = autoCompleteInput.text.toString().trim().lowercase()
                    if (input.isEmail() && contactAdapter!!.addUsedContact(input)) addRecipient(email = input, name = input)
                }
            )

            // TODO : Do once
            autoComplete?.let {
                autoCompletedContacts = it
                autoCompletedContacts.adapter = contactAdapter!!
            }

            // TODO : Do once
            autoCompleteInput.apply {
                doOnTextChanged { text, _, _, _ ->
                    if (text?.isNotEmpty() == true) {
                        if ((text.trim().count()) > 0) contactAdapter!!.filterField(text) else contactAdapter!!.clear()
                        if (!isAutoCompletionOpened) openAutoCompletion()
                    } else if (isAutoCompletionOpened) {
                        closeAutoCompletion()
                    }
                }

                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE && autoCompleteInput.text.isNotBlank()) {
                        contactAdapter!!.addFirstAvailableItem()
                    }
                    // if (actionId == EditorInfo.IME_ACTION_NEXT) onFocusNext?.invoke()
                    // if (actionId == EditorInfo.IME_ACTION_PREVIOUS) onFocusPrevious?.invoke()
                    true // Keep keyboard open
                }
            }
        }
    }

    private fun openAutoCompletion() {
        isAutoCompletionOpened = true
        onAutoCompletionToggled?.invoke(isAutoCompletionOpened)
    }

    private fun closeAutoCompletion() {
        isAutoCompletionOpened = false
        onAutoCompletionToggled?.invoke(isAutoCompletionOpened)
    }

    private fun addRecipient(email: String, name: String) {
        if (recipients.isEmpty()) isCollapsed = false
        val recipient = Recipient().initLocalValues(email, name)
        val recipientIsNew = recipients.add(recipient)
        if (recipientIsNew) {
            createChip(recipient)
            onContactAdded?.invoke(recipient)
            clearField()
        }
    }

    private fun createChip(recipient: Recipient) {
        ChipContactBinding.inflate(LayoutInflater.from(context)).root.apply {
            text = recipient.getNameOrEmail()
            setOnClickListener { removeRecipient(recipient) }
            binding.itemsChipGroup.addView(this)
        }
    }

    private fun removeRecipient(recipient: Recipient) = with(binding) {
        val index = recipients.indexOf(recipient)
        contactAdapter?.removeEmail(recipient.email)
        val successfullyRemoved = recipients.remove(recipient)
        if (successfullyRemoved) {
            itemsChipGroup.removeViewAt(index)
            onContactRemoved?.invoke(recipient)
        }
    }

    fun onAutoCompletionToggled(callback: (hasOpened: Boolean) -> Unit) {
        onAutoCompletionToggled = callback
    }

    // fun onFocusNext(callback: () -> Unit) {
    //     onFocusNext = callback
    // }
    //
    // fun onFocusPrevious(callback: () -> Unit) {
    //     onFocusPrevious = callback
    // }

    fun setOnToggleListener(listener: ((isCollapsed: Boolean) -> Unit)?) {
        onToggle = listener
    }

    fun clearField() {
        binding.autoCompleteInput.setText("")
    }

    fun onContactRemoved(callback: ((Recipient) -> Unit)) {
        onContactRemoved = callback
    }

    fun onContactAdded(callback: ((Recipient) -> Unit)) {
        onContactAdded = callback
    }

    fun initRecipients(initialRecipients: List<Recipient>) {
        recipients.addAll(initialRecipients)
        updateCollapsedChipValues(isCollapsed)
    }
}
