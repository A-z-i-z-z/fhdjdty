package app.yerel.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.yerel.core.JsRun
import app.yerel.core.JsSandbox
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs JavaScript in an offline, invisible WebView: no network, no file access, a fresh page per run.
 * Blocking; must not be called on the main thread.
 */
class WebJsSandbox(private val context: Context) : JsSandbox {
    private val main = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var web: WebView? = null
    @Volatile private var pending: Pair<String, CompletableFuture<String>>? = null
    private var counter = 0

    override fun run(code: String, timeoutMs: Long): JsRun = exec("run", code, timeoutMs)

    override fun syntaxError(code: String): String? = exec("check", code, 3000).error

    private fun exec(mode: String, code: String, timeoutMs: Long): JsRun = synchronized(lock) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "WebJsSandbox must not run on the main thread" }
        val id = "r${++counter}"
        val future = CompletableFuture<String>()
        pending = id to future
        main.post {
            try {
                view().loadDataWithBaseURL(BASE_URL, page(id, mode, code), "text/html", "utf-8", null)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        try {
            val o = JSONObject(future.get(timeoutMs + 1500, TimeUnit.MILLISECONDS))
            val logs = o.optJSONArray("logs")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            JsRun(logs, o.optString("result").takeIf { !o.isNull("result") }, o.optString("error").takeIf { !o.isNull("error") })
        } catch (e: TimeoutException) {
            reset()
            JsRun(emptyList(), null, null, timedOut = true)
        } catch (e: Exception) {
            Log.w(TAG, "sandbox failed", e)
            reset()
            JsRun(emptyList(), null, "sandbox error: ${e.cause?.message ?: e.message}")
        } finally {
            pending = null
        }
    }

    /** A stuck script keeps the renderer busy; drop the view (and the renderer on Android 10+). */
    private fun reset() {
        main.post {
            web?.let { w ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) runCatching { w.webViewRenderProcess?.terminate() }
                w.destroy()
            }
            web = null
        }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun done(id: String, json: String) {
            val p = pending
            if (p != null && p.first == id) p.second.complete(json)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun view(): WebView = web ?: WebView(context.applicationContext).also { w ->
        w.settings.apply {
            javaScriptEnabled = true
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
        }
        w.addJavascriptInterface(Bridge(), "YerelJs")
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (web === view) web = null
                view.destroy()
                pending?.second?.completeExceptionally(IllegalStateException("the JavaScript engine stopped"))
                return true
            }
        }
        web = w
    }

    companion object {
        private const val TAG = "WebJsSandbox"
        const val BASE_URL = "https://sandbox.yerel.invalid/"

        private fun page(id: String, mode: String, code: String) = """<!doctype html><html><head><meta charset="utf-8"><script>
(function(){
  var logs = [], finished = false, id = ${JSONObject.quote(id)}, code = ${JSONObject.quote(code).replace("<!--", "<\\u0021--")};
  function fmt(v) {
    if (typeof v === 'string') return v;
    if (v instanceof Error) return v.name + ': ' + v.message;
    try { var s = JSON.stringify(v); return s === undefined ? String(v) : s; } catch (e) { return String(v); }
  }
  ['log', 'info', 'debug', 'warn', 'error'].forEach(function (k) {
    console[k] = function () {
      var line = Array.prototype.map.call(arguments, fmt).join(' ');
      logs.push(k === 'warn' || k === 'error' ? k + ': ' + line : line);
      if (logs.length > 500) logs.shift();
    };
  });
  function where(e) { var m = /<anonymous>:(\d+):(\d+)/.exec(e && e.stack || ''); return m ? ' (line ' + m[1] + ')' : ''; }
  function describe(e) { return (e && e.name ? e.name + ': ' + e.message : fmt(e)) + where(e); }
  function finish(result, error) {
    if (finished) return;
    finished = true;
    YerelJs.done(id, JSON.stringify({ logs: logs, result: result, error: error }));
  }
  // parses the code inside a function that is never called: syntax errors come with a line number
  function syntaxError() {
    var err = null;
    var prev = window.onerror;
    window.onerror = function (msg, src, line) { err = String(msg) + (line ? ' (line ' + Math.max(1, line - 1) + ')' : ''); return true; };
    var s = document.createElement('script');
    s.text = 'async function __yerel_check() {\n' + code + '\n}';
    document.head.appendChild(s);
    window.onerror = prev;
    return err;
  }
  window.onerror = function (msg, src, line) { finish(null, String(msg) + (line ? ' (line ' + line + ')' : '')); return true; };
  window.addEventListener('unhandledrejection', function (ev) { finish(null, 'Unhandled promise rejection: ' + describe(ev.reason)); });
  var bad = syntaxError();
  if ('$mode' === 'check' || bad) { finish(null, bad); return; }
  var r;
  try {
    r = (0, eval)(code);
  } catch (e) {
    if (e instanceof SyntaxError && /await|return/.test(e.message)) {
      try { r = (0, eval)('(async function () {\n' + code + '\n})()'); } catch (e2) { finish(null, describe(e2)); return; }
    } else { finish(null, describe(e)); return; }
  }
  if (r && typeof r.then === 'function') {
    r.then(function (v) { setTimeout(function () { finish(v === undefined ? null : fmt(v), null); }, 50); },
           function (e) { finish(null, describe(e)); });
  } else {
    // timers and promises get a moment to print
    setTimeout(function () { finish(r === undefined ? null : fmt(r), null); }, 250);
  }
})();
</script></head><body></body></html>"""
    }
}
