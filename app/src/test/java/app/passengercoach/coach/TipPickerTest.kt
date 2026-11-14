package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TipPickerTest {
    @Test
    fun `상황에 맞는 알아 두기를 고른다`() {
        val p = TipPicker()
        assertEquals(Situation.BEFORE_START, p.pick(GearKind.PARK, true, 0f, null, null))
        assertNull(p.pick(GearKind.DRIVE, false, 0f, null, null))
        assertEquals(Situation.STOPPED, p.pick(GearKind.DRIVE, true, 0f, null, null))
        assertEquals(Situation.LEFT_AHEAD, p.pick(GearKind.DRIVE, false, 0f, NaviTurnKind.LEFT, 200.0))
        assertEquals(Situation.RIGHT_AHEAD, p.pick(GearKind.DRIVE, true, 0f, NaviTurnKind.RIGHT, 100.0))
        assertEquals(Situation.UTURN_AHEAD, p.pick(GearKind.DRIVE, false, 0f, NaviTurnKind.UTURN, 100.0))
        assertNull(p.pick(GearKind.DRIVE, false, 0f, NaviTurnKind.LEFT, 2_000.0))
        assertEquals(Situation.REVERSING, p.pick(GearKind.REVERSE, true, 300f, NaviTurnKind.LEFT, 100.0))
        assertEquals(Situation.AFTER_PARK, p.pick(GearKind.PARK, true, 0f, null, null))
        assertNull(p.pick(null, null, null, null, null))
    }

    @Test
    fun `모든 상황에 두 줄 이하의 알아 두기가 있고 지시나 평가 표현이 없다`() {
        val banned = listOf("하세요", "하십시오", "위험", "실수", "잘못")
        Situation.entries.forEach { s ->
            val tip = TipPicker.tip(s)
            assertTrue(tip.title.isNotBlank() && tip.lines.size in 1..2)
            tip.lines.forEach { line -> banned.forEach { w -> assertTrue("'$line' 에 '$w'", !line.contains(w)) } }
        }
    }
}