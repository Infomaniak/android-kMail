package com.infomaniak.mail.ui.main.menu

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R
import com.infomaniak.mail.databinding.ViewMenuDrawerDropdownBinding
import com.infomaniak.mail.utils.toggleChevron

class MenuDrawerDropdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    val binding by lazy { ViewMenuDrawerDropdownBinding.inflate(LayoutInflater.from(context), this, true) }

    var isCollapsed = false
        private set

    init {
        with(binding) {
            attrs?.getAttributes(context, R.styleable.MenuDrawerDropdownView) {
                title.text = getString(R.styleable.MenuDrawerDropdownView_title)
                actionButton.isVisible = getBoolean(R.styleable.MenuDrawerDropdownView_showIcon, false)
                isCollapsed = getBoolean(R.styleable.MenuDrawerDropdownView_collapsedByDefault, false)

                actionButton.contentDescription = getString(R.styleable.MenuDrawerDropdownView_actionContentDescription)
            }

            binding.expandCustomFolderButton.rotation = getRotation(isCollapsed)
            setOnClickListener(null)
        }
    }

    fun setIsCollapsed(newState: Boolean) {
        isCollapsed = newState
        binding.expandCustomFolderButton.rotation = getRotation(isCollapsed)
    }

    private fun getRotation(isCollapsed: Boolean): Float = ResourcesCompat.getFloat(
        context.resources,
        if (isCollapsed) R.dimen.angleViewNotRotated else R.dimen.angleViewRotated
    )

    override fun setOnClickListener(listener: OnClickListener?) = with(binding) {
        root.setOnClickListener {
            isCollapsed = !isCollapsed
            expandCustomFolderButton.toggleChevron(isCollapsed)
            listener?.onClick(root)
        }
    }

    fun setOnActionClickListener(listener: OnClickListener?) {
        binding.actionButton.setOnClickListener(listener)
    }
}
