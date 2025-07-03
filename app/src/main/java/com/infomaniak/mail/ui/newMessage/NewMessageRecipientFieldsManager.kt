/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2025 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType.BCC
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType.CC
import com.infomaniak.mail.ui.newMessage.NewMessageRecipientFieldsManager.FieldType.TO
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionMessageManager
import com.infomaniak.mail.utils.extensions.copyRecipientEmailToClipboard
import dagger.hilt.android.scopes.FragmentScoped
import javax.inject.Inject

@FragmentScoped
class NewMessageRecipientFieldsManager @Inject constructor(private val snackbarManager: SnackbarManager) : NewMessageManager() {

    private var _externalsManager: NewMessageExternalsManager? = null
    private inline val externalsManager: NewMessageExternalsManager get() = _externalsManager!!
    private var _encryptionManager: EncryptionMessageManager? = null
    private inline val encryptionManager: EncryptionMessageManager get() = _encryptionManager!!

    private var lastFieldToTakeFocus: FieldType? = TO

    fun initValues(
        newMessageViewModel: NewMessageViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
        externalsManager: NewMessageExternalsManager,
        encryptionMessageManager: EncryptionMessageManager,
    ) {
        super.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = fragment,
            freeReferences = {
                _externalsManager = null
                _encryptionManager = null
            },
        )

