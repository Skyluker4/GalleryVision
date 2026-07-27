// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.domain.search

import com.lukesimmons.galleryvision.core.model.Matcher
import com.lukesimmons.galleryvision.core.model.SearchField
import com.lukesimmons.galleryvision.core.model.SearchSpec

/**
 * Recursive-descent parser for the GalleryVision search grammar (R8).
 *
 * Grammar (tightest to loosest precedence): NOT > AND > XOR > OR; parentheses override.
 *   expr   := or
 *   or     := xor ('OR' xor)*
 *   xor    := and ('XOR' and)*
 *   and    := unary (('AND')? unary)*     // juxtaposition is implicit AND
 *   unary  := 'NOT' unary | atom
 *   atom   := '(' expr ')' | predicate
 *   pred   := [field ':'] matcher         // bare term defaults to text
 * matcher := "phrase" | /regex/ | wildcard*? | literal | date..range | >date | <date
 */
object QueryParser {

    class ParseException(message: String) : Exception(message)

    private sealed interface Tok {
        data object LParen : Tok
        data object RParen : Tok
        data object And : Tok
        data object Or : Tok
        data object Xor : Tok
        data object Not : Tok
        data class Field(val name: String) : Tok
        data class Word(val value: String) : Tok
    }

    private class Lexer(input: String) {
        val tokens: List<Tok> = tokenize(input)

        private fun tokenize(s: String): List<Tok> {
            val out = ArrayList<Tok>()
            var i = 0
            val n = s.length
            while (i < n) {
                val c = s[i]
                when {
                    c.isWhitespace() -> i++
                    c == '(' -> { out.add(Tok.LParen); i++ }
                    c == ')' -> { out.add(Tok.RParen); i++ }
                    c == '"' -> { val end = s.indexOf('"', i + 1).takeIf { it >= 0 } ?: n; out.add(Tok.Word("\"" + s.substring(i + 1, end) + "\"")); i = if (end < n) end + 1 else n }
                    c == '/' -> { val end = s.indexOf('/', i + 1).takeIf { it >= 0 } ?: n; out.add(Tok.Word("/" + s.substring(i + 1, end) + "/")); i = if (end < n) end + 1 else n }
                    c.isLetter() -> {
                        var j = i
                        while (j < n && s[j].isLetter()) j++
                        if (j < n && s[j] == ':' && isFieldName(s.substring(i, j))) {
                            out.add(Tok.Field(s.substring(i, j).lowercase()))
                            i = readValue(s, j + 1, out)
                        } else {
                            val start = i
                            while (i < n && !s[i].isWhitespace() && s[i] != '(' && s[i] != ')') i++
                            when (s.substring(start, i).uppercase()) {
                                "AND" -> out.add(Tok.And)
                                "OR" -> out.add(Tok.Or)
                                "XOR" -> out.add(Tok.Xor)
                                "NOT" -> out.add(Tok.Not)
                                else -> out.add(Tok.Word(s.substring(start, i)))
                            }
                        }
                    }
                    else -> {
                        val start = i
                        while (i < n && !s[i].isWhitespace() && s[i] != '(' && s[i] != ')') i++
                        out.add(Tok.Word(s.substring(start, i)))
                    }
                }
            }
            return out
        }

        private fun readValue(s: String, start: Int, out: MutableList<Tok>): Int {
            val n = s.length
            if (start >= n) throw ParseException("Expected a value after field")
            return when (s[start]) {
                '"' -> { val end = s.indexOf('"', start + 1).takeIf { it >= 0 } ?: n; out.add(Tok.Word("\"" + s.substring(start + 1, end) + "\"")); if (end < n) end + 1 else n }
                '/' -> { val end = s.indexOf('/', start + 1).takeIf { it >= 0 } ?: n; out.add(Tok.Word("/" + s.substring(start + 1, end) + "/")); if (end < n) end + 1 else n }
                else -> {
                    var i = start
                    while (i < n && !s[i].isWhitespace() && s[i] != '(' && s[i] != ')') i++
                    out.add(Tok.Word(s.substring(start, i)))
                    i
                }
            }
        }

        private fun isFieldName(word: String): Boolean =
            word.lowercase() in setOf(
                "path", "name", "filename", "file", "created", "modified", "added", "taken", "date",
                "text", "ocr", "content", "tag", "object", "face", "person", "note",
            )
    }

    private class Parser(private val tokens: List<Tok>) {
        private var pos = 0

        private fun peek(): Tok? = tokens.getOrNull(pos)
        private fun next(): Tok? = tokens.getOrNull(pos++)

        fun parse(): SearchSpec {
            if (tokens.isEmpty()) return SearchSpec.And(emptyList())
            val result = parseOr()
            if (pos != tokens.size) throw ParseException("Unexpected token at position $pos")
            return result
        }

        private fun parseOr(): SearchSpec {
            var left = parseXor()
            val terms = mutableListOf(left)
            while (peek() is Tok.Or) {
                next()
                terms.add(parseXor())
            }
            return if (terms.size == 1) left else SearchSpec.Or(terms)
        }

