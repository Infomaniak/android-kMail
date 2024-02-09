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
import android.text.Spannable
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.text.toSpannable
import com.infomaniak.mail.MatomoMail.trackExternalEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.utils.ExternalUtils.findExternalRecipients
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.formatSubject
import com.infomaniak.mail.utils.extensions.postfixWithTag
import com.infomaniak.lib.core.R as CoreR

object SubjectFormatter {

    fun computeSubject(
        context: Context,
        subjectData: SubjectData,
        onTagClicked: (String) -> Unit
    ): Pair<String, CharSequence> = with(subjectData) {
        val subject = context.formatSubject(thread.subject)

        var spannedSubject: Pair<String, CharSequence>? = null
        if (!externalMailFlagEnabled) spannedSubject = subject to subject

        val (externalRecipientEmail, externalRecipientQuantity) = thread.findExternalRecipients(emailDictionary, aliases)
        if (externalRecipientQuantity == 0) spannedSubject = subject to subject

        val spannedSubjectWithExternal = createSpannedSubjectWithExternal(
            context,
            subject,
            externalRecipientEmail,
            externalRecipientQuantity,
            onTagClicked
        )

        val folderName = getFolderName(
            context,
            subject,
            getFolderName(thread),
            externalRecipientQuantity != 0
        )
        getSpannedFolderName(context, folderName, spannedSubjectWithExternal)?.let { spannedFolderName ->
            spannedSubject = subject to spannedFolderName
        }

        return spannedSubject!!
    }

    fun getFolderName(thread: Thread) = if (thread.messages.size > 1) "" else thread.folderName

    private fun createSpannedSubjectWithExternal(
        context: Context,
        subject: String,
        externalRecipientEmail: String?,
        externalRecipientQuantity: Int,
        onTagClicked: (String) -> Unit
    ): Spannable {
        return context.postfixWithTag(
            subject.toSpannable(),
            R.string.externalTag,
            R.color.externalTagBackground,
            R.color.externalTagOnBackground,
        ) {
            context.trackExternalEvent("threadTag")

            val description = context.resources.getQuantityString(
                R.plurals.externalDialogDescriptionExpeditor,
                externalRecipientQuantity,
                externalRecipientEmail,
            )
            onTagClicked(description)
        }
    }

    private fun getSpannedFolderName(
        context: Context,
        folderName: CharSequence?,
        spannedSubjectWithExternal: Spannable
    ): Spannable? {
        return if (!folderName.isNullOrEmpty()) {
            context.postfixWithTag(
                spannedSubjectWithExternal,
                folderName,
                R.color.backgroundFolderName,
                R.color.textColorFolderName,
            ) {
                context.trackExternalEvent("threadTag")
            }
        } else {
            null
        }
    }

    private fun getFolderName(
        context: Context,
        subject: String,
        fullFolderName: String,
        hasExternalTag: Boolean
    ): CharSequence {

        val (subjectAndExternalLayout, fullSubjectLayout, folderNameLayout) = getStaticLayouts(
            context,
            subject,
            fullFolderName,
            hasExternalTag
        )

        // In case we know that the folder name take more than one line, we insert a break line to have more space
        // If in any case, the folder name take more than the width of the screen, the string will be ellipsized
        // the middle.
        return if (fullSubjectLayout.lineCount - subjectAndExternalLayout.lineCount > 0) {
            "\n ${folderNameLayout.text}"
        } else {
            fullFolderName
        }
    }

    private fun getTagsTextPaint(context: Context) : TextPaint {
        val tagsTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        tagsTextPaint.textSize = context.resources.getDimension(R.dimen.externalTagTextSize)
        tagsTextPaint.typeface = ResourcesCompat.getFont(context, R.font.tag_font)
        tagsTextPaint.density

        return tagsTextPaint
    }

    private fun getStaticLayouts(
        context: Context,
        subjectString: String,
        fullFolderName: String,
        hasExternalTag: Boolean
    ) : StaticLayouts {
        val tagsTextPaint = getTagsTextPaint(context)

        val paddingsInPixels = (context.resources.getDimension(CoreR.dimen.marginStandard) * 2).toInt()
        val width = Resources.getSystem().displayMetrics.widthPixels - paddingsInPixels

        val externalString = if (hasExternalTag) context.getString(R.string.externalTag) else ""
        val subjectAndExternalString = subjectString + externalString
        val fullString = subjectString + externalString + fullFolderName

        val folderNameLayout =
            StaticLayout.Builder.obtain(fullFolderName, 0, fullFolderName.length, tagsTextPaint, width)
                .setEllipsizedWidth(width)
                .setEllipsize(TextUtils.TruncateAt.MIDDLE)
                .setMaxLines(1)
                .build()

        val subjectAndExternalLayout =
            StaticLayout.Builder.obtain(subjectAndExternalString, 0, subjectAndExternalString.length, tagsTextPaint, width)
                .build()
        val fullSubjectLayout = StaticLayout.Builder.obtain(fullString, 0, fullString.length, tagsTextPaint, width).build()

        return StaticLayouts(subjectAndExternalLayout, fullSubjectLayout, folderNameLayout)
    }

    data class SubjectData(
        val thread: Thread,
        val emailDictionary: MergedContactDictionary,
        val aliases: List<String>,
        val externalMailFlagEnabled: Boolean
    )

    private data class StaticLayouts(
        val subjectAndExternalLayout: StaticLayout,
        val fullSubjectLayout: StaticLayout,
        val folderNameLayout: StaticLayout
    )
}
