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
package com.infomaniak.mail

import com.infomaniak.mail.utils.extensions.safeTextFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class SafeTextFormatterTest {
    @Test
    fun zero_width_space_is_removed_isCorrect() {
        val unsafeText = "Hello\u200BWorld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("Hello World", safeText)
    }

    @Test
    fun zero_width_non_joiner_is_removed_isCorrect() {
        val unsafeText = "hel‌lo"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hel lo", safeText)
    }

    @Test
    fun zero_width_joiner_is_removed_isCorrect() {
        val unsafeText = "hel\u200Dlo"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hel lo", safeText)
    }

    @Test
    fun left_to_right_mark_is_removed_isCorrect() {
        val unsafeText = "hello\u200Eworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun right_to_left_mark_is_removed_isCorrect() {
        val unsafeText = "hello‏world"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun right_to_left_override_is_removed_isCorrect() {
        val unsafeText = "hello\u202Eworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun left_to_right_override_is_removed_isCorrect() {
        val unsafeText = "hello\u202Dworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun right_to_left_embedding_is_removed_isCorrect() {
        val unsafeText = "hello\u202Bworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun left_to_right_embedding_is_removed_isCorrect() {
        val unsafeText = "hello\u202Aworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun pop_directional_formatting_is_removed_isCorrect() {
        val unsafeText = "hello\u202Cworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    // Isolate controls
    @Test
    fun lri_is_removed_isCorrect() {
        val unsafeText = "hello\u2066world"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun rli_is_removed_isCorrect() {
        val unsafeText = "hello\u2067world"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun first_strong_isolate_is_removed_isCorrect() {
        val unsafeText = "hello\u2068world"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun pop_directional_isolate_is_removed_isCorrect() {
        val unsafeText = "hello\u2069world"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun byte_order_mark_at_start_is_removed_isCorrect() {
        val unsafeText = "\uFEFFHello World"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals(" Hello World", safeText)
    }

    @Test
    fun newline_is_removed_isCorrect() {
        val unsafeText = "hello\n"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello ", safeText)
    }

    @Test
    fun tab_is_removed_isCorrect() {
        val unsafeText = "hello\t\tworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun carriage_return_is_removed_isCorrect() {
        val unsafeText = "hello\rworld"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("hello world", safeText)
    }

    @Test
    fun real_world_example_is_removed_isCorrect() {
        val unsafeText = "if (isAdmin\u202E { // evil"
        val safeText = safeTextFormatter(unsafeText)
        assertEquals("if (isAdmin  { // evil", safeText)
    }
}
