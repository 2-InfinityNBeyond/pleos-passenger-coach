package app.passengercoach.signal

import app.passengercoach.dlog
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltNotBuckledWarningListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 차량에서 받은 값과 받은 시각. null이면 아직 값을 받지 못한 것(0과 구분). */
data class SignalSnapshot(
    val steeringDeg: Float? = null,
    val steeringAtMs: Long = 0L,
    val brakeOn: Boolean? = null,
    val brakeAtMs: Long = 0L,
    val turn: TurnSignalValue? = null,
    val turnAtMs: Long = 0L,
    val gear: VehicleGear? = null,
    val gearAtMs: Long = 0L,
    val connected: Boolean = false,
    val error: String? = null,
    /** 차량이 읽기를 지원하지 않는다고 확인된 신호 키("steering", "brake", "turn", "gear") */
    val unsupported: Set<String> = emptySet(),
    /** 연결을 시작한 시각(밀리초). 시작 직후를 "연결하는 중"으로 보여 주는 데 쓴다 */
    val startedAtMs: Long = 0L,
    // 추가 조작 신호. 차량이나 권한에 따라 못 받을 수 있고, 그때는 null로 남는다(핵심 4신호와 따로 다룬다)
    val lowBeamOn: Boolean? = null,
    val highBeamOn: Boolean? = null,
    val wiperOn: Boolean? = null,
    /** 운전석 안전벨트 미착용 경고 */
    val beltWarning: Boolean? = null,
    /** 앞뒤 좌우 문 가운데 하나라도 열림 */
    val doorOpen: Boolean? = null,
) {
    /** 어느 신호든 가장 최근에 값을 받은 시각(밀리초). 한 번도 못 받았으면 0 */
    fun lastUpdateMs(): Long = maxOf(steeringAtMs, brakeAtMs, turnAtMs, gearAtMs)
}

/**
 * Vehicle SDK 신호(핸들 각도, 브레이크, 방향지시등, 기어)를 읽기 전용으로 구독한다.
 * 받은 시각은 지연 측정을 위해 logcat(태그 SignalLab)에도 남긴다.
 */
