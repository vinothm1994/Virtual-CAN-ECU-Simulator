package com.vecu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Section header used at the top of each panel. */
@Composable
fun PanelHeader(title: String) {
    Text(
        title,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141A20))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF8FA0AE),
    )
}
