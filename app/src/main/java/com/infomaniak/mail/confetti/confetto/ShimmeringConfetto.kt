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

import android.animation.ArgbEvaluator
import android.graphics.*
import android.os.SystemClock
import java.util.Random
import kotlin.math.abs

class ShimmeringConfetto(
    bitmap: Bitmap?,
    private val fromColor: Int,
    private val toColor: Int,
    private val waveLength: Long,
    random: Random,
) : BitmapConfetto(bitmap!!) {

    private val evaluator = ArgbEvaluator()
    private val halfWaveLength: Long = waveLength / 2L
    private val randomStart: Long

    init {
        val currentTime = abs(SystemClock.elapsedRealtime().toInt())
        randomStart = (currentTime - random.nextInt(currentTime)).toLong()
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
        val currentTime = SystemClock.elapsedRealtime()
        val fraction = (currentTime - randomStart) % waveLength
        val animated = if (fraction < halfWaveLength) {
            fraction.toFloat() / halfWaveLength
        } else {
            (waveLength.toFloat() - fraction) / halfWaveLength
        }
        val color = evaluator.evaluate(animated, fromColor, toColor) as Int
        paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        super.drawInternal(canvas, matrix, paint, x, y, rotation, percentageAnimated)
    }
}
