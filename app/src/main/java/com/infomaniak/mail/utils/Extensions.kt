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
package com.infomaniak.mail.utils

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import android.widget.Button
import androidx.annotation.IdRes
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.ernestoyaquello.dragdropswiperecyclerview.DragDropSwipeRecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Folder
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.data.models.message.Message
import com.infomaniak.mail.data.models.thread.Thread
import com.infomaniak.mail.databinding.DialogDescriptionBinding
import com.infomaniak.mail.databinding.DialogInputBinding
import com.infomaniak.mail.ui.MainViewModel
import com.infomaniak.mail.ui.login.IlluColors
import com.infomaniak.mail.ui.main.folder.DateSeparatorItemDecoration
import com.infomaniak.mail.ui.main.folder.HeaderItemDecoration
import com.infomaniak.mail.ui.main.folder.ThreadListAdapter
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivityArgs
import com.infomaniak.mail.ui.main.thread.ThreadFragmentArgs
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.sentry.Sentry
import kotlinx.serialization.encodeToString
import org.jsoup.Jsoup
import java.util.Calendar
import java.util.Date
import java.util.Scanner
import com.google.android.material.R as RMaterial

fun Fragment.notYetImplemented() = showSnackbar(getString(R.string.workInProgressTitle))

fun Activity.notYetImplemented() = showSnackbar(getString(R.string.workInProgressTitle))

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

fun String.htmlToText(): String = Jsoup.parse(replace("\r", "").replace("\n", "")).wholeText()

fun String.textToHtml(): String = replace("\n", "<br>")

fun Uri.getFileNameAndSize(context: Context): Pair<String, Long>? {
    return runCatching {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(this, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
                val size = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE).let(cursor::getLong)
                displayName to size
            } else {
                Sentry.captureException(Exception("$this has empty cursor"))
                null
            }
        }
    }.getOrElse { exception ->
        Sentry.captureException(exception)
        null
    }
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
inline val ViewBinding.context: Context get() = root.context

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

fun Context.getAttributeColor(attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)
    return typedValue.data
}

fun Context.formatSubject(subject: String?): String {
    return if (subject.isNullOrBlank()) {
        getString(R.string.noSubjectTitle)
    } else {
        subject.replace("\n+".toRegex(), " ")
    }
}

