package com.cactuslauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cactuslauncher.model.NewsArticle
import com.cactuslauncher.resources.Res
import com.cactuslauncher.resources.mounts_of_mayhem
import com.cactuslauncher.resources.wilderness_bound
import com.cactuslauncher.theme.C
import com.cactuslauncher.ui.T
import com.cactuslauncher.ui.clickNoRipple
import com.cactuslauncher.ui.buttonClickNoRipple
import com.cactuslauncher.ui.softShadow
import com.cactuslauncher.ui.BannerArtwork
import com.cactuslauncher.ui.SearchBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import org.jetbrains.compose.resources.DrawableResource

internal data class MinecraftNewsPost(
    val version: String,
    val title: String,
    val summary: String,
    val artwork: DrawableResource,
    val source: String,
    val highlights: List<String>,
) {
    val versionLabel get() = "Обновление $version"
    fun matches(query: String) = "$versionLabel $title $summary".contains(query.trim(), ignoreCase = true)
}

internal val MinecraftReleaseNews = MinecraftNewsPost(
    version = "26.3",
    title = "Wilderness Bound",
    summary = "Тополевые леса, заброшенные лагеря и новые карты.",
    artwork = Res.drawable.wilderness_bound,
    source = "https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3",
    highlights = listOf(
        "Возле холодных биомов теперь можно встретить новый лес. В нём растут тополя с красной, оранжевой и жёлтой листвой, а на стволах и упавших брёвнах появляются грибы.",
        "По дороге загляните в заброшенный лагерь. Такие стоянки встречаются в разных биомах, а новые карты исследователя помогут искать лагеря и другие места.",
        "Для своей стоянки можно сделать соломенную кровать и подушки. Для стройки пригодятся тополевые доски, а ещё ступени и плиты из шерсти и бетона всех цветов.",
    ),
)

private val mountsOfMayhem = MinecraftNewsPost(
    version = "1.21.11",
    title = "Mounts of Mayhem",
    summary = "Копья, наутилусы и зомби-всадники.",
    artwork = Res.drawable.mounts_of_mayhem,
    source = "https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11",
    highlights = listOf(
        "Главная новинка: копьё. Его урон зависит от точности и скорости, так что сильнее всего оно бьёт с разгона верхом.",
        "Наутилусов можно приручить и плавать на них под водой. Ещё появились зомби-наутилусы, зомби-лошади со всадниками и незеритовая конская броня, а большинство ездовых мобов теперь умеет плавать.",
    ),
)

private val BETA_WHATS_NEW = listOf(
    "- картинки профилей",
    "- обновление модов через Modrinth с откатом",
    "- учёт времени в игре",
    "- новый экран модов",
    "- раздел «Остальное»: галерея скриншотов и серверы с пингом и быстрым входом",
    "- нормальная проверка конфликтов модов",
)

private const val BETA_TITLE = "Бета-версия Cactus Launcher 2.0"
private const val BETA_LEDE = "Один jar для Windows, macOS и Linux. Нужна Java 21, запуск: java -jar CactusLauncher-2.0-beta.jar."
private const val BETA_MACOS_NOTE = "Звуковой микшер и «Сейчас играет» на macOS пока работают только на Apple Silicon."

private const val SPIKE_BADGE = "Бета 2.0.5"
private const val SPIKE_TITLE = "Spike Launcher 2.0.5 Beta"
private const val SPIKE_LEDE = "В этой версии мы впервые открываем доступ к Spike Launcher для пользователей и поклонников Cactus Lite Launcher. Ранние сборки мы тестировали внутри команды и держали закрытыми: они работали нестабильно, а интерфейс был ещё в зачаточном состоянии. Теперь лёгкий лаунчер на Java выходит в первую публичную бету и будет получать обновления и исправления. Для запуска нужна Java 21."

private val spikeChanges = listOf(
    "Forge" to "Исправили скачивание и установку Forge при создании новой сборки.",
    "Новое имя" to "Выбрали имя Spike Launcher, чтобы лаунчер было проще отличать от других проектов.",
    "Обновлённая главная" to "На главной появился большой блок основной сборки, откуда её можно открыть одним нажатием. Оформление главной стало ближе к Material You.",
    "Плеер и новости" to "Плеер плавно раскрывается и сохраняет трек на паузе. При возврате из статьи сохраняются поиск и прокрутка.",
    "Скины и авторы" to "В боковой панели появился раздел «Скины» с пометкой «скоро...». Ссылки на разработчиков теперь собраны в «Остальное» → «Авторы».",
    "Horizon" to "Добавили экспериментальную поддержку Horizon — неофициальной версии Minecraft, написанной на Rust. Функция пока может работать нестабильно. Спасибо создателю Horizon ludovic111 и Motya за интеграцию!",
)

