package com.thunderpass.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Manages a device-bound P-256 signing key pair stored in the Android Keystore.
 *
 * ## Purpose
 * Provides cryptographic proof that a GATT payload was produced by this specific
 * device, not by someone who merely copied the installationId.
 *
 * ## Key lifecycle
 * - The key pair is generated once on first call to [ensureKeyPairAndGetPublicKey] and
 *   stored in the Android Keystore (hardware-backed TEE on most devices).
 * - The **private key never leaves the Keystore** — it cannot be extracted or exported.
 * - The **public key** is shared with peers via the GATT payload so they can verify
 *   signatures without any server-side function.
 * - If the app is reinstalled or the device is factory-reset, a new key pair is generated
 *   automatically on next launch.
 *
 * ## Signed message format
 * `"thunderpass:v1:{installationId}:{rotatingId}:{ts300}"`
 * where `ts300 = floor(epochSeconds / 300)` — a new signature is required every 5 minutes,
 * preventing indefinite replay attacks.
 */
object PayloadSigner {

    private const val KEY_ALIAS = "thunderpass_payload_key"
    private const val PROVIDER  = "AndroidKeyStore"

    /**
     * Ensures the P-256 key pair exists in the Android Keystore.
     * Idempotent — safe to call on every launch.
     *
     * @return Base64url-encoded DER SubjectPublicKeyInfo of the public key,
     *         ready to share with peers as `profiles.public_key`.
     */
    fun ensureKeyPairAndGetPublicKey(): String {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER).apply {
                initialize(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                    )
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setKeySize(256)
                        .build()
                )
                generateKeyPair()
            }
        }
        val cert = ks.getCertificate(KEY_ALIAS)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(cert.publicKey.encoded)
    }

    /**
     * Signs [message] with the device's Keystore private key.
     *
     * @return Base64url-encoded DER ECDSA signature, or `null` if the key is unavailable.
     */
    fun sign(message: String): String? = runCatching {
        val ks = KeyStore.getInstance(PROVIDER).also { it.load(null) }
        val privateKey = ks.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
        val sig = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(message.toByteArray(Charsets.UTF_8))
        }
        Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign())
    }.getOrNull()

    /**
     * Verifies [sigBase64] against [message] using the given Base64url-encoded
     * DER public key (X.509 SubjectPublicKeyInfo format).
     *
     * @return `true` if the signature is valid; `false` on any mismatch or error.
     */
    fun verify(message: String, sigBase64: String, pubKeyBase64: String): Boolean =
        runCatching {
            val pubKeyBytes = Base64.getUrlDecoder().decode(pubKeyBase64)
            val pubKey      = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_EC)
                .generatePublic(X509EncodedKeySpec(pubKeyBytes))
            val sigBytes    = Base64.getUrlDecoder().decode(sigBase64)
            Signature.getInstance("SHA256withECDSA").apply {
                initVerify(pubKey)
                update(message.toByteArray(Charsets.UTF_8))
            }.verify(sigBytes)
        }.getOrDefault(false)

    /**
     * Builds the canonical message string to sign or verify for a given exchange.
     *
     * Binds together:
     * - `installationId` — identifies the claiming device
     * - `rotatingId` — ties the signature to this specific BLE rotation window
     * - `ts300`     — `floor(epochSeconds / 300)` — expires every 5 minutes
     */
    fun signedPayload(userId: String, rotatingId: String, epochSeconds: Long): String {
        val ts300 = epochSeconds / 300
        return "thunderpass:v1:$userId:$rotatingId:$ts300"
    }

    /**
     * Builds the canonical message string for BLE mutual authentication (protocol v2).
     *
     * Binds together:
     * - `rotatingId` — ties the signature to this BLE rotation window
     * - `ts300`      — `floor(epochSeconds / 300)` — expires every 5 minutes
     *
     * Deliberately excludes userId/installationId so this format is safe for
     * privacy-mode devices too: every ThunderPass device can be authenticated
     * without revealing a stable long-term identifier.
     */
    fun signedPayload(rotatingId: String, epochSeconds: Long): String {
        val ts300 = epochSeconds / 300
        return "thunderpass:v2:$rotatingId:$ts300"
    }
}
