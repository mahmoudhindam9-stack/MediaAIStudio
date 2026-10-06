package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.zip.ZipFile

class ApkVerifier(
    private val context: Context,
    private val trustedFingerprints: Set<String> = UpdateConfig.TRUSTED_SIGNATURE_FINGERPRINTS,
    private val certExtractor: ((File) -> List<ByteArray>)? = null,
    private val packageArchiveInfoProvider: ((File) -> PackageInfo?)? = null
) {

    fun verifyApk(apkFile: File, expectedDigest: String?): VerificationResult {
        // 1. Verify file exists and is not empty
        if (!apkFile.exists() || apkFile.length() <= 0) {
            return VerificationResult.Error("FILE_NOT_FOUND_OR_EMPTY")
        }

        // 2. Verify SHA-256 if provided
        if (!expectedDigest.isNullOrBlank()) {
            val computedDigest = computeSha256(apkFile)
            val cleanExpected = expectedDigest.trim().lowercase().removePrefix("sha256:")
            if (!computedDigest.equals(cleanExpected, ignoreCase = true)) {
                return VerificationResult.Error("CHECKSUM_MISMATCH: expected $cleanExpected, got $computedDigest")
            }
        }

        // 3. Verify APK archive structure and parse PackageInfo
        val packageManager = context.packageManager
        val downloadedPackageInfo: PackageInfo? = try {
            if (packageArchiveInfoProvider != null) {
                packageArchiveInfoProvider.invoke(apkFile)
            } else {
                packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            }
        } catch (e: Exception) {
            return VerificationResult.Error("INVALID_APK_STRUCTURE: ${e.message}")
        }

        if (downloadedPackageInfo == null || downloadedPackageInfo.packageName.isNullOrBlank()) {
            return VerificationResult.Error("INVALID_APK_STRUCTURE: Unable to parse APK manifest")
        }

        // 4. Verify Package Name
        val downloadedPackageName = downloadedPackageInfo.packageName
        val expectedPackageName = UpdateConfig.EXPECTED_PACKAGE_NAME
        if (downloadedPackageName != expectedPackageName && downloadedPackageName != context.packageName) {
            return VerificationResult.Error("WRONG_PACKAGE: expected $expectedPackageName, got $downloadedPackageName")
        }

        // 5. Verify Version Code (prevent downgrades)
        val downloadedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            downloadedPackageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            downloadedPackageInfo.versionCode.toLong()
        }

        val installedVersion = getInstalledVersionCode()
        if (downloadedVersion < installedVersion) {
            return VerificationResult.Error("DOWNGRADE_REJECTED: APK version $downloadedVersion is lower than installed $installedVersion")
        }

        // 6. Verify Signing Certificate Trust
        val certificates = extractCertificates(apkFile, downloadedPackageInfo)
        if (certificates.isEmpty()) {
            return VerificationResult.Error("NO_SIGNATURE: APK is unsigned or certificates could not be extracted")
        }

        val certFingerprints = certificates.map { certBytes ->
            MessageDigest.getInstance("SHA-256")
                .digest(certBytes)
                .joinToString("") { "%02x".format(it) }
                .lowercase()
        }

        val isTrusted = certFingerprints.any { trustedFingerprints.contains(it) }
        if (!isTrusted) {
            return VerificationResult.Error("UNTRUSTED_SIGNATURE: APK certificate fingerprints $certFingerprints are not in trusted certificate list")
        }

        return VerificationResult.Success
    }

    private fun getInstalledVersionCode(): Long {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    private fun extractCertificates(apkFile: File, packageInfo: PackageInfo): List<ByteArray> {
        if (certExtractor != null) {
            return certExtractor.invoke(apkFile)
        }

        // Attempt 1: Android 9+ signingInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                val apkSigners = signingInfo.apkContentsSigners
                if (!apkSigners.isNullOrEmpty()) {
                    return apkSigners.map { it.toByteArray() }
                }
                val history = signingInfo.signingCertificateHistory
                if (!history.isNullOrEmpty()) {
                    return history.map { it.toByteArray() }
                }
            }
        }

        // Attempt 2: Legacy signatures
        @Suppress("DEPRECATION")
        val signatures = packageInfo.signatures
        if (!signatures.isNullOrEmpty()) {
            return signatures.map { it.toByteArray() }
        }

        // Attempt 3: Extract from APK zip container (META-INF/*.RSA, *.DSA, *.EC)
        return extractCertificatesFromZip(apkFile)
    }

    private fun extractCertificatesFromZip(apkFile: File): List<ByteArray> {
        val certs = mutableListOf<ByteArray>()
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                val certFactory = CertificateFactory.getInstance("X.509")
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.uppercase()
                    if (name.startsWith("META-INF/") && (name.endsWith(".RSA") || name.endsWith(".DSA") || name.endsWith(".EC"))) {
                        zip.getInputStream(entry).use { stream ->
                            val parsed = certFactory.generateCertificates(stream)
                            certs.addAll(parsed.map { it.encoded })
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return certs
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

sealed class VerificationResult {
    object Success : VerificationResult()
    data class Error(val reason: String) : VerificationResult()
}
