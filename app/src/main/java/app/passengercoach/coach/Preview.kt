package app.passengercoach.coach

import kotlin.math.abs

/** 미리 가 보기에서 짚어 주는 곳의 종류 */
enum class SpotKind { LEFT, RIGHT, UTURN, ROUNDABOUT, OVERPASS, UNDERPASS, HIGHWAY, TOLL, BUSY, ARRIVE }

/** 경로 위의 짚어 줄 곳. [atM]은 출발점부터의 거리 */
data class Spot(val atM: Double, val kind: SpotKind, val name: String)

/** 미리 가 볼 경로. [sample]이면 앱에 넣어 둔 연습 코스(시험 자료)다 */
data class PreviewRoute(val points: List<LatLon>, val spots: List<Spot>, val lengthM: Double, val minutes: Int, val sample: Boolean = false)

/**
 * 미리 가 보기: 출발하기 전에 오늘 갈 길을 빠르게 따라가며, 회전교차로처럼 미리 알아 둘 곳에서 속도를 늦추고 요령을 보여 준다.
 * 계산만 하며 Android에 의존하지 않아 단위 테스트가 가능하다.
 */
object Preview {
    /** 길찾기 서비스(카카오모빌리티)의 안내 종류 번호를 짚어 줄 곳으로 바꾼다. 직진처럼 짚을 것이 없으면 null */
    fun kindOf(type: Int): SpotKind? = when (type) {
        1 -> SpotKind.LEFT
        2 -> SpotKind.RIGHT
        3 -> SpotKind.UTURN
        in 30..41, in 70..81 -> SpotKind.ROUNDABOUT
        14 -> SpotKind.OVERPASS
        15 -> SpotKind.UNDERPASS
        in 7..12, in 42..49 -> SpotKind.HIGHWAY
        84, 85, 300 -> SpotKind.TOLL
        101 -> SpotKind.ARRIVE
        else -> null
    }

    /**
     * 안내 지점과 막히는 구간을 거리 순서로 합친다.
     * [guides]: (출발점부터의 거리, 안내 종류 번호, 이름), [roads]: (시작 거리, 끝 거리, 혼잡도). 혼잡도 1 = 정체, 2 = 지체.
     * 이어진 막히는 구간은 하나로 묶고, [BUSY_MIN_M]보다 짧으면 짚지 않는다.
     */
    fun spots(guides: List<Triple<Double, Int, String>>, roads: List<Triple<Double, Double, Int>>): List<Spot> {
        val result = ArrayList<Spot>()
        guides.forEach { (at, type, name) -> kindOf(type)?.let { result += Spot(at, it, name) } }
        var start: Double? = null
        var end = 0.0
        fun close() {
            val s = start ?: return
            if (end - s >= BUSY_MIN_M) result += Spot(s, SpotKind.BUSY, "")
            start = null
        }
        for ((from, to, state) in roads) {
            if (state == 1 || state == 2) {
                if (start == null) start = from
                end = to
            } else close()
        }
        close()
        return result.sortedBy { it.atM }
    }

    /** 길 모양만 있을 때(연습 코스): 꺾이는 곳을 회전으로, 끝을 도착으로 짚는다 */
    fun fromShape(points: List<LatLon>): PreviewRoute {
        val length = RouteMath.lengthM(points)
        val spots = RouteMath.turns(points).map { Spot(it.first, if (it.second) SpotKind.LEFT else SpotKind.RIGHT, "") } + Spot(length, SpotKind.ARRIVE, "")
        return PreviewRoute(points, spots, length, minutes = (length / 250).toInt().coerceAtLeast(1), sample = true)
    }

    /** 지금 지나고 있는 짚어 줄 곳(조금 앞에서부터 조금 지날 때까지). 없으면 null */
    fun current(atM: Double, spots: List<Spot>): Spot? = spots.firstOrNull { atM >= it.atM - BEFORE_M && atM <= it.atM + AFTER_M }

    /** 다음에 나올 짚어 줄 곳 */
    fun next(atM: Double, spots: List<Spot>): Spot? = spots.firstOrNull { it.atM > atM }

    /** 따라가는 속도(m/s): 짚어 줄 곳에서는 읽을 수 있게 느리게, 나머지는 빠르게 */
    fun speed(atM: Double, spots: List<Spot>): Double = if (current(atM, spots) != null) SLOW_MPS else FAST_MPS

