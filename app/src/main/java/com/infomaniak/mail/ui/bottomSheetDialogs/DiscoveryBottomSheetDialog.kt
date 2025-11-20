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
package com.infomaniak.mail.ui.bottomSheetDialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.annotation.Dimension
import androidx.annotation.Dimension.Companion.DP
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.infomaniak.core.ui.view.toPx
import com.infomaniak.mail.MatomoMail.MatomoName

abstract class DiscoveryBottomSheetDialog : InformationBottomSheetDialog() {

    abstract val titleRes: Int
    abstract val descriptionRes: Int?
    abstract val illustration: Illustration

    abstract val positiveButtonRes: Int

    abstract val trackMatomoWithCategory: (name: MatomoName) -> Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        title.setText(titleRes)
        descriptionRes?.let(description::setText)

        setIllustration()

        actionButton.apply {
            setText(positiveButtonRes)
            setOnClickListener {
                trackMatomoWithCategory(MatomoName.DiscoverNow)
                onPositiveButtonClicked()
                dismiss()
            }
        }

        secondaryActionButton.setOnClickListener {
            trackMatomoWithCategory(MatomoName.DiscoverLater)
            dismiss()
        }
    }

    private fun setIllustration() = with(binding) {
        when (val illustration = illustration) {
            is Illustration.Static -> infoIllustration.apply {
                isVisible = true
                infoAnimation.isGone = true
                setBackgroundResource(illustration.resId)
            }
            is Illustration.Animated -> infoAnimation.apply {
                isVisible = true
                infoIllustration.isGone = true

                updateLayoutParams { height = illustration.heightDp.toPx(root) }
                infoAnimation.setAnimation(illustration.resId)
            }
        }
    }

    abstract fun onPositiveButtonClicked()

    override fun onCancel(dialog: DialogInterface) {
        trackMatomoWithCategory(MatomoName.DiscoverLater)
        super.onCancel(dialog)
    }

    sealed interface Illustration {
        data class Static(@DrawableRes val resId: Int) : Illustration
        // heightDp must be set in order for Lottie to show the animation.
        data class Animated(@RawRes val resId: Int, @Dimension(DP) val heightDp: Int) : Illustration
    }
}
