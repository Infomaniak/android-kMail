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
package com.infomaniak.mail.utils

import android.content.Context
import android.view.ViewGroup
import com.infomaniak.mail.R
import com.infomaniak.mail.confetti.CommonConfetti
import com.infomaniak.mail.confetti.ConfettiSource
import io.sentry.Sentry
import io.sentry.SentryLevel

object ConfettiUtils {

    private const val EMISSION = 1_000_000.0f

    fun triggerEasterEggConfetti(from: String, container: ViewGroup, context: Context): Unit = with(context) {

        Sentry.withScope { scope ->
            scope.level = SentryLevel.INFO
            scope.setTag("from", from)
            Sentry.captureMessage("Easter egg Confetti has been triggered! Woohoo!")
        }

        val none = 0.0f
        val verySlow = resources.getDimensionPixelOffset(R.dimen.confetti_velocity_very_slow).toFloat()
        val slow = resources.getDimensionPixelOffset(R.dimen.confetti_velocity_slow).toFloat()
        val normal = resources.getDimensionPixelOffset(R.dimen.confetti_velocity_normal).toFloat()
        val fast = resources.getDimensionPixelOffset(R.dimen.confetti_velocity_fast).toFloat()
        val veryFast = resources.getDimensionPixelOffset(R.dimen.confetti_velocity_very_fast).toFloat()
        val ultraFast = resources.getDimensionPixelOffset(R.dimen.confetti_velocity_ultra_fast).toFloat()

        fun displayTada() {
            val config = ConfettiConfig(
                duration = 100L,
                velocityX = none,
                velocityDeviationX = fast,
                velocityY = -ultraFast,
                velocityDeviationY = veryFast,
                accelerationY = ultraFast,
                colors = resources.getIntArray(R.array.pinkColors),
            )
            val source = ConfettiSource(container.width / 2, container.height)
            displayConfetti(config, source, container)
        }

        fun displaySnow() {
            val config = ConfettiConfig(
                duration = 5_000L,
                velocityX = none,
                velocityDeviationX = slow,
                velocityY = normal,
                velocityDeviationY = slow,
                accelerationY = none,
                colors = resources.getIntArray(R.array.snowColors),
                useGaussian = false,
            )
            val size = resources.getDimensionPixelOffset(R.dimen.confetti_size)
            val source = ConfettiSource(0, -size, container.width, -size)
            displayConfetti(config, source, container)
        }

        fun displaySingleGeneva() {
            val config = ConfettiConfig(
                duration = 1_000L,
                velocityX = none,
                velocityDeviationX = verySlow,
                velocityY = -ultraFast,
                velocityDeviationY = veryFast,
                accelerationY = ultraFast,
                colors = resources.getIntArray(R.array.blueColors),
            )
            val source = ConfettiSource(container.width / 2, container.height)
            displayConfetti(config, source, container)
        }

        fun displayDoubleGeneva() {
            // Left
            val config = ConfettiConfig(
                duration = 666L,
                velocityX = veryFast,
                velocityDeviationX = verySlow,
                velocityY = -ultraFast,
                velocityDeviationY = veryFast,
                accelerationY = ultraFast,
                colors = resources.getIntArray(R.array.blueColors),
                dualMode = true,
            )
            val sourceLeft = ConfettiSource(0, container.height)
            displayConfetti(config, sourceLeft, container)

            // Right
            config.velocityX = -config.velocityX
            val sourceRight = ConfettiSource(container.width, container.height)
            displayConfetti(config, sourceRight, container)
        }

        when ((0..2).random()) {
            0 -> displayTada()
            1 -> displaySnow()
            else -> if (isInPortrait()) {
                displaySingleGeneva()
            } else {
                displayDoubleGeneva()
            }
        }
    }

    private fun displayConfetti(config: ConfettiConfig, source: ConfettiSource, container: ViewGroup) = with(config) {
        CommonConfetti.rainingConfetti(container, source, colors)
            .confettiManager
            .setNumInitialCount(0)
            .setEmissionDuration(duration)
            .setEmissionRate(EMISSION / duration.toFloat())
            .setVelocityX(velocityX, velocityDeviationX)
            .setVelocityY(velocityY, velocityDeviationY)
            .setAccelerationY(accelerationY)
            .animate(useGaussian)
    }

    @Suppress("ArrayInDataClass")
    private data class ConfettiConfig(
        var duration: Long,
        var velocityX: Float,
        var velocityDeviationX: Float,
        var velocityY: Float,
        var velocityDeviationY: Float,
        var accelerationY: Float,
        var colors: IntArray,
        var dualMode: Boolean = false,
        var useGaussian: Boolean = true,
    )
}
