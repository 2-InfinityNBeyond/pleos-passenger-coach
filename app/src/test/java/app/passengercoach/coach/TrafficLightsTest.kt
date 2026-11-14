package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrafficLightsTest {
    @Test
    fun `상태 이름을 신호 색으로 읽는다`() {
        assertEquals(LightColor.RED, TrafficLights.color("stop-And-Remain"))
        assertEquals(LightColor.RED, TrafficLights.color("stop-Then-Processed"))
        assertEquals(LightColor.GREEN, TrafficLights.color("protected-Movement-Allowed"))
        assertEquals(LightColor.YELLOW, TrafficLights.color("protected-clearance"))
        assertEquals(LightColor.YELLOW, TrafficLights.color("permissive-clearance"))
        assertEquals(LightColor.UNKNOWN, TrafficLights.color(""))
        assertNull(TrafficLights.phase("", ""))
        assertNull(TrafficLights.phase("stop-And-Remain", ""))
        assertEquals(LightPhase(LightColor.RED, 37_000), TrafficLights.phase("stop-And-Remain", "37000"))
    }

    @Test
    fun `남은 시간은 받은 뒤 흐른 만큼 줄고 오래되면 쓰지 않는다`() {
        val p = LightPhase(LightColor.RED, 12_000)
        assertEquals(12, TrafficLights.remainSeconds(p, atMs = 1_000, nowMs = 1_000))
        assertEquals(9, TrafficLights.remainSeconds(p, atMs = 1_000, nowMs = 4_500))
        assertNull(TrafficLights.remainSeconds(p, atMs = 1_000, nowMs = 13_000))
        assertNull(TrafficLights.remainSeconds(LightPhase(LightColor.RED, 90_000), atMs = 0, nowMs = TrafficLights.MAX_AGE_MS + 1))
    }

    @Test
    fun `진행 방위의 반대쪽 접근로를 고른다`() {
        assertEquals("st" to "남쪽", TrafficLights.armFor(0.0))
        assertEquals("wt" to "서쪽", TrafficLights.armFor(90.0))
        assertEquals("nt" to "북쪽", TrafficLights.armFor(180.0))
        assertEquals("et" to "동쪽", TrafficLights.armFor(270.0))
        assertEquals("sw" to "남서쪽", TrafficLights.armFor(45.0))
        assertEquals("st" to "남쪽", TrafficLights.armFor(350.0))
    }

    @Test
    fun `가까운 교차로만 고른다`() {
        val a = Intersection("3100000000", "1", "가", 35.5122, 129.4307)
        val b = Intersection("3100000000", "2", "나", 35.5382, 129.3314)
        val hit = TrafficLights.nearest(35.5125, 129.4307, listOf(a, b))
        assertEquals("가", hit?.first?.name)
        assertEquals(33.0, hit!!.second, 3.0)
        assertNull(TrafficLights.nearest(35.6, 129.0, listOf(a, b)))
    }
}