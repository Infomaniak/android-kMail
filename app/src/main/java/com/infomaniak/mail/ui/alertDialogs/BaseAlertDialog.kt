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
package com.infomaniak.mail.ui.alertDialogs

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.button.MaterialButton
import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.mail.R

abstract class BaseAlertDialog(activityContext: Context) : DefaultLifecycleObserver {

    protected val activity = activityContext as AppCompatActivity

    protected abstract val alertDialog: AlertDialog

    private var isProgressInitialized = false
    private var fragmentLifecycle: Lifecycle? = null

    protected abstract fun resetCallbacks()

    fun startLoading() {
        alertDialog.setCancelable(false)
        negativeButton.isEnabled = false
        Utils.createRefreshTimer(onTimerFinish = positiveButton::showProgressCatching).start()
    }

    fun resetLoadingAndDismiss() = with(alertDialog) {
        if (isShowing) {
            positiveButton.hideProgressCatching(R.string.buttonCreate)
            negativeButton.isEnabled = true
            setCancelable(true)
            dismiss()
        }
    }

    /**
     * This method needs to be called to avoid memory leaks for any use of callback
     *
     * This object is activity scoped but the callback are given by fragments, hence the need to clear them at the end of the
     * fragments' viewLifecycle
     *
     * @param viewLifecycleOwner The viewLifecycle of the Fragment
     */
    fun bindAlertToLifecycle(viewLifecycleOwner: LifecycleOwner) {
        fragmentLifecycle = viewLifecycleOwner.lifecycle
        fragmentLifecycle?.addObserver(this)
    }

    protected fun initProgress() = with(alertDialog) {
        // We need the dialog to be shown to access the positive button
        // But as the component is activity scoped, we only want to bind the progress the first time it is shown
        if (!isProgressInitialized && isShowing) {
            positiveButton.initProgress(activity)
            isProgressInitialized = true
        }
    }

    protected inline val positiveButton get() = (alertDialog.getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton)
    protected inline val negativeButton get() = (alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton)

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        resetCallbacks()
        fragmentLifecycle?.removeObserver(this)
    }
}
