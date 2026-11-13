package com.dontstudyy.autolog.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.autolog.AutoLogRuntime
import com.dontstudyy.autolog.report.AppSettings
import com.dontstudyy.autolog.report.estimateFuelWon
import com.dontstudyy.autolog.report.summarize
import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripKind
import com.dontstudyy.autolog.trip.TripStatus
import java.util.Calendar

/** 이번 달 운행만 모은다. */
private fun thisMonth(trips: List<Trip>): List<Trip> {
    val now = Calendar.getInstance()
    return trips.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.startTimeMs }
        c.get(Calendar.YEAR) == now.get(Calendar.YEAR) && c.get(Calendar.MONTH) == now.get(Calendar.MONTH)
    }
}

@Composable
fun ReportScreen(trips: List<Trip>, settings: AppSettings, onBack: () -> Unit, onExport: () -> Unit) {
    val month = thisMonth(trips).filter { it.status == TripStatus.DONE }
    val s = summarize(month)
    val monthNo = Calendar.getInstance().get(Calendar.MONTH) + 1

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("‹ 뒤로", onClick = onBack)
            Text("${monthNo}월 운행 리포트", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("업무사용비율", s.ratioText()?.let { "$it%" } ?: "-", Modifier.weight(1.2f), highlight = true)
            Tile("총 주행거리", "${kmShort(s.totalTenths)} km", Modifier.weight(1f))
            Tile("업무용 거리", "${kmShort(s.businessUseTenths)} km", Modifier.weight(1f), sub = "출퇴근 + 일반업무")
            Tile(
                "미분류", "${s.unclassifiedCount}건", Modifier.weight(1f),
                sub = if (s.unclassifiedCount > 0) "${kmShort(s.unclassifiedTenths)} km · 총 주행거리에서 제외" else "모두 정리됨",
                warn = s.unclassifiedCount > 0,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Tile("출퇴근", "${kmShort(s.commuteTenths)} km", Modifier.weight(1f))
            Tile("일반업무", "${kmShort(s.businessTenths)} km", Modifier.weight(1f))
            Tile("개인 용무", "${kmShort(s.privateTenths)} km", Modifier.weight(1f))
            Tile(
                "예상 유류비", "${commas(estimateFuelWon(s.totalTenths, settings.litersPer100km, settings.wonPerLiter))}원", Modifier.weight(1f),
                sub = "입력값 기준 · 연비 ${settings.litersPer100km} L/100km",
            )
            Tile("예상 통행료", "${commas(s.tollWon)}원", Modifier.weight(1f), sub = "길안내를 쓴 운행 합계")
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("운행 목록", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("미분류는 눌러서 정리하세요", color = Palette.dim, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            BigButton("내보내기", primary = true, onClick = onExport)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(month.reversed(), key = { it.id }) { TripLine(it) }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier, sub: String? = null, highlight: Boolean = false, warn: Boolean = false) {
    val border = if (highlight) Palette.accent else if (warn) Palette.amber else Palette.cardLine
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(Palette.card).border(BorderStroke(1.dp, border), RoundedCornerShape(20.dp)).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Palette.dim, fontSize = 18.sp)
        Text(value, color = if (warn) Palette.amber else Palette.text, fontSize = if (highlight) 48.sp else 36.sp, fontWeight = FontWeight.Bold)
        Text(sub ?: " ", color = Palette.dim, fontSize = 15.sp)
    }
}

@Composable
private fun TripLine(t: Trip) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.card).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(dateText(t.startTimeMs), color = Palette.dim, fontSize = 19.sp, modifier = Modifier.weight(0.8f))
        Text("${commas(t.startTenths / 10)} → ${commas((t.endTenths ?: t.startTenths) / 10)}", color = Palette.text, fontSize = 20.sp, modifier = Modifier.weight(1.4f))
        Text("${kmShort(t.distanceTenths)} km", color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.8f))
        val label = categoryLabel(t.category) + if (t.kind == TripKind.GAP) " · 빈 구간" else ""
        Text(label, color = if (t.category == Category.UNCLASSIFIED) Palette.amber else Palette.text, fontSize = 19.sp, modifier = Modifier.weight(1.2f))
        if (t.category == Category.UNCLASSIFIED) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(2.4f)) {
                SmallChoice("일반업무") { AutoLogRuntime.reclassify(t.id, Category.BUSINESS) }
                SmallChoice("출퇴근") { AutoLogRuntime.reclassify(t.id, Category.COMMUTE) }
                SmallChoice("개인 용무") { AutoLogRuntime.reclassify(t.id, Category.PRIVATE) }
            }
        } else {
            Text(t.note, color = Palette.dim, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(2.4f))
        }
    }
}

@Composable
private fun SmallChoice(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.accentSoft)
            .clickable(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}