fun Context.injectCssInHtml(@RawRes cssResId: Int, html: String): String {
    val css = Scanner(resources.openRawResource(cssResId)).useDelimiter("\\A").next()
    return with(Jsoup.parse(html)) {
        head().appendElement("style").attr("type", "text/css").appendText(css)
        html()
    }
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

fun LottieAnimationView.changePathColor(illuColors: IlluColors, isDark: Boolean) {
    val color = if (isDark) illuColors.getDarkColor() else illuColors.getLightColor()
    addValueCallback(illuColors.keyPath, LottieProperty.COLOR_FILTER) {
        SimpleColorFilter(color)
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

fun Fragment.safeNavigateToNewMessageActivity(draftMode: DraftMode, messageUid: String) {
    safeNavigate(
        R.id.newMessageActivity,
        NewMessageActivityArgs(
            draftExists = false,
            draftMode = draftMode,
            previousMessageUid = messageUid,
        ).toBundle(),
    )
}

fun Fragment.navigateToThread(thread: Thread, mainViewModel: MainViewModel) {
    if (thread.isOnlyOneDraft()) { // Directly go to NewMessage screen
        mainViewModel.navigateToSelectedDraft(thread.messages.first()).observe(viewLifecycleOwner) {
            safeNavigate(
                R.id.newMessageActivity,
                NewMessageActivityArgs(
                    draftExists = true,
                    draftLocalUuid = it.draftLocalUuid,
                    draftResource = it.draftResource,
                    messageUid = it.messageUid,
                ).toBundle(),
            )
        }
    } else {
        safeNavigate(R.id.threadFragment, ThreadFragmentArgs(thread.uid).toBundle())
    }
}
//endregion

//region API
inline fun <reified T> ApiResponse<T>.throwErrorAsException() {
    throw error?.exception ?: ApiErrorException(ApiController.json.encodeToString(this))
}

fun String.toLongUid(folderId: String) = "${this}@${folderId}"

fun String.toShortUid(): String = substringBefore('@')
//endregion

//region Realm
inline fun <reified T : RealmObject> Realm.update(items: List<RealmObject>) {
    writeBlocking {
        delete(query<T>())
        copyListToRealm(items)
    }
}

// TODO: There is currently no way to insert multiple objects in one call (https://github.com/realm/realm-kotlin/issues/938)
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
            .formatFoldersListWithAllChildren()
            .also(list::removeAll)

        val customFolders = list.formatFoldersListWithAllChildren()

        defaultFolders to customFolders
    }
}

/**
 * The `sortByName` for Folders is done twice in the app, but it's not factorisable.
 * So if this sort logic changes, it needs to be changed in both locations.
 * The other location is in `FolderController.getFoldersQuery()`.
 */
fun List<Folder>.formatFoldersListWithAllChildren(): List<Folder> {

    if (isEmpty()) return this

    tailrec fun formatFolderWithAllChildren(
        inputList: MutableList<Folder>,
        outputList: MutableList<Folder> = mutableListOf(),
    ): List<Folder> {

        val firstFolder = inputList.removeFirst()
        outputList.add(firstFolder)
        inputList.addAll(0, firstFolder.children.query().sort(Folder::name.name, Sort.ASCENDING).find())

        return if (inputList.isEmpty()) outputList else formatFolderWithAllChildren(inputList, outputList)
    }

    return formatFolderWithAllChildren(toMutableList())
}
//endregion

//region Messages
fun List<Message>.getFoldersIds(exception: String? = null) = mapNotNull { if (it.folderId == exception) null else it.folderId }

fun List<Message>.getUids(): List<String> = map { it.uid }
//endregion

fun Fragment.createDescriptionDialog(
    title: String,
    description: String,
    @StringRes confirmButtonText: Int = R.string.buttonConfirm,
    onPositiveButtonClicked: () -> Unit,
) = with(DialogDescriptionBinding.inflate(layoutInflater)) {

    dialogTitle.text = title
    dialogDescription.text = description

    MaterialAlertDialogBuilder(context)
        .setView(root)
        .setPositiveButton(confirmButtonText) { _, _ -> onPositiveButtonClicked() }
        .setNegativeButton(R.string.buttonCancel, null)
        .create()
}

fun Fragment.createInputDialog(
    @StringRes title: Int,
    @StringRes hint: Int,
    @StringRes confirmButtonText: Int,
    onPositiveButtonClicked: (Editable?) -> Unit,
) = with(DialogInputBinding.inflate(layoutInflater)) {

    fun Button.setButtonEnablement(isInputEmpty: Boolean) {
        isEnabled = !isInputEmpty

        val (textColor, backgroundColor) = if (isInputEmpty) {
            @Suppress("ResourceAsColor")
            R.color.disabledDialogButtonTextColor to resources.getColor(R.color.backgroundDisabledDialogButton, null)
        } else {
            R.color.colorOnPrimary to context.getAttributeColor(RMaterial.attr.colorPrimary)
        }
        setTextColor(resources.getColor(textColor, null))
        setBackgroundColor(backgroundColor)
    }

    fun AlertDialog.setupOnShowListener() = apply {
        setOnShowListener {
            showKeyboard()
            getButton(AlertDialog.BUTTON_POSITIVE).apply {
                setButtonEnablement(true)
                textInput.doAfterTextChanged { setButtonEnablement(it.isNullOrBlank()) }
            }
        }
    }

    dialogTitle.setText(title)
    textInputLayout.setHint(hint)

    return@with MaterialAlertDialogBuilder(context)
        .setView(root)
        .setPositiveButton(confirmButtonText) { _, _ -> onPositiveButtonClicked(textInput.text) }
        .setNegativeButton(R.string.buttonCancel, null)
        .setOnDismissListener { textInput.text?.clear() }
        .create()
        .setupOnShowListener()
}

fun DragDropSwipeRecyclerView.addStickyDateDecoration(adapter: ThreadListAdapter) {
    addItemDecoration(HeaderItemDecoration(this, false) { position ->
        return@HeaderItemDecoration position >= 0 && adapter.dataSet[position] is String
    })
    addItemDecoration(DateSeparatorItemDecoration())
}

fun Context.getLocalizedNameOrAllFolders(folder: Folder?): String {
    return folder?.getLocalizedName(this) ?: getString(R.string.searchFilterFolder)
}
