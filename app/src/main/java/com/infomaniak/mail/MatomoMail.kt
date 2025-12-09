/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.content.Context
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import com.infomaniak.core.ksuite.ui.utils.MatomoKSuite
import com.infomaniak.core.legacy.utils.capitalizeFirstChar
import com.infomaniak.core.matomo.Matomo
import com.infomaniak.core.matomo.Matomo.TrackerAction
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import com.infomaniak.mail.ui.newMessage.NewMessageEditorManager.EditorAction
import org.matomo.sdk.Tracker

object MatomoMail : Matomo {

    override val tracker: Tracker by lazy(::buildTracker)
    override val siteId = 9

    enum class MatomoCategory(val value: String) {
        Account("account"),
        AiWriter("aiWriter"),
        AppUpdate("appUpdate"),
        AttachmentActions("attachmentActions"),
        BlockUserAction("blockUserAction"),
        BottomSheetMessageActions("bottomSheetMessageActions"),
        BottomSheetThreadActions("bottomSheetThreadActions"),
        CalendarEvent("calendarEvent"),
        ContactActions("contactActions"),
        CreateFolder("createFolder"),
        EasterEgg("easterEgg"),
        EditorActions("editorActions"),
        EmojiReactions("emojiReactions"),
        Encryption("encryption"),
        Externals("externals"),
        HomeScreenShortcuts("homeScreenShortcuts"),
        InAppReview("inAppReview"),
        InAppUpdate("inAppUpdate"),
        ManageFolder("manageFolder"),
        MenuDrawer("menuDrawer"),
        Message("message"),
        MessageActions("messageActions"),
        MoveSearch("moveSearch"),
        MultiSelection("multiSelection"),
        NewMessage("newMessage"),
        NoValidMailboxes("noValidMailboxes"),
        NotificationActions("notificationActions"),
        Onboarding("onboarding"),
        ReplyBottomSheet("replyBottomSheet"),
        RestoreEmailsBottomSheet("restoreEmailsBottomSheet"),
        ScheduleSend("scheduleSend"),
        Search("search"),
        SettingsAccentColor("settingsAccentColor"),
        SettingsAutoAdvance("settingsAutoAdvance"),
        SettingsDataManagement("settingsDataManagement"),
        SettingsDensity("settingsDensity"),
        SettingsDisplayExternalContent("settingsDisplayExternalContent"),
        SettingsForwardMode("settingsForwardMode"),
        SettingsGeneral("settingsGeneral"),
        SettingsNotifications("settingsNotifications"),
        SettingsSend("settingsSend"),
        SettingsSwipeActions("settingsSwipeActions"),
        SettingsTheme("settingsTheme"),
        SettingsThreadMode("settingsThreadMode"),
        Snackbar("snackbar"),
        Snooze("snooze"),
        SwipeActions("swipeActions"),
        SyncAutoConfig("syncAutoConfig"),
        ThreadActions("threadActions"),
        ThreadList("threadList"),
        UserInfo("userInfo"),
    }

