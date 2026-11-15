package app.passengercoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.MapData
import app.passengercoach.coach.Preview
import app.passengercoach.coach.PreviewRoute
import app.passengercoach.coach.Road
import app.passengercoach.coach.RouteMath
import app.passengercoach.coach.Spot
import app.passengercoach.coach.SpotKind
import app.passengercoach.demo.DemoRoute
import app.passengercoach.dlog
import app.passengercoach.signal.RoadHub
import app.passengercoach.signal.RouteHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 미리 가 보기: 출발하기 전에 오늘 갈 길을 주행 화면과 같은 모습으로 빠르게 따라가 본다.
 * 회전교차로처럼 미리 알아 둘 곳에서는 느려지면서 그곳의 요령과, 그때 하게 될 조작(깜빡이, 핸들)의 예상을 보여 준다.
 * 길은 내비에 정한 목적지까지 따로 계산한 것이라 운전자 내비의 길과 다를 수 있다. 다른 차의 움직임은 알 수 없어 보여 주지 않는다.
 * 목적지가 없거나 길을 받지 못하면 앱에 넣어 둔 연습 코스(시험 자료)를 보여 준다.
 */
@Composable
internal fun PreviewScreen(here: LatLon?, dest: LatLon?, routes: RouteHub, roads: RoadHub, onClose: () -> Unit) {
    val c = if (isSystemInDarkTheme()) Night else Day
    val context = androidx.compose.ui.platform.LocalContext.current
    var plan by remember { mutableStateOf<PreviewRoute?>(null) }
    var map by remember { mutableStateOf<MapData?>(null) }
    var why by remember { mutableStateOf<String?>(null) }
    var atM by remember { mutableDoubleStateOf(0.0) }
    var playing by remember { mutableStateOf(true) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        val fetched = if (routes.hasKey && here != null && dest != null) {
            withContext(Dispatchers.IO) { runCatching { routes.preview(here, dest) }.onFailure { dlog("Preview") { "PREVIEW_FAILED ${it.message}" } }.getOrNull() }
        } else null
        fun asset(name: String) = context.assets.open(name).bufferedReader().use { it.readText() }
        // 길을 받지 못하면 앱에 넣어 둔 연습 코스(약 5km, 시험 자료)를 쓴다. 인증키가 없는 제출용 빌드는 항상 이쪽이다
        val p = fetched?.takeIf { it.points.size >= 2 }
            ?: withContext(Dispatchers.IO) { runCatching { RouteHub.parseSample(asset("preview_sample.json")) }.getOrNull() }
            ?: Preview.fromShape(DemoRoute.points)
        why = when {
            !p.sample || !routes.hasKey -> null
            dest == null -> "내비에 목적지를 정하면 그 길을 보여 줘요"
            else -> "길을 받지 못했어요"
        }
        plan = p
        dlog("Preview") { "PREVIEW sample=${p.sample} lengthM=${p.lengthM.toInt()} spots=${p.spots.map { it.kind }}" }
        // 주변 지형(건물, 녹지, 물, 이름, 길 위의 시설)은 조금 늦게 와도 된다. 먼저 길만으로 시작한다
        val m = withContext(Dispatchers.IO) {
            runCatching { if (p.sample) RoadHub.parseMap(asset("preview_map.json")) else roads.corridor(p.points) }
                .onFailure { dlog("Preview") { "PREVIEW_MAP_FAILED ${it.message}" } }.getOrNull()
        } ?: return@LaunchedEffect
        map = m
        plan = Preview.withMarks(p, m.marks)
        dlog("Preview") { "PREVIEW_MAP roads=${m.roads.size} areas=${m.areas.size} labels=${m.labels.size} marks=${m.marks.size} spots=${plan?.spots?.size}" }
    }
    LaunchedEffect(Unit) {
        var frames = 0
        var since = System.currentTimeMillis()
        while (true) {
            delay(TICK_MS)
            // 화면을 그리는 데 시간이 걸려도 속도가 느려지지 않게, 실제로 지난 시간만큼 나아간다
            val t = System.currentTimeMillis()
            val passed = (t - now).coerceIn(0L, 250L)
            now = t
            if (++frames == 100) {
                dlog("Preview") { "PREVIEW_FPS ${100_000 / (t - since).coerceAtLeast(1)}" }
                frames = 0
                since = t
            }
            val p = plan ?: continue
            if (playing) {
                atM += Preview.speed(atM, p.spots) * passed / 1000.0
                if (atM >= p.lengthM) {
                    atM = p.lengthM
                    playing = false
                }
            }
        }
    }

    val p = plan
    Column(Modifier.fillMaxSize().background(c.bg).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val current = p?.let { Preview.current(atM, it.spots) }
        val next = p?.let { Preview.next(atM, it.spots) }
        val done = p != null && atM >= p.lengthM

        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.mapBase)) {
            if (p == null) {
                Text("길을 찾는 중", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.align(Alignment.Center))
            } else {
                val car = RouteMath.along(p.points, atM).first
                // 꺾이는 곳에서 지도가 한 번에 돌지 않게, 조금 뒤와 조금 앞을 이은 방향을 쓴다
                val heading = RouteMath.bearing(RouteMath.along(p.points, atM - 10.0).first, RouteMath.along(p.points, atM + 20.0).first)
                MapPanel(car.lat, car.lon, heading, emptyList(), p.points, c, Modifier.fillMaxSize(), map = map ?: MapData())
                Box(Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(listOf(c.bg.copy(alpha = 0.75f), Color.Transparent))))
                Text("© OpenStreetMap contributors", fontSize = 10.sp, color = c.muted, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp))

                Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    why?.let { Pill(it, c.card, c.muted) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill("${distanceText(p.lengthM)} · ${p.minutes}분", c.card, c.text)
                        if (p.sample) Pill("연습 코스(시험 자료)", c.ask, c.askText)
                    }
                }
            }
        }

        // 안내 칸은 지도를 가리지 않게 지도 바로 아래에 둔다(눈이 지도에서 조금만 내려오면 된다)
        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val tip = when {
                p == null -> null
                current != null -> Preview.tip(p, current)
                done -> "도착했어요. 짚어 본 곳은 ${p.spots.size}곳이에요" to ""
                else -> null
            }
            SpotBanner(current ?: next, atM, tip, c, Modifier.weight(1f).fillMaxHeight())
            if (current?.kind == SpotKind.BUSY) {
                Canvas(
                    Modifier.size(76.dp, 64.dp).background(c.card, RoundedCornerShape(20.dp))
                        .semantics { contentDescription = "차로 변경 그림: 깜빡이를 켜고 옆 차로의 빈자리로 천천히 옮긴다" },
                ) { laneChange(now, c) }
            }
        }

        Row(Modifier.fillMaxWidth().height(126.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // 그곳에서 하게 될 조작의 예상(실제 조작이 아니다)
            val (blinker, wheel) = if (p == null) null to 0f else Preview.expected(atM, p)
            Box(Modifier.weight(1f).fillMaxHeight().background(c.card, RoundedCornerShape(24.dp))) {
                Text("예상 조작", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    val lit = (now / 500) % 2 == 0L
                    Blinker(left = true, on = blinker == true, lit = lit, c = c)
                    // 길 모양으로 어림한 값이라 꺾이는 점에서 한 번에 바뀐다. 실제 핸들처럼 부드럽게 돌려 보여 준다
                    val shown by androidx.compose.animation.core.animateFloatAsState(wheel, androidx.compose.animation.core.tween(700), label = "wheel")
                    CockpitWheel(shown, 88.dp, c.text, c.accent)
                    Blinker(left = false, on = blinker == false, lit = lit, c = c)
                }
            }
            // 오늘 길에서 짚어 줄 곳들. 누르면 그 앞으로 건너뛴다
            Box(Modifier.weight(1.15f).fillMaxHeight().background(c.card, RoundedCornerShape(24.dp))) {
                Text(if (p == null) "" else "짚어 볼 곳 ${p.spots.size}", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                if (p != null) {
                    Canvas(
                        Modifier.fillMaxSize().padding(horizontal = 22.dp).semantics { contentDescription = "경로에서 짚어 볼 곳 ${p.spots.size}곳. 누르면 그곳 앞으로 건너뜁니다" }
                            .pointerInput(p) {
                                detectTapGestures { tap ->
                                    val f = (tap.x / size.width).coerceIn(0f, 1f) * p.lengthM
                                    p.spots.minByOrNull { abs(it.atM - f) }?.let {
                                        atM = (it.atM - Preview.BEFORE_M).coerceAtLeast(0.0)
                                        playing = true
                                    }
                                }
                            },
                    ) { timeline(p, atM, current, c) }
                }
            }
            // 멈춤과 닫기는 오른쪽 끝에 세로로 둔다(아래 줄을 없애 지도를 키웠다)
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.size(58.dp).background(c.accent, RoundedCornerShape(20.dp)).semantics { contentDescription = if (done) "처음부터 다시" else if (playing) "멈춤" else "계속" }
                    .clickable(role = Role.Button) {
                        if (done) atM = 0.0
                        playing = done || !playing
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(26.dp)) {
                    if (playing) {
                        drawRoundRect(c.onAccent, topLeft = Offset(size.width * 0.14f, 0f), size = Size(size.width * 0.26f, size.height), cornerRadius = CornerRadius(4f))
                        drawRoundRect(c.onAccent, topLeft = Offset(size.width * 0.6f, 0f), size = Size(size.width * 0.26f, size.height), cornerRadius = CornerRadius(4f))
                    } else {
                        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width * 0.95f, size.height / 2); lineTo(size.width * 0.2f, size.height); close() }, c.onAccent)
                    }
                }
            }
            Box(
                Modifier.size(58.dp).background(c.card, RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClose).semantics { contentDescription = "닫기" },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(22.dp)) {
                    drawLine(c.text, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawLine(c.text, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 5f, cap = StrokeCap.Round)
                }
            }
            }
        }
    }
}

