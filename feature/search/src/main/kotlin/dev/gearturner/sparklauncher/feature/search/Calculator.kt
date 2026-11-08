package dev.gearturner.sparklauncher.feature.search

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * evaluates simple arithmetic typed into search. anything that is not clearly a calculation
 * returns null, including bare numbers and names with digits like "2048", so app results are
 * never pushed aside by a guess.
 */
object Calculator {

    fun evaluate(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.length > MAX_LENGTH) return null
        val tokens = tokenize(text) ?: return null
        // a lone number, or a leading minus on one, is not a calculation worth showing
        if (tokens.withIndex().none { (i, t) -> t is Token.Op && !(i == 0 && t.symbol == '-') }) return null
        return try {
            val parser = Parser(tokens)
            val value = parser.parseExpression()
            if (!parser.atEnd()) return null
            format(value)
        } catch (e: ArithmeticException) {
            null
        } catch (e: CalcError) {
            null
        }
    }

    private fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(SIGNIFICANT_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        // plain notation for everyday sizes, scientific only when it would otherwise be unreadable
        val magnitude = rounded.precision() - rounded.scale()
        return if (magnitude in -6..SIGNIFICANT_DIGITS + 2) rounded.toPlainString() else rounded.toString()
    }

    private sealed interface Token {
        data class Num(val value: BigDecimal) : Token
        data class Op(val symbol: Char) : Token
        data object Open : Token
        data object Close : Token
    }

    private class CalcError : Exception()

    private fun tokenize(text: String): List<Token>? {
        val tokens = ArrayList<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || c == '.' -> {
                    val start = i
                    while (i < text.length && (text[i].isDigit() || text[i] == '.' || text[i] == ',')) i++
                    val raw = text.substring(start, i).replace(",", "")
                    tokens += Token.Num(raw.toBigDecimalOrNull() ?: return null)
                }
                c == '(' -> { tokens += Token.Open; i++ }
                c == ')' -> { tokens += Token.Close; i++ }
                else -> {
                    val op = OPERATOR_ALIASES[c] ?: return null
                    tokens += Token.Op(op)
                    i++
                }
            }
        }
        return tokens
    }

    /** recursive descent with a depth cap so a wall of brackets cannot overflow the stack */
    private class Parser(private val tokens: List<Token>) {
        private var pos = 0
        private var depth = 0

        fun atEnd() = pos == tokens.size

        fun parseExpression(): BigDecimal {
            var left = parseTerm()
            while (true) {
                val op = peekOp() ?: return left
                if (op != '+' && op != '-') return left
                pos++
                val right = parseTerm()
                left = if (op == '+') left.add(right, CONTEXT) else left.subtract(right, CONTEXT)
            }
        }

        private fun parseTerm(): BigDecimal {
            var left = parseUnary()
            while (true) {
                val op = peekOp() ?: return left
                if (op != '*' && op != '/') return left
                pos++
                val right = parseUnary()
                left = if (op == '*') left.multiply(right, CONTEXT) else left.divide(right, CONTEXT)
            }
        }

        private fun parseUnary(): BigDecimal {
            if (peekOp() == '-') {
                pos++
                return parseUnary().negate()
            }
            if (peekOp() == '+') {
                pos++
                return parseUnary()
            }
            return parsePower()
        }

        private fun parsePower(): BigDecimal {
            val base = parsePercent()
            if (peekOp() != '^') return base
            pos++
            // right associative, so 2^3^2 is 2^9 as on a calculator
            val exponent = parseUnary()
            val whole = try {
                exponent.intValueExact()
            } catch (e: ArithmeticException) {
                throw CalcError()
            }
            if (whole !in -MAX_EXPONENT..MAX_EXPONENT) throw CalcError()
            return if (whole >= 0) base.pow(whole, CONTEXT) else BigDecimal.ONE.divide(base.pow(-whole, CONTEXT), CONTEXT)
        }

        private fun parsePercent(): BigDecimal {
            var value = parseAtom()
            while (peekOp() == '%') {
                pos++
                value = value.divide(HUNDRED, CONTEXT)
            }
            return value
        }

        private fun parseAtom(): BigDecimal {
            return when (val token = tokens.getOrNull(pos) ?: throw CalcError()) {
                is Token.Num -> { pos++; token.value }
                Token.Open -> {
                    if (++depth > MAX_DEPTH) throw CalcError()
                    pos++
                    val inner = parseExpression()
                    if (tokens.getOrNull(pos) != Token.Close) throw CalcError()
                    pos++
                    depth--
                    inner
                }
                else -> throw CalcError()
            }
        }

        private fun peekOp(): Char? = (tokens.getOrNull(pos) as? Token.Op)?.symbol
    }

    private const val MAX_LENGTH = 200
    private const val MAX_DEPTH = 32
    private const val MAX_EXPONENT = 999
    private const val SIGNIFICANT_DIGITS = 10
    private val CONTEXT = MathContext(34, RoundingMode.HALF_EVEN)
    private val HUNDRED = BigDecimal(100)
    private val OPERATOR_ALIASES = mapOf(
        '+' to '+', '-' to '-', '−' to '-',
        '*' to '*', 'x' to '*', 'X' to '*', '×' to '*',
        '/' to '/', '÷' to '/',
        '^' to '^', '%' to '%',
    )
}
