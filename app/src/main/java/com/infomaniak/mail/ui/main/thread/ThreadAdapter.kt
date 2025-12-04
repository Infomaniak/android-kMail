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
package com.infomaniak.mail.ui.main.thread

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View.OnClickListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.extensions.isNightModeEnabled
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.utils.FORMAT_DATE_DAY_FULL_MONTH_YEAR_WITH_TIME
import com.infomaniak.core.utils.FormatData
import com.infomaniak.core.utils.format
import com.infomaniak.core.utils.formatWithLocal
import com.infomaniak.emojicomponents.data.Reaction
import com.infomaniak.emojicomponents.views.EmojiReactionsView
import com.infomaniak.mail.MatomoMail.MatomoName
import com.infomaniak.mail.MatomoMail.trackMessageEvent
import com.infomaniak.mail.MatomoMail.trackScheduleSendEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachable
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.calendar.CalendarEventResponse
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.mailbox.SenderDetails
import com.infomaniak.mail.data.models.mailbox.SendersRestrictions
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.databinding.ItemSuperCollapsedBlockBinding
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadAdapterViewHolder
import com.infomaniak.mail.ui.main.thread.models.MessageUi
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.HtmlFormatter
import com.infomaniak.mail.utils.MessageBodyUtils
import com.infomaniak.mail.utils.SharedUtils.Companion.createHtmlForPlainText
import com.infomaniak.mail.utils.UiUtils
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.TEXT_HTML
import com.infomaniak.mail.utils.Utils.TEXT_PLAIN
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.WebViewUtils
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupThreadWebViewSettings
import com.infomaniak.mail.utils.WebViewUtils.Companion.toggleWebViewTheme
import com.infomaniak.mail.utils.date.DateFormatUtils.fullDateWithYear
import com.infomaniak.mail.utils.date.MailDateFormatUtils.mailFormattedDate
import com.infomaniak.mail.utils.extensions.AttachmentExt.AttachmentIntentType
import com.infomaniak.mail.utils.extensions.enableAlgorithmicDarkening
import com.infomaniak.mail.utils.extensions.formatSubject
import com.infomaniak.mail.utils.extensions.getAttributeColor
import com.infomaniak.mail.utils.extensions.indexOfFirstOrNull
import com.infomaniak.mail.utils.extensions.initWebViewClientAndBridge
import com.infomaniak.mail.utils.extensions.toDate
import com.infomaniak.mail.utils.extensions.toggleChevron
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.format.FormatStyle
import java.util.Date
import androidx.appcompat.R as RAndroid
import com.google.android.material.R as RMaterial

