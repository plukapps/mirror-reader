package com.pluk.reader.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    /** Notas vivas del libro por su lugar en el libro; a igual lugar, la más vieja primero (ANN-010). */
    @Query("SELECT * FROM notes WHERE bookId = :bookId AND deletedAt IS NULL ORDER BY progression, createdAt")
    fun observeByBook(bookId: String): Flow<List<NoteEntity>>

    /** La nota viva con ese id. */
    @Query("SELECT * FROM notes WHERE id = :id AND deletedAt IS NULL")
    suspend fun get(id: String): NoteEntity?

    /** La nota con ese id, también si está borrada (sincronización). */
    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun find(id: String): NoteEntity?

    /** Crea una nota nueva, pendiente de subir. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: NoteEntity)

    /** Edita el texto de una nota viva; queda pendiente de subir (ANN-005). */
    @Query("UPDATE notes SET text = :text, updatedAt = :updatedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun updateText(id: String, text: String, updatedAt: Long)

    /** Marca la nota como borrada; el borrado queda pendiente de subir (SYN-007). */
    @Query("UPDATE notes SET deletedAt = :at, updatedAt = :at WHERE id = :id AND deletedAt IS NULL")
    suspend fun markDeleted(id: String, at: Long)

    /** Elimina la nota de la base. */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    /** Una nota que la nube nunca tuvo no necesita marca de borrado: se elimina y listo. */
    @Query("DELETE FROM notes WHERE id = :id AND syncedAt IS NULL")
    suspend fun deleteIfNeverSynced(id: String): Int

    /** ANN-011: al quitar el libro, sus notas que la nube nunca tuvo se eliminan... */
    @Query("DELETE FROM notes WHERE bookId = :bookId AND syncedAt IS NULL")
    suspend fun deleteNeverSyncedOfBook(bookId: String)

    /** ...y las demás se marcan borradas, para que el borrado llegue a los otros dispositivos. */
    @Query("UPDATE notes SET deletedAt = :at, updatedAt = :at WHERE bookId = :bookId AND deletedAt IS NULL")
    suspend fun markDeletedOfBook(bookId: String, at: Long)

    /**
     * Cambios que la nube no tiene (SYN-016). Una nota viva solo si su libro ya está en la nube: el servidor exige
     * que exista. Una borrada siempre: ya estuvo en la nube, así que su libro también.
     */
    @Query(
        "SELECT n.* FROM notes n WHERE (n.syncedAt IS NULL OR n.syncedAt < n.updatedAt) " +
            "AND (n.deletedAt IS NOT NULL OR EXISTS (SELECT 1 FROM books b WHERE b.id = n.bookId AND b.uploadedAt IS NOT NULL))",
    )
    suspend fun getPending(): List<NoteEntity>

    /** PRF-009: hay notas que la nube no tiene, subibles o no. */
    @Query("SELECT COUNT(*) FROM notes WHERE syncedAt IS NULL OR syncedAt < updatedAt")
    suspend fun countUnsynced(): Int

    /** Hay notas que no se borraron (con la sincronización apagada, todas son de este dispositivo, PRF-009). */
    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun countAlive(): Int

    /** La nube ya tiene la versión de [updatedAt]. Si mientras tanto se editó, sigue pendiente. */
    @Query("UPDATE notes SET syncedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)

    /** Las borradas que la nube ya tiene borradas no hacen falta más. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND syncedAt >= updatedAt")
    suspend fun purgeSyncedDeletions()

    /**
     * Aplica una nota de la nube solo si es más reciente que la local (SYN-005): una edición local más nueva, pendiente
     * o no, no se pisa. Primero se crea si falta, después se reemplaza.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfMissing(entity: NoteEntity)

    @Query(
        "UPDATE notes SET text = :text, updatedAt = :editedAt, syncedAt = :editedAt, deletedAt = NULL " +
            "WHERE id = :id AND updatedAt < :editedAt",
    )
    suspend fun replaceIfOlder(id: String, text: String, editedAt: Long)

    /** Un borrado de la nube más reciente que la versión local elimina la nota (SYN-007). */
    @Query("DELETE FROM notes WHERE id = :id AND updatedAt <= :editedAt")
    suspend fun deleteIfNotNewer(id: String, editedAt: Long)
}
