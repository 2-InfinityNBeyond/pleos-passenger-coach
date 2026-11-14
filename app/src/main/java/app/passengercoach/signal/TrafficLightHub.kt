package app.passengercoach.signal

import android.util.Log
import app.passengercoach.coach.Intersection
import app.passengercoach.coach.LightInfo
import app.passengercoach.coach.TrafficLights
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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 교차로 신호등 실시간 정보(공공데이터포털 15157604)를 받아 가까운 교차로의 신호를 알려 준다.
 * - 인증키가 없으면 아무것도 하지 않는다.
 * - 차량 위치는 밖으로 보내지 않는다. 지역 전체 목록을 받아 기기 안에서 가장 가까운 교차로를 고른다.
 * - 가까운 교차로가 없을 때는 신호 목록을 받지 않는다(호출 수와 데이터 사용을 줄인다).
 */
class TrafficLightHub(private val apiKey: String) {
    private val mutableState = MutableStateFlow<LightInfo?>(null)
    val state: StateFlow<LightInfo?> = mutableState

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    @Volatile private var lat: Double? = null
    @Volatile private var lon: Double? = null
    @Volatile private var heading: Double = 0.0

    /** 실시간 신호가 들어오는 교차로(지역코드 + 교차로 ID → 위치). 처음 한 번 받는다 */
    private var known: Map<String, Intersection> = emptyMap()

    fun onLocation(lat: Double?, lon: Double?, heading: Double?) {
        this.lat = lat
        this.lon = lon
        if (heading != null) this.heading = heading
    }

    fun start() {
        if (apiKey.isBlank() || job != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        job = s.launch {
            while (isActive) {
                val wait = try {
                    step()
                } catch (e: Exception) {
                    // 네트워크가 없거나 응답이 깨져도 앱은 그대로 동작한다. 신호 줄만 비운다
                    Log.w(TAG, "LIGHT_FAILED ${e.javaClass.simpleName}: ${e.message}")
                    mutableState.value = null
                    RETRY_MS
                }
                delay(wait)
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        job = null
        mutableState.value = null
    }

    /** 한 번 처리하고, 다음까지 기다릴 시간(밀리초)을 돌려준다 */
    private fun step(): Long {
        if (known.isEmpty()) {
            known = loadKnown()
            dlog(TAG) { "LIGHT_KNOWN intersections=${known.size}" }
            if (known.isEmpty()) return RETRY_MS
        }
        val la = lat
        val lo = lon
        val near = if (la == null || lo == null) null else TrafficLights.nearest(la, lo, known.values)
        if (near == null) {
            mutableState.value = null
            return IDLE_MS
        }
        val (crossing, distance) = near
        val at = System.currentTimeMillis()
        val row = rows().firstOrNull { it.optString("stdgCd") == crossing.cityCode && it.optString("crsrdId") == crossing.id }
        if (row == null) {
            mutableState.value = null
            return NEAR_MS
        }
        val (arm, armLabel) = TrafficLights.armFor(heading)
        mutableState.value = LightInfo(
            name = crossing.name,
            distanceM = distance.toInt(),
            armLabel = armLabel,
            straight = TrafficLights.phase(row.optString("${arm}StsgSttsNm"), row.optString("${arm}StsgRmndCs")),
            left = TrafficLights.phase(row.optString("${arm}LtsgSttsNm"), row.optString("${arm}LtsgRmndCs")),
            atMs = at,
        )
        dlog(TAG) { "LIGHT ${crossing.id} arm=$arm dist=${distance.toInt()}" }
        return NEAR_MS
    }

    /** 지금 신호가 들어오는 지역을 알아낸 뒤, 그 지역 교차로의 위치를 받는다 */
    private fun loadKnown(): Map<String, Intersection> {
        val live = rows().map { it.optString("stdgCd") to it.optString("crsrdId") }.toSet()
        val result = HashMap<String, Intersection>()
        for (city in live.map { it.first }.toSet()) {
            var page = 1
            while (true) {
                val items = items(get("crsrd_map_info", "stdgCd=$city&pageNo=$page&numOfRows=$PAGE"))
                for (o in items) {
                    val id = o.optString("crsrdId")
                    val la = o.optString("mapCtptIntLat").toDoubleOrNull() ?: continue
                    val lo = o.optString("mapCtptIntLot").toDoubleOrNull() ?: continue
                    if ((city to id) in live) result["$city/$id"] = Intersection(city, id, o.optString("crsrdNm"), la, lo)
                }
                if (items.size < PAGE) break
                page++
            }
        }
        return result
    }

    private fun rows(): List<JSONObject> = items(get("tl_drct_info", "pageNo=1&numOfRows=$PAGE"))

    private fun items(body: String): List<JSONObject> {
        val items = JSONObject(body).optJSONObject("body")?.optJSONObject("items") ?: return emptyList()
        // 결과가 한 건이면 배열이 아니라 객체로 올 수 있다
        val item = items.opt("item")
        return when (item) {
            is JSONArray -> (0 until item.length()).map { item.getJSONObject(it) }
            is JSONObject -> listOf(item)
            else -> emptyList()
        }
    }

    private fun get(operation: String, query: String): String {
        val c = URL("$BASE/$operation?serviceKey=$apiKey&type=json&$query").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private companion object {
        const val TAG = "LightLab"
        const val BASE = "https://apis.data.go.kr/B551982/rti"
        const val PAGE = 1000

        /** 가까운 교차로가 있을 때 다시 받는 간격. 사이 시간은 화면에서 초를 센다 */
        const val NEAR_MS = 8_000L
        const val IDLE_MS = 5_000L
        const val RETRY_MS = 60_000L
    }
}