private val spikeLinks = listOf(
    "Telegram" to "https://t.me/virrek_cactus",
    "LibriumHub" to "https://libriumhub.com/user/user-6j8ktk6gsv_m",
    "GitHub" to "https://github.com/spikelauncher/spike",
)

@Composable
fun NewsScreen(onOpenArticle: (NewsArticle) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("Новости", 32, C.text1, FontWeight.SemiBold, lineHeight = 39)
            SearchBox(query, { query = it }, "Поиск новостей", Modifier.width(280.dp), elevated = true, fill = C.surface)
        }

        if (MinecraftReleaseNews.matches(query)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(C.banner)
                    .clickNoRipple { onOpenArticle(NewsArticle.WildernessBound) },
                contentAlignment = Alignment.BottomStart,
            ) {
                BannerArtwork(Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.1f)))))
                Column(
                    Modifier.width(544.dp).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    T(MinecraftReleaseNews.versionLabel, 13, C.bannerEyebrow, lineHeight = 16)
                    T(MinecraftReleaseNews.title, 40, Color.White, FontWeight.SemiBold, lineHeight = 48)
                    T(
                        MinecraftReleaseNews.summary,
                        15,
                        C.bannerText,
                        lineHeight = 18,
                    )
                }
            }
        }

        val spikeSearch = "$SPIKE_TITLE $SPIKE_LEDE ${spikeChanges.joinToString(" ") { (title, text) -> "$title $text" }}"
        if (spikeSearch.contains(query.trim(), ignoreCase = true)) {
            SpikeNewsCard { onOpenArticle(NewsArticle.SpikeBeta) }
        }

        if (MinecraftReleaseNews.matches(query)) {
            MinecraftNewsCard(MinecraftReleaseNews) { onOpenArticle(NewsArticle.WildernessBound) }
        }

        val betaSearch = "$BETA_TITLE $BETA_LEDE ${BETA_WHATS_NEW.joinToString(" ")} $BETA_MACOS_NOTE 2.0 Beta"
        if (betaSearch.contains(query.trim(), ignoreCase = true)) {
            BetaNewsCard(Modifier.fillMaxWidth()) { onOpenArticle(NewsArticle.LauncherBeta) }
        }
        if (mountsOfMayhem.matches(query)) {
            MinecraftNewsCard(mountsOfMayhem) { onOpenArticle(NewsArticle.MountsOfMayhem) }
        }
    }
}

@Composable
private fun MinecraftNewsCard(post: MinecraftNewsPost, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.surface)
            .clickNoRipple(onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BannerArtwork(Modifier.width(144.dp).height(84.dp).clip(RoundedCornerShape(12.dp)), post.artwork)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            T(post.versionLabel, 12, C.accent, FontWeight.Medium, lineHeight = 15)
            T(post.title, 20, C.text1, FontWeight.SemiBold, lineHeight = 25)
            T(post.summary, 14, C.text2, lineHeight = 20)
        }
        T("Читать →", 13, C.accent, FontWeight.Medium, lineHeight = 16)
    }
}

@Composable
private fun SpikeNewsCard(onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface)
            .clickNoRipple(onClick).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        T(SPIKE_BADGE, 12, C.accent, FontWeight.Medium, lineHeight = 15)
        T(SPIKE_TITLE, 22, C.text1, FontWeight.SemiBold, lineHeight = 27)
        T("Впервые открываем доступ пользователям и поклонникам Cactus Lite Launcher. Обновили главную, исправили Forge и добавили поддержку Horizon.", 14, C.text2, lineHeight = 20)
        T("Читать →", 13, C.accent, FontWeight.Medium, lineHeight = 16)
    }
}

@Composable
private fun BetaNewsCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .softShadow(RoundedCornerShape(20.dp), elevation = 8.dp, color = Color(0x14000000))
            .clip(RoundedCornerShape(20.dp))
            .background(C.surface)
            .clickNoRipple(onClick)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        T("Бета 2.0", 12, C.accent, FontWeight.Medium, lineHeight = 15)
        T(BETA_TITLE, 20, C.text1, FontWeight.SemiBold, lineHeight = 25)
        T(BETA_LEDE, 14, C.text2, lineHeight = 20)
        T("Что нового:", 15, C.text1, FontWeight.SemiBold, lineHeight = 20)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BETA_WHATS_NEW.forEach { item ->
                T(item, 14, C.text2, lineHeight = 19)
            }
        }
        T(BETA_MACOS_NOTE, 13, C.text2, lineHeight = 18)
    }
}

