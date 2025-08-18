/*
 * Infomaniak Mail - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
import android.util.AttributeSet
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.emojicomponents.R
import com.infomaniak.emojicomponents.components.EmojiReactionDetails
import com.infomaniak.emojicomponents.data.ReactionDetail
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackEmojiReactionsEvent
import com.infomaniak.mail.ui.components.views.MailBottomSheetScaffoldComposeView

class EmojiReactionDetailsBottomSheet @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MailBottomSheetScaffoldComposeView(context, attrs, defStyleAttr) {

    private val emojiReactionDetails = mutableStateListOf<Pair<String, SnapshotStateList<ReactionDetail>>>()
    private var initialEmoji by mutableStateOf<String?>(null)

    override val title: String = context.getString(R.string.reactionsTitle)

    @Composable
    override fun BottomSheetContent() {
        Column {
            Spacer(modifier = Modifier.height(Margin.Medium))
            EmojiReactionDetails(
                details = emojiReactionDetails,
                initialEmoji = initialEmoji,
                onAllTabClick = { trackEmojiReactionsEvent(MatomoName.SwitchReactionTab) },
                onEmojiTabClick = { trackEmojiReactionsEvent(MatomoName.SwitchReactionTabToAll) },
            )
        }
    }

    fun showBottomSheetFor(emojiDetails: Map<String, List<ReactionDetail>>, preselectedEmojiTab: String) {
        emojiReactionDetails.clear()
        emojiDetails.forEach { (emoji, details) ->
            val emojiReactionData = mutableStateListOf<ReactionDetail>()
            emojiReactionData.addAll(details)
            emojiReactionDetails.add(emoji to emojiReactionData)
        }

        initialEmoji = preselectedEmojiTab

        showBottomSheet()
    }
}
