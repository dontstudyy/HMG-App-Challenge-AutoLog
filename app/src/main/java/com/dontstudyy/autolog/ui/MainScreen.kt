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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.autolog.AutoLogRuntime
import com.dontstudyy.autolog.SavePlacePrompt
import com.dontstudyy.autolog.classify.Place
import com.dontstudyy.autolog.classify.findPlace
import com.dontstudyy.autolog.data.Profile
import com.dontstudyy.autolog.location.Fix
import com.dontstudyy.autolog.location.isLost
import com.dontstudyy.autolog.report.logbookRows
import com.dontstudyy.autolog.trip.Category
import com.dontstudyy.autolog.trip.Trip
import com.dontstudyy.autolog.trip.TripKind
import com.dontstudyy.autolog.trip.TripStatus
import com.dontstudyy.autolog.vehicle.Gear
import com.dontstudyy.autolog.vehicle.VehicleState
import kotlinx.coroutines.delay

private enum class Screen { MAIN, REPORT, SETTINGS }

/** 일반업무로 답할 때 고르는 자주 쓰는 메모. */
private val MEMO_CHIPS = listOf("거래처 미팅", "고객 방문", "납품·수거", "교육·회의")

@Composable
fun AutoLogApp() {
    var screen by remember { mutableStateOf(Screen.MAIN) }
    val vehicle by AutoLogRuntime.vehicle.collectAsState()
    val trips by AutoLogRuntime.trips.collectAsState()
    val dismissed by AutoLogRuntime.dismissedGaps.collectAsState()
    val places by AutoLogRuntime.places.collectAsState()
    val profiles by AutoLogRuntime.profiles.collectAsState()
    val userId by AutoLogRuntime.currentUserId.collectAsState()
    val fix by AutoLogRuntime.lastFix.collectAsState()
    val savePrompt by AutoLogRuntime.savePrompt.collectAsState()

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        when (screen) {
            Screen.REPORT -> ReportScreen(trips = trips, onBack = { screen = Screen.MAIN })
            Screen.SETTINGS -> SettingsScreen(places, profiles, userId, fix, onBack = { screen = Screen.MAIN })
            Screen.MAIN -> MainScreen(
                vehicle, trips, dismissed, places, profiles, userId, fix, savePrompt,
                onReport = { screen = Screen.REPORT }, onSettings = { screen = Screen.SETTINGS },
            )
        }
    }
}

@Composable
private fun MainScreen(
    vehicle: VehicleState, trips: List<Trip>, dismissed: Set<Long>, places: List<Place>, profiles: List<Profile>,
    userId: Long, fix: Fix?, savePrompt: SavePlacePrompt?, onReport: () -> Unit, onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("오토로그", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("업무 운행 자동 기록", color = Palette.dim, fontSize = 18.sp)
            Spacer(Modifier.weight(1f))
            Pill(
                if (vehicle.connected) "● 차량 연결됨" else "● 차량 연결 중",
                if (vehicle.connected) Palette.green else Palette.amber,
                if (vehicle.connected) Palette.greenSoft else Palette.amberSoft,
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AppCard(vehicle, trips, dismissed, profiles, userId, savePrompt, onReport, onSettings, Modifier.weight(0.55f).fillMaxHeight())
            CarNumbersPanel(vehicle, trips, places, fix, Modifier.weight(0.45f).fillMaxHeight())
        }
        LogbookStrip(trips, profiles)
    }
}

// ---------------------------------------------------------------- 왼쪽: 앱 카드

