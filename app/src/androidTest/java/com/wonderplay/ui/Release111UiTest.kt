package com.wonderplay.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.wonderplay.MainActivity
import com.wonderplay.WonderPlayApp
import com.wonderplay.domain.AudioQuality
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test


class Release111UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun separateQualityPillsPersistAfterRecreation() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("Settings list").performScrollToIndex(9)
        compose.onNodeWithText("Low").performClick()
        val app=compose.activity.application as WonderPlayApp
        compose.waitUntil(5000) {runBlocking {app.container.library.settings.first().audioQuality==AudioQuality.LOW}}
        compose.onNodeWithText("Low").assertIsSelected()
        compose.onNodeWithText("Medium").performClick()
        compose.waitUntil(5000) {runBlocking {app.container.library.settings.first().audioQuality==AudioQuality.MEDIUM}}
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("Settings list").performScrollToIndex(9)
        compose.onNodeWithText("Medium").assertIsSelected()
        compose.onNodeWithText("High").performClick()
        compose.waitUntil(5000) {runBlocking {app.container.library.settings.first().audioQuality==AudioQuality.HIGH}}
    }
    @Test fun informationDialogsDismissAndDiscoveryRefreshDoesNotFocusKeyboard() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("Settings list").performScrollToIndex(15)
        compose.onNodeWithText("Music sources").performClick()
        compose.onNodeWithText("Sources").assertExists()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Home",useUnmergedTree=true).performClick()
        compose.onNodeWithContentDescription("Search",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("Search refresh").performTouchInput {swipeDown()}
        compose.onNodeWithContentDescription("Search music").assertIsNotFocused()
    }
}
