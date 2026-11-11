package ai.yaho.hyundai.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import ai.yaho.hyundai.BuildConfig
import ai.yaho.hyundai.core.ExperimentVariant
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class InstallationIdentity(val installationId: String, val accessToken: String)

interface InstallationCredentialProvider {
    fun credentialsFor(variant: ExperimentVariant): InstallationIdentity?
}


class ReleaseCredentialProvider : InstallationCredentialProvider {
    override fun credentialsFor(variant: ExperimentVariant): InstallationIdentity? = null
}


class DebugInstallationCredentialStore(context: Context) : InstallationCredentialProvider {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun credentialsFor(variant: ExperimentVariant): InstallationIdentity? {
        if (!BuildConfig.DEBUG) return null
        val encoded = preferences.getString(tokenKey(variant), null) ?: return null
        val installationId = preferences.getString(idKey(variant), null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = bytes.firstOrNull()?.toInt()?.and(0xff) ?: return null
            require(ivSize in 12..16 && bytes.size > ivSize)
            val iv = bytes.copyOfRange(1, 1 + ivSize)
            val cipherText = bytes.copyOfRange(1 + ivSize, bytes.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, keyFor(variant), GCMParameterSpec(TAG_BITS, iv))
            InstallationIdentity(installationId, String(cipher.doFinal(cipherText), Charsets.UTF_8))
        }.getOrNull()
    }

    fun save(variant: ExperimentVariant, identity: InstallationIdentity) {
        check(BuildConfig.DEBUG) { "Local dev credentials are debug-only" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyFor(variant))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(identity.accessToken.toByteArray(Charsets.UTF_8))
        val packed = byteArrayOf(iv.size.toByte()) + iv + encrypted
        preferences.edit()
            .putString(idKey(variant), identity.installationId)
            .putString(tokenKey(variant), Base64.encodeToString(packed, Base64.NO_WRAP))
            .apply()
    }

    fun clear(variant: ExperimentVariant) {
        preferences.edit().remove(idKey(variant)).remove(tokenKey(variant)).apply()
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(alias(variant))
        }
    }

    private fun keyFor(variant: ExperimentVariant): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias(variant), null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias(variant),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun alias(variant: ExperimentVariant) = "yaho_hyundai_installation_${variant.wireKey}"
    private fun idKey(variant: ExperimentVariant) = "installation_id_${variant.wireKey}"
    private fun tokenKey(variant: ExperimentVariant) = "encrypted_access_token_${variant.wireKey}"

    companion object {
        private const val PREFS_NAME = "yaho_hyundai_debug_installations"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
