package com.cactuslauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.cactuslauncher.game.TrackCommand
import com.cactuslauncher.model.TrackSnapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cactuslauncher.model.Dest
import com.cactuslauncher.theme.C
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun Sidebar(
    current: Dest,
    onSelect: (Dest) -> Unit,
    onOpenSettings: () -> Unit,
    currentTrack: TrackSnapshot?,
    trackError: String?,
    onTrackCommand: (TrackCommand) -> Unit,
    playerExpanded: Boolean,
    onTogglePlayer: () -> Unit,
    profileName: String,
    onOpenProfiles: () -> Unit,
    profileAvatar: androidx.compose.ui.graphics.ImageBitmap? = null,
    profileSubtitle: String = "Офлайн-профиль",
    trackPosition: () -> Double = { currentTrack?.positionSeconds?.toDouble() ?: 0.0 },
    onSeek: (Int) -> Unit = {},
    horizonEnabled: Boolean = false,
    showPlayer: Boolean = true,
    canStartRadio: Boolean = false,
) {
    BoxWithConstraints(Modifier.width(240.dp).fillMaxHeight().background(C.sidebar)) {
        val playerMaxHeight = maxHeight * 0.48f
        Column(
            Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickNoRipple(onOpenProfiles).padding(start = 8.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileAvatar(profileName, profileAvatar, 36.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    T("$profileName ▾", 15, C.text1, lineHeight = 18, maxLines = 1)
                    T(profileSubtitle, 12, C.text2, lineHeight = 15)
                }
            }

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Dest.entries.filter { horizonEnabled || it != Dest.Horizon }.forEach { dest ->
                    NavItem(dest.label, dest == current) { onSelect(dest) }
                }
            }

            if (showPlayer) {
                AnimatedContent(
                    targetState = playerExpanded,
                    modifier = Modifier.fillMaxWidth().heightIn(max = playerMaxHeight),
                    contentAlignment = Alignment.BottomCenter,
                    transitionSpec = {
                        (fadeIn(tween(160, easing = CactusMotion.easeOut)) togetherWith
                        fadeOut(tween(CactusMotion.quick)))
                            .using(SizeTransform { _, _ -> tween(CactusMotion.normal, easing = CactusMotion.easeOut) })
                    },
                    label = "sidebar-player",
                ) { expanded ->
                    if (expanded) {
                        PlayerPopup(onTogglePlayer, currentTrack, trackError, canStartRadio, onTrackCommand, trackPosition, onSeek)
                    } else {
                        CompactPlayer(currentTrack, trackError, onTogglePlayer)
                    }
                }
            }

            NavItem("Настройки", false, C.text2, onOpenSettings)
        }
    }
}

