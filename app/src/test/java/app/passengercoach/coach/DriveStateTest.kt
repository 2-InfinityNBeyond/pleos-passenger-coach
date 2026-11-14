package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveStateTest {
    @Test
    fun `마지막 수신이 기준 시간 안이면 끊김이 아니다`() {
        assertFalse(DriveState.isStale(demo = false, hasSignal = true, lastUpdateMs = 1_000, nowMs = 1_000 + DriveState.STALE_MS))
    }

    @Test
    fun `마지막 수신이 기준 시간보다 오래되면 끊김이다`() {
        assertTrue(DriveState.isStale(demo = false, hasSignal = true, lastUpdateMs = 1_000, nowMs = 1_001 + DriveState.STALE_MS))
    }

    @Test
    fun `데모는 끊김 판정을 하지 않는다`() {
        assertFalse(DriveState.isStale(demo = true, hasSignal = true, lastUpdateMs = 1_000, nowMs = 1_000_000))
    }

    @Test
    fun `한 번도 값을 못 받았거나 신호가 없으면 끊김이 아니라 신호 없음 화면이 담당한다`() {
        assertFalse(DriveState.isStale(demo = false, hasSignal = true, lastUpdateMs = 0, nowMs = 1_000_000))
        assertFalse(DriveState.isStale(demo = false, hasSignal = false, lastUpdateMs = 1_000, nowMs = 1_000_000))
    }

    @Test
    fun `기어 D에서 브레이크가 켜져 있고 핸들이 가운데면 정차`() {
        assertTrue(DriveState.isStopped(GearKind.DRIVE, brakeOn = true, steeringDeg = 3f))
        assertTrue(DriveState.isStopped(GearKind.REVERSE, brakeOn = true, steeringDeg = -9f))
    }

    @Test
    fun `브레이크가 꺼져 있거나 핸들이 돌아가 있으면 정차가 아니다`() {
        assertFalse(DriveState.isStopped(GearKind.DRIVE, brakeOn = false, steeringDeg = 0f))
        assertFalse(DriveState.isStopped(GearKind.DRIVE, brakeOn = true, steeringDeg = 25f))
        assertFalse(DriveState.isStopped(GearKind.DRIVE, brakeOn = null, steeringDeg = 0f))
    }

    @Test
    fun `P와 N은 정차 판정과 관계없이 주차 중과 중립으로 표시한다`() {
        assertEquals("P 주차 중", DriveState.stateText(GearKind.PARK, brakeOn = false, steeringDeg = 50f))
        assertEquals("N 중립", DriveState.stateText(GearKind.NEUTRAL, brakeOn = true, steeringDeg = 0f))
    }

    @Test
    fun `D와 R 문구`() {
        assertEquals("D 정차 중", DriveState.stateText(GearKind.DRIVE, brakeOn = true, steeringDeg = 0f))
        assertEquals("D", DriveState.stateText(GearKind.DRIVE, brakeOn = false, steeringDeg = 0f))
        assertEquals("R", DriveState.stateText(GearKind.REVERSE, brakeOn = false, steeringDeg = 0f))
    }

    @Test
    fun `기어를 모르면 대시를 표시하고 시동 상태 문구는 만들지 않는다`() {
        assertEquals("-", DriveState.stateText(null, brakeOn = true, steeringDeg = 0f))
        listOf(GearKind.PARK, GearKind.NEUTRAL, GearKind.DRIVE, GearKind.REVERSE, GearKind.OTHER).forEach {
            val text = DriveState.stateText(it, brakeOn = true, steeringDeg = 0f)
            assertFalse("'$text'", text.contains("시동"))
        }
    }
}
