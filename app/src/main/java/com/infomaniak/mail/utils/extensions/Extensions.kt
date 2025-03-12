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
package com.infomaniak.mail.utils.extensions

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.TypedArray
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.util.Patterns
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import androidx.annotation.*
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.getColorOrThrow
import androidx.core.text.toSpannable
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.work.Data
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.core.utils.endOfTheWeek
import com.infomaniak.core.utils.startOfTheDay
import com.infomaniak.core.utils.startOfTheWeek
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.removeAccents
import com.infomaniak.lib.core.utils.showToast
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.cache.mailboxContent.FolderController
import com.infomaniak.mail.data.cache.mailboxContent.ImpactedFolders
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.FolderRole
import com.infomaniak.mail.data.models.SnoozeState
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.signature.Signature
import com.infomaniak.mail.ui.alertDialogs.BaseAlertDialog
import com.infomaniak.mail.ui.alertDialogs.DescriptionAlertDialog
import com.infomaniak.mail.ui.login.IlluColors.IlluColors
import com.infomaniak.mail.ui.main.SnackbarManager
import com.infomaniak.mail.ui.main.folder.DateSeparatorItemDecoration
import com.infomaniak.mail.ui.main.folder.HeaderItemDecoration
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.thread.MessageWebViewClient
import com.infomaniak.mail.ui.main.thread.RoundedBackgroundSpan
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.Companion.getTagsPaint
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.EllipsizeConfiguration
import com.infomaniak.mail.ui.main.thread.SubjectFormatter.TagColor
import com.infomaniak.mail.ui.main.thread.ThreadFragment.HeaderState
import com.infomaniak.mail.ui.newMessage.NewMessageViewModel.UiRecipients
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.ApiErrorException
import com.infomaniak.mail.utils.JsoupParserUtil.jsoupParseWithLog
import com.infomaniak.mail.utils.UiUtils.animateColorChange
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.TAG_SEPARATOR
import com.infomaniak.mail.utils.Utils.isPermanentDeleteFolder
import com.infomaniak.mail.utils.Utils.kSyncAccountUri
import com.infomaniak.mail.utils.WebViewUtils
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.toRealmList
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import org.jsoup.nodes.Document
import java.util.Calendar
import java.util.Date
import java.util.Scanner
import kotlin.math.roundToInt

const val IK_FOLDER = ".ik"

//region Type alias
// Explanation of this Map: Map<Email, Map<Name, MergedContact>>
typealias MergedContactDictionary = Map<String, Map<String, MergedContact>>
//endregion

fun Fragment.notYetImplemented(anchor: View? = null) = showSnackbar(getString(R.string.workInProgressTitle), anchor)

fun Activity.notYetImplemented(anchor: View? = null) = showSnackbar(getString(R.string.workInProgressTitle), anchor)

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun String.removeLineBreaksFromHtml(): Document = jsoupParseWithLog(replace("\r", "").replace("\n", ""))

fun String.htmlToText(): String = removeLineBreaksFromHtml().wholeText()

fun String?.getStartAndEndOfPlusEmail(): Pair<String, String> {
    val splittedEmail = this?.split("@")
    val fromStartToPlus = splittedEmail?.first() + "+"
    val fromArobaseToEnd = "@" + splittedEmail?.last()
    return fromStartToPlus to fromArobaseToEnd
}

//region Date
fun RealmInstant.toDate(): Date = Date(epochSeconds * 1_000L + nanosecondsOfSecond / 1_000L)

fun Date.toRealmInstant(): RealmInstant {
    val seconds = time / 1_000L
    val nanoseconds = (time - seconds * 1_000L).toInt()
    return RealmInstant.from(seconds, nanoseconds)
}

fun Date.isSmallerThanDays(daysAgo: Int): Boolean {
    val lastDay = Calendar.getInstance().apply {
        add(Calendar.DATE, -daysAgo)
    }.time.startOfTheDay()
    return lastDay <= this
}

fun Date.isLastWeek(): Boolean {
    val lastWeek = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, -1) }.time
    return this in lastWeek.startOfTheWeek()..lastWeek.endOfTheWeek()
}
//endregion

