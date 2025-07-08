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
import android.app.Activity
import android.content.Context
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.NavDestination
import com.infomaniak.core.myksuite.ui.utils.MatomoMyKSuite
import com.infomaniak.lib.core.MatomoCore
import com.infomaniak.lib.core.MatomoCore.TrackerAction
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftAction
import org.matomo.sdk.Tracker

object MatomoMail : MatomoCore {

    override val Context.tracker: Tracker get() = (this as MainApplication).matomoTracker
    override val siteId = 9

    //region Tracker category
    private const val THREAD_ACTION_CATEGORY = "threadActions"
    private const val THREAD_BOTTOM_SHEET_ACTION_CATEGORY = "bottomSheetThreadActions"
    //endregion

    //region Tracker name
    const val OPEN_ACTION_BOTTOM_SHEET = "openBottomSheet"
    const val OPEN_FROM_DRAFT_NAME = "openFromDraft"
    const val OPEN_LOCAL_DRAFT = "openLocalDraft"
    const val ACTION_OPEN_NAME = "open"
    const val ACTION_REPLY_NAME = "reply"
    const val ACTION_REPLY_ALL_NAME = "replyAll"
    const val ACTION_FORWARD_NAME = "forward"
    const val ACTION_DELETE_NAME = "delete"
    const val ACTION_ARCHIVE_NAME = "archive"
    const val ACTION_MARK_AS_SEEN_NAME = "markAsSeen"
    const val ACTION_MOVE_NAME = "move"
    const val ACTION_FAVORITE_NAME = "favorite"
    const val ACTION_SPAM_NAME = "spam"
    const val ACTION_PRINT_NAME = "print"
    const val ACTION_SHARE_LINK_NAME = "shareLink"
    const val ACTION_SAVE_TO_KDRIVE_NAME = "saveInkDrive"
    const val ACTION_SNOOZE_NAME = "snooze"
    const val ACTION_MODIFY_SNOOZE_NAME = "modifySnooze"
    const val ACTION_CANCEL_SNOOZE_NAME = "cancelSnooze"
    const val ADD_MAILBOX_NAME = "addMailbox"
    const val DISCOVER_LATER = "discoverLater"
    const val DISCOVER_NOW = "discoverNow"
    const val SEARCH_FOLDER_FILTER_NAME = "folderFilter"
    const val SEARCH_DELETE_NAME = "deleteSearch"
    const val SEARCH_VALIDATE_NAME = "validateSearch"
    const val SWITCH_MAILBOX_NAME = "switchMailbox"
    const val LAST_SELECTED_SCHEDULE = "lastSelectedSchedule"
    const val CUSTOM_SCHEDULE = "customSchedule"
    const val CUSTOM_SCHEDULE_CONFIRM = "customScheduleConfirm"
    const val SCHEDULED_CUSTOM_DATE = "scheduledCustomDate"
    const val SNOOZE_CUSTOM_DATE = "snoozeCustomDate"
    //endregion
    enum class MatomoCategory(name: String) {
        Account("account"),
        MenuDrawer("menuDrawer"),
        NewMessage("newMessage"),
        EditorActions("editorActions"),
        BottomSheetThreadActions("bottomSheetThreadActions"),
        BottomSheetMessageActions("bottomSheetMessageActions"),
        Search("search"),
        MoveSearch("moveSearch"),
        ContactActions("contactActions"),
        Message("message"),
        MessageActions("messageActions"),
        ThreadActions("threadActions"),
        SwipeActions("swipeActions"),
        SettingsGeneral("settingsGeneral"),
        SettingsDensity("settingsDensity"),
        SettingsTheme("settingsTheme"),
        SettingsAccentColor("settingsAccentColor"),
        SettingsSwipeActions("settingsSwipeActions"),
        SettingsThreadMode("settingsThreadMode"),
        SettingsDisplayExternalContent("settingsDisplayExternalContent"),
        SettingsNotifications("settingsNotifications"),
        SettingsSend("settingsSend"),
        SettingsForwardMode("settingsForwardMode"),
        SettingsCancelPeriod("settingsCancelPeriod"),
        SettingsDataManagement("settingsDataManagement"),
        SettingsAutoAdvance("settingsAutoAdvance"),
        Snackbar("snackbar"),
        UserInfo("userInfo"),
        MultiSelection("multiSelection"),
        CreateFolder("createFolder"),
        ReplyBottomSheet("replyBottomSheet"),
        AttachmentActions("attachmentActions"),
        Onboarding("onboarding"),
        ThreadList("threadList"),
        RestoreEmailsBottomSheet("restoreEmailsBottomSheet"),
        NoValidMailboxes("noValidMailboxes"),
        InvalidPasswordMailbox("invalidPasswordMailbox"),
        NotificationActions("notificationActions"),
        Externals("externals"),
        AiWriter("aiWriter"),
        SyncAutoConfig("syncAutoConfig"),
        AppReview("appReview"),
        AppUpdate("appUpdate"),
        InAppUpdate("inAppUpdate"),
        KeyboardShortcutActions("keyboardShortcutActions"),
        EasterEgg("easterEgg"),
        CalendarEvent("calendarEvent"),
        HomeScreenShortcuts("homeScreenShortcuts"),
        UpdateVersion("updateVersion"),
        SearchMultiSelection("searchMultiSelection"),
        BlockUserAction("blockUserAction"),
        ScheduleSend("scheduleSend"),
        Snooze("snooze"),
        MyKSuite("myKSuite"),
        MyKSuiteUpgradeBottomSheet("myKSuiteUpgradeBottomSheet"),
        ManageFolder("manageFolder"),
    }

