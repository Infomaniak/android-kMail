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
package com.infomaniak.mail.ui.main.settings

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.infomaniak.lib.core.utils.getAttributes
import com.infomaniak.mail.R

class SettingRadioGroupView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), OnCheckListener {

    @IdRes
    private var checkedId: Int = View.NO_ID
    private var checkedValue: String? = null
    private var onItemCheckedListener: ((Int, String?, Enum<*>?) -> Unit)? = null
    private var shouldAddDividers = true

    private var bijectionTable: Map<Int, Enum<*>> = emptyMap()

    init {
        orientation = VERTICAL

        attrs?.getAttributes(context, R.styleable.SettingRadioGroupView) {
            checkedId = getResourceId(R.styleable.SettingRadioGroupView_defaultCheckedId, View.NO_ID)
            check(checkedId)

            shouldAddDividers = !getBoolean(R.styleable.SettingRadioGroupView_ignoreDividers, false)
        }

        if (shouldAddDividers) {
            showDividers = SHOW_DIVIDER_MIDDLE
            dividerPadding = resources.getDimension(R.dimen.dividerHorizontalPadding).toInt()
            dividerDrawable = ContextCompat.getDrawable(context, R.drawable.divider)
        }
    }

    override fun onFinishInflate() {
        children.forEach {
            if (it is RadioCheckable && it.id == View.NO_ID) it.id = generateViewId()
        }
        super.onFinishInflate()
    }

    override fun onChecked(@IdRes viewId: Int) {
        if (viewId != checkedId) {
            check(viewId)
            onItemCheckedListener?.invoke(checkedId, checkedValue, bijectionTable[viewId])
        }
    }

    fun <T : Enum<T>> initBijectionTable(vararg pairs: Pair<Int, T>) {
        bijectionTable = pairs.toMap()
    }

    @Suppress("TypeParameterFindViewById")
    fun check(@IdRes viewId: Int) {
        if (viewId == checkedId) return

        (findViewById(checkedId) as? RadioCheckable)?.uncheck()
        with(findViewById(viewId) as RadioCheckable) {
            check()
            checkedValue = associatedValue
        }

        checkedId = viewId
    }

    fun check(enum: Enum<*>) {
        val viewId = bijectionTable.keys.firstOrNull { bijectionTable[it] == enum } ?: return
        check(viewId)
    }

    fun onItemCheckedListener(listener: ((id: Int, value: String?, enum: Enum<*>?) -> Unit)?) {
        onItemCheckedListener = listener
    }
}
