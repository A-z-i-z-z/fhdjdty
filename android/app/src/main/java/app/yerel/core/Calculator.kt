package app.yerel.core

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor

class CalcException(message: String) : Exception(message)

/** Evaluates arithmetic for the calculator tool: + - * / % ^, parentheses, functions and constants. */
object Calculator {
    private val functions: Map<String, (Double) -> Double> = mapOf(
        "sqrt" to Math::sqrt, "cbrt" to Math::cbrt, "abs" to Math::abs,
        "sin" to Math::sin, "cos" to Math::cos, "tan" to Math::tan,
        "asin" to Math::asin, "acos" to Math::acos, "atan" to Math::atan,
        "ln" to Math::log, "log" to Math::log10, "log10" to Math::log10, "log2" to { x -> Math.log(x) / Math.log(2.0) },
        "exp" to Math::exp, "floor" to Math::floor, "ceil" to Math::ceil, "round" to { x -> Math.round(x).toDouble() },
    )
    private val constants = mapOf("pi" to Math.PI, "π" to Math.PI, "e" to Math.E)

    fun evaluate(expression: String): Double {
        val p = Parser(normalize(expression))
        val v = p.expr()
        p.skipSpaces()
        if (!p.atEnd()) throw CalcException("unexpected '${p.rest()}'")
        if (v.isNaN() || v.isInfinite()) throw CalcException("result is not a finite number")
        return v
    }

    fun format(v: Double): String {
        if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
        return BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private fun normalize(s: String) = s
        .replace('×', '*').replace('÷', '/').replace('−', '-')
        .replace("**", "^")
        // a comma between digits is the Turkish decimal separator
        .replace(Regex("(?<=\\d),(?=\\d)"), ".")

    private class Parser(val s: String) {
        var i = 0

        fun atEnd() = i >= s.length
        fun rest() = s.substring(i)
        fun skipSpaces() { while (i < s.length && s[i].isWhitespace()) i++ }

        private fun eat(c: Char): Boolean {
            skipSpaces()
            if (i < s.length && s[i] == c) { i++; return true }
            return false
        }

        fun expr(): Double {
            var v = term()
            while (true) {
                v = when {
                    eat('+') -> v + term()
                    eat('-') -> v - term()
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = unary()
            while (true) {
                v = when {
                    eat('*') -> v * unary()
                    eat('/') -> {
                        val d = unary()
                        if (d == 0.0) throw CalcException("division by zero")
                        v / d
                    }
                    eat('%') -> v % unary()
                    else -> return v
                }
            }
        }

        private fun unary(): Double = when {
            eat('-') -> -unary()
            eat('+') -> unary()
            else -> power()
        }

        private fun power(): Double {
            val base = postfix()
            return if (eat('^')) Math.pow(base, unary()) else base
        }

        private fun postfix(): Double {
            var v = primary()
            while (eat('!')) {
                if (v < 0 || v != floor(v) || v > 170) throw CalcException("factorial needs an integer 0..170")
                var f = 1.0
                for (k in 2..v.toInt()) f *= k
                v = f
            }
            return v
        }

        private fun primary(): Double {
            skipSpaces()
            if (eat('(')) {
                val v = expr()
                if (!eat(')')) throw CalcException("missing ')'")
                return v
            }
            val start = i
            if (i < s.length && (s[i].isDigit() || s[i] == '.')) {
                while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                if (i < s.length && (s[i] == 'e' || s[i] == 'E') && i + 1 < s.length &&
                    (s[i + 1].isDigit() || ((s[i + 1] == '-' || s[i + 1] == '+') && i + 2 < s.length && s[i + 2].isDigit()))
                ) {
                    i += 2
                    while (i < s.length && s[i].isDigit()) i++
                }
                return s.substring(start, i).toDoubleOrNull() ?: throw CalcException("bad number '${s.substring(start, i)}'")
            }
            while (i < s.length && (s[i].isLetter() || s[i].isDigit() || s[i] == '_')) i++
            val name = s.substring(start, i).lowercase()
            if (name.isEmpty()) throw CalcException(if (atEnd()) "unexpected end" else "unexpected '${s[i]}'")
            constants[name]?.let { return it }
            val fn = functions[name] ?: throw CalcException("unknown name '$name'")
            if (!eat('(')) throw CalcException("'$name' needs parentheses")
            val arg = expr()
            if (!eat(')')) throw CalcException("missing ')'")
            return fn(arg)
        }
    }
}
