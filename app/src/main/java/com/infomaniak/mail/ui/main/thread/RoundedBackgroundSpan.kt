/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.LineHeightSpan
import android.text.style.ReplacementSpan
import androidx.core.content.res.ResourcesCompat
import com.infomaniak.mail.R

/**
 * A span to create a rounded background on a text.
 *
 * If radius is set, it generates a rounded background.
 * If radius is null, it generates a circle background.
 */
class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val textColor: Int,
    private val textTypeface: Typeface,
    private val fontSize: Float,
    private val cornerRadius: Float = CORNER_RADIUS
) : ReplacementSpan(), LineHeightSpan {

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: FontMetricsInt?): Int {
        paint.setGivenTextStyle()
        return (LEFT_MARGIN + PADDING + paint.measureText(text, start, end) + PADDING).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint,
    ) {
        paint.setGivenTextStyle()
        val width = paint.measureText(text, start, end)

        val rect = RectF(
            /* left = */ LEFT_MARGIN + x,
            /* top = */ top.toFloat() + VERTICAL_OFFSET,
            /* right = */ LEFT_MARGIN + x + width + 2 * PADDING,
            /* bottom = */ bottom.toFloat() - VERTICAL_OFFSET,
        )

        paint.color = backgroundColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)

        paint.setGivenTextStyle()
        canvas.drawText(
            /* text = */ text,
            /* start = */ start,
            /* end = */ end,
            /* x = */ LEFT_MARGIN + x + PADDING,
            /* y = */ y.toFloat() - VERTICAL_OFFSET,
            /* paint = */ paint,
        )
    }

    private fun Paint.setGivenTextStyle() {
        color = textColor
        typeface = textTypeface
        textSize = fontSize
    }

    override fun chooseHeight(
        text: CharSequence?,
        start: Int,
        end: Int,
        spanstartv: Int,
        lineHeight: Int,
        fm: FontMetricsInt?,
    ) = Unit

    companion object {
        private const val LEFT_MARGIN = 4
        private const val PADDING = 16
        private const val VERTICAL_OFFSET = 4
        private const val CORNER_RADIUS = 10f

        fun getTagsPaint(context: Context) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ResourcesCompat.getColor(context.resources, R.color.folderNameTextColor, null)
            textSize = context.resources.getDimension(R.dimen.externalTagTextSize)
            typeface = ResourcesCompat.getFont(context, R.font.tag_font)
        }
    }
}
