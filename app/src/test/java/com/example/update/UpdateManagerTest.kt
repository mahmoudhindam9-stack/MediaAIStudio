package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.update.models.UpdateInfo
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateManagerTest {

    private lateinit var context: Context
    private lateinit var testCacheDir: File

    private val trustedCertBytes = "TRUSTED_PERMANENT_RELEASE_KEY_CONTENT".toByteArray()
    private val trustedFingerprint = MessageDigest.getInstance("SHA-256")
        .digest(trustedCertBytes)
        .joinToString("") { "%02x".format(it) }
        .lowercase()

    private val untrustedCertBytes = "UNTRUSTED_ARBITRARY_KEY_CONTENT".toByteArray()
    private val untrustedFingerprint = MessageDigest.getInstance("SHA-256")
        .digest(untrustedCertBytes)
        .joinToString("") { "%02x".format(it) }
        .lowercase()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        testCacheDir = context.cacheDir
        testCacheDir.mkdirs()
    }

    @After
    fun tearDown() {
        testCacheDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun testNoUpdateAvailable() = runTest {
        val client = GitHubReleaseClient()
        val json = """
            [
              {
                "id": 1,
                "tag_name": "v1.7.0",
                "draft": false,
                "body": "versionCode: 12\nSome notes",
                "published_at": "2026-10-06T00:00:00Z",
                "assets": [
                  {
                    "name": "MediaAIStudio-v1.7.0.apk",
                    "browser_download_url": "https://github.com/mahmoudhindam9-stack/MediaAIStudio/releases/download/v1.7.0/app.apk",
                    "size": 1024000
                  }
                ]
              }
            ]
        """.trimIndent()

        val parsed = client.parseReleasesJson(json)
        assertNotNull(parsed)

        val checker = UpdateChecker(
            context = context,
            currentVersionCodeProvider = { 12 }
        )

        val result = if (parsed!!.versionCode > checker.getCurrentVersionCode()) {
            UpdateCheckResult.UpdateAvailable(parsed)
        } else {
            UpdateCheckResult.UpToDate
        }

        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun testUpdateAvailable() = runTest {
        val client = GitHubReleaseClient()
        val json = """
            [
              {
                "id": 2,
                "tag_name": "v1.8.0",
                "draft": false,
                "body": "versionCode: 15\nSHA-256: 11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff",
                "published_at": "2026-10-06T01:00:00Z",
                "assets": [
                  {
                    "name": "MediaAIStudio-v1.8.0.apk",
                    "browser_download_url": "https://github.com/mahmoudhindam9-stack/MediaAIStudio/releases/download/v1.8.0/app.apk",
                    "size": 2048000
                  }
                ]
              }
            ]
        """.trimIndent()

        val parsed = client.parseReleasesJson(json)
        assertNotNull(parsed)
        assertEquals(15, parsed!!.versionCode)
        assertEquals("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff", parsed.sha256)

        val checker = UpdateChecker(
            context = context,
            currentVersionCodeProvider = { 12 }
        )

        val result = if (parsed.versionCode > checker.getCurrentVersionCode()) {
            UpdateCheckResult.UpdateAvailable(parsed)
        } else {
            UpdateCheckResult.UpToDate
        }

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        assertEquals(15, (result as UpdateCheckResult.UpdateAvailable).releaseInfo.versionCode)
    }

    @Test
    fun testDowngradeRejected() {
        val dummyApk = File(testCacheDir, "test_downgrade.apk").apply {
            writeBytes("DUMMY_APK_DATA".toByteArray())
        }

        val dummyPackageInfo = PackageInfo().apply {
            packageName = UpdateConfig.EXPECTED_PACKAGE_NAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 8L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 8
            }
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint),
            certExtractor = { listOf(trustedCertBytes) },
            packageArchiveInfoProvider = { dummyPackageInfo }
        )

        val result = verifier.verifyApk(dummyApk, null)
        assertTrue(result is VerificationResult.Error)
        assertTrue((result as VerificationResult.Error).reason.contains("DOWNGRADE_REJECTED"))
    }

    @Test
    fun testWrongPackageRejected() {
        val dummyApk = File(testCacheDir, "test_wrong_pkg.apk").apply {
            writeBytes("DUMMY_APK_DATA".toByteArray())
        }

        val dummyPackageInfo = PackageInfo().apply {
            packageName = "com.untrusted.fakeapp"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 99L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 99
            }
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint),
            certExtractor = { listOf(trustedCertBytes) },
            packageArchiveInfoProvider = { dummyPackageInfo }
        )

        val result = verifier.verifyApk(dummyApk, null)
        assertTrue(result is VerificationResult.Error)
        assertTrue((result as VerificationResult.Error).reason.contains("WRONG_PACKAGE"))
    }

    @Test
    fun testWrongSignatureRejected() {
        val dummyApk = File(testCacheDir, "test_wrong_sig.apk").apply {
            writeBytes("DUMMY_APK_DATA".toByteArray())
        }

        val dummyPackageInfo = PackageInfo().apply {
            packageName = UpdateConfig.EXPECTED_PACKAGE_NAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 99L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 99
            }
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint),
            certExtractor = { listOf(untrustedCertBytes) },
            packageArchiveInfoProvider = { dummyPackageInfo }
        )

        val result = verifier.verifyApk(dummyApk, null)
        assertTrue(result is VerificationResult.Error)
        assertTrue((result as VerificationResult.Error).reason.contains("UNTRUSTED_SIGNATURE"))
    }

    @Test
    fun testWrongSha256Rejected() {
        val dummyApk = File(testCacheDir, "test_wrong_sha.apk").apply {
            writeBytes("DUMMY_APK_CONTENT_DATA".toByteArray())
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint)
        )

        val mismatchedSha = "0000000000000000000000000000000000000000000000000000000000000000"
        val result = verifier.verifyApk(dummyApk, mismatchedSha)
        assertTrue(result is VerificationResult.Error)
        assertTrue((result as VerificationResult.Error).reason.contains("CHECKSUM_MISMATCH"))
    }

    @Test
    fun testIncompleteApkRejected() {
        val emptyApk = File(testCacheDir, "empty.apk").apply {
            createNewFile()
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint)
        )

        val result = verifier.verifyApk(emptyApk, null)
        assertTrue(result is VerificationResult.Error)
        assertTrue((result as VerificationResult.Error).reason.contains("FILE_NOT_FOUND_OR_EMPTY"))
    }

    @Test
    fun testInvalidGitHubReleaseRejected() {
        val client = GitHubReleaseClient()

        // 1. Draft release must be ignored
        val draftJson = """
            [
              {
                "id": 10,
                "tag_name": "v2.0.0",
                "draft": true,
                "body": "versionCode: 20",
                "assets": [
                  {
                    "name": "MediaAIStudio.apk",
                    "browser_download_url": "https://github.com/mahmoudhindam9-stack/MediaAIStudio/releases/download/v2.0.0/app.apk",
                    "size": 5000000
                  }
                ]
              }
            ]
        """.trimIndent()
        assertNull(client.parseReleasesJson(draftJson))

        // 2. Only debug APK or idsig must be ignored
        val debugOnlyJson = """
            [
              {
                "id": 11,
                "tag_name": "v2.0.1",
                "draft": false,
                "body": "versionCode: 21",
                "assets": [
                  {
                    "name": "app-debug.apk",
                    "browser_download_url": "https://github.com/mahmoudhindam9-stack/MediaAIStudio/releases/download/v2.0.1/app-debug.apk",
                    "size": 5000000
                  },
                  {
                    "name": "app-release.apk.idsig",
                    "browser_download_url": "https://github.com/mahmoudhindam9-stack/MediaAIStudio/releases/download/v2.0.1/app-release.apk.idsig",
                    "size": 1000
                  }
                ]
              }
            ]
        """.trimIndent()
        assertNull(client.parseReleasesJson(debugOnlyJson))
    }

    @Test
    fun testFailedDownloadCleanup() = runTest {
        val failedFile = File(testCacheDir, "update_temp_fail_test.apk.part").apply {
            writeBytes("PARTIAL_DOWNLOAD_DATA".toByteArray())
        }
        assertTrue(failedFile.exists())

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint)
        )

        val manager = UpdateManager(
            context = context,
            verifier = verifier
        )

        manager.cleanUpOldUpdates(50)
        assertFalse(failedFile.exists())
    }

    @Test
    fun testSuccessfulValidation() {
        val validApk = File(testCacheDir, "test_valid.apk").apply {
            writeBytes("VALID_APK_PAYLOAD_TEST".toByteArray())
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(validApk.readBytes())
            .joinToString("") { "%02x".format(it) }

        val validPackageInfo = PackageInfo().apply {
            packageName = UpdateConfig.EXPECTED_PACKAGE_NAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 99L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 99
            }
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint),
            certExtractor = { listOf(trustedCertBytes) },
            packageArchiveInfoProvider = { validPackageInfo }
        )

        val result = verifier.verifyApk(validApk, digest)
        assertTrue(result is VerificationResult.Success)
    }

    @Test
    fun testValidCachedApkReuse() = runTest {
        val cachedApk = File(testCacheDir, "update_verified_100.apk").apply {
            writeBytes("CACHED_VERIFIED_DATA".toByteArray())
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cachedApk.readBytes())
            .joinToString("") { "%02x".format(it) }

        val validPackageInfo = PackageInfo().apply {
            packageName = UpdateConfig.EXPECTED_PACKAGE_NAME
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode = 100L
            } else {
                @Suppress("DEPRECATION")
                versionCode = 100
            }
        }

        val verifier = ApkVerifier(
            context = context,
            trustedFingerprints = setOf(trustedFingerprint),
            certExtractor = { listOf(trustedCertBytes) },
            packageArchiveInfoProvider = { validPackageInfo }
        )

        val updateInfo = UpdateInfo(
            versionName = "3.0.0",
            versionCode = 100,
            downloadUrl = "https://example.com/cached.apk",
            sha256 = digest,
            releaseNotes = "Cached release",
            publishedAt = "2026-10-06T00:00:00Z"
        )

        val manager = UpdateManager(
            context = context,
            verifier = verifier
        )

        val states = manager.downloadAndVerify(updateInfo).toList()
        assertEquals(1, states.size)
        assertTrue(states[0] is UpdateState.ReadyToInstall)
        assertEquals(cachedApk.absolutePath, (states[0] as UpdateState.ReadyToInstall).apkFile.absolutePath)
    }
}
