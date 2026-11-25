package dev.pocketagent.app.ui.text

/**
 * Which pixel icons turn around in a right-to-left layout. The names are PixelIcon entry names. The
 * arrow of BACK points left, SEND and PLAY point right and SHARE is a node with an arrow; every other
 * icon is symmetric or means the same in both directions. The scene never mirrors.
 */
object Mirroring {
    val MIRRORED: Set<String> = setOf("BACK", "SEND", "PLAY", "SHARE")

    fun iconMirrors(iconName: String): Boolean = iconName in MIRRORED
}
