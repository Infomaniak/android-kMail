/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.correspondent.Correspondent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UiUtils {

    const val FULLY_SLID = 1.0f
    const val IGNORE_DIVIDER_TAG = "ignoreDividerTag"
    const val PRIMARY_COLOR_CODE = "--kmail-primary-color"

    fun formatUnreadCount(unread: Int) = if (unread >= 100) "99+" else unread.toString()

    fun Context.getPrettyNameAndEmail(
        correspondent: Correspondent,
        ignoreIsMe: Boolean = false,
    ): Pair<String, String?> = with(correspondent) {
        return when {
            isMe() && !ignoreIsMe -> getString(R.string.contactMe) to email
            name.isBlank() || name == email -> email to null
            else -> name to email
        }
    }

    fun fillInUserNameAndEmail(
        correspondent: Correspondent,
        nameTextView: TextView,
        emailTextView: TextView,
        ignoreIsMe: Boolean = false,
    ): Boolean {
        val (name, email) = nameTextView.context.getPrettyNameAndEmail(correspondent, ignoreIsMe)
        nameTextView.text = name

        val isSingleField = email == null
        emailTextView.apply {
            text = email
            isGone = isSingleField
        }

        return isSingleField
    }

    fun Fragment.animateColorChange(
        @ColorInt oldColor: Int,
        @ColorInt newColor: Int,
        duration: Long = 150L,
        animate: Boolean = true,
        applyColor: (color: Int) -> Unit,
    ): ValueAnimator? {
        return if (animate) {
            ValueAnimator.ofObject(ArgbEvaluator(), oldColor, newColor).apply {
                setDuration(duration)
                addUpdateListener { animator ->
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) { applyColor(animator.animatedValue as Int) }
                }
                start()
            }
        } else {
            applyColor(newColor)
            null
        }
    }

    fun dividerDrawable(context: Context) = AppCompatResources.getDrawable(context, R.drawable.divider)

    fun saveFocusWhenNavigatingBack(getLayout: () -> ViewGroup, lifecycle: Lifecycle) {

        val lifecycleObserver = object : DefaultLifecycleObserver {

            @IdRes
            private var lastFocusViewId: Int? = null

            override fun onStart(owner: LifecycleOwner) {
                super.onStart(owner)
                lastFocusViewId?.let { viewId -> getLayout().findViewById<View>(viewId).requestFocus() }
            }

            override fun onStop(owner: LifecycleOwner) {
                getLayout().focusedChild?.let { lastFocusViewId = it.id }
                super.onStop(owner)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                lifecycle.removeObserver(observer = this)
                super.onDestroy(owner)
            }
        }

        lifecycle.addObserver(lifecycleObserver)
    }
}
