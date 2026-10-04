package com.wonderplay.domain

import com.wonderplay.metadata.MetadataResolver

object RadioPolicy {
    fun select(candidates: List<Track>, queue: List<Track>): List<Track> {
        val known = queue.map { it.id }.toSet()
        val chosen = mutableListOf<Track>()
        for(track in candidates.distinctBy { it.id }) {
            if(track.source != "youtube" || track.id in known || track.durationMs !in 45_000..900_000) continue
            if((queue + chosen).any { MetadataResolver.sameRecording(it, track) }) continue
            chosen += track
            if(chosen.size == 6) break
        }
        return chosen
    }
}