@Composable
private fun AppCard(
    vehicle: VehicleState, trips: List<Trip>, dismissed: Set<Long>, profiles: List<Profile>, userId: Long,
    savePrompt: SavePlacePrompt?, onReport: () -> Unit, onSettings: () -> Unit, modifier: Modifier,
) {
    val running = trips.lastOrNull { it.status == TripStatus.IN_PROGRESS }
    val driving = running != null && vehicle.gear != Gear.PARK
    // 질문·정리·프로필 카드는 주행 규제가 풀려 있을 때만(설계서 4장)
    val canAsk = vehicle.uxRestricted != true && !driving
    val question = trips.lastOrNull {
        it.kind == TripKind.NORMAL && it.status == TripStatus.DONE && it.category == Category.UNCLASSIFIED && it.id !in dismissed
    }
    val gap = trips.lastOrNull {
        it.kind == TripKind.GAP && it.status == TripStatus.DONE && it.category == Category.UNCLASSIFIED && it.id !in dismissed
    }
    var picking by remember { mutableStateOf(false) }
    val user = profiles.firstOrNull { it.id == userId }

    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Palette.card)
            .border(BorderStroke(1.dp, Palette.cardLine), RoundedCornerShape(28.dp)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("오토로그", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(if (driving) "주행 중" else "정차 중", color = Palette.dim, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Palette.accentSoft)
                    .clickable(enabled = canAsk) { picking = !picking }.padding(horizontal = 18.dp, vertical = 8.dp),
            ) { Text(user?.label ?: "운전자", color = Palette.accent, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        }
        when {
            driving -> DrivingCard(vehicle, running!!)
            picking && canAsk -> ProfilePicker(profiles, userId) { picking = false }
            savePrompt != null && canAsk -> SavePlaceCard(savePrompt)
            question != null && canAsk -> QuestionCard(question)
            gap != null && canAsk -> GapCard(gap)
            else -> WaitingCard(trips, onReport, onSettings)
        }
    }
}

@Composable
private fun DrivingCard(vehicle: VehicleState, running: Trip) {
    val now = vehicle.odoTenths ?: running.startTenths
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill("● 기록 중", Palette.accent, Palette.accentSoft)
        Text("이번 운행", color = Palette.dim, fontSize = 20.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kmShort(maxOf(0, now - running.startTenths)), color = Palette.text, fontSize = 88.sp, fontWeight = FontWeight.Bold)
            Text(" km", color = Palette.dim, fontSize = 32.sp, modifier = Modifier.padding(bottom = 14.dp))
        }
        Text("출발할 때 계기판 ${kmText(running.startTenths)} km", color = Palette.dim, fontSize = 20.sp)
    }
}

/** 주차 후 목적 질문. 일반업무를 누르면 메모 칩을 고른다. */
@Composable
private fun QuestionCard(trip: Trip) {
    var memo by remember(trip.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill("● 주차 완료", Palette.accent, Palette.accentSoft)
        Text("${kmShort(trip.distanceTenths)}킬로미터 운행했어요.\n업무 목적인가요?", color = Palette.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text(routeText(trip), color = Palette.dim, fontSize = 20.sp)
        if (!memo) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("일반업무", primary = true) { memo = true }
                BigButton("출퇴근") { AutoLogRuntime.answer(trip.id, Category.COMMUTE) }
                BigButton("개인 용무") { AutoLogRuntime.answer(trip.id, Category.PRIVATE) }
                BigButton("나중에") { AutoLogRuntime.dismissGap(trip.id) }
            }
        } else {
            Text("메모를 고르세요 (건너뛸 수 있어요)", color = Palette.text, fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MEMO_CHIPS.forEach { m -> BigButton(m) { AutoLogRuntime.answer(trip.id, Category.BUSINESS, m) } }
            }
            BigButton("건너뛰기", primary = true) { AutoLogRuntime.answer(trip.id, Category.BUSINESS) }
        }
    }
}

private fun routeText(t: Trip): String {
    val from = t.startPlace.ifBlank { "미등록 위치" }
    val to = t.endPlace.ifBlank { "미등록 위치" }
    return "$from → $to"
}

@Composable
private fun SavePlaceCard(p: SavePlacePrompt) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill("● 새 장소", Palette.accent, Palette.accentSoft)
        Text("이 위치를 거래처로 저장할까요?", color = Palette.text, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        Text("저장하면 다음부터 여기에 도착하면 자동으로 일반업무로 분류돼요", color = Palette.dim, fontSize = 20.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("저장", primary = true) { AutoLogRuntime.saveClientPlace(p) }
            BigButton("저장 안 함") { AutoLogRuntime.skipSavePlace() }
        }
    }
}

@Composable
private fun ProfilePicker(profiles: List<Profile>, userId: Long, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("누가 운전하나요?", color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("정차 중에만 바꿀 수 있어요", color = Palette.dim, fontSize = 18.sp)
        profiles.forEach { p ->
            BigButton(p.label, primary = p.id == userId) { AutoLogRuntime.selectUser(p.id); onDone() }
        }
    }
}

