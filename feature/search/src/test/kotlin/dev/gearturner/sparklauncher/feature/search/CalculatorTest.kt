package dev.gearturner.sparklauncher.feature.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {

    @Test
    fun followsOrderOfOperations() {
        assertEquals("14", Calculator.evaluate("2+3*4"))
        assertEquals("84", Calculator.evaluate("12*(3+4)"))
        assertEquals("512", Calculator.evaluate("2^3^2"))
    }

    @Test
    fun handlesUnaryMinusDecimalsAndAliases() {
        assertEquals("-2", Calculator.evaluate("-5+3"))
        assertEquals("-8", Calculator.evaluate("-5-3"))
        assertEquals("0.3", Calculator.evaluate("0.1+0.2"))
        assertEquals("12", Calculator.evaluate("3 × 4"))
        assertEquals("2.5", Calculator.evaluate("5 ÷ 2"))
        assertEquals("6", Calculator.evaluate("2x3"))
    }

    @Test
    fun percentDividesByHundred() {
        assertEquals("15", Calculator.evaluate("200*7.5%"))
    }

    @Test
    fun roundsLongResults() {
        assertEquals("0.3333333333", Calculator.evaluate("1/3"))
    }

    @Test
    fun divideByZeroAndMalformedInputGiveNothing() {
        assertNull(Calculator.evaluate("1/0"))
        assertNull(Calculator.evaluate("2+"))
        assertNull(Calculator.evaluate("(2+3"))
        assertNull(Calculator.evaluate("2+3)"))
        assertNull(Calculator.evaluate("1..2+1"))
    }

    @Test
    fun namesAndBareNumbersAreNotCalculations() {
        assertNull(Calculator.evaluate("2048"))
        assertNull(Calculator.evaluate("-5"))
        assertNull(Calculator.evaluate("covid-19"))
        assertNull(Calculator.evaluate("maps"))
        assertNull(Calculator.evaluate("x"))
    }

    @Test
    fun deepNestingAndHugePowersAreRefusedSafely() {
        val deep = "(".repeat(100) + "1" + ")".repeat(100) + "+1"
        assertNull(Calculator.evaluate(deep))
        assertNull(Calculator.evaluate("9^99999"))
        assertEquals("2", Calculator.evaluate("((1))+1"))
    }
}
