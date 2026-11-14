package app.passengercoach.demo

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.LightColor
import app.passengercoach.coach.LightInfo
import app.passengercoach.coach.LightPhase
import app.passengercoach.coach.RouteMath
import app.passengercoach.signal.NaviSnapshot
import app.passengercoach.signal.SignalSnapshot
import app.passengercoach.signal.TurnStep
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    /** 지도에 그릴 데모 경로(실제 길 모양) */
    val route: StateFlow<List<LatLon>> = MutableStateFlow(DemoRoute.points)

    /** 데모 경로를 따라 간 거리(미터) */
    @Volatile private var traveled = 0.0

    private fun signal(f: (SignalSnapshot) -> SignalSnapshot) = mutableSignals.update { f(it).copy(connected = true) }

    /** 좌회전 → 작은 핸들 조작 → 신호 대기 → 후진 주차를 무한 반복한다. 취소하면 멈춘다. */
    suspend fun run(): Unit = coroutineScope {
        launch { move() }
        while (true) {
            traveled = 0.0
            mutableLight.value = null
            signal { it.copy(gear = VehicleGear.GEAR_PARK, brakeOn = true, turn = TurnSignalValue.OFF, steeringDeg = 0f, lowBeamOn = false, wiperOn = false, beltWarning = true, doorOpen = false) }
            delay(2_000)
            // 출발 전: 안전벨트를 매고 전조등을 켠다
            signal { it.copy(beltWarning = false) }
            delay(2_000)
            signal { it.copy(lowBeamOn = true) }
            delay(2_000)

            signal { it.copy(brakeOn = false) }
            delay(1_500)
            // 여기부터 달린다. 첫 회전(왼쪽)까지 약 9.5초가 걸리도록 속도를 맞춰 두었다
            signal { it.copy(gear = VehicleGear.GEAR_DRIVE) }
            delay(6_500)
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
            mutableLight.value = LightInfo("시험 사거리", 40, "북쪽", LightPhase(LightColor.RED, 5_000), LightPhase(LightColor.RED, 25_000), System.currentTimeMillis())
            delay(5_000)
            mutableLight.value = LightInfo("시험 사거리", 40, "북쪽", LightPhase(LightColor.GREEN, 20_000), LightPhase(LightColor.RED, 20_000), System.currentTimeMillis())
            delay(1_500)
            signal { it.copy(brakeOn = false) }
            delay(2_500)
            mutableLight.value = null

            // 후진 주차: 멈추고 R → 핸들을 한 바퀴 돌려 놓고 브레이크를 뗌 → 멈추고 핸들을 풂 → 곧게 칸 안으로 → P
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
            signal { it.copy(brakeOn = false) }
            delay(2_500)
            signal { it.copy(brakeOn = true) }
            delay(1_500)
        }
    }

    /** 기어 D에서 브레이크를 뗀 동안 데모 경로를 따라 위치를 옮기고, 남은 거리로 내비 안내를 만든다 */
    private suspend fun move() {
        while (true) {
            val s = mutableSignals.value
            if (s.gear == VehicleGear.GEAR_DRIVE && s.brakeOn == false) traveled += SPEED_MPS * TICK_MS / 1000.0
            val (here, heading) = RouteMath.along(DemoRoute.points, traveled)
            val steps = DemoRoute.turns.filter { it.first > traveled }
                .map { TurnStep(if (it.second) TurnType.TURN_LEFT else TurnType.TURN_RIGHT, it.first - traveled, "시험 안내") }
            val first = steps.firstOrNull()
            mutableNavi.update {
                it.copy(
                    lat = here.lat, lon = here.lon, heading = heading,
                    turns = steps, turn = first?.type, turnDistanceM = first?.distanceM, turnText = first?.text,
                    destName = "시험 목적지", speedLimit = 50, ready = true,
                )
            }
            delay(TICK_MS)
        }
    }

    companion object {
        /** 후진 주차 장면: 핸들을 이만큼 돌려 놓고 이 시간 동안 후진한다(합성 후방 화면의 주차 칸 위치 계산에 쓴다) */
        const val PARK_STEER_DEG = 360f
        const val PARK_ARC_MS = 3_000L

        private const val TICK_MS = 100L

        /** D로 바꾼 뒤 9.5초쯤에 첫 회전 지점에 닿는 속도 */
        private val SPEED_MPS = DemoRoute.firstTurnM / 9.5
    }
}
