package com.chbachman.api.reddit

data class RedditCommentPost(
    val name: String,
    val title: String
)

data class RedditCommentPostData(
    val kind: String,
    val data: RedditCommentPost
)

data class RedditCommentData(
    val children: List<RedditCommentPostData>
)

data class RedditCommentRequest(
    val kind: String,
    val data: RedditCommentData
)

data class RedditSearchPost(
    val title: String,
    val name: String,
    // Location
    val subreddit: String,
    val domain: String,
    val subreddit_name_prefixed: String,
    // Author
    val author: String,

    val score: Int,
    // Text of Post
    val selftext: String,
    val selftext_html: String = selftext,
    // Properties of Post
    val spoiler: Boolean,
    val over_18: Boolean,
    val locked: Boolean,

    val num_comments: Int,
    // Linking to Post
    val permalink: String,
    val url: String
)

data class RedditSearchPostData(
    val kind: String,
    val data: RedditSearchPost
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