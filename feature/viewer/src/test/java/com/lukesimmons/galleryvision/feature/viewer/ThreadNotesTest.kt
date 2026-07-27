// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 Luke Simmons <luke5083@live.com>
package com.lukesimmons.galleryvision.feature.viewer

import com.lukesimmons.galleryvision.core.database.entity.NoteEntity
import com.lukesimmons.galleryvision.core.model.NoteTargetKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadNotesTest {

    private fun note(id: Long, parent: Long?, body: String = "n$id") = NoteEntity(
        id = id,
        targetKind = NoteTargetKind.MEDIA,
        targetId = 1,
        body = body,
        parentNoteId = parent,
    )

    @Test
    fun emptyInputYieldsEmptyThread() {
        assertEquals(emptyList(), threadNotes(emptyList()))
    }

    @Test
    fun repliesNestUnderParentsInOrder() {
        val notes = listOf(
            note(3, 1),
            note(1, null),
            note(4, 3),
            note(2, null),
        )
        val threaded = threadNotes(notes)
        assertEquals(listOf(1L, 3L, 4L, 2L), threaded.map { it.first.id })
        assertEquals(listOf(0, 1, 2, 0), threaded.map { it.second })
    }

    @Test
    fun orphansReattachAtRoot() {
        val notes = listOf(note(5, 99), note(1, null))
        val threaded = threadNotes(notes)
        assertEquals(listOf(1L, 5L), threaded.map { it.first.id })
        assertEquals(listOf(0, 0), threaded.map { it.second })
    }

    @Test
    fun deepChainsKeepAscendingIdsWithinALevel() {
        val notes = listOf(note(1, null), note(2, 1), note(3, 1))
        val threaded = threadNotes(notes)
        assertEquals(listOf(1L, 2L, 3L), threaded.map { it.first.id })
        assertEquals(listOf(0, 1, 1), threaded.map { it.second })
    }
}
