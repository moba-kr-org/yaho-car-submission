package ai.yaho.hyundai.core

import org.junit.Assert.*
import org.junit.Test

class RainGuidanceControllerTest {
    @Test fun onceOnlySpeechExpiresAndReentryDoesNotResetPersistentCooldown() {
        var stored: Long? = null
        var stops = 0
        val controller = RainGuidanceController({stored},{stored=it;true},{stops++})
        assertEquals("시험 안내",controller.update("시험 안내",true,1000,0))
        assertTrue(controller.canRead)
        assertEquals("시험 안내",controller.consumeSpeech(true))
        assertFalse(controller.canRead)
        assertNull(controller.consumeSpeech(true))
        assertNull(controller.update("시험 안내",true,13_000,12_000))
        assertTrue(stops>=2)
        val restarted = RainGuidanceController({stored},{stored=it;true},{})
        assertNull(restarted.update("시험 안내",true,14_000,0))
    }
    @Test fun withdrawalOrExpiryAndBackgroundInvalidateBannerAndPendingSpeech() {
        for (foreground in listOf(true,false)) {
            var stops = 0
            val controller = RainGuidanceController({null},{true},{stops++})
            controller.update("시험 안내",true,1000,0)
            assertNull(controller.update(if(foreground) null else "시험 안내",foreground,2000,1000))
            assertNull(controller.consumeSpeech(true))
            assertEquals(2,stops)
        }
    }
    @Test fun failedStorageOrClockRollbackCannotPresentOrSpeak() {
        assertNull(RainGuidanceController({null},{false},{}).update("시험",true,1000,0))
        val controller=RainGuidanceController({null},{true},{})
        controller.update("시험",true,1000,1000)
        assertNull(controller.update("시험",true,1001,999))
        assertNull(controller.consumeSpeech(true))
    }
    @Test fun unavailableVoiceDoesNotConsumeTheOnlyRequestAndDismissDoesNotReplay() {
        val controller=RainGuidanceController({null},{true},{})
        controller.update("시험",true,1000,0)
        assertNull(controller.consumeSpeech(false))
        assertEquals("시험",controller.consumeSpeech(true))
        controller.dismiss()
        assertNull(controller.update("시험",true,1001,1))
    }
}
