package com.pluk.reader.notes

import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Notas en memoria, con el mismo orden que la base (ANN-010). */
class FakeNotes(initial: List<Note> = emptyList()) : NoteRepository {
    val all = MutableStateFlow(initial)

    override fun observe(bookId: String): Flow<List<Note>> =
        all.map { list -> list.filter { it.bookId == bookId }.sortedWith(compareBy({ it.progression }, { it.createdAt })) }

    override suspend fun get(id: String): Note? = all.value.firstOrNull { it.id == id }

    override suspend fun save(note: Note) {
        all.value = all.value.filterNot { it.id == note.id } + note
    }

    override suspend fun delete(id: String) {
        all.value = all.value.filterNot { it.id == id }
    }
}

fun note(
    id: String,
    bookId: String = "hash-del-libro",
    progression: Double = 0.5,
    text: String = "nota $id",
    locatorJson: String = """{"href":"c.xhtml","id":"$id"}""",
) = Note(id, bookId, locatorJson, "texto citado $id", text, progression, createdAt = 1, updatedAt = 1)
