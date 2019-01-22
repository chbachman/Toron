package com.chbachman.toron.util

import com.chbachman.toron.api.reddit.RedditPost
import redis.clients.jedis.Jedis
import redis.clients.jedis.Pipeline

fun Pipeline.redditPosts() = mapOf<String, RedditPost>("post")
fun Jedis.redditPosts() = mapOf<String, RedditPost>("post")