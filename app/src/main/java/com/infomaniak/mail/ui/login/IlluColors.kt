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
package com.infomaniak.mail.ui.login

import android.graphics.Color
import com.airbnb.lottie.model.KeyPath
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor1
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor9
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor10
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor11
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor2
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor3
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor4
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor5
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor6
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor7
import com.infomaniak.mail.ui.login.Colors.Companion.blueColor8
import com.infomaniak.mail.ui.login.Colors.Companion.color1
import com.infomaniak.mail.ui.login.Colors.Companion.color10
import com.infomaniak.mail.ui.login.Colors.Companion.color2
import com.infomaniak.mail.ui.login.Colors.Companion.color3
import com.infomaniak.mail.ui.login.Colors.Companion.color4
import com.infomaniak.mail.ui.login.Colors.Companion.color5
import com.infomaniak.mail.ui.login.Colors.Companion.color6
import com.infomaniak.mail.ui.login.Colors.Companion.color7
import com.infomaniak.mail.ui.login.Colors.Companion.color8
import com.infomaniak.mail.ui.login.Colors.Companion.color9
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor1
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor10
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor11
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor12
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor13
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor2
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor3
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor4
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor5
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor6
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor7
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor8
import com.infomaniak.mail.ui.login.Colors.Companion.pinkColor9

class IlluColors(val keyPath: KeyPath, val color: Colors) {
    fun getLightColor() = Color.parseColor(color.light)
    fun getDarkColor() = Color.parseColor(color.dark)

