package com.md.modesetters

import android.app.Activity
import android.os.Build
import android.view.WindowInsetsController
import android.view.WindowManager
import com.md.AudioPlayer
import com.md.CategorySingleton
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity
import com.md.utils.ToastSingleton

abstract class ModeSetter {
    @JvmField
    protected var mActivity: SpacedRepeaterActivity? = null
    @JvmField
    protected var modeHand: ModeHandler? = null
    fun switchMode(context: Activity) {
        if (this !is DeckChooseModeSetter &&
                !CategorySingleton.getInstance().hasCategory()) {
            ToastSingleton.getInstance().msg("No deck selected. \nUsing default")
            val deckChooser = DeckChooseModeSetter.getInstance()
            val defaultDeck = deckChooser.nextDeckWithItems
            if (defaultDeck != null) {
                deckChooser.loadDeck(defaultDeck)
            }
        }
        // Switching away from learning mode should stop playback.
        AudioPlayer.instance.pause()
        switchModeImpl(context)
    }

    abstract fun switchModeImpl(context: Activity)

    fun parentSetup(context: Activity?, modeHand: ModeHandler?) {
        mActivity = context as SpacedRepeaterActivity?
        this.modeHand = modeHand
    }

    protected fun commonSetup(context: Activity, view: Int) {
        context.setContentView(view)
        adjustScreenLock()
        modeHand!!.add(this)
    }

    protected open fun adjustScreenLock() {
        mActivity!!.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        showSystemUi()
        /*
        See if these affect brightness when screen off.
        android.provider.Settings.System.putInt(mActivity.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS, 200);
        android.provider.Settings.System.putInt(mActivity.getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS, 0);
                */
    }

    protected fun hideSystemUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val controller = mActivity!!.window.insetsController
        if (controller != null) {
            //controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
    }

    protected fun showSystemUi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val controller = mActivity!!.window.insetsController
        if (controller != null) {
            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
    }

    open fun proceed() {
        // If play is pressed setup learning mode.
        LearningModeSetter.instance.switchMode(mActivity!!)
    }

    open fun undo() {}
    open fun resetActivity() {}
    open fun handleReplay() {}
    open fun proceedFailure() {}
    open fun toggleDim() {}
    open fun mark() {}
    open fun secondaryAction(): String? {
        return ""
    }

    open fun postponeNote() {}
}