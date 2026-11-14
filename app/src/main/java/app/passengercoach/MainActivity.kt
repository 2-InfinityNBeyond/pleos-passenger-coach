package app.passengercoach

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.passengercoach.coach.RecordStore
import app.passengercoach.demo.DemoPlayer
import java.io.File
import app.passengercoach.signal.NaviHub
import app.passengercoach.signal.SignalHub
import app.passengercoach.signal.RoadHub
import app.passengercoach.signal.RouteHub
import app.passengercoach.signal.TrafficLightHub
import app.passengercoach.ui.CoachRoot

class MainActivity : ComponentActivity() {
    private lateinit var hub: SignalHub
    private lateinit var navi: NaviHub
    private val lights = TrafficLightHub(BuildConfig.SIGNAL_API_KEY)
    private val routes = RouteHub(BuildConfig.ROUTE_API_KEY)
    private lateinit var roads: RoadHub
    private val demo = DemoPlayer()

    private var consented by mutableStateOf(false)
    private var receiverRegistered = false

    /** 개발용 경로 요청(debug 빌드에서만 등록): adb shell am broadcast -a app.passengercoach.DEBUG_ROUTE --ed lat <위도> --ed lon <경도> */
    private val debugRoute = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val lat = intent.getDoubleExtra("lat", Double.NaN)
            val lon = intent.getDoubleExtra("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN()) return
            // adb shell am broadcast -a app.passengercoach.DEBUG_LOCATION --ed lat <위도> --ed lon <경도> --ed heading <방위>
            if (intent.action == ACTION_LOCATION) navi.setDebugLocation(lat, lon, intent.getDoubleExtra("heading", 0.0))
            else navi.requestDebugRoute(lat, lon)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hub = SignalHub(applicationContext)
        navi = NaviHub(applicationContext)
        roads = RoadHub(cacheDir)
        consented = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CONSENT, false)
        setContent {
            CoachRoot(
                consented = consented,
                onAgree = {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_CONSENT, true).apply()
                    consented = true
                    startSignals()
                },
                onWithdraw = {
                    // 동의 철회: 신호 읽기를 멈추고, 저장한 설정과 학습 기록을 지운다
                    hub.stop()
                    navi.stop()
                    lights.stop()
                    routes.stop()
                    roads.stop()
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply()
                    RecordStore(File(filesDir, RecordStore.FILE_NAME)).clear()
                    RecordStore(File(filesDir, RecordStore.ASKS_FILE)).clear()
                    consented = false
                },
                hub = hub,
                navi = navi,
                lights = lights,
                routes = routes,
                roads = roads,
                demo = demo,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        // 동의 전에는 차량 신호를 읽지 않는다. 동의 후에는 다른 앱(내비)이 앞에 와도 계속 받도록 onDestroy에서 해제한다.
        if (consented) startSignals()
    }

    override fun onResume() {
        super.onResume()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable && !receiverRegistered) {
            ContextCompat.registerReceiver(
                this,
                debugRoute,
                IntentFilter("app.passengercoach.DEBUG_ROUTE").apply { addAction(ACTION_LOCATION) },
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(debugRoute) }
        navi.stop()
        hub.stop()
        lights.stop()
        routes.stop()
        roads.stop()
        super.onDestroy()
    }

    private fun startSignals() {
        hub.start()
        navi.start()
        lights.start()
        routes.start()
        roads.start()
    }

    private companion object {
        const val PREFS = "coach"
        const val KEY_CONSENT = "consent_v5"
        const val ACTION_LOCATION = "app.passengercoach.DEBUG_LOCATION"
    }
}
