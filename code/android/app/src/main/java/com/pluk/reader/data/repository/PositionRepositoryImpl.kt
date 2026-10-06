package com.pluk.reader.data.repository

import com.pluk.reader.data.local.db.ReadingPositionDao
import com.pluk.reader.data.local.db.ReadingPositionEntity
import com.pluk.reader.domain.repository.PositionRepository
import javax.inject.Inject

class PositionRepositoryImpl @Inject constructor(
    private val dao: ReadingPositionDao,
) : PositionRepository {
    override suspend fun get(bookId: String): String? = dao.get(bookId)?.locatorJson

    override suspend fun save(bookId: String, locatorJson: String) {
        dao.upsert(ReadingPositionEntity(bookId, locatorJson, System.currentTimeMillis()))
    }
}
