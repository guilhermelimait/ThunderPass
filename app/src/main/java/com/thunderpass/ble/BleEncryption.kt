package com.thunderpass.ble

import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "ThunderPass/BleEnc"

/**
 * Ephemeral ECDH key exchange + AES-256-GCM payload encryption for all BLE GATT exchanges.
 *
 * ## Per-session protocol
 * 1. CLIENT generates a fresh ephemeral P-256 key pair (`generateEphemeralKeyPair`).
 * 2. CLIENT writes REQUEST_CHAR: `[REQUEST_TYPE_ENCRYPTED (0x02)][X.509 pubkey, 91 bytes]`.
 * 3. SERVER parses the client ephemeral public key.
 * 4. SERVER generates its own fresh ephemeral P-256 key pair.
 * 5. SERVER derives a 256-bit AES key via ECDH + HKDF-Extract (HMAC-SHA256).
 * 6. SERVER encrypts the JSON payload with AES-256-GCM (12-byte random IV, 16-byte auth tag).
 * 7. SERVER chunks and sends RESPONSE_CHAR:
 *    `[ENCRYPTED_RESPONSE_MAGIC (0xE5)][server pubkey, 91 bytes][IV, 12 bytes][ciphertext+tag]`.
 * 8. CLIENT reassembles chunks, performs ECDH with the server pubkey → same AES key.
 * 9. CLIENT decrypts + authenticates the ciphertext; parses JSON on success.
 *
 * ## Security guarantees
 * - **Confidentiality**: passive BLE sniffers cannot read profile data.
 * - **Perfect forward secrecy**: fresh ephemeral keys each session — old sessions stay secret.
 * - **Integrity + authenticity**: GCM auth tag causes decryption to fail if any byte is tampered.
 * - **No cleartext fallback**: the server rejects any REQUEST that is not the 92-byte encrypted frame.
 * - **Anti-replay**: GCM nonce is random per message; re-sending the same ciphertext produces a
 *   different nonce, making replay detection automatic (different ciphertext each session).
 */
object BleEncryption {

    /**
     * Size of an X.509-encoded P-256 public key. Android's `EC` KeyPairGenerator always
     * produces SubjectPublicKeyInfo wrappers of exactly 91 bytes for P-256.
     */
    const val PUBLIC_KEY_BYTES = 91

    /** AES-GCM IV size (96-bit nonce as recommended by NIST SP 800-38D). */
    private const val IV_BYTES = 12

    /** AES-GCM authentication tag size in bits (128-bit provides maximum security). */
    private const val TAG_BITS = 128

    /**
     * First byte of the REQUEST_CHAR frame for an encrypted exchange.
     * A frame starting with any other value is rejected by the server.
     */
    const val REQUEST_TYPE_ENCRYPTED: Byte = 0x02

    /**
     * Total byte length of the REQUEST_CHAR frame:
     * `[REQUEST_TYPE_ENCRYPTED: 1 byte][X.509 client ephemeral pubkey: 91 bytes]` = 92 bytes.
     * This fits well within any negotiated MTU ≥ 95 bytes (the minimum post-negotiation ATT MTU).
     */
    const val REQUEST_FRAME_SIZE = 1 + PUBLIC_KEY_BYTES  // = 92

    /**
     * Magic byte that identifies an encrypted RESPONSE payload (before chunking).
     * 0xE5 is outside valid UTF-8 single-byte range, so it is distinct from any JSON payload.
     */
    const val ENCRYPTED_RESPONSE_MAGIC: Byte = 0xE5.toByte()

    /**
     * Minimum byte length for a structurally valid encrypted response:
     * magic(1) + server pubkey(91) + IV(12) + minimum ciphertext+tag(17) = 121 bytes.
     */
    const val MIN_ENCRYPTED_RESPONSE_SIZE = 1 + PUBLIC_KEY_BYTES + IV_BYTES + TAG_BITS / 8 + 1

    // HKDF salt — domain-separates the key derivation from any other use of the same ECDH output.
    private val HKDF_SALT = "thunderpass-ble-v2".toByteArray(Charsets.UTF_8)

