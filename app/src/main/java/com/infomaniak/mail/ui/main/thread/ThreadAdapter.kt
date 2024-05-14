/*
 * Infomaniak Mail - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import android.net.Uri
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.lib.core.utils.FormatterFileSize.formatShortFileSize
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.mail.MatomoMail.trackMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.databinding.ItemSuperCollapsedBlockBinding
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadAdapterViewHolder
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.MailDateFormatUtils.mailFormattedDate
import com.infomaniak.mail.utils.MailDateFormatUtils.mostDetailedDate
import com.infomaniak.mail.utils.SharedUtils.Companion.createHtmlForPlainText
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.Utils.TEXT_HTML
import com.infomaniak.mail.utils.Utils.TEXT_PLAIN
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupThreadWebViewSettings
import com.infomaniak.mail.utils.WebViewUtils.Companion.toggleWebViewTheme
import com.infomaniak.mail.utils.extensions.*
import com.infomaniak.mail.utils.extensions.AttachmentExtensions.AttachmentIntentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import com.google.android.material.R as RMaterial

class ThreadAdapter(
    private val shouldLoadDistantResources: Boolean,
    private val isForPrinting: Boolean = false,
    private val threadAdapterState: ThreadAdapterState,
    private var threadAdapterCallbacks: ThreadAdapterCallbacks? = null,
) : ListAdapter<Any, ThreadAdapterViewHolder>(MessageDiffCallback()) {

    inline val items: MutableList<Any> get() = currentList

    //region Auto-scroll at Thread opening
    private val currentSetOfLoadedExpandedMessagesUids = mutableSetOf<String>()
    private var hasNotScrolledYet = true
    //endregion

    private val manuallyAllowedMessageUids = mutableSetOf<String>()

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

    override fun getItemCount(): Int = runCatchingRealm { items.count() }.getOrDefault(0)

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return when (items[position]) {
            is Message -> DisplayType.MAIL.layout
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
        if (item is Message && holder is MessageViewHolder) with(holder.binding) {
            when (payload) {
                NotifyType.TOGGLE_LIGHT_MODE -> holder.handleToggleLightModePayload(item.uid)
                NotifyType.RE_RENDER -> reloadVisibleWebView()
                NotifyType.FAILED_MESSAGE -> handleFailedMessagePayload(item.uid)
                NotifyType.ONLY_REBIND_CALENDAR_ATTENDANCE -> handleCalendarAttendancePayload(item)
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

    override fun onBindViewHolder(holder: ThreadAdapterViewHolder, position: Int) {

        val item = items[position]

        holder.binding.root.tag = if (item is SuperCollapsedBlock || (item is Message && item.shouldHideDivider)) {
            IGNORE_DIVIDER_TAG
        } else {
            null
        }

        if (item is Message) {
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

    private fun MessageViewHolder.bindMail(message: Message, position: Int) {

        initMapForNewMessage(message, position)

        bindHeader(message)
        bindAlerts(message.uid)
        bindCalendarEvent(message)
        bindAttachment(message)
        bindContent(message)

        onExpandOrCollapseMessage(message, shouldTrack = false)
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
                    shouldStartExpanded = threadAdapterState.isCalendarEventExpandedMap[message.uid] ?: false,
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
        val isThemeTheSame = threadAdapterState.isThemeTheSameMap[messageUid]!!
        bodyWebView.toggleWebViewTheme(isThemeTheSame)
        fullMessageWebView.toggleWebViewTheme(isThemeTheSame)
        toggleFrameLayoutsTheme(isThemeTheSame)
    }

    private fun MessageViewHolder.loadContentAndQuote(message: Message) {
        val body = message.body
        val splitBody = message.splitBody

        if (body != null && splitBody != null) {
            if (binding.isQuoteCollapsed) {
                val completeBody = MessageBodyUtils.mergeSplitBodyAndSubBodies(splitBody.content, body.subBodies, message.uid)
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
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, threadAdapterState.isThemeTheSameMap[uid]!!)
        }

        var styledBody = if (type == TEXT_PLAIN) createHtmlForPlainText(bodyWebView) else bodyWebView
        styledBody = processMailDisplay(styledBody, uid, isForPrinting)

        settings.setupThreadWebViewSettings()
        setupZoomListeners()

        loadDataWithBaseURL("", styledBody, TEXT_HTML, Utils.UTF_8, "")
    }

    private fun WebView.processMailDisplay(styledBody: String, uid: String, isForPrinting: Boolean): String {
        val isDisplayedInDark =
            context.isNightModeEnabled() && threadAdapterState.isThemeTheSameMap[uid] == true && !isForPrinting
        return if (isForPrinting) {
            webViewUtils.processHtmlForPrint(styledBody, HtmlFormatter.PrintData(context, items.first() as Message))
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
            quoteButton.setTextColor(context.getAttributeColor(RMaterial.attr.colorPrimary))
        } else {
            val color = context.getColor(R.color.background_color_light)
            webViewsFrameLayout.setBackgroundColor(color)
            quoteButtonFrameLayout.setBackgroundColor(color)
            quoteButton.setTextColor(context.getAttributeColor(RMaterial.attr.colorPrimaryInverse))
        }
    }

    private fun MessageViewHolder.bindHeader(message: Message) = with(binding) {
        val messageDate = message.date.toDate()

        if (message.isDraft) {
            userAvatar.loadAvatar(AccountUtils.currentUser!!)
            expeditorName.apply {
                text = context.getString(R.string.messageIsDraftOption)
                setTextAppearance(R.style.BodyMedium_Error)
            }
            shortMessageDate.text = ""
        } else {
            val firstSender = message.sender
            userAvatar.loadAvatar(firstSender)
            expeditorName.apply {
                text = firstSender?.let { context.getPrettyNameAndEmail(it).first }
                    ?: run { context.getString(R.string.unknownRecipientTitle) }
                setTextAppearance(R.style.BodyMedium)
            }
            shortMessageDate.text = mailFormattedDate(context, messageDate)
        }

        val listener: OnClickListener? = message.sender?.let { recipient ->
            OnClickListener {
                context.trackMessageEvent("selectAvatar")
                threadAdapterCallbacks?.onContactClicked?.invoke(recipient)
            }
        }

        userAvatar.setOnClickListener(listener)

        setDetailedFieldsVisibility(message)

        handleHeaderClick(message)
        handleExpandDetailsClick(message)
        bindRecipientDetails(message, messageDate)
    }

    private fun ItemMessageBinding.setDetailedFieldsVisibility(message: Message) {
        fromGroup.isVisible = message.from.isNotEmpty()
        toGroup.isVisible = message.to.isNotEmpty()
        ccGroup.isVisible = message.cc.isNotEmpty()
        bccGroup.isVisible = message.bcc.isNotEmpty()
    }

    private fun MessageViewHolder.handleHeaderClick(message: Message) = with(threadAdapterState) {
        binding.messageHeader.setOnClickListener {
            if (isExpandedMap[message.uid] == true) {
                isExpandedMap[message.uid] = false
                onExpandOrCollapseMessage(message)
            } else {
                if (message.isDraft) {
                    threadAdapterCallbacks?.onDraftClicked?.invoke(message)
                } else {
                    isExpandedMap[message.uid] = true
                    onExpandOrCollapseMessage(message)
                }
            }
        }
    }

    private fun ItemMessageBinding.handleExpandDetailsClick(message: Message) {
        recipientOverlayedButton.setOnClickListener {
            message.detailsAreExpanded = !message.detailsAreExpanded
            val isExpanded = message.detailsAreExpanded
            recipientChevron.toggleChevron(!isExpanded)
            messageDetails.isVisible = isExpanded
            context.trackMessageEvent("openDetails", isExpanded)
        }
    }

    private fun MessageViewHolder.bindRecipientDetails(message: Message, messageDate: Date) = with(binding) {

        fromAdapter.updateList(message.from.toList())
        toAdapter.updateList(message.to.toList())

        val ccIsNotEmpty = message.cc.isNotEmpty()
        ccGroup.isVisible = ccIsNotEmpty
        if (ccIsNotEmpty) ccAdapter.updateList(message.cc.toList())

        val bccIsNotEmpty = message.bcc.isNotEmpty()
        bccGroup.isVisible = bccIsNotEmpty
        if (bccIsNotEmpty) bccAdapter.updateList(message.bcc.toList())

        detailedMessageDate.text = mostDetailedDate(context, messageDate)
    }

    private fun MessageViewHolder.bindAlerts(messageUid: String) = with(binding) {
        distantImagesAlert.onAction1 {
            bodyWebViewClient.unblockDistantResources()
            fullMessageWebViewClient.unblockDistantResources()

            manuallyAllowedMessageUids.add(messageUid)

            reloadVisibleWebView()

            distantImagesAlert.isGone = true
            hideAlertGroupIfNoneDisplayed()
        }
    }

    private fun ItemMessageBinding.reloadVisibleWebView() {
        if (isQuoteCollapsed) bodyWebView.reload() else fullMessageWebView.reload()
    }

    private fun ItemMessageBinding.hideAlertGroupIfNoneDisplayed() {
        alertsGroup.isVisible = areOneOrMoreAlertsVisible()
    }

    private fun ItemMessageBinding.areOneOrMoreAlertsVisible() = alerts.children.any { it.isVisible }

    @SuppressLint("SetTextI18n")
    private fun MessageViewHolder.bindAttachment(message: Message) = with(binding) {
        val attachments = message.attachments
        val fileSize = formatAttachmentFileSize(attachments)
        attachmentLayout.attachmentsSizeText.text = context.resources.getQuantityString(
            R.plurals.attachmentQuantity,
            attachments.size,
            attachments.size,
        ) + " ($fileSize)"
        attachmentAdapter.setAttachments(attachments)
        attachmentLayout.attachmentsDownloadAllButton.setOnClickListener {
            threadAdapterCallbacks?.onDownloadAllClicked?.invoke(message)
        }
        attachmentLayout.root.isVisible = message.attachments.isNotEmpty()
    }

    private fun ItemMessageBinding.formatAttachmentFileSize(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return ""

        val totalAttachmentsFileSizeInBytes: Long = attachments.map { attachment ->
            attachment.size
        }.reduce { accumulator: Long, size: Long -> accumulator + size }

        return context.formatShortFileSize(totalAttachmentsFileSizeInBytes)
    }

    private fun MessageViewHolder.bindContent(message: Message) {
        binding.messageLoader.isVisible = message.splitBody == null
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

    private fun MessageViewHolder.onExpandOrCollapseMessage(message: Message, shouldTrack: Boolean = true) = with(binding) {
        val isExpanded = threadAdapterState.isExpandedMap[message.uid]!!

        if (shouldTrack) context.trackMessageEvent("openMessage", isExpanded)

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
            isVisible = isExpanded
            setOnClickListener { threadAdapterCallbacks?.onReplyClicked?.invoke(message) }
        }
        menuButton.apply {
            isVisible = isExpanded
            setOnClickListener { threadAdapterCallbacks?.onMenuClicked?.invoke(message) }
        }

        recipient.text = if (isExpanded) getAllRecipientsFormatted(message) else context.formatSubject(message.subject)
        recipientChevron.isVisible = isExpanded
        recipientOverlayedButton.isVisible = isExpanded
    }

    private fun ItemMessageBinding.getAllRecipientsFormatted(message: Message): String = with(message) {
        return listOf(*to.toTypedArray(), *cc.toTypedArray(), *bcc.toTypedArray()).joinToString { it.displayedName(context) }
    }

    fun isMessageUidManuallyAllowed(messageUid: String) = manuallyAllowedMessageUids.contains(messageUid)

    fun toggleLightMode(message: Message) {
        val index = items.indexOf(message)
        notifyItemChanged(index, NotifyType.TOGGLE_LIGHT_MODE)
    }

    fun reRenderMails() {
        notifyItemRangeChanged(0, itemCount, NotifyType.RE_RENDER)
    }

    fun updateFailedMessages(uids: List<String>) {
        uids.forEach { uid ->
            val index = items.indexOfFirst { it is Message && it.uid == uid }
            notifyItemChanged(index, NotifyType.FAILED_MESSAGE)
        }
    }

    fun resetCallbacks() {
        threadAdapterCallbacks = null
    }

    fun undoUserAttendanceClick(message: Message) {
        val indexOfMessage = items.indexOfFirst { it is Message && it.uid == message.uid }.takeIf { it >= 0 }
        indexOfMessage?.let { notifyItemChanged(it, NotifyType.ONLY_REBIND_CALENDAR_ATTENDANCE) }
    }

    private enum class NotifyType {
        TOGGLE_LIGHT_MODE,
        RE_RENDER,
        FAILED_MESSAGE,
        ONLY_REBIND_CALENDAR_ATTENDANCE,
    }

    enum class ContextMenuType {
        LINK,
        EMAIL,
        PHONE,
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is Message -> newItem is Message && newItem.uid == oldItem.uid
                is SuperCollapsedBlock -> newItem is SuperCollapsedBlock
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when (oldItem) {
                is Message -> {
                    newItem is Message &&
                            areMessageContentsTheSameExceptCalendar(oldItem, newItem) &&
                            newItem.latestCalendarEventResponse == oldItem.latestCalendarEventResponse
                }
                is SuperCollapsedBlock -> {
                    newItem is SuperCollapsedBlock &&
                            newItem.messagesUids.count() == oldItem.messagesUids.count()
                }
                else -> false
            }
        }

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {

            if (oldItem !is Message || newItem !is Message) return null

            // If everything but Attendees is the same, then we know the only thing that could've changed is Attendees.
            return if (everythingButAttendeesIsTheSame(oldItem, newItem)) NotifyType.ONLY_REBIND_CALENDAR_ATTENDANCE else null
        }

        companion object {
            fun everythingButAttendeesIsTheSame(oldMessage: Message, newMessage: Message): Boolean {
                val newCalendarEventResponse = newMessage.latestCalendarEventResponse
                val oldCalendarEventResponse = oldMessage.latestCalendarEventResponse

                return (areMessageContentsTheSameExceptCalendar(oldMessage, newMessage) &&
                        !(newCalendarEventResponse == null && oldCalendarEventResponse == null)
                        && newCalendarEventResponse?.everythingButAttendeesIsTheSame(oldCalendarEventResponse) == true)
            }

            private fun areMessageContentsTheSameExceptCalendar(oldMessage: Message, newMessage: Message): Boolean {
                return newMessage.body?.value == oldMessage.body?.value &&
                        newMessage.splitBody == oldMessage.splitBody &&
                        newMessage.shouldHideDivider == oldMessage.shouldHideDivider
            }
        }
    }

    data class ThreadAdapterCallbacks(
        var onBodyWebViewFinishedLoading: (() -> Unit)? = null,
        var onContactClicked: ((contact: Recipient) -> Unit)? = null,
        var onDeleteDraftClicked: ((message: Message) -> Unit)? = null,
        var onDraftClicked: ((message: Message) -> Unit)? = null,
        var onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null,
        var onAttachmentOptionsClicked: ((attachment: Attachment) -> Unit)? = null,
        var onDownloadAllClicked: ((message: Message) -> Unit)? = null,
        var onReplyClicked: ((Message) -> Unit)? = null,
        var onMenuClicked: ((Message) -> Unit)? = null,
        var onAllExpandedMessagesLoaded: (() -> Unit)? = null,
        var onSuperCollapsedBlockClicked: (() -> Unit)? = null,
        var navigateToNewMessageActivity: ((Uri) -> Unit)? = null,
        var navigateToAttendeeBottomSheet: ((List<Attendee>) -> Unit)? = null,
        var navigateToDownloadProgressDialog: ((Attachment, AttachmentIntentType) -> Unit)? = null,
        var replyToCalendarEvent: ((AttendanceState, Message) -> Unit)? = null,
        var promptLink: ((String, ContextMenuType) -> Unit)? = null,
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
        onContactClicked: ((contact: Recipient) -> Unit)?,
        onAttachmentClicked: ((attachment: Attachment) -> Unit)?,
        onAttachmentOptionsClicked: ((attachment: Attachment) -> Unit)?,
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

        const val IGNORE_DIVIDER_TAG = "ignoreDividerTag"

        private val contextMenuTypeForHitTestResultType = mapOf(
            HitTestResult.PHONE_TYPE to ContextMenuType.PHONE,
            HitTestResult.EMAIL_TYPE to ContextMenuType.EMAIL,
            HitTestResult.GEO_TYPE to ContextMenuType.LINK,
            HitTestResult.SRC_ANCHOR_TYPE to ContextMenuType.LINK,
            HitTestResult.SRC_IMAGE_ANCHOR_TYPE to ContextMenuType.LINK,
        )
    }
}
