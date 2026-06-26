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
package com.infomaniak.mail.ui.main.search

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.infomaniak.mail.R
import com.infomaniak.mail.ui.Scenarios.waitFor
import com.infomaniak.mail.ui.Utils.onViewWithTimeout
import com.infomaniak.mail.ui.login.BaseActivityTest
import com.infomaniak.mail.ui.login.LoginActivity
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@LargeTest
class SearchActivityTest : BaseActivityTest(startingActivity = LoginActivity::class) {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun searchMail(mailContent: String) {
        onView(isRoot()).perform(waitFor(5.seconds))

        onViewWithTimeout(matcher = withId(R.id.searchTextInput)).perform(
            click(),
            replaceText(mailContent),
            pressImeActionButton(),
            closeSoftKeyboard(),
        )

        onView(isRoot()).perform(waitFor(2.seconds))
    }

    private fun goToFolderAndBackToInbox(folderName: String) {
        onViewWithTimeout(matcher = withContentDescription(R.string.contentDescriptionButtonMenu)).perform(click())
        onViewWithTimeout(matcher = withId(R.id.menuDrawerRecyclerView)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(folderName)),
                click(),
            ),
        )

        onView(isRoot()).perform(waitFor(5.seconds))

        onViewWithTimeout(matcher = withContentDescription(R.string.contentDescriptionButtonMenu)).perform(click())
        onViewWithTimeout(matcher = withId(R.id.menuDrawerRecyclerView)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
                hasDescendant(withText(R.string.inboxFolder)),
                click(),
            ),
        )
    }

    private fun filterSearchWithFolder(folderName: String) {
        onViewWithTimeout(matcher = withId(R.id.searchButton)).perform(click())
        onViewWithTimeout(matcher = withId(R.id.allFoldersButton)).perform(click())
        onViewWithTimeout(matcher = withId(R.id.foldersRecyclerView)).perform(
            RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(folderName)), click()),
        )
    }

    private fun clickOnHorizontalChipList(chipId: Int) {
        onViewWithTimeout(matcher = withId(R.id.searchButton)).perform(click())
        onViewWithTimeout(matcher = withId(R.id.horizontalScrollViewFilters)).perform(swipeLeft())
        onViewWithTimeout(matcher = withId(chipId)).perform(click())
    }

    @Test
    fun searchWithMailContent() {
        onView(isRoot()).perform(waitFor(5.seconds))
        onViewWithTimeout(matcher = withId(R.id.searchButton)).perform(click())

        val mailContent = "#1 Search UI test"
        searchMail(mailContent)

        onViewWithTimeout(
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText(mailContent))),
        )
    }

    @Test
    fun searchWithSearchUITestFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))
        val folderName = "SearchUITest"
        goToFolderAndBackToInbox(folderName)
        filterSearchWithFolder(folderName)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#12 Search UI test SearchUITest folder"))),
        )
    }

    @Test
    fun searchWithArchivedFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))

        val archiveFolderName = context.getString(R.string.archiveFolder)
        goToFolderAndBackToInbox(archiveFolderName)
        filterSearchWithFolder(archiveFolderName)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#11 Search UI test Archived folder"))),
        )
    }

    @Test
    fun searchWithTrashFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))

        val trashFolderName = context.getString(R.string.trashFolder)
        goToFolderAndBackToInbox(trashFolderName)
        filterSearchWithFolder(trashFolderName)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#9 Search UI test Trash folder"))),
        )
    }

    @Test
    fun searchWithSpamFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))

        val spamFolderName = context.getString(R.string.spamFolder)
        goToFolderAndBackToInbox(spamFolderName)
        filterSearchWithFolder(spamFolderName)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#8 Search UI test Spam folder"))),
        )
    }

    @Test
    fun searchWithDraftsFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))

        filterSearchWithFolder(context.getString(R.string.draftFolder))

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#7 Search UI test Drafts folder"))),
        )
    }

    @Test
    fun searchWithSentMessagesFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))

        filterSearchWithFolder(context.getString(R.string.sentFolder))

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#5 Search UI test Sent messages folder"))),
        )
    }

    @Test
    fun searchWithInboxFolder() {
        onView(isRoot()).perform(waitFor(5.seconds))

        filterSearchWithFolder(context.getString(R.string.inboxFolder))

        val mailContent = "#2 Search UI test Inbox folder"
        searchMail(mailContent)
        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText(mailContent))),
        )
    }

    @Test
    fun searchWithAttachmentsFilter() {
        onView(isRoot()).perform(waitFor(5.seconds))

        clickOnHorizontalChipList(R.id.attachments)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#16 Search UI test Attachments"))),
        )
    }

    @Test
    fun searchWithFavoritesFilter() {
        onView(isRoot()).perform(waitFor(5.seconds))

        clickOnHorizontalChipList(R.id.favorites)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#15 Search UI test Favorites"))),
        )
    }

    @Test
    fun searchWithUnreadFilter() {
        onView(isRoot()).perform(waitFor(5.seconds))

        clickOnHorizontalChipList(R.id.unread)

        val mailContent = "#14 Search UI test Unread"
        searchMail(mailContent)
        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText(mailContent))),
        )
    }

    @Test
    fun searchWithReadFilter() {
        onView(isRoot()).perform(waitFor(5.seconds))

        clickOnHorizontalChipList(R.id.read)

        onViewWithTimeout(
            retryInterval = 5.seconds,
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#13 Search UI test Read"))),
        )
    }

    @Test
    fun searchWithContact() {
        onView(isRoot()).perform(waitFor(5.seconds))

        onViewWithTimeout(matcher = withId(R.id.searchButton)).perform(click())
        searchMail("mobiletest@ik.me")
        onViewWithTimeout(
            matcher = withId(R.id.mailRecyclerView),
            assertion = matches(hasDescendant(withText("#1 Search UI test"))),
        )
    }
}
