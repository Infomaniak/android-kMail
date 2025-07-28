/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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

import com.infomaniak.mail.ui.main.thread.ThreadAdapter.MessageDiffCallback.Companion.containsTheSameEmojiValuesAs
import com.infomaniak.mail.ui.main.thread.models.EmojiReactionAuthorUi
import com.infomaniak.mail.ui.main.thread.models.EmojiReactionStateUi
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.types.RealmDictionary
import org.junit.Assert.assertFalse
import org.junit.Test

class EmojiReactionStateEqualityTest {

    @Test
    fun equivalentEmojiReactionStates_areEqual() {
        val dictionary1: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(
            checkEmojiReactionState,
            crossEmojiReactionState,
        )

        val dictionary2: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(
            crossEmojiReactionState,
            checkEmojiReactionState,
        )

        assert(dictionary1.containsTheSameEmojiValuesAs(dictionary2))
        assert(dictionary2.containsTheSameEmojiValuesAs(dictionary1))
    }

    @Test
    fun emojiReactionStates_withMissingEmoji_areNotEqual() {
        val dictionary1: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(checkEmojiReactionState)

        val dictionary2: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(
            checkEmojiReactionState,
            crossEmojiReactionState,
        )

        assertFalse(dictionary1.containsTheSameEmojiValuesAs(dictionary2))
        assertFalse(dictionary2.containsTheSameEmojiValuesAs(dictionary1))
    }

    @Test
    fun emojiReactionStates_withDifferentCount_areNotEqual() {
        val checkEmoji1 = "✅" to emojiReactionStateOf("✅", 2, false)
        val checkEmoji2 = "✅" to emojiReactionStateOf("✅", 3, false) // count changed

        val dictionary1: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(checkEmoji1)
        val dictionary2: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(checkEmoji2)

        assertFalse(dictionary1.containsTheSameEmojiValuesAs(dictionary2))
        assertFalse(dictionary2.containsTheSameEmojiValuesAs(dictionary1))
    }

    @Test
    fun emojiReactionStates_withDifferentHasReacted_areNotEqual() {
        val checkEmoji1 = "✅" to emojiReactionStateOf("✅", 2, false)
        val checkEmoji2 = "✅" to emojiReactionStateOf("✅", 2, true) // hasReacted changed

        val dictionary1: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(checkEmoji1)
        val dictionary2: RealmDictionary<EmojiReactionStateUi?> = realmDictionaryOf(checkEmoji2)

        assertFalse(dictionary1.containsTheSameEmojiValuesAs(dictionary2))
        assertFalse(dictionary2.containsTheSameEmojiValuesAs(dictionary1))
    }

    companion object {
        private val checkEmojiReactionState = "✅" to emojiReactionStateOf("✅", 2, false)
        private val crossEmojiReactionState = "❌" to emojiReactionStateOf("❌", 1, true)

        // This method need to return the same class (here: EmojiReactionStateUi) that is used where
        // containsTheSameEmojiValuesAs() is called in the actual code of the app. This is needed so we can make sure that
        // underlying equality checks used inside of containsTheSameEmojiValuesAs() are always correctly defined
        private fun emojiReactionStateOf(emoji: String, count: Int, hasReacted: Boolean): EmojiReactionStateUi {
            return EmojiReactionStateUi(emoji, List(count) { EmojiReactionAuthorUi.FakeMe }, hasReacted)
        }
    }
}
