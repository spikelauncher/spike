package com.cactuslauncher

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.cactuslauncher.ui.AdaptiveMotionProvider
import java.awt.event.WindowFocusListener
import java.awt.event.WindowEvent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cactuslauncher.game.GameServiceImpl
import com.cactuslauncher.game.LauncherBuildInfo
import com.cactuslauncher.resources.Res
import com.cactuslauncher.resources.spike_launcher
import com.cactuslauncher.theme.C
import org.jetbrains.compose.resources.painterResource

fun main() {
    val launchCount = runCatching { recordLauncherStart() }.getOrElse {
        System.err.println("Не удалось сохранить счётчик запусков: ${it.javaClass.simpleName}")
        1L
    }
    application {
        val state = rememberWindowState(size = DpSize(1200.dp, 760.dp))
        val game = remember { GameServiceImpl() }
        val motionQuality = remember { desktopMotionQuality() }
        val version = remember { LauncherBuildInfo.version() }
        Window(
            onCloseRequest = { game.stopLauncherMusic(); exitApplication() },
            state = state,
            title = if (version.isEmpty()) "Spike Launcher" else "Spike Launcher $version Beta",
            icon = painterResource(Res.drawable.spike_launcher),
        ) {
            var focused by remember { mutableStateOf(window.isFocused) }
            DisposableEffect(window) {
                val listener = object : WindowFocusListener {
                    override fun windowGainedFocus(event: WindowEvent) { focused = true }
                    override fun windowLostFocus(event: WindowEvent) { focused = false }
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }
            val refreshRate = window.graphicsConfiguration.device.displayMode.refreshRate
            val dark = C.dark
            // macOS: match the title bar to the launcher theme.
            LaunchedEffect(dark) {
                if (System.getProperty("os.name").orEmpty().startsWith("Mac")) {
                    window.rootPane.putClientProperty(
                        "apple.awt.windowAppearance", if (dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
                    )
                }
            }
            // Light keeps the default selection colours.
            val selection = if (dark) TextSelectionColors(C.accent, C.accent.copy(alpha = 0.35f)) else LocalTextSelectionColors.current
            CompositionLocalProvider(LocalTextSelectionColors provides selection) {
                AdaptiveMotionProvider(motionQuality, refreshRate, focused && !state.isMinimized) {
                    App(
                        game,
                        microsoftLabel = com.cactuslauncher.ui.microsoftProfileLabel(launchCount),
                        onGhostMode = { game.stopLauncherMusic(); exitApplication() },
                        onBringToFront = {
                            if (state.isMinimized) state.isMinimized = false
                            window.toFront()
                            window.requestFocus()
                        },
                    )
                }
            }
        }
    }

}
