package com.pluk.reader.data.repository

import com.pluk.reader.data.local.db.ReadingPositionDao
import com.pluk.reader.data.local.db.ReadingPositionEntity
import com.pluk.reader.domain.model.LocalPosition
import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.repository.PositionRepository
import com.pluk.reader.domain.repository.PositionSyncStore
import javax.inject.Inject

class PositionRepositoryImpl @Inject constructor(
    private val dao: ReadingPositionDao,
) : PositionRepository, PositionSyncStore {
    override suspend fun get(bookId: String): String? = dao.get(bookId)?.locatorJson

    override suspend fun save(bookId: String, locatorJson: String, totalProgression: Double?) {
        // Queda pendiente de enviar (isSynced = false) hasta que PositionSync la suba (SYN-011).
        dao.upsert(ReadingPositionEntity(bookId, locatorJson, System.currentTimeMillis(), totalProgression))
    }

    override suspend fun local(bookId: String): LocalPosition? =
        dao.get(bookId)?.let { LocalPosition(it.toPosition(), it.isSynced) }

    override suspend fun pending(): List<ReadingPosition> = dao.getPending().map { it.toPosition() }

    override suspend fun markSynced(bookId: String, readAt: Long) = dao.markSynced(bookId, readAt)

    override suspend fun markPending(bookId: String) = dao.markPending(bookId)

    override suspend fun applyRemote(position: ReadingPosition) {
        // Dos pasos atómicos cada uno: crear si falta, y reemplazar solo si la de la nube es más nueva. Si el usuario
        // lee entre un paso y otro, su lectura más reciente no se pisa.
        dao.insertIfMissing(
            ReadingPositionEntity(position.bookId, position.locatorJson, position.readAt, position.progress, isSynced = true),
        )
        dao.replaceIfOlder(position.bookId, position.locatorJson, position.progress, position.readAt)
    }

    private fun ReadingPositionEntity.toPosition() = ReadingPosition(bookId, locatorJson, progress, updatedAt)
}
