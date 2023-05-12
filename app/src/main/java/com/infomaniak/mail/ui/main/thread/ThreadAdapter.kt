/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.views.ViewHolder
import com.infomaniak.mail.MatomoMail.trackMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Attachment.*
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.databinding.ItemMessageBinding
import com.infomaniak.mail.ui.main.thread.ThreadAdapter.ThreadViewHolder
import com.infomaniak.mail.utils.*
import com.infomaniak.mail.utils.UiUtils.getPrettyNameAndEmail
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.TEXT_HTML
import com.infomaniak.mail.utils.Utils.TEXT_PLAIN
import com.infomaniak.mail.utils.WebViewUtils.Companion.setupThreadWebViewSettings
import com.infomaniak.mail.utils.WebViewUtils.Companion.toggleWebViewTheme
import org.jsoup.Jsoup
import java.util.*
import com.google.android.material.R as RMaterial

class ThreadAdapter : RecyclerView.Adapter<ThreadViewHolder>(),
    RealmChangesBinding.OnRealmChanged<Message> {

    var messages = listOf<Message>()
        private set
    var isExpandedMap = mutableMapOf<String, Boolean>()
    var isThemeTheSameMap = mutableMapOf<String, Boolean>()
    var contacts: Map<String, Map<String, MergedContact>> = emptyMap()

    var onContactClicked: ((contact: Recipient) -> Unit)? = null
    var onDeleteDraftClicked: ((message: Message) -> Unit)? = null
    var onDraftClicked: ((message: Message) -> Unit)? = null
    var onAttachmentClicked: ((attachment: Attachment) -> Unit)? = null
    var onDownloadAllClicked: ((message: Message) -> Unit)? = null
    var onReplyClicked: ((Message) -> Unit)? = null
    var onMenuClicked: ((Message) -> Unit)? = null

    private lateinit var recyclerView: RecyclerView
    private val webViewUtils by lazy { WebViewUtils(recyclerView.context) }

    private val scaledTouchSlop by lazy { ViewConfiguration.get(recyclerView.context).scaledTouchSlop }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
        super.onAttachedToRecyclerView(recyclerView)
    }

    override fun updateList(itemList: List<Message>) {
        messages = itemList
    }

    override fun getItemCount() = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreadViewHolder {
        return ThreadViewHolder(
            ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            onContactClicked,
            onAttachmentClicked,
        )
    }

    // Add here everything in a Message that can be updated in the UI.
    override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
        return newItem.body?.value == oldItem.body?.value &&
                newItem.isSeen == oldItem.isSeen &&
                newItem.isFavorite == oldItem.isFavorite
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int, payloads: MutableList<Any>) = with(holder.binding) {
        val message = messages[position]

        val payload = payloads.firstOrNull()
        if (payload is NotificationType) {
            when (payload) {
                NotificationType.AVATAR -> if (!message.isDraft) userAvatar.loadAvatar(message.from.first(), contacts)
                NotificationType.TOGGLE_LIGHT_MODE -> {
                    isThemeTheSameMap[message.uid] = !isThemeTheSameMap[message.uid]!!
                    holder.toggleBodyAndQuoteTheme(message)
                }
                NotificationType.RERENDER -> holder.loadBodyAndQuote(message) // TODO : Try to undo js script and recall the method to fix rendering
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ThreadViewHolder, position: Int) = with(holder) {
        val message = messages[position]

        initMapForNewMessage(message, position)

        bindHeader(message)
        bindAttachment(message)

        displayExpandedCollapsedMessage(message, shouldTrack = false)

        bindQuoteButton(message)
    }

    private fun initMapForNewMessage(message: Message, position: Int) {
        if (isExpandedMap[message.uid] == null) {
            isExpandedMap[message.uid] = message.shouldBeExpanded(position, messages.lastIndex)
        }

        if (isThemeTheSameMap[message.uid] == null) isThemeTheSameMap[message.uid] = true
    }

    private fun ThreadViewHolder.toggleBodyAndQuoteTheme(message: Message) = with(binding) {
        val isThemeTheSame = isThemeTheSameMap[message.uid]!!
        bodyWebView.toggleWebViewTheme(isThemeTheSame)
        fullMessageWebView.toggleWebViewTheme(isThemeTheSame)
        toggleQuoteButtonTheme(isThemeTheSame)
    }

    private fun ThreadViewHolder.loadBodyAndQuote(message: Message) {
        message.body?.let { body ->
            if (splitBody == null) {
                val (messageBody, messageQuote) = MessageBodyUtils.splitBodyAndQuote(body)
                splitBody = messageBody
                splitQuote = messageQuote

                message.hasQuote = messageQuote != null
            }

            if (binding.bodyWebView.isVisible) {
                loadBodyInWebView(message.uid, splitBody!!, body.type)
            } else if (binding.fullMessageWebView.isVisible) {
                loadQuoteInWebView(message.uid, splitQuote, body.type)
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
        val showStandardWebView = fullMessageWebView.isVisible
        bodyWebView.isVisible = showStandardWebView
        fullMessageWebView.isVisible = !showStandardWebView

        loadBodyAndQuote(message)
    }

    private fun WebView.applyWebViewContent(uid: String, bodyWebView: String, type: String) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isThemeTheSameMap[uid]!!)
        }

        var styledBody = if (type == TEXT_PLAIN) createHtmlForPlainText(bodyWebView) else bodyWebView
        styledBody = processMailDisplay(styledBody, uid)

        settings.setupThreadWebViewSettings()
        setupZoomListeners()

        loadDataWithBaseURL("", styledBody, TEXT_HTML, Utils.UTF_8, "")
    }

    private fun createHtmlForPlainText(text: String): String {
        Jsoup.parse("").apply {
            body().appendElement("pre").text(text).attr("style", "word-wrap: break-word; white-space: pre-wrap;")
            return html()
        }
    }

    private fun WebView.processMailDisplay(styledBody: String, uid: String): String {
        val isDisplayedInDark = context.isNightModeEnabled() && isThemeTheSameMap[uid] == true
        return webViewUtils.processHtmlForDisplay(styledBody, isDisplayedInDark)
    }

    private fun WebView.setupZoomListeners() {
        val scaleListener = MessageBodyScaleListener(recyclerView, this, this.parent as FrameLayout)
        val scaleDetector = ScaleGestureDetector(context, scaleListener)
        val touchListener = MessageBodyTouchListener(recyclerView, scaleDetector, scaledTouchSlop)
        setOnTouchListener(touchListener)
    }

    private fun ItemMessageBinding.toggleQuoteButtonTheme(isThemeTheSame: Boolean) {
        if (isThemeTheSame) {
            quoteButtonFrameLayout.setBackgroundColor(context.getColor(R.color.background_color_dark))
            quoteButton.setTextColor(context.getAttributeColor(RMaterial.attr.colorPrimary))
        } else {
            quoteButtonFrameLayout.setBackgroundColor(context.getColor(R.color.background_color_light))
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
            val firstSender = message.from.firstOrNull()
            userAvatar.loadAvatar(firstSender, contacts)
            expeditorName.apply {
                text = firstSender?.let { context.getPrettyNameAndEmail(it).first }
                    ?: run { context.getString(R.string.unknownRecipientTitle) }
                setTextAppearance(R.style.BodyMedium)
            }
            shortMessageDate.text = context.mailFormattedDate(messageDate)
        }

        message.from.firstOrNull()?.let { recipient ->
            userAvatar.setOnClickListener {
                context.trackMessageEvent("selectAvatar")
                onContactClicked?.invoke(recipient)
            }
        }

        setDetailedFieldsVisibility(message)

        initWebViewClientIfNeeded(message)

        handleHeaderClick(message)
        handleExpandDetailsClick(message)
        bindRecipientDetails(message, messageDate)
    }

    private fun Context.mailFormattedDate(date: Date): CharSequence = with(date) {
        return when {
            isToday() -> format(FORMAT_EMAIL_DATE_HOUR)
            isYesterday() -> getString(
                R.string.messageDetailsDateAt,
                getString(R.string.messageDetailsYesterday),
                format(FORMAT_EMAIL_DATE_HOUR),
            )
            isThisYear() -> getString(
                R.string.messageDetailsDateAt,
                format(FORMAT_EMAIL_DATE_SHORT_DATE),
                format(FORMAT_EMAIL_DATE_HOUR),
            )
            else -> this@mailFormattedDate.mostDetailedDate(date = this@with)
        }
    }

    private fun Context.mostDetailedDate(date: Date): String = with(date) {
        return getString(
            R.string.messageDetailsDateAt,
            format(FORMAT_EMAIL_DATE_LONG_DATE),
            format(FORMAT_EMAIL_DATE_HOUR),
        )
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
                displayExpandedCollapsedMessage(message)
            } else {
                if (message.isDraft) {
                    onDraftClicked?.invoke(message)
                } else {
                    isExpandedMap[message.uid] = true
                    displayExpandedCollapsedMessage(message)
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
        attachmentLayout.attachmentsDownloadAllButton.setOnClickListener { onDownloadAllClicked?.invoke(message) }
    }

    private fun ItemMessageBinding.formatAttachmentFileSize(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return ""

        val totalAttachmentsFileSizeInBytes: Long = attachments.map { attachment ->
            attachment.size
        }.reduce { accumulator: Long, size: Long -> accumulator + size }

        return FormatterFileSize.formatShortFileSize(context, totalAttachmentsFileSizeInBytes)
    }

    private fun ThreadViewHolder.displayExpandedCollapsedMessage(message: Message, shouldTrack: Boolean = true) = with(binding) {
        val isExpanded = isExpandedMap[message.uid]!!

        if (shouldTrack) context.trackMessageEvent("openMessage", isExpanded)

        collapseMessageDetails(message)
        setHeaderState(message, isExpanded)

        fullMessageWebView.isGone = true

        if (message.body?.value == null) {
            messageLoader.isVisible = isExpanded
            bodyWebView.isGone = true
        } else {
            messageLoader.isGone = true
            bodyWebView.isVisible = isExpanded
        }

        if (isExpanded) {
            loadBodyAndQuote(message)
            displayAttachments(message.attachments)
            if (message.hasQuote) quoteButton.text = context.getString(R.string.messageShowQuotedText)
            quoteButtonFrameLayout.isVisible = message.hasQuote
        } else {
            hideAttachments()
            quoteButtonFrameLayout.isGone = true
        }
    }

    @SuppressLint("SetTextI18n")
    private fun ItemMessageBinding.displayAttachments(attachments: List<Attachment>) {
        if (attachments.isEmpty()) hideAttachments() else showAttachments()
    }

    private fun ItemMessageBinding.hideAttachments() {
        attachmentLayout.root.isGone = true
    }

    private fun ItemMessageBinding.showAttachments() {
        attachmentLayout.root.isVisible = true
    }

    private fun ItemMessageBinding.collapseMessageDetails(message: Message) {
        message.detailsAreExpanded = false
        messageDetails.isGone = true
        recipientChevron.rotation = 0.0f
    }

    private fun ItemMessageBinding.setHeaderState(message: Message, isExpanded: Boolean) = with(message) {
        deleteDraftButton.apply {
            isVisible = isDraft
            setOnClickListener { onDeleteDraftClicked?.invoke(this@with) }
        }
        replyButton.apply {
            isVisible = isExpanded
            setOnClickListener { onReplyClicked?.invoke(message) }
        }
        menuButton.apply {
            isVisible = isExpanded
            setOnClickListener { onMenuClicked?.invoke(message) }
        }

        recipient.text = if (isExpanded) getAllRecipientsFormatted(message = this@with) else context.formatSubject(subject)
        recipientChevron.isVisible = isExpanded
        recipientOverlayedButton.isVisible = isExpanded
    }

    private fun ItemMessageBinding.getAllRecipientsFormatted(message: Message): String = with(message) {
        return listOf(*to.toTypedArray(), *cc.toTypedArray(), *bcc.toTypedArray()).joinToString { it.displayedName(context) }
    }

    private fun ThreadViewHolder.bindQuoteButton(message: Message) = with(binding) {
        quoteButton.setOnClickListener {
            val textId = if (fullMessageWebView.isVisible) R.string.messageShowQuotedText else R.string.messageHideQuotedText
            quoteButton.text = context.getString(textId)
            toggleWebViews(message)
        }
    }

    fun updateContacts(newContacts: Map<String, Map<String, MergedContact>>) {
        contacts = newContacts
        notifyItemRangeChanged(0, itemCount, NotificationType.AVATAR)
    }

    fun toggleLightMode(message: Message) {
        val index = messages.indexOf(message)
        notifyItemChanged(index, NotificationType.TOGGLE_LIGHT_MODE)
    }

    fun rerenderMails() {
        notifyItemRangeChanged(0, itemCount, NotificationType.RERENDER)
    }

    private enum class NotificationType {
        AVATAR,
        TOGGLE_LIGHT_MODE,
        RERENDER,
    }

    private companion object {
        const val FORMAT_EMAIL_DATE_HOUR = "HH:mm"
        const val FORMAT_EMAIL_DATE_SHORT_DATE = "d MMM"
        const val FORMAT_EMAIL_DATE_LONG_DATE = "d MMM yyyy"
    }

    class ThreadViewHolder(
        val binding: ItemMessageBinding,
        onContactClicked: ((contact: Recipient) -> Unit)?,
        onAttachmentClicked: ((attachment: Attachment) -> Unit)?,
    ) : ViewHolder(binding.root) {

        val fromAdapter = DetailedRecipientAdapter(onContactClicked)
        val toAdapter = DetailedRecipientAdapter(onContactClicked)
        val ccAdapter = DetailedRecipientAdapter(onContactClicked)
        val bccAdapter = DetailedRecipientAdapter(onContactClicked)
        val attachmentAdapter = AttachmentAdapter { onAttachmentClicked?.invoke(it) }

        private var doesWebViewNeedInit = true

        var splitBody: String? = null
        var splitQuote: String? = null

        init {
            with(binding) {
                fromRecyclerView.adapter = fromAdapter
                toRecyclerView.adapter = toAdapter
                ccRecyclerView.adapter = ccAdapter
                bccRecyclerView.adapter = bccAdapter
                attachmentLayout.attachmentsRecyclerView.adapter = attachmentAdapter
            }
        }

        fun initWebViewClientIfNeeded(message: Message) = with(binding) {
            if (doesWebViewNeedInit) {
                bodyWebView.initWebViewClientAndBridge(message.attachments, message.uid)
                fullMessageWebView.initWebViewClientAndBridge(message.attachments, message.uid)
                doesWebViewNeedInit = false
            }
        }
    }
}
