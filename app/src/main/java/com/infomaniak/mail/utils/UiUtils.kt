/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.mail.utils

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Window
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.toColor
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.utils.extensions.updateNavigationBarColor

object UiUtils {

    const val FULLY_SLID = 1.0f

    @ColorInt
    fun pointBetweenColors(@ColorInt from: Int, @ColorInt to: Int, percent: Float): Int {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            pointBetweenColors25(from, to, percent)
        } else {
            val fromColor = from.toColor()
            val toColor = to.toColor()
            Color.pack(
                pointBetweenColor(fromColor.red(), toColor.red(), percent),
                pointBetweenColor(fromColor.green(), toColor.green(), percent),
                pointBetweenColor(fromColor.blue(), toColor.blue(), percent),
                pointBetweenColor(fromColor.alpha(), toColor.alpha(), percent),
            ).toColorInt()
        }
    }

    private fun pointBetweenColor(from: Float, to: Float, percent: Float): Float = from + percent * (to - from)

    // TODO: Delete everything when API 25 is not supported anymore.
    private fun pointBetweenColors25(from: Int, to: Int, percent: Float): Int {
        data class Color(
            @ColorInt val a: Int,
            @ColorInt val r: Int,
            @ColorInt val g: Int,
            @ColorInt val b: Int,
        ) {
            fun toColorInt(): Int = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun toColor(@ColorInt color: Int): Color {
            val r = (color shr 16 and 0xff)
            val g = (color shr 8 and 0xff)
            val b = (color and 0xff)
            val a = (color shr 24 and 0xff)
            return Color(a, r, g, b)
        }

        fun pointBetweenColor(from: Int, to: Int, percent: Float): Int = (from + percent * (to - from)).toInt()

        val fromColor = toColor(from)
        val toColor = toColor(to)

        return Color(
            pointBetweenColor(fromColor.a, toColor.a, percent),
            pointBetweenColor(fromColor.r, toColor.r, percent),
            pointBetweenColor(fromColor.g, toColor.g, percent),
            pointBetweenColor(fromColor.b, toColor.b, percent),
        ).toColorInt()
    }

    fun Window.progressivelyColorSystemBars(
        @FloatRange(0.0, 1.0) slideOffset: Float,
        @ColorInt statusBarColorFrom: Int,
        @ColorInt statusBarColorTo: Int,
        @ColorInt navBarColorFrom: Int,
        @ColorInt navBarColorTo: Int,
    ) {
        if (slideOffset == FULLY_SLID) {
            statusBarColor = statusBarColorTo
            updateNavigationBarColor(navBarColorTo)
        } else {
            statusBarColor = pointBetweenColors(statusBarColorFrom, statusBarColorTo, slideOffset)
            updateNavigationBarColor(pointBetweenColors(navBarColorFrom, navBarColorTo, slideOffset))
        }
    }

    fun formatUnreadCount(unread: Int) = if (unread >= 100) "99+" else unread.toString()

    fun Context.getPrettyNameAndEmail(
        correspondent: Correspondent,
        ignoreIsMe: Boolean = false,
    ): Pair<String, String?> = with(correspondent) {
        return when {
            isMe() && !ignoreIsMe -> getString(R.string.contactMe) to email
            name.isBlank() || name == email -> email to null
            else -> name to email
        }
    }

    fun fillInUserNameAndEmail(
        correspondent: Correspondent,
        nameTextView: TextView,
        emailTextView: TextView,
        ignoreIsMe: Boolean = false,
    ): Boolean = with(correspondent) {
        val (name, email) = nameTextView.context.getPrettyNameAndEmail(correspondent, ignoreIsMe)
        nameTextView.text = name

        val filledSingleField = email == null
        emailTextView.apply {
            text = email
            isGone = filledSingleField
        }

        filledSingleField
    }

    fun animateColorChange(
        @ColorInt oldColor: Int,
        @ColorInt newColor: Int,
        duration: Long = 150L,
        animate: Boolean = true,
        applyColor: (color: Int) -> Unit,
    ): ValueAnimator? {
        return if (animate) {
            ValueAnimator.ofObject(ArgbEvaluator(), oldColor, newColor).apply {
                setDuration(duration)
                addUpdateListener { animator -> applyColor(animator.animatedValue as Int) }
                start()
            }
        } else {
            applyColor(newColor)
            null
        }
    }

    fun dividerDrawable(context: Context) = AppCompatResources.getDrawable(context, R.drawable.divider)
}
