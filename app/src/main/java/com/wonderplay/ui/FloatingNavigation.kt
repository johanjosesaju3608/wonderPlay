package com.wonderplay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun FloatingNavigation(tab: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    Surface(modifier.padding(horizontal = 20.dp, vertical = 12.dp).fillMaxWidth().height(64.dp), shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .16f))) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Home" to Icons.Rounded.Home, "Search" to Icons.Rounded.Search, "Library" to Icons.Rounded.LibraryMusic).forEach { (label, icon) ->
                val active = tab == label
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val recovery = remember { Animatable(1f) }
                LaunchedEffect(active) {
                    if(active && !reduced) { recovery.snapTo(.86f); recovery.animateTo(1f, tween(230)) }
                    else recovery.snapTo(1f)
                }
                val pressScale by animateFloatAsState(if(pressed && !reduced) .86f else 1f, tween(if(reduced) 0 else 90), label = "Tab press")
                val iconColor by animateColorAsState(
                    if(pressed || recovery.value < .96f) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .58f)
                    else if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(if(reduced) 0 else 130), label = "Tab icon")
                val weight by animateFloatAsState(if(active) 1.6f else 1f, tween(if(reduced) 0 else 230), label = "Navigation pill")
                val color by animateColorAsState(if(active) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent, tween(if(reduced) 0 else 230), label = "Navigation color")
                Surface(Modifier.weight(weight).fillMaxHeight().clip(CircleShape).semantics { selected = active; contentDescription = label }.clickable(interactionSource = interaction, indication = null, role = Role.Tab) { onSelect(label) }, shape = CircleShape, color = color) {
                    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(icon, null, Modifier.size(23.dp).graphicsLayer { scaleX = pressScale * recovery.value; scaleY = pressScale * recovery.value }, tint = iconColor)
                        if(active) { Spacer(Modifier.width(8.dp)); Text(label, modifier = Modifier.graphicsLayer { alpha = ((recovery.value - .86f) / .14f).coerceIn(0f, 1f) }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1) }
                    }
                }
            }
        }
    }
}