//region UI
fun Context.isInPortrait(): Boolean = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

// If height in Landscape is too small to correctly display Tablet Mode, even in landscape.
fun Fragment.isPhone(): Boolean = requireContext().resources.getBoolean(R.bool.isPhone)
// If screen is big enough to display Tablet Mode, but currently in portrait, so currently not big enough.
fun Fragment.isTabletInPortrait(): Boolean = requireContext().resources.getBoolean(R.bool.isTabletInPortrait)
// Screen is big enough, and in landscape, so we can finally display Tablet Mode! o/
fun Fragment.isTabletInLandscape(): Boolean = requireContext().resources.getBoolean(R.bool.isTabletInLandscape)

fun View.toggleChevron(
    isCollapsed: Boolean,
    collapsedAngle: Float? = null,
    expandedAngle: Float? = null,
    duration: Long = 300L,
) {
    val angle = if (isCollapsed) {
        collapsedAngle ?: ResourcesCompat.getFloat(context.resources, R.dimen.angleViewNotRotated)
    } else {
        expandedAngle ?: ResourcesCompat.getFloat(context.resources, R.dimen.angleViewRotated)
    }
    animate().rotation(angle).setDuration(duration).start()
}

fun Context.getAttributeColor(@AttrRes attribute: Int): Int {
    return MaterialColors.getColor(this, attribute, "Attribute color can't be resolved")
}

fun Context.formatSubject(subject: String?): String {
    return if (subject.isNullOrBlank()) {
        getString(R.string.noSubjectTitle)
    } else {
        subject.replace("\n+".toRegex(), " ")
    }
}

fun Context.readRawResource(@RawRes resId: Int): String = Scanner(resources.openRawResource(resId)).useDelimiter("\\A").next()

fun Context.loadCss(@RawRes cssResId: Int, customColors: List<Pair<String, Int>> = emptyList()): String {
    var css = readRawResource(cssResId)

    if (customColors.isNotEmpty()) {
        var header = ":root {\n"
        customColors.forEach { (variableName, color) ->
            header += formatCssVariable(variableName, color)
        }
        header += "}\n\n"

        css = header + css
    }

    return css
}

private fun formatCssVariable(variableName: String, color: Int): String {
    val formattedColor = Utils.colorToHexRepresentation(color)
    return "$variableName: $formattedColor;\n"
}

fun LottieAnimationView.repeatFrame(firstFrame: Int, lastFrame: Int) {
    addAnimatorListener(object : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) = Unit

        override fun onAnimationEnd(animation: Animator) {
            removeAllAnimatorListeners()
            repeatCount = ValueAnimator.INFINITE
            setMinAndMaxFrame(firstFrame, lastFrame)
            playAnimation()
        }

        override fun onAnimationCancel(animation: Animator) = Unit

        override fun onAnimationRepeat(animation: Animator) = Unit
    })
}

fun LottieAnimationView.changePathColor(illuColors: IlluColors) {
    addValueCallback(illuColors.keyPath, LottieProperty.COLOR_FILTER) {
        SimpleColorFilter(illuColors.color)
    }
}

fun WebView.initWebViewClientAndBridge(
    attachments: List<Attachment>,
    messageUid: String,
    shouldLoadDistantResources: Boolean,
    onBlockedResourcesDetected: (() -> Unit)? = null,
    navigateToNewMessageActivity: ((Uri) -> Unit)?,
    onPageFinished: (() -> Unit)? = null,
    onWebViewFinishedLoading: (() -> Unit)? = null,
): MessageWebViewClient {

    WebViewUtils.initJavascriptBridge(onWebViewFinishedLoading)
    addJavascriptInterface(WebViewUtils.jsBridge, "kmail")

    val cidDictionary = mutableMapOf<String, Attachment>().apply {
        attachments.forEach {
            it.contentId?.let { cid ->
                if (cid.isNotBlank()) this[cid] = it
            }
        }
    }

    return MessageWebViewClient(
        context,
        cidDictionary,
        messageUid,
        shouldLoadDistantResources,
        onBlockedResourcesDetected,
        navigateToNewMessageActivity,
        onPageFinished,
    ).also {
        webViewClient = it
    }
}
//endregion

