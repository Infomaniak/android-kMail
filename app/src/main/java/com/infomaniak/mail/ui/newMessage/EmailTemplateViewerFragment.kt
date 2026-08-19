/*
 * Infomaniak Mail - Android
 * Copyright (C) 2026 Infomaniak Network SA
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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.MailTemplate
import com.infomaniak.mail.databinding.FragmentEmailTemplateViewerBinding
import com.infomaniak.mail.ui.newMessage.EditorContentManager.Companion.toSanitizedHtml
import com.infomaniak.mail.utils.WebViewUtils
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupThreadWebViewSettings
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailTemplateViewerFragment : Fragment() {

    private var binding: FragmentEmailTemplateViewerBinding by safeBinding()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()
    private val navigationArgs: EmailTemplateViewerFragmentArgs by navArgs()

    private val webViewUtils by lazy { WebViewUtils(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentEmailTemplateViewerBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        val template = MailTemplate.mocks.firstOrNull { it.id == navigationArgs.templateId } ?: run {
            findNavController().popBackStack()
            return
        }

        root.setTitle(template.title.ifBlank { getString(R.string.emailTemplateNoTitle) })

        setupWebView(template)
        insertButton.setOnClickListener { onInsertClicked(template) }
    }

    private fun setupWebView(template: MailTemplate) = with(binding) {
        val isDarkMode = requireContext().isNightModeEnabled()
        val sanitizedHtml = BodyContentPayload(template.body, BodyContentType.HTML_UNSANITIZED).toSanitizedHtml()
        val styledHtml = webViewUtils.processHtmlForDisplay(sanitizedHtml, isDarkMode, aliases = emptyList())

        templateWebView.apply {
            enableAlgorithmicDarkening(isDarkMode)
            settings.setupThreadWebViewSettings()
            loadDataWithBaseURL("", styledHtml, "text/html", "UTF-8", "")
        }
    }

    private fun onInsertClicked(template: MailTemplate) {
        newMessageViewModel.setPlaceholderVisibility(false)
        newMessageViewModel.editorBodyInitializer.postValue(
            BodyContentPayload(template.body, BodyContentType.HTML_UNSANITIZED)
        )
        findNavController().popBackStack(R.id.newMessageFragment, false)
    }
}