class SignalHub(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(SignalSnapshot())
    val state: StateFlow<SignalSnapshot> = mutableState

    private var vehicle: Vehicle? = null
    private var started = false

    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = onSteering(angle)
        override fun onFailed(e: Exception) = fail("steering", e)
    }
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) = onBrake(pressed)
        override fun onFailed(e: Exception) = fail("brake", e)
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = onTurn(value)
        override fun onFailed(e: Exception) = fail("turn", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) = fail("gear", e)
    }

    // ---- 추가 신호 ----
    private val wiperArea = WindowArea(WindowValue.FRONT_WINDSHIELD)
    // shortcut: 운전석을 1열 왼쪽으로 가정(국내 좌핸들), 우핸들 차량을 지원할 때 차량 정보로 판별
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val doors = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
    private val doorPositions = IntArray(doors.size)

    private val lowBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = mutableState.update { it.copy(lowBeamOn = state == LightState.ON) }
        override fun onFailed(e: Exception) = extraFail("lowBeam", e)
    }
    private val highBeamListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = mutableState.update { it.copy(highBeamOn = state == LightState.ON) }
        override fun onFailed(e: Exception) = extraFail("highBeam", e)
    }
    private val wiperListener = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = mutableState.update { it.copy(wiperOn = state == WiperState.ON) }
        override fun onFailed(e: Exception) = extraFail("wiper", e)
    }
    private val beltListener = object : SeatBeltNotBuckledWarningListener {
        override fun onSeatBeltNotBuckledWarningUpdated(warning: Boolean?) = mutableState.update { it.copy(beltWarning = warning) }
        override fun onFailed(e: Exception) = extraFail("belt", e)
    }
    private val doorListeners = doors.indices.map { i ->
        object : DoorPositionListener {
            override fun onPositionUpdated(position: Int?) = onDoor(i, position)
            override fun onFailed(e: Exception) = extraFail("door", e)
        }
    }

    private fun onDoor(index: Int, position: Int?) {
        doorPositions[index] = position ?: 0
        mutableState.update { it.copy(doorOpen = doorPositions.any { p -> p > 0 }) }
    }

    /** 추가 신호는 하나가 실패해도 나머지와 핵심 신호에 영향을 주지 않게 하나씩 따로 건다 */
    private fun registerExtras(v: Vehicle) {
        runCatching {
            v.getLight().registerLowBeamLightState(lowBeamListener)
            v.getLight().getCurrentLowBeamLightState({ s: LightState? -> lowBeamListener.onLightStateUpdated(s) }, { e: Exception -> extraFail("lowBeam-get", e) })
        }.onFailure { extraFail("lowBeam", it) }
        runCatching {
            v.getLight().registerHighBeamLightState(highBeamListener)
            v.getLight().getCurrentHighBeamLightState({ s: LightState? -> highBeamListener.onLightStateUpdated(s) }, { e: Exception -> extraFail("highBeam-get", e) })
        }.onFailure { extraFail("highBeam", it) }
        runCatching {
            v.getWiper().registerWiperState(wiperArea, wiperListener)
            v.getWiper().getCurrentWiperState(wiperArea, { s: WiperState? -> wiperListener.onWiperStateUpdated(s) }, { e: Exception -> extraFail("wiper-get", e) })
        }.onFailure { extraFail("wiper", it) }
        runCatching {
            v.getSafety().registerSeatBeltNotBuckledWarning(driverSeat, beltListener)
            v.getSafety().hasSeatBeltNotBuckledWarning(driverSeat, { w: Boolean? -> beltListener.onSeatBeltNotBuckledWarningUpdated(w) }, { e: Exception -> extraFail("belt-get", e) })
        }.onFailure { extraFail("belt", it) }
        doors.forEachIndexed { i, door ->
            runCatching {
                v.getDoor().registerDoorPosition(DoorArea(door), doorListeners[i])
                v.getDoor().getCurrentDoorPosition(DoorArea(door), { pos: Int? -> onDoor(i, pos) }, { e: Exception -> extraFail("door-get", e) })
            }.onFailure { extraFail("door", it) }
        }
    }

    private fun unregisterExtras(v: Vehicle) {
        runCatching { v.getLight().unregisterLowBeamLightState(lowBeamListener) }
        runCatching { v.getLight().unregisterHighBeamLightState(highBeamListener) }
        runCatching { v.getWiper().unregisterWiperState(wiperArea, wiperListener) }
        runCatching { v.getSafety().unregisterSeatBeltNotBuckledWarning(driverSeat, beltListener) }
        doors.forEachIndexed { i, door -> runCatching { v.getDoor().unregisterDoorPosition(DoorArea(door), doorListeners[i]) } }
    }

    // 추가 신호의 실패는 화면의 오류 문구로 올리지 않는다(핵심 신호가 정상이면 앱은 정상)
    private fun extraFail(what: String, e: Throwable) {
        Log.w(TAG, "EXTRA_FAIL $what: ${e.javaClass.simpleName}: ${e.message}")
    }

    fun start() {
        if (started) return
        started = true
        mutableState.update { it.copy(startedAtMs = System.currentTimeMillis()) }
        connect(attempt = 0)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        // 연결에 실패해 vehicle이 없어도 다시 시작할 수 있어야 한다
        started = false
        releaseVehicle()
    }

    private fun releaseVehicle() {
        val v = vehicle ?: return
        runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
        runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
        runCatching { v.getTurnSignal().unregisterTurnSignalState(turnListener) }
        runCatching { v.getGear().unregisterGearSelection(gearListener) }
        unregisterExtras(v)
        runCatching { v.release() }
        vehicle = null
    }

    /** "다시 시도" 버튼용: 구독을 정리하고 값도 비운 뒤 처음부터 연결한다. */
    fun restart() {
        stop()
        mutableState.value = SignalSnapshot()
        start()
    }

    /**
     * 값이 한동안 안 바뀌었을 때 지금 값을 다시 물어본다. 응답이 오면 받은 시각이 갱신되고,
     * 응답이 없으면 화면이 "차량 신호 없음"으로 바뀐다(값이 안 바뀐 것과 끊긴 것을 구분).
     */
    fun ping() {
        val v = vehicle ?: return
        runCatching { readCurrent(v) }
    }

    private fun connect(attempt: Int) {
        try {
            releaseVehicle()
            val v = Vehicle(context)
            vehicle = v
            v.initialize()
            v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getTurnSignal().registerTurnSignalState(turnListener)
            v.getGear().registerGearSelection(gearListener)
            readCurrent(v)
            checkSupport(v)
            registerExtras(v)
            mutableState.update { it.copy(connected = true, error = null) }
            dlog(TAG) { "CONNECTED attempt=$attempt" }
        } catch (e: Exception) {
            Log.w(TAG, "connect failed attempt=$attempt: ${e.message}")
            mutableState.update { it.copy(connected = false, error = e.message ?: e.javaClass.simpleName) }
            if (attempt < MAX_RETRY) main.postDelayed({ connect(attempt + 1) }, RETRY_DELAY_MS)
        }
    }

    private fun readCurrent(v: Vehicle) {
        v.getSteeringWheel().getCurrentSteeringWheelAngle({ a: Float? -> onSteering(a) }, { e: Exception -> fail("steering-get", e) })
        v.getBrake().getCurrentBrakePedalStatus({ b: Boolean? -> onBrake(b) }, { e: Exception -> fail("brake-get", e) })
        v.getTurnSignal().getCurrentTurnSignalState({ t: TurnSignalValue? -> onTurn(t) }, { e: Exception -> fail("turn-get", e) })
        v.getGear().getCurrentGearSelectionState({ g: VehicleGear? -> onGear(g) }, { e: Exception -> fail("gear-get", e) })
    }

    /** 차량이 해당 신호를 읽게 해 주는지 확인한다. 확인이 실패하면 지원 여부를 모르는 것으로 보고 그대로 둔다. */
    private fun checkSupport(v: Vehicle) {
        try {
            v.getBrake().checkBrakePedalStatusCapabilities(
                { c: CapabilityConfig? -> if (!readable(c)) markUnsupported("brake") },
                { e: Exception -> fail("brake-cap", e) },
            )
            v.getTurnSignal().checkTurnSignalStateCapabilities(
                { c: CapabilityConfig? -> if (!readable(c)) markUnsupported("turn") },
                { e: Exception -> fail("turn-cap", e) },
            )
            v.getSteeringWheel().checkSteeringWheelAngleCapabilities(
                { c: CapabilityConfig?, _: AreaBounds<Float>? -> if (!readable(c)) markUnsupported("steering") },
                { e: Exception -> fail("steering-cap", e) },
            )
        } catch (e: Exception) {
            Log.w(TAG, "CAP_CHECK_FAILED ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun readable(c: CapabilityConfig?): Boolean =
        c != null && (c.accessType == AccessType.READ || c.accessType == AccessType.READ_WRITE)

    private fun markUnsupported(key: String) {
        dlog(TAG) { "UNSUPPORTED $key" }
        mutableState.update { it.copy(unsupported = it.unsupported + key) }
    }

    private fun onSteering(value: Float?) {
        val t = System.currentTimeMillis()
        dlog(TAG) { "STEER value=$value t=$t" }
        mutableState.update { it.copy(steeringDeg = value, steeringAtMs = t) }
    }

    private fun onBrake(value: Boolean?) {
        val t = System.currentTimeMillis()
        dlog(TAG) { "BRAKE value=$value t=$t" }
        mutableState.update { it.copy(brakeOn = value, brakeAtMs = t) }
    }

    private fun onTurn(value: TurnSignalValue?) {
        val t = System.currentTimeMillis()
        dlog(TAG) { "TURN value=$value t=$t" }
        mutableState.update { it.copy(turn = value, turnAtMs = t) }
    }

    private fun onGear(value: VehicleGear?) {
        val t = System.currentTimeMillis()
        dlog(TAG) { "GEAR value=$value t=$t" }
        mutableState.update { it.copy(gear = value, gearAtMs = t) }
    }

    private fun fail(what: String, e: Exception) {
        Log.w(TAG, "FAIL $what: ${e.message}")
        mutableState.update { it.copy(error = "$what: ${e.message ?: e.javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "SignalLab"
        const val MAX_RETRY = 5
        const val RETRY_DELAY_MS = 1500L
    }
}
