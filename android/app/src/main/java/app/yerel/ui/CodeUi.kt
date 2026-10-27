package app.yerel.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.yerel.core.CodeBlock
import app.yerel.core.CodeBlocks
import app.yerel.core.CodeCheck
import app.yerel.core.CodeLang
import app.yerel.core.JsRun
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What code blocks in the chat can do; provided by the chat screen. */
class CodeActions(
    val open: (CodeBlock, CodeTab) -> Unit,
)

enum class CodeTab { PREVIEW, CODE, CONSOLE }

val LocalCodeActions = compositionLocalOf<CodeActions?> { null }

// ---- syntax highlighting -------------------------------------------------------------------

data class CodeColors(val keyword: Color, val string: Color, val comment: Color, val number: Color, val tag: Color, val attr: Color)

@Composable
fun codeColors(): CodeColors {
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3 < 0.5f }
    return if (dark) {
        CodeColors(Color(0xFFC792EA), Color(0xFFC3E88D), Color(0xFF7F8C98), Color(0xFFF78C6C), Color(0xFF82AAFF), Color(0xFFFFCB6B))
    } else {
        CodeColors(Color(0xFF7C3AED), Color(0xFF15803D), Color(0xFF6B7280), Color(0xFFC2410C), Color(0xFF1D4ED8), Color(0xFFA16207))
    }
}

object SyntaxHighlighter {
    private val KEYWORDS = mapOf(
        "c" to "function return if else for while do switch case break continue new delete typeof instanceof in of var let const class extends super this import export from default try catch finally throw async await yield null undefined true false void static public private protected interface enum implements package fun val when is object companion override data sealed lateinit def lambda pass raise with as elif except None True False and or not global nonlocal print self struct impl fn mut pub use mod match loop int float double char bool long short unsigned signed auto sizeof namespace template typename using std include define",
        "css" to "important media keyframes from to import font-face supports",
    ).mapValues { it.value.split(' ').toSet() }

    private val C_LIKE = Regex(
        "(?<comment>//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$)|#(?![a-fA-F0-9]{3,8}\\b)[^\\n]*)|" +
            "(?<string>\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?|`(?:\\\\.|[^`\\\\])*`?)|" +
            "(?<number>\\b\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?\\b|\\b0x[0-9a-fA-F]+\\b)|" +
            "(?<word>[A-Za-z_][A-Za-z0-9_]*)",
    )
    private val MARKUP = Regex(
        "(?<comment><!--[\\s\\S]*?(?:-->|$))|(?<tag></?[A-Za-z][A-Za-z0-9:-]*|/?>)|" +
            "(?<attr>\\s[A-Za-z_:][A-Za-z0-9_:.-]*(?==))|(?<string>\"[^\"]*\"?|'[^']*'?)",
    )

    fun highlight(code: String, lang: CodeLang, c: CodeColors): AnnotatedString {
        if (code.length > 60_000) return AnnotatedString(code)
        return buildAnnotatedString {
            append(code)
            val markup = lang == CodeLang.HTML || lang == CodeLang.SVG || lang == CodeLang.XML
            val hashComments = lang == CodeLang.PYTHON || lang == CodeLang.OTHER
            if (markup) {
                for (m in MARKUP.findAll(code)) {
                    val g = m.groups
                    val style = when {
                        g["comment"] != null -> SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
                        g["tag"] != null -> SpanStyle(color = c.tag)
                        g["attr"] != null -> SpanStyle(color = c.attr)
                        g["string"] != null -> SpanStyle(color = c.string)
                        else -> null
                    }
                    style?.let { addStyle(it, m.range.first, m.range.last + 1) }
                }
                // scripts and styles inside the page get the code colors
                for (m in Regex("(<(script|style)\\b[^>]*>)([\\s\\S]*?)(</\\2>|$)", RegexOption.IGNORE_CASE).findAll(code)) {
                    val body = m.groups[3] ?: continue
                    val inner = if (m.groupValues[2].equals("style", true)) CodeLang.CSS else CodeLang.JAVASCRIPT
                    highlightCode(body.value, inner, c, body.range.first, hashComments = false)
                }
            } else {
                highlightCode(code, lang, c, 0, hashComments)
            }
        }
    }

    private fun AnnotatedString.Builder.highlightCode(code: String, lang: CodeLang, c: CodeColors, offset: Int, hashComments: Boolean) {
        val words = KEYWORDS[if (lang == CodeLang.CSS) "css" else "c"]!!
        for (m in C_LIKE.findAll(code)) {
            val g = m.groups
            val style = when {
                g["comment"] != null -> if (m.value.startsWith("#") && !hashComments) null else SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
                g["string"] != null -> SpanStyle(color = c.string)
                g["number"] != null -> SpanStyle(color = c.number)
                g["word"] != null -> if (m.value in words) SpanStyle(color = c.keyword) else null
                else -> null
            }
            style?.let { addStyle(it, offset + m.range.first, offset + m.range.last + 1) }
        }
    }
}

