package com.thunderpass.github

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private const val TAG          = "ThunderPass/GistSync"
const val GITHUB_CLIENT_ID     = "Ov23liCrH5SjXNndTNwY"
private const val GIST_DESC    = "ThunderPass profile card"
private const val GIST_FILE    = "thunderpass.json"
private const val PREF_GIST_ID = "gist_id"
private const val PREF_USER    = "gh_username"
private const val SCHEMA_VER   = "thunderpass/v1"

// ── Device Flow state machine ─────────────────────────────────────────────────
sealed class DeviceFlowState {
    object Idle : DeviceFlowState()
    data class AwaitingCode(
        val userCode: String,
        val verificationUri: String,
        val deviceCode: String,
        val interval: Int,
    ) : DeviceFlowState()
    object Polling : DeviceFlowState()
    data class Connected(val username: String) : DeviceFlowState()
    data class Failure(val message: String) : DeviceFlowState()
}

// ── Card model (Gist payload) ─────────────────────────────────────────────────
data class ThunderCard(
    val displayName:     String,
    val greeting:        String,
    val avatarKind:      String,
    val avatarColor:     String,
    val ghostGame:       String,
    val retroUsername:   String,
    val joules:          Long,
    val encounterCount:  Int,
    val encounterStreak: Int,
    val stickers:        List<String>,
    val updatedAt:       Long,
) {
    fun toJson(): String = JSONObject().apply {
        put("schema", SCHEMA_VER)
        put("updatedAt", updatedAt)
        put("profile", JSONObject().apply {
            put("displayName",   displayName)
            put("greeting",      greeting)
            put("avatarKind",    avatarKind)
            put("avatarColor",   avatarColor)
            put("ghostGame",     ghostGame)
            put("retroUsername", retroUsername)
        })
        put("stats", JSONObject().apply {
            put("joules",          joules)
            put("encounterCount",  encounterCount)
            put("encounterStreak", encounterStreak)
            put("stickers",        JSONArray(stickers))
        })
    }.toString(2)

    companion object {
        fun fromJson(raw: String): ThunderCard? = runCatching {
            val j = JSONObject(raw)
            val p = j.getJSONObject("profile")
            val s = j.getJSONObject("stats")
            val arr = s.getJSONArray("stickers")
            ThunderCard(
                displayName     = p.optString("displayName",   "Traveler"),
                greeting        = p.optString("greeting",      ""),
                avatarKind      = p.optString("avatarKind",    "defaultBolt"),
                avatarColor     = p.optString("avatarColor",   "#FFD700"),
                ghostGame       = p.optString("ghostGame",     ""),
                retroUsername   = p.optString("retroUsername", ""),
                joules          = s.optLong("joules",          0L),
                encounterCount  = s.optInt("encounterCount",   0),
                encounterStreak = s.optInt("encounterStreak",  0),
                stickers        = (0 until arr.length()).map { arr.getString(it) },
                updatedAt       = j.optLong("updatedAt",       0L),
            )
        }.getOrNull()
    }
}

// ── Manager ───────────────────────────────────────────────────────────────────
class GistSyncManager private constructor(private val ctx: Context) {

    private val http = OkHttpClient()

    private val secure by lazy {
        val key = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, "gh_secure_prefs", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val meta by lazy {
        ctx.getSharedPreferences("gh_meta_prefs", Context.MODE_PRIVATE)
    }

    // ── Token / prefs accessors ───────────────────────────────────────────────
    fun getToken():    String? = runCatching { secure.getString("token", null) }.getOrNull()
    fun getUsername(): String? = meta.getString(PREF_USER, null)
    fun getGistId():   String? = meta.getString(PREF_GIST_ID, null)
    fun isConnected(): Boolean = getToken() != null

    private fun storeToken(t: String)   = secure.edit().putString("token", t).apply()
    private fun storeUsername(u: String) = meta.edit().putString(PREF_USER, u).apply()
    private fun storeGistId(id: String)  = meta.edit().putString(PREF_GIST_ID, id).apply()

    fun disconnect() {
        runCatching { secure.edit().remove("token").apply() }
        meta.edit().remove(PREF_USER).remove(PREF_GIST_ID).apply()
    }

    // ── Device Flow: step 1 — request user/device codes ──────────────────────
    suspend fun requestDeviceCode(): DeviceFlowState = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", GITHUB_CLIENT_ID)
            .add("scope", "gist")
            .build()
        runCatching {
            val resp = http.newCall(
                Request.Builder()
                    .url("https://github.com/login/device/code")
                    .addHeader("Accept", "application/json")
                    .post(body)
                    .build()
            ).execute()
            val j = JSONObject(resp.body!!.string())
            DeviceFlowState.AwaitingCode(
                userCode        = j.getString("user_code"),
                verificationUri = j.getString("verification_uri"),
                deviceCode      = j.getString("device_code"),
                interval        = j.optInt("interval", 5),
            )
        }.getOrElse { DeviceFlowState.Failure(it.message ?: "Network error") }
    }

