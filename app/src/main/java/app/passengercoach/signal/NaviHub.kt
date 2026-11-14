package app.passengercoach.signal

import app.passengercoach.dlog
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NaviMode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 내비가 알려 준 다음 안내. null이면 아직 받은 안내가 없음. */
data class NaviSnapshot(
    val turn: TurnType? = null,
    val turnDistanceM: Double? = null,
    val turnText: String? = null,
    val turnAtMs: Long = 0L,
    val status: NavigationStatus? = null,
    val mode: NaviMode? = null,
    val ready: Boolean = false,
    val error: String? = null,
)

/** NaviHelper SDK에서 회전 안내(TBT)와 내비 상태를 읽기 전용으로 받는다. */
class NaviHub(private val context: Context) {
    private val mutableState = MutableStateFlow(NaviSnapshot())
    val state: StateFlow<NaviSnapshot> = mutableState

    private var navi: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            val first = list.firstOrNull()
            val t = System.currentTimeMillis()
            dlog(TAG) { "TBT count=${list.size} first=${first?.type} dist=${first?.distance} desc=${first?.description} t=$t" }
            if (first == null) {
                mutableState.update { it.copy(turn = null, turnDistanceM = null, turnText = null, turnAtMs = t) }
            } else {
                mutableState.update { it.copy(turn = first.type, turnDistanceM = first.distance, turnText = first.description, turnAtMs = t) }
            }
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            dlog(TAG) { "STATUS $status t=${System.currentTimeMillis()}" }
            mutableState.update { it.copy(status = status) }
        }

        override fun onRouteStateInfo(info: RouteStateInfo) {
            dlog(TAG) { "ROUTE_STATE mode=${info.naviMode} option=${info.routeOption} t=${System.currentTimeMillis()}" }
            mutableState.update { it.copy(mode = info.naviMode) }
        }

        override fun onRouteStarted(info: RouteStartInfo) {
            dlog(TAG) { "ROUTE_STARTED $info t=${System.currentTimeMillis()}" }
        }

        override fun onRouteEnded() = clearTurn("ROUTE_ENDED")
        override fun onRouteCancelled() = clearTurn("ROUTE_CANCELLED")
        override fun onDestinationArrived(info: DestinationArrivedInfo) = clearTurn("ARRIVED")

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            dlog(TAG) { "LOCATION lat=${info.latitude} lon=${info.longitude} heading=${info.heading} t=${System.currentTimeMillis()}" }
        }

        override fun onCurrentRoadSpeedLimit(limit: Int) {
            dlog(TAG) { "SPEED_LIMIT $limit t=${System.currentTimeMillis()}" }
        }

        override fun onCameraAlert(list: List<CameraAlert>) {
            dlog(TAG) { "CAMERA count=${list.size} first=${list.firstOrNull()?.type} t=${System.currentTimeMillis()}" }
        }

        override fun onCautionPointInfo(list: List<CautionPoint>) {
            dlog(TAG) { "CAUTION count=${list.size} first=${list.firstOrNull()?.type} t=${System.currentTimeMillis()}" }
        }

        override fun onError(code: NaviErrorCode) {
            Log.w(TAG, "ERROR $code")
            mutableState.update { it.copy(error = code.name) }
        }
    }

    fun start() {
        if (navi != null) return
        try {
            val n = NaviHelper(context)
            navi = n
            n.initialize(
                {
                    try {
                        n.addListener(listener)
                        n.getTBTInfo()
                        n.getRouteStateInfo()
                        n.getCurrentLocationInfo()
                        mutableState.update { it.copy(ready = true, error = null) }
                        dlog(TAG) { "NAVI_READY" }
                    } catch (e: Exception) {
                        // 권한 부족 등으로 내비 정보를 못 받아도 앱은 종료되지 않는다(요구서: 앱은 죽지 않음)
                        Log.w(TAG, "NAVI_READ_FAILED ${e.javaClass.simpleName}: ${e.message}")
                        mutableState.update { it.copy(ready = false, error = e.message ?: e.javaClass.simpleName) }
                    }
                },
                { t: Throwable ->
                    Log.w(TAG, "NAVI_INIT_FAILED ${t.message}")
                    mutableState.update { it.copy(ready = false, error = t.message ?: t.javaClass.simpleName) }
                },
            )
        } catch (e: Exception) {
            Log.w(TAG, "NAVI_START_EXCEPTION ${e.message}")
            mutableState.update { it.copy(error = e.message ?: e.javaClass.simpleName) }
        }
    }

    /** 개발용: 목적지를 지정해 경로를 요청한다(에뮬레이터 검증용). */
    fun requestDebugRoute(lat: Double, lon: Double) {
        dlog(TAG) { "DEBUG_ROUTE request lat=$lat lon=$lon" }
        try {
            navi?.requestRoute(RouteInfo(lat, lon, null, null, "debug", "", "", "", RouteOption.RECOMMENDED, "", false))
        } catch (e: Exception) {
            Log.w(TAG, "DEBUG_ROUTE failed ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun stop() {
        val n = navi ?: return
        runCatching { n.removeListener(listener) }
        runCatching { n.release() }
        navi = null
    }

    private fun clearTurn(reason: String) {
        dlog(TAG) { "$reason t=${System.currentTimeMillis()}" }
        mutableState.update { it.copy(turn = null, turnDistanceM = null, turnText = null) }
    }

    private companion object {
        const val TAG = "NaviLab"
    }
}
