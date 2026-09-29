package io.github.kioskrelay.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.kioskrelay.R
import kotlin.math.roundToInt

/** A focus cue for keyboard/D-pad users, without adding an extra focus stop. */
fun Modifier.remoteFocus(shape: Shape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    onFocusChanged { focused = it.isFocused }
        .border(2.dp, if (focused) MaterialTheme.colorScheme.tertiary else Color.Transparent, shape)
}

fun Modifier.initialFocus(): Modifier = composed {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    focusRequester(requester)
}

/** Keep left/right native editing; up/down leave sliders and text fields. */
fun Modifier.remoteVerticalNavigation(): Modifier = composed {
    val manager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        val direction = when (event.key) {
            Key.DirectionUp -> FocusDirection.Previous
            Key.DirectionDown -> FocusDirection.Next
            else -> null
        }
        if (direction == null) false else {
            if (event.type == KeyEventType.KeyDown) manager.moveFocus(direction)
            true
        }
    }
}

@Composable
fun RemoteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.Button(
    onClick = onClick, modifier = modifier.remoteFocus(ButtonDefaults.shape),
    enabled = enabled, content = content,
)

@Composable
fun RemoteOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    content: @Composable RowScope.() -> Unit,
) = androidx.compose.material3.OutlinedButton(
    onClick = onClick, modifier = modifier.remoteFocus(shape),
    enabled = enabled, shape = shape, content = content,
)

@Composable
fun RemoteSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().remoteFocus()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun BootDelaySlider(seconds: Int, onValueChange: (Int) -> Unit) {
    val description = stringResource(R.string.boot_delay, seconds)
    Slider(
        value = seconds.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 60)) },
        valueRange = 0f..60f,
        steps = 11,
        modifier = Modifier.testTag("boot-delay-slider")
            .semantics { contentDescription = description }
            .remoteFocus().remoteVerticalNavigation(),
    )
}
