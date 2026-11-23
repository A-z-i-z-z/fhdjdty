package dev.pocketagent.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.pocketagent.app.ui.theme.Pocket

/**
 * Text input with a sunken pixel box (bgDeep face, 1-pixel border; the border turns accent-coloured and
 * gets an ember glow ring while focused). [label] sits above the box. At least 48 dp high.
 */
@Composable
fun PocketTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    monospace: Boolean = false,
    password: Boolean = false,
) {
    val c = Pocket.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = c.text,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
    )
    val a11y = label ?: placeholder
    val fieldModifier = if (a11y != null) {
        Modifier.fillMaxWidth().semantics { contentDescription = a11y }
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) c.accentSoft else c.textMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = fieldModifier,
            textStyle = style,
            cursorBrush = SolidColor(c.accent),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            maxLines = if (singleLine) 1 else maxLines,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else KeyboardType.Text,
                autoCorrectEnabled = !(password || monospace),
            ),
            interactionSource = interaction,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pixelSurface(
                            face = c.bgDeep,
                            border = if (focused) c.accent else c.outline,
                            corner = 2,
                            pixel = 2.dp,
                            shade = null,
                            highlight = null,
                            glow = if (focused) c.fireMid else null,
                            glowAlpha = { 0.22f },
                        )
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = style.copy(color = c.textMuted),
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