        _externalsManager = externalsManager
        _encryptionManager = encryptionMessageManager
    }

    fun setupAutoCompletionFields() = with(binding) {
        toField.initRecipientField(
            autoComplete = autoCompleteTo,
            callBackRecipientField = RecipientFieldView.callBackRecipientField(
                onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(TO, hasOpened) },
                onContactAddedCallback = { recipient -> onContactAdded(recipient, TO) },
                onContactRemovedCallback = { recipient -> onContactRemoved(recipient, TO) },
                onCopyContactAddressCallback = { fragment.copyRecipientEmailToClipboard(it, snackbarManager) },
                gotFocusCallback = { fieldGotFocus(TO) },
                onToggleEverythingCallback = ::openAdvancedFields,
                getAddressBookWithGroupCallback = ::getAddressBookWithGroup,
                getMergedContactFromContactGroupCallback = ::getMergedContactFromContactGroup,
                getGroupFromAdressBookCallback = ::getGroupFromAdressBook,
            ),
        )

        ccField.initRecipientField(
            autoComplete = autoCompleteCc,
            callBackRecipientField = RecipientFieldView.callBackRecipientField(
                onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(CC, hasOpened) },
                onContactAddedCallback = { recipient -> onContactAdded(recipient, CC) },
                onContactRemovedCallback = { recipient -> onContactRemoved(recipient, CC) },
                onCopyContactAddressCallback = { fragment.copyRecipientEmailToClipboard(it, snackbarManager) },
                gotFocusCallback = { fieldGotFocus(CC) },
                getAddressBookWithGroupCallback = ::getAddressBookWithGroup,
                getMergedContactFromContactGroupCallback = ::getMergedContactFromContactGroup,
                getGroupFromAdressBookCallback = ::getGroupFromAdressBook,
            )
        )

        bccField.initRecipientField(
            autoComplete = autoCompleteBcc,
            callBackRecipientField = RecipientFieldView.callBackRecipientField(
                onAutoCompletionToggledCallback = { hasOpened -> toggleAutoCompletion(BCC, hasOpened) },
                onContactAddedCallback = { recipient -> onContactAdded(recipient, BCC) },
                onContactRemovedCallback = { recipient -> onContactRemoved(recipient, BCC) },
                onCopyContactAddressCallback = { fragment.copyRecipientEmailToClipboard(it, snackbarManager) },
                gotFocusCallback = { fieldGotFocus(BCC) },
                getAddressBookWithGroupCallback = ::getAddressBookWithGroup,
                getMergedContactFromContactGroupCallback = ::getMergedContactFromContactGroup,
                getGroupFromAdressBookCallback = ::getGroupFromAdressBook,
            )
        )
    }

    private fun onContactAdded(recipient: Recipient, fieldType: FieldType) {
        newMessageViewModel.addRecipientToField(recipient, fieldType)
        if (newMessageViewModel.isEncryptionActivated.value == true) encryptionManager.checkRecipientEncryptionStatus(recipient)
    }

    private fun onContactRemoved(recipient: Recipient, fieldType: FieldType) {
        recipient.removeInViewModelAndUpdateBannerVisibility(fieldType)
        encryptionManager.removeUnencryptableRecipient(recipient)
    }

    private fun getAddressBookWithGroup(contactGroup: ContactGroup): AddressBook? {
        return newMessageViewModel.getAddressBookWithName(contactGroup)
    }

    private fun getMergedContactFromContactGroup(contactGroup: ContactGroup): List<MergedContact> {
        return newMessageViewModel.getMergedContactFromContactGroup(contactGroup)
    }

    private fun getGroupFromAdressBook(addressBook: AddressBook): List<ContactGroup> {
        return newMessageViewModel.getGroupFromAdressBook(addressBook)
    }

    private fun toggleAutoCompletion(field: FieldType? = null, isAutoCompletionOpened: Boolean) = with(binding) {
        preFields.isGone = isAutoCompletionOpened
        to.isVisible = !isAutoCompletionOpened || field == TO
        cc.isVisible = !isAutoCompletionOpened || field == CC
        bcc.isVisible = !isAutoCompletionOpened || field == BCC
        postFields.isGone = isAutoCompletionOpened

        newMessageViewModel.isAutoCompletionOpened = isAutoCompletionOpened
    }

    private fun Recipient.removeInViewModelAndUpdateBannerVisibility(type: FieldType) {
        newMessageViewModel.removeRecipientFromField(recipient = this, type)
        externalsManager.updateBannerVisibility()
    }

    private fun fieldGotFocus(field: FieldType?) = with(binding) {
        if (lastFieldToTakeFocus == field) return

        if (field == null && newMessageViewModel.otherRecipientsFieldsAreEmpty.value == true) {
            toField.collapseEverything()
        } else {
            if (field != TO) toField.collapse()
            if (field != CC) ccField.collapse()
            if (field != BCC) bccField.collapse()
        }

        lastFieldToTakeFocus = field
    }

    private fun openAdvancedFields(isCollapsed: Boolean) = with(binding) {
        cc.isGone = isCollapsed
        bcc.isGone = isCollapsed
    }

    fun observeCcAndBccVisibility() = with(newMessageViewModel) {
        otherRecipientsFieldsAreEmpty.observe(viewLifecycleOwner, binding.toField::updateOtherRecipientsFieldsVisibility)
        initializeFieldsAsOpen.observe(viewLifecycleOwner) { openAdvancedFields(isCollapsed = !it) }
    }

    fun setOnFocusChangedListeners() = with(newMessageViewModel) {
        binding.subjectTextField.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) fieldGotFocus(field = null) }
        binding.editorWebView.setOnFocusChangeListener { _, hasFocus -> isEditorWebViewFocusedLiveData.value = hasFocus }

        isEditorWebViewFocusedLiveData.observe(viewLifecycleOwner) { hasFocus -> if (hasFocus) fieldGotFocus(field = null) }
    }

    fun focusBodyField() {
        // TODO: Make it so keyboard is kept open through configuration changes whenever the editor gets focused
        binding.editorWebView.requestFocusAndOpenKeyboard()
    }

    fun focusToField() {
        binding.toField.showKeyboardInTextInput()
    }

    fun observeContacts() = with(binding) {
        newMessageViewModel.mergedContacts.observe(viewLifecycleOwner) { (sortedContactList, _) ->
            toField.updateContacts(sortedContactList)
            ccField.updateContacts(sortedContactList)
            bccField.updateContacts(sortedContactList)
        }
    }

    fun closeAutoCompletion() = with(binding) {
        toField.clearField()
        ccField.clearField()
        bccField.clearField()
    }

    enum class FieldType {
        TO,
        CC,
        BCC,
    }
}