    enum class MatomoName(val value: String) {
        Acknowledgement("acknowledgement"),
        Add("add"),
        AddLink("addLink"),
        AddLinkConfirm("addLinkConfirm"),
        AddMailbox("addMailbox"),
        AddMailboxConfirm("addMailboxConfirm"),
        AddNewRecipient("addNewRecipient"),
        AddReactionFromChip("addReactionFromChip"),
        AddReactionFromEmojiPicker("addReactionFromEmojiPicker"),
        AddToContacts("addToContacts"),
        AdvancedActions("advancedActions"),
        AiWriter("aiWriter"),
        All("all"),
        AlreadySynchronized("alreadySynchronized"),
        AlreadyUsedReaction("alreadyUsedReaction"),
        Always("always"),
        Archive("archive"),
        ArchiveClicked("archiveClicked"),
        ArchiveExecuted("archiveExecuted"),
        ArchiveFolder("archiveFolder"),
        ArchiveSwipe("archiveSwipe"),
        AskMe("askMe"),
        Attachment("attachment"),
        AttachmentFilter("attachmentFilter"),
        Attendees("attendees"),
        BannerInfo("bannerInfo"),
        BannerManuallyClosed("bannerManuallyClosed"),
        BlockUser("blockUser"),
        Bold("bold"),
        Cancel("cancel"),
        CancelClicked("cancelClicked"),
        CancelSnooze("cancelSnooze"),
        CloseByGesture("closeByGesture"),
        CloseStorageWarningBanner("closeStorageWarningBanner"),
        CollapseFolder("collapseFolder"),
        ColorFolder("colorFolder"),
        CommercialFolder("commercialFolder"),
        Confetti("confetti"),
        ConfigureInstall("configureInstall"),
        ConfigureReady("configureReady"),
        Confirm("confirm"),
        ConfirmSelectedUser("confirmSelectedUser"),
        Conversation("conversation"),
        ConvertToDropbox("convertToDropbox"),
        CopyEmailAddress("copyEmailAddress"),
        CustomFolder("customFolder"),
        CustomFolders("customFolders"),
        CustomSchedule("customSchedule"),
        CustomScheduleConfirm("customScheduleConfirm"),
        DailyLimitReachedUpgrade("dailyLimitReachedUpgrade"),
        Delete("delete"),
        DeleteClicked("deleteClicked"),
        DeleteConfirm("deleteConfirm"),
        DeleteDraft("deleteDraft"),
        DeleteExecuted("deleteExecuted"),
        DeleteFromHistory("deleteFromHistory"),
        DeleteQuote("deleteQuote"),
        DeleteRecipient("deleteRecipient"),
        DeleteSearch("deleteSearch"),
        DeleteSignature("deleteSignature"),
        DeleteSwipe("deleteSwipe"),
        DetachMailbox("detachMailbox"),
        DetachMailboxConfirm("detachMailboxConfirm"),
        Disable("disable"),
        DiscoverLater("discoverLater"),
        DiscoverNow("discoverNow"),
        Dislike("dislike"),
        DismissError("dismissError"),
        DismissPromptWithoutGenerating("dismissPromptWithoutGenerating"),
        DismissProposition("dismissProposition"),
        Download("download"),
        DownloadAll("downloadAll"),
        DraftFolder("draftFolder"),
        Edit("edit"),
        EmailSentExternalQuantity("emailSentExternalQuantity"),
        EmailSentWithExternals("emailSentWithExternals"),
        EmptyDraft("emptyDraft"),
        EmptySignature("emptySignature"),
        EmptySpam("emptySpam"),
        EmptyTrash("emptyTrash"),
        Enable("enable"),
        EncryptionActivation("encryptionActivation"),
        ExecuteSearch("executeSearch"),
        Expand("expand"),
        Favorite("favorite"),
        FavoriteFilter("favoriteFilter"),
        FavoriteSwipe("favoriteSwipe"),
        Feedback("feedback"),
        FifteenSecond("15s"),
        FolderFilter("folderFilter"),
        FollowingThread("followingThread"),
        Forward("forward"),
        FriendlyWriting("friendlyWriting"),
        FromHistory("fromHistory"),
        FromMenuDrawer("fromMenuDrawer"),
        FromMove("fromMove"),
        Generate("generate"),
        GeneratePassword("generatePassword"),
        Halloween("halloween"),
        Help("help"),
        ImportEmails("importEmails"),
        ImportFile("importFile"),
        ImportFromCamera("importFromCamera"),
        InboxFolder("inboxFolder"),
        IncludeOriginalInReply("includeOriginalInReply"),
        Inline("inline"),
        InsertProposition("insertProposition"),
        InstallUpdate("installUpdate"),
        Italic("italic"),
        KeepSubject("keepSubject"),
        LastSelectedSchedule("lastSelectedSchedule"),
        LaterThisMorning("laterThisMorning"),
        Like("like"),
        ListOfThread("listOfThread"),
        LoadMore("loadMore"),
        Lock("lock"),
        LogOut("logOut"),
        LogOutConfirm("logOutConfirm"),
        LoggedIn("loggedIn"),
        Mailboxes("mailboxes"),
        MarkAsSeen("markAsSeen"),
        Message("message"),
        ModifySnooze("modifySnooze"),
        Move("move"),
        MoveSwipe("moveSwipe"),
        MultipleMessagesInThread("multipleMessagesInThread"),
        NaturalThread("naturalThread"),
        NbMailboxes("nbMailboxes"),
        NbMessagesInThread("nbMessagesInThread"),
        NewYear("newYear"),
        NextMonday("nextMonday"),
        NextMondayAfternoon("nextMondayAfternoon"),
        NextMondayMorning("nextMondayMorning"),
        None("none"),
        NoneSwipe("noneSwipe"),
        NumberOfBcc("numberOfBcc"),
        NumberOfCc("numberOfCc"),
        NumberOfTo("numberOfTo"),
        OneMessagesInThread("oneMessagesInThread"),
        Open("open"),
        OpenBottomSheet("openBottomSheet"),
        OpenByButton("openByButton"),
        OpenByGesture("openByGesture"),
        OpenCreationWebview("openCreationWebview"),
        OpenDashboard("openDashboard"),
        OpenDetails("openDetails"),
        OpenEmojiPicker("openEmojiPicker"),
        OpenEncryptionActions("openEncryptionActions"),
        OpenFromBottomsheet("openFromBottomsheet"),
        OpenFromDraft("openFromDraft"),
        OpenFromFab("openFromFab"),
        OpenFromMenuDrawer("openFromMenuDrawer"),
        OpenFromSettings("openFromSettings"),
        OpenInMyCalendar("openInMyCalendar"),
        OpenLocalDraft("openLocalDraft"),
        OpenLoginWebview("openLoginWebview"),
        OpenMessage("openMessage"),
        OpenNotificationSettings("openNotificationSettings"),
        OpenAppStore("openPlayStore"),
        OpenRecipientsFields("openRecipientsFields"),
        OpenSwissTransfer("openSwissTransfer"),
        OpenSyncApp("openSyncApp"),
        Postpone("postpone"),
        PostponeSwipe("postponeSwipe"),
        PresentAlert("presentAlert"),
        PreviousThread("previousThread"),
        Print("print"),
        PrintCancelled("printCancelled"),
        PrintValidated("printValidated"),
        QuickActions("quickActions"),
        QuickActionsSwipe("quickActionsSwipe"),
        ReadFAQ("readFAQ"),
        ReadFilter("readFilter"),
        Refine("refine"),
        Regenerate("regenerate"),
        Rename("rename"),
        RenameConfirm("renameConfirm"),
        ReplaceProposition("replaceProposition"),
        ReplacePropositionConfirm("replacePropositionConfirm"),
        ReplacePropositionDialog("replacePropositionDialog"),
        ReplaceSubjectConfirm("replaceSubjectConfirm"),
        ReplaceSubjectDialog("replaceSubjectDialog"),
        Reply("reply"),
        ReplyAll("replyAll"),
        ReplyMaybe("replyMaybe"),
        ReplyNo("replyNo"),
        ReplyYes("replyYes"),
        ReportJunk("reportJunk"),
        RequestPassword("requestPassword"),
        Restore("restore"),
        RestoreEmails("restoreEmails"),
        Retry("retry"),
        SaveDraft("saveDraft"),
        SaveInkDrive("saveInkDrive"),
        SaveToKDrive("saveToKDrive"),
        ScheduleDraft("scheduleDraft"),
        ScheduledCustomDate("scheduledCustomDate"),
        ScheduledDraftsFolder("scheduledDraftsFolder"),
        SeeAllAttendees("seeAllAttendees"),
        SeePassword("seePassword"),
        SelectAvatar("selectAvatar"),
        SelectContact("selectContact"),
        SelectDate("selectDate"),
        SelectRecipient("selectRecipient"),
        SelectUser("selectUser"),
        SendMail("sendMail"),
        SendReaction("sendReaction"),
        SendWithoutSubject("sendWithoutSubject"),
        SendWithoutSubjectConfirm("sendWithoutSubjectConfirm"),
        SentFolder("sentFolder"),
        SeriousWriting("seriousWriting"),
        ShareEmail("shareEmail"),
        ShareLink("shareLink"),
        SharePassword("sharePassword"),
        Shorten("shorten"),
        ShowReactionsBottomSheet("showReactionsBottomSheet"),
        ShowSourceCode("showSourceCode"),
        SignalPhishing("signalPhishing "),
        Snooze("snooze"),
        SnoozeCustomDate("snoozeCustomDate"),
        SnoozedFolder("snoozedFolder"),
        SocialNetworksFolder("socialNetworksFolder"),
        Spam("spam"),
        SpamFolder("spamFolder"),
        SpamSwipe("spamSwipe"),
        StrikeThrough("strikeThrough"),
        Switch("switch"),
        SwitchAccount("switchAccount"),
        SwitchColor("switchColor"),
        SwitchIdentity("switchIdentity"),
        SwitchMailbox("switchMailbox"),
        SwitchReactionTab("switchReactionTab"),
        SwitchReactionTabToAll("switchReactionTabToAll"),
        TenSecond("10s"),
        ThirtySecond("30s"),
        ThisAfternoon("thisAfternoon"),
        ThisEvening("thisEvening"),
        ThreadTag("threadTag"),
        TomorrowMorning("tomorrowMorning"),
        TrashFolder("trashFolder"),
        TrySendingWithDailyLimitReached("trySendingWithDailyLimitReached"),
        TrySendingWithMailboxFull("trySendingWithMailboxFull"),
        Tutorial("tutorial"),
        TwentyFiveSecond("25s"),
        TwentySecond("20s"),
        Underline("underline"),
        Undo("undo"),
        UnorderedList("unorderedList"),
        UnreadFilter("unreadFilter"),
        ValidateSearch("validateSearch"),
        WriteEmail("writeEmail"),
        Xmas("xmas"),
        ZeroSecond("0s"),
    }

