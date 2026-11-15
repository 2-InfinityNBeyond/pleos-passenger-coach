package app.passengercoach.coach

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class LatLon(val lat: Double, val lon: Double)

/** 지도에 그릴 경로 선의 계산. Android에 의존하지 않아 단위 테스트가 가능하다. 짧은 거리라 평면 근사를 쓴다. */
object RouteMath {
    private const val M_PER_DEG = 111_320.0

    /** [origin] 기준으로 동쪽(x), 북쪽(y) 미터 */
    private fun xy(origin: LatLon, p: LatLon): Pair<Double, Double> =
        (p.lon - origin.lon) * M_PER_DEG * cos(Math.toRadians(origin.lat)) to (p.lat - origin.lat) * M_PER_DEG

    fun distanceM(a: LatLon, b: LatLon): Double = xy(a, b).let { hypot(it.first, it.second) }

    /** a에서 b로 가는 방위(0 = 북, 시계 방향) */
    fun bearing(a: LatLon, b: LatLon): Double {
        val (x, y) = xy(a, b)
        return (Math.toDegrees(atan2(x, y)) + 360.0) % 360.0
    }

    /** [p]에서 [headingDeg] 방향으로 [meters] 떨어진 점 */
    fun offset(p: LatLon, headingDeg: Double, meters: Double): LatLon {
        val h = Math.toRadians(headingDeg)
        return LatLon(p.lat + meters * cos(h) / M_PER_DEG, p.lon + meters * sin(h) / (M_PER_DEG * cos(Math.toRadians(p.lat))))
    }

    fun lengthM(route: List<LatLon>): Double = route.zipWithNext { a, b -> distanceM(a, b) }.sum()

    /** 경로 선까지의 가장 짧은 거리(미터). 길에서 벗어났는지 판단하는 데 쓴다. 경로가 비었으면 null */
    fun distanceToRouteM(p: LatLon, route: List<LatLon>): Double? {
        if (route.isEmpty()) return null
        if (route.size == 1) return distanceM(p, route[0])
        return route.zipWithNext { a, b ->
            val (bx, by) = xy(a, b)
            val (px, py) = xy(a, p)
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            hypot(px - bx * t, py - by * t)
        }.min()
    }

    /**
     * [p]에서 가장 가까운 경로 위 지점까지, 출발점부터 경로를 따라 잰 거리(미터).
     * shortcut: 같은 곳을 두 번 지나는 경로에서는 먼저 지나는 쪽을 고른다
     */
    fun alongOf(route: List<LatLon>, p: LatLon): Double {
        var best = Double.MAX_VALUE
        var bestAt = 0.0
        var walked = 0.0
        for ((a, b) in route.zipWithNext()) {
            val (bx, by) = xy(a, b)
            val (px, py) = xy(a, p)
            val len = hypot(bx, by)
            val t = if (len == 0.0) 0.0 else ((px * bx + py * by) / (len * len)).coerceIn(0.0, 1.0)
            val d = hypot(px - bx * t, py - by * t)
            if (d < best) {
                best = d
                bestAt = walked + len * t
            }
            walked += len
        }
        return bestAt
    }

    /** 경로를 따라 [meters]만큼 간 지점과 그때의 진행 방위. 끝을 넘으면 끝 점 */
    fun along(route: List<LatLon>, meters: Double): Pair<LatLon, Double> {
        var left = meters.coerceAtLeast(0.0)
        for ((a, b) in route.zipWithNext()) {
            val d = distanceM(a, b)
            if (d > 0 && left <= d) return offset(a, bearing(a, b), left) to bearing(a, b)
            left -= d
        }
        val last = route.last()
        return last to if (route.size >= 2) bearing(route[route.size - 2], last) else 0.0
    }

    /** 방위가 [minDeg]도 넘게 바뀌는 지점들의 (거리, 왼쪽이면 true). 데모 경로의 안내를 만드는 데 쓴다 */
    fun turns(route: List<LatLon>, minDeg: Double = 45.0): List<Pair<Double, Boolean>> {
        val result = ArrayList<Pair<Double, Boolean>>()
        var walked = 0.0
        for (i in 1 until route.size - 1) {
            walked += distanceM(route[i - 1], route[i])
            val change = ((bearing(route[i], route[i + 1]) - bearing(route[i - 1], route[i]) + 540.0) % 360.0) - 180.0
            if (abs(change) >= minDeg) result += walked to (change < 0)
        }
        return result
    }
}
