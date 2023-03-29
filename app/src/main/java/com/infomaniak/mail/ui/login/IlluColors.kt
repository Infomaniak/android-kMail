/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.graphics.Color
import com.airbnb.lottie.model.KeyPath
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor10
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor4
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor1
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor10
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor11
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor2
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor3
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor4
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor5
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor6
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor7
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor8
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor9
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor10
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor4

class IlluColors(val keyPath: KeyPath, val color: Colors) {
    fun getLightColor() = Color.parseColor(color.light)
    fun getDarkColor() = Color.parseColor(color.dark)

    class Colors(val light: String, val dark: String) {

        companion object {
            val commonColor1 = Colors("#F5F5F5", "#3E3E3E")
            val commonColor2 = Colors("#E0E0E0", "#4C4C4C")
            val commonColor3 = Colors("#FAFAFA", "#282828")
            val commonColor4 = Colors("#C6AC9F", "#996452")
            val commonColor5 = Colors("#FFFFFF", "#1A1A1A")
            val commonColor6 = Colors("#340E00", "#996452")
            val commonColor7 = Colors("#CCCCCC", "#818181")
            val commonColor8 = Colors("#C4C4C4", "#7C7C7C")
            val commonColor9 = Colors("#FFFFFF", "#EAEAEA")
            val commonColor10 = Colors("#F8F8F8", "#E4E4E4")
            val commonColor11 = Colors("#D9D9D9", "#626262")

            val pinkColor1 = Colors("#BC0055", "#D0759F")
            val pinkColor2 = Colors("#BD95A7", "#AE366D")
            val pinkColor3 = Colors("#DFBDCC", "#955873")
            val pinkColor4 = Colors("#824D65", "#AB6685")
            val pinkColor5 = Colors("#BF4C80", "#E75F9C")
            val pinkColor6 = Colors("#FF5B97", "#EF0057")
            val pinkColor7 = Colors("#FF4388", "#B80043")
            val pinkColor8 = Colors("#D81B60", "#FB2C77")
            val pinkColor9 = Colors("#E10B59", "#DC1A60")
            val pinkColor10 = Colors("#693D51", "#CA799E")
            val pinkColor11 = Colors("#F7E8EF", "#282828")
            val pinkColor12 = Colors("#FAF0F0", "#F1DDDD")

            val pinkColors = listOf(
                pinkColor1,
                pinkColor2,
                pinkColor3,
                pinkColor4,
                pinkColor5,
                pinkColor6,
                pinkColor7,
                pinkColor8,
                pinkColor9,
                pinkColor10,
                pinkColor11,
                pinkColor12,
            )

            val blueColor1 = Colors("#0098FF", "#0177C7")
            val blueColor2 = Colors("#3981AA", "#56AFE1")
            val blueColor3 = Colors("#84BAD8", "#588EAC")
            val blueColor4 = Colors("#10405B", "#10405B")
            val blueColor5 = Colors("#289CDD", "#0D7DBC")
            val blueColor6 = Colors("#69C9FF", "#6DCBFF")
            val blueColor7 = Colors("#5AC4FF", "#6DCBFF")
            val blueColor8 = Colors("#0A85C9", "#0A85C9")
            val blueColor9 = Colors("#0875A5", "#0875A5")
            val blueColor10 = Colors("#0B3547", "#266E8D")
            val blueColor11 = Colors("#EAF8FE", "#282828")
            val blueColor12 = Colors("#F7FCFF", "#E8F6FF")

            val blueColors = listOf(
                blueColor1,
                blueColor2,
                blueColor3,
                blueColor4,
                blueColor5,
                blueColor6,
                blueColor7,
                blueColor8,
                blueColor9,
                blueColor10,
                blueColor11,
                blueColor12,
            )
        }
    }

    enum class Category(val value: String) {
        IPHONESCREEN("IPHONE SCREEN"),
        POINT("POINT"),
        CHAT("CHAT"),
        NOTIFICATION("NOTIFICATION"),
        MOVINGNOTIFICATION("MOVING NOTIF"),
        ARCHIVES("ARCHIVES"),
        HAND("HAND"),
        STAR("STAR"),
        BIN("BIN"),
        CLOCK("CLOCK"),
        WOMAN("WOMAN"),
        MEN("MEN"),
        LETTER("LETTER"),
        LINK("LINK"),
    }

    enum class FinalLayer(val value: String) {
        BACKGROUND("Fond"),
        BORDER("Contour"),
    }

