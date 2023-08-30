package com.md.uiTheme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AppTheme(
    content: @Composable() () -> Unit
) {
    val veryDarkGrey = Color(0xFF222222)
    val blueGreen = Color(0xFF116666)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = veryDarkGrey,
            onPrimary = blueGreen
        ),
        content = content
    )
}