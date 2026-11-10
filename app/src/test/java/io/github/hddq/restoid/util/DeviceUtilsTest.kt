package io.github.hddq.restoid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceUtilsTest {

    @Test
    fun sanitizeHostname_removesNewlinesAndTabs() {
        val input = "My\nDevice\r\nName\t"
        assertEquals("My Device Name", DeviceUtils.sanitizeHostname(input))
    }

    @Test
    fun sanitizeHostname_trimsWhitespace() {
        val input = "   Pixel 8 Pro   "
        assertEquals("Pixel 8 Pro", DeviceUtils.sanitizeHostname(input))
    }

    @Test
    fun sanitizeHostname_handlesCleanString() {
        val input = "POCO F5"
        assertEquals("POCO F5", DeviceUtils.sanitizeHostname(input))
    }

    @Test
    fun sanitizeHostname_collapsesMultipleSpaces() {
        val input = "My    Custom   Device"
        assertEquals("My Custom Device", DeviceUtils.sanitizeHostname(input))
    }

    @Test
    fun sanitizeHostname_handlesEmptyString() {
        assertEquals("", DeviceUtils.sanitizeHostname("   \n\t  "))
    }
}
