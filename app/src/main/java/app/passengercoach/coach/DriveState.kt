package app.passengercoach.coach

import kotlin.math.abs

/**
 * 화면에 보여 줄 차량 상태 판정(요구서 8절 빈 상태, 실패 상태).
 * Android와 SDK에 의존하지 않아 단위 테스트가 가능하다.
 * 시동 켜짐/꺼짐은 공개 SDK에 신호가 없어 판정하지 않는다.
 */
object DriveState {
    /** 신호가 이 시간(밀리초) 동안 하나도 오지 않으면 "차량 신호 없음". 핸들 값은 평소에도 약 3초마다 다시 오므로 여유를 둔 값 */
    const val STALE_MS = 10_000L

    /** 정차로 보는 핸들 각도(절댓값, 도) */
    const val STOPPED_STEER_DEG = 10f

    /** 값이 있었는데 [STALE_MS] 넘게 갱신이 없으면 끊김. 데모(시험 자료)는 끊김 판정을 하지 않는다. */
    fun isStale(demo: Boolean, hasSignal: Boolean, lastUpdateMs: Long, nowMs: Long): Boolean =
        !demo && hasSignal && lastUpdateMs != 0L && nowMs - lastUpdateMs > STALE_MS

    /** 기어 D/R이면서 브레이크가 켜져 있고 핸들이 가운데 근처이면 정차 */
    fun isStopped(gear: GearKind?, brakeOn: Boolean?, steeringDeg: Float?): Boolean =
        (gear == GearKind.DRIVE || gear == GearKind.REVERSE) && brakeOn == true && abs(steeringDeg ?: 0f) < STOPPED_STEER_DEG

    /** 머리글에 쓰는 상태 문구. 기어를 모르면 "-" */
    fun stateText(gear: GearKind?, brakeOn: Boolean?, steeringDeg: Float?, otherGearName: String? = null): String = when (gear) {
        null -> "-"
        GearKind.PARK -> "P 주차 중"
        GearKind.NEUTRAL -> "N 중립"
        GearKind.REVERSE -> if (isStopped(gear, brakeOn, steeringDeg)) "R 정차 중" else "R"
        GearKind.DRIVE -> if (isStopped(gear, brakeOn, steeringDeg)) "D 정차 중" else "D"
        GearKind.OTHER -> otherGearName ?: "기타"
    }
}
