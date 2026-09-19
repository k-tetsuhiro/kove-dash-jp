package com.kovedash.app.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MapboxGeocoderTest {

    @Test
    fun japanese_script_searches_in_japanese_regardless_of_phone_language() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("ja", MapboxGeocoder.languageFor("東京駅"))
            assertEquals("ja", MapboxGeocoder.languageFor("とうきょう"))
            assertEquals("ja", MapboxGeocoder.languageFor("セブン"))
            assertEquals("ja", MapboxGeocoder.languageFor("ｾﾌﾞﾝ"))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun latin_script_follows_phone_language() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("en", MapboxGeocoder.languageFor("coffee"))
            Locale.setDefault(Locale.JAPAN)
            assertEquals("ja", MapboxGeocoder.languageFor("Tokyo"))
        } finally {
            Locale.setDefault(saved)
        }
    }
}
