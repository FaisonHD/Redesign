package de.simonhub.data

import android.content.Context
import android.security.keystore.*
import android.util.Base64
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {
    private val prefs=context.getSharedPreferences("connection-secrets",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey("simon-hub-bridge",null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("simon-hub-bridge",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun setToken(value: String) {
        if(value.isBlank()) {check(prefs.edit().remove("token").commit());return}
        val c=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        check(prefs.edit().putString("token",Base64.encodeToString(c.iv+c.doFinal(value.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)).commit())
    }
    fun token(): String {
        val raw=prefs.getString("token",null) ?: return "";val bytes=Base64.decode(raw,Base64.NO_WRAP)
        val c=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
        return String(c.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }
}
