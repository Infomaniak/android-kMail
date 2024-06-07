/*
 * Infomaniak Mail - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.mail.ui.main.menuDrawer

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.infomaniak.lib.bugtracker.BugTrackerActivity
import com.infomaniak.lib.bugtracker.BugTrackerActivityArgs
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MatomoMail
import com.infomaniak.mail.MatomoMail.toFloat
import com.infomaniak.mail.MatomoMail.trackMenuDrawerEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.Quotas
import com.infomaniak.mail.data.models.mailbox.Mailbox
import com.infomaniak.mail.data.models.mailbox.MailboxPermissions
import com.infomaniak.mail.databinding.*
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerAdapter.MenuDrawerViewHolder
import com.infomaniak.mail.ui.main.menuDrawer.MenuDrawerFragment.MediatorContainer
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ConfettiUtils
import com.infomaniak.mail.utils.ConfettiUtils.ConfettiType
import com.infomaniak.mail.utils.UnreadDisplay
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import com.infomaniak.mail.utils.extensions.toggleChevron
import com.infomaniak.mail.views.itemViews.*
import com.infomaniak.mail.views.itemViews.DecoratedItemView.SelectionStyle
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.math.min

class MenuDrawerAdapter @Inject constructor(
    private val globalCoroutineScope: CoroutineScope,
) : ListAdapter<Any, MenuDrawerViewHolder>(FolderDiffCallback()) {

    private var setFoldersJob: Job? = null

    private inline val items: List<Any> get() = currentList

    private lateinit var currentClassName: String
    private var confettiContainer: ViewGroup? = null
    private var currentFolderId: String? = null
    private var hasCollapsableFolder: Boolean? = null

    private lateinit var onAskingTransition: () -> Unit
    private lateinit var onAskingToCloseDrawer: () -> Unit
    private lateinit var onMailboxesHeaderClicked: () -> Unit
    private lateinit var onValidMailboxClicked: (Int) -> Unit
    private lateinit var onInvalidPasswordMailboxClicked: (Mailbox) -> Unit
    private lateinit var onLockedMailboxClicked: (String) -> Unit
    private lateinit var onCustomFoldersHeaderClicked: (Boolean) -> Unit
    private lateinit var onCreateFolderClicked: () -> Unit
    private lateinit var onFolderClicked: (folderId: String) -> Unit
    private lateinit var onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit

    operator fun invoke(
        currentClassName: String,
        confettiContainer: ViewGroup?,
        onAskingTransition: () -> Unit,
        onAskingToCloseDrawer: () -> Unit,
        onMailboxesHeaderClicked: () -> Unit,
        onValidMailboxClicked: (Int) -> Unit,
        onInvalidPasswordMailboxClicked: (Mailbox) -> Unit,
        onLockedMailboxClicked: (String) -> Unit,
        onCustomFoldersHeaderClicked: (Boolean) -> Unit,
        onCreateFolderClicked: () -> Unit,
        onFolderClicked: (folderId: String) -> Unit,
        onCollapseChildrenClicked: (folderId: String, shouldCollapse: Boolean) -> Unit,
    ): MenuDrawerAdapter {
        this.currentClassName = currentClassName
        this.confettiContainer = confettiContainer
        this.onAskingTransition = onAskingTransition
        this.onAskingToCloseDrawer = onAskingToCloseDrawer
        this.onMailboxesHeaderClicked = onMailboxesHeaderClicked
        this.onValidMailboxClicked = onValidMailboxClicked
        this.onInvalidPasswordMailboxClicked = onInvalidPasswordMailboxClicked
        this.onLockedMailboxClicked = onLockedMailboxClicked
        this.onCustomFoldersHeaderClicked = onCustomFoldersHeaderClicked
        this.onCreateFolderClicked = onCreateFolderClicked
        this.onFolderClicked = onFolderClicked
        this.onCollapseChildrenClicked = onCollapseChildrenClicked

        return this
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setItems(mediatorContainer: MediatorContainer) = runCatchingRealm {

        val (
            currentMailbox,
            areMailboxesExpanded,
            otherMailboxes,
            currentFolder,
            defaultFolders,
            areCustomFoldersExpanded,
            customFolders,
            permissions,
            quotas,
        ) = mediatorContainer

        /**
         * If there was no Folder with children, and then now there's at least one, we need to indent the whole Folders list.
         */
        // TODO: The indent isn't working great for DEFAULT folders.
        fun notifyCollapsableFolders() {
            setFoldersJob?.cancel()
            setFoldersJob = globalCoroutineScope.launch {
                val newHasCollapsableFolder = customFolders.any { it.canBeCollapsed }

                val isFirstTime = hasCollapsableFolder == null
                val collapsableFolderExistenceHasChanged = newHasCollapsableFolder != hasCollapsableFolder
                hasCollapsableFolder = newHasCollapsableFolder

                if (!isFirstTime && collapsableFolderExistenceHasChanged) {
                    Dispatchers.Main { notifyDataSetChanged() }
                }
            }
        }

        fun notifySelectedFolder() {

            fun notifyFolder(folderId: String) {
                val position = items.indexOfFirst { it is Folder && it.id == folderId }
                notifyItemChanged(position)
            }

            val oldId = currentFolderId
            val newId = currentFolder?.id

            if (newId != oldId) {
                currentFolderId = newId

                oldId?.let(::notifyFolder)
                newId?.let(::notifyFolder)
            }
        }

        val items = mutableListOf<Any>().apply {

            // Mailboxes
            add(MailboxesHeader(currentMailbox, otherMailboxes.isNotEmpty(), areMailboxesExpanded))
            if (areMailboxesExpanded) addAll(otherMailboxes)

            // Default Folders
            add(ItemType.DIVIDER)
            addAll(defaultFolders)

            // Custom Folders
            add(ItemType.DIVIDER)
            add(ItemType.CUSTOM_FOLDERS_HEADER)
            if (areCustomFoldersExpanded) {
                if (customFolders.isEmpty()) {
                    add(ItemType.EMPTY_CUSTOM_FOLDERS)
                } else {
                    addAll(customFolders)
                }
            }

            // Footer
            add(ItemType.DIVIDER)
            add(MenuDrawerFooter(permissions, quotas))
        }

        submitList(items)
        notifyCollapsableFolders()
        notifySelectedFolder()
    }

    override fun getItemCount(): Int = runCatchingRealm { items.size }.getOrDefault(0)

    override fun getItemViewType(position: Int): Int = runCatchingRealm {
        return@runCatchingRealm when (val item = items[position]) {
            is MailboxesHeader -> DisplayType.MAILBOXES_HEADER.layout
            is Mailbox -> if (item.isValid) DisplayType.MAILBOX.layout else DisplayType.INVALID_MAILBOX.layout
            is Folder -> DisplayType.FOLDER.layout
            ItemType.CUSTOM_FOLDERS_HEADER -> DisplayType.CUSTOM_FOLDERS_HEADER.layout
            ItemType.EMPTY_CUSTOM_FOLDERS -> DisplayType.EMPTY_CUSTOM_FOLDERS.layout
            is MenuDrawerFooter -> DisplayType.MENU_DRAWER_FOOTER.layout
            else -> DisplayType.DIVIDER.layout
        }
    }.getOrDefault(super.getItemViewType(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuDrawerViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        val binding = when (viewType) {
            DisplayType.MAILBOXES_HEADER.layout -> ItemMenuDrawerMailboxesHeaderBinding.inflate(inflater, parent, false)
            DisplayType.MAILBOX.layout -> ItemMenuDrawerMailboxBinding.inflate(inflater, parent, false)
            DisplayType.FOLDER.layout -> ItemMenuDrawerFolderBinding.inflate(inflater, parent, false)
            DisplayType.CUSTOM_FOLDERS_HEADER.layout -> ItemMenuDrawerCustomFoldersHeaderBinding.inflate(inflater, parent, false)
            DisplayType.EMPTY_CUSTOM_FOLDERS.layout -> ItemMenuDrawerEmptyCustomFoldersBinding.inflate(inflater, parent, false)
            DisplayType.MENU_DRAWER_FOOTER.layout -> ItemMenuDrawerFooterBinding.inflate(inflater, parent, false)
            else -> ItemMenuDrawerDividerBinding.inflate(inflater, parent, false)
        }

        return MenuDrawerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int, payloads: MutableList<Any>) {

        val payload = payloads.firstOrNull()
        if (payload !is NotifyType) {
            super.onBindViewHolder(holder, position, payloads)
            return
        }

        when (payload) {
            NotifyType.MAILBOXES_HEADER_CLICKED -> {
                Log.d("Bind", "Rebind Mailboxes header because of collapse change")
                (holder.binding as ItemMenuDrawerMailboxesHeaderBinding).updateCollapseState(items[position] as MailboxesHeader)
            }
        }
    }

    override fun onBindViewHolder(holder: MenuDrawerViewHolder, position: Int): Unit = with(holder.binding) {
        val item = items[position]

        when (getItemViewType(position)) {
            DisplayType.MAILBOXES_HEADER.layout -> {
                Log.d("Bind", "Rebind Mailboxes header")
                (this as ItemMenuDrawerMailboxesHeaderBinding).displayMailboxesHeader(item as MailboxesHeader)
            }
            DisplayType.MAILBOX.layout -> {
                Log.d("Bind", "Rebind Mailbox (${(item as Mailbox).email})")
                (this as ItemMenuDrawerMailboxBinding).displayMailbox(item as Mailbox)
            }
            DisplayType.INVALID_MAILBOX.layout -> {
                Log.d("Bind", "Rebind Invalid Mailbox (${(item as Mailbox).email})")
                (this as ItemInvalidMailboxBinding).displayInvalidMailbox(item as Mailbox)
            }
            DisplayType.FOLDER.layout -> {
                Log.d("Bind", "Rebind Folder : ${(item as Folder).name}")
                (this as ItemMenuDrawerFolderBinding).displayFolder(item as Folder)
            }
            DisplayType.CUSTOM_FOLDERS_HEADER.layout -> {
                Log.d("Bind", "Rebind Folders header")
                (this as ItemMenuDrawerCustomFoldersHeaderBinding).displayCustomFoldersHeader()
            }
            DisplayType.MENU_DRAWER_FOOTER.layout -> {
                Log.d("Bind", "Rebind Footer")
                (this as ItemMenuDrawerFooterBinding).displayFooter(item as MenuDrawerFooter)
            }
        }
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.displayMailboxesHeader(header: MailboxesHeader) = with(header) {
        mailboxSwitcherText.text = mailbox?.email

        mailboxSwitcher.apply {
            isClickable = hasMoreThanOneMailbox
            isFocusable = hasMoreThanOneMailbox
        }

        mailboxExpandButton.isVisible = hasMoreThanOneMailbox

        setMailboxSwitcherTextAppearance(isExpanded)

        root.setOnClickListener { onMailboxesHeaderClicked() }
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.updateCollapseState(header: MailboxesHeader) = with(header) {
        mailboxExpandButton.toggleChevron(!isExpanded)
        setMailboxSwitcherTextAppearance(isExpanded)
    }

    private fun ItemMenuDrawerMailboxesHeaderBinding.setMailboxSwitcherTextAppearance(isOpen: Boolean) {
        mailboxSwitcherText.setTextAppearance(if (isOpen) R.style.BodyMedium_Accent else R.style.BodyMedium)
    }

    private fun ItemMenuDrawerMailboxBinding.displayMailbox(mailbox: Mailbox) = with(root) {
        text = mailbox.email
        unreadCount = mailbox.unreadCountDisplay.count
        isPastilleDisplayed = mailbox.unreadCountDisplay.shouldDisplayPastille

        setOnClickListener {
            context.trackMenuDrawerEvent(MatomoMail.SWITCH_MAILBOX_NAME)
            onValidMailboxClicked(mailbox.mailboxId)
        }
    }

    private fun ItemInvalidMailboxBinding.displayInvalidMailbox(mailbox: Mailbox) = with(root) {
        text = mailbox.email
        itemStyle = SelectionStyle.MENU_DRAWER
        isPasswordOutdated = !mailbox.isPasswordValid
        isMailboxLocked = mailbox.isLocked
        hasNoValidMailboxes = false

        computeEndIconVisibility()

        initSetOnClickListener(
            onLockedMailboxClicked = { onLockedMailboxClicked(mailbox.email) },
            onInvalidPasswordMailboxClicked = { onInvalidPasswordMailboxClicked(mailbox) },
        )
    }

    private fun ItemMenuDrawerFolderBinding.displayFolder(folder: Folder) = with(root) {
        val unread = when (folder.role) {
            FolderRole.DRAFT -> UnreadDisplay(folder.threads.count())
            FolderRole.SENT, FolderRole.TRASH -> UnreadDisplay(0)
            else -> folder.unreadCountDisplay
        }

        folder.role?.let {
            setFolderUi(folder, it.folderIconRes, unread, it.matomoValue)
        } ?: run {
            val indentLevel = folder.path.split(folder.separator).size - 1
            setFolderUi(
                folder = folder,
                iconId = if (folder.isFavorite) R.drawable.ic_folder_star else R.drawable.ic_folder,
                unread = unread,
                trackerName = "customFolder",
                trackerValue = indentLevel.toFloat(),
                folderIndent = min(indentLevel, MAX_SUB_FOLDERS_INDENT),
            )
        }
    }

    private fun SelectableItemView.setFolderUi(
        folder: Folder,
        @DrawableRes iconId: Int,
        unread: UnreadDisplay?,
        trackerName: String,
        trackerValue: Float? = null,
        folderIndent: Int = 0,
    ) {
        val folderName = folder.getLocalizedName(context)

        text = folderName
        icon = AppCompatResources.getDrawable(context, iconId)
        setSelectedState(currentFolderId == folder.id)

        when (this) {
            is SelectableFolderItemView -> setIndent(folderIndent)
            is UnreadFolderItemView -> {
                initOnCollapsableClickListener { onCollapseChildrenClicked(folder.id, isCollapsed) }
                isPastilleDisplayed = unread?.shouldDisplayPastille ?: false
                unreadCount = unread?.count ?: 0
                isHidden = folder.isHidden
                isCollapsed = folder.isCollapsed
                canBeCollapsed = folder.canBeCollapsed
                setIndent(
                    indent = folderIndent,
                    hasCollapsableFolder = hasCollapsableFolder.let { if (it == null || folder.role != null) false else it },
                    canBeCollapsed = canBeCollapsed,
                )
                setCollapsingButtonContentDescription(folderName)
            }
            is SelectableMailboxItemView, is UnreadItemView -> {
                error("`${this::class.simpleName}` cannot exists here. Only Folder classes are allowed")
            }
        }

        setOnClickListener {
            context.trackMenuDrawerEvent(trackerName, value = trackerValue)
            onFolderClicked.invoke(folder.id)
        }
    }

    private fun ItemMenuDrawerCustomFoldersHeaderBinding.displayCustomFoldersHeader() = with(root) {
        setOnClickListener { onCustomFoldersHeaderClicked(isCollapsed) }
        setOnActionClickListener { onCreateFolderClicked() }
    }

    private fun ItemMenuDrawerFooterBinding.displayFooter(footer: MenuDrawerFooter) = with(root) {
        // Actions header
        advancedActions.setOnClickListener {
            onAskingTransition()
            context.trackMenuDrawerEvent("advancedActions", value = (!advancedActions.isCollapsed).toFloat())
            advancedActionsLayout.isGone = advancedActions.isCollapsed
        }

        // Calendar & contacts sync
        syncAutoConfig.setOnClickListener {
            // context.trackSyncAutoConfigEvent("openFromMenuDrawer")
            // launchSyncAutoConfigActivityForResult()
            onAskingToCloseDrawer()
        }

        // Import mails
        importMails.setOnClickListener {
            context.trackMenuDrawerEvent("importEmails")
            context.openUrl(BuildConfig.IMPORT_EMAILS_URL)
            onAskingToCloseDrawer()
        }

        // Restore mails
        restoreMails.setOnClickListener {
            context.trackMenuDrawerEvent("restoreEmails")
            // safeNavigate(R.id.restoreEmailsBottomSheetDialog, currentClassName = currentClassName)
        }
        restoreMails.isVisible = footer.permissions?.canRestoreEmails == true

        // Feedback
        feedback.setOnClickListener {
            onAskingToCloseDrawer()
            if (AccountUtils.currentUser?.isStaff == true) {
                Intent(context, BugTrackerActivity::class.java).apply {
                    putExtras(
                        BugTrackerActivityArgs(
                            user = AccountUtils.currentUser!!,
                            appBuildNumber = BuildConfig.VERSION_NAME,
                            bucketIdentifier = BuildConfig.BUGTRACKER_MAIL_BUCKET_ID,
                            projectName = BuildConfig.BUGTRACKER_MAIL_PROJECT_NAME,
                            repoGitHub = BuildConfig.GITHUB_REPO,
                        ).toBundle(),
                    )
                }.also(context::startActivity)
            } else {
                context.trackMenuDrawerEvent("feedback")
                context.openUrl(context.getString(R.string.urlUserReportAndroid))
            }
        }

        // Help
        help.setOnClickListener {
            ShortcutManagerCompat.reportShortcutUsed(context, Utils.Shortcuts.SUPPORT.id)
            context.trackMenuDrawerEvent("help")
            context.openUrl(BuildConfig.CHATBOT_URL)
            onAskingToCloseDrawer()
        }

        // Quotas
        val isLimited = footer.quotas != null
        storageLayout.isVisible = isLimited
        storageDivider.isVisible = isLimited
        if (isLimited) {
            storageText.text = footer.quotas!!.getText(context)
            storageIndicator.progress = footer.quotas.getProgress()
        }

        // App version
        appVersionName.apply {
            text = "App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            setOnClickListener {
                ConfettiUtils.onEasterEggConfettiClicked(
                    container = confettiContainer,
                    type = ConfettiType.INFOMANIAK,
                    matomoValue = "MenuDrawer",
                )
            }
        }
    }

    companion object {
        private const val MAX_SUB_FOLDERS_INDENT = 2
    }

    class MenuDrawerViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private enum class DisplayType(val layout: Int) {
        MAILBOXES_HEADER(R.layout.item_menu_drawer_mailboxes_header),
        MAILBOX(R.layout.item_menu_drawer_mailbox),
        INVALID_MAILBOX(R.layout.item_invalid_mailbox),
        FOLDER(R.layout.item_menu_drawer_folder),
        CUSTOM_FOLDERS_HEADER(R.layout.view_menu_drawer_dropdown),
        EMPTY_CUSTOM_FOLDERS(R.layout.item_menu_drawer_empty_custom_folders),
        MENU_DRAWER_FOOTER(R.layout.item_menu_drawer_footer),
        DIVIDER(R.layout.item_menu_drawer_divider),
    }

    private data class MailboxesHeader(val mailbox: Mailbox?, val hasMoreThanOneMailbox: Boolean, val isExpanded: Boolean)

    private data class MenuDrawerFooter(val permissions: MailboxPermissions?, val quotas: Quotas?)

    private enum class ItemType {
        CUSTOM_FOLDERS_HEADER,
        EMPTY_CUSTOM_FOLDERS,
        DIVIDER,
    }

    private enum class NotifyType {
        MAILBOXES_HEADER_CLICKED,
    }

    private class FolderDiffCallback : DiffUtil.ItemCallback<Any>() {

        override fun areItemsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            return when (oldItem) {
                is MailboxesHeader -> newItem is MailboxesHeader && newItem.mailbox?.objectId == oldItem.mailbox?.objectId
                is Mailbox -> newItem is Mailbox && newItem.objectId == oldItem.objectId
                is Folder -> newItem is Folder && newItem.id == oldItem.id
                ItemType.CUSTOM_FOLDERS_HEADER -> newItem == ItemType.CUSTOM_FOLDERS_HEADER
                ItemType.EMPTY_CUSTOM_FOLDERS -> newItem == ItemType.EMPTY_CUSTOM_FOLDERS
                is MenuDrawerFooter -> newItem is MenuDrawerFooter
                ItemType.DIVIDER -> newItem == ItemType.DIVIDER
                else -> false
            }
        }.getOrDefault(false)

        override fun areContentsTheSame(oldItem: Any, newItem: Any) = runCatchingRealm {
            return when (oldItem) {
                is MailboxesHeader -> newItem is MailboxesHeader
                        && newItem.hasMoreThanOneMailbox == oldItem.hasMoreThanOneMailbox
                        && newItem.isExpanded == oldItem.isExpanded
                        && newItem.mailbox?.unreadCountDisplay?.count == oldItem.mailbox?.unreadCountDisplay?.count
                is Mailbox -> newItem is Mailbox && newItem.unreadCountDisplay.count == oldItem.unreadCountDisplay.count
                is Folder -> newItem is Folder &&
                        newItem.name == oldItem.name &&
                        newItem.isFavorite == oldItem.isFavorite &&
                        newItem.path == oldItem.path &&
                        newItem.unreadCountDisplay == oldItem.unreadCountDisplay &&
                        newItem.threads.count() == oldItem.threads.count() &&
                        newItem.isHidden == oldItem.isHidden &&
                        newItem.canBeCollapsed == oldItem.canBeCollapsed &&
                        newItem.shouldDisplayDivider == oldItem.shouldDisplayDivider
                ItemType.CUSTOM_FOLDERS_HEADER -> true
                ItemType.EMPTY_CUSTOM_FOLDERS -> true
                is MenuDrawerFooter -> newItem is MenuDrawerFooter && newItem.quotas?.size == oldItem.quotas?.size
                else -> false
            }
        }.getOrDefault(false)

        override fun getChangePayload(oldItem: Any, newItem: Any): Any? {
            // return when (oldItem) {
            //     is MailboxesHeader -> {
            //         if (newItem is MailboxesHeader && oldItem.isExpanded != newItem.isExpanded) {
            //             NotifyType.MAILBOX_HEADER_IS_EXPANDED_OR_COLLAPSED
            //         } else {
            //             null
            //         }
            //     }
            //     is Mailbox -> null
            //     is Folder -> null
            //     ItemType.CUSTOM_FOLDERS_HEADER -> null
            //     ItemType.EMPTY_CUSTOM_FOLDERS -> null
            //     else -> null
            // }

            return if (newItem is MailboxesHeader && oldItem is MailboxesHeader && newItem.isExpanded != oldItem.isExpanded) {
                NotifyType.MAILBOXES_HEADER_CLICKED
            } else {
                null
            }
        }
    }
}
