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
package com.infomaniak.mail.ui.login

import android.os.Bundle
import com.infomaniak.lib.core.utils.Utils.lockOrientationForSmallScreens
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.mail.BuildConfig.SHOP_URL
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ActivityNoMailboxBinding
import com.infomaniak.mail.ui.BaseActivity
import com.infomaniak.mail.ui.login.IlluColors.Category
import com.infomaniak.mail.ui.login.IlluColors.IlluColors
import com.infomaniak.mail.ui.login.IlluColors.getPaletteFor
import com.infomaniak.mail.ui.login.IlluColors.keyPath
import com.infomaniak.mail.utils.changePathColor
import com.infomaniak.mail.utils.repeatFrame
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class NoMailboxActivity : BaseActivity() {

    private val binding by lazy { ActivityNoMailboxBinding.inflate(layoutInflater) }

    override fun onCreate(savedInstanceState: Bundle?) {
        lockOrientationForSmallScreens()

        super.onCreate(savedInstanceState)

        with(binding) {
            setContentView(root)

            noMailboxIconLayout.apply {
                getAccentIndependentIlluColors().forEach(::changePathColor)
                getAccentDependentIlluColors().forEach(::changePathColor)
                setAnimation(R.raw.illu_no_mailbox)
                repeatFrame(42, 112)
            }

            noMailboxActionButton.setOnClickListener {
                openUrl(SHOP_URL)
                onBackPressedDispatcher.onBackPressed()
            }

            connectAnotherAccountButton.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }
    }

    private fun getAccentIndependentIlluColors(): List<IlluColors> {
        val commonColor2 = getColor(R.color.commonColor2)
        val commonColor5 = getColor(R.color.commonColor5)
        val commonColor11 = getColor(R.color.commonColor11)

        return listOf(
            IlluColors(keyPath(Category.LINK, 1), commonColor11),
            IlluColors(keyPath(Category.IPHONESCREEN, 1), commonColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 2), commonColor2),
        )
    }

    private fun getAccentDependentIlluColors(): List<IlluColors> {
        val colors = getPaletteFor(localSettings.accentColor)
        val pinkColor4 = colors[4]
        val pinkColor10 = colors[10]

        return listOf(
            IlluColors(keyPath(Category.HAND, 1), pinkColor4),
            IlluColors(keyPath(Category.HAND, 4), pinkColor10),
            IlluColors(keyPath(Category.HAND, 5), pinkColor10),
        )
    }
}
