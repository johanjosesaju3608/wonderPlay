package com.wonderplay.domain

import org.junit.Assert.*
import org.junit.Test
class RadioPolicyTest {
    private fun track(id: String) = Track(id, id, "Artist", durationMs = 180000)
    @Test fun relatedQueueExcludesKnownRecordingsDuplicatesAndInvalidTracks() {
        val known = track("known")
        val next = track("next")
        val selected = RadioPolicy.select(listOf(known, known.copy(id = "alternate"), next, next, track("local").copy(source = "local"), track("short").copy(durationMs = 1000)), listOf(known))
        assertEquals(listOf(next), selected)
    }
    @Test fun additionsAreBoundedAndOrderedByProviderRelevance() {
        assertEquals((0..5).map { track("$it") }, RadioPolicy.select((0..30).map { track("$it") }, emptyList()))
    }
}
