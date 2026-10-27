package app.yerel

import android.app.Application
import android.content.Context
import app.yerel.data.AndroidHttp
import app.yerel.data.ConversationStore
import app.yerel.data.MemoryStore
import app.yerel.data.ModelManager
import app.yerel.data.ModelStore
import app.yerel.data.SettingsStore
import app.yerel.engine.LlamaEngine

/** Process-wide singletons, created on first use. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    val models = ModelStore(context)
    val memory = MemoryStore(context)
    val chats = ConversationStore(context)
    val engine = LlamaEngine(context)
    val manager = ModelManager(context, models) { settings.value.hfToken }
    val http = AndroidHttp()
}

class YerelApp : Application() {
    val container by lazy { AppContainer(this) }
}

val Context.container get() = (applicationContext as YerelApp).container
