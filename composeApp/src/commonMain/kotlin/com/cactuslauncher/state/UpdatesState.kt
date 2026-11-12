package com.cactuslauncher.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cactuslauncher.game.GameService
import com.cactuslauncher.model.LauncherUpdate
import kotlinx.coroutines.CancellationException

/** Launcher self-update: check, download and open the installer. */
class UpdatesState(private val game: GameService) {
    var update by mutableStateOf<LauncherUpdate?>(null)
    var downloadedFile by mutableStateOf<String?>(null)
    var status by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)

    suspend fun check(download: Boolean) {
        if (busy) return
        busy = true
        status = "Проверяем обновления…"
        try {
            val result = game.checkLauncherUpdate()
            update = result
            downloadedFile = null
            if (!result.available) {
                status = if (result.releaseUrl.isEmpty()) "Опубликованных обновлений пока нет." else "Установлена последняя версия."
            } else if (download) {
                status = "Скачиваем версию ${result.latestVersion}…"
                downloadedFile = game.downloadLauncherUpdate(result)
                status = "Версия ${result.latestVersion} готова к установке."
            } else {
                status = "Доступна версия ${result.latestVersion}."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = e.message ?: "Не удалось проверить обновления."
        } finally {
            busy = false
        }
    }

    suspend fun openDownloaded() {
        val path = downloadedFile ?: return
        val release = update ?: return
        try { game.openLauncherUpdate(release, path) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { status = e.message ?: "Не удалось открыть установщик." }
    }
}
