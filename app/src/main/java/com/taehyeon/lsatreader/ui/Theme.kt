package com.taehyeon.lsatreader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    secondary = Color(0xFF8A5A00),
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFBFAF7),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    secondary = Color(0xFFF2C46B),
    background = Color(0xFF121416),
    surface = Color(0xFF121416),
)

@Composable
fun LsatTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** 논지 구조 색상 (라이트/다크 공통, 반투명 배경) */
object RoleColors {
    val map = mapOf(
        "MAIN" to Color(0x70E57373),
        "SUB" to Color(0x60FFB74D),
        "EVIDENCE" to Color(0x4D64B5F6),
        "COUNTER" to Color(0x60BA68C8),
    )
    val labels = listOf(
        "MAIN" to "핵심 결론",
        "SUB" to "중간 결론",
        "EVIDENCE" to "근거",
        "COUNTER" to "반론·양보",
    )
}
