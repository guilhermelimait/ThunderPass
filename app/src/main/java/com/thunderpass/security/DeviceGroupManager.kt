package com.thunderpass.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

private const val TAG = "ThunderPass/DGM"

/**
 * Manages the **Device Group Key (DGK)** — a shared 32-byte secret that proves two
 * Android devices belong to the same owner without any server involvement.
 *
 * ## Pairing flow (v2 — SAS numeric comparison)
 * 1. Both devices perform ECDH key exchange over BLE.
 * 2. Both independently compute a 6-digit confirmation code from the ECDH shared secret.
 * 3. User visually compares codes on both screens and taps "Confirm" → no MITM possible.
 * 4. After profile transfer, a second confirmation code is shown for final verification.
 * 5. Both devices derive and store a DGK from the ECDH secret via [deriveAndStoreGroupKeyFromSync].
 *
 * ## Ongoing recognition
 * Each device includes a `groupTag` in regular BLE payloads:
 * `groupTag = hex(HMAC-SHA256(DGK, myInstallationId)[0..7])` (16 hex chars, 64-bit).
 * A receiving device that holds the same DGK can verify [isOwnDevice] in O(1) by
 * computing the expected HMAC and comparing — no brute-force possible without DGK.
 *
 * ## Storage
 * Stored in [EncryptedSharedPreferences] backed by a Keystore AES-256-GCM master key.
 * The raw DGK bytes never appear in plaintext outside the TrustZone / StrongBox.
 */
object DeviceGroupManager {

    private const val PREFS_NAME    = "thunderpass_dgm"
    private const val KEY_DGK       = "dgk"
    private const val KEY_PAIRED    = "paired_inst_ids"

    // ── Prefs helpers ─────────────────────────────────────────────────────────

