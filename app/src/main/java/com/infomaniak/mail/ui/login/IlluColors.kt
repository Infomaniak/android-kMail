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

import androidx.annotation.ColorInt
import com.airbnb.lottie.model.KeyPath

class IlluColors(val keyPath: KeyPath, @ColorInt val color: Int) {
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
    }
}
