package app.passengercoach.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewTest {
    @Test
    fun `안내 종류 번호를 짚어 줄 곳으로 바꾸고 직진은 뺀다`() {
        assertEquals(SpotKind.LEFT, Preview.kindOf(1))
        assertEquals(SpotKind.ROUNDABOUT, Preview.kindOf(35))
        assertEquals(SpotKind.ROUNDABOUT, Preview.kindOf(72))
        assertEquals(SpotKind.UNDERPASS, Preview.kindOf(15))
        assertEquals(SpotKind.ARRIVE, Preview.kindOf(101))
        assertNull(Preview.kindOf(0))
        assertNull(Preview.kindOf(100))
    }

    @Test
    fun `이어진 막히는 구간은 하나로 묶고 짧은 것은 뺀다`() {
        val guides = listOf(Triple(0.0, 100, "출발지"), Triple(500.0, 2, "사거리"), Triple(2000.0, 101, "목적지"))
        val roads = listOf(
            Triple(0.0, 100.0, 1),      // 100m 정체: 짧아서 뺀다
            Triple(100.0, 600.0, 4),
            Triple(600.0, 900.0, 2),    // 600~1300m가 이어진 막히는 구간
            Triple(900.0, 1300.0, 1),
            Triple(1300.0, 2000.0, 3),  // 서행은 짚지 않는다
        )
        val spots = Preview.spots(guides, roads)
        assertEquals(listOf(SpotKind.RIGHT, SpotKind.BUSY, SpotKind.ARRIVE), spots.map { it.kind })
        assertEquals(listOf(500.0, 600.0, 2000.0), spots.map { it.atM })
    }

    @Test
    fun `짚어 줄 곳 근처에서만 느리게 간다`() {
        val spots = listOf(Spot(1000.0, SpotKind.LEFT, ""))
        assertEquals(Preview.FAST_MPS, Preview.speed(500.0, spots), 0.0)
        assertEquals(Preview.SLOW_MPS, Preview.speed(950.0, spots), 0.0)
        assertEquals(Preview.SLOW_MPS, Preview.speed(1020.0, spots), 0.0)
        assertEquals(Preview.FAST_MPS, Preview.speed(1100.0, spots), 0.0)
        assertEquals(SpotKind.LEFT, Preview.next(0.0, spots)?.kind)
        assertNull(Preview.next(1000.0, spots))
    }

    @Test
    fun `회전 뒤 곧바로 다음 회전이 나오면 차로 잡는 법을 먼저 알려 준다`() {
        val spots = listOf(Spot(100.0, SpotKind.LEFT, ""), Spot(280.0, SpotKind.RIGHT, ""), Spot(900.0, SpotKind.RIGHT, ""), Spot(1500.0, SpotKind.ARRIVE, ""))
        val route = PreviewRoute(emptyList(), spots, 1500.0, 3)
        val chained = Preview.tip(route, spots[0])
        assertEquals("좌회전하고 180m 뒤에 바로 우회전이에요", chained.first)
        assertTrue(chained.second.startsWith("돌자마자 오른쪽 차로로"))
        // 다음 회전이 멀면 그 종류의 요령을, 같은 종류가 다시 나오면 다른 요령을 보여 준다
        val first = Preview.tip(route, spots[1]).first
        val second = Preview.tip(route, spots[2]).first
        assertEquals("뒷바퀴는 앞바퀴보다 안쪽으로 돌아요", first)
        assertEquals("우회전은 오른쪽 사이드미러부터 봐요", second)
        // 모든 종류에 요령이 있다
        SpotKind.entries.forEach { kind -> assertTrue(Preview.tip(route, Spot(5000.0, kind, "")).first.isNotBlank()) }
    }

    @Test
    fun `길 위의 시설은 길에 가까운 것만 넣고 같은 종류는 띄엄띄엄 넣는다`() {
        val start = LatLon(37.0, 127.0)
        val points = listOf(start, RouteMath.offset(start, 0.0, 1000.0))
        val route = PreviewRoute(points, listOf(Spot(1000.0, SpotKind.ARRIVE, "")), 1000.0, 2)
        fun at(aheadM: Double, sideM: Double) = RouteMath.offset(RouteMath.offset(start, 0.0, aheadM), 90.0, sideM)
        val marks = listOf(
            Mark(at(400.0, 3.0), MarkKind.BUMP),
            Mark(at(500.0, 3.0), MarkKind.BUMP),      // 앞의 것과 300m 안이라 뺀다
            Mark(at(600.0, 60.0), MarkKind.CAMERA),   // 길에서 멀어 뺀다
            Mark(at(800.0, 100.0), MarkKind.SCHOOL),  // 학교는 120m까지 넣는다
            Mark(at(200.0, 2.0), MarkKind.SIGNAL),    // 신호등은 지도에만 그린다
        )
        val spots = Preview.withMarks(route, marks).spots
        assertEquals(listOf(SpotKind.BUMP, SpotKind.SCHOOL, SpotKind.ARRIVE), spots.map { it.kind })
        assertEquals(400.0, spots[0].atM, 1.0)
        assertEquals(800.0, spots[1].atM, 1.0)
    }

    @Test
    fun `왼쪽으로 꺾이는 곳에서는 왼쪽 깜빡이와 왼쪽 핸들을 예상한다`() {
        // 북쪽으로 200m 간 뒤 서쪽(왼쪽)으로 200m
        val start = LatLon(37.0, 127.0)
        val corner = RouteMath.offset(start, 0.0, 200.0)
        val route = Preview.fromShape(listOf(start, corner, RouteMath.offset(corner, 270.0, 200.0)))
        assertTrue(route.sample)
        assertEquals(listOf(SpotKind.LEFT, SpotKind.ARRIVE), route.spots.map { it.kind })

        val (blinkerFar, wheelFar) = Preview.expected(100.0, route)
        assertNull(blinkerFar)
        assertEquals(0f, wheelFar, 0.01f)

        val (blinkerNear, _) = Preview.expected(180.0, route)
        assertEquals(true, blinkerNear)

        // 꺾이는 곳의 좌표를 경로 위의 거리로 되돌린다
        assertEquals(200.0, RouteMath.alongOf(route.points, corner), 0.5)
        assertEquals(300.0, RouteMath.alongOf(route.points, RouteMath.offset(RouteMath.offset(corner, 270.0, 100.0), 0.0, 8.0)), 0.5)

        val (_, wheelAt) = Preview.expected(200.0, route)
        assertTrue("왼쪽은 양수여야 한다: $wheelAt", wheelAt > 90f)
    }
}
