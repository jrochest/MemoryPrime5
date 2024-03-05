package com.md.viewmodel

import com.md.composeModes.Mode
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@ActivityScoped
class TopModeFlowProvider @Inject constructor() {
    val modeModel = MutableStateFlow(Mode.Practice)
}