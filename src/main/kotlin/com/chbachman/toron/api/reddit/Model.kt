package com.chbachman.toron.api.reddit

data class RedditSearchPostData(
    val kind: String,
    val data: RedditPost
)

data class RedditSearchData(
    val children: List<RedditSearchPostData>,
    val after: String? = null,
    val dist: Int
)

data class RedditSearchRequest(
    val kind: String,
    val data: RedditSearchData
)