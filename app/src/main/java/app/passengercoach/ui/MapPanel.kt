package app.passengercoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import app.passengercoach.coach.Area
import app.passengercoach.coach.AreaKind
import app.passengercoach.coach.MapData
import app.passengercoach.coach.MapLabel
import app.passengercoach.coach.Mark
import app.passengercoach.coach.MarkKind
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.MapMath
import app.passengercoach.coach.Road

/**
 * 앱이 직접 그리는 지도: 진행 방향이 위로 가고, 앞유리 너머로 보듯 먼 곳이 작아진다.
 * 길 모양은 OpenStreetMap 자료이고(RoadHub), 경로 선은 운전자 내비의 것이 아니라 따로 계산한 것이다(RouteHub).
 * 지도 그림을 쓰지 않고 길만 그리므로 돌려도 글자가 뒤집히지 않고, 낮과 밤 색을 앱에 맞출 수 있다.
 */
@Composable
internal fun MapPanel(lat: Double?, lon: Double?, heading: Double?, roads: List<Road>, route: List<LatLon>, c: CockpitColors, modifier: Modifier, map: MapData? = null) {
    if (lat == null || lon == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("위치를 받는 중", fontSize = 18.sp, color = c.muted) }
        return
    }
    val car = LatLon(lat, lon)
    val h = heading ?: 0.0
    Canvas(modifier.semantics { contentDescription = "진행 방향이 위로 가는 지도와 경로 선" }) {
        val dark = c.bg.luminance() < 0.5f
        // 선과 면을 하나씩 따로 그리면 수천 번이 되어 화면이 끊긴다(에뮬레이터에서 초당 5장). 종류별로 한 모양에 모아 한 번에 칠한다
        if (map != null) {
            // 주변 지형: 녹지와 물을 먼저 칠하고 그 위에 건물을 올린다
            val green = if (dark) Color(0xFF15301F) else Color(0xFFCFE9D2)
            val water = if (dark) Color(0xFF14283C) else Color(0xFFBEDBF3)
            val building = if (dark) Color(0xFF232333) else Color(0xFFD5D6E4)
            for (kind in listOf(AreaKind.GREEN, AreaKind.WATER, AreaKind.BUILDING)) {
                val shape = Path()
                map.areas.forEach { if (it.kind == kind) area(shape, car, h, it) }
                if (kind == AreaKind.WATER) map.waters.forEach { band(shape, car, h, it, 14.0, 2f) }
                drawPath(shape, when (kind) { AreaKind.GREEN -> green; AreaKind.WATER -> water; AreaKind.BUILDING -> building })
            }
        }
        // 작은 길부터 그려서 큰길이 위에 오게 한다
        for (level in 3 downTo 0) {
            val shape = Path()
            (map?.roads ?: roads).forEach { if (it.level == level) band(shape, car, h, it.points, MapMath.ROAD_WIDTH_M[level], 1.5f) }
            drawPath(shape, if (level == 3) c.card.copy(alpha = 0.55f) else c.card)
        }
        way(car, h, route, 6.0, c.accent, 6f)
        if (map != null) {
            map.marks.forEach { mark(car, h, it, c) }
            labels(car, h, map.labels, c, dark)
        }

        // 내 차: 항상 같은 자리에 있고 지도가 그 아래에서 움직인다
        val at = Offset(size.width / 2, size.height * MapMath.CAR_AT.toFloat())
        val r = size.minDimension * 0.058f
        drawCircle(c.card, radius = r, center = at)
        drawCircle(c.accent, radius = r * 0.72f, center = at)
        drawPath(
            Path().apply {
                moveTo(at.x, at.y - r * 0.55f); lineTo(at.x + r * 0.36f, at.y + r * 0.36f)
                lineTo(at.x, at.y + r * 0.16f); lineTo(at.x - r * 0.36f, at.y + r * 0.36f); close()
            },
            Color.White,
        )
    }
}

/** 차 기준으로 화면에 들어올 만한 곳인지(오른쪽, 앞쪽 미터) */
private fun near(p: Pair<Double, Double>) = kotlin.math.abs(p.first) < 520 && p.second > -100 && p.second < 800

