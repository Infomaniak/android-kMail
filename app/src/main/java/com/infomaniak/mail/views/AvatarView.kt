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
package com.infomaniak.mail.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.Observer
import coil.ImageLoader
import coil.imageLoader
import coil.load
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.CoilUtils.simpleImageLoader
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.lib.core.utils.loadAvatar
import com.infomaniak.mail.R
import com.infomaniak.mail.data.api.ApiRoutes
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ViewAvatarBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.extensions.MergedContactDictionary
import com.infomaniak.mail.utils.extensions.getColorOrNull
import com.infomaniak.mail.utils.extensions.getTransparentColor
import com.infomaniak.mail.utils.extensions.setInnerStrokeWidth
import com.infomaniak.mail.views.itemViews.AvatarMergedContactData
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AvatarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewAvatarBinding.inflate(LayoutInflater.from(context), this, true) }

    private var currentCorrespondent: Correspondent? = null
    private var currentBimi: Bimi? = null

    private val mergedContactObserver = Observer<MergedContactDictionary> { contacts ->
        val displayType = getAvatarDisplayType(currentCorrespondent, currentBimi)

        if (displayType == AvatarDisplayType.MERGED_AVATAR || displayType == AvatarDisplayType.DEFAULT) {
            loadAvatarUsingDictionary(currentCorrespondent!!, contacts)
        }
    }

    @Inject
    lateinit var _avatarMergedContactData: AvatarMergedContactData

    private val contactsFromViewModel: MergedContactDictionary
        get() {
            // Avoid lateinit property has not been initialized in preview
            return if (isInEditMode) emptyMap() else _avatarMergedContactData.mergedContactLiveData.value ?: emptyMap()
        }


    @Inject
    lateinit var svgImageLoader: ImageLoader

    var strokeWidth: Float
        get() = binding.avatarImage.strokeWidth
        set(value) {
            binding.avatarImage.setInnerStrokeWidth(value)
        }

    var strokeColor: Int?
        get() = binding.avatarImage.strokeColor?.defaultColor
        set(value) {
            binding.avatarImage.strokeColor = ColorStateList.valueOf(value ?: context.getTransparentColor())
        }

    init {
        attrs?.getAttributes(context, R.styleable.AvatarView) {
            val strokeWidthInt = getDimensionPixelOffset(R.styleable.AvatarView_strokeWidth, 0)
            strokeWidth = strokeWidthInt.toFloat()
            strokeColor = getColorOrNull(R.styleable.AvatarView_strokeColor)

            binding.avatarImage.setImageDrawable(getDrawable(R.styleable.AvatarView_android_src))

            val inset = getDimensionPixelOffset(R.styleable.AvatarView_inset, 0)
            setPaddingRelative(inset, inset, inset, inset)

            @Suppress("ClickableViewAccessibility")
            setOnTouchListener { _, event -> binding.root.onTouchEvent(event) }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isInEditMode) return // Avoid lateinit property has not been initialized in preview
        _avatarMergedContactData.mergedContactLiveData.observeForever(mergedContactObserver)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode) return // Avoid lateinit property has not been initialized in preview
        _avatarMergedContactData.mergedContactLiveData.removeObserver(mergedContactObserver)
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) = binding.root.setOnClickListener(onClickListener)

    override fun setOnLongClickListener(onLongClickListener: OnLongClickListener?) {
        binding.root.setOnLongClickListener(onLongClickListener)
    }

    fun loadAvatar(user: User) = with(binding.avatarImage) {
        contentDescription = user.email
        loadAvatar(
            backgroundColor = context.getBackgroundColorBasedOnId(user.id, R.array.AvatarColors),
            avatarUrl = user.avatar,
            initials = user.getInitials(),
            imageLoader = context.simpleImageLoader,
            initialsColor = context.getColor(R.color.onColorfulBackground),
        )
    }

    fun loadAvatar(correspondent: Correspondent?, bimi: Bimi? = null) {
        currentBimi = bimi

        fun loadSimpleCorrespondent(correspondent: Correspondent) {
            loadAvatarUsingDictionary(correspondent, contacts = contactsFromViewModel)
            currentCorrespondent = correspondent
        }

        when (getAvatarDisplayType(correspondent, bimi)) {
            AvatarDisplayType.UNKNOWN -> loadUnknownUserAvatar()
            AvatarDisplayType.MERGED_AVATAR -> loadSimpleCorrespondent(correspondent!!)
            AvatarDisplayType.BIMI -> loadBimiAvatar(ApiRoutes.bimi(bimi!!.svgContentUrl!!), correspondent!!)
            AvatarDisplayType.DEFAULT -> loadSimpleCorrespondent(correspondent!!)
        }
    }

    private fun getAvatarDisplayType(correspondent: Correspondent?, bimi: Bimi?): AvatarDisplayType {
        return when {
            correspondent == null -> AvatarDisplayType.UNKNOWN
            correspondent.hasMergedContactAvatar(contactsFromViewModel) -> AvatarDisplayType.MERGED_AVATAR
            bimi?.isDisplayable() == true -> AvatarDisplayType.BIMI
            else -> AvatarDisplayType.DEFAULT
        }
    }

    fun loadAvatar(mergedContact: MergedContact) {
        binding.avatarImage.baseLoadAvatar(mergedContact)
    }

    fun loadUnknownUserAvatar() {
        currentCorrespondent = null
        binding.avatarImage.load(R.drawable.ic_unknown_user_avatar)
    }

    private fun loadBimiAvatar(bimiUrl: String, correspondent: Correspondent) = with(binding.avatarImage) {
        contentDescription = correspondent.email
        loadAvatar(
            backgroundColor = context.getBackgroundColorBasedOnId(
                correspondent.email.hashCode(),
                R.array.AvatarColors,
            ),
            avatarUrl = bimiUrl,
            initials = correspondent.initials,
            imageLoader = svgImageLoader,
            initialsColor = context.getColor(R.color.onColorfulBackground),
        )
    }

    fun setImageDrawable(drawable: Drawable?) = binding.avatarImage.setImageDrawable(drawable)

    private fun searchInMergedContact(correspondent: Correspondent, contacts: MergedContactDictionary): MergedContact? {
        val recipientsForEmail = contacts[correspondent.email]
        return recipientsForEmail?.getOrElse(correspondent.name) { recipientsForEmail.entries.elementAt(0).value }
    }

    private fun Correspondent.hasMergedContactAvatar(contacts: MergedContactDictionary): Boolean {
        return searchInMergedContact(this, contacts)?.avatar != null
    }

    private fun loadAvatarUsingDictionary(correspondent: Correspondent, contacts: MergedContactDictionary) {
        val mergedContact = searchInMergedContact(correspondent, contacts)
        binding.avatarImage.baseLoadAvatar(correspondent = mergedContact ?: correspondent)
    }

    private fun ImageView.baseLoadAvatar(correspondent: Correspondent) {
        if (correspondent.shouldDisplayUserAvatar()) {
            this@AvatarView.loadAvatar(AccountUtils.currentUser!!)
        } else {
            val avatar = (correspondent as? MergedContact)?.avatar
            val color = context.getColor(R.color.onColorfulBackground)
            loadAvatar(
                backgroundColor = context.getBackgroundColorBasedOnId(correspondent.email.hashCode(), R.array.AvatarColors),
                avatarUrl = avatar,
                initials = correspondent.initials,
                imageLoader = context.imageLoader,
                initialsColor = color,
            )
        }
    }

    enum class AvatarDisplayType { UNKNOWN, MERGED_AVATAR, BIMI, DEFAULT }
}
