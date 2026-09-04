package com.trispeech.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Modifier.liquidGlass(radius: Dp = 18.dp): Modifier {
    val colors = MaterialTheme.colorScheme
    return this
        .shadow(14.dp, RoundedCornerShape(radius), ambientColor = Color.Black.copy(alpha = .08f), spotColor = Color.Black.copy(alpha = .10f))
        .background(colors.surface.copy(alpha = .92f), RoundedCornerShape(radius))
        .border(1.dp, colors.outline.copy(alpha = .72f), RoundedCornerShape(radius))
}
