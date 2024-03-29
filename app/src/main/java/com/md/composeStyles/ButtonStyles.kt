package com.md.composeStyles

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object ButtonStyles {

    @Composable
    fun ImportantButtonColor(): ButtonColors {
        return ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )

    }

    @Composable
    fun MediumImportanceButtonColor(): ButtonColors {
        return ButtonDefaults.outlinedButtonColors()
    }
}