/** Highlighting for the editable code field. */
private class HighlightTransformation(private val lang: CodeLang, private val colors: CodeColors) : VisualTransformation {
    override fun filter(text: AnnotatedString) =
        TransformedText(SyntaxHighlighter.highlight(text.text, lang, colors), OffsetMapping.Identity)
}

// ---- code block in the chat -----------------------------------------------------------------

private const val COLLAPSED_LINES = 24

@Composable
fun CodeBlockView(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme
    val actions = LocalCodeActions.current
    val block = remember(lang, code) { CodeBlock(lang, code) }
    val kind = block.kind
    val colors = codeColors()
    val lines = remember(code) { code.count { it == '\n' } + 1 }
    var expanded by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(1500); copied = false }
    }
    val shown = if (expanded || lines <= COLLAPSED_LINES + 4) code else code.lineSequence().take(COLLAPSED_LINES).joinToString("\n")
    val highlighted = remember(shown, kind, colors) { SyntaxHighlighter.highlight(shown, kind, colors) }
    Surface(shape = RoundedCornerShape(14.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(scheme.surfaceContainerHighest).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lang.ifBlank { kind.label.lowercase() } + " · $lines satır",
                    style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (actions != null) {
                    if (kind.previewable) {
                        IconButton(onClick = { actions.open(block, CodeTab.PREVIEW) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Visibility, "Önizle", Modifier.size(19.dp), tint = scheme.primary)
                        }
                    }
                    if (kind.runnable) {
                        IconButton(onClick = { actions.open(block, CodeTab.CONSOLE) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.PlayArrow, "Çalıştır", Modifier.size(21.dp), tint = scheme.primary)
                        }
                    }
                    IconButton(onClick = { actions.open(block, CodeTab.CODE) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.OpenInFull, "Tam ekran", Modifier.size(17.dp), tint = scheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)); copied = true }, modifier = Modifier.size(40.dp)) {
                    Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, "Kopyala", Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                }
            }
            Text(
                highlighted,
                style = MonoStyle,
                color = scheme.onSurface,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
            )
            if (shown.length < code.length) {
                TextButton(onClick = { expanded = true }, modifier = Modifier.padding(start = 4.dp)) {
                    Text("${lines - COLLAPSED_LINES} satır daha göster")
                }
            }
        }
    }
}

// ---- full screen viewer, editor and preview -------------------------------------------------

