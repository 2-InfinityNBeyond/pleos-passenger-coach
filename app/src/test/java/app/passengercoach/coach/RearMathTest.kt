package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RearMathTest {
    @Test
    fun `핸들이 가운데면 곧게 뒤로 간다`() {
        assertNull(RearMath.radius(0f))
        val p = RearMath.advance(RearMath.Pose(), 0f, 2f)
        assertEquals(0f, p.l, 0.001f)
        assertEquals(2f, p.b, 0.001f)
        assertEquals(0f, p.alpha, 0.001f)
    }

    @Test
    fun `핸들을 왼쪽으로 돌리고 후진하면 차 뒤쪽이 왼쪽으로 간다`() {
        // SDK 값은 양수가 왼쪽
        val left = RearMath.advance(RearMath.Pose(), 360f, 2f)
        assertTrue(left.l < -0.1f && left.b > 1.5f && left.alpha > 0f)
        val right = RearMath.advance(RearMath.Pose(), -360f, 2f)
        assertEquals(-left.l, right.l, 0.001f)
        assertEquals(left.b, right.b, 0.001f)
    }

    @Test
    fun `나눠서 움직여도 한 번에 움직인 것과 같은 자리에 온다`() {
        val once = RearMath.advance(RearMath.Pose(), 300f, 2.4f)
        var step = RearMath.Pose()
        repeat(24) { step = RearMath.advance(step, 300f, 0.1f) }
        assertEquals(once.l, step.l, 0.01f)
        assertEquals(once.b, step.b, 0.01f)
        assertEquals(once.alpha, step.alpha, 0.001f)
    }

    @Test
    fun `가이드선은 차 폭만큼 떨어져 시작하고 핸들 쪽으로 휜다`() {
        val l = RearMath.guide(360f, -1)
        val r = RearMath.guide(360f, 1)
        assertEquals(-RearMath.HALF_TRACK_M, l.first().l, 0.001f)
        assertEquals(RearMath.HALF_TRACK_M, r.first().l, 0.001f)
        assertTrue(l.last().l < l.first().l && r.last().l < r.first().l)
    }

    @Test
    fun `차에서 본 좌표는 차가 움직인 만큼 가까워진다`() {
        val p = RearMath.advance(RearMath.Pose(), 0f, 1f)
        val seen = RearMath.seenFrom(p, 0.5f, 4f)
        assertEquals(0.5f, seen.l, 0.001f)
        assertEquals(3f, seen.b, 0.001f)
    }
}