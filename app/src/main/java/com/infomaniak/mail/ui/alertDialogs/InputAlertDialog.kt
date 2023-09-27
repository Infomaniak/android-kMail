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
package com.infomaniak.mail.ui.alertDialogs

import android.app.Activity
import android.content.Context
import android.widget.Button
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.showKeyboard
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.DialogInputBinding
import com.infomaniak.mail.di.IoDispatcher
import com.infomaniak.mail.utils.AlertDialogUtils.positiveButton
import com.infomaniak.mail.utils.AlertDialogUtils.resetLoadingAndDismiss
import com.infomaniak.mail.utils.AlertDialogUtils.startLoading
import com.infomaniak.mail.utils.trimmedText
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.*
import javax.inject.Inject
import com.infomaniak.lib.core.R as RCore

@ActivityScoped
class InputAlertDialog @Inject constructor(@ActivityContext private val activityContext: Context) {

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val activity = activityContext as Activity
    private val binding by lazy { DialogInputBinding.inflate(activity.layoutInflater) }
    private var errorJob: Job? = null

    private var alertDialog: AlertDialog = initDialog()

    private var onErrorCheck: (suspend (CharSequence) -> String?)? = null
    private var onPositiveButtonClicked: ((String) -> Unit)? = null

    private fun initDialog() = with(binding) {

        fun Button.checkValidation(text: CharSequence) {
            errorJob?.cancel()
            errorJob = CoroutineScope(ioDispatcher).launch {
                val error = onErrorCheck?.invoke(text.trim())
                if (errorJob?.isActive == true) Dispatchers.Main {
                    textInputLayout.error = error
                    isEnabled = error == null
                }
            }
        }

        fun AlertDialog.setupOnShowListener() = apply {
            setOnShowListener {
                showKeyboard()
                positiveButton.apply {
                    isEnabled = false
                    textInput.doAfterTextChanged {
                        if (it.isNullOrBlank()) {
                            errorJob?.cancel()
                            isEnabled = false
                            textInputLayout.error = null
                        } else {
                            checkValidation(it.trim())
                        }
                    }
                    // We are forced to override the ClickListener to prevent the default one to dismiss automatically the Alert
                    setOnClickListener {
                        onPositiveButtonClicked?.invoke(binding.textInput.trimmedText)
                        positiveButton.hideKeyboard()
                        startLoading()
                    }
                }
            }
        }

        MaterialAlertDialogBuilder(context)
            .setView(root)
            .setPositiveButton(R.string.buttonCreate, null)
            .setNegativeButton(RCore.string.buttonCancel, null)
            .setOnDismissListener {
                errorJob?.cancel()
                binding.textInput.text?.clear()
            }
            .create()
            .setupOnShowListener()
    }

    fun show(@StringRes title: Int, @StringRes hint: Int, @StringRes confirmButtonText: Int) = with(binding) {
        alertDialog.show()

        dialogTitle.setText(title)
        textInputLayout.setHint(hint)
        alertDialog.positiveButton.setText(confirmButtonText)
    }

    fun setCallbacks(onPositiveButtonClicked: (String) -> Unit, onErrorCheck: suspend (CharSequence) -> String?) {
        this.onPositiveButtonClicked = onPositiveButtonClicked
        this.onErrorCheck = onErrorCheck
    }

    fun resetLoadingAndDismiss() = alertDialog.resetLoadingAndDismiss()
}
