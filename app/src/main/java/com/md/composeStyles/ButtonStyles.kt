package com.md.composeStyles

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

object ButtonStyles {

    @Composable
    fun ImportantButtonColor(): ButtonColors {
        return ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }

    @Composable
    fun MediumImportanceButtonColor(): ButtonColors {
        return ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }

    @Composable
    fun SecondaryModeButtonColor(): ButtonColors {
        return ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }

    @Composable
    fun DangerButtonColor(): ButtonColors {
        return ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}