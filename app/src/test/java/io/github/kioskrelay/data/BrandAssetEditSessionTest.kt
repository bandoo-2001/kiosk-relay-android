package io.github.kioskrelay.data

import io.github.kioskrelay.config.ConfigDefaults
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BrandAssetEditSessionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rollback_restoresExistingAssetAndRemovesNewAsset() {
        val root = temporaryFolder.newFolder("branding")
        val logo = File(root, ConfigArchiveManager.LOGO_FILE).apply {
            writeText("old")
        }
        val splash = File(root, ConfigArchiveManager.SPLASH_FILE)
        val session = BrandAssetEditSession.begin(root)

        logo.writeText("new")
        splash.writeText("new splash")
        session.rollback()

        assertEquals("old", logo.readText())
        assertFalse(splash.exists())
    }

    @Test
    fun commit_keepsEditedAsset() {
        val root = temporaryFolder.newFolder("branding")
        val logo = File(root, ConfigArchiveManager.LOGO_FILE).apply {
            writeText("old")
        }
        val session = BrandAssetEditSession.begin(root)

        logo.writeText("new")
        session.prepareCommit(ConfigDefaults.config)
        session.commit()
        BrandAssetEditSession.recoverIfNeeded(root, ConfigDefaults.config)

        assertEquals("new", logo.readText())
    }

    @Test
    fun processRecovery_rollsBackInterruptedSession() {
        val root = temporaryFolder.newFolder("branding")
        val logo = File(root, ConfigArchiveManager.LOGO_FILE).apply {
            writeText("old")
        }
        BrandAssetEditSession.begin(root)
        logo.writeText("interrupted")

        BrandAssetEditSession.recoverIfNeeded(root, ConfigDefaults.config)

        assertEquals("old", logo.readText())
    }

    @Test
    fun processRecovery_keepsAssetsWhenStoredConfigMatchesPreparedTarget() {
        val root = temporaryFolder.newFolder("branding")
        val logo = File(root, ConfigArchiveManager.LOGO_FILE).apply {
            writeText("old")
        }
        val targetConfig = ConfigDefaults.config.copy(
            branding = ConfigDefaults.config.branding.copy(productName = "Committed"),
        )
        val session = BrandAssetEditSession.begin(root)
        logo.writeText("committed")
        session.prepareCommit(targetConfig)

        BrandAssetEditSession.recoverIfNeeded(root, targetConfig)

        assertEquals("committed", logo.readText())
        assertFalse(File(root, ".settings-branding-edit").exists())
    }

    @Test
    fun processRecovery_rollsBackWhenConfigWriteDidNotCommit() {
        val root = temporaryFolder.newFolder("branding")
        val logo = File(root, ConfigArchiveManager.LOGO_FILE).apply {
            writeText("old")
        }
        val targetConfig = ConfigDefaults.config.copy(
            branding = ConfigDefaults.config.branding.copy(productName = "Not stored"),
        )
        val session = BrandAssetEditSession.begin(root)
        logo.writeText("uncommitted")
        session.prepareCommit(targetConfig)

        BrandAssetEditSession.recoverIfNeeded(root, ConfigDefaults.config)

        assertEquals("old", logo.readText())
        assertFalse(File(root, ".settings-branding-edit").exists())
    }

    @Test
    fun configurationFingerprint_isStableAcrossOriginSetOrder() {
        val first = ConfigDefaults.config.copy(
            webView = ConfigDefaults.config.webView.copy(
                allowedOrigins = linkedSetOf("https://b.example", "https://a.example"),
            ),
        )
        val second = first.copy(
            webView = first.webView.copy(
                allowedOrigins = linkedSetOf("https://a.example", "https://b.example"),
            ),
        )

        assertEquals(
            BrandAssetEditSession.configurationFingerprint(first),
            BrandAssetEditSession.configurationFingerprint(second),
        )
    }
}
