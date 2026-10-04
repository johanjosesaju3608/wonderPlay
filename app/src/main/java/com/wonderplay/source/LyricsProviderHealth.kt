package com.wonderplay.source

/** Local session-only reliability ranking; missing songs are not service failures. */
internal class LyricsProviderHealth(private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    private data class Health(var failures: Int = 0, var until: Long = 0)
    private val states = mutableMapOf("NetEase" to Health(), "lyrics.ovh" to Health())
    @Synchronized fun order(): List<String> = states.keys.sortedBy { (if(it == "NetEase") 0 else 1) + states.getValue(it).failures * 2 }
    @Synchronized fun available(provider: String) = states.getValue(provider).until <= now()
    @Synchronized fun success(provider: String) { states[provider] = Health() }
    @Synchronized fun failure(provider: String) { states.getValue(provider).apply { failures = (failures + 1).coerceAtMost(5); if(failures >= 2) until = now() + 600_000 } }
}
