package app.passengercoach.demo

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import app.passengercoach.coach.LightColor
import app.passengercoach.coach.LightInfo
import app.passengercoach.coach.LightPhase
import app.passengercoach.signal.NaviSnapshot
import app.passengercoach.signal.SignalSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * 데모(시험 자료) 모드: 차량 없이도 같은 시나리오를 반복 재생한다(PT 시연, 영상, 회귀 확인용).
 * 화면에는 "시험 자료(데모)" 표시가 항상 붙는다. 실제 차량 값이 아니다.
 */
class DemoPlayer {
    private val mutableSignals = MutableStateFlow(SignalSnapshot(connected = true))
    private val mutableNavi = MutableStateFlow(NaviSnapshot(ready = true))
    val signals: StateFlow<SignalSnapshot> = mutableSignals
    val navi: StateFlow<NaviSnapshot> = mutableNavi

    // 데모용 신호등(시험 자료): 정차 장면에서 빨간불이 초록불로 바뀌는 것을 보여 준다. 실제 신호가 아니다
    private val mutableLight = MutableStateFlow<LightInfo?>(null)
    val light: StateFlow<LightInfo?> = mutableLight

    private fun signal(f: (SignalSnapshot) -> SignalSnapshot) = mutableSignals.update { f(it).copy(connected = true) }

    private fun naviTurn(type: TurnType?, meters: Double?, text: String?) =
        mutableNavi.update { it.copy(turn = type, turnDistanceM = meters, turnText = text, ready = true) }

    /** 좌회전 → 작은 핸들 조작 → 정차 → 후진 주차를 무한 반복한다. 취소하면 멈춘다. */
    suspend fun run() {
        while (true) {
            signal { it.copy(gear = VehicleGear.GEAR_PARK, brakeOn = true, turn = TurnSignalValue.OFF, steeringDeg = 0f, lowBeamOn = false, wiperOn = false, beltWarning = true, doorOpen = false) }
            naviTurn(null, null, null)
            delay(2_000)
            // 출발 전: 안전벨트를 매고 전조등을 켠다
            signal { it.copy(beltWarning = false) }
            delay(2_000)
            signal { it.copy(lowBeamOn = true) }
            delay(2_000)

            signal { it.copy(brakeOn = false) }
            delay(1_500)
            signal { it.copy(gear = VehicleGear.GEAR_DRIVE) }
            delay(2_000)

            naviTurn(TurnType.TURN_LEFT, 200.0, "시험 안내")
            delay(2_500)
            naviTurn(TurnType.TURN_LEFT, 120.0, "시험 안내")
            delay(2_000)
            naviTurn(TurnType.TURN_LEFT, 80.0, "시험 안내")
            signal { it.copy(turn = TurnSignalValue.LEFT) }
            delay(2_000)

            for (angle in listOf(30f, 40f)) {
                signal { it.copy(steeringDeg = angle) }
                delay(500)
            }
            delay(1_500)
            for (angle in listOf(30f, 15f, 5f, 0f)) {
                signal { it.copy(steeringDeg = angle) }
                delay(500)
            }
            signal { it.copy(turn = TurnSignalValue.OFF) }
            naviTurn(TurnType.TURN_RIGHT, 400.0, "시험 안내")
            delay(2_500)

            // 작은 핸들 조작(차선 변경처럼): 오른쪽 깜빡이 → 조금 돌림 → 가운데 → 깜빡이 끔 (SDK 값은 음수가 오른쪽)
            signal { it.copy(turn = TurnSignalValue.RIGHT) }
            delay(1_500)
            signal { it.copy(steeringDeg = -8f) }
            delay(2_000)
            signal { it.copy(steeringDeg = 0f) }
            delay(1_500)
            signal { it.copy(turn = TurnSignalValue.OFF) }
            delay(1_500)

            // 신호 대기: 빨간불 5초 → 초록불로 바뀌면 출발
            signal { it.copy(brakeOn = true) }
            mutableLight.value = LightInfo("시험 사거리", 40, "남쪽", LightPhase(LightColor.RED, 5_000), LightPhase(LightColor.RED, 25_000), System.currentTimeMillis())
            delay(5_000)
            mutableLight.value = LightInfo("시험 사거리", 40, "남쪽", LightPhase(LightColor.GREEN, 20_000), LightPhase(LightColor.RED, 20_000), System.currentTimeMillis())
            delay(1_500)
            signal { it.copy(brakeOn = false) }
            delay(2_500)
            mutableLight.value = null

            naviTurn(null, null, null)
            delay(2_000)

            // 후진 주차: 멈추고 R → 핸들을 한 바퀴 돌려 놓고 브레이크를 뗌 → 멈추고 핸들을 풂 → P
            signal { it.copy(brakeOn = true) }
            delay(1_500)
            signal { it.copy(gear = VehicleGear.GEAR_REVERSE) }
            delay(2_000)
            for (angle in listOf(90f, 180f, 270f, PARK_STEER_DEG)) {
                signal { it.copy(steeringDeg = angle) }
                delay(600)
            }
            delay(1_500)
            signal { it.copy(brakeOn = false) }
            delay(PARK_ARC_MS)
            signal { it.copy(brakeOn = true) }
            delay(1_500)
            for (angle in listOf(270f, 180f, 90f, 0f)) {
                signal { it.copy(steeringDeg = angle) }
                delay(600)
            }
            delay(1_000)
            // 핸들을 풀고 곧게 칸 안으로
            signal { it.copy(brakeOn = false) }
            delay(2_500)
            signal { it.copy(brakeOn = true) }
            delay(1_500)
        }
    }

    companion object {
        /** 후진 주차 장면: 핸들을 이만큼 돌려 놓고 이 시간 동안 후진한다(합성 후방 화면의 주차 칸 위치 계산에 쓴다) */
        const val PARK_STEER_DEG = 360f
        const val PARK_ARC_MS = 3_000L
    }
}
