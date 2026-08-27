@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.diabecarekids.app.viewmodel

import com.diabecarekids.app.domain.CarbSource
import com.diabecarekids.app.domain.RegistroComida
import com.diabecarekids.app.domain.TipoComida
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FollowUpViewModelTest {

    private fun baseRegistro(estimados: Double = 50.0) = RegistroComida(
        id = "meal-1",
        fecha_hora_inicio = 1L,
        tipo_comida = TipoComida.ALMUERZO,
        glicemia_inicial = 120.0,
        nombre_alimento = "test food",
        carbohidratos_estimados = estimados,
        fuente_carbohidratos = CarbSource.MANUAL,
        foto_antes_url = null,
        foto_despues_url = null,
        porcentaje_consumido = 100,
        carbohidratos_reales = null,
        glicemia_postprandial_2h = null,
        es_registro_historico = false,
        creado_por_usuario_id = "local",
        ultima_modificacion = 1L,
    )

    @Test
    fun realCarbsAt80PercentOf50Is40() = runTest {
        val store = FakePersistenceStore()
        val vm = FollowUpViewModel(store, FakePhotoCapture(), this, baseRegistro(estimados = 50.0))

        vm.onIntakePercentChange(80)
        assertEquals(40.0, vm.state.value.realCarbsPreview, "80% of 50g must preview 40g (MEAL-003)")

        vm.onBgPost2hChange("160")
        vm.save()
        advanceUntilIdle()

        val updated = store.updated.single()
        assertEquals(80, updated.porcentaje_consumido)
        assertEquals(40.0, updated.carbohidratos_reales)
        assertEquals(160.0, updated.glicemia_postprandial_2h)
    }

    @Test
    fun saveWithoutPhotoKeepsFotoDespuesNull() = runTest {
        val store = FakePersistenceStore()
        val vm = FollowUpViewModel(store, FakePhotoCapture(uri = null), this, baseRegistro())

        vm.onIntakePercentChange(100)
        vm.onBgPost2hChange("140")
        vm.save()
        advanceUntilIdle()

        val updated = store.updated.single()
        assertNull(updated.foto_despues_url, "no after photo -> foto_despues_url must be null (INV-005)")
    }

    @Test
    fun cancelledSecondCaptureKeepsExistingAfterPhoto() = runTest {
        val store = FakePersistenceStore()
        val photo = FakePhotoCapture()
        val vm = FollowUpViewModel(store, photo, this, baseRegistro())

        photo.results.addAll(listOf("file://after.jpg", null)) // capture then cancel
        vm.takePhoto()
        advanceUntilIdle()
        assertEquals("file://after.jpg", vm.state.value.photoUri, "first capture should set the after photo")

        vm.takePhoto()
        advanceUntilIdle()
        assertEquals(
            "file://after.jpg",
            vm.state.value.photoUri,
            "a cancelled second capture must NOT erase the existing after photo",
        )
    }

    @Test
    fun saveRequiresTwoHourBg() = runTest {
        val store = FakePersistenceStore()
        val vm = FollowUpViewModel(store, FakePhotoCapture(), this, baseRegistro())

        vm.onIntakePercentChange(50)
        vm.save()
        advanceUntilIdle()

        assertEquals(true, vm.state.value.error != null)
        assertEquals(true, store.updated.isEmpty(), "must not persist T2 without 2h BG")
    }
}