        private fun parseXor(): SearchSpec {
            var left = parseAnd()
            val terms = mutableListOf(left)
            while (peek() is Tok.Xor) {
                next()
                terms.add(parseAnd())
            }
            return if (terms.size == 1) left else SearchSpec.Xor(terms)
        }

        private fun parseAnd(): SearchSpec {
            val terms = mutableListOf(parseUnary())
            while (true) {
                when (peek()) {
                    is Tok.And -> { next(); terms.add(parseUnary()) }
                    is Tok.Word, is Tok.Field, is Tok.Not, is Tok.LParen -> terms.add(parseUnary())
                    else -> break
                }
            }
            return if (terms.size == 1) terms[0] else SearchSpec.And(terms)
        }

        private fun parseUnary(): SearchSpec =
            if (peek() is Tok.Not) {
                next()
                SearchSpec.Not(parseUnary())
            } else {
                parseAtom()
            }

        private fun parseAtom(): SearchSpec =
            when (val t = next()) {
                is Tok.LParen -> {
                    val inner = parseOr()
                    if (next() !is Tok.RParen) throw ParseException("Missing closing parenthesis")
                    inner
                }
                is Tok.Word -> predicate(null, t.value)
                is Tok.Field -> {
                    when (val v = next()) {
                        is Tok.Word -> predicate(t.name, v.value)
                        else -> throw ParseException("Expected a value after '${t.name}:'")
                    }
                }
                else -> throw ParseException("Unexpected token: $t")
            }

        private fun predicate(fieldName: String?, rawValue: String): SearchSpec {
            val field = resolveField(fieldName)
            val matcher = resolveMatcher(field, rawValue)
            return SearchSpec.Predicate(field, matcher)
        }

        private fun resolveField(name: String?): SearchField {
            if (name == null) return SearchField.TEXT
            return when (name) {
                "path", "name", "filename", "file" -> SearchField.PATH
                "created" -> SearchField.CREATED
                "modified" -> SearchField.MODIFIED
                "added" -> SearchField.ADDED
                "taken", "date" -> SearchField.TAKEN
                "text", "ocr", "content" -> SearchField.TEXT
                "tag" -> SearchField.TAG
                "object" -> SearchField.OBJECT
                "face", "person" -> SearchField.FACE
                "note" -> SearchField.NOTE
                else -> throw ParseException("Unknown field '$name'")
            }
        }

        private fun resolveMatcher(field: SearchField, raw: String): Matcher {
            val isDateField = field == SearchField.CREATED || field == SearchField.MODIFIED ||
                field == SearchField.ADDED || field == SearchField.TAKEN
            if (isDateField) {
                parseDateRange(raw)?.let { return it }
            }
            return when {
                raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"") ->
                    Matcher.Phrase(raw.substring(1, raw.length - 1))
                raw.length >= 2 && raw.startsWith("/") && raw.endsWith("/") ->
                    Matcher.Regex(raw.substring(1, raw.length - 1))
                raw.contains('*') || raw.contains('?') -> Matcher.Wildcard(raw)
                else -> Matcher.Literal(raw)
            }
        }

        /** Supports A..B, >A, <A, >=A, <=A, and a bare date (that whole day). Returns null if not a date form. */
        private fun parseDateRange(raw: String): Matcher.Range? {
            fun epoch(date: String, endOfDay: Boolean): Long? = parseEpoch(date, endOfDay)
            return when {
                raw.contains("..") -> {
                    val (a, b) = raw.split("..", limit = 2)
                    Matcher.Range(epoch(a, false), epoch(b, true))
                }
                raw.startsWith(">=") -> Matcher.Range(epoch(raw.drop(2), false), null)
                raw.startsWith("<=") -> Matcher.Range(null, epoch(raw.drop(2), true))
                raw.startsWith(">") -> Matcher.Range(epoch(raw.drop(1), true), null)
                raw.startsWith("<") -> Matcher.Range(null, epoch(raw.drop(1), false))
                else -> {
                    val day = epoch(raw, false)
                    val dayEnd = epoch(raw, true)
                    if (day != null && dayEnd != null) Matcher.Range(day, dayEnd) else null
                }
            }
        }
    }

    fun parse(query: String): SearchSpec = Parser(Lexer(query).tokens).parse()
}

/** Parse YYYY-MM-DD (or epoch seconds) to epoch millis; [endOfDay] rolls to 23:59:59.999. */
internal fun parseEpoch(date: String, endOfDay: Boolean): Long? {
    val trimmed = date.trim()
    trimmed.toLongOrNull()?.let { return it }
    val parts = trimmed.split('-')
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    if (m !in 1..12 || d !in 1..31) return null
    var days = 0L
    for (year in 1970 until y) days += if (isLeap(year)) 366 else 365
    val mdays = intArrayOf(31, if (isLeap(y)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    for (mo in 0 until m - 1) days += mdays[mo]
    days += (d - 1)
    val base = days * 86_400_000L
    return if (endOfDay) base + 86_399_999L else base
}

private fun isLeap(y: Int): Boolean = (y % 4 == 0 && y % 100 != 0) || y % 400 == 0
