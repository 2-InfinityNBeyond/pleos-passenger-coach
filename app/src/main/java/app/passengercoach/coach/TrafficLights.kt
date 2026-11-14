package app.passengercoach.coach

import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class LightColor { RED, YELLOW, GREEN, UNKNOWN }

/** 한 신호(직진 또는 좌회전)의 상태와, 받은 시점에 남아 있던 시간 */
data class LightPhase(val color: LightColor, val remainMs: Long)

data class Intersection(val cityCode: String, val id: String, val name: String, val lat: Double, val lon: Double)

/** 화면에 보여 줄 가까운 교차로의 신호. [atMs]는 값을 받은 시각 */
data class LightInfo(
    val name: String,
    val distanceM: Int,
    val armLabel: String,
    val straight: LightPhase?,
    val left: LightPhase?,
    val atMs: Long,
)

/**
 * 교차로 신호등 실시간 정보(공공데이터포털, 한국지역정보개발원) 해석. Android에 의존하지 않아 단위 테스트가 가능하다.
 * 제공 기관 고지: 실제 신호와 다를 수 있으므로 참고용이다.
 */
object TrafficLights {
    /** 이 거리(미터) 안에 실시간 신호가 있는 교차로가 있을 때만 보여 준다 */
    const val NEAR_M = 300.0

    /** 값을 받은 지 이 시간이 넘으면 믿지 않고 지운다 */
    const val MAX_AGE_MS = 30_000L

    fun color(status: String?): LightColor = when {
        status.isNullOrBlank() -> LightColor.UNKNOWN
        status.startsWith("stop", ignoreCase = true) -> LightColor.RED
        status.contains("clearance", ignoreCase = true) -> LightColor.YELLOW
        status.contains("Movement-Allowed", ignoreCase = true) -> LightColor.GREEN
        else -> LightColor.UNKNOWN
    }

    /**
     * 응답의 잔여 시간. 문서에는 "센티초"라고 적혀 있지만, 6초 간격으로 두 번 받아 비교하니 값이 6000 줄었다.
     * 그래서 밀리초로 읽는다(2026-11-15 측정).
     */
    fun phase(status: String?, remain: String?): LightPhase? {
        val ms = remain?.trim()?.toLongOrNull() ?: return null
        val c = color(status)
        return if (c == LightColor.UNKNOWN) null else LightPhase(c, ms)
    }

    private val ARMS = listOf("nt" to "북쪽", "ne" to "북동쪽", "et" to "동쪽", "se" to "남동쪽", "st" to "남쪽", "sw" to "남서쪽", "wt" to "서쪽", "nw" to "북서쪽")

    /**
     * 차가 향하는 방위(0 = 북, 시계 방향)로 응답의 방향 칸을 고른다.
     * shortcut: 방향 이름이 "차가 들어오는 접근로의 방위"라고 가정했다(북쪽으로 가는 차는 남쪽 접근로).
     * 문서에 정의가 없어 확인하지 못했다. 실제 교차로에서 대조한 뒤 틀리면 180도를 빼면 된다
     */
    fun armFor(headingDeg: Double): Pair<String, String> {
        val from = ((headingDeg + 180.0) % 360.0 + 360.0) % 360.0
        return ARMS[((from + 22.5) / 45.0).toInt() % 8]
    }

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6_371_000.0 * asin(sqrt(a))
    }

    fun nearest(lat: Double, lon: Double, all: Collection<Intersection>, maxM: Double = NEAR_M): Pair<Intersection, Double>? =
        all.map { it to distanceM(lat, lon, it.lat, it.lon) }.filter { it.second <= maxM }.minByOrNull { it.second }

    /** 지금 남은 시간(초, 올림). 이미 지났거나 값이 너무 오래됐으면 null */
    fun remainSeconds(phase: LightPhase, atMs: Long, nowMs: Long): Int? {
        val age = nowMs - atMs
        if (age < 0 || age > MAX_AGE_MS) return null
        val left = phase.remainMs - age
        return if (left <= 0) null else ceil(left / 1000.0).toInt()
    }

    fun colorName(c: LightColor): String = when (c) {
        LightColor.RED -> "빨강"
        LightColor.YELLOW -> "노랑"
        LightColor.GREEN -> "초록"
        LightColor.UNKNOWN -> "-"
    }
}
