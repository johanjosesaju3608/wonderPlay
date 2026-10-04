package com.wonderplay.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshFrame(refreshing:Boolean,onRefresh:()->Unit,modifier:Modifier=Modifier,content:@Composable ()->Unit) {
    val state=rememberPullToRefreshState()
    val reduced=LocalReducedMotion.current
    val offset by animateFloatAsState(if(reduced) 0f else state.distanceFraction.coerceIn(0f,1.4f)*18f,if(reduced) tween(0) else spring(dampingRatio=.65f,stiffness=350f),label="Refresh bounce")
    val alpha=remember {Animatable(1f)}
    var wasRefreshing by remember {mutableStateOf(false)}
    LaunchedEffect(refreshing) {
        if(wasRefreshing && !refreshing && !reduced) {alpha.snapTo(.6f);alpha.animateTo(1f,tween(220))}
        wasRefreshing=refreshing
    }
    val pixels=with(LocalDensity.current) {offset.dp.toPx()}
    PullToRefreshBox(isRefreshing=refreshing,onRefresh=onRefresh,state=state,modifier=modifier) {
        Box(Modifier.fillMaxSize().graphicsLayer {translationY=pixels;this.alpha=alpha.value}) {content()}
    }
}
