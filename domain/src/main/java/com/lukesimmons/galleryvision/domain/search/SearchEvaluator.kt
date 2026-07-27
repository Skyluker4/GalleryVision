// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.domain.search

import com.lukesimmons.galleryvision.core.model.Matcher
import com.lukesimmons.galleryvision.core.model.SearchField
import com.lukesimmons.galleryvision.core.model.SearchSpec
import com.lukesimmons.galleryvision.core.model.SortDirection
import com.lukesimmons.galleryvision.core.model.SortSpec

/** Field values for one media item, used to evaluate a SearchSpec in Kotlin (regex + tests). */
data class FieldValues(
    val path: String,
    val created: Long?,
    val modified: Long?,
    val added: Long?,
    val taken: Long?,
    val texts: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val objects: List<String> = emptyList(),
    val faces: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Evaluates a SearchSpec against a media item's field values (all matcher kinds). */
object SearchEvaluator {
    fun matches(
        spec: SearchSpec,
        fv: FieldValues,
    ): Boolean =
        when (spec) {
            is SearchSpec.And -> spec.terms.all { matches(it, fv) }
            is SearchSpec.Or -> spec.terms.any { matches(it, fv) }
            is SearchSpec.Xor -> spec.terms.count { matches(it, fv) } % 2 == 1
            is SearchSpec.Not -> !matches(spec.term, fv)
            is SearchSpec.Predicate -> matchPredicate(spec.field, spec.matcher, fv)
        }

    private fun matchPredicate(
        field: SearchField,
        matcher: Matcher,
        fv: FieldValues,
    ): Boolean =
        when (field) {
            SearchField.PATH -> matchString(fv.path, matcher)
            SearchField.CREATED -> matchDate(fv.created, matcher)
            SearchField.MODIFIED -> matchDate(fv.modified, matcher)
            SearchField.ADDED -> matchDate(fv.added, matcher)
            SearchField.TAKEN -> matchDate(fv.taken, matcher)
            SearchField.TEXT -> fv.texts.any { matchString(it, matcher) }
            SearchField.TAG -> fv.tags.any { matchString(it, matcher) }
            SearchField.OBJECT -> fv.objects.any { matchString(it, matcher) }
            SearchField.FACE -> fv.faces.any { matchString(it, matcher) }
            SearchField.NOTE -> fv.notes.any { matchString(it, matcher) }
        }

    private fun matchDate(
        value: Long?,
        matcher: Matcher,
    ): Boolean =
        when (matcher) {
            is Matcher.Range -> {
                val from = matcher.from
                val to = matcher.to
                value != null && (from == null || value >= from) && (to == null || value <= to)
            }
            else -> value != null && matchString(value.toString(), matcher)
        }

    private fun matchString(
        value: String,
        matcher: Matcher,
    ): Boolean =
        when (matcher) {
            is Matcher.Literal -> value.contains(matcher.value, ignoreCase = true)
            is Matcher.Phrase -> value.contains(matcher.value, ignoreCase = false)
            is Matcher.Wildcard -> wildcardMatch(matcher.pattern, value)
            is Matcher.Regex -> Regex(matcher.pattern).containsMatchIn(value)
            is Matcher.Range -> {
                val from = matcher.from
                val to = matcher.to
                value.toLongOrNull()?.let { v -> (from == null || v >= from) && (to == null || v <= to) } ?: false
            }
        }

    /** Shell-style wildcard: * = any run, ? = one char. Case-insensitive. */
    fun wildcardMatch(
        pattern: String,
        value: String,
    ): Boolean = wildcardTable(pattern.lowercase(), value.lowercase())

    private fun wildcardTable(
        p: String,
        v: String,
    ): Boolean {
        val dp = Array(p.length + 1) { BooleanArray(v.length + 1) }
        dp[0][0] = true
        for (i in 1..p.length) {
            dp[i][0] = p[i - 1] == '*' && dp[i - 1][0]
        }
        for (i in 1..p.length) {
            for (j in 1..v.length) {
                dp[i][j] =
                    when (p[i - 1]) {
                        '*' -> dp[i - 1][j] || dp[i][j - 1]
                        '?' -> dp[i - 1][j - 1]
                        else -> dp[i - 1][j - 1] && p[i - 1] == v[j - 1]
                    }
            }
        }
        return dp[p.length][v.length]
    }

    fun comparator(sort: SortSpec): Comparator<FieldValues> {
        val c: Comparator<FieldValues> =
            when (sort.field) {
                SearchField.PATH -> compareBy { it.path.lowercase() }
                SearchField.CREATED -> compareBy { it.created ?: Long.MIN_VALUE }
                SearchField.MODIFIED -> compareBy { it.modified ?: Long.MIN_VALUE }
                SearchField.ADDED -> compareBy { it.added ?: Long.MIN_VALUE }
                SearchField.TAKEN -> compareBy { it.taken ?: Long.MIN_VALUE }
                SearchField.TEXT -> compareBy { it.texts.firstOrNull()?.lowercase() ?: "" }
                SearchField.TAG -> compareBy { it.tags.firstOrNull()?.lowercase() ?: "" }
                SearchField.OBJECT -> compareBy { it.objects.firstOrNull()?.lowercase() ?: "" }
                SearchField.FACE -> compareBy { it.faces.firstOrNull()?.lowercase() ?: "" }
                SearchField.NOTE -> compareBy { it.notes.firstOrNull()?.lowercase() ?: "" }
            }
        return if (sort.direction == SortDirection.DESC) c.reversed() else c
    }
}
