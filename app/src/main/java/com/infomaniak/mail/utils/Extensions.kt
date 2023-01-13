/*
 * Infomaniak kMail - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.navigation.NavDirections
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import com.infomaniak.lib.core.api.ApiController
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.draft.Draft.DraftMode
import com.infomaniak.mail.ui.main.newMessage.NewMessageActivityArgs
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.query
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.sentry.Sentry
import kotlinx.serialization.encodeToString
import java.util.*

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

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

inline val ViewBinding.context: Context get() = root.context

fun <T> LiveData<T?>.observeNotNull(owner: LifecycleOwner, observer: (t: T) -> Unit) {
    observe(owner) { it?.let(observer) }
}

fun Context.getAttributeColor(attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)
    return typedValue.data
}

fun Fragment.notYetImplemented() = showSnackbar("This feature is currently under development.")

fun Activity.notYetImplemented() = showSnackbar("This feature is currently under development.")

fun Fragment.animatedNavigation(directions: NavDirections, currentClassName: String? = null) {
    if (canNavigate(currentClassName)) findNavController().navigate(directions, getAnimatedNavOptions())
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

fun Uri.getFileNameAndSize(context: Context): Pair<String, Int>? {
    return runCatching {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        context.contentResolver.query(this, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayName = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME).let(cursor::getString)
                val size = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE).let(cursor::getInt)
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

inline fun <reified T> ApiResponse<T>.throwErrorAsException() {
    throw error?.exception ?: Exception(ApiController.json.encodeToString(this))
}

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

//region WorkManager
fun OneTimeWorkRequest.Builder.setExpeditedWorkRequest(): OneTimeWorkRequest.Builder {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    return this
}
//endregion