/** 건물, 녹지, 물을 원근에 맞춰 칠한다. 눈 뒤로 넘어가는 꼭짓점이 있으면 그리지 않는다 */
private fun DrawScope.area(shape: Path, car: LatLon, heading: Double, a: Area) {
    val at = MapMath.local(car, heading, a.center)
    // 건물은 멀면 점처럼 작아져서 가까운 것만 그린다
    if (!near(at) || (a.kind == AreaKind.BUILDING && (at.second > 420 || kotlin.math.abs(at.first) > 330))) return
    val xy = FloatArray(a.points.size * 2)
    a.points.forEachIndexed { i, p ->
        val l = MapMath.local(car, heading, p)
        val s = MapMath.project(l.first, l.second, size.width.toDouble(), size.height.toDouble()) ?: return
        xy[i * 2] = s[0].toFloat()
        xy[i * 2 + 1] = s[1].toFloat()
    }
    shape.moveTo(xy[0], xy[1])
    for (i in 1 until a.points.size) shape.lineTo(xy[i * 2], xy[i * 2 + 1])
    shape.close()
}

/** 길 한 줄을 원근에 맞는 폭의 띠로 만들어 [shape]에 더한다. 꺾이는 곳은 동그라미로 메운다 */
private fun DrawScope.band(shape: Path, car: LatLon, heading: Double, points: List<LatLon>, widthM: Double, minPx: Float) {
    if (points.size < 2) return
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    var prev = MapMath.local(car, heading, points[0])
    for (i in 1 until points.size) {
        val next = MapMath.local(car, heading, points[i])
        if (maxOf(kotlin.math.abs(prev.first), kotlin.math.abs(next.first)) < 700 && maxOf(prev.second, next.second) > -MapMath.EYE_BACK_M && minOf(prev.second, next.second) < 900) {
            val pieces = MapMath.pieces(prev, next, 60.0)
            for (j in 0 until pieces.size - 1) {
                val a = MapMath.project(pieces[j].first, pieces[j].second, w, h) ?: continue
                val b = MapMath.project(pieces[j + 1].first, pieces[j + 1].second, w, h) ?: continue
                val ax = a[0].toFloat(); val ay = a[1].toFloat(); val bx = b[0].toFloat(); val by = b[1].toFloat()
                val len = kotlin.math.hypot(bx - ax, by - ay)
                if (len < 0.5f) continue
                val wa = (widthM * a[2]).toFloat().coerceAtLeast(minPx) / 2
                val wb = (widthM * b[2]).toFloat().coerceAtLeast(minPx) / 2
                val nx = -(by - ay) / len
                val ny = (bx - ax) / len
                shape.moveTo(ax + nx * wa, ay + ny * wa); shape.lineTo(bx + nx * wb, by + ny * wb)
                shape.lineTo(bx - nx * wb, by - ny * wb); shape.lineTo(ax - nx * wa, ay - ny * wa); shape.close()
                // 가는 길은 이음매가 안 보여서 동그라미를 생략한다
                if (j == 0 && wa > 2.5f) shape.addOval(androidx.compose.ui.geometry.Rect(ax - wa, ay - wa, ax + wa, ay + wa))
            }
        }
        prev = next
    }
}

/** 길 위의 시설을 작은 표지로 그린다: 신호등(세 점), 단속 카메라(빨간 고리), 과속방지턱과 학교(노란 표지) */
private fun DrawScope.mark(car: LatLon, heading: Double, m: Mark, c: CockpitColors) {
    val l = MapMath.local(car, heading, m.at)
    if (!near(l) || l.second > 450) return
    val s = MapMath.project(l.first, l.second, size.width.toDouble(), size.height.toDouble()) ?: return
    val at = Offset(s[0].toFloat(), s[1].toFloat())
    val r = 9.dp.toPx()
    when (m.kind) {
        MarkKind.SIGNAL -> {
            drawRoundRect(Color(0xFF17162B), topLeft = Offset(at.x - r * 0.45f, at.y - r * 1.2f), size = androidx.compose.ui.geometry.Size(r * 0.9f, r * 2.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.4f))
            listOf(c.red, Color(0xFFF59E0B), c.green).forEachIndexed { i, color -> drawCircle(color, radius = r * 0.26f, center = Offset(at.x, at.y + (i - 1) * r * 0.7f)) }
        }
        MarkKind.CAMERA -> {
            drawCircle(Color.White, radius = r, center = at)
            drawCircle(c.red, radius = r, center = at, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.35f))
            drawCircle(Color(0xFF17162B), radius = r * 0.35f, center = at)
        }
        MarkKind.BUMP, MarkKind.SCHOOL -> {
            drawCircle(Color(0xFFFFD84D), radius = r, center = at)
            drawCircle(c.red, radius = r, center = at, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.22f))
            if (m.kind == MarkKind.BUMP) drawArc(Color(0xFF17162B), 180f, 180f, true, topLeft = Offset(at.x - r * 0.55f, at.y - r * 0.2f), size = androidx.compose.ui.geometry.Size(r * 1.1f, r * 0.9f))
            else drawPath(Path().apply { moveTo(at.x, at.y - r * 0.55f); lineTo(at.x + r * 0.55f, at.y - r * 0.05f); lineTo(at.x + r * 0.4f, at.y + r * 0.5f); lineTo(at.x - r * 0.4f, at.y + r * 0.5f); lineTo(at.x - r * 0.55f, at.y - r * 0.05f); close() }, Color(0xFF17162B))
        }
    }
}

