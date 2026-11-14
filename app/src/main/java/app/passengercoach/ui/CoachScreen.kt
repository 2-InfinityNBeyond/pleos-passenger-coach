package app.passengercoach.ui

import app.passengercoach.dlog
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.passengercoach.coach.CoachEngine
import app.passengercoach.coach.CoachTopic
import app.passengercoach.coach.Record
import app.passengercoach.coach.Recap
import app.passengercoach.coach.RecordStore
import app.passengercoach.coach.Tip
import app.passengercoach.coach.TipPicker
import app.passengercoach.coach.DriveState
import app.passengercoach.coach.GearKind
import app.passengercoach.coach.NaviTurnKind
import app.passengercoach.coach.TurnDir
import app.passengercoach.demo.DemoPlayer
import app.passengercoach.signal.NaviHub
import app.passengercoach.signal.NaviSnapshot
import app.passengercoach.signal.SignalHub
import app.passengercoach.signal.SignalSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val LightColors = lightColorScheme(
    background = Color(0xFFF4F6FA),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    // 밝은 바탕 위에서 글자 대비 4.5:1 이상을 맞춘 회색
    onSurfaceVariant = Color(0xFF5B6270),
    primary = Color(0xFF1F3A8A),
    onPrimary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFB45309),
    outline = Color(0xFFB0B5BD),
)

// 야간 화면: 밤에 밝은 화면이 운전자 눈에 거슬리지 않게 어두운 바탕을 쓴다(차량의 야간 모드를 따른다)
private val DarkColors = darkColorScheme(
    background = Color(0xFF0B0F17),
    onBackground = Color(0xFFE5E7EB),
    surface = Color(0xFF1A2130),
    onSurface = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF9AA3B2),
    primary = Color(0xFF8FB0FF),
    onPrimary = Color(0xFF0B0F17),
    tertiary = Color(0xFFF59E0B),
    outline = Color(0xFF4B5563),
)

private val MutedColor: Color
    @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val CardColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface

/** 이 너비 이상이면 가로 배치, 미만이면 세로 배치 */
private val WIDE_MIN_WIDTH = 640.dp

/** 해설이 "방금 일"로 보이는 시간, 화면에 남는 시간(밀리초) */
private const val MESSAGE_FRESH_MS = 8_000L
private const val MESSAGE_CLEAR_MS = 30_000L

/** 지금 값을 다시 물어보는 간격. 끊김 기준(DriveState.STALE_MS)보다 짧아야 한다 */
private const val PING_MS = 4_000L

/** 시작 뒤 이 시간까지는 "차량 신호 없음" 대신 "연결하는 중"으로 보여 준다 */
private const val CONNECTING_MS = 3_000L

private enum class Page { MAIN, SETTINGS, RECORDS }

/** 연습 상황: 고른 상황의 해설만 보여 준다. 주행 전에 고르고, 주행 중에는 입력을 요구하지 않는다 */
private enum class Topic(val label: String, val filter: CoachTopic?) {
    ALL("전체", null),
    TURN("회전 (깜빡이, 핸들)", CoachTopic.TURN),
    STOP("출발과 정차 (브레이크, 기어)", CoachTopic.STOP),
    PARK("주차 (후진)", CoachTopic.PARK),
    DEVICE("등화와 장치 (전조등, 와이퍼, 안전벨트, 문)", CoachTopic.DEVICE),
}

/** 첫 실행 동의 여부에 따라 안내(S0) 또는 실시간 화면(S2)을 보여 준다. */
@Composable
fun CoachRoot(
    consented: Boolean,
    onAgree: () -> Unit,
    onWithdraw: () -> Unit,
    hub: SignalHub,
    navi: NaviHub,
    demo: DemoPlayer,
) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (consented) CoachApp(hub, navi, demo, onWithdraw) else ConsentScreen(onAgree)
        }
    }
}

