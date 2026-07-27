// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.domain.search

import com.lukesimmons.galleryvision.core.model.Matcher
import com.lukesimmons.galleryvision.core.model.SearchField
import com.lukesimmons.galleryvision.core.model.SearchSpec
import com.lukesimmons.galleryvision.core.model.SortDirection
import com.lukesimmons.galleryvision.core.model.SortSpec

/**
 * Compiles a SearchSpec into a parameterized SQL WHERE clause over the media read model (R8, C4).
 *
 * Non-regex predicates compile to SQL (LIKE with ESCAPE, range comparisons, EXISTS subqueries on
 * detection/tag/note/face). If ANY regex matcher is present, [CompiledQuery.hasRegex] is set and
 * the caller must evaluate the whole AST in Kotlin instead (SQLite has no native REGEXP); the SQL
 * is then only used to narrow candidates, never to decide regex membership.
 */
object QueryCompiler {

    data class CompiledQuery(
        val where: String,
        val args: List<Any?>,
        val orderBy: String,
        val hasRegex: Boolean,
    )

    private class Ctx {
        val args = ArrayList<Any?>()
        var hasRegex = false
        fun arg(v: Any?): String {
            args.add(v)
            return "?"
        }
    }

    fun compile(spec: SearchSpec, sort: SortSpec? = null): CompiledQuery {
        val ctx = Ctx()
        val where = ctx.emit(spec)
        return CompiledQuery(
            where = where,
            args = ctx.args,
            orderBy = orderBy(sort),
            hasRegex = ctx.hasRegex,
        )
    }

    fun hasRegex(spec: SearchSpec): Boolean =
        when (spec) {
            is SearchSpec.And -> spec.terms.any { hasRegex(it) }
            is SearchSpec.Or -> spec.terms.any { hasRegex(it) }
            is SearchSpec.Xor -> spec.terms.any { hasRegex(it) }
            is SearchSpec.Not -> hasRegex(spec.term)
            is SearchSpec.Predicate -> spec.matcher is Matcher.Regex
        }

    private fun Ctx.emit(spec: SearchSpec): String =
        when (spec) {
            is SearchSpec.And ->
                if (spec.terms.isEmpty()) "1=1" else spec.terms.joinToString(" AND ") { "( ${emit(it)} )" }
            is SearchSpec.Or ->
                if (spec.terms.isEmpty()) "1=0" else spec.terms.joinToString(" OR ") { "( ${emit(it)} )" }
            is SearchSpec.Xor -> emitXor(spec.terms)
            is SearchSpec.Not -> "NOT ( ${emit(spec.term)} )"
            is SearchSpec.Predicate -> emitPredicate(spec.field, spec.matcher)
        }

    private fun Ctx.emitXor(terms: List<SearchSpec>): String {
        if (terms.isEmpty()) return "1=0"
        // Left-associative binary XOR: A XOR B == (A OR B) AND NOT (A AND B).
        fun xor2(a: String, b: String) = "(( $a ) OR ( $b )) AND NOT (( $a ) AND ( $b ))"
        return terms.map { emit(it) }.reduce { acc, next -> xor2(acc, next) }
    }

    private fun Ctx.emitPredicate(field: SearchField, matcher: Matcher): String {
        if (matcher is Matcher.Regex) {
            hasRegex = true
            return "1=1" // caller narrows candidates; regex membership decided in Kotlin
        }
        return when (field) {
            SearchField.PATH -> stringClause("media.path", matcher)
            SearchField.CREATED -> dateClause("media.dateCreated", matcher)
            SearchField.MODIFIED -> dateClause("media.dateModified", matcher)
            SearchField.ADDED -> dateClause("media.dateAdded", matcher)
            SearchField.TAKEN -> dateClause("media.dateTaken", matcher)
            SearchField.TEXT -> existsText(matcher)
            SearchField.OBJECT -> existsLabel("OBJECT", matcher)
            SearchField.TAG -> existsTag(matcher)
            SearchField.FACE -> existsFace(matcher)
            SearchField.NOTE -> existsNote(matcher)
        }
    }