    // ── Device Flow: step 2 — poll until user approves ────────────────────────
    suspend fun pollUntilAuthorized(deviceCode: String, intervalSecs: Int): DeviceFlowState =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + 14 * 60 * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(intervalSecs * 1000L)
                val body = FormBody.Builder()
                    .add("client_id", GITHUB_CLIENT_ID)
                    .add("device_code", deviceCode)
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .build()
                val resp = runCatching {
                    http.newCall(
                        Request.Builder()
                            .url("https://github.com/login/oauth/access_token")
                            .addHeader("Accept", "application/json")
                            .post(body)
                            .build()
                    ).execute()
                }.getOrNull() ?: continue

                val j     = JSONObject(resp.body!!.string())
                val token = j.optString("access_token", "")
                when {
                    token.isNotBlank() -> {
                        storeToken(token)
                        val username = fetchAuthenticatedUser(token) ?: "unknown"
                        storeUsername(username)
                        return@withContext DeviceFlowState.Connected(username)
                    }
                    j.optString("error") == "slow_down"    -> delay(5_000)
                    j.optString("error") == "expired_token" ->
                        return@withContext DeviceFlowState.Failure("Code expired — please try again.")
                    j.optString("error") == "access_denied" ->
                        return@withContext DeviceFlowState.Failure("Access denied.")
                    // "authorization_pending" → keep looping
                }
            }
            DeviceFlowState.Failure("Timed out. Please try again.")
        }

    // ── Push profile card to Gist ─────────────────────────────────────────────
    suspend fun push(card: ThunderCard): Boolean = withContext(Dispatchers.IO) {
        val token = getToken() ?: return@withContext false
        val json  = card.toJson()
        runCatching {
            val existingId = getGistId()
            if (existingId != null) {
                patchGist(token, existingId, json)
            } else {
                val newId = createGist(token, json)
                if (newId != null) { storeGistId(newId); true } else false
            }
        }.getOrElse { Log.e(TAG, "Push failed: ${it.message}"); false }
    }

    // ── Pull a public card by GitHub username ─────────────────────────────────
    suspend fun pull(githubUser: String): ThunderCard? = withContext(Dispatchers.IO) {
        runCatching {
            val resp = http.newCall(
                Request.Builder()
                    .url("https://api.github.com/users/$githubUser/gists")
                    .addHeader("Accept", "application/vnd.github+json")
                    .build()
            ).execute()
            val arr = JSONArray(resp.body!!.string())
            for (i in 0 until arr.length()) {
                val gist = arr.getJSONObject(i)
                if (gist.optString("description") == GIST_DESC) {
                    val files = gist.getJSONObject("files")
                    if (files.has(GIST_FILE)) {
                        val rawUrl = files.getJSONObject(GIST_FILE).getString("raw_url")
                        val content = fetchRaw(rawUrl) ?: return@runCatching null
                        return@runCatching ThunderCard.fromJson(content)
                    }
                }
            }
            null
        }.getOrNull()
    }

    // ── Private helpers ───────────────────────────────────────────────────────
    private fun fetchAuthenticatedUser(token: String): String? = runCatching {
        val resp = http.newCall(
            Request.Builder()
                .url("https://api.github.com/user")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .build()
        ).execute()
        JSONObject(resp.body!!.string()).getString("login")
    }.getOrNull()

    private fun fetchRaw(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().body!!.string()
    }.getOrNull()

    private fun createGist(token: String, content: String): String? = runCatching {
        val body = JSONObject().apply {
            put("description", GIST_DESC)
            put("public", true)
            put("files", JSONObject().apply {
                put(GIST_FILE, JSONObject().apply { put("content", content) })
            })
        }.toString().toRequestBody("application/json".toMediaType())
        val resp = http.newCall(
            Request.Builder()
                .url("https://api.github.com/gists")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .post(body)
                .build()
        ).execute()
        JSONObject(resp.body!!.string()).getString("id")
    }.getOrNull()

    private fun patchGist(token: String, gistId: String, content: String): Boolean = runCatching {
        val body = JSONObject().apply {
            put("files", JSONObject().apply {
                put(GIST_FILE, JSONObject().apply { put("content", content) })
            })
        }.toString().toRequestBody("application/json".toMediaType())
        http.newCall(
            Request.Builder()
                .url("https://api.github.com/gists/$gistId")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github+json")
                .patch(body)
                .build()
        ).execute().isSuccessful
    }.getOrElse { false }

    companion object {
        @Volatile private var inst: GistSyncManager? = null
        fun getInstance(ctx: Context) = inst ?: synchronized(this) {
            inst ?: GistSyncManager(ctx.applicationContext).also { inst = it }
        }
    }
}
