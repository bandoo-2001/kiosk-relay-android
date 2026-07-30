package io.github.kioskrelay.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.kioskrelay.R
import io.github.kioskrelay.config.BrandingConfig
import io.github.kioskrelay.data.BrandImageImporter

@Composable
fun StartupBrandScreen(
    branding: BrandingConfig,
    imageImporter: BrandImageImporter,
    modifier: Modifier = Modifier,
) {
    val background = rememberBrandImage(
        importer = imageImporter,
        relativePath = branding.splashRelativePath,
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(branding.backgroundColorArgb)),
        contentAlignment = Alignment.Center,
    ) {
        background?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f)),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BrandLogo(
                branding = branding,
                imageImporter = imageImporter,
            )
            Text(
                text = branding.productName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            CircularProgressIndicator(color = Color(branding.primaryColorArgb))
            Text(
                text = branding.loadingMessage.ifBlank {
                    stringResource(R.string.default_loading_message)
                },
                color = Color.White.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
fun BrandLogo(
    branding: BrandingConfig,
    imageImporter: BrandImageImporter,
    modifier: Modifier = Modifier,
    imageRevision: Int = 0,
) {
    val logo = rememberBrandImage(
        importer = imageImporter,
        relativePath = branding.logoRelativePath,
        imageRevision = imageRevision,
    )
    if (logo != null) {
        Image(
            bitmap = logo,
            contentDescription = branding.productName,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(116.dp),
        )
    } else {
        Text(
            text = "↔",
            color = Color(branding.primaryColorArgb),
            style = MaterialTheme.typography.displaySmall,
            modifier = modifier,
        )
    }
}

@Composable
internal fun rememberBrandImage(
    importer: BrandImageImporter,
    relativePath: String?,
    imageRevision: Int = 0,
): ImageBitmap? {
    val file = relativePath?.let(importer::resolve)
    return remember(file?.absolutePath, file?.lastModified(), imageRevision) {
        file?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }
    }
}
