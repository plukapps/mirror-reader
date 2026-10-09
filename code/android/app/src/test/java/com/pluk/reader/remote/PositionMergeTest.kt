package com.pluk.reader.remote

import com.pluk.reader.domain.PositionMerge
import com.pluk.reader.domain.exceedsJumpThreshold
import com.pluk.reader.domain.mergePosition
import com.pluk.reader.domain.shouldOfferJump
import com.pluk.reader.domain.model.LocalPosition
import com.pluk.reader.domain.model.ReadingPosition
import com.pluk.reader.domain.model.RemotePosition
import org.junit.Assert.assertEquals
import org.junit.Test

/** SYN-003, SYN-010: gana la lectura más reciente, no la que llegó último, y no se pierde nada. */
class PositionMergeTest {
    private fun position(readAt: Long, locator: String = "{}") = ReadingPosition("libro", locator, 0.5, readAt)
    private fun local(readAt: Long, synced: Boolean) = LocalPosition(position(readAt), synced)
    private fun remote(readAt: Long) = RemotePosition(position(readAt), "otro-dispositivo", "SM-X510")

    @Test
    fun noRemoteAndNoLocalMeansNothing() {
        assertEquals(PositionMerge.Nothing, mergePosition(null, null))
    }

    // SYN-001: lo que se leyó sin conexión y no se envió sale cuando se puede
    @Test
    fun anUnsyncedLocalWithNothingInTheCloudIsPushed() {
        assertEquals(PositionMerge.PushLocal, mergePosition(local(10, synced = false), null))
    }

    @Test
    fun aSyncedLocalWithNothingInTheCloudNeedsNothing() {
        assertEquals(PositionMerge.Nothing, mergePosition(local(10, synced = true), null))
    }

    // Dispositivo nuevo: no hay posición local y la nube tiene una
    @Test
    fun noLocalTakesTheRemote() {
        assertEquals(PositionMerge.UseRemote, mergePosition(null, remote(10)))
    }

    @Test
    fun aNewerRemoteReplacesTheLocal() {
        assertEquals(PositionMerge.UseRemote, mergePosition(local(10, synced = true), remote(11)))
    }

    // SYN-003: la lectura más nueva supera a una local pendiente más vieja
    @Test
    fun aNewerRemoteReplacesEvenAnUnsyncedOlderLocal() {
        assertEquals(PositionMerge.UseRemote, mergePosition(local(10, synced = false), remote(11)))
    }

    // SYN-003, SYN-010: un dispositivo que estuvo sin conexión no pierde su lectura más nueva
    @Test
    fun aNewerLocalIsPushedAndNotOverwritten() {
        assertEquals(PositionMerge.PushLocal, mergePosition(local(12, synced = false), remote(11)))
    }

    // Estado incoherente (marcada como enviada pero la nube está atrás): se corrige enviando
    @Test
    fun aNewerLocalMarkedSyncedIsStillPushed() {
        assertEquals(PositionMerge.PushLocal, mergePosition(local(12, synced = true), remote(11)))
    }

    // El eco de lo que este mismo dispositivo envió
    @Test
    fun theSameReadingMarksTheLocalAsSynced() {
        assertEquals(PositionMerge.MarkSynced, mergePosition(local(10, synced = false), remote(10)))
        assertEquals(PositionMerge.Nothing, mergePosition(local(10, synced = true), remote(10)))
    }

    // SYN-003: el umbral del 2 % incluye exactamente el 2 %
    @Test
    fun theThresholdIncludesExactlyTwoPercent() {
        assertEquals(false, exceedsJumpThreshold(0.52 - 0.50))
        assertEquals(false, exceedsJumpThreshold(0.0))
        assertEquals(true, exceedsJumpThreshold(0.03))
    }

    private fun remoteAt(progress: Double?) = RemotePosition(ReadingPosition("libro", "{}", progress, 10), "otro", "SM-X510")

    // SYN-013: solo se ofrece seguir cuando el otro dispositivo leyó más adelante que la posición actual
    @Test
    fun aJumpIsOfferedOnlyWhenTheOtherDeviceIsAhead() {
        assertEquals(true, shouldOfferJump(0.30, remoteAt(0.60)))
        assertEquals(false, shouldOfferJump(0.60, remoteAt(0.30)))
        assertEquals(false, shouldOfferJump(0.50, remoteAt(0.51)))
    }

    @Test
    fun aJumpIsNotOfferedWhenTheOtherProgressIsUnknown() {
        assertEquals(false, shouldOfferJump(0.30, remoteAt(null)))
    }

    @Test
    fun aJumpIsOfferedWhenOurProgressIsUnknown() {
        assertEquals(true, shouldOfferJump(null, remoteAt(0.60)))
    }
}
