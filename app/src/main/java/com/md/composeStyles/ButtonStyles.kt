package com.md.composeStyles

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable

object ButtonStyles {

    @Composable
    fun ImportantButtonColor(): ButtonColors {
       return ButtonDefaults.buttonColors()
    }

    @Composable
    fun MediumImportanceButtonColor(): ButtonColors {
        return ButtonDefaults.filledTonalButtonColors()
    }
}