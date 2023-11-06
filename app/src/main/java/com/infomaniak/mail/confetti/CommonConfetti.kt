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

import android.content.res.Resources
import android.graphics.Rect
import android.view.ViewGroup
import com.infomaniak.mail.R
import com.infomaniak.mail.confetti.Utils.defaultAlphaInterpolator
import com.infomaniak.mail.confetti.Utils.generateConfettiBitmaps
import com.infomaniak.mail.confetti.confetto.BitmapConfetto
import com.infomaniak.mail.confetti.confetto.Confetto
import java.util.Random

class CommonConfetti private constructor(container: ViewGroup) {

    var confettiManager: ConfettiManager? = null
        private set

    init {
        ensureStaticResources(container.resources)
    }

    /**
     * Starts a one-shot animation that emits all of the confetti at once.
     *
     * @return the resulting [ConfettiManager] that's performing the animation.
     */
    fun oneShot(): ConfettiManager = confettiManager!!
        .setNumInitialCount(100)
        .setEmissionDuration(0L)
        .animate(false)

    /**
     * Starts a stream of confetti that animates for the provided duration.
     *
     * @param durationInMillis how long to animate the confetti for.
     * @return the resulting [ConfettiManager] that's performing the animation.
     */
    fun stream(durationInMillis: Long): ConfettiManager = confettiManager!!
        .setNumInitialCount(0)
        .setEmissionDuration(durationInMillis)
        .setEmissionRate(50.0f)
        .animate(false)

    /**
     * Starts an infinite stream of confetti.
     *
     * @return the resulting [ConfettiManager] that's performing the animation.
     */
    fun infinite(): ConfettiManager = confettiManager!!
        .setNumInitialCount(0)
        .setEmissionDuration(ConfettiManager.INFINITE_DURATION)
        .setEmissionRate(50.0f)
        .animate(false)

    private fun getDefaultGenerator(colors: IntArray): ConfettoGenerator {
        val bitmaps = generateConfettiBitmaps(colors, defaultConfettiSize)
        val numBitmaps = bitmaps.size
        return object : ConfettoGenerator {
            override fun generateConfetto(random: Random): Confetto = BitmapConfetto(bitmaps[random.nextInt(numBitmaps)])
        }
    }

    private fun configureRainingConfetti(container: ViewGroup, confettiSource: ConfettiSource, colors: IntArray) {
        val generator = getDefaultGenerator(colors)
        confettiManager = ConfettiManager(container.context, generator, confettiSource, container)
            .setVelocityX(0.0f, defaultVelocitySlow.toFloat())
            .setVelocityY(defaultVelocityNormal.toFloat(), defaultVelocitySlow.toFloat())
            .setInitialRotation(180, 180)
            .setRotationalAcceleration(360.0f, 180.0f)
            .setTargetRotationalVelocity(360.0f)
    }

    private fun configureExplosion(container: ViewGroup, x: Int, y: Int, colors: IntArray) {
        val generator = getDefaultGenerator(colors)
        val confettiSource = ConfettiSource(x, y)
        confettiManager = ConfettiManager(container.context, generator, confettiSource, container)
            .setTTL(1_000L)
            .setBound(Rect(x - explosionRadius, y - explosionRadius, x + explosionRadius, y + explosionRadius))
            .setVelocityX(0.0f, defaultVelocityFast.toFloat())
            .setVelocityY(0.0f, defaultVelocityFast.toFloat())
            .enableFadeOut(defaultAlphaInterpolator)
            .setInitialRotation(180, 180)
            .setRotationalAcceleration(360.0f, 180.0f)
            .setTargetRotationalVelocity(360.0f)
    }

    companion object {

        private var defaultConfettiSize = 0
        private var defaultVelocitySlow = 0
        private var defaultVelocityNormal = 0
        private var defaultVelocityFast = 0
        private var explosionRadius = 0

        // region Pre-configured confetti animations
        /**
         * @param container the container viewgroup to host the confetti animation.
         * @param colors    the set of colors to colorize the confetti bitmaps.
         * @return the created common confetti object.
         * @see .rainingConfetti
         */
        fun rainingConfetti(
            container: ViewGroup,
            colors: IntArray,
        ) = CommonConfetti(container).apply {
            val confettiSource = ConfettiSource(0, -defaultConfettiSize, container.width, -defaultConfettiSize)
            configureRainingConfetti(container, confettiSource, colors)
        }

        /**
         * Configures a confetti manager that has confetti falling from the provided confetti source.
         *
         * @param container      the container viewgroup to host the confetti animation.
         * @param confettiSource the source of the confetti animation.
         * @param colors         the set of colors to colorize the confetti bitmaps.
         * @return the created common confetti object.
         */
        fun rainingConfetti(
            container: ViewGroup,
            confettiSource: ConfettiSource,
            colors: IntArray,
        ) = CommonConfetti(container).apply {
            configureRainingConfetti(container, confettiSource, colors)
        }

        /**
         * Configures a confetti manager that has confetti exploding out in all directions from the
         * provided x and y coordinates.
         *
         * @param container the container viewgroup to host the confetti animation.
         * @param x         the x coordinate of the explosion source.
         * @param y         the y coordinate of the explosion source.
         * @param colors    the set of colors to colorize the confetti bitmaps.
         * @return the created common confetti object.
         */
        fun explosion(
            container: ViewGroup,
            x: Int,
            y: Int,
            colors: IntArray,
        ) = CommonConfetti(container).apply {
            configureExplosion(container, x, y, colors)
        }
        // endregion

        private fun ensureStaticResources(resources: Resources) = with(resources) {
            if (defaultConfettiSize == 0) {
                defaultConfettiSize = getDimensionPixelSize(R.dimen.confetti_size)
                defaultVelocitySlow = getDimensionPixelOffset(R.dimen.confetti_velocity_slow)
                defaultVelocityNormal = getDimensionPixelOffset(R.dimen.confetti_velocity_normal)
                defaultVelocityFast = getDimensionPixelOffset(R.dimen.confetti_velocity_fast)
                explosionRadius = getDimensionPixelOffset(R.dimen.confetti_explosion_radius)
            }
        }
    }
}