private const val TICK_MS = 40L

private fun glyphOf(kind: SpotKind): Glyph = when (kind) {
    SpotKind.LEFT -> Glyph.LEFT
    SpotKind.RIGHT -> Glyph.RIGHT
    SpotKind.UTURN -> Glyph.UTURN
    SpotKind.ROUNDABOUT -> Glyph.ROUNDABOUT
    SpotKind.OVERPASS -> Glyph.OVER
    SpotKind.UNDERPASS -> Glyph.UNDER
    SpotKind.HIGHWAY -> Glyph.MERGE
    SpotKind.TOLL -> Glyph.TOLL
    SpotKind.BUSY -> Glyph.BUSY
    SpotKind.BUMP -> Glyph.OVER
    SpotKind.CAMERA -> Glyph.CAMERA
    SpotKind.SCHOOL -> Glyph.SCHOOL
    SpotKind.ARRIVE -> Glyph.ARRIVE
}

/**
 * 지도 위의 안내 칸: 다음에(또는 지금) 짚어 줄 곳의 그림, 남은 거리, 이름. 그곳에 다가가면 같은 칸이 넓어지며 요령이 붙는다.
 * 요령을 화면 맨 아래에 따로 두면 눈이 지도와 아래를 오가야 해서, 한 칸에 모았다.
 */
