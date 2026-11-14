package app.passengercoach.signal

import android.util.Log
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.RouteMath
import app.passengercoach.dlog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 지도에 그릴 경로 선을 외부 길찾기(카카오모빌리티)로 따로 계산한다.
 * 공개 SDK는 운전자 내비의 경로 좌표를 주지 않기 때문이다. 그래서 **내비의 길과 다를 수 있다.**
 * 다음 경우에 다시 계산한다: 목적지나 내비 경로가 바뀜([onNavi]의 epoch), 차가 선에서 [OFF_ROUTE_M] 넘게 벗어남.
 * 인증키가 없으면 아무것도 하지 않는다. 현재 위치와 목적지가 길찾기 서비스로 나간다(동의 화면에 밝힘).
 */
class RouteHub(private val apiKey: String) {
    private val mutableState = MutableStateFlow<List<LatLon>>(emptyList())
    val state: StateFlow<List<LatLon>> = mutableState

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    @Volatile private var here: LatLon? = null
    @Volatile private var dest: LatLon? = null
    @Volatile private var epoch = 0

    private var doneDest: LatLon? = null
    private var doneEpoch = -1
    private var lastFetchMs = 0L

    fun onNavi(lat: Double?, lon: Double?, destLat: Double?, destLon: Double?, routeEpoch: Int) {
        here = if (lat != null && lon != null) LatLon(lat, lon) else null
        dest = if (destLat != null && destLon != null) LatLon(destLat, destLon) else null
        epoch = routeEpoch
    }

    fun start() {
        if (apiKey.isBlank() || job != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        job = s.launch {
            while (isActive) {
                try {
                    step()
                } catch (e: Exception) {
                    // 길을 못 받아도 앱은 그대로 동작한다. 지도에는 위치만 보인다
                    Log.w(TAG, "ROUTE_FAILED ${e.javaClass.simpleName}: ${e.message}")
                }
                delay(CHECK_MS)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        job = null
        mutableState.value = emptyList()
        doneDest = null
    }

    private fun step() {
        val from = here
        val to = dest
        if (from == null || to == null) {
            if (to == null && mutableState.value.isNotEmpty()) mutableState.value = emptyList()
            doneDest = null
            return
        }
        val off = RouteMath.distanceToRouteM(from, mutableState.value)
        val need = doneDest != to || doneEpoch != epoch || (off != null && off > OFF_ROUTE_M)
        // 벗어난 채로 계속 달릴 때 호출이 몰리지 않게 간격을 둔다
        if (!need || System.currentTimeMillis() - lastFetchMs < MIN_GAP_MS) return
        lastFetchMs = System.currentTimeMillis()
        val line = fetch(from, to)
        doneDest = to
        doneEpoch = epoch
        mutableState.value = line
        dlog(TAG) { "ROUTE points=${line.size} offBefore=${off?.toInt()}" }
    }

    private fun fetch(from: LatLon, to: LatLon): List<LatLon> {
        val url = "$BASE?origin=${from.lon},${from.lat}&destination=${to.lon},${to.lat}&priority=RECOMMEND"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Authorization", "KakaoAK $apiKey")
        try {
            if (c.responseCode != 200) {
                dlog(TAG) { "ROUTE_HTTP ${c.responseCode} from=${from.lat},${from.lon} to=${to.lat},${to.lon} body=${c.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)}" }
                throw IllegalStateException("HTTP ${c.responseCode}")
            }
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val route = JSONObject(body).getJSONArray("routes").getJSONObject(0)
            if (route.optInt("result_code", -1) != 0) throw IllegalStateException("result ${route.optInt("result_code")}")
            val line = ArrayList<LatLon>()
            val sections = route.getJSONArray("sections")
            for (s in 0 until sections.length()) {
                val roads = sections.getJSONObject(s).getJSONArray("roads")
                for (r in 0 until roads.length()) {
                    // vertexes: 경도, 위도가 번갈아 들어 있다
                    val v = roads.getJSONObject(r).getJSONArray("vertexes")
                    var i = 0
                    while (i + 1 < v.length()) {
                        line += LatLon(v.getDouble(i + 1), v.getDouble(i))
                        i += 2
                    }
                }
            }
            return line
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val TAG = "RouteLab"
        const val BASE = "https://apis-navi.kakaomobility.com/v1/directions"
        const val OFF_ROUTE_M = 50.0
        const val CHECK_MS = 2_000L
        const val MIN_GAP_MS = 6_000L
    }
}
