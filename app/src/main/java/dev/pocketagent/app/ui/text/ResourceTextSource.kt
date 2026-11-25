package dev.pocketagent.app.ui.text

import android.content.res.Resources

/**
 * The app's own resources as one layer: the strings and plurals of the language the given Resources
 * were made for. An entry the resources do not have gives null, so the next layer is asked.
 */
class ResourceTextSource(private val resources: Resources) : TextSource {
    override fun string(key: TextKey): String? = try {
        resources.getString(key.id)
    } catch (_: Resources.NotFoundException) {
        null
    }

    override fun plural(key: TextKey, count: Int): String? = try {
        resources.getQuantityText(key.id, count).toString()
    } catch (_: Resources.NotFoundException) {
        null
    }
}
