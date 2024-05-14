/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ViewAvatarNameEmailBinding
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.google.android.material.R as RMaterial

class AvatarNameEmailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewAvatarNameEmailBinding.inflate(LayoutInflater.from(context), this, true) }

    private var processNameAndEmail = true
    private var displayAsAttendee = false

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.AvatarNameEmailView) {
                getDimensionPixelSize(R.styleable.AvatarNameEmailView_padding, NOT_SET).takeIf { it != NOT_SET }?.let {
                    userAvatar.setMarginsRelative(start = it)
                    textLayout.setMarginsRelative(end = it)
                }

                userAvatar.setImageDrawable(getDrawable(R.styleable.AvatarNameEmailView_avatar))
                userName.text = getString(R.styleable.AvatarNameEmailView_name)
                userEmail.text = getString(R.styleable.AvatarNameEmailView_email)

                processNameAndEmail = getBoolean(R.styleable.AvatarNameEmailView_processNameAndEmail, processNameAndEmail)
                displayAsAttendee = getBoolean(R.styleable.AvatarNameEmailView_displayAsAttendee, displayAsAttendee)
            }

            attendeeAvatar.isVisible = displayAsAttendee
            userAvatar.isGone = displayAsAttendee
        }
    }

    fun setCorrespondent(correspondent: Correspondent, bimi: Bimi? = null) = with(binding) {
        if (bimi == null || !bimi.isCertified) {
            userAvatar.loadAvatar(correspondent)
        } else {
            userAvatar.loadBimiAvatar(bimi.svgContentUrl.orEmpty(), correspondent)
        }
        setNameAndEmail(correspondent, isCorrespondentCertified = bimi?.isCertified ?: false)
    }

    fun setMergedContact(mergedContact: MergedContact) = with(binding) {
        userAvatar.loadAvatar(mergedContact)
        setNameAndEmail(mergedContact)
    }

    fun setAttendee(attendee: Attendee) = with(binding) {
        attendeeAvatar.setAttendee(attendee)
        setNameAndEmail(attendee)
    }

    private fun ViewAvatarNameEmailBinding.setNameAndEmail(
        correspondent: Correspondent,
        isCorrespondentCertified: Boolean = false
    ) {
        val filledSingleField = fillInUserNameAndEmail(correspondent, userName, userEmail, ignoreIsMe = !processNameAndEmail)
        if (displayAsAttendee) {
            val userNameTextColor = if (filledSingleField) R.style.AvatarNameEmailSecondary else R.style.AvatarNameEmailPrimary
            userName.setTextAppearance(userNameTextColor)
        }

        iconCertified.isVisible = isCorrespondentCertified
    }

    fun setAutocompleteUnknownContact(searchQuery: String) = with(binding) {
        userAvatar.loadUnknownUserAvatar()
        userName.text = context.getString(R.string.addUnknownRecipientTitle)
        userEmail.text = searchQuery
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
        @ColorInt color: Int = context.getAttributeColor(RMaterial.attr.colorPrimary),
    ) {
        val highlightedText = text.toSpannable()
        highlightedText.setSpan(ForegroundColorSpan(color), startIndex, endIndex, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        text = highlightedText
    }

    companion object {
        private const val NOT_SET = -1
    }
}
