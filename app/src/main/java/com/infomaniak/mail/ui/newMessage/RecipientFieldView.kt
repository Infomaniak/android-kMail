/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.InsetDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.material.chip.Chip
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.lib.core.views.DividerItemDecorator
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.addressBook.AddressBook
import com.infomaniak.mail.data.models.addressBook.ContactGroup
import com.infomaniak.mail.data.models.correspondent.ContactAutocompletable
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ViewContactChipContextMenuBinding
import com.infomaniak.mail.databinding.ViewRecipientFieldBinding
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.newMessage.encryption.EncryptableView
import com.infomaniak.mail.ui.newMessage.encryption.EncryptionStatus
import com.infomaniak.mail.utils.ExternalUtils.ExternalData
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.extensions.isEmail
import com.infomaniak.mail.utils.extensions.toggleChevron
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.min
import com.infomaniak.lib.core.R as RCore

@AndroidEntryPoint
class RecipientFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), EncryptableView {

    private val binding by lazy { ViewRecipientFieldBinding.inflate(LayoutInflater.from(context), this, true) }
    private var contactAdapter: ContactAdapter
    private var contactChipAdapter: ContactChipAdapter

    override var isEncryptionActivated: Boolean = false
        set(value) {
            field = value
            applyEncryptionStyle()
        }
    override var unencryptableRecipients: Set<String>? = null
        set(value) {
            field = value
            contactChipAdapter.updateUnencryptableRecipients(value)
            setSingleChipStyle()
            setPlusChipStyle()
        }
    override var encryptionPassword: String = ""
        set(value) {
            field = value
            applyEncryptionStyle()
        }

    private lateinit var popupRecipient: Recipient
    private var popupDeletesTheCollapsedChip = false

    private val popupMaxWidth by lazy { resources.getDimensionPixelSize(R.dimen.contactPopupMaxWidth) }

    private val contextMenuBinding by lazy {
        ViewContactChipContextMenuBinding.inflate(LayoutInflater.from(context), null, false)
    }

    private val contactPopupWindow by lazy {
        PopupWindow(context).apply {
            contentView = contextMenuBinding.root
            height = LayoutParams.WRAP_CONTENT

            val displayMetrics = context.resources.displayMetrics
            val percentageOfScreen = (displayMetrics.widthPixels * MAX_WIDTH_PERCENTAGE).toInt()
            width = min(percentageOfScreen, popupMaxWidth)

            isFocusable = true
        }
    }

    private var onAutoCompletionToggled: ((hasOpened: Boolean) -> Unit)? = null
    private var onToggleEverything: ((isCollapsed: Boolean) -> Unit)? = null
    private var onContactRemoved: ((Recipient) -> Unit)? = null
    private var onContactAdded: ((Recipient) -> Unit)? = null
    private var onCopyContactAddress: ((Recipient) -> Unit)? = null
    private var gotFocus: (() -> Unit)? = null
    private var getAddressBookWithGroup: ((ContactGroup) -> AddressBook?)? = null
    private var getMergedContactFromContactGroup: ((ContactGroup) -> List<MergedContact>)? = null
    private var getMergedContactFromAddressBook: ((AddressBook) -> List<MergedContact>)? = null

    @Inject
    lateinit var snackbarManager: SnackbarManager

    private var canCollapseEverything = false
    private var otherFieldsAreEmpty = true

    private var isEverythingCollapsed = true
        set(value) {
            field = value
            isSelfCollapsed = field
            updateCollapsedEverythingUiState(value)
        }

