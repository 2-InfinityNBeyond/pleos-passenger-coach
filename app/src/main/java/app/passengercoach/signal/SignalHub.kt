package app.passengercoach.signal

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
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
)

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

    fun start() {
        if (started) return
        started = true
        connect(attempt = 0)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val v = vehicle ?: return
        runCatching { v.getSteeringWheel().unregisterSteeringWheelAngle(steeringListener) }
        runCatching { v.getBrake().unregisterBrakePedalStatus(brakeListener) }
        runCatching { v.getTurnSignal().unregisterTurnSignalState(turnListener) }
        runCatching { v.getGear().unregisterGearSelection(gearListener) }
        runCatching { v.release() }
        vehicle = null
        started = false
    }

    private fun connect(attempt: Int) {
        try {
            val v = Vehicle(context)
            v.initialize()
            vehicle = v
            v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
            v.getBrake().registerBrakePedalStatus(brakeListener)
            v.getTurnSignal().registerTurnSignalState(turnListener)
            v.getGear().registerGearSelection(gearListener)
            readCurrent(v)
            mutableState.update { it.copy(connected = true, error = null) }
            Log.i(TAG, "CONNECTED attempt=$attempt")
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

    private fun onSteering(value: Float?) {
        val t = System.currentTimeMillis()
        Log.i(TAG, "STEER value=$value t=$t")
        mutableState.update { it.copy(steeringDeg = value, steeringAtMs = t) }
    }

    private fun onBrake(value: Boolean?) {
        val t = System.currentTimeMillis()
        Log.i(TAG, "BRAKE value=$value t=$t")
        mutableState.update { it.copy(brakeOn = value, brakeAtMs = t) }
    }

    private fun onTurn(value: TurnSignalValue?) {
        val t = System.currentTimeMillis()
        Log.i(TAG, "TURN value=$value t=$t")
        mutableState.update { it.copy(turn = value, turnAtMs = t) }
    }

    private fun onGear(value: VehicleGear?) {
        val t = System.currentTimeMillis()
        Log.i(TAG, "GEAR value=$value t=$t")
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
