package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioCleanupEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CallPulse AI", appName)
    }

    @Test
    fun `audio cleanup engine processes pcm and computes diagnostics`() {
        val engine = AudioCleanupEngine(16000)
        assertNotNull(engine)

        // Generate 16kHz test tone PCM (100ms = 1600 samples = 3200 bytes)
        val sampleCount = 1600
        val pcmBytes = ByteArray(sampleCount * 2)
        for (i in 0 until sampleCount) {
            val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / 16000.0) * 16000.0).toInt()
            pcmBytes[i * 2] = (sample and 0xFF).toByte()
            pcmBytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }

        val cleaned = engine.processPcm(pcmBytes, pcmBytes.size)
        assertEquals(pcmBytes.size, cleaned.size)

        val diagnostics = engine.getDiagnostics()
        assertEquals(16000, diagnostics.sampleRate)
        assertTrue(diagnostics.currentLufs < 0.0)
    }

    @Test
    fun `launch MainActivity without crash`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        assertNotNull(controller.get())
    }
}
