/*
 * Infomaniak Mail - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.newMessage

import android.animation.FloatEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.transition.Slide
import android.transition.TransitionManager
import android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
import android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.shape.MaterialShapeDrawable
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.mail.MatomoMail.trackAiWriterEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings
import com.infomaniak.mail.data.models.FeatureFlag
import com.infomaniak.mail.data.models.ai.AiPromptOpeningStatus
import com.infomaniak.mail.databinding.FragmentNewMessageBinding
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.extensions.observeNotNull
import com.infomaniak.mail.utils.extensions.updateNavigationBarColor
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.FragmentScoped
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt
import com.infomaniak.lib.core.R as RCore

@FragmentScoped
class NewMessageAiManager @Inject constructor(
    @ActivityContext private val activityContext: Context,
    private val localSettings: LocalSettings,
) : NewMessageManager() {

    private inline val activity get() = activityContext as Activity

    private var _aiViewModel: AiViewModel? = null
    private inline val aiViewModel: AiViewModel get() = _aiViewModel!!

    private val animationDuration by lazy { resources.getInteger(R.integer.aiPromptAnimationDuration).toLong() }
    private val scrimOpacity by lazy { ResourcesCompat.getFloat(context.resources, R.dimen.scrimOpacity) }
    private val black by lazy { context.getColor(RCore.color.black) }

    private var aiPromptFragment: AiPromptFragment? = null

    private var valueAnimator: ValueAnimator? = null

    fun initValues(
        newMessageViewModel: NewMessageViewModel,
        binding: FragmentNewMessageBinding,
        fragment: NewMessageFragment,
        aiViewModel: AiViewModel,
    ) {
        super.initValues(
            newMessageViewModel = newMessageViewModel,
            binding = binding,
            fragment = fragment,
            freeReferences = {
                _aiViewModel = null
                valueAnimator?.cancel()
                valueAnimator = null
            },
        )

        _aiViewModel = aiViewModel
    }

    fun observeAiOutput() = with(binding) {
        aiViewModel.aiOutputToInsert.observe(viewLifecycleOwner) { (subject, content) ->
            bodyText.setText(content)
        }
    }

    fun observeAiPromptStatus() {
        aiViewModel.aiPromptOpeningStatus.observe(viewLifecycleOwner) { (shouldDisplay, shouldResetContent, becauseOfGeneration) ->
            if (shouldDisplay) onAiPromptOpened(shouldResetContent) else onAiPromptClosed(becauseOfGeneration)
        }
    }

    private fun onAiPromptOpened(resetPrompt: Boolean) = with(binding) {
        if (resetPrompt) {
            aiViewModel.apply {
                aiPrompt = ""
                aiPromptOpeningStatus.value?.shouldResetPrompt = false
            }
        }

        // Keyboard is opened inside onCreate() of AiPromptFragment

        val foundFragment = childFragmentManager.findFragmentByTag(AI_PROMPT_FRAGMENT_TAG) as? AiPromptFragment
        if (aiPromptFragment == null) {
            aiPromptFragment = foundFragment ?: AiPromptFragment()
        }

        if (foundFragment == null) {
            childFragmentManager
                .beginTransaction()
                .add(aiPromptFragmentContainer.id, aiPromptFragment!!, AI_PROMPT_FRAGMENT_TAG)
                .commitNow()
        }

        scrim.apply {
            isVisible = true
            isClickable = true
        }

        animateAiPrompt(true)
        setAiPromptVisibility(true)
        newMessageConstraintLayout.descendantFocusability = FOCUS_BLOCK_DESCENDANTS
    }

    private fun onAiPromptClosed(withoutTransition: Boolean) = with(binding) {

        fun removeFragmentAndHideScrim() {
            aiPromptFragment?.let {
                childFragmentManager
                    .beginTransaction()
                    .remove(it)
                    .commitNow()
            }
            aiPromptFragment = null

            scrim.isGone = true
        }

        aiPromptFragmentContainer.hideKeyboard()

        if (withoutTransition) {
            removeFragmentAndHideScrim()
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(animationDuration)
                removeFragmentAndHideScrim()
            }
        }

        if (!withoutTransition) animateAiPrompt(false)
        setAiPromptVisibility(false)
        newMessageConstraintLayout.descendantFocusability = FOCUS_BEFORE_DESCENDANTS
    }

    private fun animateAiPrompt(isVisible: Boolean) = with(binding) {
        val slidingTransition = Slide()
            .addTarget(aiPromptLayout)
            .setDuration(animationDuration)

        TransitionManager.beginDelayedTransition(root, slidingTransition)

        val (startOpacity, endOpacity) = if (isVisible) 0.0f to scrimOpacity else scrimOpacity to 0.0f

        valueAnimator?.cancel()
        valueAnimator = ValueAnimator.ofObject(FloatEvaluator(), startOpacity, endOpacity).apply {
            duration = animationDuration
            addUpdateListener { animator ->

                val alpha = ((animator.animatedValue as Float) * 256.0f).roundToInt() / 256.0f
                val toolbarColor = (binding.toolbar.background as? ColorDrawable?)?.color
                    ?: (binding.toolbar.background as? MaterialShapeDrawable?)?.fillColor?.defaultColor
                    ?: context.getColor(R.color.backgroundColor)

                scrim.alpha = alpha
                activity.window.statusBarColor = UiUtils.pointBetweenColors(toolbarColor, black, alpha)
            }
            start()
        }
    }

    private fun setAiPromptVisibility(isVisible: Boolean) {

        fun updateNavigationBarColor() {
            val backgroundColorRes = if (isVisible) R.color.backgroundColorSecondary else R.color.backgroundColor
            activity.window.updateNavigationBarColor(context.getColor(backgroundColorRes))
        }

        binding.aiPromptLayout.isVisible = isVisible
        updateNavigationBarColor()
    }

    fun observeAiFeatureFlagUpdates() {
        newMessageViewModel.currentMailboxLive.observeNotNull(viewLifecycleOwner) { mailbox ->
            val isAiEnabled = mailbox.featureFlags.contains(FeatureFlag.AI)
            binding.editorAi.isVisible = isAiEnabled
            if (isAiEnabled) navigateToDiscoveryBottomSheetIfFirstTime()
        }
    }

    private fun navigateToDiscoveryBottomSheetIfFirstTime() = with(localSettings) {
        if (showAiDiscoveryBottomSheet) {
            showAiDiscoveryBottomSheet = false
            fragment.safeNavigate(NewMessageFragmentDirections.actionNewMessageFragmentToAiDiscoveryBottomSheetDialog())
        }
    }

    fun navigateToPropositionFragment() {

        closeAiPrompt(becauseOfGeneration = true)
        resetAiProposition()

        fragment.safeNavigate(
            NewMessageFragmentDirections.actionNewMessageFragmentToAiPropositionFragment(
                isSubjectBlank = fragment.formatSubject() == null,
            ),
        )
    }

    fun openAiPrompt() {
        aiViewModel.aiPromptOpeningStatus.value = AiPromptOpeningStatus(isOpened = true)
    }

    fun closeAiPrompt(becauseOfGeneration: Boolean = false) {
        context.trackAiWriterEvent(name = if (becauseOfGeneration) "generate" else "dismissPromptWithoutGenerating")
        aiViewModel.aiPromptOpeningStatus.value = AiPromptOpeningStatus(
            isOpened = false,
            becauseOfGeneration = becauseOfGeneration,
        )
    }

    private fun resetAiProposition() {
        aiViewModel.aiPropositionStatusLiveData.value = null
    }

    private companion object {
        const val AI_PROMPT_FRAGMENT_TAG = "aiPromptFragmentTag"
    }
}
