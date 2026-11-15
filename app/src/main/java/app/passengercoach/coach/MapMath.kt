package app.passengercoach.coach

import kotlin.math.cos
import kotlin.math.sin

/** 지도에 그릴 길 한 줄. [level]: 0 = 큰길, 1 = 중간 길, 2 = 동네 길, 3 = 진입로 같은 작은 길 */
data class Road(val points: List<LatLon>, val level: Int)

/** 지도에 칠할 넓은 곳 */
enum class AreaKind { BUILDING, GREEN, WATER }

data class Area(val points: List<LatLon>, val kind: AreaKind) {
    /** 멀리 있는 것을 빨리 걸러 내려고 가운데 점을 미리 구해 둔다 */
    val center: LatLon = LatLon(points.sumOf { it.lat } / points.size, points.sumOf { it.lon } / points.size)
}

/** 지도에 쓸 이름. [road]면 길 이름, 아니면 건물이나 장소 이름 */
data class MapLabel(val at: LatLon, val text: String, val road: Boolean)

/** 길 위의 시설: 신호등, 단속 카메라, 과속방지턱, 학교 */
enum class MarkKind { SIGNAL, CAMERA, BUMP, SCHOOL }

data class Mark(val at: LatLon, val kind: MarkKind)

/** 경로 주변의 지도 자료(OpenStreetMap) */
data class MapData(
    val roads: List<Road> = emptyList(),
    val areas: List<Area> = emptyList(),
    val waters: List<List<LatLon>> = emptyList(),
    val labels: List<MapLabel> = emptyList(),
    val marks: List<Mark> = emptyList(),
)

/**
 * 앱이 직접 그리는 지도의 좌표 계산. 진행 방향이 위로 가고, 앞유리 너머로 보듯 먼 곳이 작아진다.
 * Android에 의존하지 않아 단위 테스트가 가능하다.
 */
object MapMath {
    /** 차가 있는 줄에서 화면 너비에 담기는 거리(미터) */
    const val WIDTH_AT_CAR_M = 330.0

    /** 보는 눈이 차보다 이만큼(미터) 뒤에 있다고 본다. 작을수록 원근이 강해진다 */
    const val EYE_BACK_M = 240.0

    /** 눈에 이보다 가까운 곳은 그리지 않는다 */
    const val MIN_DEPTH_M = 120.0

    /** 지평선의 높이(화면 높이에 대한 비율, 위쪽 바깥이라 음수) */
    const val HORIZON = -0.10

    /** 차가 놓이는 높이(화면 높이에 대한 비율) */
    const val CAR_AT = 0.78

    /** 길의 폭(미터): 큰길부터 작은 길까지 */
    val ROAD_WIDTH_M = doubleArrayOf(14.0, 10.0, 7.0, 4.0)

    fun levelOf(highway: String): Int = when (highway.removeSuffix("_link")) {
        "motorway", "trunk", "primary" -> 0
        "secondary", "tertiary" -> 1
        "residential", "unclassified", "living_street" -> 2
        else -> 3
    }

    /** 차 기준 좌표: (오른쪽 미터, 앞쪽 미터). [headingDeg]: 0 = 북, 시계 방향 */
    fun local(car: LatLon, headingDeg: Double, p: LatLon): Pair<Double, Double> {
        val east = (p.lon - car.lon) * 111_320.0 * cos(Math.toRadians(car.lat))
        val north = (p.lat - car.lat) * 111_320.0
        val h = Math.toRadians(headingDeg)
        return (east * cos(h) - north * sin(h)) to (east * sin(h) + north * cos(h))
    }

    /** 화면 좌표와 그 자리의 축척(픽셀/미터): [x, y, scale]. 눈에 너무 가까우면 null */
    fun project(right: Double, ahead: Double, width: Double, height: Double): DoubleArray? {
        val depth = ahead + EYE_BACK_M
        if (depth < MIN_DEPTH_M) return null
        val near = EYE_BACK_M / depth
        val scale = width / WIDTH_AT_CAR_M * near
        val horizon = HORIZON * height
        return doubleArrayOf(width / 2 + right * scale, horizon + (CAR_AT * height - horizon) * near, scale)
    }

    /**
     * 선분을 그릴 수 있는 조각들로 바꾼다: 눈 뒤쪽은 잘라 내고, 폭이 자연스럽게 변하도록 [stepM] 미터마다 나눈다.
     * 돌려주는 값은 차 기준 좌표의 점들.
     */
    fun pieces(a: Pair<Double, Double>, b: Pair<Double, Double>, stepM: Double = 25.0): List<Pair<Double, Double>> {
        val minAhead = MIN_DEPTH_M - EYE_BACK_M
        if (a.second < minAhead && b.second < minAhead) return emptyList()
        var from = a
        var to = b
        if (from.second < minAhead) from = cut(from, to, minAhead)
        if (to.second < minAhead) to = cut(to, from, minAhead)
        val length = kotlin.math.hypot(to.first - from.first, to.second - from.second)
        val n = (length / stepM).toInt().coerceIn(1, 40)
        return (0..n).map { i -> (from.first + (to.first - from.first) * i / n) to (from.second + (to.second - from.second) * i / n) }
    }

    private fun cut(behind: Pair<Double, Double>, front: Pair<Double, Double>, minAhead: Double): Pair<Double, Double> {
        val t = (minAhead - behind.second) / (front.second - behind.second)
        return (behind.first + (front.first - behind.first) * t) to minAhead
    }
}