    companion object {
        val illuColors = arrayOf(
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 18", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 22", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 25", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 26", "Fond 1"), color3),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 27", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 28", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 29", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 30", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 31", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 32", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 33", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 34", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 35", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 36", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 37", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 38", "Fond 1"), color2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 39", "Fond 1"), color4),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 44", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 49", "Fond 1"), color4),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 50", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 62", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 68", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 70", "Fond 1"), color1),
        )
        val illuPinkColors = arrayOf(
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), pinkColor2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), pinkColor3),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), pinkColor4),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), pinkColor5),
        )
        val illuBlueColors = arrayOf(
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 1", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 2", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 3", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 4", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 5", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 6", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 9", "Fond 1"), blueColor2),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 12", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 15", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 19", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 20", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 23", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 24", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 43", "Fond 1"), blueColor3),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 48", "Fond 1"), blueColor4),
        )

        val illu1Colors = arrayOf(
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("POINT 5", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("POINT 6", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 56", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), color6),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 69", "Fond 1"), color5),
        )
        val illu1PinkColors = arrayOf(
            IlluColors(KeyPath("CHAT 1", "Groupe 1", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("CHAT 2", "Groupe 1", "Fond 1"), pinkColor4),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), pinkColor6),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 66", "Fond 1"), pinkColor7),
        )
        val illu1BlueColors = arrayOf(
            IlluColors(KeyPath("CHAT 1", "Groupe 1", "Fond 1"), blueColor1),
            IlluColors(KeyPath("CHAT 2", "Groupe 1", "Fond 1"), blueColor3),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 55", "Fond 1"), blueColor5),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 66", "Fond 1"), blueColor6),
        )

        val illu234Colors = arrayOf(
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 73", "Fond 1"), color6),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 74", "Fond 1"), color1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 75", "Fond 1"), color5),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 76", "Fond 1"), color2),
        )
        val illu234PinkColors = arrayOf(
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), pinkColor6),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), pinkColor7),
        )
        val illu234BlueColors = arrayOf(
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), blueColor4),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), blueColor5),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), blueColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), blueColor6),
        )

        val illu2Colors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 5", "Fond 1"), color4),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 6", "Fond 1"), color1),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 9", "Fond 1"), color7),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 10", "Fond 1"), color7),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 12", "Fond 1"), color5),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 13", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 14", "Fond 1"), color1),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 4", "Fond 1"), color8),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 5", "Fond 1"), color8),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 6", "Fond 1"), color8),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 7", "Fond 1"), color5),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 8", "Fond 1"), color2),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 9", "Fond 1"), color2),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 10", "Fond 1"), color2),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 13", "Fond 1"), color5),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 14", "Fond 1"), color8),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 5", "Fond 1"), color4),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 6", "Fond 1"), color1),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 9", "Fond 1"), color7),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 10", "Fond 1"), color7),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 12", "Fond 1"), color5),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 13", "Fond 1"), color2),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 14", "Fond 1"), color1),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 15", "Fond 1"), color1),
            IlluColors(KeyPath("MOVING NOTIF 2 TITLE", "Groupe 1", "Fond 1"), color2),
            IlluColors(KeyPath("MOVING NOTIF 2 PREVIEW", "Groupe 1", "Fond 1"), color2),
            IlluColors(KeyPath("ARCHIVES", "Groupe 1", "Fond 1"), color5),
            IlluColors(KeyPath("ARCHIVES", "Groupe 2", "Fond 1"), color5),
            IlluColors(KeyPath("ARCHIVES", "Groupe 3", "Fond 1"), color5),
            IlluColors(KeyPath("ARCHIVES", "Groupe 4", "Fond 1"), color5),
        )
        val illu2PinkColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 11", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 54", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 61", "Fond 1"), pinkColor6),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 67", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("IPHONE SCREEN", "Groupe 72", "Fond 1"), pinkColor7),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), pinkColor7),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), pinkColor8),
            IlluColors(KeyPath("HAND", "Groupe 5", "Fond 1"), pinkColor8),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 15", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 4", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 11", "Fond 1"), pinkColor1),
        )
        val illu2BlueColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), blueColor4),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 11", "Fond 1"), blueColor1),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), blueColor6),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), blueColor7),
            IlluColors(KeyPath("HAND", "Groupe 5", "Fond 1"), blueColor7),
            IlluColors(KeyPath("MOVING NOTIF 1", "Groupe 15", "Fond 1"), blueColor1),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 4", "Fond 1"), blueColor4),
            IlluColors(KeyPath("MOVING NOTIF 2", "Groupe 11", "Fond 1"), blueColor1),
        )

        val illu3Colors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 1", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 2", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 3", "Fond 1"), color5),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 1", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 2", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 3", "Fond 1"), color5),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 1", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 2", "Fond 1"), color2),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 3", "Fond 1"), color5),
            IlluColors(KeyPath("STAR", "Groupe 2", "Fond 1"), color3),
            IlluColors(KeyPath("BEN", "Groupe 7", "Fond 1"), color3),
            IlluColors(KeyPath("RING", "Groupe 5", "Fond 1"), color3),
        )
        val illu3PinkColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 5", "Fond 1"), pinkColor9),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 4", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 5", "Fond 1"), pinkColor9),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 4", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 5", "Fond 1"), pinkColor9),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), pinkColor7),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), pinkColor8),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), pinkColor8),
            IlluColors(KeyPath("STAR", "Groupe 1", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("BEN", "Groupe 1", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("BEN", "Groupe 2", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("BEN", "Groupe 3", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("BEN", "Groupe 4", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("BEN", "Groupe 5", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("BEN", "Groupe 6", "Contour 1"), pinkColor1),
            IlluColors(KeyPath("RING", "Groupe 1", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("RING", "Groupe 2", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("RING", "Groupe 3", "Fond 1"), pinkColor1),
            IlluColors(KeyPath("RING", "Groupe 4", "Fond 1"), pinkColor1),
        )
        val illu3BlueColors = arrayOf(
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 4", "Fond 1"), blueColor1),
            IlluColors(KeyPath("NOTIFICATION 2", "Groupe 5", "Fond 1"), blueColor8),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 4", "Fond 1"), blueColor1),
            IlluColors(KeyPath("NOTIFICATION 3", "Groupe 5", "Fond 1"), blueColor8),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 4", "Fond 1"), blueColor1),
            IlluColors(KeyPath("NOTIFICATION 4", "Groupe 5", "Fond 1"), blueColor8),
            IlluColors(KeyPath("HAND", "Groupe 1", "Fond 1"), blueColor6),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), blueColor7),
            IlluColors(KeyPath("HAND", "Groupe 4", "Fond 1"), blueColor7),
            IlluColors(KeyPath("STAR", "Groupe 1", "Contour 1"), blueColor1),
            IlluColors(KeyPath("BEN", "Groupe 1", "Contour 1"), blueColor1),
            IlluColors(KeyPath("BEN", "Groupe 2", "Contour 1"), blueColor1),
            IlluColors(KeyPath("BEN", "Groupe 3", "Contour 1"), blueColor1),
            IlluColors(KeyPath("BEN", "Groupe 4", "Contour 1"), blueColor1),
            IlluColors(KeyPath("BEN", "Groupe 5", "Contour 1"), blueColor1),
            IlluColors(KeyPath("BEN", "Groupe 6", "Contour 1"), blueColor1),
            IlluColors(KeyPath("RING", "Groupe 1", "Fond 1"), blueColor1),
            IlluColors(KeyPath("RING", "Groupe 2", "Fond 1"), blueColor1),
            IlluColors(KeyPath("RING", "Groupe 3", "Fond 1"), blueColor1),
            IlluColors(KeyPath("RING", "Groupe 4", "Fond 1"), blueColor1),
        )

        val illu4Colors = arrayOf(
            IlluColors(KeyPath("WOMAN", "Groupe 5", "Fond 1"), color4),
            IlluColors(KeyPath("WOMAN", "Groupe 6", "Fond 1"), color1),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), color4),
            IlluColors(KeyPath("MEN", "Groupe 6", "Fond 1"), color1),
            IlluColors(KeyPath("LETTER", "Groupe 3", "Fond 1"), color9),
            IlluColors(KeyPath("LETTER", "Groupe 4", "Fond 1"), color10),
        )
        val illu4PinkColors = arrayOf(
            IlluColors(KeyPath("WOMAN", "Groupe 4", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), pinkColor4),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), pinkColor5),
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), pinkColor4),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), pinkColor4),
            IlluColors(KeyPath("LETTER", "Groupe 1", "Fond 1"), pinkColor10),
            IlluColors(KeyPath("LETTER", "Groupe 2", "Fond 1"), pinkColor11),
            IlluColors(KeyPath("LETTER", "Groupe 5", "Fond 1"), pinkColor12),
            IlluColors(KeyPath("LETTER", "Groupe 6", "Fond 1"), pinkColor13),
            IlluColors(KeyPath("LETTER", "Groupe 7", "Fond 1"), pinkColor13),
        )
        val illu4BlueColors = arrayOf(
            IlluColors(KeyPath("WOMAN", "Groupe 4", "Fond 1"), blueColor4),
            IlluColors(KeyPath("MEN", "Groupe 5", "Fond 1"), blueColor3),
            IlluColors(KeyPath("POINT 1", "Groupe 1", "Fond 1"), blueColor4),
            IlluColors(KeyPath("POINT 2", "Groupe 1", "Fond 1"), blueColor4),
            IlluColors(KeyPath("POINT 3", "Groupe 1", "Fond 1"), blueColor3),
            IlluColors(KeyPath("POINT 4", "Groupe 1", "Fond 1"), blueColor3),
            IlluColors(KeyPath("LETTER", "Groupe 1", "Fond 1"), blueColor2),
            IlluColors(KeyPath("LETTER", "Groupe 2", "Fond 1"), blueColor9),
            IlluColors(KeyPath("LETTER", "Groupe 5", "Fond 1"), blueColor10),
            IlluColors(KeyPath("LETTER", "Groupe 6", "Fond 1"), blueColor11),
            IlluColors(KeyPath("LETTER", "Groupe 7", "Fond 1"), blueColor11),
        )
    }
}
