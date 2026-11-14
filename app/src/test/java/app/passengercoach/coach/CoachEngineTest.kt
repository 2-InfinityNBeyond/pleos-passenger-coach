package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachEngineTest {
    private fun CoachEngine.feed(
        steering: Float? = 0f,
        brake: Boolean? = false,
        turn: TurnDir? = TurnDir.OFF,
        gear: GearKind? = GearKind.DRIVE,
        at: Long = 0L,
    ): String? = onSignals(steering, brake, turn, gear, at)

    @Test
    fun `첫 관측은 해설하지 않는다`() {
        val e = CoachEngine()
        assertNull(e.feed(steering = 60f, brake = true, turn = TurnDir.LEFT, gear = GearKind.PARK))
    }

    @Test
    fun `S1 왼쪽 회전은 깜빡이 핸들 복귀 순서로 해설한다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertEquals("왼쪽 깜빡이를 켰어요.", e.feed(turn = TurnDir.LEFT, at = 1_000))
        assertEquals("깜빡이를 켠 뒤 핸들을 왼쪽으로 돌렸어요.", e.feed(turn = TurnDir.LEFT, steering = 25f, at = 3_000))
        assertEquals("핸들이 다시 가운데로 돌아왔어요.", e.feed(turn = TurnDir.LEFT, steering = 2f, at = 6_000))
        assertEquals("깜빡이를 껐어요.", e.feed(turn = TurnDir.OFF, steering = 0f, at = 7_000))
    }

    @Test
    fun `작은 핸들 조작은 조금 돌렸다고 해설하고 차선 변경이라고 단정하지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.feed(turn = TurnDir.RIGHT, at = 1_000)
        assertEquals("깜빡이를 켠 뒤 핸들을 오른쪽으로 조금 돌렸어요.", e.feed(turn = TurnDir.RIGHT, steering = -8f, at = 2_000))
        assertEquals("핸들이 다시 가운데로 돌아왔어요.", e.feed(turn = TurnDir.RIGHT, steering = -1f, at = 4_000))
    }

    @Test
    fun `기준보다 작은 흔들림은 해설하지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertNull(e.feed(steering = 4f, at = 1_000))
        assertNull(e.feed(steering = -5f, at = 2_000))
    }

    @Test
    fun `조금 돌린 뒤 더 돌리면 한 번 더 알려 준다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertEquals("핸들을 왼쪽으로 조금 돌렸어요.", e.feed(steering = 8f, at = 1_000))
        assertEquals("핸들을 왼쪽으로 더 돌렸어요.", e.feed(steering = 25f, at = 2_000))
        assertNull(e.feed(steering = 12f, at = 3_000))
    }

    @Test
    fun `오른쪽 회전은 음수 각도로 해설한다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertEquals("오른쪽 깜빡이를 켰어요.", e.feed(turn = TurnDir.RIGHT, at = 1_000))
        assertEquals("깜빡이를 켠 뒤 핸들을 오른쪽으로 돌렸어요.", e.feed(turn = TurnDir.RIGHT, steering = -30f, at = 2_000))
    }

    @Test
    fun `깜빡이와 반대 방향 핸들은 묶지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.feed(turn = TurnDir.LEFT, at = 1_000)
        assertEquals("핸들을 오른쪽으로 돌렸어요.", e.feed(turn = TurnDir.LEFT, steering = -30f, at = 2_000))
    }

    @Test
    fun `묶음 시간이 지나면 깜빡이와 연결하지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.feed(turn = TurnDir.LEFT, at = 1_000)
        assertEquals("핸들을 왼쪽으로 돌렸어요.", e.feed(turn = TurnDir.LEFT, steering = 30f, at = 1_000 + CoachEngine.LINK_WINDOW_MS + 1))
    }

    @Test
    fun `S2 브레이크 켜짐과 꺼짐을 해설한다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertEquals("브레이크를 밟았어요.", e.feed(brake = true, at = 1_000))
        assertEquals("브레이크를 뗐어요.", e.feed(brake = false, at = 2_000))
    }

    @Test
    fun `기어가 바뀌면 해설한다`() {
        val e = CoachEngine()
        e.feed(gear = GearKind.PARK, at = 0)
        assertEquals("기어를 P(주차)에서 D(주행)로 바꿨어요.", e.feed(gear = GearKind.DRIVE, at = 1_000))
    }

    @Test
    fun `내비 안내 뒤 깜빡이까지의 시간을 알려 준다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 200.0, nowMs = 1_000)
        assertEquals(
            "내비가 좌회전을 안내하고 4초 뒤, 왼쪽 깜빡이를 켰어요.",
            e.feed(turn = TurnDir.LEFT, at = 5_000),
        )
    }

    @Test
    fun `먼 안내는 가까워진 시점부터 잰다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 4_000.0, nowMs = 1_000)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 250.0, nowMs = 21_000)
        assertEquals(
            "내비가 좌회전을 안내하고 4초 뒤, 왼쪽 깜빡이를 켰어요.",
            e.feed(turn = TurnDir.LEFT, at = 25_000),
        )
    }

    @Test
    fun `같은 방향의 다음 안내는 앞 안내의 시각을 이어 쓰지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 80.0, nowMs = 1_000)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 2_000.0, nowMs = 10_000)
        assertEquals("왼쪽 깜빡이를 켰어요.", e.feed(turn = TurnDir.LEFT, at = 60_000))
        e.feed(turn = TurnDir.OFF, at = 61_000)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 250.0, nowMs = 100_000)
        assertEquals("내비가 좌회전을 안내하고 3초 뒤, 왼쪽 깜빡이를 켰어요.", e.feed(turn = TurnDir.LEFT, at = 103_000))
    }

    @Test
    fun `가운데를 거치지 않고 반대쪽으로 돌려도 해설한다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertEquals("핸들을 왼쪽으로 돌렸어요.", e.feed(steering = 40f, at = 1_000))
        assertEquals("핸들을 오른쪽으로 돌렸어요.", e.feed(steering = -40f, at = 2_000))
    }

    @Test
    fun `아직 멀리 있는 안내와 깜빡이는 시차를 말하지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.onNavi(NaviTurnKind.LEFT, distanceM = 4_000.0, nowMs = 1_000)
        assertEquals("왼쪽 깜빡이를 켰어요.", e.feed(turn = TurnDir.LEFT, at = 25_000))
    }

    @Test
    fun `내비 안내와 다른 방향 깜빡이는 시차를 말하지 않는다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        e.onNavi(NaviTurnKind.RIGHT, distanceM = 200.0, nowMs = 1_000)
        assertEquals("왼쪽 깜빡이를 켰어요.", e.feed(turn = TurnDir.LEFT, at = 5_000))
    }

    @Test
    fun `해설에는 평가나 점수 표현이 없다`() {
        val e = CoachEngine()
        val messages = mutableListOf<String>()
        e.feed(at = 0)
        listOf(
            e.feed(brake = true, at = 1_000),
            e.feed(turn = TurnDir.LEFT, at = 2_000),
            e.feed(turn = TurnDir.LEFT, steering = 40f, at = 3_000),
            e.feed(turn = TurnDir.LEFT, steering = 0f, at = 4_000),
            e.feed(turn = TurnDir.OFF, at = 5_000),
            e.feed(gear = GearKind.PARK, at = 6_000),
        ).forEach { if (it != null) messages += it }
        assertTrue(messages.isNotEmpty())
        val banned = listOf("급", "위험", "점", "잘", "못", "실수", "나쁜", "좋은")
        messages.forEach { m -> banned.forEach { w -> assertFalse("'$m' 에 평가 단어 '$w'", m.contains(w)) } }
    }

    @Test
    fun `값이 없는 신호는 무시한다`() {
        val e = CoachEngine()
        e.feed(at = 0)
        assertNull(e.onSignals(null, null, null, null, 1_000))
    }
}
