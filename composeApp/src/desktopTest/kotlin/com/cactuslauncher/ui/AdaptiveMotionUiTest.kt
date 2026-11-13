package com.cactuslauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cactuslauncher.theme.AppFontProvider
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AdaptiveMotionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reducingQualityDisablesTactileScalingAndPreservesSwitchActions() {
        val quality = mutableStateOf(MotionQuality.Full)
        val pressed = mutableStateOf(false)
        val on = mutableStateOf(false)
        lateinit var scale: State<Float>
        var enabled = true
        compose.setContent {
            CompositionLocalProvider(LocalMotionQuality provides quality.value) {
                scale = rememberTactileScale(if (pressed.value) CactusMotion.buttonScale else 1f, "adaptive-test", pressed.value)
                enabled = tactileMotionEnabled()
                Switch(on.value) { on.value = it }
            }
        }
        compose.runOnIdle { pressed.value = true }
        compose.waitForIdle()
        compose.runOnIdle { quality.value = MotionQuality.Reduced }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(enabled); assertEquals(1f, scale.value, 0.001f) }
        compose.onNode(isToggleable()).performClick().assertIsOn()
    }

    @Test fun profilesRemainUsableWhenShadowsAndTransformsAreDisabled() {
        var chosen: ProfileType? = null
        compose.setContent {
            AppFontProvider {
                CompositionLocalProvider(LocalMotionQuality provides MotionQuality.Reduced) {
                    Box(Modifier.requiredSize(1000.dp, 740.dp)) {
                        ProfileTypeDialog("Microsoft", {}, { chosen = it })
                    }
                }
            }
        }
        compose.onNodeWithText("Microsoft").performClick()
        compose.onNodeWithText("Вход через Microsoft").assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText("Оффлайн").performClick()
        compose.runOnIdle { assertEquals(ProfileType.Offline, chosen) }
    }

    @Test fun inactiveProviderDoesNotKeepRequestingFrames() {
        val active = mutableStateOf(false)
        val on = mutableStateOf(false)
        compose.setContent {
            AdaptiveMotionProvider(MotionQuality.Reduced, 60, active.value) { Switch(on.value) { on.value = it } }
        }
        compose.waitForIdle()
        compose.runOnIdle { active.value = true }
        compose.waitForIdle() // finite sampling must settle, rather than an infinite frame loop
        compose.onNode(isToggleable()).performTouchInput { click() }
        compose.onNode(isToggleable()).assertIsOn()
        compose.runOnIdle { active.value = false }
        compose.waitForIdle()
        compose.onNode(isToggleable()).assertIsOn()
    }
}
