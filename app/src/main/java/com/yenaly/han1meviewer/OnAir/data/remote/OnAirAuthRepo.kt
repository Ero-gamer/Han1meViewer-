package com.yenaly.han1meviewer.OnAir.data.remote

import android.util.Log
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork.baseUrl
import com.yenaly.han1meviewer.OnAir.data.remote.OnAirNetwork.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response

object OnAirAuthRepo {

    private const val TAG = "OnAirAuthRepo"

    data class CaptchaChallenge(
        val csrfToken: String,
        val honeypotName: String,
        val captchaImageUrl: String,
        val extraFields: Map<String, String> = emptyMap(),
    )

    sealed interface AuthResult {
        data object Success : AuthResult
        data class FormError(val message: String) : AuthResult
        data class NetworkError(val throwable: Throwable) : AuthResult
    }

    fun fetchLoginCaptcha() = flow {
        try {
            val response = service.getRaw(baseUrl.trimEnd('/') + "/login")
            val html = response.checkedHtml()
            emit(parseCaptchaChallenge(html, "login"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchLoginCaptcha failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun fetchRegisterCaptcha() = flow {
        try {
            val response = service.getRaw(baseUrl.trimEnd('/') + "/register")
            val html = response.checkedHtml()
            emit(parseCaptchaChallenge(html, "register"))
        } catch (e: Exception) {
            Log.e(TAG, "fetchRegisterCaptcha failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun login(
        challenge: CaptchaChallenge,
        username: String,
        password: String,
        captchaAnswer: String,
    ) = flow {
        try {
            val body = FormBody.Builder()
                .add("csrf_token", challenge.csrfToken)
                .add(challenge.honeypotName, "")
                .add("username", username)
                .add("password", password)
                .add("captcha_answer", captchaAnswer)
                .build()

            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/login")
                .post(body)
                .build()

            OnAirNetwork.sharedOkHttpClient.newCall(request).execute().use { res ->
                val code = res.code
                val location = res.header("Location").orEmpty()
                val finalHtml = res.body?.string().orEmpty()

                Log.d(
                    TAG,
                    "login response: code=$code, location=$location, htmlLen=${finalHtml.length}",
                )

                val looksLikeLoginPage =
                    finalHtml.contains("name=\"password\"", ignoreCase = true) &&
                            finalHtml.contains("name=\"csrf_token\"", ignoreCase = true)

                val extracted = extractAndSaveCookies()

                if (code in 200..399 && extracted && !looksLikeLoginPage) {
                    emit(AuthResult.Success)
                    return@use
                }

                if (code in 200..399 && !looksLikeLoginPage && !extracted) {

                    emit(AuthResult.Success)
                    return@use
                }

                val error = parseFormError(finalHtml)
                emit(
                    AuthResult.FormError(
                        error ?: "Login failed (HTTP $code${if (location.isNotBlank()) " → $location" else ""})"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "login failed", e)
            emit(AuthResult.NetworkError(e))
        }
    }.flowOn(Dispatchers.IO)

    fun register(
        challenge: CaptchaChallenge,
        username: String,
        password: String,
        confirm: String,
        captchaAnswer: String,
        refCode: String = "",
    ) = flow {
        try {
            val body = FormBuilder()
                .add("csrf_token", challenge.csrfToken)
                .add("ref_code", refCode)
                .add(challenge.honeypotName, "")
                .add("username", username)
                .add("password", password)
                .add("confirm", confirm)
                .add("captcha_answer", captchaAnswer)
                .build()

            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/register")
                .post(body)
                .build()

            OnAirNetwork.sharedOkHttpClient.newCall(request).execute().use { res ->
                val code = res.code
                val location = res.header("Location").orEmpty()
                val finalHtml = res.body?.string().orEmpty()

                Log.d(
                    TAG,
                    "register response: code=$code, location=$location, htmlLen=${finalHtml.length}",
                )

                val looksLikeRegisterPage =
                    finalHtml.contains("name=\"confirm\"", ignoreCase = true) &&
                            finalHtml.contains("name=\"csrf_token\"", ignoreCase = true)

                val extracted = extractAndSaveCookies()

                if (code in 200..399 && extracted && !looksLikeRegisterPage) {
                    emit(AuthResult.Success)
                    return@use
                }

                if (code in 200..399 && !looksLikeRegisterPage && !extracted) {
                    emit(AuthResult.Success)
                    return@use
                }

                val error = parseFormError(finalHtml)
                emit(
                    AuthResult.FormError(
                        error ?: "Registration failed (HTTP $code${if (location.isNotBlank()) " → $location" else ""})"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "register failed", e)
            emit(AuthResult.NetworkError(e))
        }
    }.flowOn(Dispatchers.IO)

    fun fetchLogoutToken() = flow {
        try {
            val response = service.getRaw(baseUrl.trimEnd('/') + "/settings")
            val html = response.checkedHtml()
            val doc = Jsoup.parse(html, baseUrl)
            val token = doc.selectFirst("form[action*=logout] input[name=csrf_token]")
                ?.attr("value")
                ?.takeIf { it.isNotBlank() }
                ?: doc.selectFirst("input[name=csrf_token]")?.attr("value")
                    ?.takeIf { it.isNotBlank() }
                ?: error("Missing logout csrf_token on /settings")
            emit(token)
        } catch (e: Exception) {
            Log.e(TAG, "fetchLogoutToken failed", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun logout(token: String) = flow {
        try {
            val body = FormBody.Builder()
                .add("csrf_token", token)
                .build()
            val request = Request.Builder()
                .url(baseUrl.trimEnd('/') + "/logout")
                .post(body)
                .build()
            OnAirNetwork.sharedOkHttpClient.newCall(request).execute().use { res ->
                val code = res.code
                if (code in 200..399) {
                    OnAirCookieManager.clearCookies()
                    emit(AuthResult.Success)
                } else {
                    emit(AuthResult.FormError("Logout failed (HTTP $code)"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "logout failed", e)
            emit(AuthResult.NetworkError(e))
        }
    }.flowOn(Dispatchers.IO)

    fun currentUsernameHint(): String? {
        val raw = OnAirCookieManager.cookiesRaw
        if (raw.isBlank()) return null
        val match = Regex("""(?:^|;\s*)remember_web_[^=]+=([^;]+)""").find(raw)
        if (match != null) {
            val decoded = runCatching {
                java.net.URLDecoder.decode(match.groupValues[1], "UTF-8")
            }.getOrNull()
            val m2 = decoded?.let { Regex(""""username":"([^"]+)"""").find(it) }
            if (m2 != null) return m2.groupValues[1]
        }
        return null
    }

    fun captchaImageUrl(): String = baseUrl.trimEnd('/') + "/captcha.png"

    private class FormBuilder {
        private val inner = FormBody.Builder()
        fun add(name: String, value: String) = apply { inner.add(name, value) }
        fun build(): FormBody = inner.build()
    }

    private fun Response<ResponseBody>.checkedHtml(): String {
        if (!isSuccessful) throw IllegalStateException("HTTP ${code()}: ${message()}")
        return body()?.string().orEmpty()
    }

    private fun parseCaptchaChallenge(html: String, kind: String): CaptchaChallenge {
        val doc = Jsoup.parse(html, baseUrl)
        val csrfToken = doc.selectFirst("input[name=csrf_token]")?.attr("value")
            ?: error("Missing csrf_token on /$kind")
        val honeypot = doc.select("input[type=text][name^=hp_]")
            .firstOrNull()
            ?.attr("name")
            ?: error("Missing honeypot field on /$kind")
        val refCode = doc.selectFirst("input[name=ref_code]")?.attr("value").orEmpty()
        val extra = if (kind == "register") mapOf("ref_code" to refCode) else emptyMap()
        return CaptchaChallenge(
            csrfToken = csrfToken,
            honeypotName = honeypot,
            captchaImageUrl = baseUrl.trimEnd('/') + "/captcha.png",
            extraFields = extra,
        )
    }

    private fun parseFormError(html: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html, baseUrl)
        val candidates = listOf(
            ".form-error",
            ".alert-danger",
            ".invalid-feedback",
            ".text-danger",
            ".help-block",
        )
        candidates.forEach { sel ->
            doc.select(sel).map { it.text().trim() }
                .firstOrNull { it.isNotBlank() }
                ?.let { return it }
        }
        return null
    }

    private fun extractAndSaveCookies(): Boolean {
        val jar = runCatching {
            val f = OnAirNetwork.javaClass.getDeclaredField("_cookieJar")
            f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            f.get(OnAirNetwork) as? OnAirCookieJar
        }.getOrNull() ?: return false

        val cookieStoreField = OnAirCookieJar.javaClass.getDeclaredField("cookieStore")
        cookieStoreField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val store = cookieStoreField.get(jar) as? Map<String, Map<String, okhttp3.Cookie>>
            ?: return false

        val all = store.values.flatMap { it.values }
        if (all.isEmpty()) return false

        val raw = all.joinToString("; ") { "${it.name}=${it.value}" }
        OnAirCookieManager.saveCookies(raw)
        Log.d(TAG, "Saved cookies after auth: ${all.size}")
        return true
    }
}
