/*
 * Infomaniak Mail - Android
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
package com.infomaniak.mail.utils

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.Spanned
import android.util.Patterns
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import androidx.annotation.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.*
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.github.razir.progressbutton.DrawableButton.Companion.GRAVITY_CENTER
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.login.InfomaniakLogin
import com.infomaniak.mail.BuildConfig
import com.infomaniak.mail.MainApplication
import com.infomaniak.mail.MatomoMail.OPEN_FROM_DRAFT_NAME
import com.infomaniak.mail.MatomoMail.trackNewMessageEvent
import com.infomaniak.mail.R
import com.infomaniak.mail.data.LocalSettings.ThreadDensity
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.Folder.*
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.data.models.correspondent.Recipient
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.DialogDescriptionBinding
import com.infomaniak.mail.databinding.DialogInputBinding
import com.infomaniak.mail.ui.MainActivity
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.login.IlluColors.IlluColors
import com.infomaniak.mail.ui.login.LoginActivity
import com.infomaniak.mail.ui.login.LoginActivityArgs
import com.infomaniak.mail.ui.login.NoMailboxActivity
import com.infomaniak.mail.ui.main.folder.DateSeparatorItemDecoration
import com.infomaniak.mail.ui.main.folder.HeaderItemDecoration
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.ui.main.thread.MessageWebViewClient
import com.infomaniak.mail.ui.main.thread.ThreadFragment
import com.infomaniak.mail.ui.main.thread.ThreadFragmentArgs
import com.infomaniak.mail.ui.noValidMailboxes.NoValidMailboxesActivity
import com.infomaniak.mail.utils.Utils.runCatchingRealm
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.copyFromRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.jsoup.Jsoup
import java.util.Calendar
import java.util.Date
import java.util.Scanner
import com.infomaniak.lib.core.R as RCore

//region Type alias
typealias MergedContactDictionary = Map<String, Map<String, MergedContact>>
//endregion

fun Fragment.notYetImplemented(anchor: View? = null) = showSnackbar(getString(R.string.workInProgressTitle), anchor)

fun Activity.notYetImplemented(anchor: View? = null) = showSnackbar(getString(R.string.workInProgressTitle), anchor)

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun String.htmlToText(): String = Jsoup.parse(replace("\r", "").replace("\n", "")).wholeText()

fun String.textToHtml(): String = replace("\n", "<br>")

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
): MessageWebViewClient {

    addJavascriptInterface(WebViewUtils.jsBridge, "kmail")

    val cidDictionary = mutableMapOf<String, Attachment>().apply {
        attachments.forEach {
            if (it.contentId?.isNotBlank() == true) this[it.contentId as String] = it
        }
    }

    return MessageWebViewClient(
        context,
        cidDictionary,
        messageUid,
        shouldLoadDistantResources,
        onBlockedResourcesDetected,
        navigateToNewMessageActivity,
    ).also {
        webViewClient = it
    }
}
//endregion

//region Navigation
fun Fragment.animatedNavigation(directions: NavDirections, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) findNavController().navigate(directions, getAnimatedNavOptions())
}

fun Fragment.animatedNavigation(@IdRes resId: Int, args: Bundle? = null, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) findNavController().navigate(resId, args, getAnimatedNavOptions())
}

fun getAnimatedNavOptions() = NavOptions
    .Builder()
    .setEnterAnim(R.anim.fragment_swipe_enter)
    .setExitAnim(R.anim.fragment_swipe_exit)
    .setPopEnterAnim(R.anim.fragment_swipe_pop_enter)
    .setPopExitAnim(R.anim.fragment_swipe_pop_exit)
    .build()

fun Fragment.safeNavigateToNewMessageActivity(
    draftMode: DraftMode,
    previousMessageUid: String,
    currentClassName: String? = null,
    shouldLoadDistantResources: Boolean = false,
) {
    safeNavigateToNewMessageActivity(
        args = NewMessageActivityArgs(
            arrivedFromExistingDraft = false,
            draftMode = draftMode,
            previousMessageUid = previousMessageUid,
            shouldLoadDistantResources = shouldLoadDistantResources,
        ).toBundle(),
        currentClassName = currentClassName,
    )
}

fun Fragment.safeNavigateToNewMessageActivity(args: Bundle? = null, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) (activity as MainActivity).navigateToNewMessageActivity(args)
}

fun Fragment.navigateToThread(thread: Thread, mainViewModel: MainViewModel) = runCatchingRealm {
    if (thread.isOnlyOneDraft) { // Directly go to NewMessage screen
        trackNewMessageEvent(OPEN_FROM_DRAFT_NAME)
        mainViewModel.navigateToSelectedDraft(thread.messages.first()).observe(viewLifecycleOwner) {
            safeNavigateToNewMessageActivity(
                NewMessageActivityArgs(
                    arrivedFromExistingDraft = true,
                    draftLocalUuid = it.draftLocalUuid,
                    draftResource = it.draftResource,
                    messageUid = it.messageUid,
                ).toBundle(),
            )
        }
    } else {
        safeNavigate(R.id.threadFragment, ThreadFragmentArgs(thread.uid).toBundle())
    }
}.getOrDefault(Unit)
//endregion

//region API
inline fun <reified T> ApiResponse<T>.throwErrorAsException(): Nothing = throw getApiException()

inline fun <reified T> ApiResponse<T>.getApiException(): Exception {
    return error?.exception ?: ApiErrorException(ApiController.json.encodeToString(this))
}

fun String.toLongUid(folderId: String) = "${this}@${folderId}"

fun String.toShortUid(): Int = substringBefore('@').toInt()
//endregion

//region Realm
inline fun <reified T : RealmObject> Realm.update(items: List<RealmObject>) {
    writeBlocking { update<T>(items) }
}

inline fun <reified T : RealmObject> MutableRealm.update(items: List<RealmObject>) {
    delete(query<T>())
    copyListToRealm(items)
}

// There is currently no way to insert multiple objects in one call (https://github.com/realm/realm-kotlin/issues/938)
fun MutableRealm.copyListToRealm(items: List<RealmObject>, alsoCopyManagedItems: Boolean = true) {
    items.forEach { if (alsoCopyManagedItems || !it.isManaged()) copyToRealm(it, UpdatePolicy.ALL) }
}
//endregion

//region LiveData
fun <T> LiveData<T?>.observeNotNull(owner: LifecycleOwner, observer: (t: T) -> Unit) {
    observe(owner) { it?.let(observer) }
}
//endregion

//region Folders
fun List<Folder>.getMenuFolders(): Pair<List<Folder>, List<Folder>> {
    return toMutableList().let { list ->

        val defaultFolders = list
            .filter { it.role != null }
            .sortedBy { it.role?.order }
            .flatMapFolderChildren()
            .also(list::removeAll)

        val customFolders = list.flatMapFolderChildren()

        defaultFolders to customFolders
    }
}

fun List<Folder>.getDefaultMenuFolders(): List<Folder> {
    return sortedBy { it.role?.order }.flatMapFolderChildren()
}

fun List<Folder>.getCustomMenuFolders(dismissHiddenChildren: Boolean = false): List<Folder> {
    return flatMapFolderChildren(dismissHiddenChildren)
}

fun List<Folder>.flatMapFolderChildren(dismissHiddenChildren: Boolean = false): List<Folder> {

    if (isEmpty()) return this

    tailrec fun formatFolderWithAllChildren(
        inputList: MutableList<Folder>,
        outputList: MutableList<Folder> = mutableListOf(),
    ): List<Folder> {

        val folder = inputList.removeFirst()

        if (folder.isManaged()) {
            outputList.add(folder.copyFromRealm(1u))
            val children = with(folder.children) {
                (if (dismissHiddenChildren) query("${Folder::isHidden.name} == false") else query())
                    .sort(Folder::name.name, Sort.ASCENDING)
                    .find()
            }
            inputList.addAll(0, children)
        } else {
            outputList.add(folder)
            val children = with(folder.children) { if (dismissHiddenChildren) filter { !it.isHidden } else this }
            inputList.addAll(children)
        }

        return if (inputList.isEmpty()) outputList else formatFolderWithAllChildren(inputList, outputList)
    }

    return formatFolderWithAllChildren(toMutableList())
}
//endregion

//region Messages
fun List<Message>.getFoldersIds(exception: String? = null) = mapNotNull { if (it.folderId == exception) null else it.folderId }

fun List<Message>.getUids(): List<String> = map { it.uid }
//endregion

fun Fragment.deleteWithConfirmationPopup(folderRole: FolderRole?, count: Int, callback: () -> Unit) {
    when (folderRole) {
        FolderRole.DRAFT, FolderRole.SPAM, FolderRole.TRASH -> {
            createDescriptionDialog(
                title = resources.getQuantityString(R.plurals.threadListDeletionConfirmationAlertTitle, count, count),
                description = resources.getQuantityString(R.plurals.threadListDeletionConfirmationAlertDescription, count),
                onPositiveButtonClicked = callback,
            ).show()
        }
        else -> callback()
    }
}

fun Fragment.createInformationDialog(
    title: String,
    description: CharSequence,
    @StringRes confirmButtonText: Int,
): AlertDialog = requireActivity().createInformationDialog(title, description, confirmButtonText)

fun Activity.createInformationDialog(
    title: String,
    description: CharSequence,
    @StringRes confirmButtonText: Int,
): AlertDialog = createDescriptionDialog(
    title = title,
    description = description,
    confirmButtonText = confirmButtonText,
    displayCancelButton = false,
    onPositiveButtonClicked = {},
)

fun Fragment.createDescriptionDialog(
    title: String,
    description: CharSequence,
    @StringRes confirmButtonText: Int = R.string.buttonConfirm,
    displayCancelButton: Boolean = true,
    onPositiveButtonClicked: () -> Unit,
    onDismissed: (() -> Unit)? = null,
): AlertDialog = requireActivity().createDescriptionDialog(
    title,
    description,
    confirmButtonText,
    displayCancelButton,
    onPositiveButtonClicked,
    onDismissed,
)

fun Activity.createDescriptionDialog(
    title: String,
    description: CharSequence,
    @StringRes confirmButtonText: Int = R.string.buttonConfirm,
    displayCancelButton: Boolean = true,
    onPositiveButtonClicked: () -> Unit,
    onDismissed: (() -> Unit)? = null,
) = with(DialogDescriptionBinding.inflate(layoutInflater)) {

    dialogTitle.text = title
    dialogDescription.text = description

    MaterialAlertDialogBuilder(context)
        .setView(root)
        .setPositiveButton(confirmButtonText) { _, _ -> onPositiveButtonClicked() }
        .also { if (displayCancelButton) it.setNegativeButton(RCore.string.buttonCancel, null) }
        .setOnDismissListener { onDismissed?.invoke() }
        .create()
}

fun Fragment.createInputDialog(
    @StringRes title: Int,
    @StringRes hint: Int,
    @StringRes confirmButtonText: Int,
    onErrorCheck: suspend (CharSequence) -> String?,
    onPositiveButtonClicked: (String) -> Unit,
) = with(DialogInputBinding.inflate(layoutInflater)) {

    var errorJob: Job? = null

    fun Button.checkValidation(text: CharSequence) {
        errorJob?.cancel()
        errorJob = lifecycleScope.launch(Dispatchers.IO) {
            val error = onErrorCheck(text.trim())
            if (errorJob?.isActive == true) Dispatchers.Main {
                textInputLayout.error = error
                isEnabled = error == null
            }
        }
    }

    fun AlertDialog.setupOnShowListener() = apply {
        setOnShowListener {
            showKeyboard()
            positiveButton.apply {
                setOnClickListener {
                    onPositiveButtonClicked(textInput.trimmedText)
                    hideKeyboard()
                    showProgress(gravity = GRAVITY_CENTER)
                    negativeButton.isEnabled = false
                }
                setText(confirmButtonText)
                isEnabled = false
                textInput.doAfterTextChanged {
                    if (it.isNullOrBlank()) {
                        errorJob?.cancel()
                        isEnabled = false
                        textInputLayout.error = null
                    } else {
                        checkValidation(it.trim())
                    }
                }
            }
        }
    }

    dialogTitle.setText(title)
    textInputLayout.setHint(hint)

    return@with MaterialAlertDialogBuilder(context)
        .setView(root)
        .setCancelable(false)
        .setPositiveButton(confirmButtonText, null)
        .setNegativeButton(RCore.string.buttonCancel) { dialog, _ -> dialog.dismiss() }
        .setOnDismissListener {
            errorJob?.cancel()
            textInput.text?.clear()
        }
        .create()
        .setupOnShowListener()
}

inline val AlertDialog.positiveButton get() = (getButton(DialogInterface.BUTTON_POSITIVE) as MaterialButton)
inline val AlertDialog.negativeButton get() = (getButton(DialogInterface.BUTTON_NEGATIVE) as MaterialButton)

fun DragDropSwipeRecyclerView.addStickyDateDecoration(adapter: ThreadListAdapter, threadDensity: ThreadDensity) {
    addItemDecoration(HeaderItemDecoration(this, false) { position ->
        return@HeaderItemDecoration position >= 0 && adapter.dataSet[position] is String
    })
    if (threadDensity == ThreadDensity.NORMAL) addItemDecoration(DateSeparatorItemDecoration())
}

fun Context.getLocalizedNameOrAllFolders(folder: Folder?): String {
    return folder?.getLocalizedName(context = this) ?: getString(R.string.searchFilterFolder)
}

fun Context.getInfomaniakLogin(): InfomaniakLogin {
    return InfomaniakLogin(context = this, appUID = BuildConfig.APPLICATION_ID, clientID = BuildConfig.CLIENT_ID)
}

fun Window.updateNavigationBarColor(color: Int) {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) navigationBarColor = color
}

fun Fragment.copyRecipientEmailToClipboard(recipient: Recipient, snackBarAnchor: View? = null) = with(recipient) {
    val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText(email, email))

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        showSnackbar(R.string.snackbarEmailCopiedToClipboard, anchor = snackBarAnchor)
    }
}

inline infix fun <reified E : Enum<E>, V> ((E) -> V).enumValueFrom(value: V): E? {
    return enumValues<E>().firstOrNull { this(it) == value }
}

fun Fragment.changeToolbarColorOnScroll(
    toolbar: MaterialToolbar,
    nestedScrollView: NestedScrollView,
    @ColorRes loweredColor: Int = R.color.toolbarLoweredColor,
    @ColorRes elevatedColor: Int = R.color.toolbarElevatedColor,
    otherUpdates: ((color: Int) -> Unit)? = null,
) {
    requireContext().changeToolbarColorOnScroll(
        toolbar,
        nestedScrollView,
        loweredColor,
        elevatedColor,
        otherUpdates,
        viewLifecycleOwner.lifecycle,
        requireActivity(),
    )
}

fun FragmentActivity.changeToolbarColorOnScroll(
    toolbar: MaterialToolbar,
    nestedScrollView: NestedScrollView,
    @ColorRes loweredColor: Int = R.color.toolbarLoweredColor,
    @ColorRes elevatedColor: Int = R.color.toolbarElevatedColor,
    otherUpdates: ((color: Int) -> Unit)? = null,
) {
    changeToolbarColorOnScroll(
        toolbar,
        nestedScrollView,
        loweredColor,
        elevatedColor,
        otherUpdates,
        lifecycle,
        this,
    )
}

private fun Context.changeToolbarColorOnScroll(
    toolbar: MaterialToolbar,
    nestedScrollView: NestedScrollView,
    @ColorRes loweredColor: Int,
    @ColorRes elevatedColor: Int,
    otherUpdates: ((color: Int) -> Unit)?,
    lifecycle: Lifecycle,
    activity: Activity,
) {
    var valueAnimator: ValueAnimator? = null
    var oldColor = getColor(loweredColor)

    lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            if (event == Lifecycle.Event.ON_DESTROY) valueAnimator?.cancel()
        }
    })

    var headerColorState = ThreadFragment.HeaderState.LOWERED
    nestedScrollView.setOnScrollChangeListener { view, _, _, _, _ ->
        val isAtTheTop = !view.canScrollVertically(-1)
        if (headerColorState == ThreadFragment.HeaderState.ELEVATED && !isAtTheTop) return@setOnScrollChangeListener

        val newColor = view.context.getColor(if (isAtTheTop) loweredColor else elevatedColor)
        headerColorState = if (isAtTheTop) ThreadFragment.HeaderState.LOWERED else ThreadFragment.HeaderState.ELEVATED

        if (oldColor == newColor) return@setOnScrollChangeListener

        valueAnimator?.cancel()
        valueAnimator = UiUtils.animateColorChange(oldColor, newColor, animate = true) { color ->
            oldColor = color
            toolbar.setBackgroundColor(color)
            activity.window.statusBarColor = color
            otherUpdates?.invoke(color)
        }
    }
}

fun Activity.getMainApplication() = (application as MainApplication)

fun Fragment.getStringWithBoldArg(@StringRes resId: Int, arg: String): Spanned {
    val textColor = context?.getColor(R.color.primaryTextColor)?.let(Utils::colorToHexRepresentation)
    val coloredArg = textColor?.let { "<font color=\"$it\">$arg</font color>" } ?: arg

    return Html.fromHtml(getString(resId, "<b>$coloredArg</b>"), Html.FROM_HTML_MODE_LEGACY)
}

fun Context.getLoginActivityIntent(args: LoginActivityArgs? = null, shouldClearStack: Boolean = false): Intent {
    return Intent(this, LoginActivity::class.java).apply {
        if (shouldClearStack) clearStack()
        args?.toBundle()?.let(::putExtras)
    }
}

fun Context.launchLoginActivity(args: LoginActivityArgs? = null) {
    getLoginActivityIntent(args).also(::startActivity)
}

fun Context.launchNoValidMailboxesActivity() {
    Intent(this, NoValidMailboxesActivity::class.java).apply {
        clearStack()
    }.also(::startActivity)
}

fun Context.launchNoMailboxActivity(shouldStartLoginActivity: Boolean = false) {
    val noMailboxAcctivityIntent = Intent(this, NoMailboxActivity::class.java)
    val intentsArray = if (shouldStartLoginActivity) {
        arrayOf(getLoginActivityIntent(shouldClearStack = true), noMailboxAcctivityIntent)
    } else {
        arrayOf(noMailboxAcctivityIntent)
    }

    startActivities(intentsArray)
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

inline val AndroidViewModel.context: Context get() = getApplication()

val TextInputEditText.trimmedText inline get() = text?.trim().toString()
