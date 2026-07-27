// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.domain.search

import com.lukesimmons.galleryvision.core.model.Matcher
import com.lukesimmons.galleryvision.core.model.SearchField
import com.lukesimmons.galleryvision.core.model.SearchSpec
import com.lukesimmons.galleryvision.core.model.SortDirection
import com.lukesimmons.galleryvision.core.model.SortSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueryParserTest {
    private fun pred(field: SearchField, matcher: Matcher) = SearchSpec.Predicate(field, matcher)

    @Test
    fun bareTermDefaultsToTextLiteral() {
        assertEquals(pred(SearchField.TEXT, Matcher.Literal("hello")), QueryParser.parse("hello"))
    }

    @Test
    fun fieldPredicate() {
        assertEquals(pred(SearchField.PATH, Matcher.Literal("foo")), QueryParser.parse("path:foo"))
        assertEquals(pred(SearchField.PATH, Matcher.Literal("foo")), QueryParser.parse("name:foo"))
    }

    @Test
    fun implicitAnd() {
        val expected = SearchSpec.And(listOf(
            pred(SearchField.TEXT, Matcher.Literal("a")),
            pred(SearchField.TEXT, Matcher.Literal("b")),
        ))
        assertEquals(expected, QueryParser.parse("a b"))
    }

    @Test
    fun explicitBooleanOps() {
        val a = pred(SearchField.TEXT, Matcher.Literal("a"))
        val b = pred(SearchField.TEXT, Matcher.Literal("b"))
        assertEquals(SearchSpec.Or(listOf(a, b)), QueryParser.parse("a OR b"))
        assertEquals(SearchSpec.Xor(listOf(a, b)), QueryParser.parse("a XOR b"))
        assertEquals(SearchSpec.Not(a), QueryParser.parse("NOT a"))
    }

    @Test
    fun precedenceNotOverAndOverXorOverOr() {
        val a = pred(SearchField.TEXT, Matcher.Literal("a"))
        val b = pred(SearchField.TEXT, Matcher.Literal("b"))
        val c = pred(SearchField.TEXT, Matcher.Literal("c"))
        // AND binds tighter than OR
        assertEquals(SearchSpec.Or(listOf(a, SearchSpec.And(listOf(b, c)))), QueryParser.parse("a OR b AND c"))
        // NOT binds tighter than AND
        assertEquals(SearchSpec.And(listOf(SearchSpec.Not(a), b)), QueryParser.parse("NOT a AND b"))
        // XOR binds tighter than OR
        assertEquals(SearchSpec.Or(listOf(SearchSpec.Xor(listOf(a, b)), c)), QueryParser.parse("a XOR b OR c"))
    }

    @Test
    fun parenthesesOverridePrecedence() {
        val a = pred(SearchField.TEXT, Matcher.Literal("a"))
        val b = pred(SearchField.TEXT, Matcher.Literal("b"))
        val c = pred(SearchField.TEXT, Matcher.Literal("c"))
        assertEquals(SearchSpec.And(listOf(SearchSpec.Or(listOf(a, b)), c)), QueryParser.parse("(a OR b) AND c"))
    }

    @Test
    fun phraseRegexWildcard() {
        assertEquals(pred(SearchField.TEXT, Matcher.Phrase("hello world")), QueryParser.parse("text:\"hello world\""))
        assertEquals(pred(SearchField.PATH, Matcher.Regex("foo.*bar")), QueryParser.parse("path:/foo.*bar/"))
        assertEquals(pred(SearchField.PATH, Matcher.Wildcard("img_*")), QueryParser.parse("name:img_*"))
    }

    @Test
    fun dateRanges() {
        val r = QueryParser.parse("taken:2024-01-01..2024-12-31")
        assertTrue(r is SearchSpec.Predicate && r.field == SearchField.TAKEN && r.matcher is Matcher.Range)
        val range = (r as SearchSpec.Predicate).matcher as Matcher.Range
        assertTrue(range.from != null && range.to != null)

        val gt = QueryParser.parse("taken:>2024-01-01") as SearchSpec.Predicate
        val gtRange = gt.matcher as Matcher.Range
        assertTrue(gtRange.from != null && gtRange.to == null)
    }
}

class QueryCompilerTest {
    private fun compile(spec: SearchSpec) = QueryCompiler.compile(spec)

    @Test
    fun textLiteralCompilesToExistsLike() {
        val q = compile(SearchSpec.Predicate(SearchField.TEXT, Matcher.Literal("cat")))
        assertTrue(q.where.contains("EXISTS"))
        assertTrue(q.where.contains("d.valueText LIKE ? ESCAPE '\\'"))
        assertEquals(listOf("%cat%"), q.args)
        assertFalse(q.hasRegex)
    }

