/*
 * Infomaniak kMail - Android
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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ViewAvatarNameEmailBinding
import com.infomaniak.mail.utils.UiUtils.fillInUserNameAndEmail

class AvatarNameEmailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
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

    fun setRecipient(recipient: Recipient, contacts: Map<String, Map<String, MergedContact>>) = with(binding) {
        userAvatar.loadAvatar(recipient, contacts)
        setNameAndEmail(recipient)
    }

    fun setMergedContact(mergedContact: MergedContact) = with(binding) {
        userAvatar.loadAvatar(mergedContact)
        setNameAndEmail(mergedContact)
    }

    private fun ViewAvatarNameEmailBinding.setNameAndEmail(correspondent: Correspondent) {
        if (processNameAndEmail) {
            fillInUserNameAndEmail(correspondent, userName, userEmail)
        } else {
            userName.text = correspondent.name
            userEmail.text = correspondent.email
        }
    }

    fun setAutocompleteUnknownContact(searchQuery: String) = with(binding) {
        userAvatar.loadUnknownUserAvatar()
        userName.text = context.getString(R.string.addUnknownRecipientTitle)
        userEmail.text = searchQuery
    }

    fun updateAvatar(recipient: Recipient, contacts: Map<String, Map<String, MergedContact>>) {
        binding.userAvatar.loadAvatar(recipient, contacts)
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) {
        binding.root.setOnClickListener(onClickListener)
    }

    private companion object {
        const val NOT_SET = -1
    }
}
