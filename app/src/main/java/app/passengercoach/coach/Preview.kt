package app.passengercoach.coach

import kotlin.math.abs

/** 미리 가 보기에서 짚어 주는 곳의 종류 */
enum class SpotKind { LEFT, RIGHT, UTURN, ROUNDABOUT, OVERPASS, UNDERPASS, HIGHWAY, TOLL, BUSY, BUMP, CAMERA, SCHOOL, ARRIVE }

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

    /**
     * 지도 자료에서 찾은 길 위의 시설(과속방지턱, 단속 카메라, 학교)을 짚어 줄 곳에 더한다.
     * 길에서 [MARK_NEAR_M](학교는 [SCHOOL_NEAR_M]) 안에 있는 것만, 같은 종류는 [MARK_GAP_M]마다 하나만 넣는다. 신호등은 지도에만 그린다.
     */
    fun withMarks(route: PreviewRoute, marks: List<Mark>): PreviewRoute {
        val added = ArrayList<Spot>()
        for (m in marks) {
            val kind = when (m.kind) {
                MarkKind.BUMP -> SpotKind.BUMP
                MarkKind.CAMERA -> SpotKind.CAMERA
                MarkKind.SCHOOL -> SpotKind.SCHOOL
                MarkKind.SIGNAL -> continue
            }
            val at = RouteMath.alongOf(route.points, m.at)
            val far = RouteMath.distanceM(RouteMath.along(route.points, at).first, m.at)
            if (far > (if (kind == SpotKind.SCHOOL) SCHOOL_NEAR_M else MARK_NEAR_M)) continue
            if (added.none { it.kind == kind && abs(it.atM - at) < MARK_GAP_M }) added += Spot(at, kind, "")
        }
        return route.copy(spots = (route.spots + added).sortedBy { it.atM })
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
     * 그곳에서 알아 둘 것: (한 줄 요령, 덧붙이는 설명). 지시가 아니라 이런 요령이 있다는 안내다.
     * 깜빡이를 켜라 같은 기본은 빼고, 실제로 운전할 때 도움이 되는 것을 고른다.
     * 회전 뒤 곧바로 다음 회전이 나오면 차로를 어떻게 잡을지를 먼저 알려 준다. 같은 종류가 여러 번 나오면 다른 요령을 돌아가며 보여 준다.
     * (앱은 그 교차로가 비보호인지, 차로가 몇 개인지 모른다.)
     * shortcut: 운전 요령과 도로교통법을 근거로 쓴 초안이며 전문가 검수 전, 검수 뒤 교체
     */
    fun tip(route: PreviewRoute, spot: Spot): Pair<String, String> {
        val next = route.spots.firstOrNull { it.kind in TURNS && it.atM > spot.atM }
        if (spot.kind in TURNS && next != null && next.atM - spot.atM <= CHAIN_M) {
            val d = ((next.atM - spot.atM) / 10).toInt() * 10
            val side = if (leftSide(next.kind)) "왼쪽" else "오른쪽"
            return if (leftSide(spot.kind) == leftSide(next.kind)) {
                "${title(spot.kind)}하고 ${d}m 뒤에 또 ${title(next.kind)}이에요" to "돌고 나서 차로를 바꾸지 말고 $side 차로를 그대로 지켜요"
            } else {
                "${title(spot.kind)}하고 ${d}m 뒤에 바로 ${title(next.kind)}이에요" to "돌자마자 $side 차로로 옮겨야 해요. 도는 동안 거울로 뒤차를 미리 봐 둬요"
            }
        }
        val pool = TIPS.getValue(spot.kind)
        return pool[route.spots.filter { it.kind == spot.kind }.indexOf(spot).coerceAtLeast(0) % pool.size]
    }

    private fun leftSide(kind: SpotKind) = kind == SpotKind.LEFT || kind == SpotKind.UTURN

    private val TIPS: Map<SpotKind, List<Pair<String, String>>> = mapOf(
        SpotKind.LEFT to listOf(
            "교차로 바닥의 흰 점선(유도선)을 따라 돌아요" to "시선은 보닛 앞이 아니라 들어갈 차로 멀리. 차는 보는 쪽으로 가요",
            "좌회전 차로가 둘이면 내 차로 그대로 돌아요" to "돌면서 옆 차로로 넘어가면 나란히 도는 차와 부딪혀요",
            "비보호 좌회전은 초록불에, 맞은편이 비었을 때만" to "맞은편 차는 보기보다 빨리 와요. 애매하면 다음 신호를 기다려요",
            "노란불에 앞차를 따라 들어가지 않아요" to "교차로 안에 갇히면 가로로 오는 차를 막게 돼요",
        ),
        SpotKind.RIGHT to listOf(
            "뒷바퀴는 앞바퀴보다 안쪽으로 돌아요" to "연석에 바짝 붙어 돌면 뒷바퀴가 턱을 타요. 조금 띄우고 돌아요",
            "우회전은 오른쪽 사이드미러부터 봐요" to "차와 연석 사이로 오토바이와 자전거가 들어와요",
            "앞 신호가 빨간불이면 정지선에서 완전히 멈춰요" to "돌고 나서 만나는 횡단보도에 사람이 있으면 한 번 더 멈춰요",
            "돌자마자 서 있는 차가 있을 수 있어요" to "버스 정류장, 택시, 짐 내리는 차. 도는 동안 속도를 미리 줄여 둬요",
        ),
        SpotKind.UTURN to listOf(
            "유턴은 앞차부터 차례로 돌아요" to "뒤에서 먼저 돌면 앞차와 겹쳐요. 맞은편에서 우회전해 오는 차도 살펴요",
            "핸들을 끝까지 돌리고 천천히 돌아요" to "한 번에 못 돌 것 같으면 무리하지 말고 멈췄다가 후진해요",
        ),
        SpotKind.ROUNDABOUT to listOf(
            "들어갈 땐 왼쪽, 나갈 땐 오른쪽 깜빡이" to "안에서 돌고 있는 차가 먼저예요. 왼쪽에서 오는 차를 보고 들어가요",
            "출구를 놓치면 한 바퀴 더 돌면 돼요" to "급하게 꺾지 않아요. 안에서는 멈추지 말고 흐름대로 돌아요",
        ),
        SpotKind.OVERPASS to listOf(
            "바닥의 분홍·초록 유도선을 따라가요" to "고가 입구에서 차로가 갈라져요. 300m 전부터 탈 차로에 서 있어요",
        ),
        SpotKind.UNDERPASS to listOf(
            "지하차도 안은 실선이라 차로를 못 바꿔요" to "들어가기 전에 차로를 정해요. 어두워지니 앞차와 거리를 벌려요",
        ),
        SpotKind.HIGHWAY to listOf(
            "가속 차로를 끝까지 써서 속도를 맞춰요" to "느리게 끼어드는 게 더 위험해요. 사이드미러, 어깨 너머, 그다음 진입",
            "본선 차가 먼저예요. 들어갈 틈은 차 뒤에서 찾아요" to "따라 들어갈 차 한 대를 정하고 그 뒤에 붙는다고 생각하면 쉬워요",
        ),
        SpotKind.TOLL to listOf(
            "바닥이 파란 차로는 하이패스예요" to "단말기 없이 잘못 들어가도 멈추거나 후진하지 않아요. 지나간 뒤에 내면 돼요",
        ),
        SpotKind.BUSY to listOf(
            "사이드미러 안쪽 절반에 뒤차가 보이면 들어갈 수 있어요" to "깜빡이를 켜고 3초, 고개를 돌려 옆을 직접 보고, 속도를 줄이지 않고 옮겨요",
            "멈출 땐 앞차 뒷바퀴가 보이는 거리에 서요" to "앞차가 못 움직여도 돌아 나갈 수 있고, 뒤에서 받혀도 앞차에 닿지 않아요",
            "차로가 줄어들면 한 대씩 번갈아 들어가요" to "실선에서는 끼어들 수 없어요. 점선 구간에서 미리 옮겨요",
        ),
        SpotKind.BUMP to listOf(
            "과속방지턱은 넘기 직전에 브레이크를 떼요" to "밟은 채 넘으면 앞이 눌려 더 크게 덜컹해요. 미리 줄이고 발을 떼고 넘어요",
        ),
        SpotKind.CAMERA to listOf(
            "단속 카메라 앞에서 급하게 밟지 않아요" to "뒤차가 놀라요. 제한속도 표지를 볼 때 미리 맞춰 두면 그냥 지나가요",
        ),
        SpotKind.SCHOOL to listOf(
            "학교 근처예요. 어린이 보호구역이면 시속 30 이하" to "보호구역의 신호 없는 횡단보도에서는 사람이 없어도 일단 멈춰요",
            "주차된 차 사이에서 아이가 뛰어나올 수 있어요" to "발을 브레이크 위에 올려 두고, 길 가장자리에서 조금 떨어져 달려요",
        ),
        SpotKind.ARRIVE to listOf(
            "도착하기 전에 세울 곳부터 정해요" to "목적지 앞에서 갑자기 서지 않아요. 깜빡이를 켜고 천천히 길가로 붙여요",
        ),
    )

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
        SpotKind.BUMP -> "과속방지턱"
        SpotKind.CAMERA -> "단속 카메라"
        SpotKind.SCHOOL -> "학교 앞"
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

    /** 회전 뒤 이 거리 안에 다음 회전이 있으면 차로 잡는 법을 먼저 알려 준다 */
    const val CHAIN_M = 250.0
    const val MARK_NEAR_M = 25.0
    const val SCHOOL_NEAR_M = 120.0
    const val MARK_GAP_M = 300.0
    private const val LOOK_M = 25.0
    private const val WHEEL_PER_DEG = 2.0
    private const val MAX_WHEEL = 270f
}
