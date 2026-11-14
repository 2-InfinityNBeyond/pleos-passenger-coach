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
import app.passengercoach.coach.LatLon
import app.passengercoach.coach.MapMath
import app.passengercoach.coach.Road

/**
 * 앱이 직접 그리는 지도: 진행 방향이 위로 가고, 앞유리 너머로 보듯 먼 곳이 작아진다.
 * 길 모양은 OpenStreetMap 자료이고(RoadHub), 경로 선은 운전자 내비의 것이 아니라 따로 계산한 것이다(RouteHub).
 * 지도 그림을 쓰지 않고 길만 그리므로 돌려도 글자가 뒤집히지 않고, 낮과 밤 색을 앱에 맞출 수 있다.
 */
@Composable
internal fun MapPanel(lat: Double?, lon: Double?, heading: Double?, roads: List<Road>, route: List<LatLon>, c: CockpitColors, modifier: Modifier) {
    if (lat == null || lon == null) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("위치를 받는 중", fontSize = 18.sp, color = c.muted) }
        return
    }
    val car = LatLon(lat, lon)
    val h = heading ?: 0.0
    Canvas(modifier.semantics { contentDescription = "진행 방향이 위로 가는 지도와 경로 선" }) {
        // 작은 길부터 그려서 큰길이 위에 오게 한다
        for (level in 3 downTo 0) {
            val color = if (level == 3) c.card.copy(alpha = 0.55f) else c.card
            roads.filter { it.level == level }.forEach { way(car, h, it.points, MapMath.ROAD_WIDTH_M[level], color, 1.5f) }
        }
        way(car, h, route, 6.0, c.accent, 6f)

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
