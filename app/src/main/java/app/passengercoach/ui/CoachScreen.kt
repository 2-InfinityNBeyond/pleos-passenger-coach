package app.passengercoach.ui

import app.passengercoach.dlog
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.passengercoach.coach.CoachEngine
import app.passengercoach.coach.DriveState
import app.passengercoach.coach.GearKind
import app.passengercoach.coach.NaviTurnKind
import app.passengercoach.coach.TurnDir
import app.passengercoach.demo.DemoPlayer
import app.passengercoach.signal.NaviHub
import app.passengercoach.signal.NaviSnapshot
import app.passengercoach.signal.SignalHub
import app.passengercoach.signal.SignalSnapshot
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

// 밝은 바탕(#F4F6FA) 위에서 글자 대비 4.5:1 이상을 맞춘 회색
private val MutedColor = Color(0xFF5B6270)
private val CardColor = Color(0xFFFFFFFF)

/** 이 너비 이상이면 가로 배치, 미만이면 세로 배치 */
private val WIDE_MIN_WIDTH = 640.dp

/** 첫 실행 동의 여부에 따라 안내(S0) 또는 실시간 화면(S2)을 보여 준다. */
@Composable
fun CoachRoot(
    consented: Boolean,
    onAgree: () -> Unit,
    hub: SignalHub,
    navi: NaviHub,
    demo: DemoPlayer,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF4F6FA)) {
            if (consented) CoachApp(hub, navi, demo) else ConsentScreen(onAgree)
        }
    }
}

@Composable
private fun ConsentScreen(onAgree: () -> Unit) {
    var declined by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("이세계주행천재", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("동승자를 위한 운전 학습 화면", fontSize = 20.sp, color = MutedColor)
        listOf(
            "운전자의 핸들, 브레이크, 방향지시등, 기어와 내비 안내를 동승자 화면에 보여 줘요.",
            "차량을 제어하지 않고, 운전자 화면에는 표시되지 않아요.",
            "차량 데이터는 이 기기 안에서만 쓰고, 저장하거나 보내지 않아요.",
            "운전자에게 이 앱을 쓴다고 알려 주세요. 운전자의 조작이 동승자 화면에 보여요.",
            "교육을 돕는 도구예요. 실제 운전의 책임은 운전자에게 있어요.",
        ).forEach { Text("• $it", fontSize = 20.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onAgree) { Text("동의하고 시작", fontSize = 22.sp) }
            OutlinedButton(onClick = { declined = true }) { Text("동의하지 않음", fontSize = 22.sp) }
        }
        if (declined) Text("동의하지 않으면 앱을 쓸 수 없어요. 마음이 바뀌면 '동의하고 시작'을 눌러 주세요.", fontSize = 18.sp, color = MutedColor)
    }
}

