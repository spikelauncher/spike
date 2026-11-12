package com.cactuslauncher.model

data class NewsArticle(
    val id: String,
    val badge: String,
    val title: String,
    val summary: String,
    val cardSummary: String,
    val byline: String,
    val featured: Boolean,
    val showBodyOnCard: Boolean,
    val banners: List<String>,
    val body: List<NewsBlock>,
    val links: List<NewsLink>,
)

data class NewsBlock(val title: String, val text: String)

data class NewsLink(val label: String, val url: String)
