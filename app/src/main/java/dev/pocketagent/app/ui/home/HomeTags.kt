package dev.pocketagent.app.ui.home

/**
 * The test tags of Home. One tag per node. HOME-HERO writes this registry; the hero and the list both use the names. The first
 * two equal `RobotTags.HOME_STATE` and `RobotTags.HOME_START`, which the older scenarios already know.
 */
object HomeTags {
    const val STATE = "home.state"
    const val START = "home.start"
    const val HERO = "home.hero"
    const val FIRE = "home.fire"
    const val PET = "home.pet"
    const val SKY = "home.sky"
    const val STRIP = "home.strip"
    const val STOP = "home.stop"
    const val CANCEL = "home.cancel"
    const val ERROR = "home.error"
    const val ERROR_DETAILS = "home.error.details"
    const val ASK = "home.ask"
    const val SEND = "home.send"
    const val AVATARS = "home.avatars"
    const val AVATAR_NEW = "home.avatar.new"
    const val GROUP = "home.group"
    const val RECENT = "home.recent"
    const val RECENT_EMPTY = "home.recent.empty"
    const val USAGE = "home.usage"
    const val WOODPILE = "home.woodpile"
    const val SKELETON = "home.skeleton"
    const val TILE_HERMES = "home.tile.hermes"
    const val TILE_TERMINAL = "home.tile.terminal"
    const val TILE_ROUTINES = "home.tile.routines"
    const val TILE_SAVED = "home.tile.saved"

    fun suggest(i: Int) = "home.suggest.$i"
    fun avatar(i: Int) = "home.avatar.$i"
    fun recent(i: Int) = "home.recent.$i"
    fun row(id: String) = "home.row.$id"
}
