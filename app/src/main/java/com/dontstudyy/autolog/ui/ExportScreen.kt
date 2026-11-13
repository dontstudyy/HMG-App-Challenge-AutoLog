package com.dontstudyy.autolog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.autolog.AutoLogRuntime
import com.dontstudyy.autolog.report.ExportResult
import com.dontstudyy.autolog.report.summarize
import com.dontstudyy.autolog.report.tripsOfMonth
import com.dontstudyy.autolog.report.totalsOf

/**
 * 내보내기: 미분류가 남아 있으면 먼저 경고하고, 엑셀·PDF를 앱 전용 폴더에 저장한 뒤 서식 칸 그대로 미리본다.
 * 파일은 adb pull로 꺼낼 수 있다(설계서 2장 B8).
 */
@Composable
fun ExportScreen(onBack: () -> Unit) {
    val trips by AutoLogRuntime.trips.collectAsState()
    val unclassified = summarize(tripsOfMonth(trips, System.currentTimeMillis())).unclassifiedCount
    var result by remember { mutableStateOf<ExportResult?>(null) }
    var error by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("‹ 뒤로", onClick = onBack)
            Text("운행기록부 내보내기", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        if (unclassified > 0 && result == null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.amberSoft).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("미분류 ${unclassified}건이 남아 있어요", color = Palette.amber, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(
                    "그대로 내보내면 미분류 거리는 ⑧⑨(업무용)에 들어가지 않아 업무사용비율이 낮게 계산돼요. 리포트에서 먼저 정리하는 걸 권해요.",
                    color = Palette.text, fontSize = 18.sp,
                )
            }
        }

        if (result == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton(if (unclassified > 0) "그래도 내보내기" else "내보내기", primary = true) {
                    try { result = AutoLogRuntime.export(); error = "" } catch (t: Throwable) { error = "내보내기에 실패했어요: ${t.message}" }
                }
                if (unclassified > 0) BigButton("정리하러 가기", onClick = onBack)
            }
            if (error.isNotBlank()) Text(error, color = Palette.amber, fontSize = 18.sp)
        } else {
            val r = result!!
            Text("저장했어요", color = Palette.green, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("엑셀  ${r.xlsx.absolutePath}", color = Palette.dim, fontSize = 16.sp)
            Text("PDF   ${r.pdf.absolutePath}", color = Palette.dim, fontSize = 16.sp)
            Preview(r)
        }
    }
}

/** 서식 칸 그대로 보여 주는 미리보기(③~⑩ 표와 ⑪⑫⑬). */
@Composable
private fun Preview(r: ExportResult) {
    val doc = r.doc
    val t = totalsOf(doc.rows)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "과세기간 ${doc.periodStart} ～ ${doc.periodEnd}   상호명 ${doc.settings.company.ifBlank { "-" }}   사업자등록번호 ${doc.settings.bizNo.ifBlank { "-" }}",
            color = Palette.text, fontSize = 18.sp,
        )
        Text("①차종 ${doc.settings.vehicleType}   ②자동차등록번호 ${doc.settings.plate}", color = Palette.text, fontSize = 18.sp)
        Column(Modifier.fillMaxWidth()) {
            LogRow(listOf("③ 사용일자", "④ 사용자", "⑤ 주행 전", "⑥ 주행 후", "⑦ 주행거리", "⑧ 출퇴근", "⑨ 일반업무", "⑩ 비고"), header = true)
            doc.rows.takeLast(8).forEach { d ->
                LogRow(
                    listOf(
                        d.date, listOf(d.dept, d.name).filter { it.isNotBlank() }.joinToString(" "), commas(d.beforeKm), commas(d.afterKm), d.distanceKm.toString(),
                        d.commuteKm?.toString() ?: "", d.businessKm?.toString() ?: "", d.remark,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TotalBox("⑪ 과세기간 총주행 거리", "${commas(t.totalKm)} km", Modifier.weight(1f))
            TotalBox("⑫ 과세기간 업무용 사용거리", "${commas(t.businessKm)} km", Modifier.weight(1f))
            TotalBox("⑬ 업무사용비율 (⑫/⑪)", t.ratioText()?.let { "$it%" } ?: "-", Modifier.weight(1f), highlight = true)
        }
    }
}

@Composable
private fun TotalBox(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (highlight) Palette.accentSoft else Palette.card).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, color = Palette.dim, fontSize = 16.sp)
        Text(value, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}
