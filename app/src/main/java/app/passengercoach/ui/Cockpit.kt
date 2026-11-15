package app.passengercoach.ui

import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.passengercoach.coach.CoachEngine
import app.passengercoach.coach.DriveState
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.MapData
import app.passengercoach.coach.LightColor
import app.passengercoach.coach.LightInfo
import app.passengercoach.coach.LightPhase
import app.passengercoach.coach.Road
import app.passengercoach.coach.RoadSigns
import app.passengercoach.coach.Tip
import app.passengercoach.coach.TrafficLights
import app.passengercoach.signal.NaviSnapshot
import app.passengercoach.signal.SignalSnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/** 새 주행 화면의 색. 낮과 밤 두 벌이며, 차량의 야간 모드를 따른다 */
internal data class CockpitColors(
    val bg: Color, val card: Color, val text: Color, val muted: Color,
    val accent: Color, val onAccent: Color,
    val bar: Color, val barText: Color, val barMuted: Color,
    val chip: Color, val chipText: Color, val chipMuted: Color,
    val off: Color, val offGlyph: Color,
    val green: Color, val greenBg: Color, val red: Color, val redText: Color,
    val ask: Color, val askText: Color, val mapBase: Color,
)

internal val Day = CockpitColors(
    bg = Color(0xFFF3F2F8), card = Color(0xFFFFFFFF), text = Color(0xFF17162B), muted = Color(0xFF5F5E76),
    accent = Color(0xFF5B4BFF), onAccent = Color(0xFFFFFFFF),
    bar = Color(0xFF17162B), barText = Color(0xFFFFFFFF), barMuted = Color(0xFFC9C6F5),
    chip = Color(0xFF17162B), chipText = Color(0xFFFFFFFF), chipMuted = Color(0xFFC9C6F5),
    off = Color(0xFFEFEFF5), offGlyph = Color(0xFFC4C4D4),
    green = Color(0xFF18A957), greenBg = Color(0xFFDDF7E5), red = Color(0xFFE5484D), redText = Color(0xFFB4232A),
    ask = Color(0xFFFFD84D), askText = Color(0xFF17162B), mapBase = Color(0xFFE4E6F1),
)

internal val Night = CockpitColors(
    bg = Color(0xFF0C0C14), card = Color(0xFF191927), text = Color(0xFFF1F0FA), muted = Color(0xFFA9A8C2),
    accent = Color(0xFF8F86FF), onAccent = Color(0xFF0C0C14),
    bar = Color(0xFF222233), barText = Color(0xFFF1F0FA), barMuted = Color(0xFFA9A8C2),
    chip = Color(0xFFF1F0FA), chipText = Color(0xFF0C0C14), chipMuted = Color(0xFF4B4A63),
    off = Color(0xFF222233), offGlyph = Color(0xFF4B4A63),
    green = Color(0xFF3DDC84), greenBg = Color(0xFF123A26), red = Color(0xFFFF6369), redText = Color(0xFFFF8A8F),
    ask = Color(0xFFFFD84D), askText = Color(0xFF0C0C14), mapBase = Color(0xFF14141F),
)

/**
 * 새 주행 화면: 위는 앞유리 자리(진행 방향이 위로 가는 지도와 경로 선), 아래는 계기판(핸들, 깜빡이, 브레이크, 기어).
 * 글자는 해설 한 줄만 두고 나머지는 그림으로 보여 준다.
 */