//region API
inline fun <reified T> ApiResponse<T>.throwErrorAsException(): Nothing = throw getApiException()

inline fun <reified T> ApiResponse<T>.getApiException(): Exception {
    return error?.exception ?: ApiErrorException(ApiController.json.encodeToString(this))
}

fun List<ApiResponse<*>>.atLeastOneSucceeded(): Boolean = any { it.isSuccess() }

fun List<ApiResponse<*>>.allFailed(): Boolean = none { it.isSuccess() }
//endregion

//region Realm
suspend inline fun <reified T : RealmObject> Realm.update(items: List<RealmObject>) {
    write { update<T>(items) }
}

inline fun <reified T : RealmObject> MutableRealm.update(items: List<RealmObject>) {
    delete(query<T>())
    copyListToRealm(items)
}

// There is currently no way to insert multiple objects in one call (https://github.com/realm/realm-kotlin/issues/938)
fun MutableRealm.copyListToRealm(items: List<RealmObject>, alsoCopyManagedItems: Boolean = true) {
    items.forEach { if (alsoCopyManagedItems || !it.isManaged()) copyToRealm(it, UpdatePolicy.ALL) }
}

inline fun <reified T> RealmList<T>.replaceContent(list: List<T>) {
    clear()
    addAll(list.toRealmList())
}
//endregion

//region LiveData
fun <T> LiveData<T?>.observeNotNull(owner: LifecycleOwner, observer: (t: T) -> Unit) {
    observe(owner) { it?.let(observer) }
}

fun <T> LiveData<List<T>>.valueOrEmpty(): List<T> = value ?: emptyList()

@JvmName("valueOrEmptyForUiRecipients")
fun LiveData<UiRecipients>.valueOrEmpty(): List<Recipient> = value?.recipients ?: emptyList()
//endregion

//region Mailboxes
fun List<Signature>.getDefault(draftMode: DraftMode? = null): Signature? {
    return firstOrNull {
        if (draftMode == DraftMode.REPLY || draftMode == DraftMode.REPLY_ALL) it.isDefaultReply else it.isDefault
    }
}
//endregion

//region Folders
fun List<Folder>.flattenFolderChildrenAndRemoveMessages(dismissHiddenChildren: Boolean = false): List<Folder> {

    if (isEmpty()) return this

    return formatFolderWithAllChildren(dismissHiddenChildren, toMutableList())
}

private tailrec fun formatFolderWithAllChildren(
    dismissHiddenChildren: Boolean,
    inputList: MutableList<Folder>,
    outputList: MutableList<Folder> = mutableListOf(),
): List<Folder> {

    val folder = inputList.removeAt(0)

    /*
    * There are two types of folders:
    * - user's folders (with or without a role)
    * - hidden IK folders (ScheduledDrafts, Snoozed, etc…)
    *
    * We want to display the user's folders, and also the IK folders for which we handle the role.
    * IK folders where we don't handle the role are dismissed.
    */
    fun shouldThisFolderBeAdded(): Boolean = folder.path.startsWith(IK_FOLDER).not() || folder.role != null

    val children = if (folder.isManaged()) {
        if (shouldThisFolderBeAdded()) outputList.add(folder.copyFromRealm(depth = 1u))

        with(folder.children) {
            (if (dismissHiddenChildren) query("${Folder::isHidden.name} == false") else query()).sortFolders().find()
        }
    } else {
        if (shouldThisFolderBeAdded()) outputList.add(folder)

        (if (dismissHiddenChildren) folder.children.filter { !it.isHidden } else folder.children).sortFolders()
    }

    inputList.addAll(index = 0, children)

    return if (inputList.isEmpty()) outputList else formatFolderWithAllChildren(dismissHiddenChildren, inputList, outputList)
}

/**
 * These 2 `sortFolders()` functions should always implement the same sort logic.
 */
