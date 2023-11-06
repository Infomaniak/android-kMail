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
package com.infomaniak.mail.confetti.confetto

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * A lightly more optimal way to draw a circle shape that doesn't require the use of a bitmap.
 */
class CircleConfetto(private val color: Int, private val radius: Float) : Confetto() {

    private val diameter: Int = (radius * 2.0f).toInt()

    override fun getWidth(): Int = diameter

    override fun getHeight(): Int = diameter

    override fun configurePaint(paint: Paint) {
        super.configurePaint(paint)
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    override fun drawInternal(
        canvas: Canvas,
        matrix: Matrix,
        paint: Paint,
        x: Float,
        y: Float,
        rotation: Float,
        percentageAnimated: Float,
    ) {
        canvas.drawCircle(x + radius, y + radius, radius, paint)
    }
}