@Composable
private fun GapCard(gap: Trip) {
    var choosing by remember(gap.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Pill("● 빈 구간 발견", Palette.amber, Palette.amberSoft)
        Text("앱이 꺼져 있던 동안 ${kmShort(gap.distanceTenths)}km를 달렸어요", color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "직전 기록 종료 ${kmText(gap.startTenths)} km → 지금 계기판 ${kmText(gap.endTenths ?: gap.startTenths)} km",
            color = Palette.dim, fontSize = 20.sp,
        )
        if (!choosing) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("지금 정리", primary = true) { choosing = true }
                BigButton("나중에 정리") { AutoLogRuntime.dismissGap(gap.id) }
            }
        } else {
            Text("어떤 운행이었나요?", color = Palette.text, fontSize = 22.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("일반업무", primary = true) { AutoLogRuntime.reclassify(gap.id, Category.BUSINESS) }
                BigButton("출퇴근") { AutoLogRuntime.reclassify(gap.id, Category.COMMUTE) }
                BigButton("개인 용무") { AutoLogRuntime.reclassify(gap.id, Category.PRIVATE) }
            }
        }
    }
}

@Composable
private fun WaitingCard(trips: List<Trip>, onReport: () -> Unit, onSettings: () -> Unit) {
    val last = trips.lastOrNull { it.status == TripStatus.DONE }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("오늘도 운행기록부는\n차가 대신 씁니다", color = Palette.text, fontSize = 38.sp, fontWeight = FontWeight.Bold)
        Text("출발하면 기록이 시작되고, 주차하면 한 줄이 완성돼요", color = Palette.dim, fontSize = 20.sp)
        if (last != null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.accentSoft).padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("직전 운행", color = Palette.dim, fontSize = 18.sp)
                    Text(
                        "${commas(last.startTenths / 10)} → ${commas((last.endTenths ?: last.startTenths) / 10)}",
                        color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    )
                    val tag = categoryLabel(last.category) + (if (last.kind == TripKind.GAP) " · 빈 구간" else "") +
                        (if (last.note.isNotBlank() && last.kind == TripKind.NORMAL) " · ${last.note}" else "")
                    Text(tag, color = Palette.dim, fontSize = 18.sp)
                }
                Text("${kmShort(last.distanceTenths)} km", color = Palette.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(1f, fill = false))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("월간 리포트", primary = true, onClick = onReport)
            BigButton("설정", onClick = onSettings)
        }
    }
}

// ---------------------------------------------------------------- 오른쪽: 차가 아는 숫자