@Composable
private fun SpotBanner(spot: Spot?, atM: Double, tip: Pair<String, String>?, c: CockpitColors, modifier: Modifier) {
    Row(
        modifier.background(c.chip, RoundedCornerShape(20.dp)).padding(start = 12.dp, end = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (spot != null) {
            val left = spot.atM - atM
            Canvas(Modifier.size(44.dp)) { glyph(glyphOf(spot.kind), c.chipText) }
            Column {
                Text(if (left > 5) distanceText(left) else "여기", fontSize = 28.sp, fontWeight = FontWeight.Black, color = c.chipText, maxLines = 1)
                val title = Preview.title(spot.kind)
                Text(if (spot.name.isBlank() || spot.name == title) title else "$title · ${spot.name}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.chipMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp))
            }
        }
        if (tip != null) {
            if (spot != null) Box(Modifier.size(1.dp, 44.dp).background(c.chipMuted.copy(alpha = 0.4f)))
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(tip.first, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.chipText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (tip.second.isNotEmpty()) Text(tip.second, fontSize = 13.sp, color = c.chipMuted, maxLines = 2, lineHeight = 17.sp)
            }
        }
    }
}

/** 경로를 한 줄로 펴고 짚어 줄 곳을 그림으로 찍는다. 지나온 곳은 강조색, 지금 있는 곳은 노란색 */
private fun DrawScope.timeline(p: PreviewRoute, atM: Double, current: Spot?, c: CockpitColors) {
    val y = size.height * 0.58f
    val r = 15.dp.toPx()
    val x = { m: Double -> (m / p.lengthM.coerceAtLeast(1.0)).toFloat() * size.width }
    drawLine(c.off, Offset(0f, y), Offset(size.width, y), strokeWidth = 8f, cap = StrokeCap.Round)
    drawLine(c.accent, Offset(0f, y), Offset(x(atM), y), strokeWidth = 8f, cap = StrokeCap.Round)
    p.spots.forEachIndexed { i, spot ->
        // 가까이 붙은 곳끼리 겹치지 않게 위아래로 번갈아 둔다
        val cy = y + (if (i % 2 == 0) -r * 1.25f else r * 1.25f)
        val cx = x(spot.atM)
        val (bg, fg) = when {
            spot == current -> c.ask to c.askText
            spot.atM <= atM -> c.accent to c.onAccent
            else -> c.off to c.muted
        }
        drawLine(bg, Offset(cx, y), Offset(cx, cy), strokeWidth = 3f)
        drawCircle(bg, radius = r, center = Offset(cx, cy))
        val pad = r * 0.62f
        inset(left = cx - pad, top = cy - pad, right = size.width - cx - pad, bottom = size.height - cy - pad) { glyph(glyphOf(spot.kind), fg) }
    }
    drawCircle(c.card, radius = 9f, center = Offset(x(atM), y))
    drawCircle(c.accent, radius = 6f, center = Offset(x(atM), y))
}

/** 차로 변경 그림: 깜빡이를 켜고(노란 점), 옆 차로의 빈자리로 천천히 옮긴다. 실제 주변 차가 아니라 설명용 그림이다 */
private fun DrawScope.laneChange(now: Long, c: CockpitColors) {
    val w = size.width
    val h = size.height
    val phase = (now % 3600L) / 3600f
    val laneL = w * 0.34f
    val laneR = w * 0.66f
    val car = Size(w * 0.17f, h * 0.22f)
    drawLine(c.offGlyph, Offset(w / 2, h * 0.08f), Offset(w / 2, h * 0.92f), strokeWidth = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
    // 옆 차로의 앞차와 뒤차 사이에 빈자리가 있다
    for (top in listOf(0.10f, 0.70f)) drawRoundRect(c.offGlyph, topLeft = Offset(laneR - car.width / 2, h * top), size = car, cornerRadius = CornerRadius(6f))
    val move = ((phase - 0.35f) / 0.4f).coerceIn(0f, 1f)
    val x = laneL + (laneR - laneL) * move
    val y = h * (0.62f - 0.22f * move)
    drawRoundRect(c.accent, topLeft = Offset(x - car.width / 2, y), size = car, cornerRadius = CornerRadius(6f))
    if (phase < 0.75f && (now / 300) % 2 == 0L) drawCircle(c.ask, radius = 5f, center = Offset(x + car.width / 2 + 2f, y + 4f))
}
