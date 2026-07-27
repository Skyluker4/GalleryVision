// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.core.model

/** Matcher for a single field predicate (R8). */
sealed interface Matcher {
    /** Plain substring/term match. */
    data class Literal(
        val value: String,
    ) : Matcher

    /** Quoted phrase. */
    data class Phrase(
        val value: String,
    ) : Matcher

    /** Shell-style wildcard: * and ? */
    data class Wildcard(
        val pattern: String,
    ) : Matcher

    /** Full regular expression. */
    data class Regex(
        val pattern: String,
    ) : Matcher

    /** Numeric/date range, bounds in epoch millis (null = unbounded). */
    data class Range(
        val from: Long?,
        val to: Long?,
    ) : Matcher
}

/**
 * Immutable search abstract syntax tree (R8, C4). Compiles to a parameterized
 * SQL query by the query compiler in :domain.
 * Precedence (tightest to loosest): NOT > AND > XOR > OR.
 */
sealed interface SearchSpec {
    data class And(
        val terms: List<SearchSpec>,
    ) : SearchSpec

    data class Or(
        val terms: List<SearchSpec>,
    ) : SearchSpec

    data class Xor(
        val terms: List<SearchSpec>,
    ) : SearchSpec

    data class Not(
        val term: SearchSpec,
    ) : SearchSpec

    data class Predicate(
        val field: SearchField,
        val matcher: Matcher,
    ) : SearchSpec
}

/** Sort key + direction. */
data class SortSpec(
    val field: SearchField,
    val direction: SortDirection,
)
