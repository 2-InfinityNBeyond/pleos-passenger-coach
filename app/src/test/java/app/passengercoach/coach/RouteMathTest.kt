package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMathTest {
    private val a = LatLon(37.42, 127.1268)
    private val b = RouteMath.offset(a, 0.0, 100.0)
    private val c = RouteMath.offset(b, 270.0, 100.0)
    private val route = listOf(a, b, c)

    @Test
    fun `거리와 방위를 잰다`() {
        assertEquals(100.0, RouteMath.distanceM(a, b), 0.5)
        assertEquals(0.0, RouteMath.bearing(a, b), 0.5)
        assertEquals(270.0, RouteMath.bearing(b, c), 0.5)
        assertEquals(200.0, RouteMath.lengthM(route), 1.0)
    }

    @Test
    fun `경로에서 벗어난 거리를 잰다`() {
        assertNull(RouteMath.distanceToRouteM(a, emptyList()))
        assertEquals(0.0, RouteMath.distanceToRouteM(RouteMath.offset(a, 0.0, 40.0), route)!!, 0.5)
        assertEquals(60.0, RouteMath.distanceToRouteM(RouteMath.offset(RouteMath.offset(a, 0.0, 40.0), 90.0, 60.0), route)!!, 1.0)
    }

    @Test
    fun `경로를 따라간 지점과 방위를 구한다`() {
        val (p, heading) = RouteMath.along(route, 150.0)
        assertEquals(270.0, heading, 0.5)
        assertEquals(50.0, RouteMath.distanceM(b, p), 1.0)
        assertEquals(0.0, RouteMath.distanceM(c, RouteMath.along(route, 999.0).first), 0.5)
    }

    @Test
    fun `꺾이는 지점과 방향을 찾는다`() {
        // 북쪽으로 가다 서쪽으로 꺾으면 좌회전
        assertEquals(listOf(true), RouteMath.turns(route).map { it.second })
        assertEquals(100.0, RouteMath.turns(route).first().first, 1.0)
        assertTrue(RouteMath.turns(listOf(a, b)).isEmpty())
    }

    @Test
    fun `표지판 이름은 아는 종류만 붙인다`() {
        assertEquals("어린이 보호구역", RoadSigns.caution("SCHOOL_ZONE"))
        assertNull(RoadSigns.caution("SOMETHING_NEW"))
        assertEquals("과속 단속 50", RoadSigns.camera("SPEED_CAMERA_FIX", 50))
        assertEquals("어린이 보호구역 단속 30", RoadSigns.camera("SPEED_CAMERA_SCHOOL_ZONE", 30))
        assertEquals(RoadSign("과속방지턱", 80), RoadSigns.nearest(listOf(RoadSign("사고 잦은 곳", 300), RoadSign("과속방지턱", 80))))
    }
}