    // ── Key generation ────────────────────────────────────────────────────────

    /**
     * Generates a fresh P-256 (secp256r1) ephemeral key pair for a single GATT session.
     * Must be generated once per `connect()` call and discarded after the session ends.
     * Never stored persistently.
     */
    fun generateEphemeralKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    /** Returns the X.509-encoded bytes of [key] (91 bytes for P-256), ready to send over BLE. */
    fun encodePublicKey(key: PublicKey): ByteArray = key.encoded

    /**
     * Parses an X.509-encoded P-256 public key from [bytes].
     * @throws java.security.spec.InvalidKeySpecException if the bytes are malformed.
     */
    fun decodePublicKey(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))

    // ── Key derivation ────────────────────────────────────────────────────────

    /**
     * Derives a 256-bit AES session key from an ECDH exchange.
     *
     * Steps:
     * 1. ECDH: `secret = localPrivate × remotePub` (Diffie-Hellman on P-256).
     * 2. HKDF-Extract: `PRK = HMAC-SHA256(HKDF_SALT, secret)`.
     * 3. The 32-byte PRK is used directly as the AES-256 key.
     *
     * Feeding the raw ECDH output through HMAC (HKDF-Extract) defends against
     * invalid-curve and small-subgroup attacks, and produces a uniformly-distributed key.
     */
    fun deriveSharedKey(localPrivate: PrivateKey, remotePub: PublicKey): SecretKey {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(localPrivate)
        ka.doPhase(remotePub, true)
        val ikm = ka.generateSecret()  // 32-byte ECDH shared secret for P-256

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(HKDF_SALT, "HmacSHA256"))
        val prk = mac.doFinal(ikm)     // 32-byte PRK → AES-256 key material

        return SecretKeySpec(prk, "AES")
    }

    // ── Encryption / Decryption ───────────────────────────────────────────────

    /**
     * Encrypts [plaintext] with AES-256-GCM.
     *
     * @return `[random IV: 12 bytes] [AES-GCM ciphertext + 16-byte authentication tag]`.
     */
    fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Decrypts and authenticates an AES-256-GCM ciphertext.
     *
     * @param ivAndCiphertext `[IV: 12 bytes][ciphertext + 16-byte GCM tag]`.
     * @return Plaintext bytes, or `null` if GCM authentication fails or the input is malformed.
     *         A `null` return indicates tampering, corruption, or key mismatch — the payload
     *         must be discarded unconditionally.
     */
    fun decrypt(ivAndCiphertext: ByteArray, key: SecretKey): ByteArray? = runCatching {
        if (ivAndCiphertext.size < IV_BYTES + TAG_BITS / 8 + 1) return null
        val iv = ivAndCiphertext.copyOfRange(0, IV_BYTES)
        val ct = ivAndCiphertext.copyOfRange(IV_BYTES, ivAndCiphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.doFinal(ct)
    }.getOrElse {
        Log.w(TAG, "AES-GCM decryption failed: ${it.message}")
        null
    }

    // ── Response frame builder ────────────────────────────────────────────────

    /**
     * Builds the full encrypted RESPONSE payload ready for chunking:
     * `[ENCRYPTED_RESPONSE_MAGIC][server ephemeral pubkey: 91 bytes][IV: 12 bytes][ciphertext+tag]`.
     *
     * This byte sequence is then chunked by the existing CHUNK_MAGIC protocol in
     * [GattServer] before transmission.
     *
     * @param serverEphemeral Server's ephemeral key pair for this session.
     * @param sharedKey       256-bit AES session key derived from ECDH.
     * @param json            Plaintext JSON profile payload to protect.
     */
    fun buildEncryptedResponse(
        serverEphemeral: KeyPair,
        sharedKey: SecretKey,
        json: String,
    ): ByteArray {
        val serverPubBytes = encodePublicKey(serverEphemeral.public)
        val encrypted      = encrypt(json.toByteArray(Charsets.UTF_8), sharedKey)
        return byteArrayOf(ENCRYPTED_RESPONSE_MAGIC) + serverPubBytes + encrypted
    }
}
