package app.passengercoach.signal

import android.util.Log
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.Preview
import app.passengercoach.coach.PreviewRoute
import app.passengercoach.coach.Spot
import app.passengercoach.coach.SpotKind
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

    val hasKey: Boolean get() = apiKey.isNotBlank()

    /** 미리 가 보기용: 경로 선과 함께 안내 지점, 구간별 혼잡도까지 받는다. 부르는 쪽에서 IO 스레드로 부른다 */
    fun preview(from: LatLon, to: LatLon): PreviewRoute = parse(download(from, to))

    private fun fetch(from: LatLon, to: LatLon): List<LatLon> = parse(download(from, to)).points

    private fun download(from: LatLon, to: LatLon): String {
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
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** 길찾기 응답을 경로 선, 짚어 줄 곳, 길이, 걸리는 시간으로 바꾼다 */
        fun parse(body: String): PreviewRoute {
            val route = JSONObject(body).getJSONArray("routes").getJSONObject(0)
            if (route.optInt("result_code", -1) != 0) throw IllegalStateException("result ${route.optInt("result_code")}")
            val line = ArrayList<LatLon>()
            val spots = ArrayList<Triple<LatLon, Int, String>>()
            val busy = ArrayList<Triple<Double, Double, Int>>()
            var roadAt = 0.0
            val sections = route.getJSONArray("sections")
            for (s in 0 until sections.length()) {
                val section = sections.getJSONObject(s)
                val roads = section.getJSONArray("roads")
                for (r in 0 until roads.length()) {
                    val road = roads.getJSONObject(r)
                    // vertexes: 경도, 위도가 번갈아 들어 있다
                    val v = road.getJSONArray("vertexes")
                    var i = 0
                    while (i + 1 < v.length()) {
                        line += LatLon(v.getDouble(i + 1), v.getDouble(i))
                        i += 2
                    }
                    val d = road.optDouble("distance", 0.0)
                    busy += Triple(roadAt, roadAt + d, road.optInt("traffic_state", 0))
                    roadAt += d
                }
                val g = section.optJSONArray("guides") ?: continue
                for (i in 0 until g.length()) {
                    val guide = g.getJSONObject(i)
                    spots += Triple(LatLon(guide.optDouble("y", 0.0), guide.optDouble("x", 0.0)), guide.optInt("type", 0), guide.optString("name"))
                }
            }
            // 안내 지점의 좌표를 경로 선 위의 거리로 바꾼다(안내에 적힌 거리를 더하는 것보다 꺾이는 곳에 정확히 맞는다)
            val guides = spots.map { Triple(RouteMath.alongOf(line, it.first), it.second, it.third) }
            val minutes = (route.optJSONObject("summary")?.optInt("duration", 0) ?: 0) / 60
            return PreviewRoute(line, Preview.spots(guides, busy), RouteMath.lengthM(line), minutes)
        }

        /** 앱에 넣어 둔 연습 코스(시험 자료)를 읽는다. 길 모양과 안내 지점은 OpenStreetMap 자료다(공개 OSRM 시험 서버로 한 번 받음) */
        fun parseSample(body: String): PreviewRoute {
            val o = JSONObject(body)
            val p = o.getJSONArray("points")
            val points = (0 until p.length()).map { p.getJSONArray(it).let { a -> LatLon(a.getDouble(0), a.getDouble(1)) } }
            val s = o.getJSONArray("spots")
            val spots = (0 until s.length()).map { s.getJSONArray(it).let { a -> Spot(a.getDouble(0), SpotKind.valueOf(a.getString(1)), a.getString(2)) } }
            return PreviewRoute(points, spots, RouteMath.lengthM(points), o.getInt("minutes"), sample = true)
        }

        private const val TAG = "RouteLab"
        private const val BASE = "https://apis-navi.kakaomobility.com/v1/directions"
        private const val OFF_ROUTE_M = 50.0
        private const val CHECK_MS = 2_000L
        private const val MIN_GAP_MS = 6_000L
    }
}