@Composable
private fun ConsentScreen(onAgree: () -> Unit) {
    var declined by remember { mutableStateOf(false) }
    var driverOk by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("이세계주행천재", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("동승자를 위한 운전 학습 화면", fontSize = 20.sp, color = MutedColor)
        listOf(
            "운전자의 핸들, 브레이크, 방향지시등, 기어와 내비 안내를 동승자 화면에 보여 줘요.",
            "차량을 제어하지 않고, 운전자 화면에는 표시되지 않아요.",
            "차량 데이터는 이 기기 밖으로 보내지 않아요. 학습 기록은 설정에서 켠 경우에만 이 기기에 저장해요.",
            "교육을 돕는 도구예요. 실제 운전의 책임은 운전자에게 있어요.",
            "동의는 설정에서 언제든 철회할 수 있어요.",
        ).forEach { Text("• $it", fontSize = 20.sp) }
        // 조작이 화면에 보이는 사람은 운전자이므로, 운전자의 동의를 따로 받는다
        Row(
            Modifier.clickable { driverOk = !driverOk },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = driverOk, onCheckedChange = { driverOk = it })
            Text("운전자: 내 조작이 동승자 화면에 보이는 것에 동의해요. (운전자가 직접 눌러 주세요)", fontSize = 20.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAgree, enabled = driverOk) { Text("동의하고 시작", fontSize = 22.sp) }
            OutlinedButton(onClick = { declined = true }) { Text("동의하지 않음", fontSize = 22.sp) }
        }
        if (!driverOk) Text("운전자가 동의해야 시작할 수 있어요.", fontSize = 18.sp, color = MutedColor)
        if (declined) Text("동의하지 않으면 앱을 쓸 수 없어요. 마음이 바뀌면 '동의하고 시작'을 눌러 주세요.", fontSize = 18.sp, color = MutedColor)
    }
}

@Composable
private fun CoachApp(hub: SignalHub, navi: NaviHub, demo: DemoPlayer, onWithdraw: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("coach", Context.MODE_PRIVATE) }
    val records = remember { RecordStore(File(context.filesDir, RecordStore.FILE_NAME)) }
    var page by remember { mutableStateOf(Page.MAIN) }
    var topic by remember { mutableStateOf(Topic.entries.getOrElse(prefs.getInt("topic", 0)) { Topic.ALL }) }
    var coachMode by remember { mutableStateOf(prefs.getBoolean("coach_mode", false)) }
    var recordOn by remember { mutableStateOf(prefs.getBoolean("record_on", false)) }
    val topicNow by rememberUpdatedState(topic)
    val recordNow by rememberUpdatedState(recordOn)

    var demoOn by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    // key: 데모를 켜고 끌 때 이전 쪽의 마지막 값이 한 프레임 남아 상황 판정이 틀어지지 않게 한다
    val signals by key(demoOn) { (if (demoOn) demo.signals else hub.state).collectAsState() }
    val naviSnapshot by key(demoOn) { (if (demoOn) demo.navi else navi.state).collectAsState() }

    val engine = remember(demoOn) { CoachEngine() }
    var message by remember(demoOn) { mutableStateOf<String?>(null) }
    var messageAtMs by remember(demoOn) { mutableLongStateOf(0L) }
    var why by remember(demoOn) { mutableStateOf<String?>(null) }
    val tipPicker = remember(demoOn) { TipPicker() }
    val recap = remember(demoOn) { Recap() }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    LaunchedEffect(demoOn) {
        if (demoOn) demo.run()
        // 값이 안 바뀐 것과 신호가 끊긴 것을 구분하려고 주기적으로 지금 값을 다시 물어본다
        while (true) {
            delay(PING_MS)
            hub.ping()
        }
    }
    LaunchedEffect(demoOn) {
        (if (demoOn) demo.signals else hub.state).collect { s ->
            val at = System.currentTimeMillis()
            val text = engine.onSignals(s.steeringDeg, s.brakeOn, s.turn.toDir(), s.gear.toKind(), at)
                ?: engine.onExtras(s.lowBeamOn, s.highBeamOn, s.wiperOn, s.beltWarning, s.doorOpen)
                ?: return@collect
            // 고른 연습 상황의 해설만 보여 준다
            if (topicNow.filter != null && engine.topic != topicNow.filter) return@collect
            message = text
            recap.add(text)
            why = engine.why
            messageAtMs = at
            dlog("Coach") { "MESSAGE $text" }
            // 시험 자료(데모)는 실제 조작이 아니므로 기록하지 않는다
            if (recordNow && !demoOn) withContext(Dispatchers.IO) { runCatching { records.add(at, text) } }
        }
    }
    LaunchedEffect(demoOn) {
        (if (demoOn) demo.navi else navi.state).collect { n -> engine.onNavi(n.turn.toNaviKind(), n.turnDistanceM, System.currentTimeMillis()) }
    }

    when (page) {
        Page.SETTINGS -> SettingsScreen(
            topic = topic,
            onTopic = {
                topic = it
                prefs.edit().putInt("topic", it.ordinal).apply()
            },
            coachMode = coachMode,
            onCoachMode = {
                coachMode = it
                prefs.edit().putBoolean("coach_mode", it).apply()
            },
            recordOn = recordOn,
            onRecordOn = {
                recordOn = it
                prefs.edit().putBoolean("record_on", it).apply()
            },
            onRecords = { page = Page.RECORDS },
            onWithdraw = onWithdraw,
            onBack = { page = Page.MAIN },
        )
        Page.RECORDS -> RecordsScreen(records, onBack = { page = Page.SETTINGS })
        Page.MAIN -> CoachScreen(
            s = signals,
            n = naviSnapshot,
            // 오래된 해설을 지금 일처럼 보이지 않게: 잠시 뒤 흐리게, 더 지나면 지운다
            message = message.takeIf { now - messageAtMs < MESSAGE_CLEAR_MS },
            // 코치 모드: 강사가 직접 설명하므로 설명 줄을 숨긴다
            why = why.takeIf { !coachMode },
            // 멈춰 있을 때(주차, 정차)는 방금 한 조작을 순서대로 되짚는다
            recap = recap.update(signals.gear == VehicleGear.GEAR_PARK || DriveState.isStopped(signals.gear.toKind(), signals.brakeOn, signals.steeringDeg)),
            // 상황에 맞는 알아 두기. 코치 모드에서는 강사가 직접 설명하므로 숨긴다
            tip = tipPicker.pick(signals.gear.toKind(), signals.brakeOn, signals.steeringDeg, naviSnapshot.turn.toNaviKind(), naviSnapshot.turnDistanceM)
                ?.takeIf { !coachMode }?.let { TipPicker.tip(it) },
            messageOld = now - messageAtMs > MESSAGE_FRESH_MS,
            demoOn = demoOn,
            now = now,
            onRetry = {
                hub.restart()
                navi.restart()
            },
            onTitleTap = {
                taps += 1
                if (taps >= 5) {
                    taps = 0
                    demoOn = !demoOn
                }
            },
            onSettings = { page = Page.SETTINGS },
        )
    }
}

