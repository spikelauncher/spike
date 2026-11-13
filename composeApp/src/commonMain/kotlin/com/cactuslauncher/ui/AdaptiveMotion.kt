package com.cactuslauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.channels.Channel
import kotlin.time.TimeSource

internal enum class MotionQuality { Full, Balanced, Reduced }
internal val LocalMotionQuality = staticCompositionLocalOf { MotionQuality.Full }

/** Resource hints only select the initial mode. Actual frame delivery can override it. */
internal fun initialMotionQuality(processors: Int, physicalMemoryBytes: Long?, softwareRenderer: Boolean): MotionQuality =
    when {
        softwareRenderer || processors in 1..2 || (physicalMemoryBytes != null && physicalMemoryBytes in 1..(4L shl 30)) -> MotionQuality.Reduced
        processors in 3..4 || (physicalMemoryBytes != null && physicalMemoryBytes in 1..(8L shl 30)) -> MotionQuality.Balanced
        else -> MotionQuality.Full
    }

/** Short active sampling windows; hysteresis prevents a single hiccup or recovery from changing modes. */
internal class AdaptiveMotionController(initial: MotionQuality, refreshRate: Int) {
    var quality: MotionQuality = initial
        private set
    private val budget = 1_000_000_000L / (refreshRate.takeIf { it in 30..240 } ?: 60)
    private var badWindows = 0
    private var goodWindows = 0
    private var changedAt = 0L

    fun record(frames: List<Long>, nowNanos: Long): MotionQuality {
        // Ignore incomplete windows, clock anomalies, debugger pauses and suspend/resume gaps.
        if (frames.size < 24 || frames.any { it <= 0 || it > 500_000_000L }) {
            badWindows = 0
            goodWindows = 0
            return quality
        }
        val slow = frames.count { it > budget * 3 / 2 }.toDouble() / frames.size
        val lostTime = frames.sumOf { (it - budget).coerceAtLeast(0L) }.toDouble() / frames.sum()
        when {
            slow >= 0.15 || lostTime >= 0.20 -> { badWindows++; goodWindows = 0 }
            slow <= 0.08 -> { goodWindows++; badWindows = 0 }
            else -> { badWindows = 0; goodWindows = 0 }
        }
        val next = when {
            badWindows >= 2 && quality != MotionQuality.Reduced -> MotionQuality.entries[quality.ordinal + 1]
            goodWindows >= 6 && nowNanos - changedAt >= 45_000_000_000L && quality != MotionQuality.Full -> MotionQuality.entries[quality.ordinal - 1]
            else -> quality
        }
        if (next != quality) {
            quality = next
            changedAt = nowNanos
            badWindows = 0
            goodWindows = 0
        }
        return quality
    }
}

/** No permanent frame loop: sample on focus/restore and user activity, at most once per five seconds. */
@Composable
internal fun AdaptiveMotionProvider(
    initial: MotionQuality,
    refreshRate: Int,
    active: Boolean,
    content: @Composable () -> Unit,
) {
    val controller = remember(initial, refreshRate) { AdaptiveMotionController(initial, refreshRate) }
    var quality by remember(controller) { mutableStateOf(controller.quality) }
    val requests = remember { Channel<Unit>(Channel.CONFLATED) }
    val epoch = remember { TimeSource.Monotonic.markNow() }
    LaunchedEffect(active) { if (active) requests.trySend(Unit) }
    LaunchedEffect(controller, active) {
        if (!active) return@LaunchedEffect
        var lastSample = -5_000_000_000L
        for (request in requests) {
            val now = epoch.elapsedNow().inWholeNanoseconds
            if (now - lastSample < 5_000_000_000L) continue
            lastSample = now
            val frames = ArrayList<Long>(32)
            var previous = 0L
            // The first timestamp establishes the baseline; later intervals include animation work.
            repeat(33) { index ->
                val frame = withFrameNanos { it }
                if (index >= 1) frames.add(frame - previous)
                previous = frame
            }
            quality = controller.record(frames, epoch.elapsedNow().inWholeNanoseconds)
        }
    }
    CompositionLocalProvider(LocalMotionQuality provides quality) {
        Box(
            Modifier.fillMaxSize().onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) requests.trySend(Unit)
                false
            }.pointerInput(requests) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll || event.changes.any { it.changedToDownIgnoreConsumed() }) {
                            requests.trySend(Unit)
                        }
                    }
                }
            },
        ) { content() }
    }
}
