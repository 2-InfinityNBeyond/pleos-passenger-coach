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
import ai.pleos.playground.navi.data.DestinationInfo
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.RoadSign
import app.passengercoach.coach.RoadSigns
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

/** 앞으로 나올 안내 한 건 */
data class TurnStep(val type: TurnType, val distanceM: Double, val text: String?)

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
    /** 현재 위치와 진행 방위(0 = 북, 시계 방향). 가까운 교차로를 기기 안에서 찾는 데만 쓴다 */
    val lat: Double? = null,
    val lon: Double? = null,
    val heading: Double? = null,
    /** 앞으로 나올 안내 전체(가까운 것부터). 맨 앞이 [turn]과 같다 */
    val turns: List<TurnStep> = emptyList(),
    /** 목적지. 지도에 경로 선을 따로 계산해 그리는 데 쓴다 */
    val destLat: Double? = null,
    val destLon: Double? = null,
    val destName: String? = null,
    /** 내비 경로가 바뀔 때마다 1씩 는다(경로 시작, 재탐색 끝, 목적지 변경) */
    val routeEpoch: Int = 0,
    val speedLimit: Int? = null,
    val signs: List<RoadSign> = emptyList(),
)

/** NaviHelper SDK에서 회전 안내(TBT)와 내비 상태를 읽기 전용으로 받는다. */
class NaviHub(private val context: Context) {
    private val mutableState = MutableStateFlow(NaviSnapshot())
    val state: StateFlow<NaviSnapshot> = mutableState

    private var navi: NaviHelper? = null
    private var cameras: List<RoadSign> = emptyList()
    private var cautions: List<RoadSign> = emptyList()

    /** 내비 경로가 바뀌었다: 목적지를 다시 묻고, 지도 경로를 다시 계산하게 번호를 올린다 */
    private fun routeChanged() {
        dlog(TAG) { "ROUTE_CHANGED t=${System.currentTimeMillis()}" }
        mutableState.update { it.copy(routeEpoch = it.routeEpoch + 1) }
        runCatching { navi?.getDestinationInfo() }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onTBTInfo(list: List<TBTInfo>) {
            val first = list.firstOrNull()
            val t = System.currentTimeMillis()
            dlog(TAG) { "TBT count=${list.size} first=${first?.type} dist=${first?.distance} desc=${first?.description} t=$t" }
            val steps = list.map { TurnStep(it.type, it.distance, it.description) }
            if (first == null) {
                mutableState.update { it.copy(turn = null, turnDistanceM = null, turnText = null, turnAtMs = t, turns = steps) }
            } else {
                mutableState.update { it.copy(turn = first.type, turnDistanceM = first.distance, turnText = first.description, turnAtMs = t, turns = steps) }
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
            routeChanged()
        }

        override fun onReRouteEnded() = routeChanged()
        override fun onRoutingCompleted() = routeChanged()
        override fun onDestinationChanged() = routeChanged()

        override fun onDestinationInfo(info: DestinationInfo) {
            dlog(TAG) { "DESTINATION name=${info.name} lat=${info.latitude} lon=${info.longitude} dLat=${info.displayLatitude} dLon=${info.displayLongitude} remain=${info.remainDistance} t=${System.currentTimeMillis()}" }
            // 좌표가 0으로 오는 경우가 있어(에뮬레이터에서 확인) 표시용 좌표를 대신 쓰고, 그것도 없으면 목적지를 모르는 것으로 둔다
            val lat = info.latitude.takeIf { it != 0.0 } ?: info.displayLatitude?.takeIf { it != 0.0 }
            val lon = info.longitude.takeIf { it != 0.0 } ?: info.displayLongitude?.takeIf { it != 0.0 }
            // 개발용으로 직접 요청한 목적지가 있으면 그것을 유지한다(에뮬레이터는 목적지 좌표를 0으로 준다)
            mutableState.update { it.copy(destLat = lat ?: debugDest?.lat, destLon = lon ?: debugDest?.lon, destName = info.name) }
        }

        override fun onRouteEnded() = clearTurn("ROUTE_ENDED")
        override fun onRouteCancelled() = clearTurn("ROUTE_CANCELLED")
        override fun onDestinationArrived(info: DestinationArrivedInfo) = clearTurn("ARRIVED")

        override fun onCurrentLocationInfo(info: CurrentLocationInfo) {
            dlog(TAG) { "LOCATION lat=${info.latitude} lon=${info.longitude} heading=${info.heading} t=${System.currentTimeMillis()}" }
            if (!debugLocation) mutableState.update { it.copy(lat = info.latitude.toDouble(), lon = info.longitude.toDouble(), heading = info.heading.toDouble()) }
        }

        override fun onCurrentRoadSpeedLimit(limit: Int) {
            dlog(TAG) { "SPEED_LIMIT $limit t=${System.currentTimeMillis()}" }
            mutableState.update { it.copy(speedLimit = limit.takeIf { l -> l > 0 }) }
        }

        override fun onCameraAlert(list: List<CameraAlert>) {
            dlog(TAG) { "CAMERA count=${list.size} first=${list.firstOrNull()?.type} t=${System.currentTimeMillis()}" }
            cameras = list.mapNotNull { a -> RoadSigns.camera(a.type.name, a.speedLimit)?.let { RoadSign(it, a.remainDistance) } }
            mutableState.update { it.copy(signs = cameras + cautions) }
        }

        override fun onCautionPointInfo(list: List<CautionPoint>) {
            dlog(TAG) { "CAUTION count=${list.size} first=${list.firstOrNull()?.type} t=${System.currentTimeMillis()}" }
            cautions = list.mapNotNull { p -> RoadSigns.caution(p.type.name)?.let { RoadSign(it, p.remainDistance) } }
            mutableState.update { it.copy(signs = cameras + cautions) }
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
                        n.getDestinationInfo()
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

    fun restart() {
        stop()
        start()
    }

    /** 개발용: 목적지를 지정해 경로를 요청한다(에뮬레이터 검증용). */
    fun requestDebugRoute(lat: Double, lon: Double) {
        dlog(TAG) { "DEBUG_ROUTE request lat=$lat lon=$lon" }
        debugDest = LatLon(lat, lon)
        mutableState.update { it.copy(destLat = lat, destLon = lon) }
        try {
            navi?.requestRoute(RouteInfo(lat, lon, null, null, "debug", "", "", "", RouteOption.RECOMMENDED, "", false))
        } catch (e: Exception) {
            Log.w(TAG, "DEBUG_ROUTE failed ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private var debugLocation = false
    private var debugDest: LatLon? = null

    /** 개발용: 위치를 직접 정한다(에뮬레이터 위치가 신호 데이터가 없는 지역일 때 검증용). 이후 내비 위치는 무시한다 */
    fun setDebugLocation(lat: Double, lon: Double, heading: Double) {
        debugLocation = true
        mutableState.update { it.copy(lat = lat, lon = lon, heading = heading) }
    }

    fun stop() {
        val n = navi ?: return
        runCatching { n.removeListener(listener) }
        runCatching { n.release() }
        navi = null
    }

    private fun clearTurn(reason: String) {
        dlog(TAG) { "$reason t=${System.currentTimeMillis()}" }
        mutableState.update { it.copy(turn = null, turnDistanceM = null, turnText = null, turns = emptyList(), destLat = null, destLon = null, destName = null, signs = emptyList()) }
        debugDest = null
    }

    private companion object {
        const val TAG = "NaviLab"
    }
}
