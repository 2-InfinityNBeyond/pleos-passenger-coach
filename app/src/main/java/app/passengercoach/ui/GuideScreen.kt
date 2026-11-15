package app.passengercoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 안내의 한 장: 제목, 설명, 강조할 화면 부분들의 이름 */
private class GuideStep(val title: String, val body: String, val keys: List<String>)

private val GUIDE = listOf(
    GuideStep("계기판", "운전자가 핸들을 돌리고, 깜빡이를 켜고, 브레이크를 밟으면 여기 그림이 똑같이 움직여요.", listOf("WHEEL", "BRAKE", "GEAR")),
    GuideStep("지도와 다음 안내", "운전자 내비의 다음 안내와 남은 거리가 왼쪽 위에 나와요. 주변 건물과 길 이름으로 꺾을 곳을 익혀요.", listOf("MAP")),
    GuideStep("해설 한 줄", "방금 한 조작과 그 요령이 여기에 나와요. 멈추면 방금 한 조작을 순서대로 되짚어 줘요.", listOf("BAR")),
    GuideStep("? 버튼", "궁금한 순간에 한 번 눌러 두세요. 주차한 뒤 설정의 '물어볼 것 보기'에서 그 순간을 다시 볼 수 있어요.", listOf("ASK")),
    GuideStep("미리 가 보기", "출발하기 전에 눌러서 오늘 갈 길을 먼저 따라가 봐요. 짚어 볼 곳마다 요령과 예상 조작이 나와요.", listOf("PREVIEW")),
    GuideStep("설정", "연습할 상황 고르기, 학습 기록, 동의 철회가 있어요. 차 없이 보려면 왼쪽의 P R N D 칸을 5번 눌러 데모를 켜요.", listOf("SETTINGS")),
)

/** 주행 화면의 한 부분이 화면 어디에 있는지 안내에 알려 준다. 안내가 떠 있을 때만 쓴다 */
internal fun Modifier.guideRegion(key: String, onRegion: ((String, Rect) -> Unit)?): Modifier =
    if (onRegion == null) this else onGloballyPositioned { onRegion(key, it.boundsInRoot()) }

/**
 * 처음 쓰는 사람을 위한 안내: 실제 주행 화면 위를 어둡게 덮고, 설명하는 부분만 밝게 남겨 어디에 있는지 보여 준다.
 * 첫 실행 때 한 번 보여 주고, 설정에서 다시 볼 수 있다. 버튼에 글자를 붙이는 대신 이 안내로 설명한다(화면의 글자를 줄이려고).
 */
@Composable
internal fun GuideOverlay(regions: Map<String, Rect>, onDone: () -> Unit) {
    val c = if (isSystemInDarkTheme()) Night else Day
    var step by remember { mutableIntStateOf(0) }
    val now = GUIDE[step]
    val target = now.keys.mapNotNull { regions[it] }.reduceOrNull { a, b -> Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
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
            }
        }
        // 설명 칸은 강조한 부분을 가리지 않는 쪽(위 또는 아래)에 둔다
        val below = target != null && target.center.y < constraints.maxHeight / 2f
        Column(
            Modifier.align(if (below) Alignment.BottomCenter else Alignment.TopCenter).padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .background(c.card, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(now.title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = c.text)
                Text("${step + 1} / ${GUIDE.size}", fontSize = 15.sp, color = c.muted)
            }
            Text(now.body, fontSize = 17.sp, color = c.muted, lineHeight = 24.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Pill("건너뛰기", c.off, c.muted, Modifier.clickable(role = Role.Button, onClick = onDone))
                if (step > 0) Pill("이전", c.off, c.text, Modifier.clickable(role = Role.Button) { step -= 1 }, big = true)
                val last = step == GUIDE.lastIndex
                Pill(if (last) "시작하기" else "다음", c.accent, c.onAccent, Modifier.clickable(role = Role.Button) { if (last) onDone() else step += 1 }, big = true)
            }
        }
    }
}
