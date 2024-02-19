/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.thread

import android.content.Context
import android.content.res.Resources
import android.graphics.Paint
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils.TruncateAt
import androidx.annotation.ColorRes
import androidx.core.content.res.ResourcesCompat
import com.infomaniak.mail.MatomoMail.trackExternalEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.ExternalUtils.findExternalRecipients
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.formatSubject
import com.infomaniak.mail.utils.extensions.postfixWithTag
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubjectFormatter @Inject constructor(private val context: Context) {

    fun generateSubjectContent(
        subjectData: SubjectData,
        onExternalClicked: (String) -> Unit,
    ): Pair<String, CharSequence> = with(subjectData) {
        val subject = context.formatSubject(thread.subject)

        val spannedSubjectWithExternal = handleExternals(subject, onExternalClicked)
        val spannedSubjectWithFolder = handleFolders(spannedSubjectWithExternal)

        return subject to spannedSubjectWithFolder
    }

    private fun SubjectData.handleExternals(
        previousContent: String,
        onExternalClicked: (String) -> Unit,
    ): CharSequence {
        if (!externalMailFlagEnabled) return previousContent

        val (externalRecipientEmail, externalRecipientQuantity) = thread.findExternalRecipients(emailDictionary, aliases)
        if (externalRecipientQuantity == 0) return previousContent

        return postFixWithExternal(previousContent, externalRecipientQuantity, externalRecipientEmail, onExternalClicked)
    }

    private fun postFixWithExternal(
        previousContent: CharSequence,
        externalRecipientQuantity: Int,
        externalRecipientEmail: String?,
        onExternalClicked: (String) -> Unit,
    ) = context.postfixWithTag(
        previousContent,
        R.string.externalTag,
        TagColor(R.color.externalTagBackground, R.color.externalTagOnBackground),
    ) {
        context.trackExternalEvent("threadTag")

        val description = context.resources.getQuantityString(
            R.plurals.externalDialogDescriptionExpeditor,
            externalRecipientQuantity,
            externalRecipientEmail,
        )
        onExternalClicked(description)
    }

    private fun SubjectData.handleFolders(previousContent: CharSequence): CharSequence {
        val folderName = getFolderName(thread)
        if (folderName.isEmpty()) return previousContent

        val ellipsizeTag = getEllipsizeConfiguration(folderName)
        return postFixWithFolder(previousContent, folderName, ellipsizeTag)
    }

    private fun postFixWithFolder(
        previousContent: CharSequence,
        folderName: String,
        ellipsizeConfiguration: EllipsizeConfiguration?,
    ) = context.postfixWithTag(
        previousContent,
        folderName,
        TagColor(R.color.folderTagBackground, R.color.folderTagTextColor),
        ellipsizeConfiguration,
    )

    private fun getFolderName(thread: Thread) = if (thread.messages.size > 1) "" else thread.folderName

    private fun getEllipsizeConfiguration(tag: String): EllipsizeConfiguration? {
        val paddingsInPixels = (context.resources.getDimension(R.dimen.threadHorizontalMargin) * 2).toInt()
        val width = Resources.getSystem().displayMetrics.widthPixels - paddingsInPixels

        val tagTextPaint = getTagsPaint(context)
        val layoutWithTag = StaticLayout.Builder.obtain(tag, 0, tag.length, tagTextPaint, width).build()

        return layoutWithTag.takeIf { it.lineCount > 1 }?.let {
            EllipsizeConfiguration(width, TruncateAt.MIDDLE, withNewLine = true)
        }
    }

    data class SubjectData(
        val thread: Thread,
        val emailDictionary: MergedContactDictionary,
        val aliases: List<String>,
        val externalMailFlagEnabled: Boolean,
    )

    data class EllipsizeConfiguration(
        val maxWidth: Int,
        val truncateAt: TruncateAt = TruncateAt.MIDDLE,
        val withNewLine: Boolean = false,
    )

    data class TagColor(@ColorRes val backgroundColorRes: Int, @ColorRes val textColorRes: Int)

    companion object {

        fun getTagsPaint(context: Context) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = context.resources.getDimension(R.dimen.tagTextSize)
            typeface = ResourcesCompat.getFont(context, R.font.tag_font)
        }
    }
}
