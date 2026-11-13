package com.cactuslauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cactuslauncher.theme.C
import com.cactuslauncher.resources.Res
import com.cactuslauncher.resources.ely_by
import org.jetbrains.compose.resources.painterResource

internal enum class ProfileType { Ely, Offline, Microsoft }

internal fun microsoftProfileLabel(launchCount: Long): String =
    if (launchCount > 0 && launchCount % 10L == 0L) "Microslop" else "Microsoft"

@Composable
internal fun ProfileTypeDialog(
    microsoftLabel: String,
    onDismiss: () -> Unit,
    onChoose: (ProfileType) -> Unit,
) {
    var unavailable by remember { mutableStateOf(false) }
    if (unavailable) {
        MicrosoftUnavailableDialog(microsoftLabel, onDismiss = { unavailable = false })
        return
    }
    Modal(onDismiss, width = 480.dp, height = 520.dp, wrapHeight = true) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                T("Тип профиля", 24, C.text1, FontWeight.SemiBold, lineHeight = 29, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(C.fill).buttonClickNoRipple(onDismiss),
                    contentAlignment = Alignment.Center,
                ) { T("✕", 14, C.text2, FontWeight.Medium, lineHeight = 17) }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.input),
            ) {
                ProfileTypeRow(ProfileType.Ely, "Ely.by", "Аккаунт и скины Ely.by") { onChoose(ProfileType.Ely) }
                ProviderDivider()
                ProfileTypeRow(ProfileType.Offline, "Оффлайн", "Игра без аккаунта") { onChoose(ProfileType.Offline) }
                ProviderDivider()
                ProfileTypeRow(ProfileType.Microsoft, microsoftLabel, "Minecraft: Java Edition") { unavailable = true }
            }
        }
    }
}

@Composable
private fun MicrosoftUnavailableDialog(microsoftLabel: String, onDismiss: () -> Unit) {
    Modal(onDismiss, width = 480.dp, height = 380.dp, wrapHeight = true) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ProviderIcon(ProfileType.Microsoft, C.text2)
                T("Вход через $microsoftLabel", 24, C.text1, FontWeight.SemiBold, lineHeight = 29)
            }
            T("Авторизация пока недоступна. Выберите Ely.by или оффлайн-профиль.", 15, C.text2, lineHeight = 22)
            Box(
                Modifier.align(Alignment.End).clip(RoundedCornerShape(999.dp)).background(C.accentFill)
                    .buttonClickNoRipple(onDismiss).padding(horizontal = 24.dp, vertical = 12.dp),
            ) {
                T("Понятно", 15, C.onAccent, FontWeight.Medium, lineHeight = 18)
            }
        }
    }
}

@Composable
private fun ProviderDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 60.dp, end = 16.dp).height(1.dp).background(C.border.copy(alpha = 0.5f)))
}

@Composable
private fun ProfileTypeRow(type: ProfileType, title: String, subtitle: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val interactions by rememberControlInteractions(source, true)
    Row(
        Modifier.fillMaxWidth()
            .background(if (interactions.hovered) C.fill else Color.Transparent)
            .buttonClickNoRipple(enabled = true, interactionSource = source, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { ProviderIcon(type, C.text2) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            T(title, 16, C.text1, FontWeight.Medium, lineHeight = 20)
            T(subtitle, 13, C.text2, lineHeight = 17)
        }
        val arrowColor = C.text3
        Canvas(Modifier.size(12.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(arrowColor, Offset(size.width * 0.35f, size.height * 0.2f), Offset(size.width * 0.65f, size.height * 0.5f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(arrowColor, Offset(size.width * 0.65f, size.height * 0.5f), Offset(size.width * 0.35f, size.height * 0.8f), stroke, androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}

@Composable
private fun ProviderIcon(type: ProfileType, tint: Color) {
    if (type == ProfileType.Ely) {
        Image(painterResource(Res.drawable.ely_by), contentDescription = null, modifier = Modifier.size(28.dp))
        return
    }
    Canvas(Modifier.size(28.dp)) {
        val u = size.width / 28f
        when (type) {
            ProfileType.Microsoft -> {
                val colors = listOf(0xFFF25022, 0xFF7FBA00, 0xFF00A4EF, 0xFFFFB900)
                colors.forEachIndexed { i, color -> drawRect(Color(color), Offset((i % 2) * 15f * u, (i / 2) * 15f * u), Size(12f * u, 12f * u)) }
            }
            ProfileType.Offline -> {
                drawCircle(tint, 4.5f * u, Offset(14f * u, 8f * u), style = Stroke(2f * u))
                drawRoundRect(tint, Offset(5f * u, 17f * u), Size(18f * u, 9f * u), androidx.compose.ui.geometry.CornerRadius(4f * u), style = Stroke(2f * u))
            }
            ProfileType.Ely -> Unit

        }
    }
}
