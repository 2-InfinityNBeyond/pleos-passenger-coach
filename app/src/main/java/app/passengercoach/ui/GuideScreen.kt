package app.passengercoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GUIDE = listOf(
    "계기판" to "운전자가 핸들을 돌리고, 깜빡이를 켜고, 브레이크를 밟으면 그림이 똑같이 움직여요.",
    "지도와 다음 안내" to "운전자 내비의 다음 안내와 남은 거리가 지도 왼쪽 위에 나와요. 주변 건물과 길 이름으로 꺾을 곳을 익혀요.",
    "해설 한 줄" to "지도 바로 아래에 방금 한 조작과 그 요령이 나와요. 멈추면 방금 한 조작을 순서대로 되짚어 줘요.",
    "노란 ? 버튼" to "궁금한 순간에 한 번 눌러 두세요. 주차한 뒤 설정의 '물어볼 것 보기'에서 그 순간을 다시 볼 수 있어요.",
    "보라색 버튼: 미리 가 보기" to "출발하기 전에 오늘 갈 길을 먼저 따라가 봐요. 짚어 볼 곳마다 요령과 예상 조작이 나와요.",
    "설정" to "연습할 상황 고르기, 학습 기록, 동의 철회가 있어요. 차 없이 보려면 P R N D 칸을 5번 눌러 데모를 켜요.",
)

/**
 * 처음 쓰는 사람을 위한 안내: 화면의 각 부분과 버튼이 무엇인지 한 장씩 넘기며 알려 준다.
 * 첫 실행 때 한 번 보여 주고, 설정에서 다시 볼 수 있다. 버튼에 글자를 붙이는 대신 이 안내로 설명한다(화면의 글자를 줄이려고).
 */
@Composable
internal fun GuideScreen(onDone: () -> Unit) {
    val c = if (isSystemInDarkTheme()) Night else Day
    var step by remember { mutableIntStateOf(0) }
    val (title, body) = GUIDE[step]
    Column(Modifier.fillMaxSize().background(c.bg).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("처음 오셨네요 · ${step + 1} / ${GUIDE.size}", fontSize = 16.sp, color = c.muted)
            Pill("건너뛰기", c.card, c.text, Modifier.clickable(role = Role.Button, onClick = onDone))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(190.dp, 150.dp), contentAlignment = Alignment.Center) { Picture(step, c) }
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = c.text)
            Text(body, fontSize = 19.sp, color = c.muted, textAlign = TextAlign.Center, lineHeight = 28.sp, modifier = Modifier.widthIn(max = 520.dp))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GUIDE.indices.forEach { Box(Modifier.size(if (it == step) 22.dp else 8.dp, 8.dp).background(if (it == step) c.accent else c.offGlyph, CircleShape)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (step > 0) Pill("이전", c.card, c.text, Modifier.clickable(role = Role.Button) { step -= 1 }, big = true)
                val last = step == GUIDE.lastIndex
                Pill(if (last) "시작하기" else "다음", c.accent, c.onAccent, Modifier.clickable(role = Role.Button) { if (last) onDone() else step += 1 }, big = true)
            }
        }
    }
}

/** 각 장의 그림: 실제 화면에 있는 것과 같은 모양을 크게 보여 준다 */
@Composable
private fun Picture(step: Int, c: CockpitColors) {
    when (step) {
        0 -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Blinker(left = true, on = true, lit = true, c = c)
            CockpitWheel(35f, 120.dp, c.text, c.accent)
        }
        1 -> Row(Modifier.background(c.chip, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(56.dp)) { glyph(Glyph.LEFT, c.chipText) }
            Text("80m", fontSize = 34.sp, fontWeight = FontWeight.Black, color = c.chipText)
        }
        2 -> Column(Modifier.width(190.dp).background(c.bar, RoundedCornerShape(22.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth().height(12.dp).background(c.barText, CircleShape))
            Box(Modifier.width(110.dp).height(8.dp).background(c.barMuted, CircleShape))
        }
        3 -> Box(Modifier.size(110.dp).background(c.ask, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
            Text("?", fontSize = 56.sp, fontWeight = FontWeight.Black, color = c.askText)
        }
        4 -> Box(Modifier.size(110.dp).background(c.accent, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(62.dp)) { previewGlyph(c.onAccent) }
        }
        else -> Box(Modifier.size(110.dp).background(c.card, RoundedCornerShape(36.dp)), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(52.dp)) { slidersGlyph(c.text) }
        }
    }
}
