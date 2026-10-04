package com.wonderplay.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

// Scrollable content reaches behind the floating controls; only its end has clearance.
internal val LocalOverlayBottom = staticCompositionLocalOf { 0.dp }
