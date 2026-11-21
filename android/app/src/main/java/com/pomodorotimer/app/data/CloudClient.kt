package com.pomodorotimer.app.data

import com.pomodorotimer.app.domain.AppState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Minimal Supabase client: password auth, one workspace document, optimistic concurrency. */
class CloudClient(
    private val baseUrl: String,
    private val anonKey: String,
) {

    data class Session(val userId: String, val accessToken: String, val refreshToken: String)
    data class CloudRow(val revision: Long, val state: AppState, val writer: String)
    class ConflictException : Exception("SYNC_CONFLICT")
    class AuthExpiredException : Exception("AUTH_EXPIRED")

    val configured: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    suspend fun signIn(email: String, password: String): Session = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val json = JSONObject(request("POST", "/auth/v1/token?grant_type=password", body, token = null))
        sessionFrom(json)
    }

    suspend fun signUp(email: String, password: String): Session? = withContext(Dispatchers.IO) {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val json = JSONObject(request("POST", "/auth/v1/signup", body, token = null))
        if (json.has("access_token")) sessionFrom(json) else null
    }

    /** Fetch the current account identity when older stored sessions lack an email claim. */
    suspend fun accountEmail(session: Session): String? = withContext(Dispatchers.IO) {
        val user = JSONObject(request("GET", "/auth/v1/user", null, session.accessToken))
        if (user.optString("id") != session.userId) return@withContext null
        user.optString("email").takeIf { it.isNotBlank() }
    }

    suspend fun refresh(session: Session): Session = withContext(Dispatchers.IO) {
        val body = JSONObject().put("refresh_token", session.refreshToken).toString()
        val json = JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", body, token = null))
        sessionFrom(json)
    }

    suspend fun fetch(session: Session, afterRevision: Long? = null): CloudRow? = withContext(Dispatchers.IO) {
        val filter = afterRevision?.let { "&revision=gt.$it" }.orEmpty()
        val raw = request(
            "GET",
            "/rest/v1/workspaces?select=revision,payload,writer&user_id=eq.${session.userId}$filter",
            null,
            session.accessToken,
        )
        val array = org.json.JSONArray(raw)
        if (array.length() == 0) return@withContext null
        val row = array.getJSONObject(0)
        val state = StateCodec.decode(row.getJSONObject("payload").toString())
            ?: throw IllegalStateException("Invalid cloud workspace")
        CloudRow(
            revision = row.optLong("revision", 1L),
            state = state,
            writer = row.optString("writer"),
        )
    }

    suspend fun save(session: Session, state: AppState, revision: Long, device: String): CloudRow =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("expected_revision", revision)
                put("new_payload", JSONObject(StateCodec.encode(state)))
                put("device_id", device)
            }.toString()
            val raw = runCatching {
                request("POST", "/rest/v1/rpc/save_workspace", body, session.accessToken)
            }.getOrElse { error ->
                if (error.message?.contains("SYNC_CONFLICT") == true) throw ConflictException()
                throw error
            }
            val row = JSONObject(raw)
            CloudRow(
                revision = row.optLong("revision", revision + 1),
                state = StateCodec.decode(row.getJSONObject("payload").toString()) ?: state,
                writer = row.optString("writer"),
            )
        }

    /** Server-corrected clock, used to align records made on different devices. */
    suspend fun serverTime(session: Session): Long? = withContext(Dispatchers.IO) {
        runCatching {
            val raw = request("POST", "/rest/v1/rpc/server_time", "{}", session.accessToken)
            raw.trim().toDoubleOrNull()?.toLong()
        }.getOrNull()
    }

    private fun sessionFrom(json: JSONObject): Session {
        val user = json.optJSONObject("user")
        val userId = user?.optString("id").orEmpty()
        val access = json.optString("access_token")
        if (userId.isEmpty() || access.isEmpty()) throw IllegalStateException("登录失败")
        return Session(userId, access, json.optString("refresh_token"))
    }

    private fun request(method: String, path: String, body: String?, token: String?): String {
        val connection = (URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", anonKey)
            setRequestProperty("Content-Type", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
            }.orEmpty()
            if (code !in 200..299) {
                if (code == 401 && token != null) throw AuthExpiredException()
                val message = runCatching { JSONObject(text).optString("message") }.getOrDefault("")
                throw IllegalStateException(if (message.isNotBlank()) message else "HTTP $code")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Reads the deployment settings baked into the build; empty means cloud is off. */
        fun fromBuildConfig(url: String, key: String): CloudClient = CloudClient(url, key)
    }
}
