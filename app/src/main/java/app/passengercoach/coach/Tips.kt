package app.passengercoach.coach

/** 신호로 알 수 있는 상황. 신호 대기인지, 주변에 차가 있는지 같은 것은 SDK에 신호가 없어 알 수 없다. */
enum class Situation { BEFORE_START, REVERSING, AFTER_PARK, LEFT_AHEAD, RIGHT_AHEAD, UTURN_AHEAD, STOPPED }

data class Tip(val title: String, val lines: List<String>)

/**
 * 상황에 맞는 "알아 두기"를 고른다. 지금 이렇게 하라는 지시가 아니라, 이런 규칙과 요령이 있다는 안내다.
 * (앱은 이 교차로가 비보호인지, 지금 신호가 무엇인지 모른다.)
 * 깜빡이를 켜라 같은 기본은 빼고, 실제로 운전할 때 도움이 되는 요령을 쓴다(사용자 피드백).
 * shortcut: 운전 요령과 도로교통법을 근거로 쓴 초안이며 전문가 검수 전, 검수 뒤 교체
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
                    "사이드미러는 내 차 옆면이 조금만 보이게 맞춰요. 옆 차로가 넓게 보여요.",
                    "출발할 때는 거울만 보지 않고 고개를 돌려 어깨 너머를 한 번 봐요.",
                ),
            )
            Situation.REVERSING -> Tip(
                "후진과 주차",
                listOf(
                    "후진은 핸들을 돌린 쪽으로 차 뒤가 가요. 헷갈리면 멈추고 다시 맞춰요.",
                    "뒤가 들어갈 때 앞머리는 반대쪽으로 크게 돌아요. 앞 모서리와 옆 차 사이도 봐요.",
                ),
            )
            Situation.AFTER_PARK -> Tip(
                "주차한 뒤",
                listOf(
                    "문은 먼 쪽 손으로 열어요. 몸이 돌아가서 뒤에서 오는 자전거가 보여요.",
                    "경사진 곳에서는 바퀴를 길 가장자리 쪽으로 돌려 두고 주차 브레이크를 걸어요.",
                ),
            )
            Situation.LEFT_AHEAD -> Tip(
                "좌회전",
                listOf(
                    "좌회전은 교차로 바닥의 흰 점선(유도선)을 따라 돌아요. 시선은 들어갈 차로 멀리.",
                    "비보호 좌회전은 초록불에, 맞은편이 비었을 때만 가요. 맞은편 차는 보기보다 빨리 와요.",
                ),
            )
            Situation.RIGHT_AHEAD -> Tip(
                "우회전",
                listOf(
                    "우회전할 때 뒷바퀴는 앞바퀴보다 안쪽으로 돌아요. 연석에서 조금 띄우고 돌아요.",
                    "앞 신호가 빨간불이면 정지선에서 완전히 멈춰요. 돌고 나서 횡단보도에 사람이 있으면 한 번 더 멈춰요.",
                ),
            )
            Situation.UTURN_AHEAD -> Tip(
                "유턴",
                listOf(
                    "유턴은 앞차부터 차례로 돌아요. 맞은편에서 우회전해 오는 차도 살펴요.",
                    "표지에 신호가 적혀 있으면 그 신호일 때만 돌아요. 한 번에 못 돌면 멈췄다가 후진해요.",
                ),
            )
            Situation.STOPPED -> Tip(
                "멈춰 있을 때",
                listOf(
                    "멈출 땐 앞차 뒷바퀴가 보이는 거리에 서요. 앞차가 못 움직여도 돌아 나갈 수 있어요.",
                    "초록불로 바뀌어도 바로 나가지 않아요. 가로로 늦게 지나가는 차를 먼저 봐요.",
                ),
            )
        }
    }
}
