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
package com.infomaniak.emojicomponents.components

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infomaniak.core.avatar.components.Avatar
import com.infomaniak.core.avatar.models.AvatarColors
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.core.compose.margin.Margin
import com.infomaniak.emojicomponents.R
import com.infomaniak.emojicomponents.data.ReactionDetail
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiReactionDetails(
    details: SnapshotStateList<Pair<String, SnapshotStateList<ReactionDetail>>>,
    modifier: Modifier = Modifier,
    initialEmoji: String? = null,
) {
    fun computeInitialPage(): Int = details
        .indexOfFirst { it.first == initialEmoji }
        .takeIf { it != -1 }
        ?.plus(1)
        ?: 0

    Column(modifier) {
        val pagerState = rememberPagerState(computeInitialPage()) { details.count() + 1 }
        val scope = rememberCoroutineScope()

        Box {
            // Workaround because when PrimaryScrollableTabRow has only a few tabs to display, the horizontal divider integrated
            // with it won't reach the edge of the screen: https://issuetracker.google.com/issues/261741384.
            HorizontalDivider(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )

            PrimaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                edgePadding = Margin.Mini,
            ) {
                CustomTab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                ) {
                    Text(stringResource(R.string.reactionsAll))
                }

                details.forEachIndexed { index, (emoji, details) ->
                    CustomTab(
                        selected = pagerState.currentPage == index + 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index + 1) } },
                    ) {
                        Text(emoji + " " + details.size)
                    }
                }
            }
        }

        HorizontalPager(
            pagerState,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxHeight(),
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                if (it == 0) {
                    details.forEach { (_, details) ->
                        details.forEach { detail ->
                            Item(detail)
                        }
                    }
                } else {
                    details[it - 1].let { (_, details) ->
                        details.forEach { detail ->
                            Item(detail)
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun CustomTab(selected: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Tab(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .height(38.dp)
            .clip(MaterialTheme.shapes.medium),
        content = content
    )
}

@Composable
private fun Item(detail: ReactionDetail) {
    Row(
        Modifier
            .heightIn(56.dp)
            .padding(horizontal = Margin.Medium),
        horizontalArrangement = Arrangement.spacedBy(Margin.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(detail.avatarType)
        Text(detail.name, modifier = Modifier.weight(1f), overflow = TextOverflow.MiddleEllipsis, maxLines = 1)
        Text(detail.emoji)
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Composable
private fun Preview() {
    val details = remember {
        mutableStateListOf(
            "✅" to mutableStateListOf(
                ReactionDetail("✅", "Alice", AvatarType.WithInitials.Initials("A", AvatarColors(Color.DarkGray, Color.White)))
            ),
            "❌" to mutableStateListOf(
                ReactionDetail("✅", "Bob", AvatarType.WithInitials.Initials("B", AvatarColors(Color.Gray, Color.White))),
                ReactionDetail(
                    "✅",
                    "Adolph Blaine Charles David Earl Frederick Gerald Hubert",
                    AvatarType.WithInitials.Initials("AB", AvatarColors(Color.Cyan, Color.White))
                )
            ),
        )
    }

    MaterialTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface {
            EmojiReactionDetails(details)
        }
    }
}
