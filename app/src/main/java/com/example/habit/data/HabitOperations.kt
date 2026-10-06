package com.example.habit.data

import com.example.habit.domain.*
import java.time.LocalDate

/** Local mutation port; historical corrections compare the exact facts shown to the user. */
interface HabitOperations {
    suspend fun correctChecked(id: Long, date: LocalDate, openedOn: LocalDate,
        expected: HabitSettings, before: CompletionValue?, value: CompletionValue?)
    suspend fun archive(id: Long): ArchiveChange
    suspend fun undoArchive(change: ArchiveChange): Boolean
    suspend fun delete(id: Long)
}
