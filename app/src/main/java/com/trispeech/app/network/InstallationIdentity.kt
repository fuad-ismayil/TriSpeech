package com.trispeech.app.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Anonymous, non-hardware device identity. It deliberately contains no advertising or hardware ID. */
class InstallationIdentity(context: Context) {
    private val prefs = context.getSharedPreferences("anonymous_installation", Context.MODE_PRIVATE)
    val installationId: String = prefs.getString(INSTALLATION_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(INSTALLATION_ID, it).apply() }

    private val alias = "trispeech-installation-$installationId"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    init {
        if (!keyStore.containsAlias(alias)) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(
                    KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
            }.generateKeyPair()
        }
    }

    val publicKey: String
        get() = Base64.encodeToString(
            keyStore.getCertificate(alias).publicKey.encoded,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun sign(value: String): String {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keyStore.getKey(alias, null) as java.security.PrivateKey)
        signature.update(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(signature.sign(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    companion object {
        private const val INSTALLATION_ID = "installation_id"

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
