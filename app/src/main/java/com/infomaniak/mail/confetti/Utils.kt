/**
 * Copyright (C) 2016 Robinhood Markets, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.infomaniak.mail.confetti

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.animation.Interpolator
import kotlin.math.tan

object Utils {

    private val paint = Paint().apply { style = Paint.Style.FILL }

    @JvmStatic
    var defaultAlphaInterpolator: Interpolator? = null
        get() {
            if (field == null) field = Interpolator { v: Float -> if (v >= 0.9f) 1.0f - (v - 0.9f) * 10.0f else 1.0f }
            return field
        }
        private set

    @JvmStatic
    fun generateConfettiBitmaps(colors: IntArray, size: Int): List<Bitmap> = mutableListOf<Bitmap>().apply {
        for (color in colors) {
            add(createCircleBitmap(color, size))
            add(createSquareBitmap(color, size))
            add(createTriangleBitmap(color, size))
        }
    }

    fun createCircleBitmap(color: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = size / 2.0f
        paint.color = color
        canvas.drawCircle(radius, radius, radius, paint)
        return bitmap
    }

    fun createSquareBitmap(color: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val path = Path().apply {
            moveTo(0.0f, 0.0f)
            lineTo(size.toFloat(), 0.0f)
            lineTo(size.toFloat(), size.toFloat())
            lineTo(0.0f, size.toFloat())
            close()
        }
        paint.color = color
        canvas.drawPath(path, paint)
        return bitmap
    }

    fun createTriangleBitmap(color: Int, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // Generate equilateral triangle (http://mathworld.wolfram.com/EquilateralTriangle.html).
        val path = Path().apply {
            val point = tan(15.0f / 180.0f * Math.PI).toFloat() * size
            moveTo(0.0f, 0.0f)
            lineTo(size.toFloat(), point)
            lineTo(point, size.toFloat())
            close()
        }
        paint.color = color
        canvas.drawPath(path, paint)
        return bitmap
    }
}
