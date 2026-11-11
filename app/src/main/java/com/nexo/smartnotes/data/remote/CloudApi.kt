package com.nexo.smartnotes.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 云端接口异常，[message] 可直接展示给用户。 */
class ApiException(message: String) : IOException(message)

/**
 * 智趣笔记云服务客户端。
 * 所有方法均为挂起函数并在 IO 线程执行，网络错误统一转换为 [ApiException]。
 */
class CloudApi(private val serverUrlProvider: () -> String) {

    private val json = ApiJson.json

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private fun base(): String = serverUrlProvider().trim().trimEnd('/')

    private fun Request.Builder.authed(token: String?) = apply {
        if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
    }

    private suspend fun <T> execute(
        path: String,
        method: String = "GET",
        token: String? = null,
        body: String? = null,
        parse: (String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val url = base() + path
        val builder = Request.Builder().url(url).authed(token)
        when (method) {
            "POST" -> builder.post((body ?: "{}").toRequestBody(jsonMedia))
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        try {
            client.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching {
                        json.parseToJsonElement(text).let { el ->
                            (el as? kotlinx.serialization.json.JsonObject)
                                ?.get("message")?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                                ?.content
                        }
                    }.getOrNull()
                    throw ApiException(msg ?: "请求失败 (HTTP ${response.code})")
                }
                parse(text)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            throw ApiException("无法连接服务器：${e.message ?: "网络异常"}")
        }
    }

    /* ------------------------------------------------------- 认证 */

    suspend fun register(username: String, password: String, displayName: String): AuthResponse =
        execute("/api/auth/register", "POST", body = json.encodeToString(RegisterRequest(username, password, displayName))) {
            json.decodeFromString<AuthResponse>(it)
        }

    suspend fun login(username: String, password: String): AuthResponse =
        execute("/api/auth/login", "POST", body = json.encodeToString(LoginRequest(username, password))) {
            json.decodeFromString<AuthResponse>(it)
        }

    suspend fun me(token: String): MeResponse =
        execute("/api/auth/me", "GET", token) { json.decodeFromString<MeResponse>(it) }

    suspend fun health(): Boolean = runCatching {
        execute("/api/health", "GET") { it.contains("\"ok\":true") }
    }.getOrDefault(false)

    /* ------------------------------------------------------- 用户 / 分享 */

    suspend fun searchUsers(token: String, query: String): List<ApiUser> =
        execute("/api/users?q=${java.net.URLEncoder.encode(query, "UTF-8")}", "GET", token) {
            json.decodeFromString<UsersResponse>(it).users
        }

    suspend fun share(token: String, noteId: String, username: String, permission: String): List<ApiShare> =
        execute("/api/share", "POST", token, json.encodeToString(ShareRequest(noteId, username, permission))) {
            json.decodeFromString<ShareResponse>(it).shares
        }

    suspend fun unshare(token: String, noteId: String, userId: String): List<ApiShare> =
        execute("/api/share/$noteId/$userId", "DELETE", token) {
            json.decodeFromString<ShareResponse>(it).shares
        }

    /* ------------------------------------------------------- 图片 */

    suspend fun uploadImage(token: String, bytes: ByteArray, mime: String): String {
        val dataUrl = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        return execute("/api/upload", "POST", token, json.encodeToString(UploadRequest(dataUrl))) {
            json.decodeFromString<UploadResponse>(it).path
        }
    }

    /* ------------------------------------------------------- 同步 */

    suspend fun sync(token: String, request: SyncRequest): SyncResponse =
        execute("/api/sync", "POST", token, json.encodeToString(request)) {
            json.decodeFromString<SyncResponse>(it)
        }
}