@Composable
private fun CoachApp(hub: SignalHub, navi: NaviHub, demo: DemoPlayer) {
    var demoOn by remember { mutableStateOf(false) }
    var taps by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val signals by (if (demoOn) demo.signals else hub.state).collectAsState()
    val naviSnapshot by (if (demoOn) demo.navi else navi.state).collectAsState()

    val engine = remember(demoOn) { CoachEngine() }
    var message by remember(demoOn) { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    LaunchedEffect(demoOn) { if (demoOn) demo.run() }
    LaunchedEffect(demoOn) {
        (if (demoOn) demo.signals else hub.state).collect { s ->
            engine.onSignals(s.steeringDeg, s.brakeOn, s.turn.toDir(), s.gear.toKind(), System.currentTimeMillis())?.let {
                message = it
                dlog("Coach") { "MESSAGE $it" }
            }
        }
    }
    LaunchedEffect(demoOn) {
        (if (demoOn) demo.navi else navi.state).collect { n -> engine.onNavi(n.turn.toNaviKind(), n.turnDistanceM, System.currentTimeMillis()) }
    }

    CoachScreen(
        s = signals,
        n = naviSnapshot,
        message = message,
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
    )
}

@Composable
private fun CoachScreen(
    s: SignalSnapshot,
    n: NaviSnapshot,
    message: String?,
    demoOn: Boolean,
    now: Long,
    onRetry: () -> Unit,
    onTitleTap: () -> Unit,
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
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("이세계주행천재", fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onTitleTap))
                if (demoOn) Text("시험 자료(데모)", fontSize = 18.sp, color = Color(0xFFB45309), fontWeight = FontWeight.Bold)
                Text(stateLabel(s), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (stale) MutedColor else Color.Unspecified)
            }
            if (noSignal) {
                Text("차량 신호 없음", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MutedColor)
                s.error?.let { Text(it, fontSize = 16.sp, color = MutedColor) }
                Button(onClick = onRetry) { Text("다시 시도", fontSize = 22.sp) }
            } else {
                if (stale) {
                    Text("차량 신호 없음 · 마지막 수신 ${staleSeconds}초 전", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MutedColor)
                    Button(onClick = onRetry) { Text("다시 시도", fontSize = 22.sp) }
                }
                NaviCard(n, dim = stale)
                val wheelSize = if (wide) 170.dp else 130.dp
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        SteeringCard(s, stale, wheelSize, Modifier.weight(1f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            TurnCard(s, stale)
                            BrakeCard(s, stale)
                        }
                    }
                } else {
                    SteeringCard(s, stale, wheelSize, Modifier.fillMaxWidth())
                    TurnCard(s, stale)
                    BrakeCard(s, stale)
                }
                CoachCard(message, dim = stale)
            }
        }
    }
}

@Composable
private fun CoachCard(message: String?, dim: Boolean) {
    Box(Modifier.fillMaxWidth().background(CardColor, RoundedCornerShape(12.dp)).padding(16.dp)) {
        Column {
            Text("해설", fontSize = 18.sp, color = MutedColor)
            Text(
                message ?: "운전자의 조작이 감지되면 이곳에 한 문장으로 알려 줘요.",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (message == null || dim) MutedColor else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun NaviCard(n: NaviSnapshot, dim: Boolean) {
    Column {
        Text("내비 다음 안내", fontSize = 20.sp)
        val turn = n.turn
        if (turn == null) {
            Text("내비 안내 없음", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MutedColor)
        } else {
            val dist = n.turnDistanceM?.let { "  ${it.roundToInt()}m" } ?: ""
            Text(turnLabel(turn, n.turnText) + dist, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
        }
    }
}

/**
 * 핸들 각도. SDK 값은 양수=왼쪽(반시계), 음수=오른쪽(시계)이다(가상 콘솔 소스로 확인).
 * 화면 회전은 시계 방향이 양수이므로 부호를 뒤집는다.
 */
@Composable
private fun SteeringCard(s: SignalSnapshot, dim: Boolean, wheelSize: Dp, modifier: Modifier) {
    val angle = s.steeringDeg
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("핸들", fontSize = 20.sp)
        if ("steering" in s.unsupported) {
            Text(unsupportedText("핸들 각도"), fontSize = 22.sp, color = MutedColor)
            return@Column
        }
        Canvas(Modifier.size(wheelSize)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2 - 8f
            val color = if (dim) Color(0xFFB0B5BD) else Color(0xFF1F3A8A)
            rotate(degrees = -(angle ?: 0f), pivot = c) {
                drawCircle(color = color, radius = r, center = c, style = Stroke(width = 16f))
                drawLine(color, c, Offset(c.x - r, c.y), strokeWidth = 14f)
                drawLine(color, c, Offset(c.x + r, c.y), strokeWidth = 14f)
                drawLine(color, c, Offset(c.x, c.y + r), strokeWidth = 14f)
                drawCircle(color = Color(0xFFE11D48), radius = 10f, center = Offset(c.x, c.y - r))
            }
        }
        Text(angleLabel(angle), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (dim) MutedColor else Color.Unspecified)
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
        angle > 0f -> "왼쪽 $deg°"
        else -> "오른쪽 $deg°"
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
    else -> NaviTurnKind.OTHER
}
