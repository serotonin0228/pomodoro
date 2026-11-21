package com.pomodorotimer.app.data

import android.content.Context
import com.pomodorotimer.app.domain.AppState
import com.pomodorotimer.app.domain.Clock
import com.pomodorotimer.app.domain.uid
import org.json.JSONObject

/** Snapshot as persisted and as exchanged with the cloud. */
data class PersistedWorkspace(
    val state: AppState,
    val pending: Boolean,
    val revision: Long,
)

/**
 * Local-first storage.
 *
 * One key per account (plus a guest key) mirrors the reference app: switching account switches the
 * whole workspace, and a write queued for one account can never land in another's document.
 */
class WorkspaceStore(context: Context) {

    private val cloudPrefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)
    private val prefs = context.getSharedPreferences("pomodoro.workspace.v1", Context.MODE_PRIVATE)

    fun deviceId(): String {
        val existing = prefs.getString(KEY_DEVICE, null)
        if (existing != null && existing.isNotEmpty()) return existing
        val created = "android-${uid(Clock.now())}"
        prefs.edit().putString(KEY_DEVICE, created).apply()
        return created
    }

    fun activeAccount(): String? = accountFromSession(cloudPrefs.getString("session", null))

    fun legacyOwner(): String? {
        migrateLegacyWorkspace()
        return prefs.getString("legacy_owner", null)
    }

    fun loadActive(): PersistedWorkspace? = load(activeAccount())

    private fun migrateLegacyWorkspace() {
        if (prefs.getBoolean("accounts_v2", false)) return
        val account = activeAccount()
        val guest = prefs.getString(key(null), null)
        val editor = prefs.edit().putBoolean("accounts_v2", true).putString("legacy_owner", account)
        if (guest != null && account != null && !prefs.contains(key(account))) {
            val legacy = runCatching { JSONObject(guest) }.getOrNull()
            if (legacy != null) {
                legacy.put("revision", maxOf(legacy.optLong("revision"), cloudPrefs.getLong("revision", 0L)))
                editor.putString(key(account), legacy.toString())
                    .putString("legacy_workspace_v1", guest)
                    .remove(key(null))
            }
        }
        editor.apply()
    }

    fun load(account: String?): PersistedWorkspace? {
        migrateLegacyWorkspace()
        val raw = prefs.getString(key(account), null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val state = StateCodec.decode(root.getJSONObject("state").toString()) ?: return@runCatching null
            PersistedWorkspace(
                state = state,
                pending = root.optBoolean("pending", false),
                revision = root.optLong("revision", 0L),
            )
        }.getOrNull()
    }

    fun save(account: String?, workspace: PersistedWorkspace) {
        val payload = JSONObject().apply {
            put("state", JSONObject(StateCodec.encode(workspace.state)))
            put("pending", workspace.pending)
            put("revision", workspace.revision)
        }
        prefs.edit().putString(key(account), payload.toString()).apply()
    }

    fun clear(account: String?) {
        prefs.edit().remove(key(account)).apply()
    }

    private fun key(account: String?): String = "pomodoro.${account ?: "guest"}.v1"

    private companion object {
        const val KEY_DEVICE = "pomodoro.device"
    }
}
