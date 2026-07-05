package com.duynd.uthsynctask

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSettingsTest {
    @Test
    fun `notification mode should resolve from stored value`() {
        assertEquals(NotificationMode.OFF, NotificationMode.fromPreferenceValue("off"))
        assertEquals(NotificationMode.BASIC, NotificationMode.fromPreferenceValue("basic"))
        assertEquals(NotificationMode.FULL, NotificationMode.fromPreferenceValue("full"))
        assertEquals(NotificationMode.BASIC, NotificationMode.fromPreferenceValue("unknown"))
    }

    @Test
    fun `notification mode should expose a display label`() {
        assertEquals("Tắt", NotificationMode.OFF.label)
        assertEquals("Cơ bản", NotificationMode.BASIC.label)
        assertEquals("Đầy đủ", NotificationMode.FULL.label)
    }
}
