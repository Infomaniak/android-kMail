/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.mail.views.itemViews

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewBannerMyKsuiteStorageBinding

class MyKSuiteStorageBanner @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewBannerMyKsuiteStorageBinding.inflate(LayoutInflater.from(context), this, true) }

    var storageLevel = StorageLevel.Normal
        set(value) {
            binding.root.isGone = value == StorageLevel.Normal
            if (value != StorageLevel.Normal) setStorageLevelUi(value)
            field = value
        }


    fun setupListener(onCloseButtonClicked: () -> Unit) {
        binding.closeButton.setOnClickListener { onCloseButtonClicked() }
    }

    private fun setStorageLevelUi(newStorageLevel: StorageLevel) = with(binding) {
        if (newStorageLevel == storageLevel) return@with

        title.text = context.getText(newStorageLevel.titleRes)
        description.text = context.getText(newStorageLevel.descriptionRes)
        alertIcon.setColorFilter(context.getColor(newStorageLevel.iconColorRes))

        closeButton.isVisible = newStorageLevel == StorageLevel.Warning
    }

    enum class StorageLevel(
        @ColorRes val iconColorRes: Int,
        @StringRes val titleRes: Int,
        @StringRes val descriptionRes: Int,
    ) {
        Normal(
            iconColorRes = ResourcesCompat.ID_NULL,
            titleRes = ResourcesCompat.ID_NULL,
            descriptionRes = ResourcesCompat.ID_NULL,
        ),
        Warning(
            iconColorRes = R.color.orangeWarning,
            titleRes = R.string.myKSuiteQuotasAlertTitle,
            descriptionRes = R.string.myKSuiteQuotasAlertDescription,
        ),
        Full(
            iconColorRes = R.color.redDestructiveAction,
            titleRes = R.string.myKSuiteQuotasAlertFullTitle,
            descriptionRes = R.string.myKSuiteQuotasAlertFullDescription,
        );

        companion object {
            const val WARNING_THRESHOLD = 85
        }
    }
}
