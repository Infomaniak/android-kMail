// AiPromptView.kt
package com.infomaniak.mail.views

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.widget.doAfterTextChanged
import com.infomaniak.core.legacy.utils.showKeyboard
import com.infomaniak.mail.databinding.ViewAiPromptContentBinding

class AiPromptView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding = ViewAiPromptContentBinding.inflate(LayoutInflater.from(context), this, true)
    private var onPromptChanged: ((String) -> Unit)? = null

    fun bind(
        prompt: CharSequence?,
        placeholder: CharSequence,
        onCloseClick: () -> Unit,
        onGenerateClick: () -> Unit,
        onPromptChanged: (String) -> Unit,
    ) = with(binding) {
        this@AiPromptView.onPromptChanged = onPromptChanged

        this.prompt.setText(prompt)
        this.prompt.setSelection(this.prompt.text?.length ?: 0)
        this.prompt.hint = placeholder

        closeButton.setOnClickListener { onCloseClick() }
        generateButton.setOnClickListener { onGenerateClick() }

        this.prompt.doAfterTextChanged { text ->
            generateButton.isEnabled = text?.isNotEmpty() == true
            this@AiPromptView.onPromptChanged?.invoke(text?.toString().orEmpty())
        }

        generateButton.isEnabled = this.prompt.text?.isNotEmpty() == true
    }

    fun focusPrompt() = binding.prompt.showKeyboard()
}
