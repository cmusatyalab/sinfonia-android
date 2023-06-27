package com.wireguard.android.widget

import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton

class ContainedButton @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : MaterialButton(context, attrs)  {
    private var isRestoringState = false
    private var listener: OnCheckedChangeListener? = null

    override fun onRestoreInstanceState(state: Parcelable?) {
        isRestoringState = true
        super.onRestoreInstanceState(state)
        isRestoringState = false
    }

    override fun setChecked(checked: Boolean) {
        if (checked == isChecked) return
        if (isRestoringState || listener == null) {
            super.setChecked(checked)
            return
        }
        isEnabled = false
        listener!!.onCheckedChanged(this, checked)
    }

    fun setCheckedInternal(checked: Boolean) {
        super.setChecked(checked)
        isEnabled = true
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        this.listener = listener
    }
}