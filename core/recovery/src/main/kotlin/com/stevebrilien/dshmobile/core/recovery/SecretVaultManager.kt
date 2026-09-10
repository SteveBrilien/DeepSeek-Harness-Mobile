package com.stevebrilien.dshmobile.core.recovery

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Encrypted recovery wrapper for DSH-owned secrets.
 *
 * The vault master key has two wrappers:
 * - Android Keystore for unattended backups while the app remains installed;
 * - a PBKDF2 recovery-password wrapper that survives app uninstall.
 *
 * No plaintext credential is written to shared Recovery Vault storage.
 */
class SecretVaultManager(
    context: Context,
    private val vault: RecoveryVault = RecoveryVault(context.applicationContext),
) {
    companion object {
        private const val SCHEMA = 1
        private const val KEY_ALIAS = "dsh-mobile-recovery-master-v1"
        private const val PBKDF2_ITERATIONS = 310_000
        private const val CREDENTIAL_FILE = "dsh-credentials.enc.json"
        private const val SSH_IDENTITY_FILE = "ssh-identity.enc.json"
    }

    data class Status(
        val configured: Boolean,
        val deviceUnlocked: Boolean,
        val encryptedCredentialsPresent: Boolean,
        val encryptedSshIdentityPresent: Boolean,
    )

    private val appContext = context.applicationContext
    private val secureRandom = SecureRandom()

    fun status(): Status {
        val dir = encryptedDir()
        return Status(
            configured = metadataFile().isFile,
            deviceUnlocked = loadDeviceMasterKey().isSuccess,
            encryptedCredentialsPresent = File(dir, CREDENTIAL_FILE).isFile,
            encryptedSshIdentityPresent = File(dir, SSH_IDENTITY_FILE).isFile,
        )
    }

    fun initialize(recoveryPassword: CharArray): Result<Unit> = runCatching {
        requireStrongEnough(recoveryPassword)
        val recovery = vault.ensureLayout().getOrThrow()
        check(recovery.persistentAcrossUninstall) { "请先授权跨卸载持久存储，再设置恢复密码" }
        check(!metadataFile().exists()) { "恢复密钥已初始化；请使用解锁流程" }
        val master = ByteArray(32).also(secureRandom::nextBytes)
        writeMetadata(master, recoveryPassword)
        writeDeviceWrapper(master)
        master.fill(0)
    }

    fun unlock(recoveryPassword: CharArray): Result<Unit> = runCatching {
        requireStrongEnough(recoveryPassword)
        val master = unwrapRecoveryMaster(recoveryPassword)
        try {
            writeDeviceWrapper(master)
        } finally {
            master.fill(0)
        }
    }

    fun backupDshCredentials(source: File): Result<File?> = runCatching {
        if (!source.isFile) return@runCatching null
        val master = loadDeviceMasterKey().getOrThrow()
        val plaintext = source.readBytes()
        try {
            val sealed = encrypt(master, plaintext)
            val json = JSONObject()
                .put("schemaVersion", SCHEMA)
                .put("updatedAtEpochMillis", System.currentTimeMillis())
                .put("sourceSha256", sha256(plaintext))
                .put("iv", b64(sealed.iv))
                .put("ciphertext", b64(sealed.ciphertext))
            val target = File(encryptedDir(), CREDENTIAL_FILE)
            atomicWrite(target, json.toString(2).toByteArray(Charsets.UTF_8))
            target
        } finally {
            plaintext.fill(0)
            master.fill(0)
        }
    }

    /** Encrypt the complete DSH-home .ssh directory into the cross-uninstall vault. */
    fun backupSshIdentity(sourceDir: File): Result<File?> = runCatching {
        if (!sourceDir.isDirectory) return@runCatching null
        val root = sourceDir.canonicalFile
        val files = sourceDir.walkTopDown()
            .filter { file ->
                file.isFile &&
                    !java.nio.file.Files.isSymbolicLink(file.toPath()) &&
                    runCatching { file.canonicalPath.startsWith(root.path + File.separator) }.getOrDefault(false)
            }
            .toList()
        // Never replace a good cross-uninstall SSH backup with an empty archive merely
        // because an editor/cleanup operation temporarily removed the local files. An
        // explicit future "forget SSH backup" action should own destructive tombstones.
        if (files.isEmpty()) return@runCatching null
        val plaintext = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                files.forEach { file ->
                    val relative = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
                    require(isSafeRelativePath(relative)) { "Unsafe SSH identity path: $relative" }
                    zip.putNextEntry(ZipEntry(relative).apply { time = file.lastModified() })
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
        val master = loadDeviceMasterKey().getOrThrow()
        try {
            val sealed = encrypt(master, plaintext)
            val json = JSONObject()
                .put("schemaVersion", SCHEMA)
                .put("kind", "ssh-identity")
                .put("updatedAtEpochMillis", System.currentTimeMillis())
                .put("entryCount", files.size)
                .put("sourceSha256", sha256(plaintext))
                .put("iv", b64(sealed.iv))
                .put("ciphertext", b64(sealed.ciphertext))
            val target = File(encryptedDir(), SSH_IDENTITY_FILE)
            atomicWrite(target, json.toString(2).toByteArray(Charsets.UTF_8))
            target
        } finally {
            plaintext.fill(0)
            master.fill(0)
        }
    }

    /** Restore encrypted SSH identity files without replacing files the user already has. */
    fun restoreSshIdentity(destinationDir: File, recoveryPassword: CharArray? = null): Result<Int> = runCatching {
        val encrypted = File(encryptedDir(), SSH_IDENTITY_FILE)
        if (!encrypted.isFile) return@runCatching 0
        val master = if (recoveryPassword != null) unwrapRecoveryMaster(recoveryPassword) else loadDeviceMasterKey().getOrThrow()
        try {
            val json = JSONObject(encrypted.readText())
            check(json.optInt("schemaVersion", -1) == SCHEMA && json.optString("kind") == "ssh-identity") {
                "不支持的 SSH 身份备份格式"
            }
            val plaintext = decrypt(master, b64d(json.getString("iv")), b64d(json.getString("ciphertext")))
            try {
                check(sha256(plaintext) == json.getString("sourceSha256")) { "SSH 身份恢复完整性校验失败" }
                check(destinationDir.exists() || destinationDir.mkdirs()) { "无法创建 SSH 身份目录" }
                Os.chmod(destinationDir.absolutePath, 0x1C0) // 0700
                val root = destinationDir.canonicalFile
                var restored = 0
                ZipInputStream(ByteArrayInputStream(plaintext)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) {
                            zip.closeEntry()
                            continue
                        }
                        val relative = entry.name.replace('\\', '/')
                        require(isSafeRelativePath(relative)) { "Unsafe SSH restore entry: $relative" }
                        val destination = File(root, relative).canonicalFile
                        check(destination.path.startsWith(root.path + File.separator)) { "SSH restore path escapes identity root" }
                        destination.parentFile?.let {
                            check(it.exists() || it.mkdirs())
                            runCatching { Os.chmod(it.absolutePath, 0x1C0) } // 0700
                        }
                        if (!destination.exists()) {
                            val bytes = zip.readBytes()
                            try {
                                atomicWrite(destination, bytes)
                                Os.chmod(destination.absolutePath, 0x180) // 0600, safe for all SSH files
                                restored += 1
                            } finally {
                                bytes.fill(0)
                            }
                        }
                        zip.closeEntry()
                    }
                }
                restored
            } finally {
                plaintext.fill(0)
            }
        } finally {
            master.fill(0)
        }
    }

    fun restoreDshCredentials(destination: File, recoveryPassword: CharArray? = null): Result<Boolean> = runCatching {
        val encrypted = File(encryptedDir(), CREDENTIAL_FILE)
        if (!encrypted.isFile) return@runCatching false
        val master = if (recoveryPassword != null) unwrapRecoveryMaster(recoveryPassword) else loadDeviceMasterKey().getOrThrow()
        try {
            val json = JSONObject(encrypted.readText())
            check(json.optInt("schemaVersion", -1) == SCHEMA) { "不支持的凭据备份格式" }
            val plaintext = decrypt(master, b64d(json.getString("iv")), b64d(json.getString("ciphertext")))
            try {
                check(sha256(plaintext) == json.getString("sourceSha256")) { "凭据恢复完整性校验失败" }
                destination.parentFile?.let { check(it.exists() || it.mkdirs()) }
                atomicWrite(destination, plaintext)
                Os.chmod(destination.absolutePath, 0x180) // 0600
                true
            } finally {
                plaintext.fill(0)
            }
        } finally {
            master.fill(0)
        }
    }

    private fun writeMetadata(master: ByteArray, recoveryPassword: CharArray) {
        val salt = ByteArray(16).also(secureRandom::nextBytes)
        val recoveryKey = derivePasswordKey(recoveryPassword, salt)
        try {
            val sealed = encrypt(recoveryKey.encoded, master)
            val json = JSONObject()
                .put("schemaVersion", SCHEMA)
                .put("createdAtEpochMillis", System.currentTimeMillis())
                .put("kdf", "PBKDF2WithHmacSHA256")
                .put("iterations", PBKDF2_ITERATIONS)
                .put("salt", b64(salt))
                .put("recoveryIv", b64(sealed.iv))
                .put("recoveryCiphertext", b64(sealed.ciphertext))
            atomicWrite(metadataFile(), json.toString(2).toByteArray(Charsets.UTF_8))
        } finally {
            recoveryKey.encoded.fill(0)
            salt.fill(0)
        }
    }

    private fun unwrapRecoveryMaster(password: CharArray): ByteArray {
        val json = JSONObject(metadataFile().readText())
        check(json.optInt("schemaVersion", -1) == SCHEMA) { "不支持的秘密保险库格式" }
        val salt = b64d(json.getString("salt"))
        val key = derivePasswordKey(password, salt, json.optInt("iterations", PBKDF2_ITERATIONS))
        return try {
            decrypt(key.encoded, b64d(json.getString("recoveryIv")), b64d(json.getString("recoveryCiphertext")))
        } finally {
            key.encoded.fill(0)
            salt.fill(0)
        }
    }

    private fun writeDeviceWrapper(master: ByteArray) {
        val key = getOrCreateDeviceKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(master)
        val device = JSONObject()
            .put("schemaVersion", SCHEMA)
            .put("iv", b64(cipher.iv))
            .put("ciphertext", b64(ciphertext))
        val file = File(appContext.filesDir, "recovery/device-secret-wrapper.json")
        atomicWrite(file, device.toString().toByteArray(Charsets.UTF_8))
    }

    private fun loadDeviceMasterKey(): Result<ByteArray> = runCatching {
        val file = File(appContext.filesDir, "recovery/device-secret-wrapper.json")
        check(file.isFile) { "设备恢复密钥尚未解锁" }
        val json = JSONObject(file.readText())
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = store.getKey(KEY_ALIAS, null) as? SecretKey ?: error("设备 KeyStore 包装密钥不存在")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, b64d(json.getString("iv"))))
        cipher.doFinal(b64d(json.getString("ciphertext")))
    }

    private fun getOrCreateDeviceKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun derivePasswordKey(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private data class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

    private fun encrypt(key: ByteArray, plaintext: ByteArray): Sealed {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also(secureRandom::nextBytes)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return Sealed(iv, cipher.doFinal(plaintext))
    }

    private fun decrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun encryptedDir(): File {
        val status = vault.ensureLayout().getOrThrow()
        return File(status.root, "Recovery/Secrets/encrypted-vault").apply { check(exists() || mkdirs()) }
    }

    private fun metadataFile(): File = File(encryptedDir(), "secret-vault.json")

    private fun requireStrongEnough(password: CharArray) {
        require(password.size >= 10) { "恢复密码至少需要 10 个字符" }
    }

    private fun atomicWrite(file: File, bytes: ByteArray) {
        file.parentFile?.let { check(it.exists() || it.mkdirs()) }
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
    }

    private fun isSafeRelativePath(path: String): Boolean =
        path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it.isBlank() || it == "." || it == ".." }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun b64d(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
