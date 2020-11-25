package com.md.modesetters

import android.app.Activity
import android.widget.ToggleButton
import com.md.CategorySingleton
import com.md.ModeHandler
import com.md.R

object SettingModeSetter : ModeSetter(), ItemDeletedHandler {
    fun setup(memoryDroid: Activity?, modeHand: ModeHandler?) {
        parentSetup(memoryDroid, modeHand)
    }

    override fun setupModeImpl(context: Activity) {
        commonSetup(context, R.layout.settings)
        setupSettings(context)
    }

    private fun setupSettings(activity: Activity) {
        val markButton = activity.findViewById<ToggleButton>(R.id.look_ahead)
        val instance = CategorySingleton.getInstance()
        markButton.isChecked = instance.lookAheadDays != 0
        markButton.setOnClickListener {
            val checked = markButton.isChecked
            instance.lookAheadDays = if (checked) 1 else 0
        }
        val repeatButton = activity.findViewById<ToggleButton>(R.id.repeat)
        repeatButton.isChecked = instance.shouldRepeat()
        repeatButton.setOnClickListener { instance.setRepeat(!repeatButton.isChecked) }
    }
}