package com.thunderpass.security

import android.content.Context
import android.util.Log
import com.thunderpass.data.db.dao.MyProfileDao
import com.thunderpass.retro.RetroAuthManager
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ThunderPass/PairedSync"
private const val PREF_NAME = "thunderpass_paired_sync"
private const val SYNC_COOLDOWN_MS = 15L * 60 * 1000 // 15 minutes
private const val KEY_PAIRED_DEVICES = "paired_devices_json"

/**
 * Handles periodic delta-merge of profile content between paired devices discovered via BLE.
 *
 * When [GattClient] detects a paired device via groupTag, it calls [shouldSync] to check the
 * cooldown and then [mergeIncomingProfile] to apply field-level merge rules:
 * - Profile text fields (displayName, greeting, avatar, retroUsername, etc.) —
 *   peer wins when the peer's [updatedAt] timestamp is newer than the local profile's.
 *   NOTE: raApiKey is intentionally excluded — credentials are only transferred via the
 *   explicit SAS-verified SyncGattServer/SyncGattClient flow, never via ambient BLE payloads.
 * - privacyMode — peer wins when newer (unified across devices).
 * - voltsTotal — max(local, peer) so neither device loses energy across devices.
 * - badgesJson / stickersJson — union of both sets, so earned content is never lost.
 * - Device-specific fields (installationId, payloadPublicKey, deviceType) are always kept
 *   from the local profile.
 */
object PairedSyncManager {

    fun shouldSync(context: Context, peerInstId: String): Boolean {
        val last = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong("last_sync_$peerInstId", 0L)
        return System.currentTimeMillis() - last > SYNC_COOLDOWN_MS
    }

    private fun recordSync(context: Context, peerInstId: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putLong("last_sync_$peerInstId", System.currentTimeMillis()).apply()
    }

    suspend fun mergeIncomingProfile(
        context: Context,
        profileDao: MyProfileDao,
        peerInstId: String,
        data: JSONObject,
    ) {
        val local = profileDao.get() ?: return
        val peerUpdatedAt = data.optLong("updatedAt", 0L)
        val useNewerFields = peerUpdatedAt > local.updatedAt
        val avatar = data.optJSONObject("avatar")

        // Badges and stickers: union merge — never lose earned content across paired devices
        val localBadges = local.badgesJson.split(",").filter(String::isNotBlank).toMutableSet()
        val peerBadges  = data.optString("syncBadges", "").split(",").filter(String::isNotBlank)
        localBadges.addAll(peerBadges)

        val localStickers = local.stickersJson.split(",").filter(String::isNotBlank).toMutableSet()
        val peerStickers  = data.optString("syncStickers", "").split(",").filter(String::isNotBlank)
        localStickers.addAll(peerStickers)

        val merged = local.copy(
            displayName   = if (useNewerFields) data.optString("displayName",   local.displayName)   else local.displayName,
            greeting      = if (useNewerFields) data.optString("greeting",      local.greeting)      else local.greeting,
            avatarKind    = if (useNewerFields && avatar != null) avatar.optString("kind",  local.avatarKind)  else local.avatarKind,
            avatarColor   = if (useNewerFields && avatar != null) avatar.optString("color", local.avatarColor) else local.avatarColor,
            avatarSeed    = if (useNewerFields && avatar != null) avatar.optString("seed",  local.avatarSeed)  else local.avatarSeed,
            retroUsername = if (useNewerFields) data.optString("retroUsername", local.retroUsername) else local.retroUsername,
            raApiKey      = local.raApiKey,  // credentials synced only via explicit SyncGattServer/SyncGattClient flow
            ghostGame     = if (useNewerFields) data.optString("ghostGame",     local.ghostGame)     else local.ghostGame,
            ghostScore    = if (useNewerFields) data.optLong("ghostScore",      local.ghostScore)    else local.ghostScore,
            country       = if (useNewerFields) data.optString("country",        local.country)       else local.country,
            city          = if (useNewerFields) data.optString("city",           local.city)          else local.city,
            privacyMode   = if (useNewerFields) data.optBoolean("syncPrivacyMode", local.privacyMode) else local.privacyMode,
            updatedAt     = if (useNewerFields) peerUpdatedAt else local.updatedAt,
            voltsTotal    = maxOf(local.voltsTotal, data.optLong("volts", 0L)),
            badgesJson    = localBadges.joinToString(","),
            stickersJson  = localStickers.joinToString(","),
            // Never sync: installationId, payloadPublicKey, deviceType (device-specific)
        )

        if (merged != local) {
            profileDao.upsert(merged)
            Log.i(TAG, "Profile delta-merged from paired device $peerInstId " +
                "(peerUpdatedAt=$peerUpdatedAt, volts merged → ${merged.voltsTotal})")

            // Mirror RA credentials to EncryptedSharedPreferences so
            // RetroAuthManager.hasCredentials() stays in sync.
            if (merged.retroUsername != local.retroUsername) {
                if (merged.retroUsername.isNotBlank()) {
                    val auth = RetroAuthManager.getInstance(context)
                    auth.saveCredentials(apiUser = merged.retroUsername)
                    Log.i(TAG, "RA username mirrored to RetroAuthManager via delta sync.")
                }
            }
        }
        recordSync(context, peerInstId)
    }

    // ── Persistent paired-device metadata ─────────────────────────────────────

    data class PairedDeviceInfo(
        val instId: String,
        val displayName: String,
        val deviceType: String,
        val lastSeenMs: Long,
    )

    /** Persists or updates metadata for a paired device (called on every BLE sighting). */
    fun recordPairedDevice(context: Context, instId: String, displayName: String, deviceType: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_PAIRED_DEVICES, "[]")) } catch (_: Exception) { JSONArray() }

        // Update existing entry or append new
        var found = false
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("instId") == instId) {
                obj.put("displayName", displayName)
                obj.put("deviceType", deviceType)
                obj.put("lastSeenMs", System.currentTimeMillis())
                found = true
                break
            }
        }
        if (!found) {
            arr.put(JSONObject().apply {
                put("instId", instId)
                put("displayName", displayName)
                put("deviceType", deviceType)
                put("lastSeenMs", System.currentTimeMillis())
            })
        }
        prefs.edit().putString(KEY_PAIRED_DEVICES, arr.toString()).apply()
    }

    /** Returns all known paired devices ordered by most recently seen. */
    fun pairedDevices(context: Context): List<PairedDeviceInfo> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_PAIRED_DEVICES, "[]")) } catch (_: Exception) { return emptyList() }
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            PairedDeviceInfo(
                instId      = obj.optString("instId", ""),
                displayName = obj.optString("displayName", ""),
                deviceType  = obj.optString("deviceType", ""),
                lastSeenMs  = obj.optLong("lastSeenMs", 0L),
            )
        }.sortedByDescending { it.lastSeenMs }
    }

    /** Removes a paired device by instId and clears its sync timestamp. */
    fun removePairedDevice(context: Context, instId: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = try { JSONArray(prefs.getString(KEY_PAIRED_DEVICES, "[]")) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("instId") != instId) updated.put(obj)
        }
        prefs.edit()
            .putString(KEY_PAIRED_DEVICES, updated.toString())
            .remove("last_sync_$instId")
            .apply()
    }
}
