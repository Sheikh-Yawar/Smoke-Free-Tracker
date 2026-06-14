package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Backgrounds
val BackgroundBase     = Color(0xFFF2F1F8)   // main screen bg — very light lavender
val BackgroundDeep     = Color(0xFFE8E6F4)   // slightly deeper for contrast sections

// Surfaces
val SurfaceCard        = Color(0xFFFFFFFF)   // pure white cards
val SurfaceElevated    = Color(0xFFF0EEF9)   // slightly tinted elevated cards
val SurfaceHero        = Color(0xFF2D2251)   // hero card background — deep indigo-purple

// Accent — Primary
val VioletPrimary      = Color(0xFF6C4FD4)   // buttons, active icons, progress fills
val VioletDeep         = Color(0xFF4B3299)   // pressed states, darker variant
val VioletLight        = Color(0xFFA58EE8)   // lighter tint for backgrounds, chips
val VioletMuted        = Color(0xFFD4CBF5)   // very soft tint, tag backgrounds

// Accent — Secondary
val CoralAccent        = Color(0xFFFF6B6B)   // SOS button, relapse indicator, alert
val MintAccent         = Color(0xFF4ECBA0)   // achieved milestones, positive states
val AmberAccent        = Color(0xFFFFA726)   // partial days, streak flame, warnings

// Text
val TextPrimaryDark    = Color(0xFF1A1035)   // main text on light backgrounds
val TextSecondary      = Color(0xFF7B6FA0)   // subtitles, labels, captions
val TextOnHero         = Color(0xFFF5F3FF)   // text on the dark hero card
val TextOnAccent       = Color(0xFFFFFFFF)   // text on violet buttons

// Utility
val DividerColor       = Color(0xFFE2DDEF)
val ShadowColor        = Color(0x1A6C4FD4)   // violet-tinted shadow
val CalendarClean      = Color(0xFF4ECBA0)   // green — smoke-free day
val CalendarRelapse    = Color(0xFFFF6B6B)   // red — relapse day
val CalendarPartial    = Color(0xFFFFA726)   // amber — partial day
val CalendarEmpty      = Color(0xFFE2DDEF)   // no data

// Back-compatibility aliases for other screens
val EmptyGrey         = CalendarEmpty
val WarningYellow     = AmberAccent
val PrimaryTealDark   = VioletPrimary
val HeroGradientStart = SurfaceHero
val HeroGradientEnd   = SurfaceHero

val NavyDeep       = SurfaceHero
val NavyMid        = SurfaceElevated
val NavyLight      = BackgroundDeep

val GoldPrimary    = VioletPrimary
val GoldSoft       = VioletLight
val GoldMuted      = VioletMuted

val RecoveryGreen  = MintAccent
val AlertRed       = CoralAccent
val WarmAmber      = AmberAccent

val SurfaceDark    = BackgroundBase
val TextPrimary    = TextPrimaryDark
val Divider        = DividerColor
