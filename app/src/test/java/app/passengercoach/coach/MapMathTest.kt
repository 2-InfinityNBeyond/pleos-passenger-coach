package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapMathTest {
    private val car = LatLon(37.42, 127.1268)

    @Test
    fun `차는 화면 가운데 아래 정해진 자리에 온다`() {
        val p = MapMath.project(0.0, 0.0, 600.0, 300.0)!!
        assertEquals(300.0, p[0], 0.001)
        assertEquals(300.0 * MapMath.CAR_AT, p[1], 0.001)
    }

    @Test
    fun `앞쪽은 위로 오른쪽은 오른쪽으로 가고 멀수록 작아진다`() {
        val here = MapMath.project(0.0, 0.0, 600.0, 300.0)!!
        val ahead = MapMath.project(0.0, 200.0, 600.0, 300.0)!!
        val right = MapMath.project(50.0, 0.0, 600.0, 300.0)!!
        assertTrue(ahead[1] < here[1] && ahead[2] < here[2])
        assertTrue(right[0] > here[0])
        assertNull(MapMath.project(0.0, -MapMath.EYE_BACK_M, 600.0, 300.0))
    }

    @Test
    fun `진행 방향이 위가 되도록 돌린다`() {
        val north = RouteMath.offset(car, 0.0, 100.0)
        val east = RouteMath.offset(car, 90.0, 100.0)
        // 북쪽을 보고 있으면 북쪽 점이 앞, 동쪽 점이 오른쪽
        assertEquals(100.0, MapMath.local(car, 0.0, north).second, 1.0)
        assertEquals(100.0, MapMath.local(car, 0.0, east).first, 1.0)
        // 동쪽을 보고 있으면 동쪽 점이 앞, 북쪽 점이 왼쪽
        assertEquals(100.0, MapMath.local(car, 90.0, east).second, 1.0)
        assertEquals(-100.0, MapMath.local(car, 90.0, north).first, 1.0)
    }

    @Test
    fun `눈 뒤쪽으로 넘어가는 선분은 잘라 낸다`() {
        val minAhead = MapMath.MIN_DEPTH_M - MapMath.EYE_BACK_M
        assertTrue(MapMath.pieces(0.0 to -500.0, 0.0 to -400.0).isEmpty())
        val cut = MapMath.pieces(0.0 to -500.0, 0.0 to 100.0)
        assertEquals(minAhead, cut.first().second, 0.001)
        assertEquals(100.0, cut.last().second, 0.001)
    }

    @Test
    fun `길 종류를 네 단계로 나눈다`() {
        assertEquals(0, MapMath.levelOf("primary_link"))
        assertEquals(1, MapMath.levelOf("tertiary"))
        assertEquals(2, MapMath.levelOf("residential"))
        assertEquals(3, MapMath.levelOf("service"))
    }
}