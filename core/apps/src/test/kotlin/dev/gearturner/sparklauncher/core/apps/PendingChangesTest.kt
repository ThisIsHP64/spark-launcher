package dev.gearturner.sparklauncher.core.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingChangesTest {

    @Test
    fun namesAreAnnouncedOnlyAfterTheReloadFinishes() {
        val pending = PendingChanges().apply { add(setOf("com.example.photos")) }
        val events = mutableListOf<String>()

        reloadThenAnnounce(pending, load = { events += "load" }, announce = { events += "announce ${it.sorted()}" })

        assertEquals(listOf("load", "announce [com.example.photos]"), events)
    }

    @Test
    fun aNameThatArrivesMidReloadWaitsForTheNextReload() {
        val pending = PendingChanges().apply { add(setOf("first")) }
        val announced = mutableListOf<Set<String>>()

        reloadThenAnnounce(pending, load = { pending.add(setOf("second")) }, announce = { announced += it })
        assertEquals(listOf(setOf("first")), announced)

        reloadThenAnnounce(pending, load = {}, announce = { announced += it })
        assertEquals(listOf(setOf("first"), setOf("second")), announced)
    }

    @Test
    fun aReloadWithNothingPendingAnnouncesNothing() {
        var announced = false

        val result = reloadThenAnnounce(PendingChanges(), load = { 42 }, announce = { announced = true })

        assertEquals(42, result)
        assertTrue(!announced)
    }

    @Test
    fun repeatedNamesAreAnnouncedOnce() {
        val pending = PendingChanges().apply {
            add(setOf("a", "b"))
            add(setOf("b"))
        }

        assertEquals(setOf("a", "b"), pending.drain())
        assertEquals(emptySet<String>(), pending.drain())
    }
}
