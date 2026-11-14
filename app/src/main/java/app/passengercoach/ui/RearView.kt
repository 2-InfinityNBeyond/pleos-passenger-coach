package app.passengercoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.passengercoach.coach.RearMath
import app.passengercoach.demo.DemoPlayer
import kotlin.math.cos
import kotlin.math.sin

/**
 * 합성 후방 화면. **실제 카메라 영상이 아니다**(공개 SDK에 후방 카메라 API가 없음).
 * 가이드선은 핸들 각도로 계산한 예상 경로이고, 실제 신호에서는 차의 위치를 몰라 가이드선만 그린다.
 * 주차 칸과 차의 움직임은 데모(시험 자료)에서만 그린다. 실제 카메라 API가 공개되면 영상으로 바꾼다.
 */
@Composable
fun RearView(steeringDeg: Float, moving: Boolean, demo: Boolean, modifier: Modifier = Modifier) {
    var pose by remember { mutableStateOf(RearMath.Pose()) }
    val steerNow by rememberUpdatedState(steeringDeg)
    val movingNow by rememberUpdatedState(moving)
    if (demo) {
        LaunchedEffect(Unit) {
            var last = withFrameNanos { it }
            while (true) {
                val t = withFrameNanos { it }
                val dt = ((t - last) / 1e9f).coerceAtMost(0.1f)
                last = t
                if (movingNow) pose = RearMath.advance(pose, steerNow, RearMath.DEMO_SPEED_MPS * dt)
            }
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF2B2F36))
            .semantics { contentDescription = "후방 화면 시뮬레이션. 가이드선이 핸들 각도에 따라 휘어요." },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val horizon = size.height * 0.16f
            drawRect(Color(0xFF3B4252), size = Size(size.width, horizon))
            if (demo) drawBay(pose, horizon)
            drawGuides(steeringDeg, horizon)
        }
        Text(
            "후방 화면 · 시뮬레이션",
            fontSize = 14.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// 간단한 원근: 카메라 높이 1.1m, 차 뒤 1.2m 지점부터 보이는 것으로 둔다
private const val CAM_HEIGHT_M = 1.1f
private const val NEAR_M = 1.2f

private fun DrawScope.project(p: RearMath.Point, horizon: Float): Offset? {
    val depth = p.b + NEAR_M
    if (depth < 0.35f) return null
    val f = size.width * 0.75f
    return Offset(size.width / 2 + p.l / depth * f, horizon + CAM_HEIGHT_M / depth * f)
}

private fun DrawScope.groundLine(points: List<RearMath.Point>, horizon: Float, width: Float, color: (Int) -> Color) {
    for (i in 0 until points.size - 1) {
        val a = project(points[i], horizon) ?: continue
        val b = project(points[i + 1], horizon) ?: continue
        drawLine(color(i), a, b, strokeWidth = width, cap = StrokeCap.Round)
    }
}

private fun between(a: RearMath.Point, b: RearMath.Point, pieces: Int = 16): List<RearMath.Point> =
    (0..pieces).map { i -> RearMath.Point(a.l + (b.l - a.l) * i / pieces, a.b + (b.b - a.b) * i / pieces) }

/** 가이드선: 가까운 곳 빨강, 중간 노랑, 먼 곳 초록(실제 후방 화면의 관례) */
private fun DrawScope.drawGuides(steeringDeg: Float, horizon: Float) {
    val step = 0.25f
    val zone = { i: Int -> if (i * step < 1f) Color(0xFFEF4444) else if (i * step < 2.5f) Color(0xFFFACC15) else Color(0xFF22C55E) }
    val left = RearMath.guide(steeringDeg, -1, stepM = step)
    val right = RearMath.guide(steeringDeg, 1, stepM = step)
    val width = size.width * 0.018f
    groundLine(left, horizon, width, zone)
    groundLine(right, horizon, width, zone)
    for (meters in listOf(1f, 2.5f, 4f)) {
        val i = (meters / step).toInt()
        groundLine(between(left[i], right[i], 4), horizon, width * 0.7f) { zone(i) }
    }
}

/** 데모 장면의 주차 칸: 데모 대본(핸들 한 바퀴로 정해진 시간 후진)이 끝나는 자리에 맞춰 놓는다 */
private fun DrawScope.drawBay(pose: RearMath.Pose, horizon: Float) {
    val end = RearMath.advance(RearMath.Pose(), DemoPlayer.PARK_STEER_DEG, RearMath.DEMO_SPEED_MPS * DemoPlayer.PARK_ARC_MS / 1000f)
    fun bay(l: Float, b: Float): RearMath.Point {
        val wl = end.l + cos(end.alpha) * l - sin(end.alpha) * b
        val wb = end.b + sin(end.alpha) * l + cos(end.alpha) * b
        return RearMath.seenFrom(pose, wl, wb)
    }
    val white = { _: Int -> Color(0xFFF9FAFB) }
    val gray = { _: Int -> Color(0xFF9CA3AF) }
    val width = size.width * 0.012f
    val (near, far) = 0.3f to 5.3f
    for (k in -3..3 step 2) {
        val l = k * 1.25f
        // 들어갈 칸의 양쪽 선은 밝고 굵게, 옆 칸의 선은 흐리게
        val target = k == -1 || k == 1
        groundLine(between(bay(l, near), bay(l, far)), horizon, if (target) width * 1.6f else width, if (target) white else gray)
    }
    groundLine(between(bay(-3.75f, far), bay(3.75f, far), 32), horizon, width, gray)
}
