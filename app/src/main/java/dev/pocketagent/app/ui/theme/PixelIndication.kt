package dev.pocketagent.app.ui.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

/**
 * Pixel-style press feedback used instead of Material's ripple: while a finger is down the element is
 * covered by a flat, hard-edged warm tint (no fade, no circle). Provided as LocalIndication by PocketTheme,
 * so every clickable() in the app gets it.
 */
object PixelIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        PixelIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -7
}

private class PixelIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {
    private var pressCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> { pressCount++; invalidateDraw() }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        if (pressCount > 0) pressCount--
                        invalidateDraw()
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressCount > 0) drawRect(PRESS_TINT)
    }

    private companion object {
        val PRESS_TINT = Color(0x33FF9A3C) // warm ember tint: visible on dark and on light surfaces
    }
}
