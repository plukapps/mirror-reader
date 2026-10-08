package com.pluk.reader.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.pluk.reader.domain.account.QuotaSource
import com.pluk.reader.domain.account.StorageQuota
import com.pluk.reader.domain.remote.RemoteUnavailableException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Lee `users/{uid}`: `usedBytes` lo mantiene el servidor y `quotaBytes` lo define el plan (ADR 0008). */
class FirestoreQuotaSource @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : QuotaSource {
    override suspend fun current(): Result<StorageQuota> = remoteCall {
        val uid = auth.currentUser?.uid ?: throw RemoteUnavailableException("No hay sesión iniciada.")
        // Siempre del servidor: un valor viejo en caché dejaría subir de más o bloquearía sin motivo.
        val doc = db.document("users/$uid").get(Source.SERVER).await()
        val used = doc.getLong("usedBytes")
        val quota = doc.getLong("quotaBytes")
        // Si el documento aún no existe, la función de alta no terminó: se reintenta más tarde.
        if (!doc.exists() || used == null || quota == null) throw RemoteUnavailableException("La cuenta todavía no está lista.")
        StorageQuota(used, quota)
    }
}
