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

import android.content.Context
import android.util.Patterns
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IdRes
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.day
import com.infomaniak.lib.core.utils.month
import com.infomaniak.lib.core.utils.year
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Attachment
import com.infomaniak.mail.data.models.Attachment.AttachmentType
import com.infomaniak.mail.data.models.Mailbox
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.util.*

fun RealmInstant.toDate(): Date = Date(epochSeconds * 1_000L + nanosecondsOfSecond / 1_000L)

fun Date.toRealmInstant(): RealmInstant {
    val seconds = time / 1_000L
    val nanoseconds = (time - seconds * 1_000L).toInt()
    return RealmInstant.from(seconds, nanoseconds)
}

fun Date.isToday(): Boolean = Date().let { now -> year() == now.year() && month() == now.month() && day() == now.day() }

fun Date.isYesterday(): Boolean {
    val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }.time
    return year() == yesterday.year() && month() == yesterday.month() && day() == yesterday.day()
}

fun Date.isThisYear(): Boolean = Date().let { now -> year() == now.year() }

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

fun View.setMargins(left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) {
    if (layoutParams is ViewGroup.MarginLayoutParams) {
        (layoutParams as ViewGroup.MarginLayoutParams).setMargins(left, top, right, bottom)
        requestLayout()
    }
}

fun String.isEmail(): Boolean = Patterns.EMAIL_ADDRESS.matcher(this).matches()

inline val ViewBinding.context: Context get() = root.context

fun <T> Flow<T>.toSharedFlow(): SharedFlow<T> {
    return distinctUntilChanged().shareIn(
        scope = CoroutineScope(Dispatchers.IO),
        started = SharingStarted.WhileSubscribed(),
        replay = 1,
    )
}

fun <T> LiveData<T?>.observeNotNull(owner: LifecycleOwner, observer: (t: T) -> Unit) {
    observe(owner) { it?.let(observer) }
}

fun Context.getAttributeColor(@IdRes attribute: Int): Int {
    val typedValue = TypedValue()
    theme.resolveAttribute(attribute, typedValue, true)
    return typedValue.data
}

fun Fragment.notYetImplemented() {
    showSnackbar("This feature is currently under development.")
}

fun List<Mailbox>.sortMailboxes(): List<Mailbox> = sortedByDescending { it.unseenMessages }

fun Attachment.getFileTypeFromExtension(): AttachmentType {
    return when {
        mimeType.contains(Regex("application/(zip|rar|x-tar|.*compressed|.*archive)")) -> AttachmentType.ARCHIVE
        mimeType.contains(Regex("audio/")) -> AttachmentType.AUDIO
        mimeType.contains(Regex("image/")) -> AttachmentType.IMAGE
        mimeType.contains(Regex("/pdf")) -> AttachmentType.PDF
        mimeType.contains(Regex("spreadsheet|excel|comma-separated-values")) -> AttachmentType.SPREADSHEET
        mimeType.contains(Regex("document|text/plain|msword")) -> AttachmentType.TEXT
        mimeType.contains(Regex("video/")) -> AttachmentType.VIDEO
        else -> AttachmentType.UNKNOWN
    }
}