@Composable
fun NewsReader(article: NewsArticle, onBack: () -> Unit) {
    val post = when (article) {
        NewsArticle.WildernessBound -> MinecraftReleaseNews
        NewsArticle.SpikeBeta, NewsArticle.LauncherBeta -> null
        NewsArticle.MountsOfMayhem -> mountsOfMayhem
    }
    val spike = article == NewsArticle.SpikeBeta
    val uriHandler = LocalUriHandler.current
    var sourceError by remember(article) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.readerBg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(C.readerBar)
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.buttonClickNoRipple(onBack),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                T("←", 16, C.readerGreen, FontWeight.SemiBold, lineHeight = 19)
                T("Все новости", 15, C.readerGreen, FontWeight.Medium, lineHeight = 18)
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.width(720.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(C.readerGreenTag)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    ) {
                        T(post?.versionLabel ?: if (spike) SPIKE_BADGE else "Бета 2.0", 12, C.readerGreenTagText, FontWeight.Medium, lineHeight = 15)
                    }
                    T(post?.title ?: if (spike) SPIKE_TITLE else BETA_TITLE, 40, C.readerInk, FontWeight.SemiBold, lineHeight = 48)
                    T(
                        post?.summary ?: if (spike) SPIKE_LEDE else BETA_LEDE,
                        19,
                        C.readerLede,
                        lineHeight = 25,
                    )
                    if (!spike) {
                        T(if (post == null) "Cactus Launcher 2.0 Beta" else "По материалам minecraft.net, 2 мин чтения", 13, C.readerMuted, lineHeight = 16)
                    }
                }
                if (spike) {
                    SpikeReleaseDetails()
                } else if (post == null) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        T("Что нового:", 24, C.readerInk, FontWeight.SemiBold, lineHeight = 30)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            BETA_WHATS_NEW.forEach { item ->
                                T(item, 17, C.readerBody, lineHeight = 24)
                            }
                        }
                        T(BETA_MACOS_NOTE, 16, C.readerBody, lineHeight = 23)
                    }
                } else {
                    BannerArtwork(Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(24.dp)), post.artwork)
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        T("Что нового", 26, C.readerInk, FontWeight.SemiBold, lineHeight = 31)
                        post.highlights.forEach { paragraph ->
                            T(paragraph, 17, C.readerBody, lineHeight = 23)
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(C.readerCard)
                                .padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            T("Совет", 12, C.readerGreen, FontWeight.Medium, lineHeight = 15)
                            T(
                                "Перед обновлением сделайте копию мира, чтобы было куда откатиться.",
                                15,
                                C.readerInk,
                                lineHeight = 18,
                            )
                        }
                        T("Создать сборку с версией ${post.version} можно в разделе «Сборки».", 17, C.readerBody, lineHeight = 23)
                        T(
                            "Все изменения на Minecraft.net ↗",
                            16,
                            C.accent,
                            FontWeight.Medium,
                            lineHeight = 21,
                            modifier = Modifier.buttonClickNoRipple {
                                sourceError = runCatching { uriHandler.openUri(post.source) }.isFailure
                            },
                        )
                        if (sourceError) T("Не удалось открыть ссылку в браузере.", 14, C.danger, lineHeight = 20)
                    }
                }
            }
        }
    }
}


@Composable
private fun SpikeReleaseDetails() {
    val uriHandler = LocalUriHandler.current
    var failedLink by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        T("Что нового", 24, C.readerInk, FontWeight.SemiBold, lineHeight = 30)
        spikeChanges.forEach { (title, text) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(title, 16, C.readerInk, FontWeight.SemiBold, lineHeight = 21)
                T(text, 16, C.readerBody, lineHeight = 23)
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.readerCard).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        T("Где найти релиз", 18, C.readerInk, FontWeight.SemiBold, lineHeight = 23)
        spikeLinks.forEach { (label, url) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).buttonClickNoRipple {
                    failedLink = runCatching { uriHandler.openUri(url) }.exceptionOrNull()?.let { label }
                }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T(label, 15, C.readerInk, FontWeight.Medium, lineHeight = 20)
                T("↗", 16, C.accent, lineHeight = 20)
            }
        }
        failedLink?.let { T("Не удалось открыть $it в браузере.", 14, C.danger, lineHeight = 20) }
    }
}
