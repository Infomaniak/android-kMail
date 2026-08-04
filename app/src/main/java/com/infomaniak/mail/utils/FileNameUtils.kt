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
package com.infomaniak.mail.utils

import io.sentry.Sentry
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.Normalizer

/**  Android’s filesystems limit each path component to 255 UTF-8 bytes.
 * 240 leaves 15 bytes for prefixes/suffixes.
 * The upload prefix (Int.hashCode() plus ) needs at most 12 bytes, yielding at most 252 bytes.
 * The remaining 3 bytes are conservative headroom. */
private const val MAX_FILE_NAME_SIZE_BYTES = 240
private const val MAX_PRESERVED_EXTENSION_SIZE_BYTES = 32
private const val DEFAULT_FILE_NAME = "attachment"
private val invalidFileNameCharacters = Regex("[\\\\/:*?\"<>|%\\p{Cc}]")

fun String.toSafeFileName(): String {
    val normalizedName = Normalizer.normalize(this, Normalizer.Form.NFC)
        .replace(invalidFileNameCharacters, "_")
        .trim()
        .takeUnless { it.isEmpty() || it == "." || it == ".." }
        ?: DEFAULT_FILE_NAME

    return if (normalizedName.utf8Size <= MAX_FILE_NAME_SIZE_BYTES) normalizedName else computeTruncatedFileName(normalizedName)
}

fun File.resolveContainedPath(untrustedPath: String): File {
    if (File(untrustedPath).isAbsolute) throw SecurityException("Absolute paths are not allowed")

    val canonicalRoot = canonicalFile
    val resolvedFile = File(canonicalRoot, untrustedPath).canonicalFile
    if (resolvedFile != canonicalRoot && !resolvedFile.toPath().startsWith(canonicalRoot.toPath())) {
        throw SecurityException("Resolved path escapes its allowed root")
    }
    return resolvedFile
}

fun File.resolveContainedFileName(untrustedName: String): File? {
    val canonicalRoot = canonicalFile
    val resolvedFile = File(canonicalRoot, untrustedName.toSafeFileName()).canonicalFile
    return if (resolvedFile.parentFile != canonicalRoot) {
        Sentry.captureException(SecurityException("Resolved file escapes its allowed directory"))
        null
    } else {
        resolvedFile
    }
}

private fun computeTruncatedFileName(normalizedName: String): String {
    val extensionStart = normalizedName.lastIndexOf('.').takeIf { it in 1 until normalizedName.lastIndex }
    val extension = extensionStart?.let(normalizedName::substring)
        ?.takeIf { it.utf8Size <= MAX_PRESERVED_EXTENSION_SIZE_BYTES }
        .orEmpty()
    val fileNameWithoutExtension = normalizedName.dropLast(extension.length)
    val truncatedFileName = fileNameWithoutExtension.takeUtf8Bytes(MAX_FILE_NAME_SIZE_BYTES - extension.utf8Size)

    return truncatedFileName.ifEmpty { DEFAULT_FILE_NAME } + extension
}

private inline val String.utf8Size: Int get() = toByteArray(StandardCharsets.UTF_8).size

/**
 * Compute with utf8 size because Kotlin manipulate utf16 entities
 * This avoids character needing 2 bytes like 'é' to count only for 1 in a take(MAX_FILE_NAME_SIZE_BYTES) instead of 2.
 */
private fun String.takeUtf8Bytes(maxBytes: Int): String {
    if (maxBytes <= 0) return ""
    if (utf8Size <= maxBytes) return this

    val result = StringBuilder()
    var byteCount = 0
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        val character = String(Character.toChars(codePoint))
        val characterSize = character.utf8Size
        if (byteCount + characterSize > maxBytes) break

        result.append(character)
        byteCount += characterSize
        index += Character.charCount(codePoint)
    }
    return result.toString()
}