    enum class MatomoName(name: String){
        OpenLoginWebview("openLoginWebview"),
        OpenCreationWebview("openCreationWebview"),
        LoggedIn("loggedIn"),
        Switch("switch"),
        Add("add"),
        LogOut("logOut"),
        LogOutConfirm("logOutConfirm"),
        DeleteAccount("deleteAccount"),
        AddMailbox("addMailbox"),
        AddMailboxConfirm("addMailboxConfirm"),
        SwitchMailbox("switchMailbox"),
        OpenByGesture("openByGesture"),
        OpenByButton("openByButton"),
        CloseByTap("closeByTap"),
        CloseByAccessibility("closeByAccessibility"),
        CloseByGesture("closeByGesture"),
        Mailboxes("mailboxes"),
        InboxFolder("inboxFolder"),
        SentFolder("sentFolder"),
        SnoozedFolder("snoozedFolder"),
        ScheduledDraftsFolder("scheduledDraftsFolder"),
        DraftFolder("draftFolder"),
        SpamFolder("spamFolder"),
        TrashFolder("trashFolder"),
        ArchiveFolder("archiveFolder"),
        CommercialFolder("commercialFolder"),
        SocialNetworksFolder("socialNetworksFolder"),
        CustomFolders("customFolders"),
        CustomFolder("customFolder"),
        CollapseFolder("collapseFolder"),
        AdvancedActions("advancedActions"),
        ImportEmails("importEmails"),
        RestoreEmails("restoreEmails"),
        Feedback("feedback"),
        JoinBetaProgram("joinBetaProgram"),
        Help("help"),
        OpenFromFab("openFromFab"),
        DeleteSignature("deleteSignature"),
        DeleteRecipient("deleteRecipient"),
        SendMail("sendMail"),
        OpenFromDraft("openFromDraft"),
        OpenLocalDraft("openLocalDraft"),
        NumberOfTo("numberOfTo"),
        NumberOfCc("numberOfCc"),
        NumberOfBcc("numberOfBcc"),
        AddNewRecipient("addNewRecipient"),
        SaveDraft("saveDraft"),
        ScheduleDraft("scheduleDraft"),
        SendReaction("sendReaction"),
        SendWithoutSubject("sendWithoutSubject"),
        SwitchIdentity("switchIdentity"),
        TrySendingWithDailyLimitReached("trySendingWithDailyLimitReached"),
        TrySendingWithMailboxFull("trySendingWithMailboxFull"),
        SendWithoutSubjectConfirm("sendWithoutSubjectConfirm"),
        Bold("bold"),
        Italic("italic"),
        Underline("underline"),
        StrikeThrough("strikeThrough"),
        UnorderedList("unorderedList"),
        ImportFile("importFile"),
        ImportImage("importImage"),
        ImportFromCamera("importFromCamera"),
        AddLink("addLink"),
        AddLinkConfirm("addLinkConfirm"),
        AiWriter("aiWriter"),
        Reply("reply"),
        ReplyAll("replyAll"),
        Forward("forward"),
        Delete("delete"),
        Archive("archive"),
        MarkAsSeen("markAsSeen"),
        Move("move"),
        Favorite("favorite"),
        Spam("spam"),
        SignalPhishing("signalPhishing "),
        BlockUser("blockUser"),
        Print("print"),
        PrintValidated("printValidated"),
        PrintCancelled("printCancelled"),
        Snooze("snooze"),
        ModifySnooze("modifySnooze"),
        CancelSnooze("cancelSnooze"),
        MoveToInbox("moveToInbox"),
        ShareLink("shareLink"),
        SaveInkDrive("saveInkDrive"),
        ReadFilter("readFilter"),
        UnreadFilter("unreadFilter"),
        FavoriteFilter("favoriteFilter"),
        AttachmentFilter("attachmentFilter"),
        FolderFilter("folderFilter"),
        DeleteSearch("deleteSearch"),
        DeleteFromHistory("deleteFromHistory"),
        SelectContact("selectContact"),
        FromHistory("fromHistory"),
        ValidateSearch("validateSearch"),
        ExecuteSearch("executeSearch"),
        WriteEmail("writeEmail"),
        AddToContacts("addToContacts"),
        CopyEmailAddress("copyEmailAddress"),
        SelectAvatar("selectAvatar"),
        SelectRecipient("selectRecipient"),
        OpenMessage("openMessage"),
        OpenDetails("openDetails"),
        DeleteDraft("deleteDraft"),
        OpenBottomSheet("openBottomSheet"),
        Postpone("postpone"),
        Tutorial("tutorial"),
        QuickActions("quickActions"),
        Lock("lock"),
        Compact("compact"),
        Normal("normal"),
        Large("large"),
        System("system"),
        Light("light"),
        Dark("dark"),
        Pink("pink"),
        Blue("blue"),
        DeleteSwipe("deleteSwipe"),
        ArchiveSwipe("archiveSwipe"),
        MoveSwipe("moveSwipe"),
        FavoriteSwipe("favoriteSwipe"),
        PostponeSwipe("postponeSwipe"),
        SpamSwipe("spamSwipe"),
        QuickActionsSwipe("quickActionsSwipe"),
        NoneSwipe("noneSwipe"),
        Conversation("conversation"),
        Message("message"),
        Always("always"),
        AskMe("askMe"),
        AllNotifications("allNotifications"),
        MailboxNotifications("mailboxNotifications"),
        OpenNotificationSettings("openNotificationSettings"),
        IncludeOriginalInReply("includeOriginalInReply"),
        Acknowledgement("acknowledgement"),
        Inline("inline"),
        Attachment("attachment"),
        ZeroSecond("0s"),
        TenSecond("10s"),
        FifteenSecond("15s"),
        TwentySecond("20s"),
        TwentyFiveSecond("25s"),
        ThirtySecond("30s"),
        ShowSourceCode("showSourceCode"),
        PreviousThread("previousThread"),
        FollowingThread("followingThread"),
        ListOfThread("listOfThread"),
        NaturalThread("naturalThread"),
        Undo("undo"),
        NbMailboxes("nbMailboxes"),
        NbMessagesInThread("nbMessagesInThread"),
        OneMessagesInThread("oneMessagesInThread"),
        MultipleMessagesInThread("multipleMessagesInThread"),
        Cancel("cancel"),
        All("all"),
        None("none"),
        Enable("enable"),
        FromMenuDrawer("fromMenuDrawer"),
        FromMove("fromMove"),
        Confirm("confirm"),
        Download("download"),
        Share("share"),
        DownloadAll("downloadAll"),
        SaveToKDrive("saveToKDrive"),
        OpenFromBottomsheet("openFromBottomsheet"),
        Open("open"),
        OpenSwissTransfer("openSwissTransfer"),
        SwitchColorBlue("switchColorBlue"),
        SwitchColorPink("switchColorPink"),
        SwitchColorSystem("switchColorSystem"),
        LoadMore("loadMore"),
        EmptyTrash("emptyTrash"),
        EmptyTrashConfirm("emptyTrashConfirm"),
        EmptyDraft("emptyDraft"),
        EmptyDraftConfirm("emptyDraftConfirm"),
        EmptySpam("emptySpam"),
        EmptySpamConfirm("emptySpamConfirm"),
        SelectDate("selectDate"),
        Restore("restore"),
        SwitchAccount("switchAccount"),
        ReadFAQ("readFAQ"),
        UpdatePassword("updatePassword"),
        RequestPassword("requestPassword"),
        DetachMailbox("detachMailbox"),
        DetachMailboxConfirm("detachMailboxConfirm"),
        CancelClicked("cancelClicked"),
        ArchiveClicked("archiveClicked"),
        DeleteClicked("deleteClicked"),
        ArchiveExecuted("archiveExecuted"),
        DeleteExecuted("deleteExecuted"),
        ThreadTag("threadTag"),
        BannerInfo("bannerInfo"),
        BannerManuallyClosed("bannerManuallyClosed"),
        EmailSentWithExternals("emailSentWithExternals"),
        EmailSentExternalQuantity("emailSentExternalQuantity"),
        DismissPromptWithoutGenerating("dismissPromptWithoutGenerating"),
        Generate("generate"),
        DismissProposition("dismissProposition"),
        InsertProposition("insertProposition"),
        ReplaceProposition("replaceProposition"),
        ReplacePropositionConfirm("replacePropositionConfirm"),
        ReplacePropositionDialog("replacePropositionDialog"),
        ReplaceSubjectDialog("replaceSubjectDialog"),
        ReplaceSubjectConfirm("replaceSubjectConfirm"),
        KeepSubject("keepSubject"),
        Refine("refine"),
        Edit("edit"),
        Regenerate("regenerate"),
        Shorten("shorten"),
        Expand("expand"),
        SeriousWriting("seriousWriting"),
        FriendlyWriting("friendlyWriting"),
        Retry("retry"),
        DiscoverNow("discoverNow"),
        DiscoverLater("discoverLater"),
        DismissError("dismissError"),
        OpenFromMenuDrawer("openFromMenuDrawer"),
        OpenFromSettings("openFromSettings"),
        ConfigureInstall("configureInstall"),
        ConfigureReady("configureReady"),
        OpenPlayStore("openPlayStore"),
        OpenSyncApp("openSyncApp"),
        AlreadySynchronized("alreadySynchronized"),
        Start("start"),
        Done("done"),
        OpenSettings("openSettings"),
        CopyPassword("copyPassword"),
        PresentAlert("presentAlert"),
        Like("like"),
        Dislike("dislike"),
        InstallUpdate("installUpdate"),
        Refresh("refresh"),
        NextThread("nextThread"),
        NewMessage("newMessage"),
        NewWindow("newWindow"),
        HalloweenYYYY("halloweenYYYY"),
        XmasYYYY("xmasYYYY"),
        ConfettiMenuDrawer("confettiMenuDrawer"),
        ConfettiAvatar("confettiAvatar"),
        Attendees("attendees"),
        SeeAllAttendees("seeAllAttendees"),
        ReplyYes("replyYes"),
        ReplyMaybe("replyMaybe"),
        ReplyNo("replyNo"),
        OpenInMyCalendar("openInMyCalendar"),
        Search("search"),
        Support("support"),
        MoreInfo("moreInfo"),
        Later("later"),
        Update("update"),
        SelectUser("selectUser"),
        ConfirmSelectedUser("confirmSelectedUser"),
        LaterThisMorning("laterThisMorning"),
        ThisAfternoon("thisAfternoon"),
        ThisEvening("thisEvening"),
        TomorrowMorning("tomorrowMorning"),
        NextMonday("nextMonday"),
        NextMondayMorning("nextMondayMorning"),
        NextMondayAfternoon("nextMondayAfternoon"),
        LastSelectedSchedule("lastSelectedSchedule"),
        CustomSchedule("customSchedule"),
        CustomScheduleConfirm("customScheduleConfirm"),
        OpenDashboard("openDashboard"),
        TryAddingFileWithDriveFull("tryAddingFileWithDriveFull"),
        CloseStorageWarningBanner("closeStorageWarningBanner"),
        DropboxQuotaExceeded("dropboxQuotaExceeded"),
        ShareLinkQuotaExceeded("shareLinkQuotaExceeded"),
        TrashStorageLimit("trashStorageLimit"),
        ShareLinkPassword("shareLinkPassword"),
        ShareLinkExpiryDate("shareLinkExpiryDate"),
        ColorFolder("colorFolder"),
        ConvertToDropbox("convertToDropbox"),
        SnoozeCustomDate("snoozeCustomDate"),
        ScheduledCustomDate("scheduledCustomDate"),
        EmptySignature("emptySignature"),
        NotEnoughStorageUpgrade("notEnoughStorageUpgrade"),
        DailyLimitReachedUpgrade("dailyLimitReachedUpgrade"),
        ShareEmail("shareEmail"),
        Rename("rename"),
        RenameConfirm("renameConfirm"),
        DeleteConfirm("deleteConfirm"),
    }


