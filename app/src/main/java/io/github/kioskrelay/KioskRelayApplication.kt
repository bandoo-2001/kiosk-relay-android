package io.github.kioskrelay

import android.app.Application
import android.content.Context
import io.github.kioskrelay.data.AndroidBrandImageImporter
import io.github.kioskrelay.data.BrandImageImporter
import io.github.kioskrelay.data.ConfigArchiveManager
import io.github.kioskrelay.data.ConfigRepository
import io.github.kioskrelay.data.ConfigurationExportCoordinator
import io.github.kioskrelay.data.PendingConfigExport
import io.github.kioskrelay.data.ExternalDocumentStager
import io.github.kioskrelay.data.ProtoConfigRepository
import io.github.kioskrelay.data.BrandingOperationCoordinator
import io.github.kioskrelay.diagnostics.DiagnosticLog
import io.github.kioskrelay.diagnostics.DiagnosticRingLog
import io.github.kioskrelay.security.AdminAuthenticator
import io.github.kioskrelay.security.Pbkdf2AdminAuthenticator
import io.github.kioskrelay.security.SharedPreferencesAdminCredentialStore
import java.io.File

class KioskRelayApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DefaultAppContainer(this)
    }
}

interface AppContainer {
    val configRepository: ConfigRepository
    val adminAuthenticator: AdminAuthenticator
    val brandImageImporter: BrandImageImporter
    val configArchiveManager: ConfigArchiveManager
    val brandingOperationCoordinator: BrandingOperationCoordinator
    val configurationExportCoordinator: ConfigurationExportCoordinator
    val pendingConfigExport: PendingConfigExport
    val externalDocumentStager: ExternalDocumentStager
    val diagnosticLog: DiagnosticLog
    val brandingDirectory: File
}

private class DefaultAppContainer(
    context: Context,
) : AppContainer {
    private val applicationContext = context.applicationContext

    override val configRepository: ConfigRepository by lazy {
        ProtoConfigRepository.create(applicationContext)
    }

    override val adminAuthenticator: AdminAuthenticator by lazy {
        Pbkdf2AdminAuthenticator(
            SharedPreferencesAdminCredentialStore(applicationContext),
        )
    }

    override val brandingDirectory: File =
        File(applicationContext.filesDir, BRANDING_DIRECTORY)

    override val brandImageImporter: BrandImageImporter by lazy {
        AndroidBrandImageImporter(
            context = applicationContext,
            assetRoot = brandingDirectory,
        )
    }

    override val configArchiveManager: ConfigArchiveManager by lazy {
        ConfigArchiveManager()
    }

    override val brandingOperationCoordinator: BrandingOperationCoordinator =
        BrandingOperationCoordinator()

    override val configurationExportCoordinator: ConfigurationExportCoordinator =
        ConfigurationExportCoordinator()

    override val pendingConfigExport: PendingConfigExport by lazy {
        PendingConfigExport(
            cacheRoot = applicationContext.cacheDir,
            archiveManager = configArchiveManager,
        )
    }

    override val externalDocumentStager: ExternalDocumentStager =
        ExternalDocumentStager(applicationContext)

    override val diagnosticLog: DiagnosticLog by lazy {
        DiagnosticRingLog()
    }

    private companion object {
        const val BRANDING_DIRECTORY = "branding"
    }
}
