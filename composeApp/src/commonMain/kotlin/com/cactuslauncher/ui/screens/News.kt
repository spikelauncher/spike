package com.cactuslauncher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cactuslauncher.model.NewsArticle
import com.cactuslauncher.resources.Res
import com.cactuslauncher.resources.mounts_of_mayhem
import com.cactuslauncher.resources.wilderness_bound
import com.cactuslauncher.theme.C
import com.cactuslauncher.ui.BannerArtwork
import com.cactuslauncher.ui.SearchBox
import com.cactuslauncher.ui.T
import com.cactuslauncher.ui.buttonClickNoRipple
import com.cactuslauncher.ui.clickNoRipple

@Composable
fun NewsScreen(
    posts: List<NewsArticle>,
    loadImage: suspend (String) -> ImageBitmap?,
    onOpenArticle: (NewsArticle) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val term = query.trim()
    val visible = remember(posts, term) {
        posts.filter {
            it.title.contains(term, true) || it.badge.contains(term, true) || it.summary.contains(term, true) ||
                it.cardSummary.contains(term, true) || it.body.any { block ->
                    block.title.contains(term, true) || block.text.contains(term, true)
                }
        }
    }
    val featured = remember(visible) { visible.filter { it.featured } }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(40.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        item(key = "news-header") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                T("Новости", 32, C.text1, FontWeight.SemiBold, lineHeight = 39)
                SearchBox(query, { query = it }, "Поиск новостей", Modifier.width(280.dp), elevated = true, fill = C.surface)
            }
        }
        items(featured, key = { "banner:${it.id}" }) { post ->
            NewsBanner(post, loadImage) { onOpenArticle(post) }
        }
        items(visible, key = { "post:${it.id}" }) { post ->
            NewsCard(post, loadImage) { onOpenArticle(post) }
        }
        if (visible.isEmpty()) item(key = "news-empty") {
            T(if (term.isBlank()) "Пока нет новостей." else "Ничего не найдено.", 15, C.text2, lineHeight = 20)
        }
    }
}

@Composable
internal fun NewsBanner(
    post: NewsArticle,
    loadImage: suspend (String) -> ImageBitmap?,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().height(if (compact) 136.dp else 260.dp)
            .clip(RoundedCornerShape(if (compact) 28.dp else 24.dp))
            .background(C.banner).clickNoRipple(onClick),
        contentAlignment = if (compact) Alignment.CenterStart else Alignment.BottomStart,
    ) {
        post.banners.firstOrNull()?.let { NewsImage(it, loadImage, Modifier.fillMaxSize()) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
            listOf(Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.12f)),
        )))
        Row(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 12.dp)) {
                T(post.badge, 13, C.bannerEyebrow, lineHeight = 16, maxLines = 1)
                T(post.title, if (compact) 24 else 40, Color.White, FontWeight.SemiBold,
                    lineHeight = if (compact) 29 else 48, maxLines = 2)
                T(post.cardSummary.ifBlank { post.summary }, 15, C.bannerText, lineHeight = 18,
                    maxLines = if (compact) 1 else 3)
            }
            if (compact) T("Читать →", 15, Color.White, FontWeight.Medium, lineHeight = 18)
        }
    }
}

@Composable
private fun NewsCard(post: NewsArticle, loadImage: suspend (String) -> ImageBitmap?, onClick: () -> Unit) {
    val cover = post.banners.firstOrNull()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(C.surface)
            .clickNoRipple(onClick).padding(if (cover == null) 24.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cover != null) NewsImage(cover, loadImage, Modifier.width(144.dp).height(84.dp).clip(RoundedCornerShape(12.dp)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T(post.badge, 12, C.accent, FontWeight.Medium, lineHeight = 15)
            T(post.title, 20, C.text1, FontWeight.SemiBold, lineHeight = 25)
            T(post.cardSummary.ifBlank { post.summary }, 14, C.text2, lineHeight = 20)
            if (post.showBodyOnCard) post.body.forEach { block ->
                if (block.title.isNotBlank()) T(block.title, 15, C.text1, FontWeight.Medium, lineHeight = 20)
                T(block.text, 14, C.text2, lineHeight = 20)
            }
        }
        T("Читать →", 13, C.accent, FontWeight.Medium, lineHeight = 16)
    }
}

@Composable
fun NewsReader(article: NewsArticle, loadImage: suspend (String) -> ImageBitmap?, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var failedLink by remember(article.id) { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(C.readerBg)) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).background(C.readerBar).padding(horizontal = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("←  Все новости", 15, C.readerGreen, FontWeight.SemiBold, lineHeight = 19,
                modifier = Modifier.buttonClickNoRipple(onBack))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.width(720.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.clip(RoundedCornerShape(999.dp)).background(C.readerGreenTag)
                        .padding(horizontal = 12.dp, vertical = 5.dp)) {
                        T(article.badge, 12, C.readerGreenTagText, FontWeight.Medium, lineHeight = 15)
                    }
                    T(article.title, 40, C.readerInk, FontWeight.SemiBold, lineHeight = 48)
                    T(article.summary, 19, C.readerLede, lineHeight = 25)
                    if (article.byline.isNotBlank()) T(article.byline, 13, C.readerMuted, lineHeight = 16)
                }
                article.banners.forEach { url ->
                    NewsImage(url, loadImage, Modifier.fillMaxWidth().aspectRatio(2.25f).clip(RoundedCornerShape(24.dp)))
                }
                article.body.forEach { block ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (block.title.isNotBlank()) T(block.title, 20, C.readerInk, FontWeight.SemiBold, lineHeight = 25)
                        block.text.split("\n\n").forEach { T(it, 17, C.readerBody, lineHeight = 24) }
                    }
                }
                if (article.links.isNotEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.readerCard).padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        article.links.forEach { link ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).buttonClickNoRipple {
                                    failedLink = runCatching { uriHandler.openUri(link.url) }.exceptionOrNull()?.let { link.label }
                                }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(link.label, 15, C.readerInk, FontWeight.Medium, lineHeight = 20, modifier = Modifier.weight(1f))
                                T("↗", 16, C.accent, lineHeight = 20)
                            }
                        }
                        failedLink?.let { T("Не удалось открыть ссылку «$it».", 14, C.danger, lineHeight = 20) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsImage(url: String, loadImage: suspend (String) -> ImageBitmap?, modifier: Modifier) {
    val bitmap = key(url) {
        produceState<ImageBitmap?>(null, url) { value = loadImage(url) }.value
    }
    val fallback = when (url) {
        "https://raw.githubusercontent.com/spikelauncher/news/main/banners/wilderness_bound.jpg" -> Res.drawable.wilderness_bound
        "https://raw.githubusercontent.com/spikelauncher/news/main/banners/mounts_of_mayhem.jpg" -> Res.drawable.mounts_of_mayhem
        else -> null
    }
    when {
        bitmap != null -> Image(bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
        fallback != null -> BannerArtwork(modifier, fallback)
        else -> Box(modifier.background(C.fill))
    }
}