fun RealmQuery<Folder>.sortFolders() = sort(Folder::sortedName.name, Sort.ASCENDING)
    .sort(Folder::isFavorite.name, Sort.DESCENDING)
    .sort(Folder::roleOrder.name, Sort.DESCENDING)

/**
 * These 2 `sortFolders()` functions should always implement the same sort logic.
 */
fun List<Folder>.sortFolders() = sortedBy { it.sortedName }
    .sortedByDescending { it.isFavorite }
    .sortedByDescending { it.roleOrder }

fun List<Folder>.addDividerBeforeFirstCustomFolder(dividerType: Any): List<Any> {
    val folders = this
    var needsToAddDivider = true
    return buildList {
        folders.forEach { folder ->
            if (needsToAddDivider && folder.isRootAndCustom) {
                needsToAddDivider = false
                add(dividerType)
            }
            add(folder)
        }
    }
}
//endregion

//region Messages
fun List<Message>.getFoldersIds(exception: String? = null): ImpactedFolders {
    val impactedFolders = ImpactedFolders()

    for (message in this) {
        if (message.folderId == exception) continue

        impactedFolders += message.folderId
        if (message.snoozeState == SnoozeState.Snoozed) impactedFolders += FolderRole.SNOOZED
    }

    return impactedFolders
}

fun List<Message>.getUids(): List<String> = map { it.uid }
//endregion

fun DescriptionAlertDialog.deleteWithConfirmationPopup(
    folderRole: FolderRole?,
    count: Int,
    displayLoader: Boolean = true,
    onCancel: (() -> Unit)? = null,
    callback: () -> Unit,
) = if (isPermanentDeleteFolder(folderRole) && folderRole != FolderRole.DRAFT) { // We don't want to display the popup for Drafts
    showDeletePermanentlyDialog(count, displayLoader, callback, onCancel)
} else {
    callback()
}

fun DragDropSwipeRecyclerView.addStickyDateDecoration(adapter: ThreadListAdapter, threadDensity: ThreadDensity) {
    addItemDecoration(HeaderItemDecoration(this, false) { position ->
        return@HeaderItemDecoration position >= 0 && adapter.dataSet[position] is String
    })
    if (threadDensity == ThreadDensity.NORMAL) addItemDecoration(DateSeparatorItemDecoration())
}

fun Context.getLocalizedNameOrAllFolders(folder: Folder?): String {
    return folder?.getLocalizedName(context = this) ?: getString(R.string.searchFilterFolder)
}

fun Context.getInfomaniakLogin() = InfomaniakLogin(
    context = this,
    appUID = BuildConfig.APPLICATION_ID,
    clientID = BuildConfig.CLIENT_ID,
    accessType = null,
)

fun Fragment.copyRecipientEmailToClipboard(recipient: Recipient, snackbarManager: SnackbarManager) {
    requireContext().copyStringToClipboard(recipient.email, R.string.snackbarEmailCopiedToClipboard, snackbarManager)
}

fun Context.copyStringToClipboard(value: String, @StringRes snackbarTitle: Int, snackbarManager: SnackbarManager) {
    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText(value, value))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) snackbarManager.setValue(getString(snackbarTitle))
}

fun Context.shareString(value: String) {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, value)
    }

    safeStartActivity(Intent.createChooser(intent, null))
}

fun Context.safeStartActivity(intent: Intent) {
    runCatching {
        startActivity(intent)
    }.onFailure {
        showToast(R.string.startActivityCantHandleAction)
    }
}

inline infix fun <reified E : Enum<E>, V> ((E) -> V).enumValueFrom(value: V): E? {
    return enumValues<E>().firstOrNull { this(it) == value }
}

fun View.isAtTheTop(): Boolean = !canScrollVertically(-1)

