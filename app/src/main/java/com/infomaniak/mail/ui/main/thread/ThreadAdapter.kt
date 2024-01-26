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
import android.content.Context
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
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
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.lib.core.utils.*
import com.infomaniak.mail.MatomoMail.trackMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.calendar.Attendee
import com.infomaniak.mail.data.models.calendar.Attendee.AttendanceState
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.extensions.mailFormattedDate
import com.infomaniak.mail.extensions.mostDetailedDate
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadViewHolder
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.AttachmentIntentUtils.AttachmentIntentType
import com.infomaniak.mail.utils.AttachmentIntentUtils.createDownloadDialogNavArgs
import com.infomaniak.mail.utils.SharedUtils.Companion.createHtmlForPlainText
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.TEXT_HTML
import com.infomaniak.mail.utils.Utils.TEXT_PLAIN
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupThreadWebViewSettings
import com.infomaniak.mail.utils.WebViewUtils.Companion.toggleWebViewTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import com.google.android.material.R as RMaterial

class ThreadAdapter(
    private val shouldLoadDistantResources: Boolean,
    private val isForPrinting: Boolean = false,
    private val isCalendarEventExpandedMap: MutableMap<String, Boolean>,
    onBodyWebviewFinishedLoading: (() -> Unit)? = null,
    onContactClicked: ((contact: Recipient) -> Unit)? = null,
    onDeleteDraftClicked: ((message: Message) -> Unit)? = null,
    onDraftClicked: ((message: Message) -> Unit)? = null,
    onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null,
    onDownloadAllClicked: ((message: Message) -> Unit)? = null,
    onReplyClicked: ((Message) -> Unit)? = null,
    onMenuClicked: ((Message) -> Unit)? = null,
    onAllExpandedMessagesLoaded: (() -> Unit)? = null,
    navigateToNewMessageActivity: ((Uri) -> Unit)? = null,
    navigateToAttendeeBottomSheet: ((List<Attendee>) -> Unit)? = null,
    promptLink: ((String, ContextMenuType) -> Unit)? = null,
) : ListAdapter<Message, ThreadViewHolder>(MessageDiffCallback()) {

    inline val messages: MutableList<Message> get() = currentList

    var isExpandedMap = mutableMapOf<String, Boolean>()
    var initialSetOfExpandedMessagesUids = setOf<String>()
    private val currentSetOfLoadedExpandedMessagesUids = mutableSetOf<String>()
    private var hasNotScrolledYet = true

    private val manuallyAllowedMessageUids = mutableSetOf<String>()
    var isThemeTheSameMap = mutableMapOf<String, Boolean>()

    private var threadAdapterCallbacks: ThreadAdapterCallbacks? = null

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

    init {
        threadAdapterCallbacks = ThreadAdapterCallbacks(
            onBodyWebviewFinishedLoading,
            onContactClicked,
            onDeleteDraftClicked,
            onDraftClicked,
            onAttachmentClicked,
            onDownloadAllClicked,
            onReplyClicked,
            onMenuClicked,
            onAllExpandedMessagesLoaded,
            navigateToNewMessageActivity,
            navigateToAttendeeBottomSheet,
            navigateToDownloadProgressDialog,
            replyToCalendarEvent,
            promptLink,
        )
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun getItemCount(): Int = runCatchingRealm { messages.count() }.getOrDefault(0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        return ThreadViewHolder(
            ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            shouldLoadDistantResources,
            threadAdapterCallbacks?.onContactClicked,
            threadAdapterCallbacks?.onAttachmentClicked,
        )
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int, payloads: MutableList<Any>) = runCatchingRealm {
        with(holder.binding) {
            if (payloads.isEmpty()) {
                super.onBindViewHolder(holder, position, payloads)
                return
            }
            payloads.forEach { payload ->
                if (payload !is NotifyType) {
                    super.onBindViewHolder(holder, position, payloads)
                    return
                }

                val message = messages[position]

                when (payload) {
                    NotifyType.TOGGLE_LIGHT_MODE -> {
                        isThemeTheSameMap[message.uid] = !isThemeTheSameMap[message.uid]!!
                        holder.toggleContentAndQuoteTheme(message.uid)
                    }
                    NotifyType.TOGGLE_RECIPIENTS -> {
                        holder.openRecipients()
                    }
                    NotifyType.RE_RENDER -> reloadVisibleWebView()
                    NotifyType.FAILED_MESSAGE -> {
                        messageLoader.isGone = true
                        failedLoadingErrorMessage.isVisible = true
                        if (isExpandedMap[message.uid] == true) onExpandedMessageLoaded(message.uid)
                    }
                }
                NotifyType.ONLY_REBIND_CALENDAR_ATTENDANCE -> {
                    val attendees = message.latestCalendarEventResponse?.calendarEvent?.attendees ?: emptyList()
                    holder.binding.calendarEvent.onlyUpdateAttendance(attendees)
                }
            }
        }
    }.getOrDefault(Unit)

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) = with(holder) {
        val message = messages[position]

        initMapForNewMessage(message, position)

        bindHeader(message)
        bindAlerts(message.uid)
        bindCalendarEvent(message)
        bindAttachment(message)
        bindContent(message)

        onExpandOrCollapseMessage(message, shouldTrack = false)
    }

    private fun ThreadViewHolder.bindCalendarEvent(message: Message) {
        val attachment = message.calendarAttachment ?: return
        val calendarEvent = message.latestCalendarEventResponse?.calendarEvent

        binding.calendarEvent.apply {
            isVisible = calendarEvent != null

            calendarEvent?.let {
                val calendarEventResponse = message.latestCalendarEventResponse!!

                loadCalendarEvent(
                    calendarEvent = it,
                    isCanceled = calendarEventResponse.isCanceled,
                    shouldDisplayReplyOptions = calendarEventResponse.isReplyAuthorized(),
                    attachment = attachment,
                    hasAssociatedInfomaniakCalendarEvent = calendarEventResponse.hasAssociatedInfomaniakCalendarEvent(),
                    shouldStartExpanded = isCalendarEventExpandedMap[message.uid] ?: false,
                )
            }

            initCallback(
                navigateToAttendeesBottomSheet = { attendees ->
                    threadAdapterCallbacks?.navigateToAttendeeBottomSheet?.invoke(attendees)
                },
                navigateToDownloadProgressDialog = {
                    threadAdapterCallbacks?.navigateToDownloadProgressDialog?.invoke(
                        R.id.downloadAttachmentProgressDialog,
                        attachment.createDownloadDialogNavArgs(AttachmentIntentType.OPEN_WITH),
                    )
                },
                replyToCalendarEvent = { attendanceState ->
                    threadAdapterCallbacks?.replyToCalendarEvent?.invoke(attendanceState, message)
                },
                onAttendeesButtonClicked = { isExpanded -> isCalendarEventExpandedMap[message.uid] = isExpanded },
            )
        }
    }

    private fun initMapForNewMessage(message: Message, position: Int) {
        if (isExpandedMap[message.uid] == null) {
            isExpandedMap[message.uid] = message.shouldBeExpanded(position, messages.lastIndex)
        }

        if (isThemeTheSameMap[message.uid] == null) isThemeTheSameMap[message.uid] = true
    }

    private fun ThreadViewHolder.toggleContentAndQuoteTheme(messageUid: String) = with(binding) {
        val isThemeTheSame = isThemeTheSameMap[messageUid]!!
        bodyWebView.toggleWebViewTheme(isThemeTheSame)
        fullMessageWebView.toggleWebViewTheme(isThemeTheSame)
        toggleFrameLayoutsTheme(isThemeTheSame)
    }

    private fun ThreadViewHolder.openRecipients() = with(binding) {
        messageDetails.isVisible = true
        recipientChevron.toggleChevron(false)
    }

    private fun ThreadViewHolder.loadContentAndQuote(message: Message) {
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

    private fun ThreadViewHolder.loadBodyInWebView(uid: String, body: String, type: String) = with(binding) {
        bodyWebView.applyWebViewContent(uid, body, type)
    }

    private fun ThreadViewHolder.loadQuoteInWebView(uid: String, quote: String?, type: String) = with(binding) {
        if (quote == null) return@with
        fullMessageWebView.applyWebViewContent(uid, quote, type)
    }

    private fun ThreadViewHolder.toggleWebViews(message: Message) = with(binding) {
        isQuoteCollapsed = !isQuoteCollapsed
        loadContentAndQuote(message)
    }

    private fun WebView.applyWebViewContent(uid: String, bodyWebView: String, type: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isThemeTheSameMap[uid]!!)
        }

        var styledBody = if (type == TEXT_PLAIN) createHtmlForPlainText(bodyWebView) else bodyWebView
        styledBody = processMailDisplay(styledBody, uid, isForPrinting)

        settings.setupThreadWebViewSettings()
        setupZoomListeners()

        loadDataWithBaseURL("", styledBody, TEXT_HTML, Utils.UTF_8, "")
    }

    private fun WebView.processMailDisplay(styledBody: String, uid: String, isForPrinting: Boolean): String {
        val isDisplayedInDark = context.isNightModeEnabled() && isThemeTheSameMap[uid] == true && !isForPrinting
        return webViewUtils.processHtmlForDisplay(context, styledBody, isDisplayedInDark, messages.first(), isForPrinting)
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

    private fun ThreadViewHolder.bindHeader(message: Message) = with(binding) {
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
            shortMessageDate.text = context.mailFormattedDate(messageDate)
        }

        message.sender?.let { recipient ->
            userAvatar.setOnClickListener {
                context.trackMessageEvent("selectAvatar")
                threadAdapterCallbacks?.onContactClicked?.invoke(recipient)
            }
        }

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

    private fun ThreadViewHolder.handleHeaderClick(message: Message) = with(binding) {
        messageHeader.setOnClickListener {
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

    private fun ThreadViewHolder.bindRecipientDetails(message: Message, messageDate: Date) = with(binding) {

        fromAdapter.updateList(message.from.toList())
        toAdapter.updateList(message.to.toList())

        val ccIsNotEmpty = message.cc.isNotEmpty()
        ccGroup.isVisible = ccIsNotEmpty
        if (ccIsNotEmpty) ccAdapter.updateList(message.cc.toList())

        val bccIsNotEmpty = message.bcc.isNotEmpty()
        bccGroup.isVisible = bccIsNotEmpty
        if (bccIsNotEmpty) bccAdapter.updateList(message.bcc.toList())

        detailedMessageDate.text = context.mostDetailedDate(messageDate)
    }

    private fun ThreadViewHolder.bindAlerts(messageUid: String) = with(binding) {
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
    private fun ThreadViewHolder.bindAttachment(message: Message) = with(binding) {
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

        return Formatter.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun ThreadViewHolder.bindContent(message: Message) {
        binding.messageLoader.isVisible = message.splitBody == null
        message.splitBody?.let { splitBody -> bindBody(message, hasQuote = splitBody.quote != null) }
    }

    private fun ThreadViewHolder.bindBody(message: Message, hasQuote: Boolean) = with(binding) {
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
            onWebViewFinishedLoading = { threadAdapterCallbacks?.onBodyWebviewFinishedLoading?.invoke() },
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
            if (currentSetOfLoadedExpandedMessagesUids.containsAll(initialSetOfExpandedMessagesUids)) {
                hasNotScrolledYet = false
                threadAdapterCallbacks?.onAllExpandedMessagesLoaded?.invoke()
            }
        }
    }

    private fun ThreadViewHolder.onExpandOrCollapseMessage(message: Message, shouldTrack: Boolean = true) = with(binding) {
        val isExpanded = isExpandedMap[message.uid]!!

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
        val index = messages.indexOf(message)
        notifyItemChanged(index, NotifyType.TOGGLE_LIGHT_MODE)
    }

    fun openRecipients(message: Message) {
        val index = messages.indexOf(message)
        notifyItemChanged(index, NotifyType.TOGGLE_RECIPIENTS)
    }

    fun reRenderMails() {
        notifyItemRangeChanged(0, itemCount, NotifyType.RE_RENDER)
    }

    fun updateFailedMessages(uids: List<String>) {
        uids.forEach { uid ->
            val index = messages.indexOfFirst { it.uid == uid }
            notifyItemChanged(index, NotifyType.FAILED_MESSAGE)
        }
    }

    fun resetCallbacks() {
        threadAdapterCallbacks = null
    }

    fun undoUserAttendanceClick(message: Message) {
        val indexOfMessage = messages.indexOfFirst { it.uid == message.uid }.takeIf { it >= 0 }
        indexOfMessage?.let { notifyItemChanged(it, NotifyType.ONLY_REBIND_CALENDAR_ATTENDANCE) }
    }

    private enum class NotifyType {
        TOGGLE_LIGHT_MODE,
        TOGGLE_RECIPIENTS,
        RE_RENDER,
        FAILED_MESSAGE,
        ONLY_REBIND_CALENDAR_ATTENDANCE,
    }

    enum class ContextMenuType {
        LINK,
        EMAIL,
        PHONE,
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldMessage: Message, newMessage: Message): Boolean {
            return oldMessage.uid == newMessage.uid
        }

        override fun areContentsTheSame(oldMessage: Message, newMessage: Message): Boolean {
            return areMessageContentsTheSameExceptCalendar(oldMessage, newMessage) &&
                    newMessage.latestCalendarEventResponse == oldMessage.latestCalendarEventResponse
        }

        override fun getChangePayload(oldItem: Message, newItem: Message): Any? {
            // If everything but attendees is the same, then we know the only thing that could've changed is attendees
            return if (everythingButAttendeesIsTheSame(oldItem, newItem)) NotifyType.ONLY_REBIND_CALENDAR_ATTENDANCE else null
        }

        companion object {
            fun everythingButAttendeesIsTheSame(oldItem: Message, newItem: Message): Boolean {
                val newCalendarEventResponse = newItem.latestCalendarEventResponse
                val oldCalendarEventResponse = oldItem.latestCalendarEventResponse

                return (areMessageContentsTheSameExceptCalendar(oldItem, newItem) &&
                        !(newCalendarEventResponse == null && oldCalendarEventResponse == null)
                        && newCalendarEventResponse?.everythingButAttendeesIsTheSame(oldCalendarEventResponse) == true)
            }

            private fun areMessageContentsTheSameExceptCalendar(oldMessage: Message, newMessage: Message): Boolean {
                return newMessage.body?.value == oldMessage.body?.value &&
                        newMessage.splitBody == oldMessage.splitBody
            }
        }
    }

    class ThreadViewHolder(
        val binding: ItemMessageBinding,
        private val shouldLoadDistantResources: Boolean,
        onContactClicked: ((contact: Recipient) -> Unit)?,
        onAttachmentClicked: ((attachment: Attachment) -> Unit)?,
    ) : ViewHolder(binding.root) {

        val fromAdapter = DetailedRecipientAdapter(onContactClicked)
        val toAdapter = DetailedRecipientAdapter(onContactClicked)
        val ccAdapter = DetailedRecipientAdapter(onContactClicked)
        val bccAdapter = DetailedRecipientAdapter(onContactClicked)
        val attachmentAdapter = AttachmentAdapter { onAttachmentClicked?.invoke(it) }

        private var _bodyWebViewClient: MessageWebViewClient? = null
        private var _fullMessageWebViewClient: MessageWebViewClient? = null
        val bodyWebViewClient get() = _bodyWebViewClient!!
        val fullMessageWebViewClient get() = _fullMessageWebViewClient!!

        init {
            WebView.enableSlowWholeDocumentDraw()
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
                    onWebViewFinishedLoading = onWebViewFinishedLoading
                )
                _fullMessageWebViewClient = binding.fullMessageWebView.initWebViewClientAndBridge(
                    attachments = message.attachments,
                    messageUid = message.uid,
                    shouldLoadDistantResources = shouldLoadDistantResources,
                    onBlockedResourcesDetected = ::promptUserForDistantImages,
                    navigateToNewMessageActivity = navigateToNewMessageActivity,
                    onWebViewFinishedLoading = onWebViewFinishedLoading
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

    private data class ThreadAdapterCallbacks(
        var onBodyWebviewFinishedLoading: (() -> Unit)? = null,
        var onContactClicked: ((contact: Recipient) -> Unit)? = null,
        var onDeleteDraftClicked: ((message: Message) -> Unit)? = null,
        var onDraftClicked: ((message: Message) -> Unit)? = null,
        var onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null,
        var onDownloadAllClicked: ((message: Message) -> Unit)? = null,
        var onReplyClicked: ((Message) -> Unit)? = null,
        var onMenuClicked: ((Message) -> Unit)? = null,
        var onAllExpandedMessagesLoaded: (() -> Unit)? = null,
        var navigateToNewMessageActivity: ((Uri) -> Unit)? = null,
        var navigateToAttendeeBottomSheet: ((List<Attendee>) -> Unit)? = null,
		var navigateToDownloadProgressDialog: (Int, Bundle) -> Unit,
        var replyToCalendarEvent: (AttendanceState, Message) -> Unit,
        var promptLink: ((String, ContextMenuType) -> Unit)? = null,
    )

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
