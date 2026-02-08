package com.tds.binarystars.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "binarystars_device_key"
    private const val RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"

    /**
     * Loads or creates the device RSA key pair in the Android Keystore.
     */
    fun getOrCreateKeyPair(): KeyPair {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry
        if (existing != null) {
            return KeyPair(existing.certificate.publicKey, existing.privateKey)
        }

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setKeySize(2048)
            .build()

        generator.initialize(spec)
        return generator.generateKeyPair()
    }

    /**
     * Returns the device public key encoded as base64.
     */
    fun getPublicKeyBase64(): String {
        val keyPair = getOrCreateKeyPair()
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Returns the public key algorithm identifier.
     */
    fun getPublicKeyAlgorithm(): String {
        return "RSA"
    }

    /**
     * Encrypts input data to the output stream and returns a JSON envelope.
     */
    fun encryptToStream(
        input: InputStream,
        output: OutputStream,
        senderDeviceId: String,
        targetDeviceId: String,
        targetPublicKeyBase64: String
    ): String {
        val keyPair = getOrCreateKeyPair()
        val senderPublicKey = keyPair.public
        val targetPublicKey = decodePublicKey(targetPublicKeyBase64)

        val aesKey = generateAesKey()
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))

        CipherOutputStream(output, cipher).use { cipherOut ->
            input.copyTo(cipherOut)
        }

        val wrappedForSender = wrapKey(aesKey, senderPublicKey)
        val wrappedForTarget = wrapKey(aesKey, targetPublicKey)

        val envelope = JSONObject()
        envelope.put("dataAlgorithm", "AES-GCM")
        envelope.put("keyAlgorithm", "RSA-OAEP-256")
        envelope.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        envelope.put("senderKeyId", senderDeviceId)
        envelope.put("targetKeyId", targetDeviceId)
        envelope.put("wrappedKeyForSender", Base64.encodeToString(wrappedForSender, Base64.NO_WRAP))
        envelope.put("wrappedKeyForTarget", Base64.encodeToString(wrappedForTarget, Base64.NO_WRAP))
        return envelope.toString()
    }

    /**
     * Decrypts input data using the provided envelope JSON.
     */
    fun decryptToStream(input: InputStream, output: OutputStream, envelopeJson: String) {
        val envelope = JSONObject(envelopeJson)
        val iv = Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)
        val wrappedKey = Base64.decode(envelope.getString("wrappedKeyForTarget"), Base64.NO_WRAP)

        val aesKeyBytes = unwrapKey(wrappedKey)
        val aesKey = SecretKeySpec(aesKeyBytes, "AES")

        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))

        CipherInputStream(input, cipher).use { cipherIn ->
            cipherIn.copyTo(output)
        }
    }

    private fun generateAesKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256)
        return keyGen.generateKey()
    }

    private fun wrapKey(key: SecretKey, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(key.encoded)
    }

    private fun unwrapKey(wrappedKey: ByteArray): ByteArray {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val privateKey = entry.privateKey
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(wrappedKey)
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        val spec = X509EncodedKeySpec(bytes)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePublic(spec)
    }
}
