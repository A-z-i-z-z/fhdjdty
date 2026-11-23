package dev.pocketagent.app.ui.theme

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.pocketagent.app.util.AppLog

/**
 * Switchable pixel-art launcher icons backed by `<activity-alias>` entries in AndroidManifest.xml.
 * `MainActivity` itself stays enabled at all times so deep links, share intents and `am start` never break.
 */
enum class AppIconStyle(
    val aliasClass: String,
    val fgSprite: String,
    val bgSprite: String,
) {
    NEWROZ("dev.pocketagent.app.LauncherNewroz", "icon_fg", "icon_bg"),
    PET("dev.pocketagent.app.LauncherPet", "icon_pet_fg", "icon_pet_bg"),
    FLAME("dev.pocketagent.app.LauncherFlame", "icon_flame_fg", "icon_flame_bg"),
    ZAGROS("dev.pocketagent.app.LauncherZagros", "icon_zagros_fg", "icon_zagros_bg"),
    KAWA("dev.pocketagent.app.LauncherKawa", "icon_kawa_fg", "icon_kawa_bg"),
    ROJ("dev.pocketagent.app.LauncherRoj", "icon_roj_fg", "icon_roj_bg"),
}

object AppIconManager {
    fun current(context: Context): AppIconStyle {
        val pm = context.packageManager
        val pkg = context.packageName
        for (style in AppIconStyle.entries) {
            val state = pm.getComponentEnabledSetting(ComponentName(pkg, style.aliasClass))
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return style
        }
        return AppIconStyle.NEWROZ
    }

    /**
     * Switches the active launcher icon alias. Enables the target alias first before disabling the others
     * so Android never sees zero launcher activities.
     */
    fun apply(context: Context, style: AppIconStyle): Boolean {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName
            pm.setComponentEnabledSetting(
                ComponentName(pkg, style.aliasClass),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (other in AppIconStyle.entries) {
                if (other == style) continue
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, other.aliasClass),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            AppLog.i("Icon", "switched launcher icon to ${style.name}")
            true
        } catch (e: Throwable) {
            AppLog.e("Icon", "failed switching launcher icon to ${style.name}", e)
            false
        }
    }
}
