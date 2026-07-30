package io.github.kioskrelay.data

import io.github.kioskrelay.config.ConfigDefaults
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingConfigExportTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun preparedSnapshot_survivesOwnerRecreationAndKeepsDraft() {
        val cache = temporaryFolder.newFolder("cache")
        val assets = temporaryFolder.newFolder("assets")
        val manager = ConfigArchiveManager()
        val snapshotId = PendingConfigExport(cache, manager).newSnapshotId()
        val draft = ConfigDefaults.config.copy(
            onboardingCompleted = true,
            branding = ConfigDefaults.config.branding.copy(productName = "Draft brand"),
            webView = ConfigDefaults.config.webView.copy(
                initialUrl = "https://display.example.com/",
                allowedOrigins = setOf("https://display.example.com"),
            ),
        )
        PendingConfigExport(cache, manager).prepare(snapshotId, draft, assets)

        val bytes = ByteArrayOutputStream().also { output ->
            PendingConfigExport(cache, manager).copyTo(snapshotId, output)
        }.toByteArray()
        val imported = manager.import(
            input = ByteArrayInputStream(bytes),
            assetRoot = temporaryFolder.newFolder("imported-assets"),
        )

        assertEquals("Draft brand", imported.config.branding.productName)
        PendingConfigExport(cache, manager).discard(snapshotId)
        assertFalse(
            File(cache, "configuration-export/pending-$snapshotId.kioskrelay").exists(),
        )
    }

    @Test
    fun discard_oldGeneration_doesNotDeleteNewGeneration() {
        val cache = temporaryFolder.newFolder("cache")
        val assets = temporaryFolder.newFolder("assets")
        val manager = ConfigArchiveManager()
        val pending = PendingConfigExport(cache, manager)
        val oldId = pending.newSnapshotId()
        val newId = pending.newSnapshotId()

        pending.prepare(oldId, ConfigDefaults.config, assets)
        pending.prepare(newId, ConfigDefaults.config, assets)
        pending.discard(oldId)

        val bytes = ByteArrayOutputStream().also { output ->
            pending.copyTo(newId, output)
        }.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun processStartupCleanup_removesOnlyManagedExportFiles() {
        val cache = temporaryFolder.newFolder("cache")
        val assets = temporaryFolder.newFolder("assets")
        val manager = ConfigArchiveManager()
        val pending = PendingConfigExport(cache, manager)
        val snapshotId = pending.newSnapshotId()
        pending.prepare(snapshotId, ConfigDefaults.config, assets)
        val exportDirectory = File(cache, "configuration-export")
        val unrelated = File(exportDirectory, "keep.txt").apply { writeText("keep") }

        assertEquals(1, pending.discardOrphans())
        assertTrue(unrelated.isFile)
    }

    @Test
    fun processRestart_recoversOnlyLaunchedSnapshot() {
        val cache = temporaryFolder.newFolder("cache")
        val assets = temporaryFolder.newFolder("assets")
        val manager = ConfigArchiveManager()
        val owner = PendingConfigExport(cache, manager)
        val snapshotId = owner.newSnapshotId()
        owner.prepare(snapshotId, ConfigDefaults.config, assets)
        owner.markLaunched(snapshotId)

        val recreatedOwner = PendingConfigExport(cache, manager)

        assertEquals(
            snapshotId,
            recreatedOwner.recoverLaunchedSnapshotId(snapshotId),
        )
        val bytes = ByteArrayOutputStream().also { output ->
            recreatedOwner.copyTo(snapshotId, output)
        }.toByteArray()
        assertTrue(bytes.isNotEmpty())
    }

    @Test
    fun coldStart_withoutMatchingSavedState_discardsLaunchedSnapshot() {
        val cache = temporaryFolder.newFolder("cache")
        val assets = temporaryFolder.newFolder("assets")
        val manager = ConfigArchiveManager()
        val owner = PendingConfigExport(cache, manager)
        val snapshotId = owner.newSnapshotId()
        owner.prepare(snapshotId, ConfigDefaults.config, assets)
        owner.markLaunched(snapshotId)

        val coldOwner = PendingConfigExport(cache, manager)

        assertEquals(null, coldOwner.recoverLaunchedSnapshotId(null))
        assertFalse(
            File(cache, "configuration-export/pending-$snapshotId.kioskrelay").exists(),
        )
    }

    @Test
    fun processRestart_discardsSnapshotWhoseResultWasAlreadyReceived() {
        val cache = temporaryFolder.newFolder("cache")
        val assets = temporaryFolder.newFolder("assets")
        val manager = ConfigArchiveManager()
        val owner = PendingConfigExport(cache, manager)
        val snapshotId = owner.newSnapshotId()
        owner.prepare(snapshotId, ConfigDefaults.config, assets)
        owner.markLaunched(snapshotId)
        owner.markResultReceived(snapshotId)

        val recreatedOwner = PendingConfigExport(cache, manager)

        assertEquals(null, recreatedOwner.recoverLaunchedSnapshotId(snapshotId))
        assertFalse(
            File(cache, "configuration-export/pending-$snapshotId.kioskrelay").exists(),
        )
    }
}
