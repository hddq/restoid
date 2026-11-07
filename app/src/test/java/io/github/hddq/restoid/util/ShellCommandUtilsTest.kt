package io.github.hddq.restoid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandUtilsTest {

    @Test
    fun escapeResticPattern_escapesLiteralGlobCharacters() {
        assertEquals(
            "/storage/emulated/0/Photos\\[1\\]\\*\\?",
            escapeResticPattern("/storage/emulated/0/Photos[1]*?")
        )
    }
}
