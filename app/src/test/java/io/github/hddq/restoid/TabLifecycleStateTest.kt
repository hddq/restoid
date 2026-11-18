package io.github.hddq.restoid

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Test

class TabLifecycleStateTest {

    @Test
    fun activeTabMirrorsParentState() {
        assertEquals(
            Lifecycle.State.RESUMED,
            resolveTabLifecycleState(Lifecycle.State.RESUMED, isActive = true)
        )
        assertEquals(
            Lifecycle.State.STARTED,
            resolveTabLifecycleState(Lifecycle.State.STARTED, isActive = true)
        )
        assertEquals(
            Lifecycle.State.CREATED,
            resolveTabLifecycleState(Lifecycle.State.CREATED, isActive = true)
        )
        assertEquals(
            Lifecycle.State.DESTROYED,
            resolveTabLifecycleState(Lifecycle.State.DESTROYED, isActive = true)
        )
    }

    @Test
    fun inactiveTabCapsResumedParentToStartedAndAllowsDestroy() {
        assertEquals(
            Lifecycle.State.STARTED,
            resolveTabLifecycleState(Lifecycle.State.RESUMED, isActive = false)
        )
        assertEquals(
            Lifecycle.State.STARTED,
            resolveTabLifecycleState(Lifecycle.State.STARTED, isActive = false)
        )
        assertEquals(
            Lifecycle.State.CREATED,
            resolveTabLifecycleState(Lifecycle.State.CREATED, isActive = false)
        )
        assertEquals(
            Lifecycle.State.DESTROYED,
            resolveTabLifecycleState(Lifecycle.State.DESTROYED, isActive = false)
        )
    }
}
