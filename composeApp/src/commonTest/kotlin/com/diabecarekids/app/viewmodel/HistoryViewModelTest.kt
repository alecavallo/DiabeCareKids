@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import com.diabecarekids.app.export.PdfExportOutcome
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryViewModelTest {

    private fun reg(id: String, at: Long) = RegistroComida(
        id = id,
        fecha_hora_inicio = at,
        tipo_comida = TipoComida.ALMUERZO,
        glicemia_inicial = 120.0,
        nombre_alimento = "food $id",
        carbohidratos_estimados = 50.0,
        fuente_carbohidratos = CarbSource.MANUAL,
        porcentaje_consumido = 100,
        carbohidratos_reales = 50.0,
        es_registro_historico = true,
        creado_por_usuario_id = "local",
        ultima_modificacion = at,
    )

    @Test
    fun loadsRecordsNewestFirst() = runTest {
        val store = FakePersistenceStore()
        // Insert out of order to prove the ViewModel sorts, not the store (design decision).
        store.save(reg("old", at = 100L))
        store.save(reg("new", at = 300L))
        store.save(reg("mid", at = 200L))

        val vm = HistoryViewModel(store, FakePdfReportExporter(), FakeReportShareLauncher(), this)
        advanceUntilIdle()

        assertEquals(
            listOf("new", "mid", "old"),
            vm.state.value.records.map { it.id },
            "timeline must be sorted by fecha_hora_inicio descending, newest first (R2)",
        )
    }

    @Test
    fun emptyStoreLoadsEmptyTimeline() = runTest {
        val vm = HistoryViewModel(store = FakePersistenceStore(), exporter = FakePdfReportExporter(), shareLauncher = FakeReportShareLauncher(), scope = this)
        advanceUntilIdle()

        assertEquals(emptyList(), vm.state.value.records, "empty store must yield an empty timeline (R2)")
    }

    @Test
    fun reloadRefreshesAfterNewSave() = runTest {
        val store = FakePersistenceStore()
        val vm = HistoryViewModel(store, FakePdfReportExporter(), FakeReportShareLauncher(), this)
        advanceUntilIdle()
        assertEquals(0, vm.state.value.records.size)

        // A record persisted after initial load must appear on explicit reload.
        store.save(reg("later", at = 999L))
        vm.reload()
        advanceUntilIdle()

        assertEquals(
            listOf("later"),
            vm.state.value.records.map { it.id },
            "reload must reflect records persisted after the initial load",
        )
    }

    // --- PDF export flow (CAP-004, S2.7) ---

    @Test
    fun exportSuccessSharesReturnedUri() = runTest {
        val store = FakePersistenceStore()
        store.save(reg("in-range", at = 100L))
        val exporter = FakePdfReportExporter(
            outcome = PdfExportOutcome.Success("content://pdf/report.pdf", rowCount = 1),
        )
        val launcher = FakeReportShareLauncher()
        val vm = HistoryViewModel(store, exporter, launcher, this)
        advanceUntilIdle()

        vm.exportReport(from = 0L, to = 200L)
        advanceUntilIdle()

        assertEquals(1, exporter.calls, "exporter must be invoked once")
        assertTrue(
            launcher.sharedUris.contains("content://pdf/report.pdf"),
            "on Success the ViewModel must share the exporter's URI",
        )
        assertEquals(null, vm.state.value.exportError, "no error on success")
        assertEquals(false, vm.state.value.isExporting, "exporting must reset after success")
    }

    @Test
    fun exportFailureSetsErrorAndDoesNotShare() = runTest {
        val store = FakePersistenceStore()
        store.save(reg("in-range", at = 100L))
        val exporter = FakePdfReportExporter(
            outcome = PdfExportOutcome.Failure("disk full"),
        )
        val launcher = FakeReportShareLauncher()
        val vm = HistoryViewModel(store, exporter, launcher, this)
        advanceUntilIdle()

        vm.exportReport(from = 0L, to = 200L)
        advanceUntilIdle()

        assertEquals("disk full", vm.state.value.exportError, "failure must surface exportError")
        assertEquals(0, launcher.calls, "share must never be invoked on failure (safe-behavior)")
        assertEquals(false, vm.state.value.isExporting, "exporting must reset after failure")
    }

    @Test
    fun exportTogglesIsExportingAndIgnoresReentry() = runTest {
        val store = FakePersistenceStore()
        store.save(reg("in-range", at = 100L))
        val exporter = FakePdfReportExporter(
            outcome = PdfExportOutcome.Success("content://pdf/report.pdf", rowCount = 1),
        )
        val launcher = FakeReportShareLauncher()
        val vm = HistoryViewModel(store, exporter, launcher, this)
        advanceUntilIdle()

        vm.exportReport(from = 0L, to = 200L)
        // A re-entry while the first export is still running must be ignored.
        vm.exportReport(from = 0L, to = 200L)
        advanceUntilIdle()

        assertEquals(1, exporter.calls, "re-entry while exporting must be ignored")
        assertEquals(1, launcher.calls, "only the single accepted export shares")
    }
}