class ThreadAdapter(
    private val shouldLoadDistantResources: Boolean,
    private val isForPrinting: Boolean = false,
    private val isSpamFilterActivated: () -> Boolean = { false },
    private val areMessagesCollapsibles: () -> Boolean,
    private val senderRestrictions: () -> SendersRestrictions? = { null },
    private val threadAdapterState: ThreadAdapterState,
    private var threadAdapterCallbacks: ThreadAdapterCallbacks? = null,
) : ListAdapter<Any, ThreadAdapterViewHolder>(MessageDiffCallback()) {

    inline val items: List<Any> get() = currentList

    //region Auto-scroll at Thread opening
    private val currentSetOfLoadedExpandedMessagesUids = mutableSetOf<String>()
    private var hasNotScrolledYet = true
    //endregion

    private val manuallyAllowedMessagesUids = mutableSetOf<String>()

    private lateinit var recyclerView: RecyclerView
    private val webViewUtils by lazy { WebViewUtils(recyclerView.context) }

    private val scaledTouchSlop by lazy { ViewConfiguration.get(recyclerView.context).scaledTouchSlop }

    private var ItemMessageBinding.isQuoteCollapsed
        get() = bodyWebView.isVisible
        set(value) {
            bodyWebView.isVisible = value
            fullMessageWebView.isVisible = !value

            val textId = if (isQuoteCollapsed) R.string.messageShowQuotedText else R.string.messageHideQuotedText
            quoteButton.text = context.getString(textId)
        }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = items.count()

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (items[position]) {
            is MessageUi -> DisplayType.MAIL.layout
            else -> DisplayType.SUPER_COLLAPSED_BLOCK.layout
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadAdapterViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == DisplayType.MAIL.layout) {
            MessageViewHolder(
                ItemMessageBinding.inflate(layoutInflater, parent, false),
                shouldLoadDistantResources,
                threadAdapterCallbacks?.onContactClicked,
                threadAdapterCallbacks?.onAttachmentClicked,
                threadAdapterCallbacks?.onAttachmentOptionsClicked,
            )
        } else {
            SuperCollapsedBlockViewHolder(ItemSuperCollapsedBlockBinding.inflate(layoutInflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ThreadAdapterViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {
        val payload = payloads.firstOrNull()
        if (payload !is NotifyType) {
            super.onBindViewHolder(holder, position, payloads)
            return@runCatchingRealm
        }

        val item = items[position]
        if (item is MessageUi && holder is MessageViewHolder) with(holder.binding) {
            when (payload) {
                NotifyType.ToggleLightMode -> holder.handleToggleLightModePayload(item.message.uid)
                NotifyType.ReRender -> reloadVisibleWebView()
                NotifyType.FailedMessage -> handleFailedMessagePayload(item.message.uid)
                NotifyType.OnlyRebindCalendarAttendance -> handleCalendarAttendancePayload(item.message)
                NotifyType.OnlyRebindEmojiReactions -> handleEmojiReactionPayload(item)
                NotifyType.UnsubscribeRebind -> bindUnsubscribe(item)
                is NotifyType.MessagesCollapseStateChanged -> {
                    holder.handleMessagesCollapseStatePayload(item.message, isCollapsible = payload.isCollapsible)
                }
            }
        }
    }.getOrDefault(Unit)

    private fun MessageViewHolder.handleToggleLightModePayload(messageUid: String) = with(threadAdapterState) {
        isThemeTheSameMap[messageUid] = !isThemeTheSameMap[messageUid]!!
        toggleContentAndQuoteTheme(messageUid)
    }

    private fun ItemMessageBinding.handleFailedMessagePayload(messageUid: String) {
        messageLoader.isGone = true
        failedLoadingErrorMessage.isVisible = true
        if (threadAdapterState.isExpandedMap[messageUid] == true) onExpandedMessageLoaded(messageUid)
    }

    private fun ItemMessageBinding.handleCalendarAttendancePayload(message: Message) {
        val attendees = message.latestCalendarEventResponse?.calendarEvent?.attendees ?: emptyList()
        calendarEvent.onlyUpdateAttendance(attendees)
    }

    private fun ItemMessageBinding.handleEmojiReactionPayload(message: MessageUi) {
        emojiReactions.bindEmojiReactions(message)
    }

    private fun MessageViewHolder.handleMessagesCollapseStatePayload(message: Message, isCollapsible: Boolean) {
        handleHeaderClick(message, isCollapsible)
        if (!isCollapsible) {
            threadAdapterState.isExpandedMap[message.uid] = true
            onExpandOrCollapseMessage(message, shouldTrack = false)
        }
    }

    private fun EmojiReactionsView.bindEmojiReactions(message: MessageUi) {
        val canBeReactedTo by lazy { message.canBeReactedTo() }

        isVisible = message.isReactionsFeatureAvailable && (canBeReactedTo || message.hasEmojis())
        setEmojiReactions(message.emojiReactionsState.map { it.value })

        if (message.isReactionsFeatureAvailable) setAddReactionEnabledState(isEnabled = canBeReactedTo)
    }

    override fun onBindViewHolder(holder: ThreadAdapterViewHolder, position: Int) {

        val item = items[position]

        holder.binding.root.tag = if (item is SuperCollapsedBlock || (item is MessageUi && item.message.shouldHideDivider)) {
            UiUtils.IGNORE_DIVIDER_TAG
        } else {
            null
        }

        if (item is MessageUi) {
            (holder as MessageViewHolder).bindMail(item, position)
        } else {
            (holder as SuperCollapsedBlockViewHolder).bindSuperCollapsedBlock(item as SuperCollapsedBlock)
        }
    }

    private fun SuperCollapsedBlockViewHolder.bindSuperCollapsedBlock(
        item: SuperCollapsedBlock,
    ) = with(binding.superCollapsedBlock) {
        text = context.getString(R.string.superCollapsedBlock, item.messagesUids.count())
        setOnClickListener {
            text = context.getString(R.string.loadingText)
            threadAdapterCallbacks?.onSuperCollapsedBlockClicked?.invoke()
        }
    }

    private fun MessageViewHolder.bindMail(messageUi: MessageUi, position: Int) {

        initMapForNewMessage(messageUi.message, position)

        bindHeader(messageUi.message)
        bindAlerts(messageUi)
        bindCalendarEvent(messageUi.message)
        bindAttachments(messageUi.message)
        bindContent(messageUi.message)
        bindEmojiReactions(messageUi)

        onExpandOrCollapseMessage(messageUi.message, shouldTrack = false)
    }

    private fun MessageViewHolder.bindCalendarEvent(message: Message) {

        val calendarAttachment = message.calendarAttachment ?: return
        val calendarEvent = message.latestCalendarEventResponse?.calendarEvent

        binding.calendarEvent.apply {
            isVisible = calendarEvent != null

            calendarEvent?.let {
                val calendarEventResponse = message.latestCalendarEventResponse!!

                loadCalendarEvent(
                    calendarEvent = it,
                    isCanceled = calendarEventResponse.isCanceled,
                    shouldDisplayReplyOptions = calendarEventResponse.isReplyAuthorized(),
                    attachment = calendarAttachment,
                    hasAssociatedInfomaniakCalendarEvent = calendarEventResponse.hasAssociatedInfomaniakCalendarEvent(),
                    shouldStartExpanded = threadAdapterState.isCalendarEventExpandedMap[message.uid] == true,
                )
            }

            initCallback(
                navigateToAttendeesBottomSheet = { attendees ->
                    threadAdapterCallbacks?.navigateToAttendeeBottomSheet?.invoke(attendees)
                },
                navigateToDownloadProgressDialog = { attachment, attachmentIntentType ->
                    threadAdapterCallbacks?.navigateToDownloadProgressDialog?.invoke(attachment, attachmentIntentType)
                },
                replyToCalendarEvent = { attendanceState ->
                    threadAdapterCallbacks?.replyToCalendarEvent?.invoke(attendanceState, message)
                },
                onAttendeesButtonClicked = { isExpanded ->
                    threadAdapterState.isCalendarEventExpandedMap[message.uid] = isExpanded
                },
            )
        }
    }

    private fun initMapForNewMessage(message: Message, position: Int) = with(threadAdapterState) {
        if (isExpandedMap[message.uid] == null) {
            isExpandedMap[message.uid] = message.shouldBeExpanded(position, items.lastIndex)
        }

        if (isThemeTheSameMap[message.uid] == null) isThemeTheSameMap[message.uid] = true
    }

    private fun MessageViewHolder.toggleContentAndQuoteTheme(messageUid: String) = with(binding) {
        val isThemeTheSame = isThemeTheSameForMessageUid(messageUid)
        bodyWebView.toggleWebViewTheme(isThemeTheSame)
        fullMessageWebView.toggleWebViewTheme(isThemeTheSame)
        toggleFrameLayoutsTheme(isThemeTheSame)
    }

    private fun MessageViewHolder.loadContentAndQuote(message: Message) {
        val body = message.body
        val splitBody = message.splitBody

        if (body != null && splitBody != null) {
            if (binding.isQuoteCollapsed) {
                val completeBody = MessageBodyUtils.mergeSplitBodyAndSubBodies(splitBody.content, body.subBodies)
                loadBodyInWebView(message.uid, completeBody, body.type)
            } else {
                loadQuoteInWebView(message.uid, splitBody.quote, body.type)
            }
        }
    }

    private fun MessageViewHolder.loadBodyInWebView(uid: String, body: String, type: String) = with(binding) {
        bodyWebView.applyWebViewContent(uid, body, type)
    }

    private fun MessageViewHolder.loadQuoteInWebView(uid: String, quote: String?, type: String) = with(binding) {
        if (quote == null) return@with
        fullMessageWebView.applyWebViewContent(uid, quote, type)
    }

    private fun MessageViewHolder.toggleWebViews(message: Message) = with(binding) {
        isQuoteCollapsed = !isQuoteCollapsed
        loadContentAndQuote(message)
    }

    private fun WebView.applyWebViewContent(uid: String, bodyWebView: String, type: String) {
        enableAlgorithmicDarkening(isThemeTheSameForMessageUid(uid))

        var styledBody = if (type == TEXT_PLAIN) createHtmlForPlainText(bodyWebView) else bodyWebView
        styledBody = processMailDisplay(styledBody, uid, isForPrinting)

        settings.setupThreadWebViewSettings()
        setupZoomListeners()

        loadDataWithBaseURL("", styledBody, TEXT_HTML, Utils.UTF_8, "")
    }

    private fun isThemeTheSameForMessageUid(messageUid: String) = threadAdapterState.isThemeTheSameMap[messageUid] ?: run {
        // TODO: Find the cause. The bug probably affects other parts of the code that do not crash
        Sentry.captureMessage("Missing message uid inside isThemeTheSameMap", SentryLevel.ERROR) { scope ->
            val mapStringRepresentation = threadAdapterState.isThemeTheSameMap
                .map { (key, value) -> "($key -> $value)" }
                .joinToString(prefix = "[", postfix = "]")

            scope.setExtra("isThemeTheSameMap", mapStringRepresentation)
            scope.setExtra("looking for messageUid", messageUid)
        }

        true
    }

    private fun WebView.processMailDisplay(styledBody: String, uid: String, isForPrinting: Boolean): String {
        val isDisplayedInDark =
            context.isNightModeEnabled() && isThemeTheSameForMessageUid(uid) && !isForPrinting
        return if (isForPrinting) {
            webViewUtils.processHtmlForPrint(styledBody, HtmlFormatter.PrintData(context, (items.first() as MessageUi).message))
        } else {
            webViewUtils.processHtmlForDisplay(styledBody, isDisplayedInDark)
        }
    }

    private fun WebView.setupZoomListeners() {
        val scaleListener = MessageBodyScaleListener(recyclerView, this, this.parent as FrameLayout)
        val scaleDetector = ScaleGestureDetector(context, scaleListener)
        val touchListener = MessageBodyTouchListener(recyclerView, scaleDetector, scaledTouchSlop)
        setOnTouchListener(touchListener)
    }

    private fun ItemMessageBinding.toggleFrameLayoutsTheme(isThemeTheSame: Boolean) {
        if (isThemeTheSame) {
            val color = context.getColor(R.color.background_color_dark)
            webViewsFrameLayout.setBackgroundColor(color)
            quoteButtonFrameLayout.setBackgroundColor(color)
            quoteButton.setTextColor(context.getAttributeColor(RAndroid.attr.colorPrimary))
        } else {
            val color = context.getColor(R.color.background_color_light)
            webViewsFrameLayout.setBackgroundColor(color)
            quoteButtonFrameLayout.setBackgroundColor(color)
            quoteButton.setTextColor(context.getAttributeColor(RMaterial.attr.colorPrimaryInverse))
        }
    }

    private fun MessageViewHolder.bindHeader(message: Message) = with(binding) {
        val messageDate = message.displayDate.toDate()

        if (message.isDraft) {
            userAvatar.loadUserAvatar(AccountUtils.currentUser!!)
            expeditorName.apply {
                text = context.getString(R.string.messageIsDraftOption)
                setTextAppearance(R.style.BodyMedium_Error)
            }
            shortMessageDate.text = ""
        } else {
            val firstSender = message.sender

            expeditorName.apply {
                text = firstSender?.let { context.getPrettyNameAndEmail(it).first }
                    ?: run { context.getString(R.string.unknownRecipientTitle) }
                setTextAppearance(R.style.BodyMedium)
            }

            userAvatar.loadAvatar(firstSender, message.bimi)
            certifiedIcon.isVisible = message.bimi?.isCertified == true

            shortMessageDate.text = context.mailFormattedDate(messageDate)
        }

        val listener: OnClickListener? = message.sender?.let { recipient ->
            OnClickListener {
                trackMessageEvent(MatomoName.SelectAvatar)
                threadAdapterCallbacks?.onContactClicked?.invoke(recipient, message.bimi)
            }
        }

        userAvatar.setOnClickListener(listener)

        setDetailedFieldsVisibility(message)

        handleHeaderClick(message, areMessagesCollapsibles())
        handleExpandDetailsClick(message)
        bindRecipientDetails(message, messageDate)
    }

    private fun ItemMessageBinding.setDetailedFieldsVisibility(message: Message) {
        fromGroup.isVisible = message.from.isNotEmpty()
        toGroup.isVisible = message.to.isNotEmpty()
        ccGroup.isVisible = message.cc.isNotEmpty()
        bccGroup.isVisible = message.bcc.isNotEmpty()
    }

    private fun MessageViewHolder.handleHeaderClick(message: Message, isCollapsible: Boolean) = with(threadAdapterState) {
        // Disable ripple animation of `messageHeader` if `isCollapsible` is false
        binding.messageHeader.isEnabled = isCollapsible
        if (isCollapsible) {
            binding.messageHeader.setOnClickListener {
                if (isExpandedMap[message.uid] == true) {
                    isExpandedMap[message.uid] = false
                    onExpandOrCollapseMessage(message, shouldTrack = true)
                } else {
                    if (message.isDraft) {
                        threadAdapterCallbacks?.onDraftClicked?.invoke(message)
                    } else {
                        isExpandedMap[message.uid] = true
                        onExpandOrCollapseMessage(message, shouldTrack = true)
                    }
                }
            }
        } else {
            binding.messageHeader.setOnClickListener(null)
        }
    }

    private fun ItemMessageBinding.handleExpandDetailsClick(message: Message) {
        recipientOverlayedButton.setOnClickListener {
            message.detailsAreExpanded = !message.detailsAreExpanded
            val isExpanded = message.detailsAreExpanded
            recipientChevron.toggleChevron(!isExpanded)
            messageDetails.isVisible = isExpanded
            trackMessageEvent(MatomoName.OpenDetails, isExpanded)
        }
    }

    private fun MessageViewHolder.bindRecipientDetails(message: Message, messageDate: Date) = with(binding) {

        fromAdapter.updateList(message.from.toList(), message.bimi)
        toAdapter.updateList(message.to.toList())

        val ccIsNotEmpty = message.cc.isNotEmpty()
        ccGroup.isVisible = ccIsNotEmpty
        if (ccIsNotEmpty) ccAdapter.updateList(message.cc.toList())

        val bccIsNotEmpty = message.bcc.isNotEmpty()
        bccGroup.isVisible = bccIsNotEmpty
        if (bccIsNotEmpty) bccAdapter.updateList(message.bcc.toList())

        detailedMessageDate.text = context.fullDateWithYear(messageDate)
    }

    private fun MessageViewHolder.bindAlerts(messageUi: MessageUi) = with(binding) {
        val message = messageUi.message
        if (message.isEncrypted) {
            bindEncryption(message)
        } else {
            encryptionAlert.isGone = true
        }

        if (message.isScheduledDraft) {
            bindScheduled(message)
        } else {
            scheduleSendIcon.isGone = true
            scheduleAlert.isGone = true
        }

        distantImagesAlert.onAction1 {
            bodyWebViewClient.unblockDistantResources()
            fullMessageWebViewClient.unblockDistantResources()

            manuallyAllowedMessagesUids.add(message.uid)

            reloadVisibleWebView()

            distantImagesAlert.isGone = true
            hideAlertGroupIfNoneDisplayed()
        }

        bindUnsubscribe(messageUi)
        bindSpam(message)

        hideAlertGroupIfNoneDisplayed() // Must be called after binding all the different alerts
    }

    private fun ItemMessageBinding.bindEncryption(message: Message) {
        encryptionAlert.apply {
            isVisible = true

            val isMe = message.from.all(Recipient::isMe)

            val recipientsNeedingPassword = message.allRecipients.filter { recipient -> recipient.hasExternalProvider }
            val passwordValidity = message.encryptionPasswordValidity?.toDate()
            val (description, actionRes) = if (isMe && recipientsNeedingPassword.isNotEmpty() && passwordValidity != null) {
                getDisplayablePasswordValidity(context, passwordValidity) to R.string.encryptedButtonSeeConcernedRecipients
            } else {
                context.getString(R.string.encryptedMessageHeader) to null
            }

            setDescription(description)
            actionRes?.let {
                setAction1Text(context.getString(it))
                onAction1 { threadAdapterCallbacks?.onEncryptionSeeConcernedRecipients?.invoke(recipientsNeedingPassword) }
            } ?: setActionsVisibility(isVisible = false)
        }
    }

    private fun getDisplayablePasswordValidity(context: Context, passwordValidity: Date): String {
        val displayableDate = passwordValidity.formatWithLocal(formatData = FormatData.DATE, formatStyle = FormatStyle.SHORT)
        return context.getString(R.string.encryptedMessageHeaderPasswordExpiryDate, displayableDate)
    }

    private fun ItemMessageBinding.bindScheduled(message: Message) {
        scheduleAlert.setDescription(
            context.getString(
                R.string.scheduledEmailHeader,
                message.displayDate.toDate().format(FORMAT_DATE_DAY_FULL_MONTH_YEAR_WITH_TIME),
            ),
        )

        scheduleSendIcon.isVisible = true
        alertsGroup.isVisible = true
        scheduleAlert.isVisible = true

        message.draftResource?.let { draftResource ->
            scheduleAlert.onAction1 {
                trackScheduleSendEvent(MatomoName.ModifySnooze)
                threadAdapterCallbacks?.onRescheduleClicked?.invoke(
                    draftResource,
                    message.displayDate.takeIf { message.isScheduledDraft }?.epochSeconds?.times(1_000),
                )
            }
        }

        scheduleAlert.onAction2 {
            trackScheduleSendEvent(MatomoName.CancelSnooze)
            threadAdapterCallbacks?.onModifyScheduledClicked?.invoke(message)
        }
    }

    private fun ItemMessageBinding.bindUnsubscribe(messageUi: MessageUi) {
        when (messageUi.unsubscribeState) {
            is MessageUi.UnsubscribeState.CanUnsubscribe -> {
                unsubscribeAlert.isVisible = true
                unsubscribeAlert.hideAction1Progress(R.string.unsubscribeButtonTitle)
                unsubscribeAlert.onAction1 { threadAdapterCallbacks?.unsubscribeClicked?.invoke(messageUi.message) }
            }
            is MessageUi.UnsubscribeState.InProgress -> unsubscribeAlert.showAction1Progress()
            is MessageUi.UnsubscribeState.Completed, null -> {
                unsubscribeAlert.isVisible = false
            }
        }
    }

    //region Spam
    private enum class SpamAction {
        MoveToSpam, EnableFilter, Unblock, None,
    }

    private data class SpamData(val spamAction: SpamAction, val description: String = "", val action: String = "")

    private fun ItemMessageBinding.bindSpam(message: Message) {
        val firstExpeditor = message.from.firstOrNull()
        val spamAction = getSpamBannerAction(message, firstExpeditor)
        val spamData = context.getSpamBannerData(spamAction = spamAction, emailToUnblock = firstExpeditor?.email)

        if (spamData.spamAction == SpamAction.None) {
            spamAlert.isVisible = false
        } else {
            spamAlert.isVisible = true
            spamAlert.setDescription(spamData.description)
            spamAlert.setAction1Text(spamData.action)
            spamAlert.onAction1 { spamActionButton(spamData, message, firstExpeditor!!) }
        }
    }

    private fun getSpamBannerAction(message: Message, firstExpeditor: Recipient?): SpamAction {

        fun shouldIgnoreForSpam(isMessageSpam: Boolean, isExpeditorAuthorized: Boolean): Boolean {
            return isMessageSpam && !message.isInSpamFolder() && isSpamFilterActivated() && isExpeditorAuthorized
        }

        fun shouldHideSpamBanner(isMessageSpam: Boolean, isExpeditorAuthorized: Boolean): Boolean {
            return firstExpeditor == null || message.from.size > 1 || shouldIgnoreForSpam(isMessageSpam, isExpeditorAuthorized)
        }

        fun Recipient.getExpeditorIn(restrictedSenders: List<SenderDetails>?): String? {
            return restrictedSenders?.firstOrNull { email == it.email }?.email
        }

        val isMessageSpam = message.headers?.isSpam == true
        val isExpeditorBlocked = firstExpeditor?.getExpeditorIn(senderRestrictions()?.blockedSenders) != null
        val isExpeditorAuthorized = firstExpeditor?.getExpeditorIn(senderRestrictions()?.authorizedSenders) != null

        return when {
            shouldHideSpamBanner(isMessageSpam, isExpeditorAuthorized) -> {
                SpamAction.None
            }
            isMessageSpam && !message.isInSpamFolder() && isSpamFilterActivated() -> {
                SpamAction.MoveToSpam
            }
            isMessageSpam && !message.isInSpamFolder() && !isSpamFilterActivated() && !isExpeditorAuthorized -> {
                SpamAction.EnableFilter
            }
            !isMessageSpam && message.isInSpamFolder() && isExpeditorBlocked -> {
                SpamAction.Unblock
            }
            else -> {
                SpamAction.None
            }
        }
    }

    private fun Context.getSpamBannerData(spamAction: SpamAction, emailToUnblock: String? = null): SpamData {
        return when (spamAction) {
            SpamAction.MoveToSpam -> {
                SpamData(
                    spamAction = spamAction,
                    description = getString(R.string.messageIsSpamShouldMoveToSpam),
                    action = getString(R.string.moveInSpamButton),
                )
            }
            SpamAction.EnableFilter -> {
                SpamData(
                    spamAction = spamAction,
                    description = getString(R.string.messageIsSpamShouldActivateFilter),
                    action = getString(R.string.enableFilterButton),
                )
            }
            SpamAction.Unblock -> {
                SpamData(
                    spamAction = spamAction,
                    description = getString(R.string.messageIsSpamBecauseSenderIsBlocked, emailToUnblock),
                    action = getString(R.string.unblockButton),
                )
            }
            SpamAction.None -> {
                SpamData(spamAction = spamAction)
            }
        }
    }

    private fun ItemMessageBinding.spamActionButton(spamData: SpamData, message: Message, firstExpeditor: Recipient) {
        when (spamData.spamAction) {
            SpamAction.MoveToSpam -> threadAdapterCallbacks?.moveMessageToSpam?.invoke(message.uid)
            SpamAction.EnableFilter -> threadAdapterCallbacks?.activateSpamFilter?.invoke()
            SpamAction.Unblock -> threadAdapterCallbacks?.unblockMail?.invoke(firstExpeditor.email)
            else -> Unit
        }
        spamAlert.isVisible = false
        hideAlertGroupIfNoneDisplayed()
    }
    //endregion

    private fun ItemMessageBinding.reloadVisibleWebView() {
        if (isQuoteCollapsed) bodyWebView.reload() else fullMessageWebView.reload()
    }

    private fun ItemMessageBinding.hideAlertGroupIfNoneDisplayed() {
        alertsGroup.isVisible = areOneOrMoreAlertsVisible()
    }

    private fun ItemMessageBinding.areOneOrMoreAlertsVisible() = alerts.children.any { it.isVisible }

    @SuppressLint("SetTextI18n")
    private fun MessageViewHolder.bindAttachments(message: Message) = with(binding) {

        if (!message.hasAttachable) {
            attachmentLayout.root.isVisible = false
            return@with
        }

        val attachments = message.attachments + message.swissTransferFiles
        val attachmentString = computeAttachmentString(context, message)
        val fileSize = formatAttachmentFileSize(attachments)
        val downloadAllString = context.resources.getString(R.string.buttonDownloadAll)
        val totalAttachmentsSize = SpannableString("$attachmentString ($fileSize). $downloadAllString").apply {
            setSpan(
                ForegroundColorSpan(context.getColor(R.color.primary_color_disabled)),
                length - downloadAllString.length,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }

        attachmentAdapter.submitList(attachments)

        attachmentLayout.attachmentsSizeText.text = totalAttachmentsSize
        attachmentLayout.attachmentsInfo.setOnClickListener { threadAdapterCallbacks?.onDownloadAllClicked?.invoke(message) }
        attachmentLayout.root.isVisible = true
    }

    private fun computeAttachmentString(context: Context, message: Message): String {
        val attachmentsCount = message.attachments.size
        val swissTransferFilesCount = message.swissTransferFiles.size

        return buildString {
            if (attachmentsCount > 0) {
                append(context.resources.getQuantityString(R.plurals.attachmentQuantity, attachmentsCount, attachmentsCount))

                if (swissTransferFilesCount > 0) append(" ${context.resources.getString(R.string.linkingWord)} ")
            }

            if (swissTransferFilesCount > 0) {
                append(
                    context.resources.getQuantityString(
                        R.plurals.fileQuantity,
                        swissTransferFilesCount,
                        swissTransferFilesCount,
                    ),
                )
            }
        }
    }

    private fun ItemMessageBinding.formatAttachmentFileSize(attachments: List<Attachable>): String {
        if (attachments.isEmpty()) return ""

        val totalAttachmentsFileSizeInBytes = attachments.sumOf { it.size }

        return context.formatShortFileSize(totalAttachmentsFileSizeInBytes)
    }

    private fun MessageViewHolder.bindContent(message: Message) {
        binding.messageLoader.isVisible = message.splitBody == null
        binding.attachmentLayout.attachmentsInfo.isVisible = message.isFullyDownloaded()
        message.splitBody?.let { splitBody -> bindBody(message, hasQuote = splitBody.quote != null) }
    }

    private fun MessageViewHolder.bindBody(message: Message, hasQuote: Boolean) = with(binding) {

        bodyWebView.setupLinkContextualMenu { data, type ->
            threadAdapterCallbacks?.promptLink?.invoke(data, type)
        }
        fullMessageWebView.setupLinkContextualMenu { data, type ->
            threadAdapterCallbacks?.promptLink?.invoke(data, type)
        }

        setQuoteInitialCollapsedState(hasQuote)
        quoteButton.setOnClickListener { toggleWebViews(message) }
        quoteButtonFrameLayout.isVisible = hasQuote

        initWebViewClientIfNeeded(
            message,
            threadAdapterCallbacks?.navigateToNewMessageActivity,
            onPageFinished = { onExpandedMessageLoaded(message.uid) },
            onWebViewFinishedLoading = { threadAdapterCallbacks?.onBodyWebViewFinishedLoading?.invoke() },
        )

        // If the view holder got recreated while the fragment is not destroyed, keep the user's choice effective
        if (isMessageUidManuallyAllowed(message.uid)) {
            bodyWebViewClient.unblockDistantResources()
            fullMessageWebViewClient.unblockDistantResources()
        }
    }

    private fun WebView.setupLinkContextualMenu(onClicked: (String, ContextMenuType) -> Unit) {
        setOnLongClickListener {
            val result = hitTestResult

            when (result.type) {
                HitTestResult.PHONE_TYPE,
                HitTestResult.EMAIL_TYPE,
                HitTestResult.GEO_TYPE,
                HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                HitTestResult.SRC_ANCHOR_TYPE -> {
                    getDataFromResult(result)?.let { data -> onClicked(data, contextMenuTypeForHitTestResultType[result.type]!!) }
                    true
                }
                else -> false
            }
        }
    }

    private fun WebView.getDataFromResult(hitTestResult: HitTestResult): String? {
        return when (hitTestResult.type) {
            HitTestResult.PHONE_TYPE,
            HitTestResult.EMAIL_TYPE,
            HitTestResult.SRC_ANCHOR_TYPE -> hitTestResult.extra
            HitTestResult.GEO_TYPE -> WebView.SCHEME_GEO + hitTestResult.extra
            HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val message = handler.obtainMessage()
                requestFocusNodeHref(message)
                message.data.getString("url")
            }
            else -> null
        }
    }

    // Automatically expand quotes of single message threads
    private fun ItemMessageBinding.setQuoteInitialCollapsedState(hasQuote: Boolean) {
        val isSingleMessageThread = itemCount == 1
        val shouldBeExpanded = isSingleMessageThread && hasQuote && !isForPrinting

        isQuoteCollapsed = !shouldBeExpanded
    }

    private fun onExpandedMessageLoaded(messageUid: String) {
        if (hasNotScrolledYet) {
            currentSetOfLoadedExpandedMessagesUids.add(messageUid)
            if (currentSetOfLoadedExpandedMessagesUids.containsAll(threadAdapterState.isExpandedMap.keys)) {
                hasNotScrolledYet = false
                threadAdapterCallbacks?.onAllExpandedMessagesLoaded?.invoke()
            }
        }
    }

    private fun MessageViewHolder.bindEmojiReactions(messageUi: MessageUi) = with(binding.emojiReactions) {
        bindEmojiReactions(messageUi)
        setOnAddReactionClickListener { threadAdapterCallbacks?.onAddReaction?.invoke(messageUi.message) }
        setOnEmojiClickListener { emoji ->
            threadAdapterCallbacks?.onAddEmoji?.invoke(emoji, messageUi.message.uid)
        }
        setOnLongPressListener { emoji ->
            threadAdapterCallbacks?.showEmojiDetails?.invoke(messageUi.message.uid, emoji)
        }
    }

    private fun MessageViewHolder.onExpandOrCollapseMessage(message: Message, shouldTrack: Boolean) = with(binding) {
        val isExpanded = threadAdapterState.isExpandedMap[message.uid] == true

        if (shouldTrack) trackMessageEvent(MatomoName.OpenMessage, isExpanded)

        setHeaderState(message, isExpanded)
        content.isVisible = isExpanded
        if (isExpanded) loadContentAndQuote(message)
    }

    private fun ItemMessageBinding.setHeaderState(message: Message, isExpanded: Boolean) {
        deleteDraftButton.apply {
            isVisible = message.isDraft
            setOnClickListener { threadAdapterCallbacks?.onDeleteDraftClicked?.invoke(message) }
        }
        replyButton.apply {
            isVisible = isExpanded && message.isScheduledDraft.not()
            setOnClickListener { threadAdapterCallbacks?.onReplyClicked?.invoke(message) }
        }
        menuButton.apply {
            isVisible = isExpanded && message.isScheduledDraft.not()
            setOnClickListener { threadAdapterCallbacks?.onMenuClicked?.invoke(message) }
        }

        recipient.text = if (isExpanded) getAllRecipientsFormatted(message) else context.formatSubject(message.subject)
        recipientChevron.isVisible = isExpanded
        recipientOverlayedButton.isVisible = isExpanded
    }

    private fun ItemMessageBinding.getAllRecipientsFormatted(message: Message): String {
        return message.allRecipients.joinToString { it.displayedName(context) }
    }

    fun isMessageUidManuallyAllowed(messageUid: String) = manuallyAllowedMessagesUids.contains(messageUid)

    fun toggleLightMode(message: Message) {
        val index = items.indexOfFirstOrNull { it is MessageUi && it.message == message } ?: return
        notifyItemChanged(index, NotifyType.ToggleLightMode)
    }

    fun reRenderMails() {
        notifyItemRangeChanged(0, itemCount, NotifyType.ReRender)
    }

    fun updateFailedMessages(uids: List<String>) {
        uids.forEach { uid ->
            val index = items.indexOfFirst { it is MessageUi && it.message.uid == uid }
            notifyItemChanged(index, NotifyType.FailedMessage)
        }
    }

    fun resetCallbacks() {
        threadAdapterCallbacks = null
    }

    fun undoUserAttendanceClick(message: Message) {
        val indexOfMessage = items.indexOfFirst { it is MessageUi && it.message.uid == message.uid }.takeIf { it >= 0 }
        indexOfMessage?.let { notifyItemChanged(it, NotifyType.OnlyRebindCalendarAttendance) }
    }

    fun messagesCollapseStateChange(isCollapsible: Boolean) {
        notifyItemRangeChanged(0, itemCount, NotifyType.MessagesCollapseStateChanged(isCollapsible))
    }

    // Only public because it's accessed inside of a test file
    sealed interface NotifyType {
        data object ToggleLightMode : NotifyType
        data object ReRender : NotifyType
        data object FailedMessage : NotifyType
        data object OnlyRebindCalendarAttendance : NotifyType
        data object OnlyRebindEmojiReactions : NotifyType
        data object UnsubscribeRebind : NotifyType
        @JvmInline
        value class MessagesCollapseStateChanged(val isCollapsible: Boolean) : NotifyType
    }

    enum class ContextMenuType {
        LINK,
        EMAIL,
        PHONE,
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is MessageUi -> newItem is MessageUi && newItem.message.uid == oldItem.message.uid
                is SuperCollapsedBlock -> newItem is SuperCollapsedBlock
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is MessageUi -> {
                    newItem is MessageUi && MessageDiffAspect.entries.all { it.areTheSame(oldItem, newItem) }
                }
                is SuperCollapsedBlock -> {
                    newItem is SuperCollapsedBlock &&
                            newItem.messagesUids.count() == oldItem.messagesUids.count()
                }
                else -> false
            }
        }

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {

            if (oldItem !is MessageUi || newItem !is MessageUi) return null

            // TODO: Handle the case where there are multiple aspects that changed at once
            return when {
                // null means "bind the whole item again"
                MessageDiffAspect.AnythingElse.areDifferent(oldItem, newItem) -> null
                MessageDiffAspect.EmojiReactions.areDifferent(oldItem, newItem) -> NotifyType.OnlyRebindEmojiReactions
                MessageDiffAspect.Unsubscribe.areDifferent(oldItem, newItem) -> NotifyType.UnsubscribeRebind
                else -> getCalendarEventPayloadOrNull(oldItem.message, newItem.message)
            }
        }

        private fun getCalendarEventPayloadOrNull(oldItem: Message, newItem: Message): NotifyType? {
            val oldCalendarEventResponse = oldItem.latestCalendarEventResponse
            val newCalendarEventResponse = newItem.latestCalendarEventResponse

            return when {
                oldCalendarEventResponse == null && newCalendarEventResponse == null -> null
                oldCalendarEventResponse == null || newCalendarEventResponse == null -> null
                MessageDiffAspect.Calendar.Attendees.areDifferent(
                    oldCalendarEventResponse,
                    newCalendarEventResponse,
                ) -> NotifyType.OnlyRebindCalendarAttendance
                else -> null
            }
        }

        companion object {
            fun Map<String, Reaction>.containsTheSameEmojiValuesAs(other: Map<String, Reaction>): Boolean {
                if (this.size != other.size) return false

                for ((emoji, state) in this) {
                    if (other.containsKey(emoji).not()) return false
                    if (state != other[emoji]) return false
                }

                return true
            }

            sealed class DiffAspect<T>(private val isTheSameAs: T.(T) -> Boolean) {
                fun areTheSame(message: T, other: T) = message.isTheSameAs(other)
                fun areDifferent(message: T, other: T) = areTheSame(message, other).not()
            }

            object MessageDiffAspect {
                val entries: List<DiffAspect<MessageUi>> get() = listOf(EmojiReactions, Calendar, AnythingElse, Unsubscribe)

                data object EmojiReactions : DiffAspect<MessageUi>({
                    emojiReactionsState.containsTheSameEmojiValuesAs(it.emojiReactionsState)
                })

                data object Calendar : DiffAspect<MessageUi>({
                    val calendarEventResponse = message.latestCalendarEventResponse
                    val otherCalendarEventResponse = it.message.latestCalendarEventResponse

                    when {
                        calendarEventResponse == null && otherCalendarEventResponse == null -> true
                        calendarEventResponse == null || otherCalendarEventResponse == null -> false
                        else -> Attendees.areTheSame(calendarEventResponse, otherCalendarEventResponse)
                                && AnythingElse.areTheSame(calendarEventResponse, otherCalendarEventResponse)
                    }
                }) {
                    data object Attendees : DiffAspect<CalendarEventResponse>({ attendeesAreTheSame(it) })
                    data object AnythingElse : DiffAspect<CalendarEventResponse>({ everythingButAttendeesIsTheSame(it) })
                }

                data object Unsubscribe : DiffAspect<MessageUi>({ unsubscribeState == it.unsubscribeState })

                data object AnythingElse : DiffAspect<MessageUi>({ oldMessage ->
                    // Checks for any aspect of the message that could change and trigger a whole bind of the item again. Here we
                    // check for anything that doesn't need to handle bind with precision using a custom payload
                    message.body?.value == oldMessage.message.body?.value &&
                            message.splitBody == oldMessage.message.splitBody &&
                            message.shouldHideDivider == oldMessage.message.shouldHideDivider
                })
            }
        }
    }

    data class ThreadAdapterCallbacks(
        var onBodyWebViewFinishedLoading: (() -> Unit)? = null,
        var onContactClicked: ((contact: Recipient, bimi: Bimi?) -> Unit)? = null,
        var onDeleteDraftClicked: ((message: Message) -> Unit)? = null,
        var onDraftClicked: ((message: Message) -> Unit)? = null,
        var onAttachmentClicked: ((attachment: Attachable) -> Unit)? = null,
        var onAttachmentOptionsClicked: ((attachment: Attachable) -> Unit)? = null,
        var onDownloadAllClicked: ((message: Message) -> Unit)? = null,
        var onReplyClicked: ((Message) -> Unit)? = null,
        var onMenuClicked: ((Message) -> Unit)? = null,
        var onAllExpandedMessagesLoaded: (() -> Unit)? = null,
        var onSuperCollapsedBlockClicked: (() -> Unit)? = null,
        var navigateToNewMessageActivity: ((Uri) -> Unit)? = null,
        var navigateToAttendeeBottomSheet: ((List<Attendee>) -> Unit)? = null,
        var navigateToDownloadProgressDialog: ((Attachment, AttachmentIntentType) -> Unit)? = null,
        var unsubscribeClicked: ((Message) -> Unit)? = null,
        var moveMessageToSpam: ((String) -> Unit)? = null,
        var activateSpamFilter: (() -> Unit)? = null,
        var unblockMail: ((String) -> Unit)? = null,
        var replyToCalendarEvent: ((AttendanceState, Message) -> Unit)? = null,
        var promptLink: ((String, ContextMenuType) -> Unit)? = null,
        var onRescheduleClicked: ((String, Long?) -> Unit)? = null,
        var onModifyScheduledClicked: ((Message) -> Unit)? = null,
        var onEncryptionSeeConcernedRecipients: ((List<Recipient>) -> Unit)? = null,
        var onAddReaction: ((Message) -> Unit)? = null,
        var onAddEmoji: ((emoji: String, messageUid: String) -> Unit)? = null,
        var showEmojiDetails: ((messageUid: String, emoji: String) -> Unit)? = null,
    )

    private enum class DisplayType(val layout: Int) {
        MAIL(R.layout.item_message),
        SUPER_COLLAPSED_BLOCK(R.layout.item_super_collapsed_block),
    }

    data class SuperCollapsedBlock(
        var shouldBeDisplayed: Boolean = true,
        val messagesUids: MutableSet<String> = mutableSetOf(),
    ) {
        fun isFirstTime() = shouldBeDisplayed && messagesUids.isEmpty()
    }

    abstract class ThreadAdapterViewHolder(open val binding: ViewBinding) : ViewHolder(binding.root)

    private class SuperCollapsedBlockViewHolder(
        override val binding: ItemSuperCollapsedBlockBinding,
    ) : ThreadAdapterViewHolder(binding)

    private class MessageViewHolder(
        override val binding: ItemMessageBinding,
        private val shouldLoadDistantResources: Boolean,
        onContactClicked: ((contact: Recipient, bimi: Bimi?) -> Unit)?,
        onAttachmentClicked: ((attachment: Attachable) -> Unit)?,
        onAttachmentOptionsClicked: ((attachment: Attachable) -> Unit)?,
    ) : ThreadAdapterViewHolder(binding) {

        val fromAdapter = DetailedRecipientAdapter(onContactClicked)
        val toAdapter = DetailedRecipientAdapter(onContactClicked)
        val ccAdapter = DetailedRecipientAdapter(onContactClicked)
        val bccAdapter = DetailedRecipientAdapter(onContactClicked)
        val attachmentAdapter = AttachmentAdapter(
            onAttachmentClicked = { onAttachmentClicked?.invoke(it) },
            onAttachmentOptionsClicked = { onAttachmentOptionsClicked?.invoke(it) },
        )

        private var _bodyWebViewClient: MessageWebViewClient? = null
        private var _fullMessageWebViewClient: MessageWebViewClient? = null
        val bodyWebViewClient get() = _bodyWebViewClient!!
        val fullMessageWebViewClient get() = _fullMessageWebViewClient!!

        init {
            with(binding) {
                fromRecyclerView.adapter = fromAdapter
                toRecyclerView.adapter = toAdapter
                ccRecyclerView.adapter = ccAdapter
                bccRecyclerView.adapter = bccAdapter
                attachmentLayout.attachmentsRecyclerView.adapter = attachmentAdapter
            }
        }

        fun initWebViewClientIfNeeded(
            message: Message,
            navigateToNewMessageActivity: ((Uri) -> Unit)?,
            onPageFinished: () -> Unit,
            onWebViewFinishedLoading: () -> Unit,
        ) {

            fun promptUserForDistantImages() {
                binding.promptUserForDistantImages()
            }

            if (_bodyWebViewClient == null) {
                _bodyWebViewClient = binding.bodyWebView.initWebViewClientAndBridge(
                    attachments = message.attachments,
                    messageUid = message.uid,
                    shouldLoadDistantResources = shouldLoadDistantResources,
                    onBlockedResourcesDetected = ::promptUserForDistantImages,
                    navigateToNewMessageActivity = navigateToNewMessageActivity,
                    onPageFinished = onPageFinished,
                    onWebViewFinishedLoading = onWebViewFinishedLoading,
                )
                _fullMessageWebViewClient = binding.fullMessageWebView.initWebViewClientAndBridge(
                    attachments = message.attachments,
                    messageUid = message.uid,
                    shouldLoadDistantResources = shouldLoadDistantResources,
                    onBlockedResourcesDetected = ::promptUserForDistantImages,
                    navigateToNewMessageActivity = navigateToNewMessageActivity,
                    onWebViewFinishedLoading = onWebViewFinishedLoading,
                )
            }
        }

        private fun ItemMessageBinding.promptUserForDistantImages() {
            if (distantImagesAlert.isGone) {
                CoroutineScope(Dispatchers.Main).launch {
                    alertsGroup.isVisible = true
                    distantImagesAlert.isVisible = true
                }
            }
        }
    }

    companion object {
        private val contextMenuTypeForHitTestResultType = mapOf(
            HitTestResult.PHONE_TYPE to ContextMenuType.PHONE,
            HitTestResult.EMAIL_TYPE to ContextMenuType.EMAIL,
            HitTestResult.GEO_TYPE to ContextMenuType.LINK,
            HitTestResult.SRC_ANCHOR_TYPE to ContextMenuType.LINK,
            HitTestResult.SRC_IMAGE_ANCHOR_TYPE to ContextMenuType.LINK,
        )
    }
}
