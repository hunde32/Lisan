package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

// ------------------------------------------------------------------------------------------------
// CURATED 2-3 COLOR PALETTES (Minimalist, cohesive, beautiful)
// Each theme defines: 1 background/surface family, 1 accent color, 1 readable typography family
// ------------------------------------------------------------------------------------------------
enum class AppThemePalette(
    val bg: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val textMuted: Color,
    val border: Color,
    val isDark: Boolean = true
) {
    // 1. Gruvbox (Earthy warm 2-3 colors: charcoal background, warm amber-orange accent, ivory text)
    GRUVBOX(
        bg = Color(0xFF282828),
        surface = Color(0xFF32302F),
        surfaceVariant = Color(0xFF3C3836),
        primary = Color(0xFFFE8019),
        onPrimary = Color(0xFF1D2021),
        text = Color(0xFFEBDBB2),
        textMuted = Color(0xFFA89984),
        border = Color(0xFF504945)
    ),

    // 2. Tokyo Night (Cyberpunk storm: deep midnight navy, storm surface, electric soft blue accent)
    TOKYO_NIGHT(
        bg = Color(0xFF1A1B26),
        surface = Color(0xFF24283B),
        surfaceVariant = Color(0xFF2F3549),
        primary = Color(0xFF7AA2F7),
        onPrimary = Color(0xFF15161E),
        text = Color(0xFFC0CAF5),
        textMuted = Color(0xFF787C99),
        border = Color(0xFF363B54)
    ),

    // 3. One Dark Pro (Iconic editor slate: deep slate background, soft cyan/sky accent)
    ONE_DARK_PRO(
        bg = Color(0xFF21252B),
        surface = Color(0xFF282C34),
        surfaceVariant = Color(0xFF2F343F),
        primary = Color(0xFF61AFEF),
        onPrimary = Color(0xFF1E2227),
        text = Color(0xFFABB2BF),
        textMuted = Color(0xFF677084),
        border = Color(0xFF3E4451)
    ),

    // 4. Nord (Arctic cool: polar night charcoal, snow storm surface, glacial frost blue accent)
    NORD(
        bg = Color(0xFF2E3440),
        surface = Color(0xFF3B4252),
        surfaceVariant = Color(0xFF434C5E),
        primary = Color(0xFF88C0D0),
        onPrimary = Color(0xFF2E3440),
        text = Color(0xFFECEFF4),
        textMuted = Color(0xFF98A2B5),
        border = Color(0xFF4C566A)
    ),

    // 5. Dracula (Gothic dark: deep slate-purple, pastel violet accent)
    DRACULA(
        bg = Color(0xFF282A36),
        surface = Color(0xFF343746),
        surfaceVariant = Color(0xFF44475A),
        primary = Color(0xFFBD93F9),
        onPrimary = Color(0xFF21222C),
        text = Color(0xFFF8F8F2),
        textMuted = Color(0xFF6272A4),
        border = Color(0xFF44475A)
    ),

    // 6. Catppuccin Mocha (Smooth espresso dark, soft lavender accent)
    CATPPUCCIN(
        bg = Color(0xFF1E1E2E),
        surface = Color(0xFF25253A),
        surfaceVariant = Color(0xFF313244),
        primary = Color(0xFFCBA6F7),
        onPrimary = Color(0xFF181825),
        text = Color(0xFFCDD6F4),
        textMuted = Color(0xFF7F849C),
        border = Color(0xFF45475A)
    ),

    // 7. Minimal OLED (True OLED black, dark neutral gray, vivid emerald accent)
    OLED_MINIMAL(
        bg = Color(0xFF000000),
        surface = Color(0xFF121212),
        surfaceVariant = Color(0xFF1E1E1E),
        primary = Color(0xFF10B981),
        onPrimary = Color(0xFF000000),
        text = Color(0xFFF9FAFB),
        textMuted = Color(0xFF9CA3AF),
        border = Color(0xFF27272A)
    ),

    // 8. Clean Paper Light (Warm off-white paper, pure white surface, deep indigo accent)
    CLEAN_LIGHT(
        bg = Color(0xFFF7F7F6),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEDECE9),
        primary = Color(0xFF2563EB),
        onPrimary = Color(0xFFFFFFFF),
        text = Color(0xFF111827),
        textMuted = Color(0xFF6B7280),
        border = Color(0xFFE5E7EB),
        isDark = false
    )
}

// Fallback & Compatibility constants
val Emerald = Color(0xFF10B981)
val EmeraldGlow = Color(0xFF34D399)
val CyanAccent = Color(0xFF06B6D4)
val IndigoAccent = Color(0xFF6366F1)
val VioletAccent = Color(0xFF8B5CF6)
val AmberAccent = Color(0xFFF59E0B)
val RoseAccent = Color(0xFFF43F5E)