    /**
     * 그 지점에서 운전자가 하게 될 조작의 예상: 깜빡이(왼쪽 true, 오른쪽 false, 없음 null)와 핸들 각도(도, 왼쪽이 양수).
     * 실제 조작이 아니라 길 모양으로 어림한 그림이다. 깜빡이는 회전할 곳 30m 전부터 켠다(도로교통법 시행령 별표 2).
     */
    fun expected(atM: Double, route: PreviewRoute): Pair<Boolean?, Float> {
        val turn = route.spots.firstOrNull { it.kind in TURNS && atM >= it.atM - SIGNAL_M && atM <= it.atM + AFTER_M }
        val blinker = when (turn?.kind) {
            SpotKind.LEFT, SpotKind.UTURN -> true
            SpotKind.RIGHT -> false
            else -> null
        }
        // 조금 뒤와 조금 앞의 진행 방향 차이로 핸들을 어림한다
        val before = RouteMath.along(route.points, atM - LOOK_M).second
        val after = RouteMath.along(route.points, atM + LOOK_M).second
        val change = ((after - before + 540.0) % 360.0) - 180.0
        // 완만하게 굽은 길은 핸들을 거의 안 돌리므로 그리지 않는다(안내와 반대쪽으로 살짝 도는 그림이 헷갈림을 준다)
        val wheel = if (abs(change) < 20.0) 0f else (-change * WHEEL_PER_DEG).toFloat().coerceIn(-MAX_WHEEL, MAX_WHEEL)
        return blinker to wheel
    }

    /**
     * 그곳에서 알아 둘 것: (한 줄 요령, 덧붙이는 설명). 지시가 아니라 이런 규칙과 요령이 있다는 안내다.
     * (앱은 그 교차로가 비보호인지, 차로가 몇 개인지 모른다.)
     * shortcut: 문구는 도로교통법을 근거로 쓴 초안이며 전문가 검수 전, 검수 뒤 교체
     */
    fun tip(kind: SpotKind): Pair<String, String> = when (kind) {
        SpotKind.LEFT -> "좌회전 전에 미리 왼쪽 차로로 옮겨요" to "비보호 표지가 있으면 녹색 신호에서, 마주 오는 차가 없을 때 가요"
        SpotKind.RIGHT -> "우회전 전에 미리 오른쪽 차로로 옮겨요" to "빨간불이면 정지선에서 일단 멈추고, 횡단보도를 살핀 뒤 가요"
        SpotKind.UTURN -> "유턴은 표지가 있는 곳에서만 해요" to "표지에 신호가 적혀 있으면 그 신호일 때만 돌아요"
        SpotKind.ROUNDABOUT -> "회전교차로는 안에서 도는 차가 먼저예요" to "들어갈 때는 천천히, 나갈 때는 오른쪽 깜빡이를 켜요"
        SpotKind.OVERPASS -> "고가도로 입구에서 차로가 갈라져요" to "표지를 보고 갈 차로를 미리 정해요"
        SpotKind.UNDERPASS -> "지하차도에 들어가기 전에 차로를 정해요" to "안에서는 보통 실선이라 차로를 바꿀 수 없어요"
        SpotKind.HIGHWAY -> "큰길에 합류할 때는 속도를 맞춰요" to "깜빡이를 켜고, 달리는 차의 흐름을 본 뒤 들어가요"
        SpotKind.TOLL -> "요금소 앞에서는 속도를 줄여요" to "하이패스 차로와 일반 차로를 미리 구분해요"
        SpotKind.BUSY -> "이 근처는 지금 차가 많아요" to "차로를 바꿀 때는 깜빡이 먼저, 옆 차로 뒤차와의 거리를 확인하고 천천히 옮겨요"
        SpotKind.ARRIVE -> "목적지 근처예요" to "어디에 세울지 미리 생각해 둬요"
    }

    fun title(kind: SpotKind): String = when (kind) {
        SpotKind.LEFT -> "좌회전"
        SpotKind.RIGHT -> "우회전"
        SpotKind.UTURN -> "유턴"
        SpotKind.ROUNDABOUT -> "회전교차로"
        SpotKind.OVERPASS -> "고가도로"
        SpotKind.UNDERPASS -> "지하차도"
        SpotKind.HIGHWAY -> "합류"
        SpotKind.TOLL -> "요금소"
        SpotKind.BUSY -> "막히는 구간"
        SpotKind.ARRIVE -> "도착"
    }

    private val TURNS = setOf(SpotKind.LEFT, SpotKind.RIGHT, SpotKind.UTURN)

    /** 짚어 줄 곳의 이만큼 앞에서부터 느리게 간다 */
    const val BEFORE_M = 90.0
    const val AFTER_M = 30.0
    const val SIGNAL_M = 30.0
    const val FAST_MPS = 90.0
    const val SLOW_MPS = 18.0
    const val BUSY_MIN_M = 150.0
    private const val LOOK_M = 25.0
    private const val WHEEL_PER_DEG = 2.5
    private const val MAX_WHEEL = 270f
}
