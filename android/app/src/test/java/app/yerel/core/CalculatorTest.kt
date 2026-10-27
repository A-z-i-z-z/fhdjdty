package app.yerel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalculatorTest {
    private fun calc(s: String) = Calculator.format(Calculator.evaluate(s))

    @Test
    fun arithmetic() {
        assertEquals("12", calc("7+5"))
        assertEquals("97.75", calc("(17*23)/4"))
        assertEquals("-8", calc("-2^3"))
        assertEquals("512", calc("2^3^2"))
        assertEquals("120", calc("5!"))
        assertEquals("1", calc("10 % 3"))
        assertEquals("3.5", calc("3,5"))
        assertEquals("12", calc("3 × 4"))
        assertEquals("2.82842712475", calc("sqrt(2)^3"))
        assertEquals("1", calc("sin(pi/2)"))
        assertEquals("3", calc("log(1000)"))
        assertEquals("1500", calc("1.5e3"))
        assertEquals("2.71828182846", calc("e"))
    }

    @Test
    fun errors() {
        assertThrows(CalcException::class.java) { Calculator.evaluate("1/0") }
        assertThrows(CalcException::class.java) { Calculator.evaluate("(1+2") }
        assertThrows(CalcException::class.java) { Calculator.evaluate("foo(2)") }
        assertThrows(CalcException::class.java) { Calculator.evaluate("2 2") }
        assertThrows(CalcException::class.java) { Calculator.evaluate("") }
        assertThrows(CalcException::class.java) { Calculator.evaluate("sqrt(-1)") }
    }
}
