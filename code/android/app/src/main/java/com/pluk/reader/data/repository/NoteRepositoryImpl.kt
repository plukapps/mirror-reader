package com.pluk.reader.data.repository

import com.pluk.reader.data.local.db.NoteDao
import com.pluk.reader.data.local.db.NoteEntity
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NoteRepository {
    override fun observe(bookId: String): Flow<List<Note>> = dao.observeByBook(bookId).map { list -> list.map { it.toNote() } }

    override suspend fun get(id: String): Note? = dao.get(id)?.toNote()

    override suspend fun save(note: Note) = dao.upsert(note.toEntity())

    override suspend fun delete(id: String) = dao.delete(id)

    private fun NoteEntity.toNote() = Note(id, bookId, locatorJson, quote, text, progression, createdAt, updatedAt)

    private fun Note.toEntity() = NoteEntity(id, bookId, locatorJson, quote, text, progression, createdAt, updatedAt)
}