fun Fragment.changeToolbarColorOnScroll(
    toolbar: MaterialToolbar,
    nestedScrollView: NestedScrollView,
    @ColorRes loweredColor: Int = R.color.toolbarLoweredColor,
    @ColorRes elevatedColor: Int = R.color.toolbarElevatedColor,
    shouldUpdateStatusBar: (() -> Boolean) = { true },
    otherUpdates: ((color: Int) -> Unit)? = null,
) {
    var valueAnimator: ValueAnimator? = null
    var oldColor = requireContext().getColor(loweredColor)
    var headerColorState = HeaderState.LOWERED

    viewLifecycleOwner.lifecycle.addObserver(
        LifecycleEventObserver { _, event -> if (event == Event.ON_DESTROY) valueAnimator?.cancel() },
    )

    nestedScrollView.setOnScrollChangeListener { view, _, _, _, _ ->
        val isAtTheTop = view.isAtTheTop()
        if (!isAtTheTop && headerColorState == HeaderState.ELEVATED) return@setOnScrollChangeListener

        headerColorState = if (isAtTheTop) HeaderState.LOWERED else HeaderState.ELEVATED

        val newColor = view.context.getColor(if (isAtTheTop) loweredColor else elevatedColor)
        if (newColor == oldColor) return@setOnScrollChangeListener

        valueAnimator?.cancel()
        valueAnimator = animateColorChange(oldColor, newColor, animate = true) { color ->
            oldColor = color
            toolbar.setBackgroundColor(color)
            if (shouldUpdateStatusBar()) requireActivity().window.statusBarColor = color
            otherUpdates?.invoke(color)
        }
    }
}

fun Fragment.setSystemBarsColors(
    @ColorRes statusBarColor: Int? = R.color.backgroundHeaderColor,
    @ColorRes navigationBarColor: Int? = R.color.backgroundColor,
) {
    statusBarColor?.let(requireContext()::getColor)?.let { requireActivity().window.statusBarColor = it }
    navigationBarColor?.let(requireContext()::getColor)?.let(requireActivity().window::updateNavigationBarColor)
}

fun Window.updateNavigationBarColor(@ColorInt color: Int) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) navigationBarColor = color
}

fun Activity.getMainApplication() = (application as MainApplication)

fun Fragment.getStringWithBoldArg(@StringRes resId: Int, arg: String): Spanned {
    val textColor = context?.getColor(R.color.primaryTextColor)?.let(Utils::colorToHexRepresentation)
    val coloredArg = textColor?.let { "<font color=\"$it\">$arg</font color>" } ?: arg

    return Html.fromHtml(getString(resId, "<b>$coloredArg</b>"), Html.FROM_HTML_MODE_LEGACY)
}

fun Context.isUserAlreadySynchronized(): Boolean {
    val uri = kSyncAccountUri(AccountUtils.currentUser!!.login)
    return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        cursor.count > 0
    } ?: false
}

fun TextInputLayout.setOnClearTextClickListener(trackerCallback: () -> Unit) {
    setEndIconOnClickListener {
        editText?.text?.clear()
        trackerCallback()
    }
}

fun TextInputEditText.handleEditorSearchAction(searchCallback: (String) -> Unit) {
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_SEARCH && !text.isNullOrBlank()) {
            searchCallback(text.toString())
            hideKeyboard()
        }
        true // Action got consumed
    }
}

fun CharSequence.standardize(): String = toString().removeAccents().trim().lowercase()

inline val AndroidViewModel.appContext: Context get() = getApplication()

val TextInputEditText.trimmedText inline get() = text?.trim().toString()

fun Context.postfixWithTag(
    original: CharSequence,
    @StringRes tagRes: Int,
    tagColor: TagColor,
    ellipsizeConfiguration: EllipsizeConfiguration? = null,
    onClicked: (() -> Unit)? = null,
) = postfixWithTag(
    original = original,
    tag = getString(tagRes),
    tagColor = tagColor,
    ellipsizeConfiguration = ellipsizeConfiguration,
    onClicked = onClicked,
)

/**
 * Do not forget to set `movementMethod = LinkMovementMethod.getInstance()` on a TextView to make the tag clickable
 */