/**
 * Code studio: run HTML/SVG in an offline browser, run JavaScript, edit the code, check it,
 * and send errors back to the model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeStudio(
    initial: CodeBlock,
    initialTab: CodeTab,
    allowNetwork: Boolean,
    runJs: suspend (String) -> JsRun,
    onAskFix: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = initial.kind
    val tabs = buildList {
        if (kind.previewable) add(CodeTab.PREVIEW)
        add(CodeTab.CODE)
        if (kind.previewable || kind.runnable) add(CodeTab.CONSOLE)
    }
    var tab by remember { mutableStateOf(if (initialTab in tabs) initialTab else CodeTab.CODE) }
    var code by remember { mutableStateOf(initial.code) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val console = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val problems = remember(code) { CodeCheck.check(kind, code) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(kind.mime)) { uri ->
        uri?.let { u -> runCatching { context.contentResolver.openOutputStream(u)?.use { it.write(code.toByteArray()) } } }
    }

    fun run() {
        if (!kind.runnable || running) return
        running = true
        console.clear()
        scope.launch {
            val r = runJs(code)
            console += r.logs
            r.result?.takeIf { it != "null" }?.let { console += "← $it" }
            r.error?.let { console += "error: $it" }
            if (r.timedOut) console += "error: zaman aşımı (sonsuz döngü olabilir)"
            if (r.ok && r.logs.isEmpty() && r.result == null) console += "(çıktı yok)"
            running = false
        }
    }
    LaunchedEffect(Unit) { if (initialTab == CodeTab.CONSOLE && kind.runnable) run() }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(kind.label, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kapat") } },
                    actions = {
                        if (kind.previewable) {
                            IconButton(onClick = { console.clear(); reloadKey++; tab = CodeTab.PREVIEW }) { Icon(Icons.Rounded.Refresh, "Yenile") }
                        }
                        if (kind.runnable) {
                            IconButton(onClick = { tab = CodeTab.CONSOLE; run() }, enabled = !running) { Icon(Icons.Rounded.PlayArrow, "Çalıştır") }
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) { Icon(Icons.Rounded.ContentCopy, "Kopyala") }
                        IconButton(onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, code)
                            context.startActivity(Intent.createChooser(send, "Kodu paylaş"))
                        }) { Icon(Icons.Rounded.Share, "Paylaş") }
                        IconButton(onClick = { save.launch("yerel.${kind.ext}") }) { Icon(Icons.Rounded.Save, "Kaydet") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
                if (tabs.size > 1) {
                    SecondaryTabRow(selectedTabIndex = tabs.indexOf(tab)) {
                        tabs.forEach { t ->
                            Tab(selected = t == tab, onClick = { tab = t }, text = {
                                Text(
                                    when (t) {
                                        CodeTab.PREVIEW -> "Önizleme"
                                        CodeTab.CODE -> "Kod"
                                        CodeTab.CONSOLE -> "Konsol" + if (console.isNotEmpty()) " (${console.size})" else ""
                                    },
                                )
                            })
                        }
                    }
                }
                val errors = console.filter { it.startsWith("error") } + problems
                if (errors.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                errors.first() + if (errors.size > 1) "  (+${errors.size - 1})" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { tab = if (CodeTab.CONSOLE in tabs) CodeTab.CONSOLE else CodeTab.CODE },
                            )
                            TextButton(onClick = {
                                onAskFix(fixRequest(kind, code, errors))
                                onDismiss()
                            }) {
                                Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Modele düzelt")
                            }
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        CodeTab.PREVIEW -> PreviewWebView(
                            html = remember(code) { CodeBlocks.previewHtml(CodeBlock(initial.lang, code)) },
                            reloadKey = reloadKey, allowNetwork = allowNetwork,
                            onConsole = { console += it },
                        )
                        CodeTab.CODE -> CodeEditor(code, kind) { code = it }
                        CodeTab.CONSOLE -> ConsoleView(console, running)
                    }
                }
            }
        }
    }
}

/** The message sent to the model when the user asks it to fix an error found in the studio. */
fun fixRequest(kind: CodeLang, code: String, errors: List<String>): String =
    "I ran your ${kind.label} code and got these errors:\n" + errors.take(8).joinToString("\n") { "- ${it.removePrefix("error: ")}" } +
        "\n\nThis is the code I ran:\n```${kind.ext}\n$code\n```\nFix the errors and reply with the complete corrected code."

@Composable
private fun CodeEditor(code: String, kind: CodeLang, onChange: (String) -> Unit) {
    val colors = codeColors()
    val transformation = remember(kind, colors) { HighlightTransformation(kind, colors) }
    val lines = code.count { it == '\n' } + 1
    Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            (1..lines).joinToString("\n"),
            style = MonoStyle, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 12.dp),
        )
        BasicTextField(
            value = code,
            onValueChange = onChange,
            textStyle = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = transformation,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
        )
    }
}

@Composable
private fun ConsoleView(lines: List<String>, running: Boolean) {
    val scheme = MaterialTheme.colorScheme
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(if (running) "Çalışıyor…" else "Konsol boş", color = scheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().background(scheme.surfaceContainerLowest), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item { Spacer(Modifier.height(8.dp)) }
        items(lines) { line ->
            val err = line.startsWith("error")
            val warn = line.startsWith("warn")
            Text(
                line, style = MonoStyle,
                color = when {
                    err -> scheme.error
                    warn -> Color(0xFFB45309)
                    else -> scheme.onSurface
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
            )
        }
    }
}

/** An offline browser for generated pages: JavaScript on, network and file access off, console captured. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewWebView(html: String, reloadKey: Int, allowNetwork: Boolean, onConsole: (String) -> Unit) {
    val onConsoleState = androidx.compose.runtime.rememberUpdatedState(onConsole)
    var crashed by remember { mutableStateOf(false) }
    if (crashed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TextButton(onClick = { crashed = false }) { Text("Sayfa durdu · yeniden yükle") }
        }
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = !allowNetwork
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    // links do not navigate away from the preview
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        crashed = true
                        return true
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                        val level = when (m.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> "error: "
                            ConsoleMessage.MessageLevel.WARNING -> "warn: "
                            else -> ""
                        }
                        val where = if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR && m.lineNumber() > 0) " (line ${m.lineNumber()})" else ""
                        onConsoleState.value(level + m.message() + where)
                        return true
                    }

                    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult): Boolean {
                        onConsoleState.value("alert: ${message.orEmpty()}")
                        result.confirm()
                        return true
                    }
                }
            }
        },
        update = { web ->
            val key = html.hashCode() * 31 + reloadKey
            if (web.tag != key) {
                web.tag = key
                web.loadDataWithBaseURL(PREVIEW_BASE, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private const val PREVIEW_BASE = "https://preview.yerel.invalid/"