@Composable
private fun SettingsScreen(
    topic: Topic,
    onTopic: (Topic) -> Unit,
    coachMode: Boolean,
    onCoachMode: (Boolean) -> Unit,
    recordOn: Boolean,
    onRecordOn: (Boolean) -> Unit,
    onRecords: () -> Unit,
    onWithdraw: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmWithdraw by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("설정", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onBack) { Text("돌아가기", fontSize = 20.sp) }
        }
        Text("주행 전에 정해 주세요. 주행 중에는 바꿀 필요가 없어요.", fontSize = 18.sp, color = MutedColor)

        Text("연습 상황", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("고른 상황의 해설만 보여 줘요. 조작 표시는 그대로예요.", fontSize = 18.sp, color = MutedColor)
        Topic.entries.forEach { t ->
            if (t == topic) Button(onClick = {}) { Text("✓ ${t.label}", fontSize = 20.sp) }
            else OutlinedButton(onClick = { onTopic(t) }) { Text(t.label, fontSize = 20.sp) }
        }

        SwitchRow("코치 모드", "설명 줄을 숨기고 일어난 일만 보여 줘요. 강사가 직접 설명할 때 써요.", coachMode, onCoachMode)
        SwitchRow("학습 기록", "해설 문장과 시각을 이 기기 안에만 저장해요. 밖으로 보내지 않아요.", recordOn, onRecordOn)
        OutlinedButton(onClick = onRecords) { Text("학습 기록 보기", fontSize = 20.sp) }

        Text("동의", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (!confirmWithdraw) {
            OutlinedButton(onClick = { confirmWithdraw = true }) { Text("동의 철회", fontSize = 20.sp) }
        } else {
            Text("철회하면 학습 기록과 설정이 지워지고 처음 화면으로 돌아가요.", fontSize = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onWithdraw) { Text("철회하기", fontSize = 20.sp) }
                OutlinedButton(onClick = { confirmWithdraw = false }) { Text("취소", fontSize = 20.sp) }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 18.sp, color = MutedColor)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RecordsScreen(records: RecordStore, onBack: () -> Unit) {
    var list by remember { mutableStateOf(emptyList<Record>()) }
    LaunchedEffect(Unit) { list = withContext(Dispatchers.IO) { runCatching { records.load() }.getOrDefault(emptyList()) } }
    val format = remember { SimpleDateFormat("M월 d일 HH:mm:ss", Locale.KOREA) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("학습 기록", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Button(onClick = onBack) { Text("돌아가기", fontSize = 20.sp) }
        }
        if (list.isEmpty()) {
            Text("기록이 없어요. 설정에서 학습 기록을 켜면 해설이 여기에 쌓여요.", fontSize = 20.sp, color = MutedColor)
        } else {
            Text("최근 ${list.size}개 · 이 기기 안에만 있어요", fontSize = 18.sp, color = MutedColor)
            OutlinedButton(onClick = {
                records.clear()
                list = emptyList()
            }) { Text("모두 지우기", fontSize = 20.sp) }
            list.asReversed().forEach { r ->
                Column {
                    Text(format.format(Date(r.atMs)), fontSize = 16.sp, color = MutedColor)
                    Text(r.text, fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun CoachScreen(
    s: SignalSnapshot,
    n: NaviSnapshot,
    message: String?,
    why: String?,
    recap: List<String>,
    tip: Tip?,
    messageOld: Boolean,
    demoOn: Boolean,
    now: Long,
    onRetry: () -> Unit,
    onTitleTap: () -> Unit,
    onSettings: () -> Unit,
) {
    val allEmpty = s.steeringDeg == null && s.brakeOn == null && s.turn == null && s.gear == null
    val noSignal = !s.connected || allEmpty
    val last = s.lastUpdateMs()
    // 데모는 실제 차량 값이 아니므로 끊김 판정을 하지 않는다
    val stale = DriveState.isStale(demoOn, !noSignal, last, now)
    val staleSeconds = if (stale) ((now - last) / 1000).toInt() else 0

    val headerLabel = stateLabel(s)
    LaunchedEffect(headerLabel) { dlog("Coach") { "STATE $headerLabel" } }
    LaunchedEffect(stale) { if (stale) dlog("Coach") { "STALE no signal for ${staleSeconds}s" } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_MIN_WIDTH
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("이세계주행천재", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onTitleTap))
                if (demoOn) Text("시험 자료(데모)", fontSize = 18.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stateLabel(s), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (stale) MutedColor else Color.Unspecified)
                    TextButton(onClick = onSettings) { Text("설정", fontSize = 20.sp) }
                }
            }
            if (noSignal && now - s.startedAtMs < CONNECTING_MS) {
                Text("차량에 연결하는 중", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MutedColor)
            } else if (noSignal) {
                Text("차량 신호 없음", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MutedColor)
                s.error?.let { Text(it, fontSize = 16.sp, color = MutedColor) }
                Button(onClick = onRetry) { Text("다시 시도", fontSize = 22.sp) }
            } else {
                if (stale) {
                    Text("차량 신호 없음 · 마지막 수신 ${staleSeconds}초 전", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MutedColor)
                    Button(onClick = onRetry) { Text("다시 시도", fontSize = 22.sp) }
                }
                // 가로 배치: 해설과 알아 두기가 스크롤 없이 한 화면에 들어오도록 내비 안내를 오른쪽 열로 올린다
                val wheelSize = if (wide) 140.dp else 130.dp
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        SteeringCard(s, stale, wheelSize, demoOn, Modifier.weight(1f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            NaviCard(n, dim = stale)
                            TurnCard(s, stale)
                            BrakeCard(s, stale)
                            DeviceLine(s, stale)
                        }
                    }
                } else {
                    NaviCard(n, dim = stale)
                    SteeringCard(s, stale, wheelSize, demoOn, Modifier.fillMaxWidth())
                    TurnCard(s, stale)
                    BrakeCard(s, stale)
                    DeviceLine(s, stale)
                }
                if (recap.size >= 2) RecapCard(recap) else CoachCard(message, why, dim = stale || messageOld)
                if (tip != null && !stale) TipCard(tip)
            }
        }
    }
}

@Composable
private fun CoachCard(message: String?, why: String?, dim: Boolean) {
    Box(Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column {
            Text("해설", fontSize = 16.sp, color = MutedColor)
            Text(
                message ?: "운전자의 조작이 감지되면 이곳에 한 문장으로 알려 줘요.",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (message == null || dim) MutedColor else Color.Unspecified,
            )
            if (message != null && why != null) Text(why, fontSize = 20.sp, color = MutedColor)
        }
    }
}

@Composable
private fun RecapCard(items: List<String>) {
    Box(Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("방금 한 조작 · 순서대로", fontSize = 16.sp, color = MutedColor)
            items.forEachIndexed { i, text ->
                // 마지막 줄이 가장 최근 조작
                Text("${i + 1}. $text", fontSize = 20.sp, fontWeight = if (i == items.lastIndex) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

/** 전조등, 와이퍼, 안전벨트, 문: 받은 값만 한 줄로 보여 준다. 하나도 못 받았으면 줄을 그리지 않는다 */
@Composable
private fun DeviceLine(s: SignalSnapshot, dim: Boolean) {
    val parts = listOfNotNull(
        s.lowBeamOn?.let { if (s.highBeamOn == true) "상향등 켜짐" else if (it) "전조등 켜짐" else "전조등 꺼짐" },
        s.wiperOn?.let { if (it) "와이퍼 켜짐" else "와이퍼 꺼짐" },
        s.beltWarning?.let { if (it) "운전석 안전벨트 풀림" else "안전벨트 맴" },
        s.doorOpen?.let { if (it) "문 열림" else "문 닫힘" },
    )
    if (parts.isNotEmpty()) Text(parts.joinToString(" · "), fontSize = 16.sp, color = MutedColor)
}

@Composable
private fun TipCard(tip: Tip) {
    Box(Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("알아 두기 · ${tip.title}", fontSize = 16.sp, color = MutedColor)
            tip.lines.forEach { Text("• $it", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun NaviCard(n: NaviSnapshot, dim: Boolean) {
    Column {
        Text("내비 다음 안내", fontSize = 20.sp)
        val turn = n.turn
        if (turn == null) {
            // 경로가 없는 것과 내비 정보를 못 받는 것을 구분한다
            Text(if (!n.ready && n.error != null) "내비 정보를 받을 수 없어요" else "내비 안내 없음", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MutedColor)
        } else {
            val dist = n.turnDistanceM?.let { if (it >= 1000) "  %.1fkm".format(it / 1000) else "  ${it.roundToInt()}m" } ?: ""
            Text(turnLabel(turn, n.turnText) + dist, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
        }
    }
}

/**
 * 핸들 각도. SDK 값은 양수=왼쪽(반시계), 음수=오른쪽(시계)이다(가상 콘솔 소스로 확인).
 * 화면 회전은 시계 방향이 양수이므로 부호를 뒤집는다.
 */
@Composable
private fun SteeringCard(s: SignalSnapshot, dim: Boolean, wheelSize: Dp, demo: Boolean, modifier: Modifier) {
    val angle = s.steeringDeg
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if ("steering" in s.unsupported) {
            Text("핸들", fontSize = 20.sp)
            Text(unsupportedText("핸들 각도"), fontSize = 22.sp, color = MutedColor)
            return@Column
        }
        val color = if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
        if (s.gear == VehicleGear.GEAR_REVERSE) {
            // 후진 중: 핸들을 돌리면 차 뒤쪽이 어디로 가는지 보이도록 합성 후방 화면을 크게, 핸들은 작게 겹쳐 보여 준다
            Box(Modifier.fillMaxWidth().height(wheelSize + 30.dp)) {
                RearView(angle ?: 0f, moving = s.brakeOn == false, demo = demo, modifier = Modifier.fillMaxSize())
                Wheel(angle, 56.dp, Color.White, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
            Text(angleLabel(angle), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
        } else {
            Text("핸들", fontSize = 20.sp)
            Wheel(angle, wheelSize, color, Modifier)
            Text(angleLabel(angle), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
        }
    }
}

@Composable
private fun Wheel(angle: Float?, wheelSize: Dp, color: Color, modifier: Modifier) {
    Canvas(modifier.size(wheelSize).semantics { contentDescription = "핸들 " + angleLabel(angle) }) {
        val c = Offset(size.width / 2, size.height / 2)
        val line = size.minDimension * 0.09f
        val r = size.minDimension / 2 - line / 2
        rotate(degrees = -(angle ?: 0f), pivot = c) {
            drawCircle(color = color, radius = r, center = c, style = Stroke(width = line))
            drawLine(color, c, Offset(c.x - r, c.y), strokeWidth = line * 0.9f)
            drawLine(color, c, Offset(c.x + r, c.y), strokeWidth = line * 0.9f)
            drawLine(color, c, Offset(c.x, c.y + r), strokeWidth = line * 0.9f)
            drawCircle(color = Color(0xFFE11D48), radius = line * 0.65f, center = Offset(c.x, c.y - r))
        }
    }
}

@Composable
private fun TurnCard(s: SignalSnapshot, dim: Boolean) {
    Column {
        Text("방향지시등", fontSize = 20.sp)
        if ("turn" in s.unsupported) {
            Text(unsupportedText("방향지시등"), fontSize = 22.sp, color = MutedColor)
            return@Column
        }
        val label = when (s.turn) {
            TurnSignalValue.LEFT -> "◀ 왼쪽 깜빡이"
            TurnSignalValue.RIGHT -> "오른쪽 깜빡이 ▶"
            TurnSignalValue.OFF -> "방향지시등 꺼짐"
            else -> "-"
        }
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
    }
}

@Composable
private fun BrakeCard(s: SignalSnapshot, dim: Boolean) {
    Column {
        Text("브레이크", fontSize = 20.sp)
        if ("brake" in s.unsupported) {
            Text(unsupportedText("브레이크"), fontSize = 22.sp, color = MutedColor)
            return@Column
        }
        val label = when (s.brakeOn) {
            true -> "● 브레이크 켜짐"
            false -> "○ 브레이크 꺼짐"
            null -> "-"
        }
        Text(label, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
    }
}

private fun unsupportedText(name: String) = "이 차량은 $name 신호를 제공하지 않아요"

private fun angleLabel(angle: Float?): String {
    if (angle == null) return "-"
    val deg = abs(angle).roundToInt()
    return when {
        deg == 0 -> "가운데 0°"
        else -> {
            // 주차처럼 크게 돌릴 때는 각도만으로 감이 안 오므로 바퀴 수를 함께 보여 준다
            val turns = (abs(angle) / CoachEngine.HALF_TURN_DEG).toInt().let { if (it >= 1) " (${CoachEngine.turnsLabel(it)})" else "" }
            (if (angle > 0f) "왼쪽" else "오른쪽") + " $deg°$turns"
        }
    }
}

private fun turnLabel(type: TurnType, description: String?): String = when (type) {
    TurnType.TURN_LEFT -> "◀ 좌회전"
    TurnType.TURN_RIGHT -> "우회전 ▶"
    TurnType.TURN_STRAIGHT -> "직진"
    TurnType.TURN_KEEP_L -> "◀ 왼쪽 방향 유지"
    TurnType.TURN_KEEP_R -> "오른쪽 방향 유지 ▶"
    TurnType.TURN_SHARP_L -> "◀ 크게 좌회전"
    TurnType.TURN_SHARP_R -> "크게 우회전 ▶"
    TurnType.TURN_UTURN -> "유턴"
    TurnType.TURN_ARRIVE -> "목적지 도착"
    else -> description?.takeIf { it.isNotBlank() } ?: type.name.removePrefix("TURN_")
}

/** 시동 켜짐/꺼짐은 공개 SDK에 신호가 없어 표기하지 않는다. 기어와 브레이크, 핸들로 판정 가능한 상태만 쓴다. */
private fun stateLabel(s: SignalSnapshot): String =
    DriveState.stateText(s.gear.toKind(), s.brakeOn, s.steeringDeg, s.gear?.name?.removePrefix("GEAR_"))

private fun TurnSignalValue?.toDir(): TurnDir? = when (this) {
    TurnSignalValue.LEFT -> TurnDir.LEFT
    TurnSignalValue.RIGHT -> TurnDir.RIGHT
    TurnSignalValue.OFF -> TurnDir.OFF
    else -> null
}

private fun VehicleGear?.toKind(): GearKind? = when (this) {
    null -> null
    VehicleGear.GEAR_PARK -> GearKind.PARK
    VehicleGear.GEAR_NEUTRAL -> GearKind.NEUTRAL
    VehicleGear.GEAR_REVERSE -> GearKind.REVERSE
    VehicleGear.GEAR_DRIVE -> GearKind.DRIVE
    else -> GearKind.OTHER
}

private fun TurnType?.toNaviKind(): NaviTurnKind? = when (this) {
    null -> null
    TurnType.TURN_LEFT, TurnType.TURN_SHARP_L, TurnType.TURN_KEEP_L -> NaviTurnKind.LEFT
    TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R, TurnType.TURN_KEEP_R -> NaviTurnKind.RIGHT
    TurnType.TURN_UTURN -> NaviTurnKind.UTURN
    else -> NaviTurnKind.OTHER
}
