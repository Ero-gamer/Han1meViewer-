package com.yenaly.han1meviewer.OnAir.data.remote

import android.content.Context
import com.yenaly.han1meviewer.OnAir.common.OnAirConstants
import com.yenaly.han1meviewer.logic.network.HDns
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.UrlLoggingInterceptor
import com.yenaly.han1meviewer.logic.network.interceptor.UserAgentInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object OnAirNetwork {

    @Volatile
    private var _service: OnAirService? = null
    @Volatile
    private var _baseUrl: String? = null
    @Volatile
    private var _client: OkHttpClient? = null
    @Volatile
    private var _cookieJar: OnAirCookieJar? = null
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            appContext = context.applicationContext
            isInitialized = true
            android.util.Log.d("OnAirNetwork", "Initialized")
        }
    }

    private val context: Context
        get() {
            if (!isInitialized) {
                throw IllegalStateException("OnAirNetwork not initialized.")
            }
            return appContext ?: throw IllegalStateException("OnAirNetwork context is null")
        }

    val baseUrl: String
        get() = OnAirConstants.getPrimaryUrl()

    val service: OnAirService
        get() {
            if (!isInitialized) throw IllegalStateException("OnAirNetwork not initialized.")
            val current = baseUrl
            if (_service == null || _baseUrl != current) {
                _baseUrl = current
                _service = createService(current)
            }
            return _service!!
        }

    val sharedOkHttpClient: OkHttpClient
        get() {
            if (_client == null) service
            return _client!!
        }

    private fun createService(base: String): OnAirService {
        val client = createOkHttpClient(base)
        _client = client
        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .build()
            .create(OnAirService::class.java)
    }

    private fun createOkHttpClient(base: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(UserAgentInterceptor)
            .addInterceptor(UrlLoggingInterceptor())
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Origin", base.trimEnd('/'))
                    .addHeader("Referer", base)
                    .addHeader(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    )
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .addHeader("Cache-Control", "no-cache")
                    .build()
                chain.proceed(request)
            }
            .cookieJar(getOrCreateCookieJar())
            .dns(HDns())
            .proxySelector(HProxySelector())
            .build()
    }

    private fun getOrCreateCookieJar(): OnAirCookieJar {
        return _cookieJar ?: synchronized(this) {
            _cookieJar ?: OnAirCookieJar().also {
                it.seed(OnAirCookieManager.parseToCookies(OnAirCookieManager.cookiesRaw, baseUrl))
                _cookieJar = it
            }
        }
    }

    fun refreshCookieJar() {
        val jar = getOrCreateCookieJar()
        jar.clearCookies()
        val parsed = OnAirCookieManager.parseToCookies(OnAirCookieManager.cookiesRaw, baseUrl)
        jar.seed(parsed)
    }

    fun snapshotCookies(): String {
        val jar = getOrCreateCookieJar()
        val storeField = OnAirCookieJar::class.java.getDeclaredField("cookieStore")
        storeField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val store = storeField.get(jar) as? Map<String, Map<String, okhttp3.Cookie>>
            ?: return ""
        val all = store.values.flatMap { it.values }
        return all.joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun normalizeUrl(path: String): String {
        return when {
            path.startsWith("http://") || path.startsWith("https://") -> path
            path.startsWith("//") -> "http:$path"
            path.startsWith("/") -> baseUrl.trimEnd('/') + path
            else -> baseUrl.trimEnd('/') + "/" + path
        }
    }

    fun clearCookies() {
        _cookieJar?.clearCookies()
        _cookieJar = null
    }

    fun rebuildNetwork() {
        _service = null
        _baseUrl = null
        _client = null
        _cookieJar?.let {
            it.clearCookies()
            it.seed(OnAirCookieManager.parseToCookies(OnAirCookieManager.cookiesRaw, baseUrl))
        }
    }
}
