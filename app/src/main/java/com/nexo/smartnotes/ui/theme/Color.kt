package com.nexo.smartnotes.ui.theme

import androidx.compose.ui.graphics.Color

// ---- 品牌色 ----
val Violet = Color(0xFF6C5CE7)
val VioletLight = Color(0xFFA99BF5)
val VioletDark = Color(0xFF4B3FBF)
val Teal = Color(0xFF00B894)
val Coral = Color(0xFFE17055)
val Amber = Color(0xFFF5A524)
val Rose = Color(0xFFE84393)
val Sky = Color(0xFF3B82F6)

// ---- 浅色 ----
val LightBackground = Color(0xFFF7F5FB)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF0EDF8)
val LightOnBackground = Color(0xFF1D1A2B)
val LightOnSurfaceVariant = Color(0xFF6B6480)
val LightOutline = Color(0xFFDCD6EA)

// ---- 深色 ----
val DarkBackground = Color(0xFF121016)
val DarkSurface = Color(0xFF1C1A24)
val DarkSurfaceVariant = Color(0xFF27242F)
val DarkOnBackground = Color(0xFFF0EDF8)
val DarkOnSurfaceVariant = Color(0xFFA39DB5)
val DarkOutline = Color(0xFF37333F)

/** 笔记卡片可选底色（浅色）。索引 0 为默认白。 */
val NoteColorsLight = listOf(
    Color(0xFFFFFFFF),
    Color(0xFFF3E8FF),
    Color(0xFFE3F2FD),
    Color(0xFFE8F5E9),
    Color(0xFFFFF8E1),
    Color(0xFFFFEBEE),
    Color(0xFFFCE4EC),
    Color(0xFFE0F7FA),
    Color(0xFFFFF3E0),
)

/** 深色主题下对应的卡片底色。 */
val NoteColorsDark = listOf(
    Color(0xFF1C1A24),
    Color(0xFF2A2140),
    Color(0xFF16283A),
    Color(0xFF182E24),
    Color(0xFF332B17),
    Color(0xFF3A1F24),
    Color(0xFF36202A),
    Color(0xFF153036),
    Color(0xFF33291A),
)

/** 每种笔记类型的强调色。 */
val TypeAccents = listOf(Violet, Amber, Teal, Coral, Sky, Rose)