@Composable
private fun CompactPlayer(track: TrackSnapshot?, error: String?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .softShadow(RoundedCornerShape(12.dp), elevation = 4.dp, color = Color(0x0F000000))
            .clip(RoundedCornerShape(12.dp)).background(C.surface)
            .buttonClickNoRipple(onOpen).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val artwork = track?.artwork
        if (artwork != null) {
            Image(artwork, contentDescription = null, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
        } else {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(C.artPlaceholder), contentAlignment = Alignment.Center) {
                T("♪", 18, C.accent, FontWeight.Medium, lineHeight = 20)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            T(track?.title ?: "Плеер", 13, C.text1, FontWeight.Medium, lineHeight = 16, maxLines = 1)
            val subtitle = when {
                error != null -> "Не удалось получить трек"
                track == null -> "Нет активного трека"
                track.isPlaying -> track.artist
                else -> "На паузе"
            }
            T(subtitle, 11, C.text2, lineHeight = 13, maxLines = 1)
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    color: Color = C.text1,
    onClick: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val targetFill = when {
        selected -> C.fill
        hovered -> C.fill.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val fill by animateColorAsState(targetFill, tween(140, easing = CactusMotion.easeOut), label = "navigation-fill")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(fill)
            .hoverable(hoverSource)
            .clickNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        T(
            label,
            14,
            color,
            if (selected) FontWeight.Medium else FontWeight.Normal,
            lineHeight = 17,
        )
    }
}

@Composable
private fun PlayerPopup(
    onClose: () -> Unit,
    track: TrackSnapshot?,
    error: String?,
    canStartRadio: Boolean,
    onTrackCommand: (TrackCommand) -> Unit,
    trackPosition: () -> Double,
    onSeek: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(RoundedCornerShape(16.dp), elevation = 8.dp, color = Color(0x16000000))
            .clip(RoundedCornerShape(16.dp))
            .background(C.elevated)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T("Плеер", 13, C.text1, FontWeight.SemiBold, lineHeight = 16)
            T("Свернуть", 12, C.accent, lineHeight = 16, modifier = Modifier.buttonClickNoRipple(onClose))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val artwork = track?.artwork
            if (artwork != null) {
                Image(artwork, contentDescription = "Обложка: ${track?.title ?: "трека"}", modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            } else {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(C.artPlaceholder), contentAlignment = Alignment.Center) {
                    T("♪", 24, C.accent, FontWeight.Medium, lineHeight = 28)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(track?.title ?: "Нет активного трека", 14, C.text1, FontWeight.Medium, lineHeight = 17, maxLines = 2)
                T(track?.artist ?: if (canStartRadio) "Радио лаунчера" else "Откройте аудио в браузере или плеере",
                    12, C.text2, lineHeight = 15, maxLines = 2)
            }
        }
        val duration = track?.durationSeconds ?: 0
        val playing = track?.isPlaying == true
        var tick by remember { mutableIntStateOf(0) }
        LaunchedEffect(playing) { while (playing) { delay(250); tick++ } }
        var dragFraction by remember { mutableStateOf<Float?>(null) }
        val livePosition = tick.let { trackPosition() }
        val fraction = dragFraction ?: if (duration > 0) (livePosition / duration).toFloat().coerceIn(0f, 1f) else 0f
        val position = dragFraction?.let { (it * duration).roundToInt() } ?: livePosition.toInt()
        if (duration > 0) {
            SeekBar(
                fraction = fraction,
                enabled = track != null && track.canSeek,
                dragging = dragFraction != null,
                onDrag = { dragFraction = it },
                onRelease = { value ->
                    dragFraction = null
                    onSeek((value * duration).roundToInt())
                },
                onCancel = { dragFraction = null },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                T(formatTrackTime(position), 11, C.text2, lineHeight = 14)
                T(formatTrackTime(duration), 11, C.text2, lineHeight = 14)
            }
        }
        val canSkip = track?.let { it.supported || it.canSkip } == true
        val canPause = track?.let { it.supported || it.canPause } ?: canStartRadio
        if (canSkip || canPause) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (canSkip) TrackButton("|◀", "Предыдущий трек") { onTrackCommand(TrackCommand.PREVIOUS) }
                if (canPause) {
                    val label = if (playing) "Пауза" else "Воспроизвести"
                    TrackButton(if (playing) "❚❚" else "▶", label) { onTrackCommand(TrackCommand.PLAY_PAUSE) }
                }
                if (canSkip) TrackButton("▶|", "Следующий трек") { onTrackCommand(TrackCommand.NEXT) }
            }
        }
        if (error != null) T(error, 12, C.text2, lineHeight = 15)
    }
}

@Composable
private fun SeekBar(
    fraction: Float,
    enabled: Boolean,
    dragging: Boolean,
    onDrag: (Float) -> Unit,
    onRelease: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .layout { measurable, constraints ->
                val barHeight = 4.dp.roundToPx()
                val touchHeight = 16.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = touchHeight, maxHeight = touchHeight))
                layout(placeable.width, barHeight) { placeable.place(0, (barHeight - touchHeight) / 2) }
            }
            .then(
                if (!enabled) Modifier
                else Modifier
                    .hoverable(hoverSource)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            fun at(x: Float) = if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f
                            var value = at(down.position.x)
                            drag(value)
                            var released = false
                            while (!released) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (change.pressed) {
                                    value = at(change.position.x)
                                    drag(value)
                                } else released = true
                                change.consume()
                            }
                            if (released) release(value) else cancel()
                        }
                    },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.fill)) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(RoundedCornerShape(2.dp)).background(C.accent))
        }
        if (enabled && (hovered || dragging)) {
            Box(Modifier.align(BiasAlignment(fraction * 2f - 1f, 0f)).size(10.dp).clip(CircleShape).background(C.accent))
        }
    }
}

private fun formatTrackTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

@Composable
private fun RowScope.TrackButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(8.dp)).background(C.fill)
            .buttonClickNoRipple(onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { T(label, 13, C.text1, FontWeight.Medium, lineHeight = 16) }
}
