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
package com.infomaniak.mail.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import com.infomaniak.mail.utils.Utils
import com.infomaniak.mail.utils.Utils.runCatchingRealm
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

    private val state = State()

    // We use waitInitMediator over MediatorLiveData because we know both live data will be initialized very quickly anyway
    private val avatarMediatorLiveData: LiveData<Pair<MergedContactDictionary, Boolean>> =
        if (isInEditMode) {
            MutableLiveData()
        } else {
            Utils.waitInitMediator(avatarMergedContactData.mergedContactLiveData, avatarMergedContactData.isBimiEnabledLiveData)
        }

    private val avatarUpdateObserver = Observer<Pair<MergedContactDictionary, Boolean>> { (contacts, isBimiEnabled) ->
        runCatchingRealm {
            val (correspondent, bimi) = state
            val displayType = getAvatarDisplayType(correspondent, bimi, isBimiEnabled)

            if (displayType == AvatarDisplayType.UNKNOWN_CORRESPONDENT) return@Observer
            loadAvatarByDisplayType(displayType, correspondent, bimi, contacts)
        }
    }

    @Inject
    lateinit var avatarMergedContactData: AvatarMergedContactData

    private val contactsFromViewModel: MergedContactDictionary
        get() {
            // Avoid lateinit property has not been initialized in preview
            return if (isInEditMode) emptyMap() else avatarMergedContactData.mergedContactLiveData.value ?: emptyMap()
        }

    private val isBimiEnabled: Boolean get() = !isInEditMode && avatarMergedContactData.isBimiEnabledLiveData.value == true

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

        avatarMediatorLiveData.observeForever(avatarUpdateObserver)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (isInEditMode) return // Avoid lateinit property has not been initialized in preview

        avatarMediatorLiveData.removeObserver(avatarUpdateObserver)
    }

    override fun setOnClickListener(onClickListener: OnClickListener?) = binding.root.setOnClickListener(onClickListener)

    override fun setOnLongClickListener(onLongClickListener: OnLongClickListener?) {
        binding.root.setOnLongClickListener(onLongClickListener)
    }

    override fun setFocusable(focusable: Boolean) {
        binding.root.isFocusable = focusable
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
        val avatarDisplayType = getAvatarDisplayType(correspondent, bimi, isBimiEnabled)

        loadAvatarByDisplayType(avatarDisplayType, correspondent, bimi, contactsFromViewModel)
    }

    fun loadAvatar(mergedContact: MergedContact) {
        binding.avatarImage.baseLoadAvatar(mergedContact)
    }

    fun loadUnknownUserAvatar() {
        state.update(correspondent = null, bimi = null)
        binding.avatarImage.load(R.drawable.ic_unknown_user_avatar)
    }

    private fun loadBimiAvatar(correspondent: Correspondent, bimi: Bimi) = with(binding.avatarImage) {
        state.update(correspondent, bimi)
        contentDescription = correspondent.email
        loadAvatar(
            backgroundColor = context.getBackgroundColorBasedOnId(
                correspondent.email.hashCode(),
                R.array.AvatarColors,
            ),
            avatarUrl = ApiRoutes.bimi(bimi.svgContentUrl!!),
            initials = correspondent.initials,
            imageLoader = svgImageLoader,
            initialsColor = context.getColor(R.color.onColorfulBackground),
        )
    }

    private fun loadAvatarByDisplayType(
        avatarDisplayType: AvatarDisplayType,
        correspondent: Correspondent?,
        bimi: Bimi?,
        contacts: MergedContactDictionary,
    ) {
        when (avatarDisplayType) {
            AvatarDisplayType.UNKNOWN_CORRESPONDENT -> loadUnknownUserAvatar()
            AvatarDisplayType.USER_AVATAR -> AccountUtils.currentUser?.let { loadAvatar(user = it) }
            AvatarDisplayType.CUSTOM_AVATAR,
            AvatarDisplayType.INITIALS -> loadAvatarUsingDictionary(correspondent!!, contacts, bimi)
            AvatarDisplayType.BIMI -> loadBimiAvatar(correspondent!!, bimi!!)
        }
    }

    private fun getAvatarDisplayType(correspondent: Correspondent?, bimi: Bimi?, isBimiEnabled: Boolean): AvatarDisplayType {
        return when {
            correspondent == null -> AvatarDisplayType.UNKNOWN_CORRESPONDENT
            correspondent.isMe() -> AvatarDisplayType.USER_AVATAR
            correspondent.hasMergedContactAvatar(contactsFromViewModel) -> AvatarDisplayType.CUSTOM_AVATAR
            bimi?.isDisplayable(isBimiEnabled) == true -> AvatarDisplayType.BIMI
            else -> AvatarDisplayType.INITIALS
        }
    }

    fun setImageDrawable(drawable: Drawable?) = binding.avatarImage.setImageDrawable(drawable)

    private fun searchInMergedContact(correspondent: Correspondent, contacts: MergedContactDictionary): MergedContact? {
        val recipientsForEmail = contacts[correspondent.email]
        return recipientsForEmail?.getOrElse(correspondent.name) { recipientsForEmail.entries.elementAt(0).value }
    }

    private fun Correspondent.hasMergedContactAvatar(contacts: MergedContactDictionary): Boolean {
        return searchInMergedContact(correspondent = this, contacts)?.avatar != null
    }

    private fun loadAvatarUsingDictionary(correspondent: Correspondent, contacts: MergedContactDictionary, bimi: Bimi?) {
        state.update(correspondent, bimi)
        val mergedContact = searchInMergedContact(correspondent, contacts)
        binding.avatarImage.baseLoadAvatar(correspondent = mergedContact ?: correspondent)
    }

    private fun ImageView.baseLoadAvatar(correspondent: Correspondent) {
        if (correspondent.shouldDisplayUserAvatar()) {
            this@AvatarView.loadAvatar(AccountUtils.currentUser!!)
        } else {
            loadAvatar(
                backgroundColor = context.getBackgroundColorBasedOnId(correspondent.email.hashCode(), R.array.AvatarColors),
                avatarUrl = (correspondent as? MergedContact)?.avatar,
                initials = correspondent.initials,
                imageLoader = context.imageLoader,
                initialsColor = context.getColor(R.color.onColorfulBackground),
            )
        }
    }

    private data class State(
        var correspondent: Correspondent? = null,
        var bimi: Bimi? = null,
    ) {
        fun update(correspondent: Correspondent?, bimi: Bimi?) {
            this.correspondent = correspondent
            this.bimi = bimi
        }
    }

    enum class AvatarDisplayType {
        UNKNOWN_CORRESPONDENT,
        CUSTOM_AVATAR,
        USER_AVATAR,
        BIMI,
        INITIALS,
    }
}