fun Context.postfixWithTag(
    original: CharSequence = "",
    tag: String,
    tagColor: TagColor,
    ellipsizeConfiguration: EllipsizeConfiguration? = null,
    onClicked: (() -> Unit)? = null,
): Spannable {

    fun getEllipsizedTagContent(): String {
        return ellipsizeConfiguration?.let {
            TextUtils.ellipsize(
                tag,
                getTagsPaint(this),
                ellipsizeConfiguration.maxWidth,
                ellipsizeConfiguration.truncateAt,
            ).toString()
        } ?: tag
    }

    val ellipsizedTagContent = getEllipsizedTagContent()
    val postFixed = TextUtils.concat(original, TAG_SEPARATOR, ellipsizedTagContent)

    return postFixed.toSpannable().apply {
        val startIndex = original.length + TAG_SEPARATOR.length
        val endIndex = startIndex + ellipsizedTagContent.length

        with(tagColor) {
            setTagSpan(this@postfixWithTag, startIndex, endIndex, backgroundColorRes, textColorRes)
        }
        onClicked?.let { setClickableSpan(startIndex, endIndex, it) }
    }
}

private fun Spannable.setTagSpan(
    context: Context,
    startIndex: Int,
    endIndex: Int,
    @ColorRes backgroundColorRes: Int,
    @ColorRes textColorRes: Int,
) {
    val backgroundColor = context.getColor(backgroundColorRes)
    val textColor = context.getColor(textColorRes)
    val textTypeface = ResourcesCompat.getFont(context, R.font.tag_font)!!
    val textSize = context.resources.getDimensionPixelSize(R.dimen.tagTextSize).toFloat()
    setSpan(
        RoundedBackgroundSpan(
            backgroundColor = backgroundColor,
            textColor = textColor,
            textTypeface = textTypeface,
            fontSize = textSize,
            cornerRadius = context.resources.getDimension(R.dimen.tagRadius),
        ),
        startIndex,
        endIndex,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
}

private fun Spannable.setClickableSpan(startIndex: Int, endIndex: Int, onClick: () -> Unit) {
    // TODO: Currently, the clickable zone extends beyond the span up to the edge of the textview.
    //  This is the same comportment that Gmail has.
    //  See if we can find a fix for this later.
    setSpan(
        object : ClickableSpan() {
            override fun onClick(widget: View) = onClick()
        },
        startIndex,
        endIndex,
        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
}

fun Fragment.bindAlertToViewLifecycle(alertDialog: BaseAlertDialog) {
    alertDialog.bindAlertToLifecycle(viewLifecycleOwner)
}

/**
 * Asynchronously validate folder name locally
 * @return error string, otherwise null
 */
private val invalidCharactersRegex by lazy { Regex("[/'\"]") }
fun Context.getFolderCreationError(folderName: CharSequence, folderController: FolderController): String? {
    return when {
        folderName.length > 255 -> getString(R.string.errorNewFolderNameTooLong)
        folderName.contains(invalidCharactersRegex) -> getString(R.string.errorNewFolderInvalidCharacter)
        folderController.getRootFolder(folderName.toString()) != null -> getString(R.string.errorNewFolderAlreadyExists)
        else -> null
    }
}

fun Context.getTransparentColor() = getColor(android.R.color.transparent)

fun TypedArray.getColorOrNull(index: Int): Int? = runCatching { getColorOrThrow(index) }.getOrNull()

fun ShapeableImageView.setInnerStrokeWidth(strokeWidth: Float) {
    this.strokeWidth = strokeWidth
    val halfStrokeWidth = (strokeWidth / 2.0f).roundToInt()
    setPaddingRelative(halfStrokeWidth, halfStrokeWidth, halfStrokeWidth, halfStrokeWidth)
}

fun <T : Correspondent> List<T>.findUser(): T? = firstOrNull(Correspondent::isMe)

fun List<Correspondent>.isUserIn(): Boolean = findUser() != null

fun ViewPager2.removeOverScrollForApiBelow31() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        (getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
    }
}

fun <T> List<T>.indexOfFirstOrNull(predicate: (T) -> Boolean): Int? {
    val index = indexOfFirst(predicate)
    return if (index == -1) null else index
}

fun WebView.enableAlgorithmicDarkening(isEnabled: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isEnabled)
    }
}

fun Data.getLongOrNull(key: String) = getLong(key, 0L).run { if (this == 0L) null else this }