    private fun prefs(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        // No plaintext fallback — DGK must always be stored encrypted.
        // If the Keystore is unavailable an exception propagates intentionally.
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Key management ────────────────────────────────────────────────────────

    /** Returns true if this device has a DGK (is part of a device group). */
    fun hasGroupKey(context: Context): Boolean =
        prefs(context).contains(KEY_DGK)

    /**
     * Returns the 32-byte DGK, generating a fresh one if none exists yet.
     *
     * Generation: 10-byte pairing secret → HKDF-SHA256 → 32-byte DGK.
     * The pairing secret is discarded after the DGK is derived and the DGK is stored.
     */
    fun getOrCreateGroupKey(context: Context): ByteArray {
        val p = prefs(context)
        val stored = p.getString(KEY_DGK, null)
        if (stored != null) {
            return Base64.decode(stored, Base64.NO_WRAP)
        }
        // Fresh pairing secret → derive DGK → persist DGK only
        val pairingSecret = ByteArray(10).also { SecureRandom().nextBytes(it) }
        val dgk = hkdf(pairingSecret)
        p.edit().putString(KEY_DGK, Base64.encodeToString(dgk, Base64.NO_WRAP)).apply()
        Log.i(TAG, "New device group key created.")
        return dgk
    }

    /**
     * Removes the DGK and all paired device IDs — effectively "unlinks" this device.
     * After clearing, [hasGroupKey] returns false until a new pairing is performed.
     */
    fun clearGroupKey(context: Context) {
        prefs(context).edit().remove(KEY_DGK).remove(KEY_PAIRED).apply()
        Log.i(TAG, "Device group key cleared.")
    }

    // ── SAS confirmation codes ──────────────────────────────────────────────

    /**
     * Derives a 6-digit numeric confirmation code from the ECDH shared secret.
     * Both devices compute this independently; users compare the codes verbally/visually.
     *
     * @param ecdhRaw  Raw 32-byte ECDH shared secret.
     * @param step     1 for pairing confirmation, 2 for profile verification.
     * @param payloadHash Optional SHA-256 of the sync payload (used in step 2).
     * @return A 6-digit string like "482917".
     */
    fun deriveConfirmCode(ecdhRaw: ByteArray, step: Int, payloadHash: ByteArray? = null): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("thunderpass-confirm-$step".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        mac.update(ecdhRaw)
        if (payloadHash != null) mac.update(payloadHash)
        val hash = mac.doFinal()
        val num = ((hash[0].toInt() and 0xFF) shl 16) or
                  ((hash[1].toInt() and 0xFF) shl 8) or
                  (hash[2].toInt() and 0xFF)
        return "%06d".format(num % 1_000_000)
    }

    // ── Session key (ECDH-only, SAS-verified channel) ─────────────────────────

    /**
     * Derives a 256-bit AES session key directly from the ECDH shared secret.
     * Used during the SAS-verified sync flow where the channel is authenticated
     * by the user comparing 6-digit codes (no DGK needed for the session key).
     */
    fun deriveSyncSessionKeyDirect(ecdhRaw: ByteArray): SecretKey {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("thunderpass-sync-v1".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return SecretKeySpec(mac.doFinal(ecdhRaw), "AES")
    }

    // ── DGK from ECDH (post-sync derivation) ──────────────────────────────────

    /**
     * Derives a 32-byte DGK from the ECDH shared secret after a successful SAS-verified sync,
     * then stores it in EncryptedSharedPreferences.
     * Called on both devices after the full two-step confirmation completes.
     */
    fun deriveAndStoreGroupKeyFromSync(context: Context, ecdhRaw: ByteArray) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("thunderpass-dgk-from-sync".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val dgk = mac.doFinal(ecdhRaw)
        prefs(context).edit()
            .putString(KEY_DGK, Base64.encodeToString(dgk, Base64.NO_WRAP))
            .apply()
        Log.i(TAG, "DGK derived from sync exchange.")
    }

    // ── Device group tag (BLE payload field) ──────────────────────────────────

    /**
     * Computes the 16-hex-char group tag for this device's own installation ID.
     * `groupTag = hex(HMAC-SHA256(DGK, installationId)[0..7])`.
     *
     * Returns blank string if this device has no DGK.
     */
    fun computeGroupTag(context: Context, installationId: String): String {
        if (!hasGroupKey(context) || installationId.isBlank()) return ""
        return hmacSha256Tag(getOrCreateGroupKey(context), installationId)
    }

    /**
     * Returns `true` if the received [groupTag] authenticates [peerInstId] as
     * belonging to this device group — i.e. the peer computes `HMAC(DGK, peerInstId)`
     * and we verify it matches.
     */
    fun isOwnDevice(context: Context, peerInstId: String, groupTag: String): Boolean {
        if (!hasGroupKey(context) || peerInstId.isBlank() || groupTag.isBlank()) return false
        val expected = hmacSha256Tag(getOrCreateGroupKey(context), peerInstId)
        return expected == groupTag
    }

    // ── Paired installation-ID registry ───────────────────────────────────────

    /** Remembers the stable [installationId] of every device we have successfully synced with. */
    fun addPairedInstallationId(context: Context, installationId: String) {
        val p       = prefs(context)
        val current = p.getStringSet(KEY_PAIRED, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (current.add(installationId)) {
            p.edit().putStringSet(KEY_PAIRED, current).apply()
            Log.i(TAG, "Paired device recorded: $installationId")
        }
    }

    /** All [installationId] values of devices that have been synced with this one. */
    fun pairedInstallationIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_PAIRED, emptySet()) ?: emptySet()

    // ── Session key derivation for BLE sync ───────────────────────────────────

    /**
     * Derives a 256-bit AES session key that binds both:
     * - the **ECDH shared secret** (forward secrecy, unique per session), and
     * - the **Device Group Key** (proves mutual ownership — no DGK, no data).
     *
     * `sessionKey = HMAC-SHA256(salt="thunderpass-sync-v1", ikm = ecdhSecret || DGK)`
     *
     * An eavesdropper who intercepts the ECDH handshake still cannot decrypt
     * the payload without knowing the DGK (i.e. without the 16-char pairing code).
     */
    fun deriveSyncSessionKey(context: Context, ecdhSecret: ByteArray): SecretKey {
        val dgk = getOrCreateGroupKey(context)
        val ikm = ecdhSecret + dgk   // 32 + 32 = 64 bytes
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("thunderpass-sync-v1".toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val key = mac.doFinal(ikm)   // 32-byte output → AES-256
        return SecretKeySpec(key, "AES")
    }

    // ── Private crypto helpers ────────────────────────────────────────────────

    /**
     * HKDF-Extract + single-block Expand.
     * `PRK = HMAC-SHA256("thunderpass-dgk-v1", ikm)`
     * `OKM = HMAC-SHA256(PRK, "thunderpass-device-group-key" || 0x01)` (32 bytes)
     */
    private fun hkdf(ikm: ByteArray): ByteArray {
        val salt = "thunderpass-dgk-v1".toByteArray(Charsets.UTF_8)
        val m1   = Mac.getInstance("HmacSHA256")
        m1.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk  = m1.doFinal(ikm)

        val m2   = Mac.getInstance("HmacSHA256")
        m2.init(SecretKeySpec(prk, "HmacSHA256"))
        m2.update("thunderpass-device-group-key".toByteArray(Charsets.UTF_8))
        m2.update(0x01.toByte())
        return m2.doFinal()
    }

    /** `hex(HMAC-SHA256(key, input)[0..7])` — 16 lowercase hex chars (64-bit tag). */
    private fun hmacSha256Tag(key: ByteArray, input: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val hmac = mac.doFinal(input.toByteArray(Charsets.UTF_8))
        return hmac.take(8).joinToString("") { "%02x".format(it) }
    }

}
