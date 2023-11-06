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

/**
 * The source from which confetti will appear. This can be either a line or a point.
 *
 * Please note that the specified source represents the top left corner of the drawn
 * confetti. If you want the confetti to appear from off-screen, you'll have to offset it
 * with the confetti's size.
 *
 * Specifies a line source from which all confetti will emit from.
 *
 * @param x0 x-coordinate of the first point relative to the [ConfettiView]'s parent.
 * @param y0 y-coordinate of the first point relative to the [ConfettiView]'s parent.
 * @param x1 x-coordinate of the second point relative to the [ConfettiView]'s parent.
 * @param y1 y-coordinate of the second point relative to the [ConfettiView]'s parent.
 */
class ConfettiSource(private val x0: Int, private val y0: Int, private val x1: Int, private val y1: Int) {

    /**
     * Specifies a point source from which all confetti will emit from.
     *
     * @param x x-coordinate of the point relative to the [ConfettiView]'s parent.
     * @param y y-coordinate of the point relative to the [ConfettiView]'s parent.
     */
    constructor(x: Int, y: Int) : this(x, y, x, y)

    fun getInitialX(random: Float): Float = x0 + (x1 - x0) * random

    fun getInitialY(random: Float): Float = y0 + (y1 - y0) * random
}
