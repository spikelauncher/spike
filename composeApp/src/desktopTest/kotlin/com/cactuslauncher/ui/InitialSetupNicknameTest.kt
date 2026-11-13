package com.cactuslauncher.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cactuslauncher.theme.AppFontProvider
import org.junit.Rule
import org.junit.Test

class InitialSetupNicknameTest {
    @get:Rule val compose = createComposeRule()

    @Test fun firstSetupShowsHintAndLateLoadingDoesNotOverwriteTyping() {
        val initial = mutableStateOf("")
        compose.setContent {
            AppFontProvider {
                Box(Modifier.requiredSize(1000.dp, 740.dp)) {
                    InitialSetupDialog(LauncherSettings("/tmp/game"), null, null, initial.value, {}, { null })
                }
            }
        }
        compose.onNodeWithText("Введите никнейм").assertIsDisplayed()
        compose.onNodeWithText("Nick").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextEquals("")
        compose.onNode(hasSetTextAction()).performTextInput("MyPlayer")
        compose.runOnIdle { initial.value = "SavedPlayer" }
        compose.onNode(hasSetTextAction()).assertTextEquals("MyPlayer")
    }
}
