package com.dontstudyy.autolog.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dontstudyy.autolog.AutoLogRuntime
import com.dontstudyy.autolog.classify.Place
import com.dontstudyy.autolog.classify.PlaceKind
import com.dontstudyy.autolog.data.Profile
import com.dontstudyy.autolog.location.Fix

/** 설정: 등록 장소(집·회사·거래처)와 프로필. 차종·연비·단가는 단계 5(유류비)에서 추가한다. */
@Composable
fun SettingsScreen(places: List<Place>, profiles: List<Profile>, userId: Long, fix: Fix?, onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BigButton("‹ 뒤로", onClick = onBack)
            Text("설정", color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }

        // ---- 장소 ----
        Text("등록 장소", color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            fix?.let { "현재 위치 ${"%.4f".format(it.lat)}, ${"%.4f".format(it.lon)} · 반경 ${Place.DEFAULT_RADIUS_M.toInt()}m" }
                ?: "위치를 아직 받지 못했어요",
            color = Palette.dim, fontSize = 18.sp,
        )
        var placeName by remember { mutableStateOf("") }
        var placeMsg by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(placeName, { placeName = it }, "장소 이름 (선택)", 320.dp)
            listOf(PlaceKind.HOME, PlaceKind.COMPANY, PlaceKind.CLIENT).forEach { k ->
                BigButton("현재 위치를 ${k.label}(으)로") {
                    placeMsg = if (AutoLogRuntime.registerPlaceHere(k, placeName)) "${placeName.ifBlank { k.label }} 등록했어요" else "위치를 받은 뒤에 등록할 수 있어요"
                    placeName = ""
                }
            }
        }
        if (placeMsg.isNotBlank()) Text(placeMsg, color = Palette.green, fontSize = 18.sp)
        places.forEach { p ->
            ListLine("${p.kind.label} · ${p.name}", "${"%.4f".format(p.lat)}, ${"%.4f".format(p.lon)} · 반경 ${p.radiusM.toInt()}m") {
                SmallButton("삭제") { AutoLogRuntime.deletePlace(p.id) }
            }
        }
        if (places.isEmpty()) Text("등록된 장소가 없어요", color = Palette.dim, fontSize = 18.sp)

        Spacer(Modifier.height(8.dp))

        // ---- 프로필 ----
        Text("프로필", color = Palette.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("같은 차를 여러 명이 쓰면 정차 중에 운전자를 고릅니다. 마지막으로 고른 사용자가 이어서 기록돼요.", color = Palette.dim, fontSize = 18.sp)
        var dept by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(dept, { dept = it }, "부서", 220.dp)
            Field(name, { name = it }, "이름", 220.dp)
            BigButton("프로필 추가", primary = true) { AutoLogRuntime.addProfile(dept, name); dept = ""; name = "" }
        }
        profiles.forEach { p ->
            ListLine(p.label + if (p.id == userId) "  (사용 중)" else "", "") {
                if (p.id != userId) SmallButton("선택") { AutoLogRuntime.selectUser(p.id) }
                if (profiles.size > 1) SmallButton("삭제") { AutoLogRuntime.deleteProfile(p.id) }
            }
        }
    }
}

@Composable
private fun ListLine(title: String, sub: String, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.card).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotBlank()) Text(sub, color = Palette.dim, fontSize = 15.sp)
        }
        actions()
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.accentSoft)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Palette.text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String, width: Dp) {
    Box(
        Modifier.width(width).height(60.dp).clip(RoundedCornerShape(14.dp)).background(Palette.card).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            textStyle = TextStyle(color = Palette.text, fontSize = 22.sp),
            cursorBrush = SolidColor(Palette.accent), modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) Text(hint, color = Palette.dim, fontSize = 20.sp)
    }
}