@Composable
internal fun CockpitScreen(
    s: SignalSnapshot,
    n: NaviSnapshot,
    light: LightInfo?,
    roads: List<Road>,
    map: MapData?,
    route: List<LatLon>,
    message: String?,
    why: String?,
    messageOld: Boolean,
    recap: List<String>,
    tip: Tip?,
    still: Boolean,
    demoOn: Boolean,
    now: Long,
    asked: Int,
    onRetry: () -> Unit,
    onDemoTap: () -> Unit,
    onSettings: () -> Unit,
    onAsk: () -> Unit,
    onPreview: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) Night else Day
    val allEmpty = s.steeringDeg == null && s.brakeOn == null && s.turn == null && s.gear == null
    val noSignal = !s.connected || allEmpty
    val last = s.lastUpdateMs()
    val stale = DriveState.isStale(demoOn, !noSignal, last, now)
    val reversing = s.gear == VehicleGear.GEAR_REVERSE

    // 좁은 창(세로로 긴 화면, 나눠 쓰는 창)에서는 핸들을 줄이고 각도 숫자를 뺀다
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
    val compact = maxWidth < 600.dp
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.mapBase)) {
            if (reversing) {
                RearView(s.steeringDeg ?: 0f, moving = s.brakeOn == false, demo = demoOn, modifier = Modifier.fillMaxSize())
            } else {
                MapPanel(n.lat, n.lon, n.heading, roads, route, c, Modifier.fillMaxSize(), map = map)
                // 먼 쪽을 흐리게 해서 앞유리 너머로 보는 느낌을 준다
                Box(Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.75f), Color.Transparent))))
                Text("© OpenStreetMap contributors", fontSize = 10.sp, color = c.muted, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
            }

            Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 상태 알림은 오른쪽 위에 모아서 다음 안내 칩과 겹치지 않게 한다
                if (stale) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Pill("차량 신호 없음 · ${((now - last) / 1000).toInt()}초 전", c.red, Color.White)
                        Pill("다시 시도", c.card, c.text, Modifier.clickable(role = Role.Button, onClick = onRetry))
                    }
                }
                if (demoOn) Pill("시험 자료(데모)", c.ask, c.askText)
                if (light != null) LightChips(light, now, c)
                n.speedLimit?.let { SpeedSign(it, c) }
                RoadSigns.nearest(n.signs)?.let { Pill("${it.label} ${distanceText(it.distanceM.toDouble())}", c.card, c.text) }
            }

            if (!reversing && n.turns.size > 1) {
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(14.dp).background(c.card, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    n.turns.drop(1).take(2).forEachIndexed { i, step ->
                        val color = if (i == 0) c.text else c.muted
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { contentDescription = turnName(step.type, null) }) {
                            Canvas(Modifier.size(22.dp)) { turnGlyph(step.type, color) }
                            Text(distanceText(step.distanceM), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                }
            }

            Column(Modifier.align(Alignment.TopStart).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!reversing) TurnChip(n, c, Modifier)
                // 글자를 줄이려고 알아 두기는 첫 줄만 보여 준다(두 줄 모두는 이전 화면에서 볼 수 있다)
                // 달리는 중에는 지도를 가리지 않게 알아 두기를 띄우지 않는다(멈췄을 때와 후진할 때만)
            val tipLines = tip?.lines?.take(1)?.takeIf { still || reversing }
                // 방금 한 조작과 알아 두기는 지도를 가리지 않게 아래 해설 칸에서 보여 준다
            }

            if (noSignal) {
                Column(
                    Modifier.fillMaxSize().background(c.bg.copy(alpha = 0.92f)),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                ) {
                    if (now - s.startedAtMs < 3_000L) {
                        Text("차량에 연결하는 중", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.muted)
                    } else {
                        Text("차량 신호 없음", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = c.muted)
                        s.error?.let { Text(it, fontSize = 14.sp, color = c.muted) }
                        Pill("다시 시도", c.accent, c.onAccent, Modifier.clickable(role = Role.Button, onClick = onRetry), big = true)
                    }
                }
            }
        }

        // 미리 가 보기 화면과 같은 배치: 지도 바로 아래에 해설 칸, 그 아래에 계기판
        // 글자가 길면 잘라 내지 않고 칸이 두 줄로 늘어난다(그만큼 지도가 조금 줄어든다)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.weight(1f).fillMaxHeight().heightIn(min = 72.dp).background(c.bar, RoundedCornerShape(20.dp)).padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // 해설이 없고 멈춰 있을 때는 같은 칸에서 알아 두기 한 줄을 보여 준다
                // 같은 말만 되풀이하지 않게 몇 초마다 다음 요령으로 넘긴다
                val idleTip = tip?.lines?.let { it.getOrNull(((now / 9_000) % it.size.coerceAtLeast(1)).toInt()) }?.takeIf { message == null && (still || reversing) }
                // 멈춰 있고 방금 해설이 지나갔으면, 같은 칸에서 방금 한 조작을 순서대로 되짚는다
                val recapText = recap.takeIf { it.size >= 2 && (message == null || messageOld) }?.mapIndexed { i, text -> "${i + 1}. ${text.removeSuffix(".")}" }?.joinToString("   ")
                if (recapText != null) {
                    Text("방금 한 조작", fontSize = 14.sp, color = c.barMuted)
                    Text(recapText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.barText, maxLines = 3, lineHeight = 22.sp)
                } else {
                Text(
                    message ?: idleTip ?: "운전자의 조작을 한 문장으로 알려 줘요",
                    fontSize = if ((message ?: idleTip ?: "").length > 30) 17.sp else 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 24.sp,
                    color = if (message == null && idleTip == null || messageOld) c.barMuted else c.barText,
                )
                if (message != null && why != null) Text(why, fontSize = 15.sp, color = c.barMuted, maxLines = 2, lineHeight = 20.sp)
                else if (idleTip != null && tip != null) Text("알아 두기 · ${tip.title}", fontSize = 15.sp, color = c.barMuted, maxLines = 1)
                }
            }
            Box(
                Modifier.size(64.dp).background(c.ask, RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onAsk).semantics { contentDescription = "나중에 물어볼 것으로 표시" },
                contentAlignment = Alignment.Center,
            ) {
                Text("?", fontSize = 30.sp, fontWeight = FontWeight.Black, color = c.askText)
                if (asked > 0) {
                    Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp).background(c.askText, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$asked", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.ask)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(142.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).fillMaxHeight().background(c.card, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 18.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
            ) {
                val blink = (now / 500) % 2 == 0L
                val both = s.turn != null && s.turn != TurnSignalValue.LEFT && s.turn != TurnSignalValue.RIGHT && s.turn != TurnSignalValue.OFF
                Blinker(left = true, on = s.turn == TurnSignalValue.LEFT || both, lit = blink, c = c)
                CockpitWheel(s.steeringDeg, if (compact) 100.dp else 126.dp, if (stale) c.offGlyph else c.text, c.accent)
                Blinker(left = false, on = s.turn == TurnSignalValue.RIGHT || both, lit = blink, c = c)
                if (!compact) AngleReadout(s.steeringDeg, c)
            }
            Column(
                Modifier.width(92.dp).fillMaxHeight().background(c.card, RoundedCornerShape(24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                val on = s.brakeOn == true
                Canvas(Modifier.size(56.dp, 68.dp).semantics { contentDescription = if (on) "브레이크 밟음" else "브레이크 뗌" }) { brakePedal(on, c) }
                Text("브레이크", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (on) c.redText else c.muted)
            }
            // 기어 칸을 5번 누르면 데모(시험 자료)가 켜지고 꺼진다
            Column(
                Modifier.width(60.dp).fillMaxHeight().background(c.card, RoundedCornerShape(24.dp)).clickable(onClick = onDemoTap),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            ) {
                listOf(VehicleGear.GEAR_PARK to "P", VehicleGear.GEAR_REVERSE to "R", VehicleGear.GEAR_NEUTRAL to "N", VehicleGear.GEAR_DRIVE to "D").forEach { (gear, label) ->
                    val active = s.gear == gear
                    Box(Modifier.size(32.dp).background(if (active) c.accent else Color.Transparent, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (active) c.onAccent else c.offGlyph)
                    }
                }
            }
            // 미리 가 보기와 설정은 출발 전에 쓰는 것이라 해설 칸 옆이 아니라 여기에 둔다(해설 칸을 넓게 쓰려고)
            Column(Modifier.width(60.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(c.accent, RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onPreview).semantics { contentDescription = "미리 가 보기" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(34.dp)) { previewGlyph(c.onAccent) }
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().background(c.card, RoundedCornerShape(22.dp)).clickable(role = Role.Button, onClick = onSettings).semantics { contentDescription = "설정" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(28.dp)) { slidersGlyph(c.text) }
                }
            }
        }
    }
    }
}

