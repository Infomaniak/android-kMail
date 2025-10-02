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
package com.infomaniak.mail.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.DimenRes
import androidx.annotation.StyleRes
import coil3.load
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.legacy.utils.getAttributes
import com.infomaniak.core.legacy.utils.setMarginsRelative
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.databinding.ViewAttachmentDetailsBinding
import com.infomaniak.core.legacy.R as RCore

class AttachmentDetailsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewAttachmentDetailsBinding.inflate(LayoutInflater.from(context), this) }

    init {
        attrs?.getAttributes(context, R.styleable.AttachmentDetailsView) {
            val displayStyle = DisplayStyle.entries[getInteger(R.styleable.AttachmentDetailsView_displayStyle, 0)]
            val iconSize = context.resources.getDimension(displayStyle.iconSize).toInt()
            val marginSize = context.resources.getDimension(displayStyle.marginSize).toInt()

            with(binding) {

                if (displayStyle == DisplayStyle.CHIP) {
                    fileName.maxWidth = context.resources.getDimension(R.dimen.maxAttachmentChipWidth).toInt()
                }

                fileName.setTextAppearance(displayStyle.fileNameStyle)
                fileSize.setTextAppearance(displayStyle.fileSizeStyle)
                icon.apply {
                    layoutParams.height = iconSize
                    layoutParams.width = iconSize
                    setMarginsRelative(start = marginSize, end = marginSize)
                }
            }
        }
    }

    fun setDetails(attachment: Attachable) = with(binding) {
        fileName.text = attachment.name
        fileSize.text = context.formatShortFileSize(attachment.size)
        icon.load(attachment.getFileTypeFromMimeType().icon)
    }

    private enum class DisplayStyle(
        @DimenRes val iconSize: Int,
        @DimenRes val marginSize: Int,
        @StyleRes val fileNameStyle: Int,
        @StyleRes val fileSizeStyle: Int,
    ) {
        CHIP(
            R.dimen.standardIconSize,
            RCore.dimen.marginStandardSmall,
            R.style.BodySmall,
            R.style.Label_Secondary,
        ),
        BOTTOMSHEET(
            R.dimen.largeIconSize,
            R.dimen.alternativeMargin,
            R.style.Body,
            R.style.Body_Secondary,
        ),
    }
}
