package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.webview.CounterDomScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("مراقب العداد", appName)
    }

    @Test
    fun `parseDomPayload parses totalCounter separately from secondary counters`() {
        val sampleJson = """
            {
              "url": "https://eshop.vodafone.com.eg/en/lines/red/numbers",
              "title": "Vodafone RED Numbers",
              "primary": {
                "selector": ".total-numbers",
                "value": "340",
                "numericValue": 340,
                "label": "Total Numbers",
                "kind": "TOTAL"
              },
              "totalCounter": {
                "selector": ".total-numbers",
                "value": "340",
                "numericValue": 340,
                "label": "Total Numbers",
                "kind": "TOTAL"
              },
              "candidates": [
                {
                  "selector": ".total-numbers",
                  "value": "340",
                  "numericValue": 340,
                  "label": "Total Numbers",
                  "kind": "TOTAL"
                },
                {
                  "selector": "#reservation-timer",
                  "value": "09:45",
                  "numericValue": 585,
                  "label": "Reservation Timer",
                  "kind": "TIMER"
                }
              ]
            }
        """.trimIndent()

        val payload = CounterDomScript.parseDomPayload(sampleJson)
        assertNotNull(payload)
        assertEquals("340", payload?.totalCounter?.value)
        assertEquals(340L, payload?.totalCounter?.numericValue)
        assertEquals("TOTAL", payload?.totalCounter?.kind)
        assertEquals(2, payload?.candidates?.size)
    }

    @Test
    fun `parsePickedPayload parses tapped DOM element`() {
        val pickedJson = """
            {
              "selector": ".total-count > span",
              "value": "250",
              "numericValue": 250,
              "label": "Total"
            }
        """.trimIndent()

        val picked = CounterDomScript.parsePickedPayload(pickedJson)
        assertNotNull(picked)
        assertEquals(".total-count > span", picked?.selector)
        assertEquals("250", picked?.value)
        assertEquals(250L, picked?.numericValue)
    }
}
