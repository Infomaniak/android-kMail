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

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

open class BitmapConfetto(private val bitmap: Bitmap) : Confetto() {

    private val bitmapCenterX: Float = bitmap.width / 2.0f
    private val bitmapCenterY: Float = bitmap.height / 2.0f

    override fun getWidth(): Int = bitmap.width

    override fun getHeight(): Int = bitmap.height

    override fun drawInternal(
        canvas: Canvas,
        matrix: Matrix,
        paint: Paint,
        x: Float,
        y: Float,
        rotation: Float,
        percentageAnimated: Float,
    ) {
        matrix.preTranslate(x, y)
        matrix.preRotate(rotation, bitmapCenterX, bitmapCenterY)
        canvas.drawBitmap(bitmap, matrix, paint)
    }
}
