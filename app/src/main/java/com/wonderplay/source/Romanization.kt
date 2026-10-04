package com.wonderplay.source

import android.os.Build
import androidx.annotation.RequiresApi

object Romanization {
    fun variant(lyrics: Lyrics): Lyrics? {
        if(lyrics.romanizedPlain.isNotBlank() || lyrics.romanizedLines.isNotEmpty()) return lyrics.copy(plain = lyrics.romanizedPlain, lines = lyrics.romanizedLines)
        if(Build.VERSION.SDK_INT < 29 || lyrics.instrumental) return null
        return Api29.convert(lyrics)
    }
    private fun nonLatin(value: String) = value.any { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }
    @RequiresApi(29)
    private object Api29 {
        fun convert(lyrics: Lyrics): Lyrics? {
            val original = if(lyrics.lines.isNotEmpty()) lyrics.lines.joinToString("\n") { it.text } else lyrics.plain
            if(!nonLatin(original)) return null
            return runCatching {
                val converter = android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
                val plain = converter.transliterate(lyrics.plain)
                val lines = lyrics.lines.map { it.copy(text = converter.transliterate(it.text)) }
                val converted = if(lines.isNotEmpty()) lines.joinToString("\n") { it.text } else plain
                if(converted == original || nonLatin(converted)) null else lyrics.copy(plain = plain, lines = lines, romanizationSource = "On-device transliteration")
            }.getOrNull()
        }
    }
}
