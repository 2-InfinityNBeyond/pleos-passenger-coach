package app.passengercoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 안내의 한 장: 제목, 설명, 강조할 화면 부분들의 이름 */
private class GuideStep(val title: String, val body: String, val keys: List<String>)

// 문구는 "무엇을 보여 주는가"보다 "이것으로 무엇을 익히는가"를 말한다(사용자 피드백: 뻔한 설명은 빼고 목적을 말할 것)
private val GUIDE = listOf(
    GuideStep("계기판", "운전자의 조작이 실시간으로 표시돼요. 핸들 각도, 방향지시등, 브레이크, 기어를 한눈에 확인해요.", listOf("WHEEL", "BRAKE", "GEAR")),
    GuideStep("경로와 다음 안내", "목적지까지의 경로와 다음 안내 지점을 보여 줘요. 남은 거리를 보며 언제 무엇을 준비하는지 익혀요.", listOf("MAP")),
    GuideStep("해설과 운전 요령", "방금 한 조작과 그 상황에 맞는 운전 요령을 알려 줘요. 정차하면 직전 조작을 순서대로 되짚어요.", listOf("BAR")),
    GuideStep("궁금한 순간 저장", "이유가 궁금한 장면에서 한 번 누르면 그 순간이 저장돼요. 도착한 뒤 꺼내 보며 운전자와 이야기해 보세요.", listOf("ASK")),
    GuideStep("미리 가 보기", "출발 전에 오늘의 경로를 먼저 주행해 봐요. 회전, 합류, 정체 구간마다 필요한 요령과 예상 조작을 확인해요.", listOf("PREVIEW")),
    GuideStep("설정", "연습할 상황과 학습 기록을 관리해요. 차량 없이 살펴보려면 P R N D 칸을 5번 눌러 데모를 실행하세요.", listOf("SETTINGS")),
)

/** 주행 화면의 한 부분이 화면 어디에 있는지 안내에 알려 준다. 안내가 떠 있을 때만 쓴다 */
internal fun Modifier.guideRegion(key: String, onRegion: ((String, Rect) -> Unit)?): Modifier =
    if (onRegion == null) this else onGloballyPositioned { onRegion(key, it.boundsInRoot()) }

/**
 * 처음 쓰는 사람을 위한 안내: 실제 주행 화면 위를 어둡게 덮고, 설명하는 부분만 밝게 남겨 어디에 있는지 보여 준다.
 * 설명은 시스템 알림창처럼 보이지 않게, 강조한 부분에 붙은 보라색 말풍선으로 띄운다.
 * 첫 실행 때 한 번 보여 주고, 설정에서 다시 볼 수 있다.
 */
@Composable
internal fun GuideOverlay(regions: Map<String, Rect>, onDone: () -> Unit) {
    val c = if (isSystemInDarkTheme()) Night else Day
    var step by remember { mutableIntStateOf(0) }
    var bubbleHeight by remember { mutableIntStateOf(0) }
    val now = GUIDE[step]
    val target = now.keys.mapNotNull { regions[it] }.reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val d = LocalDensity.current
        val margin = with(d) { 16.dp.toPx() }
        val gap = with(d) { 16.dp.toPx() }
        val width = minOf(with(d) { 440.dp.toPx() }, constraints.maxWidth - margin * 2)
        // 말풍선은 강조한 부분 바로 아래(위쪽 부분일 때)나 바로 위(아래쪽 부분일 때)에 붙인다
        val below = target != null && target.center.y < constraints.maxHeight / 2f
        val x = ((target?.center?.x ?: constraints.maxWidth / 2f) - width / 2).coerceIn(margin, constraints.maxWidth - margin - width)
        val wanted = if (target == null) margin else if (below) target.bottom + gap else target.top - gap - bubbleHeight
        val y = wanted.coerceIn(margin, (constraints.maxHeight - margin - bubbleHeight).coerceAtLeast(margin))
        Canvas(
            // 아래 화면이 눌리지 않게 손가락 입력을 여기서 받는다
            Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.pointerInput(Unit) { detectTapGestures { } },
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            if (target != null) {
                val r = target.inflate(6.dp.toPx())
                val corner = CornerRadius(28.dp.toPx())
                drawRoundRect(Color.Transparent, topLeft = r.topLeft, size = r.size, cornerRadius = corner, blendMode = BlendMode.Clear)
                drawRoundRect(c.ask, topLeft = r.topLeft, size = r.size, cornerRadius = corner, style = Stroke(width = 3.dp.toPx()))
                // 말풍선에서 강조한 부분을 가리키는 꼬리
                val tipX = target.center.x.coerceIn(x + 36.dp.toPx(), x + width - 36.dp.toPx())
                val half = 11.dp.toPx()
                val base = if (below) y + 1f else y + bubbleHeight - 1f
                val tip = if (below) base - 12.dp.toPx() else base + 12.dp.toPx()
                drawPath(Path().apply { moveTo(tipX - half, base); lineTo(tipX, tip); lineTo(tipX + half, base); close() }, c.accent)
            }
        }
        Column(
            Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.width(with(d) { width.toDp() }).onSizeChanged { bubbleHeight = it.height }
                .background(c.accent, RoundedCornerShape(22.dp)).padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).background(c.ask, CircleShape), contentAlignment = Alignment.Center) {
                    Text("${step + 1}", fontSize = 14.sp, fontWeight = FontWeight.Black, color = c.askText)
                }
                Text(now.title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = c.onAccent, modifier = Modifier.weight(1f))
                Text("건너뛰기", fontSize = 14.sp, color = c.onAccent.copy(alpha = 0.7f), modifier = Modifier.clickable(role = Role.Button, onClick = onDone).padding(6.dp))
            }
            Text(now.body, fontSize = 16.sp, color = c.onAccent.copy(alpha = 0.92f), lineHeight = 23.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // 진행 표시: 지금 장만 길게
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    GUIDE.indices.forEach { Box(Modifier.size(if (it == step) 18.dp else 6.dp, 6.dp).background(c.onAccent.copy(alpha = if (it == step) 1f else 0.35f), CircleShape)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step > 0) Text("이전", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onAccent.copy(alpha = 0.85f), modifier = Modifier.clickable(role = Role.Button) { step -= 1 }.padding(horizontal = 12.dp, vertical = 9.dp))
                    val last = step == GUIDE.lastIndex
                    Text(
                        if (last) "시작하기" else "다음", fontSize = 16.sp, fontWeight = FontWeight.Black, color = c.askText,
                        modifier = Modifier.background(c.ask, RoundedCornerShape(999.dp)).clickable(role = Role.Button) { if (last) onDone() else step += 1 }.padding(horizontal = 20.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}
