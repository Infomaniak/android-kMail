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
package com.infomaniak.mail.ui.login

import android.graphics.Color
import com.airbnb.lottie.model.KeyPath
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor1
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor10
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor11
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor2
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor3
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor4
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor5
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor6
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor7
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor8
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.blueColor9
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor1
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor10
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor2
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor3
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor4
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor5
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor6
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor7
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor8
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.commonColor9
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor1
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor10
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor11
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor12
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor13
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor2
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor3
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor4
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor5
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor6
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor7
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor8
import com.infomaniak.mail.ui.login.IlluColors.Colors.Companion.pinkColor9

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

            val pinkColor1 = Colors("#BC0055", "#D0759F")
            val pinkColor2 = Colors("#FF5B97", "#EF0057")
            val pinkColor3 = Colors("#AB2456", "#D0759F")
            val pinkColor4 = Colors("#BD95A7", "#AE366D")
            val pinkColor5 = Colors("#BF4C80", "#E75F9C")
            val pinkColor6 = Colors("#DFBDCC", "#955873")
            val pinkColor7 = Colors("#824D65", "#AB6685")
            val pinkColor8 = Colors("#693D51", "#CA799E")
            val pinkColor9 = Colors("#F7E8EF", "#282828")
            val pinkColor10 = Colors("#FF4388", "#B80043")
            val pinkColor11 = Colors("#D81B60", "#FB2C77")
            val pinkColor12 = Colors("#FAF0F0", "#F1DDDD")
            val pinkColor13 = Colors("#E10B59", "#DC1A60")

            val blueColor1 = Colors("#0098FF", "#0177C7")
            val blueColor2 = Colors("#69C9FF", "#6DCBFF")
            val blueColor3 = Colors("#3981AA", "#56AFE1")
            val blueColor4 = Colors("#289CDD", "#0D7DBC")
            val blueColor5 = Colors("#84BAD8", "#588EAC")
            val blueColor6 = Colors("#10405B", "#10405B")
            val blueColor7 = Colors("#0B3547", "#266E8D")
            val blueColor8 = Colors("#EAF8FE", "#282828")
            val blueColor9 = Colors("#0A85C9", "#0A85C9")
            val blueColor10 = Colors("#F7FCFF", "#E8F6FF")
            val blueColor11 = Colors("#0875A5", "#0875A5")
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
        BEN("BEN"),
        RING("RING"),
        WOMAN("WOMAN"),
        MEN("MEN"),
        LETTER("LETTER"),
    }

    enum class FinalLayer(val value: String) {
        BACKGROUND("Fond"),
        BORDER("Contour"),
    }


    companion object {

        private fun keyPath(
            category: Category,
            group: Int = 1,
            categoryNumber: Int? = null,
            finalLayer: FinalLayer = FinalLayer.BACKGROUND
        ): KeyPath {
            val categoryName = categoryNumber?.let { "${category.value} $it" } ?: category.value
            return KeyPath(categoryName, "Groupe $group", "${finalLayer.value} 1")
        }

        val illuColors = arrayOf(
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
        val illuPinkColors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 1), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 2), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 3), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 4), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 5), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 6), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 9), pinkColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 12), pinkColor3),
            IlluColors(keyPath(Category.IPHONESCREEN, 15), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 19), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 20), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 23), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 24), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 43), pinkColor4),
            IlluColors(keyPath(Category.IPHONESCREEN, 48), pinkColor5),
        )
        val illuBlueColors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 1), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 2), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 3), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 4), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 5), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 6), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 9), blueColor2),
            IlluColors(keyPath(Category.IPHONESCREEN, 12), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 15), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 19), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 20), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 23), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 24), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 43), blueColor3),
            IlluColors(keyPath(Category.IPHONESCREEN, 48), blueColor4),
        )

        val illu1Colors = arrayOf(
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
        val illu1PinkColors = arrayOf(
            IlluColors(keyPath(Category.CHAT, 1, 1), pinkColor1),
            IlluColors(keyPath(Category.CHAT, 1, 2), pinkColor4),
            IlluColors(keyPath(Category.IPHONESCREEN, 55), pinkColor6),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 66), pinkColor7),
        )
        val illu1BlueColors = arrayOf(
            IlluColors(keyPath(Category.CHAT, 1, 1), blueColor1),
            IlluColors(keyPath(Category.CHAT, 1, 2), blueColor3),
            IlluColors(keyPath(Category.IPHONESCREEN, 55), blueColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 66), blueColor6),
        )

        val illu234Colors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 73), commonColor6),
            IlluColors(keyPath(Category.IPHONESCREEN, 74), commonColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 75), commonColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 76), commonColor2),
        )
        val illu234PinkColors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 54), pinkColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), pinkColor6),
            IlluColors(keyPath(Category.IPHONESCREEN, 67), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 72), pinkColor7),
        )
        val illu234BlueColors = arrayOf(
            IlluColors(keyPath(Category.IPHONESCREEN, 54), blueColor4),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), blueColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 67), blueColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 72), blueColor6),
        )

        val illu2Colors = arrayOf(
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
        val illu2PinkColors = arrayOf(
            IlluColors(keyPath(Category.NOTIFICATION, 4, 2), pinkColor5),
            IlluColors(keyPath(Category.NOTIFICATION, 11, 2), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 54), pinkColor5),
            IlluColors(keyPath(Category.IPHONESCREEN, 61), pinkColor6),
            IlluColors(keyPath(Category.IPHONESCREEN, 67), pinkColor1),
            IlluColors(keyPath(Category.IPHONESCREEN, 72), pinkColor7),
            IlluColors(keyPath(Category.HAND, 1), pinkColor7),
            IlluColors(keyPath(Category.HAND, 4), pinkColor8),
            IlluColors(keyPath(Category.HAND, 5), pinkColor8),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 15, 1), pinkColor1),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 4, 2), pinkColor5),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 11, 2), pinkColor1),
        )
        val illu2BlueColors = arrayOf(
            IlluColors(keyPath(Category.NOTIFICATION, 4, 2), blueColor4),
            IlluColors(keyPath(Category.NOTIFICATION, 11, 2), blueColor1),
            IlluColors(keyPath(Category.HAND, 1), blueColor6),
            IlluColors(keyPath(Category.HAND, 4), blueColor7),
            IlluColors(keyPath(Category.HAND, 5), blueColor7),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 15, 1), blueColor1),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 4, 2), blueColor4),
            IlluColors(keyPath(Category.MOVINGNOTIFICATION, 11, 2), blueColor1),
        )

        val illu3Colors = arrayOf(
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
            IlluColors(keyPath(Category.BEN, 7), commonColor3),
            IlluColors(keyPath(Category.RING, 5), commonColor3),
        )
        val illu3PinkColors = arrayOf(
            IlluColors(keyPath(Category.NOTIFICATION, 4, 2), pinkColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 2), pinkColor9),
            IlluColors(keyPath(Category.NOTIFICATION, 4, 3), pinkColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 3), pinkColor9),
            IlluColors(keyPath(Category.NOTIFICATION, 4, 4), pinkColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 4), pinkColor9),
            IlluColors(keyPath(Category.HAND, 1), pinkColor7),
            IlluColors(keyPath(Category.HAND, 4), pinkColor8),
            IlluColors(keyPath(Category.HAND, 4), pinkColor8),
            IlluColors(keyPath(Category.STAR, 1, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.BEN, 1, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.BEN, 2, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.BEN, 3, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.BEN, 4, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.BEN, 5, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.BEN, 6, finalLayer = FinalLayer.BORDER), pinkColor1),
            IlluColors(keyPath(Category.RING, 1), pinkColor1),
            IlluColors(keyPath(Category.RING, 2), pinkColor1),
            IlluColors(keyPath(Category.RING, 3), pinkColor1),
            IlluColors(keyPath(Category.RING, 4), pinkColor1),
        )
        val illu3BlueColors = arrayOf(
            IlluColors(keyPath(Category.NOTIFICATION, 4, 2), blueColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 2), blueColor8),
            IlluColors(keyPath(Category.NOTIFICATION, 4, 3), blueColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 3), blueColor8),
            IlluColors(keyPath(Category.NOTIFICATION, 4, 4), blueColor1),
            IlluColors(keyPath(Category.NOTIFICATION, 5, 4), blueColor8),
            IlluColors(keyPath(Category.HAND, 1), blueColor6),
            IlluColors(keyPath(Category.HAND, 4), blueColor7),
            IlluColors(keyPath(Category.HAND, 4), blueColor7),
            IlluColors(keyPath(Category.STAR, 1, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.BEN, 1, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.BEN, 2, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.BEN, 3, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.BEN, 4, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.BEN, 5, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.BEN, 6, finalLayer = FinalLayer.BORDER), blueColor1),
            IlluColors(keyPath(Category.RING, 1), blueColor1),
            IlluColors(keyPath(Category.RING, 2), blueColor1),
            IlluColors(keyPath(Category.RING, 3), blueColor1),
            IlluColors(keyPath(Category.RING, 4), blueColor1),
        )

        val illu4Colors = arrayOf(
            IlluColors(keyPath(Category.WOMAN, 5), commonColor4),
            IlluColors(keyPath(Category.WOMAN, 6), commonColor1),
            IlluColors(keyPath(Category.MEN, 5), commonColor4),
            IlluColors(keyPath(Category.MEN, 6), commonColor1),
            IlluColors(keyPath(Category.LETTER, 3), commonColor9),
            IlluColors(keyPath(Category.LETTER, 4), commonColor10),
        )
        val illu4PinkColors = arrayOf(
            IlluColors(keyPath(Category.WOMAN, 4), pinkColor5),
            IlluColors(keyPath(Category.MEN, 5), pinkColor4),
            IlluColors(keyPath(Category.POINT, 1, 1), pinkColor5),
            IlluColors(keyPath(Category.POINT, 1, 2), pinkColor5),
            IlluColors(keyPath(Category.POINT, 1, 3), pinkColor4),
            IlluColors(keyPath(Category.POINT, 1, 4), pinkColor4),
            IlluColors(keyPath(Category.LETTER, 1), pinkColor10),
            IlluColors(keyPath(Category.LETTER, 2), pinkColor11),
            IlluColors(keyPath(Category.LETTER, 5), pinkColor12),
            IlluColors(keyPath(Category.LETTER, 6), pinkColor13),
            IlluColors(keyPath(Category.LETTER, 7), pinkColor13),
        )
        val illu4BlueColors = arrayOf(
            IlluColors(keyPath(Category.WOMAN, 4), blueColor4),
            IlluColors(keyPath(Category.MEN, 5), blueColor3),
            IlluColors(keyPath(Category.POINT, 1, 1), blueColor4),
            IlluColors(keyPath(Category.POINT, 1, 2), blueColor4),
            IlluColors(keyPath(Category.POINT, 1, 3), blueColor3),
            IlluColors(keyPath(Category.POINT, 1, 4), blueColor3),
            IlluColors(keyPath(Category.LETTER, 1), blueColor2),
            IlluColors(keyPath(Category.LETTER, 2), blueColor9),
            IlluColors(keyPath(Category.LETTER, 5), blueColor10),
            IlluColors(keyPath(Category.LETTER, 6), blueColor11),
            IlluColors(keyPath(Category.LETTER, 7), blueColor11),
        )
    }
}
