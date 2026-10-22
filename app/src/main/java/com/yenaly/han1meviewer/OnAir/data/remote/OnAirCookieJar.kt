package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

class OnAirCookieJar : CookieJar {

    companion object {
        private const val TAG = "OnAirCookieJar"
        private val cookieStore: MutableMap<String, MutableMap<String, Cookie>> = ConcurrentHashMap()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val cookies = cookieStore[host]?.values?.toMutableList() ?: mutableListOf()
        Log.d(TAG, "loadForRequest: $host, cookies=${cookies.size}")
        return cookies
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val host = url.host
        val now = System.currentTimeMillis()
        val bucket = cookieStore.getOrPut(host) { ConcurrentHashMap() }
        cookies.filter { it.expiresAt == Long.MAX_VALUE || it.expiresAt > now }
            .forEach { bucket[it.name] = it }
        Log.d(TAG, "saveFromResponse: $host, merged=${cookies.size}, total=${bucket.size}")
    }

    fun seed(cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val bucket = cookieStore.getOrPut(cookie.domain) { ConcurrentHashMap() }
            bucket[cookie.name] = cookie
        }
        Log.d(TAG, "seed: ${cookies.size} cookies")
    }

    fun clearCookies() {
        cookieStore.clear()
        Log.d(TAG, "Cleared all OnAir cookies")
    }
}
