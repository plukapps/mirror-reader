package com.pluk.reader.domain

import com.pluk.reader.domain.model.LocalPosition
import com.pluk.reader.domain.model.RemotePosition

/** Qué hacer con la posición de un libro al conocer la de la nube (SYN-003). */
enum class PositionMerge {
    /** La de la nube es más reciente: reemplaza a la local. */
    UseRemote,

    /** La local es más reciente, o aún no se envió: hay que enviarla. */
    PushLocal,

    /** Son la misma lectura (por ejemplo el eco de lo que este dispositivo envió): solo marcar la local como enviada. */
    MarkSynced,

    /** No hay nada que hacer. */
    Nothing,
}

/**
 * Gana la lectura más reciente por [ReadingPosition.readAt], no la que llegó último (SYN-003). Una lectura
 * local más vieja que la de la nube se reemplaza: la lectura más nueva la supera, no es un dato del usuario
 * que se pierda (SYN-010). Función pura, sin reloj ni acceso a datos.
 */
fun mergePosition(local: LocalPosition?, remote: RemotePosition?): PositionMerge {
    if (remote == null) return if (local != null && !local.isSynced) PositionMerge.PushLocal else PositionMerge.Nothing
    if (local == null) return PositionMerge.UseRemote
    val localReadAt = local.position.readAt
    val remoteReadAt = remote.position.readAt
    return when {
        remoteReadAt > localReadAt -> PositionMerge.UseRemote
        remoteReadAt < localReadAt -> PositionMerge.PushLocal
        local.isSynced -> PositionMerge.Nothing
        else -> PositionMerge.MarkSynced
    }
}

/** Fracción del libro (2 %) a partir de la cual se pregunta antes de saltar a la posición de otro dispositivo (SYN-003). */
const val JUMP_CONFIRM_THRESHOLD = 0.02

// La tolerancia evita que 0.52 - 0.50 (0.020000000000000018) cuente como más del 2 %.
private const val EPSILON = 1e-9

/** La distancia [gap] (fracción del libro, sin signo) es mayor que el umbral de [JUMP_CONFIRM_THRESHOLD]. */
fun exceedsJumpThreshold(gap: Double): Boolean = gap > JUMP_CONFIRM_THRESHOLD + EPSILON

/** Fracción del libro (0,5 %) a partir de la cual se avisa, mientras se lee, que otro dispositivo va más adelante (SYN-013). */
const val CONTINUE_NOTICE_THRESHOLD = 0.005

/**
 * SYN-013: mientras se lee, se ofrece seguir desde otro dispositivo solo si leyó **más adelante** que la posición
 * actual, por más del 0,5 % del libro (unas pocas páginas; el 2 % de la pregunta al abrir era demasiado para un aviso). Si no se conoce el avance de la lectura remota no se ofrece nada; si se
 * desconoce el de la actual, sí.
 */
fun shouldOfferJump(currentProgress: Double?, remote: RemotePosition): Boolean {
    val ahead = remote.position.progress ?: return false
    val current = currentProgress ?: return true
    return ahead - current > CONTINUE_NOTICE_THRESHOLD + EPSILON
}
