package app.passengercoach.demo

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
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

    private fun signal(f: (SignalSnapshot) -> SignalSnapshot) = mutableSignals.update { f(it).copy(connected = true) }

    private fun naviTurn(type: TurnType?, meters: Double?, text: String?) =
        mutableNavi.update { it.copy(turn = type, turnDistanceM = meters, turnText = text, ready = true) }

    /** 시나리오 S1(좌회전) → S2(정차)를 무한 반복한다. 취소하면 멈춘다. */
    suspend fun run() {
        while (true) {
            signal { it.copy(gear = VehicleGear.GEAR_PARK, brakeOn = true, turn = TurnSignalValue.OFF, steeringDeg = 0f) }
            naviTurn(null, null, null)
            delay(3_000)

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

            signal { it.copy(brakeOn = true) }
            delay(3_000)
            signal { it.copy(brakeOn = false) }
            delay(2_000)

            naviTurn(null, null, null)
            delay(2_000)
        }
    }
}
