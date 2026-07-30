package io.github.kioskrelay.feature.onboarding

import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebTestDialogLayoutTest {
    @Test
    fun landscapePreview_preservesLandscapeScreenRatio() {
        val layout = calculateWebTestDialogLayout(
            screenWidth = 1_280.dp,
            screenHeight = 800.dp,
            orientation = SetupOrientation.LANDSCAPE,
        )

        assertEquals(1.6f, layout.previewWidth.value / layout.previewHeight.value, 0.001f)
        assertTrue(layout.dialogWidth <= 1_280.dp)
        assertTrue(layout.dialogHeight <= 800.dp)
    }

    @Test
    fun portraitSelection_rotatesLandscapeDeviceRatioAndFitsDialog() {
        val layout = calculateWebTestDialogLayout(
            screenWidth = 1_280.dp,
            screenHeight = 800.dp,
            orientation = SetupOrientation.PORTRAIT,
        )

        assertEquals(0.625f, layout.previewWidth.value / layout.previewHeight.value, 0.001f)
        assertTrue(layout.previewWidth <= layout.dialogWidth - 40.dp)
        assertTrue(layout.previewHeight <= layout.dialogHeight - 164.dp)
    }

    @Test
    fun followSystem_usesCurrentPortraitRatio() {
        val layout = calculateWebTestDialogLayout(
            screenWidth = 800.dp,
            screenHeight = 1_280.dp,
            orientation = SetupOrientation.FOLLOW_SYSTEM,
        )

        assertTrue(
            abs(layout.previewWidth.value / layout.previewHeight.value - 0.625f) < 0.001f,
        )
    }
}
