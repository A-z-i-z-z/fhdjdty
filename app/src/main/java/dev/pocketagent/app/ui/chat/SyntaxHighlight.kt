package dev.pocketagent.app.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.pocketagent.app.ui.theme.PocketColors

/**
 * Lightweight, zero-dependency syntax highlighter for fenced code blocks + runnable command helper.
 * Theme-aware warm hearth palette for both dark and light themes.
 */
object SyntaxHighlight {
    private val RUNNABLE_LANGS = setOf("sh", "bash", "zsh", "shell", "console", "python", "py", "python3")

    private val COMMON_KEYWORDS = setOf(
        "fun", "val", "var", "class", "object", "interface", "enum", "data", "sealed", "return", "if", "else",
        "when", "for", "while", "do", "try", "catch", "finally", "throw", "import", "package", "private", "public",
        "internal", "override", "suspend", "true", "false", "null", "this", "super", "is", "in", "as", "by",
        "def", "lambda", "from", "with", "yield", "async", "await", "elif", "pass", "break", "continue", "None",
        "True", "False", "and", "or", "not", "const", "let", "function", "export", "default", "new", "typeof",
        "switch", "case", "struct", "fn", "mut", "pub", "impl", "match", "use", "mod", "crate", "type", "go",
        "func", "chan", "defer", "select", "SELECT", "FROM", "WHERE", "INSERT", "INTO", "UPDATE", "DELETE",
        "JOIN", "ON", "GROUP", "BY", "ORDER", "LIMIT", "CREATE", "TABLE", "VALUES", "SET", "AND", "OR", "NOT",
        "echo", "export", "local", "then", "fi", "done", "esac", "elif", "sudo", "cd", "ls", "grep", "curl",
        "cat", "mkdir", "rm", "cp", "mv", "chmod", "python3", "pip", "uv", "git", "hermes",
    )

    private val TOKEN_RE = Regex(
        """(?<comment>//[^\n]*|#[^\n]*|/\*[\s\S]*?\*/)""" +
            """|(?<string>"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')""" +
            """|(?<annotation>@[A-Za-z_][A-Za-z0-9_]*|--?[A-Za-z][A-Za-z0-9_-]*)""" +
            """|(?<number>\b0x[0-9a-fA-F]+\b|\b\d+(?:\.\d+)?[fFlL]?\b)""" +
            """|(?<word>\b[A-Za-z_][A-Za-z0-9_]*\b)""",
    )

    fun isRunnable(lang: String, code: String): Boolean {
        val l = lang.trim().lowercase()
        if (code.isBlank()) return false
        if (l in RUNNABLE_LANGS) return true
        val first = code.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        return l.isEmpty() && (first.startsWith("hermes ") || first.startsWith("$ ") || first.startsWith("ls ") || first.startsWith("uname"))
    }

    /** Formats a shell or Python code block into a single command ready for [TerminalSession.run]. */
    fun toTerminalCommand(lang: String, code: String): String {
        val l = lang.trim().lowercase()
        val trimmed = code.trim()
        if (l == "python" || l == "py" || l == "python3") {
            val escaped = trimmed.replace("'", "'\"'\"'")
            return "python3 -c '$escaped'"
        }
        return trimmed.lines()
            .map { line -> line.trimEnd().let { if (it.startsWith("$ ")) it.removePrefix("$ ") else it } }
            .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
            .joinToString("\n")
    }

    /** Produces a theme-aware syntax-colored [AnnotatedString]. Falls back safely on very large blocks. */
    fun highlight(code: String, lang: String, colors: PocketColors): AnnotatedString {
        if (code.length > 16_000) return AnnotatedString(code)
        val kwColor = if (colors.isDark) Color(0xFFFFB454) else Color(0xFFB34700)
        val strColor = if (colors.isDark) Color(0xFF8BD49C) else Color(0xFF1F6E3D)
        val numColor = if (colors.isDark) Color(0xFFF48FB1) else Color(0xFFA62658)
        val cmtColor = colors.textMuted
        val annColor = if (colors.isDark) Color(0xFF7CC6FE) else Color(0xFF1B5E9E)
        val typeColor = if (colors.isDark) Color(0xFFE5C07B) else Color(0xFF7B4B00)
        val isJsonOrYaml = lang.trim().lowercase() in setOf("json", "yaml", "yml")

        return buildAnnotatedString {
            append(code)
            for (m in TOKEN_RE.findAll(code)) {
                val range = m.range
                val text = m.value
                val style = when {
                    m.groups["comment"] != null -> {
                        // In bash/python/yaml `#` is a comment; in C/Kotlin/JS `#` is rare, still fine.
                        SpanStyle(color = cmtColor, fontStyle = FontStyle.Italic)
                    }
                    m.groups["string"] != null -> {
                        val after = code.drop(range.last + 1).trimStart()
                        if (isJsonOrYaml && after.startsWith(":")) SpanStyle(color = annColor, fontWeight = FontWeight.Medium)
                        else SpanStyle(color = strColor)
                    }
                    m.groups["annotation"] != null -> SpanStyle(color = annColor)
                    m.groups["number"] != null -> SpanStyle(color = numColor)
                    m.groups["word"] != null -> when {
                        text in COMMON_KEYWORDS -> SpanStyle(color = kwColor, fontWeight = FontWeight.SemiBold)
                        text.first().isUpperCase() && text.length > 1 -> SpanStyle(color = typeColor)
                        else -> null
                    }
                    else -> null
                }
                if (style != null) addStyle(style, range.first, range.last + 1)
            }
        }
    }
}