    @Test
    fun pathWildcardEscapesAndConverts() {
        val q = compile(SearchSpec.Predicate(SearchField.PATH, Matcher.Wildcard("img_*")))
        assertTrue(q.where.contains("media.path LIKE ? ESCAPE '\\'"))
        assertEquals(listOf("%img\\_%%"), q.args)
    }

    @Test
    fun dateRangeCompilesToBounds() {
        val q = compile(SearchSpec.Predicate(SearchField.TAKEN, Matcher.Range(100L, 200L)))
        assertTrue(q.where.contains("media.dateTaken >= ?"))
        assertTrue(q.where.contains("media.dateTaken <= ?"))
        assertEquals(listOf(100L, 200L), q.args)
    }

    @Test
    fun booleanCompilation() {
        val a = SearchSpec.Predicate(SearchField.TEXT, Matcher.Literal("a"))
        val b = SearchSpec.Predicate(SearchField.TEXT, Matcher.Literal("b"))
        assertTrue(compile(SearchSpec.And(listOf(a, b))).where.contains("AND"))
        assertTrue(compile(SearchSpec.Or(listOf(a, b))).where.contains("OR"))
        val xor = compile(SearchSpec.Xor(listOf(a, b))).where
        assertTrue(xor.contains("OR") && xor.contains("AND NOT"))
        assertTrue(compile(SearchSpec.Not(a)).where.startsWith("NOT"))
    }

    @Test
    fun regexFlagsHasRegex() {
        assertTrue(QueryCompiler.hasRegex(SearchSpec.Predicate(SearchField.PATH, Matcher.Regex("x.*"))))
        assertFalse(QueryCompiler.hasRegex(SearchSpec.Predicate(SearchField.PATH, Matcher.Literal("x"))))
    }

    @Test
    fun sortCompilation() {
        val q = QueryCompiler.compile(
            SearchSpec.And(emptyList()),
            SortSpec(SearchField.PATH, SortDirection.ASC),
        )
        assertEquals("ORDER BY media.path ASC, media.id ASC", q.orderBy)
    }
}

class SearchEvaluatorTest {
    private fun fv(
        path: String = "/x/y.jpg",
        texts: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        taken: Long? = null,
    ) = FieldValues(path = path, created = null, modified = null, added = null, taken = taken, texts = texts, tags = tags)

    @Test
    fun literalMatching() {
        assertTrue(SearchEvaluator.matches(SearchSpec.Predicate(SearchField.TEXT, Matcher.Literal("cat")), fv(texts = listOf("a cat here"))))
        assertFalse(SearchEvaluator.matches(SearchSpec.Predicate(SearchField.TEXT, Matcher.Literal("dog")), fv(texts = listOf("a cat"))))
    }

    @Test
    fun wildcardMatching() {
        assertTrue(SearchEvaluator.wildcardMatch("img_*", "img_001.jpg"))
        assertTrue(SearchEvaluator.wildcardMatch("*.jpg", "x.JPG"))
        assertFalse(SearchEvaluator.wildcardMatch("*.jpg", "x.png"))
        assertTrue(SearchEvaluator.wildcardMatch("a?c", "abc"))
    }

    @Test
    fun regexMatching() {
        assertTrue(SearchEvaluator.matches(SearchSpec.Predicate(SearchField.PATH, Matcher.Regex("img_\\d+")), fv(path = "img_42")))
        assertFalse(SearchEvaluator.matches(SearchSpec.Predicate(SearchField.PATH, Matcher.Regex("^zzz")), fv(path = "img_42")))
    }

    @Test
    fun dateRangeMatching() {
        assertTrue(SearchEvaluator.matches(SearchSpec.Predicate(SearchField.TAKEN, Matcher.Range(100L, 200L)), fv(taken = 150L)))
        assertFalse(SearchEvaluator.matches(SearchSpec.Predicate(SearchField.TAKEN, Matcher.Range(100L, 200L)), fv(taken = 50L)))
    }

    @Test
    fun xorParity() {
        fun p(s: String) = SearchSpec.Predicate(SearchField.TEXT, Matcher.Literal(s))
        val texts = listOf("a", "b")
        // a and b true, c false -> 2 true (even) -> XOR false
        assertFalse(SearchEvaluator.matches(SearchSpec.Xor(listOf(p("a"), p("b"), p("zzz"))), fv(texts = texts)))
        // only a true -> 1 (odd) -> XOR true
        assertTrue(SearchEvaluator.matches(SearchSpec.Xor(listOf(p("a"), p("zzz"), p("yyy"))), fv(texts = texts)))
    }

    @Test
    fun notMatching() {
        assertTrue(SearchEvaluator.matches(SearchSpec.Not(SearchSpec.Predicate(SearchField.TAG, Matcher.Literal("x"))), fv(tags = listOf("y"))))
        assertFalse(SearchEvaluator.matches(SearchSpec.Not(SearchSpec.Predicate(SearchField.TAG, Matcher.Literal("x"))), fv(tags = listOf("x"))))
    }
}
