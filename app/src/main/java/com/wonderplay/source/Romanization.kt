package com.wonderplay.source

/** Romanization must come from the matched lyrics provider; never generated on device. */
object Romanization {
    fun variant(lyrics: Lyrics): Lyrics? {
        if(lyrics.instrumental || lyrics.romanizationSource.isNullOrBlank()) return null
        if(lyrics.romanizedPlain.isBlank() && lyrics.romanizedLines.isEmpty()) return null
        return lyrics.copy(plain = lyrics.romanizedPlain, lines = lyrics.romanizedLines)
    }
}
