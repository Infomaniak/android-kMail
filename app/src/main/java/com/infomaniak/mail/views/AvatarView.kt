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
package com.infomaniak.mail.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import coil.imageLoader
import coil.request.Disposable
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.MergedContact
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.databinding.ViewAvatarBinding

class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewAvatarBinding.inflate(LayoutInflater.from(context), this, true) }

    init {
        with(binding) {
            if (attrs != null) {
                val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AvatarView, 0, 0)

                val src = typedArray.getDrawable(R.styleable.AvatarView_android_src)
                avatarImage.setImageDrawable(src)

                typedArray.recycle()
            }
        }
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) = binding.avatar.setOnClickListener(onClickListener)

    fun loadAvatar(user: User): Disposable {
        return binding.avatarImage.loadAvatar(user.id, user.avatar, user.getInitials(), context.imageLoader)
    }

    fun loadAvatar(recipient: Recipient, contacts: Map<Recipient, MergedContact>) {
        binding.avatarImage.loadCorrespondentAvatar(contacts[recipient] ?: recipient)
    }

    fun loadAvatar(mergedContact: MergedContact) {
        binding.avatarImage.loadCorrespondentAvatar(mergedContact)
    }

    private fun ImageView.loadCorrespondentAvatar(correspondent: Correspondent): Disposable = with(correspondent) {
        val avatar = (correspondent as? MergedContact)?.avatar
        return loadAvatar(email.hashCode(), avatar, initials, context.imageLoader)
    }
}
