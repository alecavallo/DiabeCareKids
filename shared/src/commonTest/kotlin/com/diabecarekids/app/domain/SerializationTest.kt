package com.diabecarekids.app.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun roundTripPreservesAllFields() {
        val original = RegistroComida(
            id = "meal-001",
            fecha_hora_inicio = 1_728_000_000_000L,
            tipo_comida = TipoComida.ALMUERZO,
            glicemia_inicial = 120.0,
            nombre_alimento = "Arroz con pollo",
            carbohidratos_estimados = 50.0,
            fuente_carbohidratos = CarbSource.USDA,
            foto_antes_url = "content://photos/antes.jpg",
            foto_despues_url = null,
            porcentaje_consumido = 80,
            carbohidratos_reales = 40.0,
            glicemia_postprandial_2h = 140.0,
            es_registro_historico = false,
            creado_por_usuario_id = "user-1",
            ultima_modificacion = 1_728_360_000_000L,
        )

        val roundTripped = json.decodeFromString<RegistroComida>(json.encodeToString(original))

        assertEquals(original, roundTripped)
    }

    @Test
    fun nullablePhotosSerializeAsNull() {
        val original = RegistroComida(
            id = "meal-002",
            fecha_hora_inicio = 1L,
            tipo_comida = TipoComida.CENA,
            glicemia_inicial = 100.0,
            nombre_alimento = "Tostadas",
            carbohidratos_estimados = 20.0,
            fuente_carbohidratos = CarbSource.MANUAL,
            creado_por_usuario_id = "user-1",
            ultima_modificacion = 2L,
        )

        val decoded = json.decodeFromString<RegistroComida>(json.encodeToString(original))

        assertEquals(original, decoded)
        assertEquals(null, decoded.foto_antes_url)
        assertEquals(null, decoded.foto_despues_url)
        // T2 default porcentaje_consumido carried when not set at T0.
        assertEquals(100, decoded.porcentaje_consumido)
    }

    @Test
    fun enumSourcesAndMealTypesRoundTrip() {
        val jsonText =
            """{"id":"m","fecha_hora_inicio":1,"tipo_comida":"MERIENDA","glicemia_inicial":95.0,""" +
                """"nombre_alimento":"Yogur","carbohidratos_estimados":15.0,"fuente_carbohidratos":"GEMINI_AI",""" +
                """"creado_por_usuario_id":"u","ultima_modificacion":2}"""

        val decoded = json.decodeFromString<RegistroComida>(jsonText)

        assertEquals(TipoComida.MERIENDA, decoded.tipo_comida)
        assertEquals(CarbSource.GEMINI_AI, decoded.fuente_carbohidratos)
    }
}
