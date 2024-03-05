package com.md.testing

import android.os.Build
import javax.inject.Inject

class TestingMode @Inject constructor() {
    val isTestDevice = Build.FINGERPRINT.contains("userdebug")
}