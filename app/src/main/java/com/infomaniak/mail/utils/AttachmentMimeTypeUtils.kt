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
package com.infomaniak.mail.utils

import com.infomaniak.mail.data.models.Attachment

object AttachmentMimeTypeUtils {

    private val pdfMatches = mimeTypeSetOf(
        "application/pdf",
        "application/acrobat",
        "application/nappdf",
        "application/x-pdf"
    )

    private val calendarMatches = mimeTypeSetOf(
        "application/ics",
        "text/calendar"
    )

    private val vcardMatches = mimeTypeSetOf(
        "text/vcard",
        "text/directory",
        "text/x-vcard"
    )

    private val imageMatches = mimeTypeSetOf(
        "image/",
        "application/postscript",
        "application/x-font-type1"
    )

    private val audioMatches = mimeTypeSetOf("audio/")

    private val videoMatches = mimeTypeSetOf(
        "video/",
        "model/vnd.mts",
        "application/mxf",
        "application/vnd.rn-realmedia",
        "application/x-shockwave-flash"
    )

    private val sheetMatches = mimeTypeSetOf(
        "text/csv",
        "application/vnd.ms-excel",
        "application/msexcel",
        "application/x-msexcel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml",
        "application/vnd.oasis.opendocument.spreadsheet"
    )

    private val pointMatches = mimeTypeSetOf(
        "application/powerpoint",
        "application/mspowerpoint",
        "application/vnd.ms-powerpoint",
        "application/x-mspowerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml",
        "application/vnd.oasis.opendocument.presentation"
    )

    private val textMatches = mimeTypeSetOf(
        "text/markdown",
        "text/plain",
        "application/msword",
        "application/vnd.ms-word",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.openxmlformats-officedocument.wordprocessingml"
    )

    private val archiveMatches = mimeTypeSetOf(
        "application/x-7z-compressed",
        "application/x-ace-compressed",
        "application/x-cfs-compressed",
        "application/x-compressed-tar",
        "application/x-cpio-compressed",
        "application/x-dgc-compressed",
        "application/x-gca-compressed",
        "application/x-lrzip-compressed-tar",
        "application/x-lz4-compressed-tar",
        "application/x-compress",
        "application/gzip",
        "application/x-zip-compressed",
        "application/x-zip",
        "application/zip",
        "application/x-bzip",
        "application/x-bzip2",
        "application/java-archive",
        "application/x-rar-compressed",
        "application/application/x-tar"
    )

    private val codeMatches = mimeTypeSetOf(
        "text/", // Beware of the order, this must come after every other "text/"
        "application/json",
        "application/xml"
    )

    private val fontMatches = mimeTypeSetOf(
        "font/",
        "application/vnd.afpc.foca-codedfont",
        "application/vnd.font-fontforge-sfd",
        "application/vnd.ms-fontobject",
        "application/font-tdpfr"
    )

    fun getFileTypeFromMimeType(mimeType: String): Attachment.AttachmentType = when (mimeType) {
        in pdfMatches -> Attachment.AttachmentType.PDF
        in calendarMatches -> Attachment.AttachmentType.CALENDAR
        in vcardMatches -> Attachment.AttachmentType.VCARD
        in imageMatches -> Attachment.AttachmentType.IMAGE
        in audioMatches -> Attachment.AttachmentType.AUDIO
        in videoMatches -> Attachment.AttachmentType.VIDEO
        in sheetMatches -> Attachment.AttachmentType.SPREADSHEET
        in pointMatches -> Attachment.AttachmentType.POINTS
        in textMatches -> Attachment.AttachmentType.TEXT
        in archiveMatches -> Attachment.AttachmentType.ARCHIVE
        in codeMatches -> Attachment.AttachmentType.CODE // Beware of the order, this must come after every other "text/"
        in fontMatches -> Attachment.AttachmentType.FONT
        else -> Attachment.AttachmentType.UNKNOWN
    }

    private fun mimeTypeSetOf(vararg mimeTypes: String) = MimeTypeSet(mimeTypes.toSet())

    private class MimeTypeSet(private val mimeTypes: Set<String>) : Set<String> by mimeTypes {
        override fun contains(element: String): Boolean = mimeTypes.any { mimeType -> element.startsWith(mimeType) }
    }
}
