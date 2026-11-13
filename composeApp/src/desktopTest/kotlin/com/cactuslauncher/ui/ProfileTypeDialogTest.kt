package com.cactuslauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cactuslauncher.model.PlayerProfile
import com.cactuslauncher.model.ProfileSnapshot
import com.cactuslauncher.theme.C
import com.cactuslauncher.theme.AppFontProvider
import org.junit.Rule
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileTypeDialogTest {
    @get:Rule val compose = createComposeRule()

    private fun show(label: String = "Microsoft", account: String? = null, add: (String) -> Unit = {}, select: (String) -> Unit = {}, signIn: (String, String, String) -> Unit = { _, _, _ -> }) {
        compose.setContent {
            AppFontProvider {
            Box(Modifier.requiredSize(1000.dp, 740.dp).background(C.window)) {
                ProfilesDialog(
                    ProfileSnapshot(listOf(PlayerProfile("one", "Steve")), "one"), false, null,
                    onDismiss = {}, onRetry = {}, onAdd = add, onRename = { _, _ -> }, onSelect = select, onDelete = {},
                    externalAccountName = account,
                    microsoftLabel = label, onSignInExternal = signIn,
                )
            }
            }
        }
        compose.onNodeWithText(if (account == null) "+ Новый профиль" else "Изменить тип профиля").performClick()
        compose.onNodeWithText("Тип профиля").assertIsDisplayed()
    }

    @Test fun offlineChoiceShowsNicknameForm() {
        show()
        compose.onNodeWithText("Оффлайн").performClick()
        compose.onNodeWithText("Оффлайн-профиль").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("Alex_123")
        compose.onNodeWithText("Добавить профиль").assertIsDisplayed()
    }

    @Test fun elyChoiceUsesItsFixedEndpointWithoutAdvancedMode() {
        var request: List<String>? = null
        show { api, user, pass -> request = listOf(api, user, pass) }
        compose.onNodeWithText("Ely.by").performClick()
        compose.onNodeWithText("Вход в Ely.by").assertIsDisplayed()
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("player@example.com")
        fields[1].performTextInput("example-password")
        compose.onNodeWithText("Войти", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf("https://account.ely.by/api/authlib-injector", "player@example.com", "example-password"), request) }
    }

    @Test fun microsoftExplainsAvailabilityAndCapturesRealUi() {
        show()
        compose.onNodeWithText("Microsoft").assertIsDisplayed()
        compose.onNodeWithText("Ely.by").assertIsDisplayed()
        compose.onNodeWithText("Оффлайн").assertIsDisplayed()
        val target = File("build/screenshots/profile-picker.png")
        target.parentFile.mkdirs()
        compose.waitForIdle()
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", target)
        compose.onNodeWithText("Microsoft").performClick()
        compose.onNodeWithText("Вход через Microsoft").assertIsDisplayed()
        compose.onNodeWithText("Авторизация пока недоступна. Выберите Ely.by или оффлайн-профиль.").assertIsDisplayed()
        compose.onNodeWithText("Тип профиля").assertDoesNotExist()
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File("build/screenshots/microsoft-unavailable.png"))
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText("Тип профиля").assertIsDisplayed()
    }

    @Test fun tenthLaunchUsesAlternateName() {
        show(microsoftProfileLabel(10))
        compose.onNodeWithText("Microslop").assertIsDisplayed()
        compose.onNodeWithText("Microsoft").assertDoesNotExist()
        compose.onNodeWithText("10 запуск", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("Microslop").performClick()
        compose.onNodeWithText("Вход через Microslop").assertIsDisplayed()
        compose.onNodeWithText("Понятно").performClick()
        compose.onNodeWithText("Microslop").assertIsDisplayed()
    }
    @Test fun escapeClosesOnlyTheCurrentPopup() {
        show()
        compose.onNodeWithText("Microsoft").performClick()
        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Тип профиля").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Профили").assertIsDisplayed()
        compose.onNodeWithText("Тип профиля").assertDoesNotExist()
    }

    @Test fun outsideClickReturnsFromMicrosoftToPicker() {
        show()
        compose.onNodeWithText("Microsoft").performClick()
        compose.onRoot().performTouchInput { click(Offset(12f, 12f)) }
        compose.onNodeWithText("Тип профиля").assertIsDisplayed()
    }

    @Test fun invalidOfflineNameCannotBeSubmitted() {
        var added: String? = null
        show(add = { added = it })
        compose.onNodeWithText("Оффлайн").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("ab")
        compose.onNodeWithText("Добавить профиль").performClick()
        compose.runOnIdle { assertEquals(null, added) }
        compose.onNode(hasSetTextAction()).performTextReplacement("Valid_123")
        compose.onNodeWithText("Добавить профиль").performClick()
        compose.runOnIdle { assertEquals("Valid_123", added) }
    }

    @Test fun externalAccountCanSwitchToExistingOfflineProfile() {
        var selected: String? = null
        show(account = "ElyPlayer", select = { selected = it })
        compose.onRoot().performKeyInput { pressKey(Key.Escape) }
        compose.onNodeWithText("Выбрать").performClick()
        compose.runOnIdle { assertEquals("one", selected) }
    }

    @Test fun externalAccountCanCreateOfflineProfile() {
        var added: String? = null
        show(account = "ElyPlayer", add = { added = it })
        compose.onNodeWithText("Оффлайн").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("NewPlayer")
        compose.onNodeWithText("Добавить профиль").performClick()
        compose.runOnIdle { assertEquals("NewPlayer", added) }
    }

    @Test fun darkThemeSupportsMicrosoftPopupAndReturn() {
        compose.runOnIdle { C.dark = true }
        try {
            show()
            compose.onNodeWithText("Microsoft").performClick()
            compose.onNodeWithText("Вход через Microsoft").assertIsDisplayed()
            ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File("build/screenshots/microsoft-unavailable-dark.png").apply { parentFile.mkdirs() })
            compose.onNodeWithText("Понятно").performClick()
            compose.onNodeWithText("Тип профиля").assertIsDisplayed()
        } finally { compose.runOnIdle { C.dark = false } }
    }

}