    @SuppressLint("RestrictedApi") // This `SuppressLint` is there so the CI can build
    fun Context.trackDestination(navDestination: NavDestination) = with(navDestination) {
        trackScreen(displayName.substringAfter("${BuildConfig.APPLICATION_ID}:id"), label.toString())
    }

    fun Context.trackSendingDraftEvent(
        action: DraftAction,
        to: List<Recipient>,
        cc: List<Recipient>,
        bcc: List<Recipient>,
        externalMailFlagEnabled: Boolean,
    ) {
        trackNewMessageEvent(action.matomoValue)
        if (action == DraftAction.SEND) {
            val trackerData = listOf("numberOfTo" to to, "numberOfCc" to cc, "numberOfBcc" to bcc)
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

                trackExternalEvent("emailSentWithExternals", TrackerAction.DATA, externalRecipientCount > 0)
                trackExternalEvent("emailSentExternalQuantity", TrackerAction.DATA, externalRecipientCount.toFloat())
            }
        }
    }

    fun Fragment.trackContactActionsEvent(name: String) {
        trackEvent("contactActions", name)
    }

    fun Fragment.trackAttachmentActionsEvent(name: String) {
        trackEvent("attachmentActions", name)
    }

    fun Fragment.trackBottomSheetMessageActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "bottomSheetMessageActions", name = name, value = value?.toMailActionValue())
    }

    fun Fragment.trackBottomSheetThreadActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = THREAD_BOTTOM_SHEET_ACTION_CATEGORY, name = name, value = value?.toMailActionValue())
    }

    private fun Fragment.trackBottomSheetMultiSelectThreadActionsEvent(name: String, value: Int) {
        trackEvent(category = THREAD_BOTTOM_SHEET_ACTION_CATEGORY, name = name, value = value.toFloat())
    }

    fun Fragment.trackThreadActionsEvent(name: String, value: Boolean? = null) {
        trackEvent(category = THREAD_ACTION_CATEGORY, name = name, value = value?.toMailActionValue())
    }

    private fun Fragment.trackMultiSelectThreadActionsEvent(name: String, value: Int) {
        trackEvent(category = THREAD_ACTION_CATEGORY, name = name, value = value.toFloat())
    }

    fun Fragment.trackMessageActionsEvent(name: String) {
        trackEvent("messageActions", name)
    }

    fun Fragment.trackBlockUserAction(name: String) {
        requireContext().trackBlockUserAction(name)
    }

    fun Context.trackBlockUserAction(name: String) {
        trackEvent("blockUserAction", name)
    }

    fun Fragment.trackSearchEvent(name: String, value: Boolean? = null) {
        context?.trackSearchEvent(name, value)
    }

    fun Fragment.trackMoveSearchEvent(name: String) {
        trackEvent("moveSearch", name)
    }

    fun Context.trackMessageEvent(name: String, value: Boolean? = null) {
        trackEvent("message", name, value = value?.toFloat())
    }

    fun Context.trackSearchEvent(name: String, value: Boolean? = null) {
        trackEvent(category = "search", name = name, value = value?.toFloat())
    }

    fun Context.trackNotificationActionEvent(name: String) {
        trackEvent(category = "notificationActions", name = name)
    }

    fun Fragment.trackNewMessageEvent(name: String) {
        context?.trackNewMessageEvent(name)
    }

    fun Context.trackNewMessageEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("newMessage", name, action, value)
    }

    fun Fragment.trackMenuDrawerEvent(name: String, value: Boolean? = null) {
        context?.trackMenuDrawerEvent(name, value = value?.toFloat())
    }

    fun Context.trackMenuDrawerEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("menuDrawer", name, action, value)
    }

    fun Fragment.trackCreateFolderEvent(name: String) {
        context?.trackCreateFolderEvent(name)
    }

    fun Context.trackCreateFolderEvent(name: String) {
        trackEvent("createFolder", name)
    }

    fun Context.trackRenameFolderEvent(name: String) {
        trackEvent("manageFolder", name)
    }

    fun Context.trackMultiSelectionEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("multiSelection", name, action)
    }

    fun Fragment.trackMultiSelectActionEvent(name: String, value: Int, isFromBottomSheet: Boolean = false) {
        val trackerName = "${if (value == 1) "bulkSingle" else "bulk"}${name.capitalizeFirstChar()}"

        if (isFromBottomSheet) {
            trackBottomSheetMultiSelectThreadActionsEvent(trackerName, value)
        } else {
            trackMultiSelectThreadActionsEvent(trackerName, value)
        }
    }

    fun Context.trackUserInfo(name: String, value: Int? = null) {
        trackEvent("userInfo", name, TrackerAction.DATA, value?.toFloat())
    }

    fun Fragment.trackOnBoardingEvent(name: String) {
        trackEvent("onboarding", name)
    }

    fun Fragment.trackThreadListEvent(name: String) {
        trackEvent("threadList", name)
    }

    fun Fragment.trackRestoreMailsEvent(name: String, action: TrackerAction) {
        trackEvent("restoreEmailsBottomSheet", name, action)
    }

    fun Fragment.trackNoValidMailboxesEvent(name: String) {
        trackEvent("noValidMailboxes", name)
    }

    fun Fragment.trackInvalidPasswordMailboxEvent(name: String) {
        trackEvent("invalidPasswordMailbox", name)
    }

    fun Context.trackExternalEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Float? = null) {
        trackEvent("externals", name, action, value)
    }

    fun Context.trackExternalEvent(name: String, action: TrackerAction = TrackerAction.CLICK, value: Boolean) {
        trackEvent("externals", name, action, value.toFloat())
    }

    fun Fragment.trackAiWriterEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        context?.trackAiWriterEvent(name, action)
    }

    fun Context.trackAiWriterEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("aiWriter", name, action)
    }

    fun Fragment.trackSyncAutoConfigEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("syncAutoConfig", name, action)
    }

    fun Fragment.trackEasterEggEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        context?.trackEasterEggEvent(name, action)
    }

    fun Context.trackEasterEggEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("easterEgg", name, action)
    }

    fun Activity.trackAppReviewEvent(name: String, action: TrackerAction = TrackerAction.CLICK) {
        trackEvent("appReview", name, action)
    }

    fun Fragment.trackAppUpdateEvent(name: String) {
        trackEvent("appUpdate", name)
    }

    fun Context.trackInAppUpdateEvent(name: String) {
        trackEvent("inAppUpdate", name)
    }

    fun Context.trackInAppReviewEvent(name: String) {
        trackEvent("inAppReview", name)
    }

    fun View.trackCalendarEventEvent(name: String, value: Boolean? = null) {
        context.trackEvent("calendarEvent", name, value = value?.toFloat())
    }

    fun Context.trackShortcutEvent(name: String) {
        trackEvent("homeScreenShortcuts", name)
    }

    fun Fragment.trackAutoAdvanceEvent(name: String) {
        trackEvent("settingsAutoAdvance", name)
    }

    fun Fragment.trackScheduleSendEvent(name: String) {
        trackEvent("scheduleSend", name)
    }

    fun Context.trackScheduleSendEvent(name: String) {
        trackEvent("scheduleSend", name)
    }

    fun Fragment.trackSnoozeEvent(name: String) {
        trackEvent("snooze", name)
    }

    fun Fragment.trackMyKSuiteEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE, name)
    }

    fun Context.trackMyKSuiteUpgradeBottomSheetEvent(name: String) {
        trackEvent(MatomoMyKSuite.CATEGORY_MY_KSUITE_UPGRADE_BOTTOMSHEET, name)
    }

    // We need to invert this logical value to keep a coherent value for analytics because actions
    // conditions are inverted (ex: if the condition is `message.isSpam`, then we want to unspam)
    private fun Boolean.toMailActionValue() = (!this).toFloat()
}
