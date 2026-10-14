package com.pluk.reader.data.repository

import com.pluk.reader.data.local.db.NoteDao
import com.pluk.reader.data.local.db.NoteEntity
import com.pluk.reader.domain.model.Note
import com.pluk.reader.domain.repository.NoteRepository
import com.pluk.reader.domain.repository.NoteSyncStore
import com.pluk.reader.domain.repository.SyncNote
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
) : NoteRepository, NoteSyncStore {
    override fun observe(bookId: String): Flow<List<Note>> = dao.observeByBook(bookId).map { list -> list.map { it.toNote() } }

    override suspend fun get(id: String): Note? = dao.get(id)?.toNote()

    /** Una nota nueva se crea; una existente cambia solo su texto (ANN-005). Las dos quedan pendientes de subir. */
    override suspend fun save(note: Note) {
        if (dao.find(note.id) == null) {
            dao.insert(note.toEntity())
        } else {
            dao.updateText(note.id, note.text, note.updatedAt)
        }
    }

    /** SYN-007: una nota que la nube nunca tuvo se elimina; si no, se marca y el borrado se sube. */
    override suspend fun delete(id: String) {
        if (dao.deleteIfNeverSynced(id) == 0) dao.markDeleted(id, System.currentTimeMillis())
    }

    override suspend fun pending(): List<SyncNote> = dao.getPending().map { SyncNote(it.toNote(), it.deletedAt) }

    override suspend fun hasUnsynced(syncOn: Boolean): Boolean =
        if (syncOn) dao.countUnsynced() > 0 else dao.countAlive() > 0

    override suspend fun markSynced(id: String, editedAt: Long) = dao.markSynced(id, editedAt)

    override suspend fun applyRemote(remote: SyncNote) {
        if (remote.deletedAt != null) {
            dao.deleteIfNotNewer(remote.note.id, remote.editedAt)
            return
        }
        // Dos pasos atómicos: crear si falta y reemplazar solo si la de la nube es más nueva. Una edición local que
        // llega entre uno y otro no se pisa.
        dao.insertIfMissing(remote.note.toEntity().copy(syncedAt = remote.editedAt))
        dao.replaceIfOlder(remote.note.id, remote.note.text, remote.editedAt)
    }

    override suspend fun purgeSyncedDeletions() = dao.purgeSyncedDeletions()

    private fun NoteEntity.toNote() = Note(id, bookId, locatorJson, quote, text, progression, createdAt, updatedAt)

    private fun Note.toEntity() = NoteEntity(id, bookId, locatorJson, quote, text, progression, createdAt, updatedAt)
}
