package com.example.update

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ApkVerifier(private val context: Context) {
    
    fun verifyApk(apkFile: File, expectedDigest: String?): VerificationResult {
        // 1. Verify SHA-256 if provided
        if (expectedDigest != null) {
            val computedDigest = computeSha256(apkFile)
            if (computedDigest != expectedDigest) {
                return VerificationResult.Error("CHECKSUM_MISMATCH")
            }
        }

        // 2. Verify Signature Identity
        val packageManager = context.packageManager
        val installedPackageInfo = try {
            packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: PackageManager.NameNotFoundException) {
            return VerificationResult.Error("INVALID_APK")
        }
        
        val downloadedPackageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return VerificationResult.Error("INVALID_APK")

        // Reject if versions are wrong
        val installedVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            installedPackageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            installedPackageInfo.versionCode.toLong()
        }
        
        val downloadedVersion = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            downloadedPackageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            downloadedPackageInfo.versionCode.toLong()
        }
        
        if (downloadedVersion <= installedVersion) {
            return VerificationResult.Error("DOWNGRADE_BLOCKED")
        }

        // Compare signatures
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val installedSignatures = installedPackageInfo.signingInfo?.apkContentsSigners
            val downloadedSignatures = downloadedPackageInfo.signingInfo?.apkContentsSigners
            
            if (installedSignatures == null || downloadedSignatures == null || installedSignatures.isEmpty() || downloadedSignatures.isEmpty()) {
                return VerificationResult.Error("SIGNATURE_MISMATCH")
            }
            
            val installedSig = installedSignatures[0].toByteArray()
            val downloadedSig = downloadedSignatures[0].toByteArray()
            
            if (!installedSig.contentEquals(downloadedSig)) {
                return VerificationResult.Error("SIGNATURE_MISMATCH")
            }
        } else {
            @Suppress("DEPRECATION")
            val installedSignatures = installedPackageInfo.signatures
            @Suppress("DEPRECATION")
            val downloadedSignatures = downloadedPackageInfo.signatures
            
            if (installedSignatures == null || downloadedSignatures == null || installedSignatures.isEmpty() || downloadedSignatures.isEmpty()) {
                return VerificationResult.Error("SIGNATURE_MISMATCH")
            }
            
            if (!installedSignatures[0].toByteArray().contentEquals(downloadedSignatures[0].toByteArray())) {
                return VerificationResult.Error("SIGNATURE_MISMATCH")
            }
        }
        
        return VerificationResult.Success
    }

    private fun computeSha256(file: File): String {
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