    private var isSelfCollapsed = true
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
            computeEndIconVisibility()
        }

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.RecipientFieldView) {
                prefix.text = getText(R.styleable.RecipientFieldView_title)
                canCollapseEverything = getBoolean(R.styleable.RecipientFieldView_canCollapseEverything, canCollapseEverything)
            }

            contactAdapter = ContactAdapter(
                usedEmails = mutableSetOf(),
                onContactClicked = ::contactClicked,
                onAddUnrecognizedContact = {
                    val input = textInput.text.toString()
                    addRecipient(email = input, name = input)
                },
                snackbarManager = snackbarManager,
                getAddressBookWithGroup = { getAddressBookWithGroup?.invoke(it) },
            )

            contactChipAdapter = ContactChipAdapter(
                openContextMenu = ::showContactContextMenu,
                onBackspace = { recipient ->
                    removeRecipient(recipient)
                    focusTextField()
                }
            )

            isSelfCollapsed = true

            setupChipsRecyclerView()

            setToggleRelatedListeners()
            setTextInputListeners()
            setPopupMenuListeners()

            if (isInEditMode) {
                singleChip.root.isVisible = canCollapseEverything
                plusChip.isVisible = canCollapseEverything
            }
        }
    }

    fun setShimmerVisibility(isShimmering: Boolean) = with(binding) {
        textInput.isGone = isShimmering
        chevronContainer.isGone = isShimmering

        loader.isVisible = isShimmering
    }

    fun showKeyboardInTextInput() {
        binding.textInput.showKeyboard()
    }

    private fun setupChipsRecyclerView() = with(binding) {
        chipsRecyclerView.adapter = contactChipAdapter

        (chipsRecyclerView.layoutManager as FlexboxLayoutManager).apply {
            flexDirection = FlexDirection.ROW
            justifyContent = JustifyContent.FLEX_START
        }
    }

    private fun setToggleRelatedListeners() = with(binding) {
        if (canCollapseEverything) chevron.setOnClickListener {
            trackMessageEvent(MatomoName.OpenRecipientsFields, isSelfCollapsed)
            isEverythingCollapsed = !isEverythingCollapsed
            if (isSelfCollapsed) textInput.hideKeyboard()
        }

        plusChip.setOnClickListener {
            expand()
            textInput.showKeyboard()
        }

        transparentButton.setOnClickListener {
            expand()
            textInput.showKeyboard()
        }

        singleChip.root.setOnClickListener {
            if (isSelfCollapsed) {
                expand()
                textInput.showKeyboard()
            } else {
                showContactContextMenu(contactChipAdapter.getRecipients().first(), singleChip.root, isForSingleChip = true)
            }
        }
    }

    private fun setTextInputListeners() = with(binding.textInput) {

        fun performContactSearch(text: CharSequence) {
            if (text.isBlank()) {
                contactAdapter.clear()
            } else {
                contactAdapter.searchContacts(text)
            }
        }

        doOnTextChanged { text, _, _, _ ->
            if (text?.isNotEmpty() == true) {
                performContactSearch(text)
                if (!isAutoCompletionOpened) openAutoCompletion()
            } else if (isAutoCompletionOpened) {
                closeAutoCompletion()
            }
        }

        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && text?.isNotBlank() == true) {
                contactAdapter.addFirstAvailableItem()
            }
            true // Keep keyboard open
        }

        setBackspaceOnEmptyFieldListener(::focusLastChip)

        setOnFocusChangeListener { _, hasFocus -> if (hasFocus) gotFocus?.invoke() }
    }

    private fun setPopupMenuListeners() {
        contextMenuBinding.copyContactAddressButton.setOnClickListener {
            onCopyContactAddress?.invoke(popupRecipient)
            contactPopupWindow.dismiss()
        }

        contextMenuBinding.deleteContactButton.setOnClickListener {
            removeRecipient(popupRecipient)
            if (popupDeletesTheCollapsedChip) {
                popupDeletesTheCollapsedChip = false
                updateCollapsedChipValues(isCollapsed = true)
            }
            contactPopupWindow.dismiss()
        }
    }

    private fun focusLastChip() {
        val count = contactChipAdapter.itemCount
        // chipsRecyclerView.children.last() won't work because they are not always ordered correctly
        if (count > 0) binding.chipsRecyclerView.getChildAt(count - 1)?.requestFocusFromTouch()
    }

    private fun focusTextField() {
        binding.textInput.requestFocus()
    }

    private fun updateCollapsedEverythingUiState(isEverythingCollapsed: Boolean) = with(binding) {
        chevron.toggleChevron(isEverythingCollapsed)
        onToggleEverything?.invoke(isEverythingCollapsed)
    }

    private fun updateCollapsedUiState(isCollapsed: Boolean) = with(binding) {
        updateCollapsedChipValues(isCollapsed)
        chipsRecyclerView.isGone = isCollapsed || contactChipAdapter.isEmpty()
    }

    private fun updateCollapsedChipValues(isCollapsed: Boolean) = with(binding) {
        val isTextInputAccessible = !isCollapsed || contactChipAdapter.isEmpty()

        singleChip.root.apply {
            isGone = isTextInputAccessible
            val recipient = contactChipAdapter.getRecipients().firstOrNull()
            text = recipient?.getNameOrEmail() ?: ""
            setSingleChipStyle()
        }
        plusChip.apply {
            isGone = !isCollapsed || contactChipAdapter.itemCount <= 1
            text = "+${contactChipAdapter.itemCount - 1}"
            setPlusChipStyle()
        }

        transparentButton.isGone = isTextInputAccessible
        textInputLayout.isVisible = isTextInputAccessible
    }

    fun updateContacts(allContacts: List<ContactAutocompletable>) {
        contactAdapter.updateContacts(allContacts)
    }

    private fun setSingleChipStyle() {
        val firstRecipient = contactChipAdapter.getRecipients().firstOrNull()
        val isExternal = firstRecipient?.isDisplayedAsExternal == true

        val firstRecipientStatus = getSpecialChipsEncryptionStatus(firstRecipient?.isEncryptable == true)
        binding.singleChip.root.setChipStyle(displayAsExternal = isExternal, encryptionStatus = firstRecipientStatus)
    }

    private fun setPlusChipStyle() {
        val recipientsExceptFirst = contactChipAdapter.getRecipients().drop(1)
        val plusChipEncryptionStatus = getSpecialChipsEncryptionStatus(recipientsExceptFirst.all { it.isEncryptable })
        binding.plusChip.setChipStyle(displayAsExternal = false, encryptionStatus = plusChipEncryptionStatus)
    }

    private fun getSpecialChipsEncryptionStatus(isRecipientEncryptable: Boolean) = when {
        !isEncryptionActivated -> EncryptionStatus.Unencrypted
        isRecipientEncryptable -> EncryptionStatus.Encrypted
        else -> EncryptionStatus.PartiallyEncrypted
    }

    private fun applyEncryptionStyle() = with(binding) {
        setSingleChipStyle()
        setPlusChipStyle()
        contactChipAdapter.toggleEncryption(isEncryptionActivated, unencryptableRecipients, encryptionPassword)
    }

    private fun openAutoCompletion() {
        isAutoCompletionOpened = true
        onAutoCompletionToggled?.invoke(isAutoCompletionOpened)
    }

    private fun closeAutoCompletion() {
        isAutoCompletionOpened = false
        onAutoCompletionToggled?.invoke(isAutoCompletionOpened)
    }

    private fun contactClicked(contact: ContactAutocompletable) {
        val listOfContact = when (contact) {
            is MergedContact -> listOf(contact)
            is ContactGroup -> getMergedContactFromContactGroup?.invoke(contact) ?: emptyList()
            is AddressBook -> getMergedContactFromAddressBook?.invoke(contact) ?: emptyList()
            else -> emptyList()
        }

        for (mergedContact in listOfContact) {
            addRecipient(mergedContact.email, mergedContact.name)
        }
    }

    private fun addRecipient(email: String, name: String) {

        if (!email.isEmail()) {
            snackbarManager.setValue(context.getString(R.string.addUnknownRecipientInvalidEmail))
            return
        }

        if (contactChipAdapter.itemCount > MAX_ALLOWED_RECIPIENT) {
            snackbarManager.setValue(context.getString(R.string.tooManyRecipients))
            return
        }

        if (contactChipAdapter.isEmpty()) {
            expand()
            binding.chipsRecyclerView.isVisible = true
        }

        val recipientIsNew = contactAdapter.addUsedContact(email)
        if (recipientIsNew) {
            val recipient = Recipient().initLocalValues(email, name)
            contactChipAdapter.addChip(recipient)
            onContactAdded?.invoke(recipient)
            clearField()
        }
    }

    private fun showContactContextMenu(recipient: Recipient, anchor: BackspaceAwareChip, isForSingleChip: Boolean = false) {
        contextMenuBinding.contactDetails.setCorrespondent(recipient)

        popupRecipient = recipient
        popupDeletesTheCollapsedChip = isForSingleChip

        hideKeyboard()
        contactPopupWindow.showAsDropDown(anchor)
    }

    private fun removeRecipient(recipient: Recipient) {
        val successfullyRemoved = contactAdapter.removeUsedEmail(recipient.email)
        if (successfullyRemoved) {
            contactChipAdapter.removeChip(recipient)
            onContactRemoved?.invoke(recipient)
        }
    }

    fun initRecipientField(
        autoComplete: RecyclerView,
        callBackRecipientField: CallBackRecipientField
    ) {

        val margin = context.resources.getDimensionPixelSize(R.dimen.dividerHorizontalPadding)
        val divider = DividerItemDecorator(InsetDrawable(UiUtils.dividerDrawable(context), margin, 0, margin, 0))

        autoCompletedContacts = autoComplete
        autoCompletedContacts.addItemDecoration(divider)
        autoCompletedContacts.adapter = contactAdapter

        with(callBackRecipientField) {
            onToggleEverything = onToggleEverythingCallback
            onAutoCompletionToggled = onAutoCompletionToggledCallback
            onContactAdded = onContactAddedCallback
            onContactRemoved = onContactRemovedCallback
            onCopyContactAddress = onCopyContactAddressCallback
            getAddressBookWithGroup = getAddressBookWithGroupCallback
            getMergedContactFromContactGroup = getMergedContactFromContactGroupCallback
            getMergedContactFromAddressBook = getMergedContactFromAddressBookCallback
            gotFocus = gotFocusCallback
        }
    }

    fun clearField() = binding.textInput.setText("")

    fun initRecipients(initialRecipients: List<Recipient>, otherFieldsAreAllEmpty: Boolean = true) {

        initialRecipients.forEach { recipient ->
            if (contactChipAdapter.addChip(recipient)) contactAdapter.addUsedContact(recipient.email)
        }

        updateCollapsedChipValues(isSelfCollapsed)

        if (canCollapseEverything && !otherFieldsAreAllEmpty) {
            isEverythingCollapsed = false
            isSelfCollapsed = true
        }
    }

    fun collapse() {
        isSelfCollapsed = true
    }

    fun collapseEverything() {
        isEverythingCollapsed = true
    }

    private fun expand() {
        if (canCollapseEverything) isEverythingCollapsed = false else isSelfCollapsed = false
    }

    fun updateOtherRecipientsFieldsVisibility(areEmpty: Boolean) {
        otherFieldsAreEmpty = areEmpty
        computeEndIconVisibility()
    }

    private fun computeEndIconVisibility() = with(binding) {
        val shouldDisplayChevron = canCollapseEverything && otherFieldsAreEmpty && !isAutoCompletionOpened
        chevron.isVisible = shouldDisplayChevron
        textInputLayout.isEndIconVisible = !shouldDisplayChevron && !textInput.text.isNullOrEmpty()
    }

    fun findAlreadyExistingExternalRecipientsInFields(): Pair<String?, Int> {
        val recipients = contactChipAdapter.getRecipients().filter { it.isDisplayedAsExternal }
        val recipientCount = recipients.count()
        return (if (recipientCount == 1) recipients.single().email else null) to recipientCount
    }

    fun updateExternals(shouldWarnForExternal: Boolean, externalData: ExternalData) {
        for (recipient in contactChipAdapter.getRecipients()) {
            if (recipient.isManuallyEntered) continue

            val shouldDisplayAsExternal = shouldWarnForExternal && recipient.isExternal(externalData)
            recipient.updateIsDisplayedAsExternal(shouldDisplayAsExternal)

            updateCollapsedChipValues(isSelfCollapsed)
        }
    }

    data class CallBackRecipientField(
        val onAutoCompletionToggledCallback: (hasOpened: Boolean) -> Unit,
        val onContactAddedCallback: ((Recipient) -> Unit),
        val onContactRemovedCallback: ((Recipient) -> Unit),
        val onCopyContactAddressCallback: ((Recipient) -> Unit),
        val gotFocusCallback: (() -> Unit),
        val onToggleEverythingCallback: ((isCollapsed: Boolean) -> Unit)? = null,
        val getAddressBookWithGroupCallback: (ContactGroup) -> AddressBook?,
        val getMergedContactFromContactGroupCallback: (ContactGroup) -> List<MergedContact>,
        val getMergedContactFromAddressBookCallback: (AddressBook) -> List<MergedContact>
    )

    companion object {
        private const val MAX_WIDTH_PERCENTAGE = 0.8f
        private const val MAX_ALLOWED_RECIPIENT = 99
        private const val EXTERNAL_CHIP_STROKE_WIDTH = 1
        private const val NO_STROKE = 0.0f

        fun Chip.setChipStyle(displayAsExternal: Boolean, encryptionStatus: EncryptionStatus) = when {
            encryptionStatus == EncryptionStatus.Encrypted -> {
                ChipStyle(
                    backgroundColor = R.color.encryptionBackgroundColor,
                    textColor = R.color.encryptionTextColor,
                    icon = R.drawable.ic_lock_filled,
                    iconTint = R.color.encryptionIconColor,
                )
            }
            encryptionStatus == EncryptionStatus.PartiallyEncrypted ||
                    encryptionStatus == EncryptionStatus.Loading -> ChipStyle(
                backgroundColor = R.color.encryptionBackgroundColor,
                textColor = R.color.encryptionTextColor,
                icon = R.drawable.ic_lock_open_filled_pastille,
            )
            displayAsExternal -> ChipStyle(
                backgroundColor = R.color.chip_contact_background_color_external,
                textColor = R.color.chip_contact_text_color_external,
                strokeColor = R.color.externalTagBackground,
            )
            else -> ChipStyle(
                backgroundColor = R.color.chip_contact_background_color,
                textColor = R.color.chip_contact_text_color,
            )
        }.applyTo(this)
    }

    private data class ChipStyle(
        val backgroundColor: Int,
        val textColor: Int,
        val strokeColor: Int? = null,
        val icon: Int? = null,
        val iconTint: Int? = null,
    ) {

        fun applyTo(chip: Chip) = chip.apply {
            val (color, width) = strokeColor?.let {
                ColorStateList.valueOf(context.getColor(it)) to EXTERNAL_CHIP_STROKE_WIDTH.toPx().toFloat()
            } ?: (null to NO_STROKE)

            chipStrokeWidth = width
            chipStrokeColor = color

            setTextColor(context.getColorStateList(textColor))
            setChipBackgroundColorResource(backgroundColor)

            chipIcon = icon?.let { ResourcesCompat.getDrawable(resources, it, null) }
            chipIconTint = iconTint?.let(context::getColorStateList)
            setChipIconSizeResource(R.dimen.iconImageSize)
            setIconStartPaddingResource(RCore.dimen.marginStandardVerySmall)
            val textStartPadding = if (icon == null) RCore.dimen.marginStandardSmall else RCore.dimen.marginStandardVerySmall
            setTextStartPaddingResource(textStartPadding)
        }
    }
}
