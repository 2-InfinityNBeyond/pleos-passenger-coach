package app.passengercoach.signal

import android.util.Log
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.MapMath
import app.passengercoach.coach.Road
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/**
 * 지도에 그릴 주변 길 모양을 OpenStreetMap(Overpass API)에서 받는다. 인증키가 필요 없다.
 * 지도 그림을 받지 않고 길 모양만 받아 앱이 직접 그리므로, 진행 방향으로 돌려도 글자가 뒤집히지 않고 색을 앱에 맞출 수 있다.
 * 위치는 약 100m 단위로 뭉뚱그려 보낸다. 받지 못해도 앱은 그대로 동작한다(경로 선과 차만 보인다).
 * shortcut: 공용 시험 서버를 쓴다. 출시할 때는 사용 조건에 맞는 지도 자료 서비스로 바꾼다
 */
class RoadHub(private val cacheDir: File) {
    private val mutableState = MutableStateFlow<List<Road>>(emptyList())
    val state: StateFlow<List<Road>> = mutableState

    private var scope: CoroutineScope? = null
    private var job: Job? = null

    @Volatile private var here: LatLon? = null
    private var fetchedAt: LatLon? = null

    fun onLocation(lat: Double?, lon: Double?) {
        here = if (lat != null && lon != null) LatLon(lat, lon) else null
    }

    fun start() {
        if (job != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        job = s.launch {
            while (isActive) {
                val wait = try {
                    step()
                    CHECK_MS
                } catch (e: Exception) {
                    Log.w(TAG, "ROADS_FAILED ${e.javaClass.simpleName}: ${e.message}")
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
    }

    private fun step() {
        val p = here ?: return
        val last = fetchedAt
        if (last != null && RouteMath.distanceM(last, p) < REFETCH_M) return
        // 소수 셋째 자리(약 100m)까지만 보낸다
        val lat = (p.lat * 1000).roundToInt() / 1000.0
        val lon = (p.lon * 1000).roundToInt() / 1000.0
        // 같은 자리의 길 모양은 바뀌지 않으므로 한 번 받은 것은 기기에 두고 다시 쓴다(공용 서버가 자주 느리다)
        val cache = File(cacheDir, "roads_${lat}_$lon.json")
        val body = if (cache.exists()) cache.readText() else download(lat, lon).also { runCatching { cache.writeText(it) } }
        val roads = parse(body)
        fetchedAt = p
        mutableState.value = roads
        dlog(TAG) { "ROADS ways=${roads.size} cached=${cache.exists()}" }
    }

    private var server = 0

    /** 공용 서버 여러 곳을 돌아가며 시도한다. 실패하면 다음 번에는 다른 서버를 쓴다 */
    private fun download(lat: Double, lon: Double): String {
        val query = "[out:json][timeout:20];way(around:$RADIUS_M,$lat,$lon)[\"highway\"~\"^($KINDS)(_link)?$\"];out geom;"
        val base = SERVERS[server % SERVERS.size]
        val c = URL("$base?data=" + URLEncoder.encode(query, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 25_000
        c.setRequestProperty("User-Agent", "NunchiCoach/0.2 (contest prototype)")
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            server++
            throw e
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** Overpass 응답(JSON)을 길 목록으로 바꾼다. 앱에 넣어 둔 데모 구간 자료를 읽는 데도 쓴다 */
        fun parse(body: String): List<Road> {
            val elements = JSONObject(body).getJSONArray("elements")
            val roads = ArrayList<Road>(elements.length())
            for (i in 0 until elements.length()) {
                val e = elements.getJSONObject(i)
                val g = e.optJSONArray("geometry") ?: continue
                val points = (0 until g.length()).map { LatLon(g.getJSONObject(it).getDouble("lat"), g.getJSONObject(it).getDouble("lon")) }
                roads += Road(points, MapMath.levelOf(e.optJSONObject("tags")?.optString("highway").orEmpty()))
            }
            return roads
        }

        private const val TAG = "RoadLab"
        private val SERVERS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
        )
        private const val KINDS = "motorway|trunk|primary|secondary|tertiary|residential|unclassified|living_street|service"
        private const val RADIUS_M = 600

        /** 받은 자리에서 이만큼 벗어나면 다시 받는다 */
        private const val REFETCH_M = 300.0
        private const val CHECK_MS = 2_000L
        private const val RETRY_MS = 6_000L
    }
}