    private fun Ctx.stringClause(column: String, matcher: Matcher): String =
        when (matcher) {
            is Matcher.Literal -> "$column LIKE ${arg(likeEscape(matcher.value, '%'))} ESCAPE '\\'"
            is Matcher.Phrase -> "$column LIKE ${arg(likeEscape(matcher.value, '%'))} ESCAPE '\\'"
            is Matcher.Wildcard -> "$column LIKE ${arg(wildcardToLike(matcher.pattern))} ESCAPE '\\'"
            is Matcher.Range -> dateClause(column, matcher)
            is Matcher.Regex -> "1=1"
        }

    private fun Ctx.dateClause(column: String, matcher: Matcher): String =
        when (matcher) {
            is Matcher.Range -> buildString {
                append("( ")
                if (matcher.from != null) append("$column >= ${arg(matcher.from)}") else append("1=1")
                append(" AND ")
                if (matcher.to != null) append("$column <= ${arg(matcher.to)}") else append("1=1")
                append(" )")
            }
            else -> stringClause(column, matcher)
        }

    private fun Ctx.existsText(matcher: Matcher): String =
        "EXISTS (SELECT 1 FROM detection d WHERE d.mediaId = media.id AND d.kind = 'TEXT' AND " +
            stringClause("d.valueText", matcher) + ")"

    private fun Ctx.existsLabel(kind: String, matcher: Matcher): String =
        "EXISTS (SELECT 1 FROM detection d WHERE d.mediaId = media.id AND d.kind = '$kind' AND " +
            stringClause("d.label", matcher) + ")"

    private fun Ctx.existsTag(matcher: Matcher): String =
        "EXISTS (SELECT 1 FROM media_tag mt JOIN tag t ON t.id = mt.tagId " +
            "WHERE mt.mediaId = media.id AND " + stringClause("t.name", matcher) + ")"

    private fun Ctx.existsFace(matcher: Matcher): String =
        "EXISTS (SELECT 1 FROM detection d JOIN face_cluster fc ON fc.id = d.clusterId " +
            "WHERE d.mediaId = media.id AND d.kind = 'FACE' AND " + stringClause("fc.name", matcher) + ")"

    private fun Ctx.existsNote(matcher: Matcher): String =
        "EXISTS (SELECT 1 FROM note n WHERE n.targetKind = 'MEDIA' AND n.targetId = media.id AND " +
            stringClause("n.body", matcher) + ")"

    private fun likeEscape(value: String, wrap: Char): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return if (wrap == '%') "%$escaped%" else escaped
    }

    private fun wildcardToLike(pattern: String): String {
        val sb = StringBuilder()
        for (c in pattern) {
            when (c) {
                '*' -> sb.append('%')
                '?' -> sb.append('_')
                '\\' -> sb.append("\\\\")
                '%' -> sb.append("\\%")
                '_' -> sb.append("\\_")
                else -> sb.append(c)
            }
        }
        return "%$sb%"
    }

    private fun orderBy(sort: SortSpec?): String {
        if (sort == null) return "ORDER BY COALESCE(media.dateTaken, media.dateAdded, media.dateModified, 0) DESC"
        val column = when (sort.field) {
            SearchField.PATH -> "media.path"
            SearchField.CREATED -> "media.dateCreated"
            SearchField.MODIFIED -> "media.dateModified"
            SearchField.ADDED -> "media.dateAdded"
            SearchField.TAKEN -> "media.dateTaken"
            SearchField.TEXT -> "(SELECT d.valueText FROM detection d WHERE d.mediaId = media.id AND d.kind = 'TEXT' ORDER BY d.confidence DESC LIMIT 1)"
            SearchField.OBJECT -> "(SELECT d.label FROM detection d WHERE d.mediaId = media.id AND d.kind = 'OBJECT' ORDER BY d.confidence DESC LIMIT 1)"
            SearchField.TAG -> "(SELECT t.name FROM media_tag mt JOIN tag t ON t.id = mt.tagId WHERE mt.mediaId = media.id ORDER BY t.name LIMIT 1)"
            SearchField.FACE -> "(SELECT fc.name FROM detection d JOIN face_cluster fc ON fc.id = d.clusterId WHERE d.mediaId = media.id AND d.kind = 'FACE' ORDER BY fc.name LIMIT 1)"
            SearchField.NOTE -> "(SELECT n.body FROM note n WHERE n.targetKind = 'MEDIA' AND n.targetId = media.id ORDER BY n.id LIMIT 1)"
        }
        val dir = if (sort.direction == SortDirection.DESC) "DESC" else "ASC"
        return "ORDER BY $column $dir, media.id ASC"
    }
}
