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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.view.View.OnAttachStateChangeListener
import android.view.ViewGroup
import android.view.animation.Interpolator
import com.infomaniak.mail.confetti.confetto.Confetto
import java.util.LinkedList
import java.util.Queue
import java.util.Random
import kotlin.math.roundToInt

/**
 * A helper manager class for configuring a set of confetti and displaying them on the UI.
 */
class ConfettiManager(
    private val confettoGenerator: ConfettoGenerator,
    private val confettiSource: ConfettiSource,
    private val parentView: ViewGroup,
    private val confettiView: ConfettiView,
) {

    private val random = Random()
    private val recycledConfetti: Queue<Confetto> = LinkedList()
    private val confetti: MutableList<Confetto> = ArrayList(300)
    private var animator: ValueAnimator? = null
    private var lastEmittedTimestamp: Long = 0
    // All of the below configured values are in milliseconds despite the setter methods take them
    // in seconds as the parameters. The parameters for the setters are in seconds to allow for
    // users to better understand/visualize the dimensions.
    // Configured attributes for the entire confetti group
    private var numInitialCount = 0
    private var emissionDuration: Long = 0
    private var emissionRate = 0f
    private var emissionRateInverse = 0f
    private var fadeOutInterpolator: Interpolator? = null
    private var bound: Rect
    // Configured attributes for each confetto
    private var velocityX = 0f
    private var velocityDeviationX = 0f
    private var velocityY = 0f
    private var velocityDeviationY = 0f
    private var accelerationX = 0f
    private var accelerationDeviationX = 0f
    private var accelerationY = 0f
    private var accelerationDeviationY = 0f
    private var targetVelocityX: Float? = null
    private var targetVelocityXDeviation: Float? = null
    private var targetVelocityY: Float? = null
    private var targetVelocityYDeviation: Float? = null
    private var initialRotation = 0
    private var initialRotationDeviation = 0
    private var rotationalVelocity = 0f
    private var rotationalVelocityDeviation = 0f
    private var rotationalAcceleration = 0f
    private var rotationalAccelerationDeviation = 0f
    private var targetRotationalVelocity: Float? = null
    private var targetRotationalVelocityDeviation: Float? = null
    private var ttl: Long
    private var animationListener: ConfettiAnimationListener? = null

    constructor(
        context: Context,
        confettoGenerator: ConfettoGenerator,
        confettiSource: ConfettiSource,
        parentView: ViewGroup,
    ) : this(confettoGenerator, confettiSource, parentView, ConfettiView(context))

    init {

        confettiView.apply {
            bind(confetti)
            addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    // No-op
                }

                override fun onViewDetachedFromWindow(v: View) {
                    terminate()
                }
            })
        }

        // Set the defaults
        ttl = -1
        bound = Rect(0, 0, parentView.width, parentView.height)
    }

    /**
     * The number of confetti initially emitted before any time has elapsed.
     *
     * @param numInitialCount the number of initial confetti.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setNumInitialCount(numInitialCount: Int) = apply {
        this.numInitialCount = numInitialCount
    }

    /**
     * Configures how long this manager will emit new confetti after the animation starts.
     *
     * @param emissionDurationInMillis how long to emit new confetti in millis. This value can be
     * [.INFINITE_DURATION] for a never-ending emission.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setEmissionDuration(emissionDurationInMillis: Long) = apply {
        emissionDuration = emissionDurationInMillis
    }

    /**
     * Configures how frequently this manager will emit new confetti after the animation starts
     * if [.emissionDuration] is a positive value.
     *
     * @param emissionRate the rate of emission in # of confetti per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setEmissionRate(emissionRate: Float) = apply {
        this.emissionRate = emissionRate / 1_000.0f
        emissionRateInverse = 1.0f / this.emissionRate
    }

    /**
     * @param velocityX the X velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setVelocityX
     */
    fun setVelocityX(velocityX: Float) = setVelocityX(velocityX, velocityDeviationX = 0.0f)

    /**
     * Set the velocityX used by this manager. This value defines the initial X velocity
     * for the generated confetti. The actual confetti's X velocity will be
     * (velocityX +- [0, velocityDeviationX]).
     *
     * @param velocityX          the X velocity in pixels per second.
     * @param velocityDeviationX the deviation from X velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setVelocityX(velocityX: Float, velocityDeviationX: Float) = apply {
        this.velocityX = velocityX / 1_000.0f
        this.velocityDeviationX = velocityDeviationX / 1_000.0f
    }

    /**
     * @param velocityY the Y velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setVelocityY
     */
    fun setVelocityY(velocityY: Float) = setVelocityY(velocityY, velocityDeviationY = 0.0f)

    /**
     * Set the velocityY used by this manager. This value defines the initial Y velocity
     * for the generated confetti. The actual confetti's Y velocity will be
     * (velocityY +- [0, velocityDeviationY]). A positive Y velocity means that the velocity
     * is going down (because Y coordinate increases going down).
     *
     * @param velocityY          the Y velocity in pixels per second.
     * @param velocityDeviationY the deviation from Y velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setVelocityY(velocityY: Float, velocityDeviationY: Float) = apply {
        this.velocityY = velocityY / 1_000.0f
        this.velocityDeviationY = velocityDeviationY / 1_000.0f
    }

    /**
     * @param accelerationX the X acceleration in pixels per second^2.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setAccelerationX
     */
    fun setAccelerationX(accelerationX: Float) = setAccelerationX(accelerationX, accelerationDeviationX = 0.0f)

    /**
     * Set the accelerationX used by this manager. This value defines the X acceleration
     * for the generated confetti. The actual confetti's X acceleration will be
     * (accelerationX +- [0, accelerationDeviationX]).
     *
     * @param accelerationX          the X acceleration in pixels per second^2.
     * @param accelerationDeviationX the deviation from X acceleration in pixels per second^2.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setAccelerationX(accelerationX: Float, accelerationDeviationX: Float) = apply {
        this.accelerationX = accelerationX / 1_000_000.0f
        this.accelerationDeviationX = accelerationDeviationX / 1_000_000.0f
    }

    /**
     * @param accelerationY the Y acceleration in pixels per second^2.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setAccelerationY
     */
    fun setAccelerationY(accelerationY: Float) = setAccelerationY(accelerationY, accelerationDeviationY = 0.0f)

    /**
     * Set the accelerationY used by this manager. This value defines the Y acceleration
     * for the generated confetti. The actual confetti's Y acceleration will be
     * (accelerationY +- [0, accelerationDeviationY]). A positive Y acceleration means that the
     * confetto will be accelerating downwards.
     *
     * @param accelerationY          the Y acceleration in pixels per second^2.
     * @param accelerationDeviationY the deviation from Y acceleration in pixels per second^2.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setAccelerationY(accelerationY: Float, accelerationDeviationY: Float) = apply {
        this.accelerationY = accelerationY / 1_000_000.0f
        this.accelerationDeviationY = accelerationDeviationY / 1_000_000.0f
    }

    /**
     * @param targetVelocityX the target X velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setTargetVelocityX
     */
    fun setTargetVelocityX(targetVelocityX: Float) = setTargetVelocityX(targetVelocityX, targetVelocityXDeviation = 0.0f)

    /**
     * Set the target X velocity that confetti can reach during the animation. The actual confetti's
     * target X velocity will be (targetVelocityX +- [0, targetVelocityXDeviation]).
     *
     * @param targetVelocityX          the target X velocity in pixels per second.
     * @param targetVelocityXDeviation the deviation from target X velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setTargetVelocityX(targetVelocityX: Float, targetVelocityXDeviation: Float) = apply {
        this.targetVelocityX = targetVelocityX / 1_000.0f
        this.targetVelocityXDeviation = targetVelocityXDeviation / 1_000.0f
    }

    /**
     * @param targetVelocityY the target Y velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setTargetVelocityY
     */
    fun setTargetVelocityY(targetVelocityY: Float) = setTargetVelocityY(targetVelocityY, targetVelocityYDeviation = 0.0f)

    /**
     * Set the target Y velocity that confetti can reach during the animation. The actual confetti's
     * target Y velocity will be (targetVelocityY +- [0, targetVelocityYDeviation]).
     *
     * @param targetVelocityY          the target Y velocity in pixels per second.
     * @param targetVelocityYDeviation the deviation from target Y velocity in pixels per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setTargetVelocityY(targetVelocityY: Float, targetVelocityYDeviation: Float) = apply {
        this.targetVelocityY = targetVelocityY / 1_000.0f
        this.targetVelocityYDeviation = targetVelocityYDeviation / 1_000.0f
    }

    /**
     * @param initialRotation the initial rotation in degrees.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setInitialRotation
     */
    fun setInitialRotation(initialRotation: Int) = setInitialRotation(initialRotation, initialRotationDeviation = 0)

    /**
     * Set the initialRotation used by this manager. This value defines the initial rotation in
     * degrees for the generated confetti. The actual confetti's initial rotation will be
     * (initialRotation +- [0, initialRotationDeviation]).
     *
     * @param initialRotation          the initial rotation in degrees.
     * @param initialRotationDeviation the deviation from initial rotation in degrees.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setInitialRotation(initialRotation: Int, initialRotationDeviation: Int) = apply {
        this.initialRotation = initialRotation
        this.initialRotationDeviation = initialRotationDeviation
    }

    /**
     * @param rotationalVelocity the initial rotational velocity in degrees per second.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setRotationalVelocity
     */
    fun setRotationalVelocity(rotationalVelocity: Float) =
        setRotationalVelocity(rotationalVelocity, rotationalVelocityDeviation = 0.0f)

    /**
     * Set the rotationalVelocity used by this manager. This value defines the the initial
     * rotational velocity for the generated confetti. The actual confetti's initial
     * rotational velocity will be (rotationalVelocity +- [0, rotationalVelocityDeviation]).
     *
     * @param rotationalVelocity          the initial rotational velocity in degrees per second.
     * @param rotationalVelocityDeviation the deviation from initial rotational velocity in
     * degrees per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setRotationalVelocity(rotationalVelocity: Float, rotationalVelocityDeviation: Float) = apply {
        this.rotationalVelocity = rotationalVelocity / 1_000.0f
        this.rotationalVelocityDeviation = rotationalVelocityDeviation / 1_000.0f
    }

    /**
     * @param rotationalAcceleration the rotational acceleration in degrees per second^2.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setRotationalAcceleration
     */
    fun setRotationalAcceleration(rotationalAcceleration: Float) =
        setRotationalAcceleration(rotationalAcceleration, rotationalAccelerationDeviation = 0.0f)

    /**
     * Set the rotationalAcceleration used by this manager. This value defines the the
     * acceleration of the rotation for the generated confetti. The actual confetti's rotational
     * acceleration will be (rotationalAcceleration +- [0, rotationalAccelerationDeviation]).
     *
     * @param rotationalAcceleration          the rotational acceleration in degrees per second^2.
     * @param rotationalAccelerationDeviation the deviation from rotational acceleration in degrees
     * per second^2.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setRotationalAcceleration(rotationalAcceleration: Float, rotationalAccelerationDeviation: Float) = apply {
        this.rotationalAcceleration = rotationalAcceleration / 1_000_000.0f
        this.rotationalAccelerationDeviation = rotationalAccelerationDeviation / 1_000_000.0f
    }

    /**
     * @param targetRotationalVelocity the target rotational velocity in degrees per second.
     * @return the confetti manager so that the set calls can be chained.
     * @see .setTargetRotationalVelocity
     */
    fun setTargetRotationalVelocity(targetRotationalVelocity: Float) =
        setTargetRotationalVelocity(targetRotationalVelocity, targetRotationalVelocityDeviation = 0.0f)

    /**
     * Set the target rotational velocity that confetti can reach during the animation. The actual
     * confetti's target rotational velocity will be
     * (targetRotationalVelocity +- [0, targetRotationalVelocityDeviation]).
     *
     * @param targetRotationalVelocity          the target rotational velocity in degrees per second.
     * @param targetRotationalVelocityDeviation the deviation from target rotational velocity
     * in degrees per second.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setTargetRotationalVelocity(targetRotationalVelocity: Float, targetRotationalVelocityDeviation: Float) = apply {
        this.targetRotationalVelocity = targetRotationalVelocity / 1_000.0f
        this.targetRotationalVelocityDeviation = targetRotationalVelocityDeviation / 1_000.0f
    }

    /**
     * Specifies a custom bound that the confetti will clip to. By default, the confetti will be
     * able to animate throughout the entire screen. The dimensions specified in bound is
     * global dimensions, e.g. x=0 is the top of the screen, rather than relative dimensions.
     *
     * @param bound the bound that clips the confetti as they animate.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setBound(bound: Rect) = apply {
        this.bound = bound
    }

    /**
     * Specifies a custom time to live for the confetti generated by this manager. When a confetti
     * reaches its time to live timer, it will disappear and terminate its animation.
     *
     *
     * The time to live value does not include the initial delay of the confetti.
     *
     * @param ttlInMillis the custom time to live in milliseconds.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setTTL(ttlInMillis: Long) = apply {
        ttl = ttlInMillis
    }

    /**
     * Enables fade out for all of the confetti generated by this manager. Fade out means that
     * the confetti will animate alpha according to the fadeOutInterpolator according
     * to its TTL or, if TTL is not set, its bounds.
     *
     * @param fadeOutInterpolator an interpolator that interpolates animation progress [0, 1] into
     * an alpha value [0, 1], 0 being transparent and 1 being opaque.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun enableFadeOut(fadeOutInterpolator: Interpolator?) = apply {
        this.fadeOutInterpolator = fadeOutInterpolator
    }

    /**
     * Disables fade out for all of the confetti generated by this manager.
     *
     * @return the confetti manager so that the set calls can be chained.
     */
    fun disableFadeOut() = apply {
        fadeOutInterpolator = null
    }

    /**
     * Enables or disables touch events for the confetti generated by this manager. By enabling
     * touch, the user can touch individual confetto and drag/fling them on the screen independent
     * of their original animation state.
     *
     * @param touchEnabled whether or not to enable touch.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setTouchEnabled(touchEnabled: Boolean) = apply {
        confettiView.setTouchEnabled(touchEnabled)
    }

    /**
     * Sets a [ConfettiAnimationListener] for this confetti manager.
     *
     * @param listener the animation listener, or null to clear out the existing listener.
     * @return the confetti manager so that the set calls can be chained.
     */
    fun setConfettiAnimationListener(listener: ConfettiAnimationListener?) = apply {
        animationListener = listener
    }

    /**
     * Start the confetti animation configured by this manager.
     *
     * @return the confetti manager itself that just started animating.
     */
    fun animate(useGaussian: Boolean) = apply {
        if (animationListener != null) animationListener!!.onAnimationStart(this)
        cleanupExistingAnimation()
        attachConfettiViewToParent()
        addNewConfetti(numInitialCount, initialDelay = 0L, useGaussian)
        startNewAnimation(useGaussian)
    }

    /**
     * Terminate the currently running animation if there is any.
     */
    fun terminate() {
        animator?.cancel()
        confettiView.terminate()
        animationListener?.onAnimationEnd(this)
    }

    private fun cleanupExistingAnimation() {
        animator?.cancel()
        lastEmittedTimestamp = 0
        val iterator = confetti.iterator()
        while (iterator.hasNext()) {
            removeConfetto(iterator.next())
            iterator.remove()
        }
    }

    private fun attachConfettiViewToParent() {
        val currentParent = confettiView.parent
        if (currentParent == null) {
            parentView.addView(confettiView)
        } else if (currentParent !== parentView) {
            (currentParent as ViewGroup).removeView(confettiView)
            parentView.addView(confettiView)
        }
        confettiView.reset()
    }

    private fun addNewConfetti(numConfetti: Int, initialDelay: Long, useGaussian: Boolean) {
        for (i in 0 until numConfetti) {
            var confetto = recycledConfetti.poll()
            if (confetto == null) confetto = confettoGenerator.generateConfetto(random)
            confetto.reset()
            confetto.configure(confettiSource, random, initialDelay, useGaussian)
            confetto.prepare(bound)
            addConfetto(confetto)
        }
    }

    private fun startNewAnimation(useGaussian: Boolean) {
        // Never-ending animator, we will cancel once the termination condition is reached.
        animator = ValueAnimator.ofInt(0).setDuration(Long.MAX_VALUE).also {
            it.addUpdateListener { valueAnimator: ValueAnimator ->
                val elapsedTime = valueAnimator.currentPlayTime
                processNewEmission(elapsedTime, useGaussian)
                updateConfetti(elapsedTime)
                if (confetti.size == 0 && elapsedTime >= emissionDuration) {
                    terminate()
                } else {
                    confettiView.invalidate()
                }
            }
            it.start()
        }
    }

    private fun processNewEmission(elapsedTime: Long, useGaussian: Boolean) {
        if (elapsedTime < emissionDuration) {
            if (lastEmittedTimestamp == 0L) {
                lastEmittedTimestamp = elapsedTime
            } else {
                val timeSinceLastEmission = elapsedTime - lastEmittedTimestamp
                // Randomly determine how many confetti to emit
                val numNewConfetti = (random.nextFloat() * emissionRate * timeSinceLastEmission).toInt()
                if (numNewConfetti > 0) {
                    lastEmittedTimestamp += (emissionRateInverse * numNewConfetti).roundToInt().toLong()
                    addNewConfetti(numNewConfetti, elapsedTime, useGaussian)
                }
            }
        }
    }

    private fun updateConfetti(elapsedTime: Long) {
        val iterator = confetti.iterator()
        while (iterator.hasNext()) {
            val confetto = iterator.next()
            if (!confetto.applyUpdate(elapsedTime)) {
                iterator.remove()
                removeConfetto(confetto)
            }
        }
    }

    private fun addConfetto(confetto: Confetto) {
        confetti.add(confetto)
        animationListener?.onConfettoEnter(confetto)
    }

    private fun removeConfetto(confetto: Confetto) {
        animationListener?.onConfettoExit(confetto)
        recycledConfetti.add(confetto)
    }

    private fun Confetto.configure(
        confettiSource: ConfettiSource,
        random: Random,
        initialDelay: Long,
        useGaussian: Boolean,
    ) {
        setInitialDelay(initialDelay)
        setInitialX(confettiSource.getInitialX(random.nextFloat()))
        setInitialY(confettiSource.getInitialY(random.nextFloat()))
        setInitialVelocityX(getVarianceAmount(velocityX, velocityDeviationX, random, useGaussian))
        setInitialVelocityY(getVarianceAmount(velocityY, velocityDeviationY, random, useGaussian))
        setAccelerationX(getVarianceAmount(accelerationX, accelerationDeviationX, random, useGaussian))
        setAccelerationY(getVarianceAmount(accelerationY, accelerationDeviationY, random, useGaussian))
        setTargetVelocityX(targetVelocityX?.let { getVarianceAmount(it, targetVelocityXDeviation!!, random, useGaussian) })
        setTargetVelocityY(targetVelocityY?.let { getVarianceAmount(it, targetVelocityYDeviation!!, random, useGaussian) })
        setInitialRotation(getVarianceAmount(initialRotation.toFloat(), initialRotationDeviation.toFloat(), random, useGaussian))
        setInitialRotationalVelocity(getVarianceAmount(rotationalVelocity, rotationalVelocityDeviation, random, useGaussian))
        setRotationalAcceleration(getVarianceAmount(rotationalAcceleration, rotationalAccelerationDeviation, random, useGaussian))
        setTargetRotationalVelocity(
            targetRotationalVelocity?.let { getVarianceAmount(it, targetRotationalVelocityDeviation!!, random, useGaussian) },
        )
        setTTL(ttl)
        setFadeOut(fadeOutInterpolator)
    }

    private fun getVarianceAmount(base: Float, deviation: Float, random: Random, useGaussian: Boolean): Float {
        // Normalize random to be [-1, 1] rather than [0, 1]
        return if (useGaussian) {
            base + deviation * (random.nextGaussian().toFloat() * 2 - 1)
        } else {
            base + deviation * (random.nextFloat() * 2 - 1)
        }
    }

    interface ConfettiAnimationListener {
        fun onAnimationStart(confettiManager: ConfettiManager?)
        fun onAnimationEnd(confettiManager: ConfettiManager?)
        fun onConfettoEnter(confetto: Confetto?)
        fun onConfettoExit(confetto: Confetto?)
    }

    class ConfettiAnimationListenerAdapter : ConfettiAnimationListener {
        override fun onAnimationStart(confettiManager: ConfettiManager?) {
            // No-op
        }

        override fun onAnimationEnd(confettiManager: ConfettiManager?) {
            // No-op
        }

        override fun onConfettoEnter(confetto: Confetto?) {
            // No-op
        }

        override fun onConfettoExit(confetto: Confetto?) {
            // No-op
        }
    }

    companion object {
        const val INFINITE_DURATION = Long.MAX_VALUE
    }
}
