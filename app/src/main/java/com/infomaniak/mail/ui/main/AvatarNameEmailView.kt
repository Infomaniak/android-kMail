/*
 * Infomaniak ikMail - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.content.Context
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.text.toSpannable
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ViewAvatarNameEmailBinding
import com.infomaniak.mail.utils.MergedContactDictionary
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import com.infomaniak.mail.utils.getAttributeColor
import com.google.android.material.R as RMaterial

class AvatarNameEmailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewAvatarNameEmailBinding.inflate(LayoutInflater.from(context), this, true) }

    private var processNameAndEmail = true

    init {
        attrs?.getAttributes(context, R.styleable.AvatarNameEmailView) {
            with(binding) {
                getDimensionPixelSize(R.styleable.AvatarNameEmailView_padding, NOT_SET).takeIf { it != NOT_SET }?.let {
                    userAvatar.setMarginsRelative(start = it)
                    textLayout.setMarginsRelative(end = it)
                }

                userAvatar.setImageDrawable(getDrawable(R.styleable.AvatarNameEmailView_avatar))
                userName.text = getString(R.styleable.AvatarNameEmailView_name)
                userEmail.text = getString(R.styleable.AvatarNameEmailView_email)

                processNameAndEmail = getBoolean(R.styleable.AvatarNameEmailView_processNameAndEmail, processNameAndEmail)
            }
        }
    }

    fun setRecipient(recipient: Recipient, contacts: MergedContactDictionary) = with(binding) {
        userAvatar.loadAvatar(recipient, contacts)
        setNameAndEmail(recipient)
    }

    fun setMergedContact(mergedContact: MergedContact) = with(binding) {
        userAvatar.loadAvatar(mergedContact)
        setNameAndEmail(mergedContact)
    }

    private fun ViewAvatarNameEmailBinding.setNameAndEmail(correspondent: Correspondent) {
        fillInUserNameAndEmail(correspondent, userName, userEmail, ignoreIsMe = !processNameAndEmail)
    }

    fun setAutocompleteUnknownContact(searchQuery: String) = with(binding) {
        userAvatar.loadUnknownUserAvatar()
        userName.text = context.getString(R.string.addUnknownRecipientTitle)
        userEmail.text = searchQuery
    }

    fun updateAvatar(recipient: Recipient, contacts: MergedContactDictionary) {
        binding.userAvatar.loadAvatar(recipient, contacts)
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.root.setOnClickListener(onClickListener)
    }

    fun highlight(nameStartIndex: Int, emailStartIndex: Int, length: Int) = with(binding) {
        if (nameStartIndex >= 0) userName.highlight(nameStartIndex, nameStartIndex + length)
        if (emailStartIndex >= 0 && userEmail.text.isNotBlank()) userEmail.highlight(emailStartIndex, emailStartIndex + length)
    }

    private fun TextView.highlight(
        startIndex: Int,
        endIndex: Int,
        @ColorInt color: Int = context.getAttributeColor(RMaterial.attr.colorPrimary)
    ) {
        val highlightedText = text.toSpannable()
        highlightedText.setSpan(ForegroundColorSpan(color), startIndex, endIndex, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        text = highlightedText
    }

    private companion object {
        const val NOT_SET = -1
    }
}