    //region Track global events
    fun trackEvent(
        category: MatomoCategory,
        name: MatomoName,
        action: TrackerAction = TrackerAction.CLICK,
        value: Float? = null,
    ) {
        trackEvent(category.value, name.value, action, value)
    }
    //endregion

    //region Track specific events
    fun trackAccountEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Account, name)
    }

    fun trackSendingDraftEvent(
        action: DraftAction,
        to: List<Recipient>,
        cc: List<Recipient>,
        bcc: List<Recipient>,
        externalMailFlagEnabled: Boolean,
    ) {
        trackNewMessageEvent(action.matomoName)
        if (action == DraftAction.SEND) {
            val trackerData = listOf(
                MatomoName.NumberOfTo to to,
                MatomoName.NumberOfCc to cc,
                MatomoName.NumberOfBcc to bcc
            )
            trackerData.forEach { (eventName, recipients) ->
                trackNewMessageEvent(eventName, TrackerAction.DATA, recipients.size.toFloat())
            }

            if (externalMailFlagEnabled) {
                var externalRecipientCount = 0
                listOf(to, cc, bcc).forEach { field ->
                    field.forEach { recipient ->
                        externalRecipientCount += if (recipient.isDisplayedAsExternal) 1 else 0
                    }
                }

                trackExternalEvent(MatomoName.EmailSentWithExternals, TrackerAction.DATA, (externalRecipientCount > 0).toFloat())
                trackExternalEvent(MatomoName.EmailSentExternalQuantity, TrackerAction.DATA, externalRecipientCount.toFloat())
            }
        }
    }

    fun trackContactActionsEvent(name: MatomoName) {
        trackEvent(MatomoCategory.ContactActions, name)
    }

    fun trackAttachmentActionsEvent(name: MatomoName) {
        trackEvent(MatomoCategory.AttachmentActions, name)
    }

    fun trackBottomSheetMessageActionsEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.BottomSheetMessageActions, name, value = value?.toMailActionValue())
    }

    fun trackBottomSheetThreadActionsEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.BottomSheetThreadActions, name, value = value?.toMailActionValue())
    }

    private fun trackBottomSheetMultiSelectThreadActionsEvent(name: MatomoName, value: Int) {
        val trackerName = "${if (value == 1) "bulkSingle" else "bulk"}${name.value.capitalizeFirstChar()}"
        trackEvent(MatomoCategory.BottomSheetThreadActions.value, trackerName, value = value.toFloat())
    }

    fun trackThreadActionsEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.ThreadActions, name, value = value?.toMailActionValue())
    }

    private fun trackMultiSelectThreadActionsEvent(name: MatomoName, value: Int) {
        val trackerName = "${if (value == 1) "bulkSingle" else "bulk"}${name.value.capitalizeFirstChar()}"
        trackEvent(MatomoCategory.ThreadActions.value, trackerName, value = value.toFloat())
    }

    fun trackMessageActionsEvent(name: MatomoName) {
        trackEvent(MatomoCategory.MessageActions, name)
    }

    fun trackBlockUserAction(name: MatomoName) {
        trackEvent(MatomoCategory.BlockUserAction, name)
    }

    fun trackMoveSearchEvent(name: MatomoName) {
        trackEvent(MatomoCategory.MoveSearch, name)
    }

    fun trackMessageEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.Message, name, value = value?.toFloat())
    }

    fun trackSearchEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.Search, name, value = value?.toFloat())
    }

    fun trackNotificationActionEvent(name: MatomoName) {
        trackEvent(MatomoCategory.NotificationActions, name)
    }

    fun trackNewMessageEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.NewMessage, name, action, value)
    }

    fun trackMenuDrawerEvent(name: MatomoName, value: Boolean? = null) {
        trackMenuDrawerEvent(name, value = value?.toFloat())
    }

    fun trackMenuDrawerEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.MenuDrawer, name, action, value)
    }

    fun trackCreateFolderEvent(name: MatomoName) {
        trackEvent(MatomoCategory.CreateFolder, name)
    }

    fun trackManageFolderEvent(name: MatomoName) {
        trackEvent(MatomoCategory.ManageFolder, name)
    }

    fun trackMultiSelectionEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent(MatomoCategory.MultiSelection, name, action)
    }

    fun trackMultiSelectActionEvent(name: MatomoName, value: Int, isFromBottomSheet: Boolean = false) {
        if (isFromBottomSheet) {
            trackBottomSheetMultiSelectThreadActionsEvent(name, value)
        } else {
            trackMultiSelectThreadActionsEvent(name, value)
        }
    }

    fun trackUserInfo(name: MatomoName, value: Int? = null) {
        trackEvent(MatomoCategory.UserInfo, name, TrackerAction.DATA, value?.toFloat())
    }

    fun trackOnBoardingEvent(name: String) {
        trackEvent(MatomoCategory.Onboarding.value, name)
    }

    fun trackThreadListEvent(name: MatomoName) {
        trackThreadListEvent(name.value)
    }

    fun trackThreadListEvent(name: String) {
        trackEvent(MatomoCategory.ThreadList.value, name)
    }

    fun trackRestoreMailsEvent(name: MatomoName, action: TrackerAction) {
        trackEvent(MatomoCategory.RestoreEmailsBottomSheet, name, action)
    }

    fun trackNoValidMailboxesEvent(name: MatomoName) {
        trackEvent(MatomoCategory.NoValidMailboxes, name)
    }

    fun trackExternalEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent(MatomoCategory.Externals, name, action, value)
    }

    fun trackAiWriterEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent(MatomoCategory.AiWriter, name, action)
    }

    fun trackSyncAutoConfigEvent(name: MatomoName, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent(MatomoCategory.SyncAutoConfig, name, action)
    }

    fun trackEasterEggEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent(MatomoCategory.EasterEgg.value, name, action)
    }

    fun trackAppUpdateEvent(name: MatomoName) {
        trackEvent(MatomoCategory.AppUpdate, name)
    }

    fun trackInAppUpdateEvent(name: MatomoName) {
        trackEvent(MatomoCategory.InAppUpdate, name)
    }

    fun trackInAppReviewEvent(name: MatomoName) {
        trackEvent(MatomoCategory.InAppReview, name)
    }

    fun trackCalendarEventEvent(name: MatomoName, value: Boolean? = null) {
        trackEvent(MatomoCategory.CalendarEvent, name, value = value?.toFloat())
    }

    fun trackShortcutEvent(name: String) {
        trackEvent(MatomoCategory.HomeScreenShortcuts.value, name)
    }

    fun trackAutoAdvanceEvent(name: MatomoName) {
        trackEvent(MatomoCategory.SettingsAutoAdvance, name)
    }

    fun trackScheduleSendEvent(name: MatomoName) {
        trackEvent(MatomoCategory.ScheduleSend, name)
    }

    fun trackSnoozeEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Snooze, name)
    }

    fun trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun trackMyKSuiteUpgradeBottomSheetEvent(name: String) {
        trackEvent(MatomoKSuite.CATEGORY_MY_KSUITE_UPGRADE_BOTTOM_SHEET, name)
    }

    fun trackKSuiteProEvent(name: String) {
        trackEvent(MatomoKSuite.CATEGORY_KSUITE_PRO, name)
    }

    fun trackKSuiteProBottomSheetEvent(name: String) {
        trackEvent(MatomoKSuite.CATEGORY_KSUITE_PRO_BOTTOM_SHEET, name)
    }

    fun trackEncryptionEvent(name: MatomoName) {
        trackEvent(MatomoCategory.Encryption, name)
    }

    fun trackEditorActionEvent(action: EditorAction, isEncryptionActivated: Boolean) {
        val name = when {
            action != EditorAction.ENCRYPTION -> action.matomoName
            isEncryptionActivated -> MatomoName.OpenEncryptionActions
            else -> MatomoName.EncryptionActivation
        }
        trackEvent(MatomoCategory.EditorActions, name)
    }

    fun trackEmojiReactionsEvent(name: MatomoName) {
        trackEvent(MatomoCategory.EmojiReactions, name)
    }

    // We need to invert this logical value to keep a coherent value for analytics because actions
    // conditions are inverted (ex: if the condition is `message.isSpam`, then we want to unspam)
    private fun Boolean.toMailActionValue() = (!this).toFloat()
    //endregion

    //region Track screens
    @SuppressLint("RestrictedApi") // This `SuppressLint` is there so the CI can build
    fun Context.trackDestination(navDestination: NavDestination) = with(navDestination) {
        trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
    }

    fun Fragment.trackScreen() {
        trackScreen(path = this::class.java.name, title = this::class.java.simpleName)
    }
    //endregion
}