@Composable
private fun CarNumbersPanel(vehicle: VehicleState, trips: List<Trip>, places: List<Place>, fix: Fix?, modifier: Modifier) {
    val last = trips.lastOrNull { it.status == TripStatus.DONE }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val lost = isLost(fix, now)

    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Palette.card)
            .border(BorderStroke(1.dp, Palette.cardLine), RoundedCornerShape(28.dp)).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("차가 아는 숫자", color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("차량 신호 (실제 값)", color = Palette.dim, fontSize = 16.sp)
        }
        Text("계기판 누적거리", color = Palette.dim, fontSize = 18.sp)
        OdometerDigits(vehicle.odoTenths)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatBox("주행 전 저장", last?.let { commas(it.startTenths / 10) } ?: "-", Modifier.weight(1f))
            StatBox("주행 후 저장", last?.endTenths?.let { commas(it / 10) } ?: "-", Modifier.weight(1f))
            StatBox("주행거리", last?.let { "${kmShort(it.distanceTenths)} km" } ?: "-", Modifier.weight(1f), highlight = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Palette.bg).padding(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("기어", color = Palette.dim, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Gear.PARK, Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE).forEach { g ->
                            val on = vehicle.gear == g
                            Box(
                                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                                    .background(if (on) Palette.accent else Palette.accentSoft),
                                contentAlignment = Alignment.Center,
                            ) { Text(g.label, color = if (on) Color.White else Palette.dim, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            StatBox(
                "주행 규제",
                when (vehicle.uxRestricted) { true -> "켜짐"; false -> "꺼짐"; null -> "알 수 없음" },
                Modifier.weight(1f),
            )
        }
        // 위치 상태: 마지막 수신이 10초 넘게 없으면 '끊김'. 끊겨도 마지막 위치로 장소를 판정한다.
        val placeName = fix?.let { findPlace(it.lat, it.lon, places)?.name ?: "미등록 위치" }
        StatBox(
            "위치 상태",
            when {
                fix == null -> "● 수신 대기 중"
                lost -> "● 끊김 · 마지막 위치 ${placeName ?: ""}"
                else -> "● 정상 · ${placeName ?: ""}"
            },
            Modifier.fillMaxWidth(),
            highlight = lost,
        )
    }
}

@Composable
private fun OdometerDigits(tenths: Int?) {
    // 6자리 + 소수 한 자리. 값이 없으면 '-'
    val text = tenths?.let { "%06d".format(it / 10) + (it % 10).toString() } ?: "-------"
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        text.forEachIndexed { i, ch ->
            if (i == 6) Text(".", color = Palette.dim, fontSize = 36.sp, modifier = Modifier.padding(bottom = 4.dp))
            val decimal = i == 6
            Box(
                Modifier.size(width = 56.dp, height = 84.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (decimal) Palette.text else Palette.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(ch.toString(), color = if (decimal) Palette.bg else Palette.text, fontSize = 52.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(" km", color = Palette.dim, fontSize = 24.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier, highlight: Boolean = false) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (highlight) Palette.amberSoft else Palette.bg)
            .then(if (highlight) Modifier.border(BorderStroke(1.dp, Palette.amber), RoundedCornerShape(16.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Palette.dim, fontSize = 16.sp)
        Text(value, color = if (highlight) Palette.amber else Palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------------------------------------------------------------- 아래: 운행기록부

@Composable
private fun LogbookStrip(trips: List<Trip>, profiles: List<Profile>) {
    val rows = logbookRows(trips).takeLast(6)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("운행기록부", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("운행이 끝날 때마다 한 줄씩 자동으로 채워집니다 · 거리 단위 km", color = Palette.dim, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        LogRow(
            listOf("③ 사용일자", "④ 사용자", "⑤ 주행 전", "⑥ 주행 후", "⑦ 주행거리", "⑧ 출퇴근", "⑨ 일반업무", "⑩ 비고"),
            header = true,
        )
        repeat(6) { i ->
            val r = rows.getOrNull(i)
            if (r == null) {
                LogRow(List(8) { "" })
            } else {
                val t = r.trip
                val who = profiles.firstOrNull { it.id == t.userId }?.name ?: "운전자"
                val remark = listOf(categoryLabel(t.category), t.note.ifBlank { if (t.kind == TripKind.NORMAL) routeText(t) else "" })
                    .filter { it.isNotBlank() }.joinToString(" · ")
                LogRow(
                    listOf(
                        dateText(t.startTimeMs), who, commas(r.beforeKm), commas(r.afterKm), r.distanceKm.toString(),
                        if (t.category == Category.COMMUTE) r.distanceKm.toString() else "",
                        if (t.category == Category.BUSINESS) r.distanceKm.toString() else "",
                        remark,
                    ),
                )
            }
        }
    }
}

private val colWeights = listOf(1.1f, 1f, 1f, 1f, 1f, 0.9f, 1f, 3f)

@Composable
private fun LogRow(cells: List<String>, header: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().height(if (header) 40.dp else 44.dp)
            .background(if (header) Palette.accentSoft else Palette.card)
            .border(BorderStroke(0.5.dp, Palette.cardLine)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { i, c ->
            Text(
                c, color = Palette.text,
                fontSize = if (header) 16.sp else 19.sp, fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (i in 2..6) TextAlign.End else TextAlign.Start, maxLines = 1,
                modifier = Modifier.weight(colWeights[i]).padding(horizontal = 12.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- 공용 부품

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 18.dp, vertical = 8.dp)) {
        Text(text, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun BigButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(60.dp).clip(RoundedCornerShape(16.dp))
            .background(if (!enabled) Palette.bg else if (primary) Palette.accent else Palette.accentSoft)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (!enabled) Palette.dim else if (primary) Color.White else Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