    companion object {

        fun keyPath(
            category: Category,
            group: Int = 1,
            categoryNumber: Int? = null,
            finalLayer: FinalLayer = FinalLayer.BACKGROUND
        ): KeyPath {
            val categoryName = categoryNumber?.let { "${category.value} $it" } ?: category.value
            return KeyPath(categoryName, "Groupe $group", "${finalLayer.value} 1")
        }

        val illuOnBoardingColors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 18), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 22), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 25), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 26), commonColor3),
            IlluColors(keyPath(Category.IPHONESCREEN, 27), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 28), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 29), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 30), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 31), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 32), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 33), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 34), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 35), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 36), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 37), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 38), commonColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 39), commonColor4),
            IlluColors(keyPath(Category.IPHONESCREEN, 44), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 49), commonColor4),
            IlluColors(keyPath(Category.IPHONESCREEN, 50), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 62), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 68), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 70), commonColor1),
        )

        val illuOnBoarding1Colors = arrayOf(
            IlluColors(keyPath(Category.POINT, 1, 1), commonColor5),
            IlluColors(keyPath(Category.POINT, 1, 2), commonColor5),
            IlluColors(keyPath(Category.POINT, 1, 3), commonColor5),
            IlluColors(keyPath(Category.POINT, 1, 4), commonColor5),
            IlluColors(keyPath(Category.POINT, 1, 5), commonColor5),
            IlluColors(keyPath(Category.POINT, 1, 6), commonColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 56), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 67), commonColor6),
            IlluColors(keyPath(Category.IPHONESCREEN, 69), commonColor5),
        )

        val illuOnBoarding234Colors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 73), commonColor6),
            IlluColors(keyPath(Category.IPHONESCREEN, 74), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 75), commonColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 76), commonColor2),
        )

        val illuOnBoarding2Colors = arrayOf(
            IlluColors(keyPath(Category.NOTIFICATION, 5, 2), commonColor4),
            IlluColors(keyPath(Category.NOTIFICATION, 6, 2), commonColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 9, 2), commonColor7),
            IlluColors(keyPath(Category.NOTIFICATION, 10, 2), commonColor7),
            IlluColors(keyPath(Category.NOTIFICATION, 12, 2), commonColor5),
            IlluColors(keyPath(Category.NOTIFICATION, 13, 2), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 14, 2), commonColor1),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 4, 1), commonColor8),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 5, 1), commonColor8),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 6, 1), commonColor8),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 7, 1), commonColor5),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 8, 1), commonColor2),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 9, 1), commonColor2),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 10, 1), commonColor2),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 13, 1), commonColor5),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 14, 1), commonColor8),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 5, 2), commonColor4),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 6, 2), commonColor1),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 9, 2), commonColor7),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 10, 2), commonColor7),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 12, 2), commonColor5),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 13, 2), commonColor2),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 14, 2), commonColor1),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 15, 2), commonColor1),
            IlluColors(KeyPath("MOVING NOTIF 2 TITLE", "Groupe 1", "Fond 1"), commonColor2),
            IlluColors(KeyPath("MOVING NOTIF 2 PREVIEW", "Groupe 1", "Fond 1"), commonColor2),
            IlluColors(keyPath(Category.ARCHIVES, 1), commonColor5),
            IlluColors(keyPath(Category.ARCHIVES, 2), commonColor5),
            IlluColors(keyPath(Category.ARCHIVES, 3), commonColor5),
            IlluColors(keyPath(Category.ARCHIVES, 4), commonColor5),
        )

        val illuOnBoarding3Colors = arrayOf(
            IlluColors(keyPath(Category.NOTIFICATION, 1, 2), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 2, 2), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 3, 2), commonColor5),
            IlluColors(keyPath(Category.NOTIFICATION, 1, 3), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 2, 3), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 3, 3), commonColor5),
            IlluColors(keyPath(Category.NOTIFICATION, 1, 4), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 2, 4), commonColor2),
            IlluColors(keyPath(Category.NOTIFICATION, 3, 4), commonColor5),
            IlluColors(keyPath(Category.STAR, 2), commonColor3),
            IlluColors(keyPath(Category.BIN, 7), commonColor3),
            IlluColors(keyPath(Category.CLOCK, 5), commonColor3),
        )

        val illuOnBoarding4Colors = arrayOf(
            IlluColors(keyPath(Category.WOMAN, 5), commonColor4),
            IlluColors(keyPath(Category.WOMAN, 6), commonColor1),
            IlluColors(keyPath(Category.MEN, 5), commonColor4),
            IlluColors(keyPath(Category.MEN, 6), commonColor1),
            IlluColors(keyPath(Category.LETTER, 3), commonColor9),
            IlluColors(keyPath(Category.LETTER, 4), commonColor10),
        )

        val illuNoMailboxColors = arrayOf(
            IlluColors(keyPath(Category.LINK, 1), commonColor11),
            IlluColors(keyPath(Category.IPHONESCREEN, 1), commonColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 2), commonColor2),
        )

        val illuNoMailboxPinkColor = arrayOf(
            IlluColors(keyPath(Category.HAND, 1), pinkColor4),
            IlluColors(keyPath(Category.HAND, 4), pinkColor10),
            IlluColors(keyPath(Category.HAND, 5), pinkColor10),
        )

        val illuNoMailboxBlueColor = arrayOf(
            IlluColors(keyPath(Category.HAND, 1), blueColor4),
            IlluColors(keyPath(Category.HAND, 4), blueColor10),
            IlluColors(keyPath(Category.HAND, 5), blueColor10),
        )
    }
}
