package dev.pocketagent.app.ui.text

import androidx.annotation.StringRes
import dev.pocketagent.app.R
import java.util.Locale

/**
 * Spoken names of the pixel icons and of the bot avatars, keyed by the enum entry name (PixelIcon.name,
 * AvatarId.name). A control that has no description of its own speaks this text instead of the English
 * name of its enum entry. The keys are eng_icon_ and eng_avatar_ plus the lower-case entry name; a unit
 * test fails when an enum gains an entry without a key. An unknown name gives null.
 */
object EnumLabels {
    private val ICONS: Map<String, Int> = mapOf(
        "HEARTH" to R.string.eng_icon_hearth,
        "CHATS" to R.string.eng_icon_chats,
        "BOTS" to R.string.eng_icon_bots,
        "SCROLL" to R.string.eng_icon_scroll,
        "TERMINAL" to R.string.eng_icon_terminal,
        "GEAR" to R.string.eng_icon_gear,
        "SEND" to R.string.eng_icon_send,
        "STOP" to R.string.eng_icon_stop,
        "PLUS" to R.string.eng_icon_plus,
        "BACK" to R.string.eng_icon_back,
        "MORE" to R.string.eng_icon_more,
        "COPY" to R.string.eng_icon_copy,
        "EDIT" to R.string.eng_icon_edit,
        "TRASH" to R.string.eng_icon_trash,
        "PIN" to R.string.eng_icon_pin,
        "GROUP" to R.string.eng_icon_group,
        "TOOL" to R.string.eng_icon_tool,
        "MEMORY" to R.string.eng_icon_memory,
        "SKILL" to R.string.eng_icon_skill,
        "SOUL" to R.string.eng_icon_soul,
        "SEARCH" to R.string.eng_icon_search,
        "REFRESH" to R.string.eng_icon_refresh,
        "PLAY" to R.string.eng_icon_play,
        "CHECK" to R.string.eng_icon_check,
        "CLOSE" to R.string.eng_icon_close,
        "PALETTE" to R.string.eng_icon_palette,
        "GLOBE" to R.string.eng_icon_globe,
        "BUG" to R.string.eng_icon_bug,
        "DOWN" to R.string.eng_icon_down,
        "UP" to R.string.eng_icon_up,
        "SPARK" to R.string.eng_icon_spark,
        "SHARE" to R.string.eng_icon_share,
        "USER" to R.string.eng_icon_user,
        "KEY" to R.string.eng_icon_key,
        "INFO" to R.string.eng_icon_info,
        "MIC" to R.string.eng_icon_mic,
        "CLIP" to R.string.eng_icon_clip,
        "CAMERA" to R.string.eng_icon_camera,
        "IMAGE" to R.string.eng_icon_image,
        "FILE" to R.string.eng_icon_file,
        "BOLT" to R.string.eng_icon_bolt,
        "FORGE" to R.string.eng_icon_forge,
        "VOLUME" to R.string.eng_icon_volume,
        "MUTE" to R.string.eng_icon_mute,
        "FORK" to R.string.eng_icon_fork,
        "SLIDERS" to R.string.eng_icon_sliders,
        "FILTER" to R.string.eng_icon_filter,
        "APPICON" to R.string.eng_icon_appicon,
        "WAVE" to R.string.eng_icon_wave,
        "MUSIC" to R.string.eng_icon_music,
        "CLOCK" to R.string.eng_icon_clock,
        "STAR" to R.string.eng_icon_star,
        "STAR_OFF" to R.string.eng_icon_star_off,
        "BELL" to R.string.eng_icon_bell,
        "CHART" to R.string.eng_icon_chart,
        "ARCHIVE" to R.string.eng_icon_archive,
        "DOWNLOAD" to R.string.eng_icon_download,
        "UPLOAD" to R.string.eng_icon_upload,
        "MOON" to R.string.eng_icon_moon,
        "LINK" to R.string.eng_icon_link,
    )

    private val AVATARS: Map<String, Int> = mapOf(
        "NEWT" to R.string.eng_avatar_newt,
        "WOLF" to R.string.eng_avatar_wolf,
        "FOX" to R.string.eng_avatar_fox,
        "OWL" to R.string.eng_avatar_owl,
        "EAGLE" to R.string.eng_avatar_eagle,
        "GOAT" to R.string.eng_avatar_goat,
        "PARTRIDGE" to R.string.eng_avatar_partridge,
        "BEE" to R.string.eng_avatar_bee,
        "LYNX" to R.string.eng_avatar_lynx,
        "CAT" to R.string.eng_avatar_cat,
        "ROBOT" to R.string.eng_avatar_robot,
        "SUN" to R.string.eng_avatar_sun,
        "MOUNTAIN" to R.string.eng_avatar_mountain,
        "HORSE" to R.string.eng_avatar_horse,
        "BEAR" to R.string.eng_avatar_bear,
        "DRAGON" to R.string.eng_avatar_dragon,
    )

    /** The string resource that names the icon [enumName] (a PixelIcon name), or null. */
    @StringRes
    fun icon(enumName: String): Int? = ICONS[enumName.uppercase(Locale.ROOT)]

    /** The string resource that names the avatar [enumName] (an AvatarId name), or null. */
    @StringRes
    fun avatar(enumName: String): Int? = AVATARS[enumName.uppercase(Locale.ROOT)]
}
