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
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import coil3.imageLoader
import coil3.load
import com.infomaniak.core.avatar.models.AvatarType
import com.infomaniak.core.avatar.models.AvatarUrlData
import com.infomaniak.core.coil.getBackgroundColorGradientDrawable
import com.infomaniak.core.coil.loadAvatar
import com.infomaniak.lib.core.models.user.User
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.data.models.Bimi
import com.infomaniak.mail.data.models.correspondent.Correspondent
import com.infomaniak.mail.data.models.correspondent.MergedContact
import com.infomaniak.mail.databinding.ViewAvatarBinding
import com.infomaniak.mail.utils.AccountUtils
import com.infomaniak.mail.utils.AvatarTypeUtils.correspondentAvatarColors
import com.infomaniak.mail.utils.AvatarTypeUtils.fromUser
import com.infomaniak.mail.utils.AvatarTypeUtils.getAvatarType
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
            if (correspondent == null) return@Observer

            val avatarType = getAvatarType(correspondent, bimi, isBimiEnabled, contacts, context) ?: return@Observer
            loadAvatarType(avatarType)
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

    fun loadUserAvatar(user: User) {
        // TODO: Replace with centralized loadAvatarByDisplayType() display logic when AvatarDisplayType is a sealed class that
        //  contains only the required properties for each situation
        loadAvatarType(AvatarType.fromUser(user, context))
    }

    fun loadAvatar(correspondent: Correspondent?, bimi: Bimi? = null) {
        state.update(correspondent, bimi)
        val avatarType = getAvatarType(correspondent, bimi, isBimiEnabled, contactsFromViewModel, context) ?: return
        loadAvatarType(avatarType)
    }

    fun loadRawMergedContactAvatar(mergedContact: MergedContact) {
        // TODO: Replace with centralized loadAvatarByDisplayType() display logic and maybe create a new AvatarDisplayType for
        //  when we use the mergedContact directly
        if (mergedContact.shouldDisplayUserAvatar()) {
            loadUserAvatar(AccountUtils.currentUser!!)
        } else {
            loadMergedContact(mergedContact)
        }
    }

    fun loadUnknownUserAvatar() {
        state.update(correspondent = null, bimi = null)
        loadAvatarType(AvatarType.DrawableResource(R.drawable.ic_unknown_user_avatar))
    }

    fun loadTeamsUserAvatar() {
        state.update(correspondent = null, bimi = null)
        loadAvatarType(AvatarType.DrawableResource(R.drawable.ic_circle_teams_user))
    }

    private fun loadAvatarType(avatarType: AvatarType) {
        when (avatarType) {
            is AvatarType.WithInitials.Url -> {
                binding.avatarImage.loadAvatar(
                    backgroundColor = getBackgroundColorGradientDrawable(avatarType.colors.containerColor.toArgb()),
                    avatarUrl = avatarType.url,
                    initials = avatarType.initials,
                    imageLoader = avatarType.imageLoader,
                    initialsColor = avatarType.colors.contentColor.toArgb(),
                )
            }
            is AvatarType.WithInitials.Initials -> {
                binding.avatarImage.loadAvatar(
                    backgroundColor = getBackgroundColorGradientDrawable(avatarType.colors.containerColor.toArgb()),
                    avatarUrl = null,
                    initials = avatarType.initials,
                    initialsColor = avatarType.colors.contentColor.toArgb(),
                )
            }
            is AvatarType.DrawableResource -> binding.avatarImage.load(avatarType.resource)
        }
    }

    fun setImageDrawable(drawable: Drawable?) = binding.avatarImage.setImageDrawable(drawable)

    private fun loadMergedContact(mergedContact: MergedContact) = loadAvatarType(
        AvatarType.getUrlOrInitials(
            avatarUrlData = mergedContact.avatar?.let { AvatarUrlData(it, context.imageLoader) },
            initials = mergedContact.initials,
            colors = context.correspondentAvatarColors(mergedContact),
        )
    )

    private data class State(
        var correspondent: Correspondent? = null,
        var bimi: Bimi? = null,
    ) {
        fun update(correspondent: Correspondent?, bimi: Bimi?) {
            this.correspondent = correspondent
            this.bimi = bimi
        }
    }
}
