package app.passengercoach.coach

import kotlin.math.abs

enum class TurnDir { OFF, LEFT, RIGHT }

enum class GearKind { PARK, NEUTRAL, REVERSE, DRIVE, OTHER }

enum class NaviTurnKind { LEFT, RIGHT, OTHER }

/**
 * 코치 해설 규칙(요구서 F2 이벤트 감지, F3 코치 해설).
 * 점수나 평가 표현 없이, 일어난 일만 한 문장으로 알려 준다.
 * Android나 Pleos SDK에 의존하지 않아 단위 테스트가 가능하다.
 */
class CoachEngine {
    private var prevGear: GearKind? = null
    private var prevBrake: Boolean? = null
    private var prevTurn: TurnDir? = null

    private enum class Wheel { CENTER, SMALL, LARGE }

    private var steeringSeen = false
    private var wheel = Wheel.CENTER

    private var turnOnAtMs = 0L
    private var turnOnDir = TurnDir.OFF
    private var steeredAfterTurn = false

    private var naviKind: NaviTurnKind? = null
    private var naviSinceMs = 0L

    /**
     * 내비 안내가 [NAVI_NEAR_M] 이내로 가까워진 시점을 기억한다(안내 → 깜빡이 시차 계산용).
     * 먼 안내(예: 5km 앞 회전)부터 재면 "안내하고 N초 뒤"가 사실과 달라지기 때문이다.
     */
    fun onNavi(kind: NaviTurnKind?, distanceM: Double?, nowMs: Long) {
        if (kind == null) {
            naviKind = null
            return
        }
        val near = distanceM != null && distanceM <= NAVI_NEAR_M
        if (kind != naviKind) {
            naviKind = kind
            naviSinceMs = if (near) nowMs else 0L
        } else if (naviSinceMs == 0L && near) {
            naviSinceMs = nowMs
        }
    }

    /** 새 신호를 반영하고, 해설할 일이 생겼으면 문장을 돌려준다. 없으면 null. */
    fun onSignals(steeringDeg: Float?, brakeOn: Boolean?, turn: TurnDir?, gear: GearKind?, nowMs: Long): String? {
        var message: String? = null

        if (gear != null) {
            val before = prevGear
            if (before != null && gear != before) {
                message = "기어를 ${gearLabel(before)}에서 ${gearLabel(gear)}로 바꿨어요."
            }
            prevGear = gear
        }

        if (brakeOn != null) {
            val before = prevBrake
            if (before != null && brakeOn != before) {
                message = if (brakeOn) "브레이크를 밟았어요." else "브레이크를 뗐어요."
            }
            prevBrake = brakeOn
        }

        if (turn != null) {
            val before = prevTurn
            if (before != null && turn != before) {
                message = if (turn == TurnDir.OFF) {
                    "깜빡이를 껐어요."
                } else {
                    turnOnAtMs = nowMs
                    turnOnDir = turn
                    steeredAfterTurn = false
                    naviLinkMessage(turn, nowMs) ?: "${dirName(turn)} 깜빡이를 켰어요."
                }
            }
            prevTurn = turn
        }

        if (steeringDeg != null) {
            val size = abs(steeringDeg)
            val dir = if (steeringDeg > 0f) TurnDir.LEFT else TurnDir.RIGHT
            if (!steeringSeen) {
                // 첫 관측은 해설하지 않고 기준만 잡는다
                steeringSeen = true
                wheel = if (size >= STEER_START_DEG) Wheel.LARGE else if (size >= STEER_SMALL_DEG) Wheel.SMALL else Wheel.CENTER
            } else if (size <= STEER_CENTER_DEG) {
                if (wheel != Wheel.CENTER) message = "핸들이 다시 가운데로 돌아왔어요."
                wheel = Wheel.CENTER
            } else if (wheel != Wheel.LARGE && size >= STEER_START_DEG) {
                message = if (wheel == Wheel.SMALL) "핸들을 ${dirName(dir)}으로 더 돌렸어요." else turnedMessage(dir, "", nowMs)
                wheel = Wheel.LARGE
            } else if (wheel == Wheel.CENTER && size >= STEER_SMALL_DEG) {
                message = turnedMessage(dir, "조금 ", nowMs)
                wheel = Wheel.SMALL
            }
        }
        return message
    }

    /** 깜빡이를 켠 직후 같은 방향으로 핸들을 돌렸으면 한 동작으로 묶어 말한다. 차선 변경이라고 단정하지는 않는다. */
    private fun turnedMessage(dir: TurnDir, amount: String, nowMs: Long): String {
        val linked = !steeredAfterTurn && dir == turnOnDir && nowMs - turnOnAtMs <= LINK_WINDOW_MS
        if (linked) steeredAfterTurn = true
        return (if (linked) "깜빡이를 켠 뒤 " else "") + "핸들을 ${dirName(dir)}으로 ${amount}돌렸어요."
    }

    private fun naviLinkMessage(turn: TurnDir, nowMs: Long): String? {
        val kind = naviKind ?: return null
        val matches = (kind == NaviTurnKind.LEFT && turn == TurnDir.LEFT) || (kind == NaviTurnKind.RIGHT && turn == TurnDir.RIGHT)
        if (!matches || naviSinceMs == 0L) return null
        val seconds = (nowMs - naviSinceMs) / 1000
        return "내비가 ${if (turn == TurnDir.LEFT) "좌회전" else "우회전"}을 안내하고 ${seconds}초 뒤, ${dirName(turn)} 깜빡이를 켰어요."
    }

    private fun dirName(dir: TurnDir): String = if (dir == TurnDir.LEFT) "왼쪽" else "오른쪽"

    private fun gearLabel(gear: GearKind): String = when (gear) {
        GearKind.PARK -> "P(주차)"
        GearKind.NEUTRAL -> "N(중립)"
        GearKind.REVERSE -> "R(후진)"
        GearKind.DRIVE -> "D(주행)"
        GearKind.OTHER -> "기타"
    }

    companion object {
        // shortcut: 아래 각도는 실차 데이터 없이 정한 임시값(센서 흔들림 약 ±1~2도 가정). 실차 측정 뒤 조정
        /** 핸들이 "돌아갔다"고 보는 각도(절댓값) */
        const val STEER_START_DEG = 20f

        /** 핸들이 "조금 돌아갔다"고 보는 각도(절댓값). 차선 변경처럼 작은 조작 */
        const val STEER_SMALL_DEG = 6f

        /** 핸들이 "가운데로 돌아왔다"고 보는 각도(절댓값). 작은 조작 시작 각도보다 작아야 한다 */
        const val STEER_CENTER_DEG = 3f

        /** 내비 안내가 이 거리(미터) 이내일 때부터 "안내한 뒤" 시간을 잰다. 임시값 */
        const val NAVI_NEAR_M = 300.0

        /** 깜빡이를 켠 뒤 핸들 조작을 같은 동작으로 묶는 시간(밀리초) */
        const val LINK_WINDOW_MS = 10_000L
    }
}
