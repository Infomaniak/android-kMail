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
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.MailTemplate
import com.infomaniak.mail.databinding.FragmentEmailTemplatesBinding
import com.infomaniak.mail.ui.main.settings.ItemSettingView
import com.infomaniak.mail.ui.newMessage.EditorContentManager.Companion.toSanitizedHtml
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EmailTemplatesFragment : Fragment() {

    private var binding: FragmentEmailTemplatesBinding by safeBinding()
    private val newMessageViewModel: NewMessageViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentEmailTemplatesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        populateTemplates(MailTemplate.mocks)
    }

    private fun populateTemplates(templates: List<MailTemplate>) = with(binding) {
        templates.forEach { template ->
            optionsContainer.addView(createTemplateItem(template))
        }
    }

    private fun createTemplateItem(template: MailTemplate): ItemSettingView {
        return ItemSettingView(requireContext()).apply {
            setTitle(template.title.ifBlank { getString(R.string.emailTemplateNoTitle) })
            val preview = template.body.toPlainTextPreview()
            if (preview.isNotEmpty()) setSubtitle(preview)
            showChevron()
            setOnClickListener { onTemplateClicked(template) }
        }
    }

    private fun onTemplateClicked(template: MailTemplate) {
        findNavController().navigate(
            EmailTemplatesFragmentDirections.actionEmailTemplatesFragmentToEmailTemplateViewerFragment(
                template.id
            )
        )
    }

    private fun String.toPlainTextPreview(): String {
        val sanitized = BodyContentPayload(this, BodyContentType.HTML_UNSANITIZED).toSanitizedHtml()
        return jsoupParseWithLog(sanitized).text().trim()
    }
}