@Composable
internal fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, big: Boolean = false) {
    Text(
        text, fontSize = if (big) 20.sp else 14.sp, fontWeight = FontWeight.Bold, color = fg, maxLines = 1,
        modifier = modifier.background(bg, RoundedCornerShape(999.dp)).padding(horizontal = if (big) 22.dp else 12.dp, vertical = if (big) 10.dp else 6.dp),
    )
}

/** 다음 안내: 큰 화살표와 남은 거리. 안내가 없으면 그렇게 알려 준다 */
@Composable
private fun TurnChip(n: NaviSnapshot, c: CockpitColors, modifier: Modifier) {
    val turn = n.turn
    if (turn == null) {
        val text = if (!n.ready && n.error != null) "내비 정보를 받을 수 없어요" else "내비 안내 없음"
        Pill(text, c.card, c.muted, modifier)
        return
    }
    Row(
        modifier.widthIn(max = 360.dp).background(c.chip, RoundedCornerShape(20.dp)).padding(start = 12.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(44.dp)) { turnGlyph(turn, c.chipText) }
        Column {
            Text(n.turnDistanceM?.let { distanceText(it) } ?: "", fontSize = 30.sp, fontWeight = FontWeight.Black, color = c.chipText)
            Text(turnName(turn, n.turnText), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.chipMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun LightChips(light: LightInfo, now: Long, c: CockpitColors) {
    Row(
        Modifier.background(c.card, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = "신호등 참고용, ${light.name} ${light.distanceM}미터" },
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        LightPart(TurnType.TURN_STRAIGHT, light.straight, light.atMs, now, c)
        LightPart(TurnType.TURN_LEFT, light.left, light.atMs, now, c)
        // 제공 기관 고지: 실제 신호와 다를 수 있어 참고용이라고 화면에 밝힌다
        Text("참고", fontSize = 11.sp, color = c.muted)
    }
}

@Composable
private fun LightPart(direction: TurnType, phase: LightPhase?, atMs: Long, now: Long, c: CockpitColors) {
    if (phase == null) return
    val dot = when (phase.color) {
        LightColor.RED -> c.red
        LightColor.YELLOW -> Color(0xFFF59E0B)
        LightColor.GREEN -> c.green
        LightColor.UNKNOWN -> c.muted
    }
    val seconds = TrafficLights.remainSeconds(phase, atMs, now)
    Row(
        Modifier.semantics { contentDescription = TrafficLights.colorName(phase.color) + (seconds?.let { " ${it}초" } ?: "") },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(Modifier.size(18.dp)) { turnGlyph(direction, c.muted) }
        // 신호등 모양: 세 칸 중 지금 켜진 칸만 색이 들어온다(위 빨강, 가운데 노랑, 아래 초록). 색만이 아니라 자리로도 구분된다
        Canvas(Modifier.size(18.dp, 46.dp)) {
            drawRoundRect(Color(0xFF17162B), cornerRadius = CornerRadius(size.width / 2))
            listOf(LightColor.RED, LightColor.YELLOW, LightColor.GREEN).forEachIndexed { i, lamp ->
                drawCircle(if (phase.color == lamp) dot else Color(0xFF4B4A63), radius = size.width * 0.33f, center = Offset(size.width / 2, size.height * (0.19f + 0.31f * i)))
            }
        }
        Text(seconds?.toString() ?: "-", fontSize = 22.sp, fontWeight = FontWeight.Black, color = c.text)
    }
}

@Composable
private fun SpeedSign(limit: Int, c: CockpitColors) {
    Box(Modifier.size(46.dp).background(c.red, CircleShape).padding(5.dp).background(Color.White, CircleShape).semantics { contentDescription = "제한속도 $limit" }, contentAlignment = Alignment.Center) {
        Text("$limit", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Color(0xFF17162B))
    }
}

@Composable
internal fun Blinker(left: Boolean, on: Boolean, lit: Boolean, c: CockpitColors) {
    val bright = on && lit
    Canvas(Modifier.size(54.dp).semantics { contentDescription = (if (left) "왼쪽" else "오른쪽") + " 방향지시등 " + if (on) "켜짐" else "꺼짐" }) {
        drawCircle(if (on) c.greenBg else c.off)
        val k = size.width / 54f
        val arrow = Path().apply {
            moveTo(30f * k, 14f * k); lineTo(16f * k, 27f * k); lineTo(30f * k, 40f * k)
            lineTo(30f * k, 32f * k); lineTo(40f * k, 32f * k); lineTo(40f * k, 22f * k); lineTo(30f * k, 22f * k); close()
        }
        scale(scaleX = if (left) 1f else -1f, scaleY = 1f) {
            drawPath(arrow, if (bright) c.green else if (on) c.green.copy(alpha = 0.35f) else c.offGlyph)
        }
    }
}

/** 핸들. SDK 값은 양수 = 왼쪽(반시계)이고 화면 회전은 시계 방향이 양수라서 부호를 뒤집는다 */
@Composable
internal fun CockpitWheel(angle: Float?, wheelSize: Dp, color: Color, accent: Color) {
    Canvas(Modifier.size(wheelSize).semantics { contentDescription = "핸들 " + angleLabel(angle) }) {
        val k = size.minDimension / 132f
        rotate(-(angle ?: 0f)) {
            drawCircle(color, radius = 56f * k, style = Stroke(width = 15f * k))
            val spokes = Path().apply {
                moveTo(14f * k, 62f * k); quadraticBezierTo(66f * k, 46f * k, 118f * k, 62f * k); lineTo(118f * k, 74f * k)
                quadraticBezierTo(94f * k, 70f * k, 82f * k, 82f * k); quadraticBezierTo(72f * k, 92f * k, 72f * k, 120f * k); lineTo(60f * k, 120f * k)
                quadraticBezierTo(60f * k, 92f * k, 50f * k, 82f * k); quadraticBezierTo(38f * k, 70f * k, 14f * k, 74f * k); close()
            }
            drawPath(spokes, color)
            drawCircle(accent, radius = 13f * k, center = Offset(66f * k, 70f * k))
            drawRoundRect(accent, topLeft = Offset(62f * k, 5f * k), size = Size(8f * k, 13f * k), cornerRadius = CornerRadius(3f * k))
        }
    }
}

@Composable
private fun AngleReadout(angle: Float?, c: CockpitColors) {
    val deg = angle?.let { abs(it).roundToInt() }
    val halfTurns = if (angle == null) 0 else (abs(angle) / CoachEngine.HALF_TURN_DEG).toInt()
    val side = when {
        angle == null || deg == 0 -> "가운데"
        angle > 0f -> "왼쪽"
        else -> "오른쪽"
    }
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(if (deg == null) "-" else "$deg°", fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.text, maxLines = 1)
        // 주차처럼 크게 돌릴 때는 각도 대신 바퀴 수가 감이 온다
        Text(if (halfTurns >= 1) "$side ${CoachEngine.turnsLabel(halfTurns)}" else side, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.muted, maxLines = 2)
    }
}

private fun DrawScope.brakePedal(on: Boolean, c: CockpitColors) {
    val k = size.width / 56f
    drawRoundRect(if (on) c.red else c.off, topLeft = Offset(6f * k, 6f * k), size = Size(44f * k, 56f * k), cornerRadius = CornerRadius(12f * k))
    for (y in listOf(20f, 30f, 40f, 50f)) {
        drawLine(if (on) Color.White else c.offGlyph, Offset(16f * k, y * k), Offset(40f * k, y * k), strokeWidth = 4f * k, cap = StrokeCap.Round)
    }
}

/** 출발점에서 굽은 길을 따라 목적지 핀까지 */
internal fun DrawScope.previewGlyph(color: Color) {
    val k = size.minDimension / 34f
    drawCircle(color, radius = 4f * k, center = Offset(7f * k, 27f * k))
    drawPath(
        Path().apply { moveTo(7f * k, 27f * k); cubicTo(24f * k, 27f * k, 4f * k, 14f * k, 19f * k, 14f * k) },
        color, style = Stroke(width = 3.5f * k, cap = StrokeCap.Round),
    )
    drawCircle(color, radius = 5.5f * k, center = Offset(26f * k, 10f * k), style = Stroke(width = 3.5f * k))
    drawLine(color, Offset(26f * k, 16f * k), Offset(26f * k, 21f * k), strokeWidth = 3.5f * k, cap = StrokeCap.Round)
}

internal fun DrawScope.slidersGlyph(color: Color) {
    val w = size.width
    for ((i, knob) in listOf(0.3f, 0.7f, 0.45f).withIndex()) {
        val y = w * (0.22f + 0.28f * i)
        drawLine(color, Offset(w * 0.08f, y), Offset(w * 0.92f, y), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        drawCircle(color, radius = w * 0.13f, center = Offset(w * knob, y))
    }
}

internal enum class Glyph { LEFT, RIGHT, STRAIGHT, UTURN, OVER, UNDER, ROUNDABOUT, ARRIVE, MERGE, TOLL, BUSY, CAMERA, SCHOOL }

private fun glyphOf(type: TurnType): Glyph {
    val name = type.name
    return when {
        "UTURN" in name -> Glyph.UTURN
        "ROUNDABOUT" in name -> Glyph.ROUNDABOUT
        "ENTER_OVER" in name -> Glyph.OVER
        "ENTER_UNDER" in name -> Glyph.UNDER
        "ARRIVE" in name -> Glyph.ARRIVE
        name.endsWith("_L") || name == "TURN_LEFT" -> Glyph.LEFT
        name.endsWith("_R") || name == "TURN_RIGHT" -> Glyph.RIGHT
        else -> Glyph.STRAIGHT
    }
}

/** 안내 화살표. 44 기준 좌표로 그리고 칸 크기에 맞춰 키운다 */
private fun DrawScope.turnGlyph(type: TurnType, color: Color) = glyph(glyphOf(type), color)

internal fun DrawScope.glyph(g: Glyph, color: Color) {
    val k = size.minDimension / 44f
    val stroke = Stroke(width = 5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(build: Path.() -> Unit) = drawPath(Path().apply(build), color, style = stroke)
    when (g) {
        Glyph.LEFT, Glyph.RIGHT -> scale(scaleX = if (g == Glyph.RIGHT) 1f else -1f, scaleY = 1f) {
            path { moveTo(14f * k, 36f * k); lineTo(14f * k, 22f * k); quadraticBezierTo(14f * k, 16f * k, 20f * k, 16f * k); lineTo(32f * k, 16f * k) }
            path { moveTo(26f * k, 9f * k); lineTo(33f * k, 16f * k); lineTo(26f * k, 23f * k) }
        }
        Glyph.STRAIGHT -> {
            path { moveTo(22f * k, 37f * k); lineTo(22f * k, 10f * k) }
            path { moveTo(14f * k, 18f * k); lineTo(22f * k, 9f * k); lineTo(30f * k, 18f * k) }
        }
        Glyph.UTURN -> {
            path { moveTo(30f * k, 37f * k); lineTo(30f * k, 20f * k); quadraticBezierTo(30f * k, 11f * k, 22f * k, 11f * k); quadraticBezierTo(14f * k, 11f * k, 14f * k, 20f * k); lineTo(14f * k, 30f * k) }
            path { moveTo(7f * k, 24f * k); lineTo(14f * k, 31f * k); lineTo(21f * k, 24f * k) }
        }
        Glyph.OVER -> {
            path { moveTo(6f * k, 30f * k); quadraticBezierTo(22f * k, 6f * k, 38f * k, 30f * k) }
            path { moveTo(6f * k, 36f * k); lineTo(38f * k, 36f * k) }
        }
        Glyph.UNDER -> {
            path { moveTo(6f * k, 16f * k); quadraticBezierTo(22f * k, 40f * k, 38f * k, 16f * k) }
            path { moveTo(6f * k, 10f * k); lineTo(38f * k, 10f * k) }
        }
        Glyph.ROUNDABOUT -> {
            drawCircle(color, radius = 9f * k, center = Offset(22f * k, 26f * k), style = stroke)
            path { moveTo(22f * k, 17f * k); lineTo(22f * k, 6f * k) }
        }
        Glyph.ARRIVE -> {
            drawCircle(color, radius = 11f * k, center = Offset(22f * k, 20f * k), style = stroke)
            drawCircle(color, radius = 4f * k, center = Offset(22f * k, 20f * k))
            path { moveTo(22f * k, 31f * k); lineTo(22f * k, 38f * k) }
        }
        Glyph.MERGE -> {
            path { moveTo(26f * k, 38f * k); lineTo(26f * k, 8f * k) }
            path { moveTo(10f * k, 38f * k); quadraticBezierTo(10f * k, 24f * k, 26f * k, 18f * k) }
            path { moveTo(19f * k, 14f * k); lineTo(26f * k, 7f * k); lineTo(33f * k, 14f * k) }
        }
        Glyph.TOLL -> {
            path { moveTo(10f * k, 38f * k); lineTo(10f * k, 14f * k) }
            path { moveTo(10f * k, 16f * k); lineTo(36f * k, 10f * k) }
            path { moveTo(34f * k, 38f * k); lineTo(34f * k, 26f * k) }
        }
        Glyph.CAMERA -> {
            drawRoundRect(color, topLeft = Offset(7f * k, 14f * k), size = Size(22f * k, 16f * k), cornerRadius = CornerRadius(4f * k), style = stroke)
            path { moveTo(29f * k, 20f * k); lineTo(37f * k, 15f * k); lineTo(37f * k, 29f * k); lineTo(29f * k, 24f * k) }
        }
        Glyph.SCHOOL -> {
            path { moveTo(8f * k, 22f * k); lineTo(22f * k, 9f * k); lineTo(36f * k, 22f * k) }
            path { moveTo(12f * k, 20f * k); lineTo(12f * k, 36f * k); lineTo(32f * k, 36f * k); lineTo(32f * k, 20f * k) }
        }
        // 차 세 대가 줄지어 선 모양
        Glyph.BUSY -> for (y in listOf(6f, 18f, 30f)) {
            drawRoundRect(color, topLeft = Offset(15f * k, y * k), size = Size(14f * k, 9f * k), cornerRadius = CornerRadius(3f * k))
        }
    }
}

/** 안내 종류를 필기시험과 내비에서 쓰는 말로 */
internal fun turnName(type: TurnType, description: String?): String {
    val name = type.name
    val base = when {
        "UTURN" in name -> "유턴"
        "ROUNDABOUT" in name -> "회전교차로"
        "ENTER_OVER" in name -> "고가도로 진입"
        "AVOID_OVER" in name -> "고가도로 옆길"
        "ENTER_UNDER" in name -> "지하차도 진입"
        "AVOID_UNDER" in name -> "지하차도 옆길"
        "ENTER_IC" in name -> "나들목 진입"
        "EXIT_IC" in name -> "나들목 진출"
        "TUNNEL" in name -> "터널"
        "TOLL" in name -> "톨게이트"
        "SERVICEAREA" in name || "RESTAREA" in name -> "휴게소"
        "ARRIVE" in name -> "목적지 도착"
        "SHARP_L" in name -> "크게 좌회전"
        "SHARP_R" in name -> "크게 우회전"
        "KEEP_L" in name -> "왼쪽 방향"
        "KEEP_R" in name -> "오른쪽 방향"
        name == "TURN_LEFT" -> "좌회전"
        name == "TURN_RIGHT" -> "우회전"
        name == "TURN_STRAIGHT" -> "직진"
        else -> "안내"
    }
    val extra = description?.takeIf { it.isNotBlank() && it != base }
    return if (extra == null) base else "$base · $extra"
}

/** 남은 거리: 1km부터는 km, 그 아래는 10m 단위로 */
internal fun distanceText(meters: Double): String = when {
    meters >= 1000 -> "%.1fkm".format(meters / 1000)
    meters >= 30 -> "${(meters / 10).roundToInt() * 10}m"
    else -> "${meters.roundToInt()}m"
}
