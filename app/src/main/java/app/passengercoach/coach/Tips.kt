package app.passengercoach.coach

/** 신호로 알 수 있는 상황. 신호 대기인지, 주변에 차가 있는지 같은 것은 SDK에 신호가 없어 알 수 없다. */
enum class Situation { BEFORE_START, REVERSING, AFTER_PARK, LEFT_AHEAD, RIGHT_AHEAD, UTURN_AHEAD, STOPPED }

data class Tip(val title: String, val lines: List<String>)

/**
 * 상황에 맞는 "알아 두기"를 고른다. 지금 이렇게 하라는 지시가 아니라, 이런 규칙과 요령이 있다는 안내다.
 * (앱은 이 교차로가 비보호인지, 지금 신호가 무엇인지 모른다.)
 * shortcut: 문구는 도로교통법을 근거로 쓴 초안이며 전문가 검수 전, 검수 뒤 교체
 */
class TipPicker {
    private var hasDriven = false

    fun pick(gear: GearKind?, brakeOn: Boolean?, steeringDeg: Float?, navi: NaviTurnKind?, naviDistanceM: Double?): Situation? {
        if (gear == GearKind.DRIVE || gear == GearKind.REVERSE) hasDriven = true
        val naviNear = naviDistanceM != null && naviDistanceM <= CoachEngine.NAVI_NEAR_M
        return when (gear) {
            GearKind.PARK -> if (hasDriven) Situation.AFTER_PARK else Situation.BEFORE_START
            GearKind.REVERSE -> Situation.REVERSING
            GearKind.DRIVE -> when {
                naviNear && navi == NaviTurnKind.LEFT -> Situation.LEFT_AHEAD
                naviNear && navi == NaviTurnKind.RIGHT -> Situation.RIGHT_AHEAD
                naviNear && navi == NaviTurnKind.UTURN -> Situation.UTURN_AHEAD
                DriveState.isStopped(gear, brakeOn, steeringDeg) -> Situation.STOPPED
                else -> null
            }
            else -> null
        }
    }

    companion object {
        fun tip(s: Situation): Tip = when (s) {
            Situation.BEFORE_START -> Tip(
                "출발 전",
                listOf(
                    "좌석과 거울을 몸에 맞추고, 모든 좌석이 안전벨트를 매요. (도로교통법 제50조)",
                    "브레이크를 밟은 채로 기어를 바꾸고, 주변을 살핀 뒤 출발해요. (운전 요령)",
                ),
            )
            Situation.REVERSING -> Tip(
                "후진과 주차",
                listOf(
                    "후진할 때는 핸들을 돌린 쪽으로 차 뒤쪽이 움직여요.",
                    "뒤와 양옆을 직접 살피고, 브레이크로 속도를 조절하며 천천히 움직여요. (운전 요령)",
                ),
            )
            Situation.AFTER_PARK -> Tip(
                "주차한 뒤",
                listOf(
                    "기어를 P에 두고 주차 브레이크를 걸어요. (운전 요령)",
                    "문을 열기 전에 뒤에서 오는 차와 자전거를 확인해요. (도로교통법 제49조)",
                ),
            )
            Situation.LEFT_AHEAD -> Tip(
                "좌회전",
                listOf(
                    "방향지시등은 회전할 곳 30m 전부터 켜요. 고속도로는 100m 전이에요. (도로교통법 시행령 별표 2)",
                    "비보호 좌회전 표지가 있는 곳은 녹색 신호일 때, 마주 오는 차에 방해가 되지 않으면 좌회전할 수 있어요.",
                ),
            )
            Situation.RIGHT_AHEAD -> Tip(
                "우회전",
                listOf(
                    "방향지시등은 회전할 곳 30m 전부터 켜요. (도로교통법 시행령 별표 2)",
                    "빨간불에 우회전할 때는 정지선에서 일단 멈춘 뒤 가요. 우회전 신호등이 있으면 그 신호를 따라요. (도로교통법 시행규칙 별표 2)",
                ),
            )
            Situation.UTURN_AHEAD -> Tip(
                "유턴",
                listOf(
                    "유턴은 유턴이 허용된 곳에서만 해요.",
                    "표지에 신호 조건이 적혀 있으면 그 신호일 때만 해요.",
                ),
            )
            Situation.STOPPED -> Tip(
                "멈춰 있을 때",
                listOf(
                    "신호에 걸려 멈출 때는 정지선을 넘지 않아요. (도로교통법 시행규칙 별표 2)",
                    "앞차와는 간격을 넉넉히 둬요. (운전 요령)",
                ),
            )
        }
    }
}