/** 이름은 화면에 똑바로 쓴다(지도가 돌아도 글자는 돌지 않는다). 가까운 것부터 몇 개만, 서로 겹치지 않게 */
private fun DrawScope.labels(car: LatLon, heading: Double, labels: List<MapLabel>, c: CockpitColors, dark: Boolean) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val used = ArrayList<androidx.compose.ui.geometry.Rect>()
    labels.map { it to MapMath.local(car, heading, it.at) }
        .filter { kotlin.math.abs(it.second.first) < 260 && it.second.second > -40 && it.second.second < 520 }
        .sortedBy { kotlin.math.hypot(it.second.first, it.second.second) }
        .forEach { (label, l) ->
            if (used.size >= MAX_LABELS) return
            val s = MapMath.project(l.first, l.second, size.width.toDouble(), size.height.toDouble()) ?: return@forEach
            paint.textSize = (if (label.road) 12 else 11).sp.toPx()
            val w = paint.measureText(label.text)
            val box = androidx.compose.ui.geometry.Rect(s[0].toFloat() - w / 2 - 6f, s[1].toFloat() - paint.textSize - 4f, s[0].toFloat() + w / 2 + 6f, s[1].toFloat() + 6f)
            // 위쪽 안내 칸과 내 차 자리는 비워 둔다
            if (box.top < size.height * 0.2f || box.left < 0f || box.right > size.width || used.any { it.overlaps(box) }) return@forEach
            used += box
            val nc = drawContext.canvas.nativeCanvas
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 5f
            paint.color = (if (dark) 0xFF14141F else 0xFFF3F2F8).toInt()
            nc.drawText(label.text, s[0].toFloat(), s[1].toFloat(), paint)
            paint.style = android.graphics.Paint.Style.FILL
            paint.color = (if (label.road) c.accent else c.muted).toArgb()
            nc.drawText(label.text, s[0].toFloat(), s[1].toFloat(), paint)
        }
}

private const val MAX_LABELS = 7

/** 길이나 경로 한 줄을 원근에 맞춰 그린다. 먼 곳일수록 가늘어지고, [minPx]보다 가늘어지지는 않는다 */
private fun DrawScope.way(car: LatLon, heading: Double, points: List<LatLon>, widthM: Double, color: Color, minPx: Float) {
    if (points.size < 2) return
    val w = size.width.toDouble()
    val h = size.height.toDouble()
    var prev = MapMath.local(car, heading, points[0])
    for (i in 1 until points.size) {
        val next = MapMath.local(car, heading, points[i])
        // 화면에서 한참 벗어난 선분은 건너뛴다
        if (maxOf(kotlin.math.abs(prev.first), kotlin.math.abs(next.first)) < 900 && maxOf(prev.second, next.second) > -MapMath.EYE_BACK_M && minOf(prev.second, next.second) < 900) {
            val pieces = MapMath.pieces(prev, next)
            for (j in 0 until pieces.size - 1) {
                val a = MapMath.project(pieces[j].first, pieces[j].second, w, h) ?: continue
                val b = MapMath.project(pieces[j + 1].first, pieces[j + 1].second, w, h) ?: continue
                val width = (widthM * (a[2] + b[2]) / 2).toFloat().coerceAtLeast(minPx)
                drawLine(color, Offset(a[0].toFloat(), a[1].toFloat()), Offset(b[0].toFloat(), b[1].toFloat()), strokeWidth = width, cap = StrokeCap.Round)
            }
        }
        prev = next
    }
}
