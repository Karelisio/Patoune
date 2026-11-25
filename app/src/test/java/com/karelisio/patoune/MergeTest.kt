package com.karelisio.patoune

import com.karelisio.patoune.domain.EventMerge
import com.karelisio.patoune.domain.FieldEvent
import com.karelisio.patoune.domain.FieldStore
import com.karelisio.patoune.domain.HlcClock
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class MergeTest {
    private fun ev(clock: HlcClock, device: String, row: String, field: String, value: String) =
        FieldEvent(UUID.randomUUID().toString(), "animal", row, field, JsonPrimitive(value), clock.tick().encode(), device)

    @Test fun threeDevicesConvergeWhateverTheOrder() {
        var t = 1000L
        val clocks = listOf("A", "B", "C").associateWith { d -> HlcClock(d) { t } }
        // Modifications concurrentes (même milliseconde) et successives, dont une suppression.
        val events = listOf(
            ev(clocks["A"]!!, "A", "cat", "name", "Mochi"),
            ev(clocks["B"]!!, "B", "cat", "name", "Moka"),
            ev(clocks["C"]!!, "C", "cat", "breed", "Européen"),
            ev(clocks["A"]!!, "A", "dog", "name", "Rex"),
        ).toMutableList()
        t = 2000L
        events += ev(clocks["C"]!!, "C", "cat", "name", "Mochi II")
        events += ev(clocks["B"]!!, "B", "dog", "deleted", "true")
        events += ev(clocks["A"]!!, "A", "cat", "breed", "Chartreux")

        val reference = EventMerge.reduce(events)
        repeat(50) { seed ->
            val stores = List(3) { FieldStore() }
            stores.forEachIndexed { i, s -> events.shuffled(kotlin.random.Random(seed * 3 + i)).forEach(s::apply) }
            // Chaque appareil rejoue aussi ses doublons : idempotent.
            stores.forEach { s -> events.take(3).forEach(s::apply) }
            stores.forEach { assertEquals(reference, it.snapshot()) }
        }
        assertEquals("\"Mochi II\"", reference["animal" to "cat"]!!["name"].toString())
        assertEquals("\"Chartreux\"", reference["animal" to "cat"]!!["breed"].toString())
        assertEquals("\"true\"", reference["animal" to "dog"]!!["deleted"].toString())
    }

    @Test fun tieOnClockIsBrokenByDevice() {
        val a = FieldEvent("1", "animal", "x", "name", JsonPrimitive("A"), "000000000001000-000000-A", "A")
        val b = FieldEvent("2", "animal", "x", "name", JsonPrimitive("B"), "000000000001000-000000-B", "B")
        assertEquals(EventMerge.reduce(listOf(a, b)), EventMerge.reduce(listOf(b, a)))
    }
}
