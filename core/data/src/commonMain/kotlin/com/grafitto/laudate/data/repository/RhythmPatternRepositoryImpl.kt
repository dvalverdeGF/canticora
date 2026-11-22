package com.grafitto.laudate.data.repository

import com.grafitto.laudate.data.local.dao.RhythmPatternDao
import com.grafitto.laudate.data.local.entity.RhythmPatternEntity
import com.grafitto.laudate.data.local.nowMillis
import com.grafitto.laudate.data.mapper.RhythmPatternMapper
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.rhythm.GuitarPatternLibrary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementación Local-First del repositorio de patrones rítmicos.
 *
 * Combina la biblioteca integrada precargada ([GuitarPatternLibrary]) con los patrones
 * personalizados almacenados en Room. Los patrones **integrados** no tienen fila propia; para
 * poder borrarlos se guarda una fila centinela (`is_built_in = 1`) con la lista de nombres
 * ocultos, de modo que el borrado **persiste** entre sesiones y se puede deshacer restaurándolos.
 *
 * Un patrón integrado **editado** se guarda como fila propia con el **mismo id** que el de la
 * biblioteca: esa fila *sustituye* a la versión integrada (que deja de listarse), de modo que
 * «Editar» modifica el ritmo que el usuario abrió en lugar de crear un duplicado con su nombre.
 */
class RhythmPatternRepositoryImpl(
    private val rhythmPatternDao: RhythmPatternDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RhythmPatternRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getAllPatterns(): Flow<List<RhythmPattern>> =
        combine(
            rhythmPatternDao.getAllPatternsFlow(),
            rhythmPatternDao.observeHiddenBuiltInsFlow()
        ) { entities, hiddenJson ->
            val hidden = decodeHidden(hiddenJson)
            val custom = entities.map { RhythmPatternMapper.toDomain(it) }
            // Un integrado con fila propia (editado) se omite: manda la versión del usuario.
            val overridden = custom.mapTo(mutableSetOf()) { it.id }
            (GuitarPatternLibrary.builtInPatterns.filterNot { it.name in hidden || it.id in overridden } + custom)
                .sortedWith(compareBy({ it.timeSignature.toString() }, { it.name }))
        }

    override fun getCustomPatterns(): Flow<List<RhythmPattern>> =
        rhythmPatternDao.getCustomPatternsFlow()
            .map { entities ->
                entities.map { RhythmPatternMapper.toDomain(it) }
                    .sortedWith(compareBy({ it.timeSignature.toString() }, { it.name }))
            }

    override suspend fun savePattern(pattern: RhythmPattern) = withContext(ioDispatcher) {
        if (pattern.isBuiltIn) return@withContext
        rhythmPatternDao.insertPattern(RhythmPatternMapper.toEntity(pattern))
    }

    override suspend fun deletePattern(patternId: String): Unit = withContext(ioDispatcher) {
        // Un patrón con fila propia (personalizado o versión editada de un integrado) se borra
        // borrando su fila. Si además sustituía a un integrado, hay que ocultar también ese
        // integrado: si no, al desaparecer la fila reaparecería la versión de la biblioteca.
        if (rhythmPatternDao.getPatternById(patternId) != null) {
            rhythmPatternDao.deleteCustomPatternById(patternId)
        }
        // Un patrón integrado se elimina ocultándolo por **nombre** (fila centinela).
        builtInNameOf(patternId)?.let { name ->
            persistHidden(decodeHidden(rhythmPatternDao.getHiddenBuiltIns()) + name)
        }
        Unit
    }

    override suspend fun restorePattern(pattern: RhythmPattern) = withContext(ioDispatcher) {
        if (pattern.isBuiltIn) {
            // Deshacer el borrado de un patrón integrado = dejar de ocultarlo por nombre.
            val hidden = decodeHidden(rhythmPatternDao.getHiddenBuiltIns()).toMutableSet()
            hidden -= pattern.name
            persistHidden(hidden)
        } else {
            // Al restaurar la versión propia de un integrado hay que dejar de ocultarlo, o la
            // fila restaurada quedaría tapada por la ocultación.
            builtInNameOf(pattern.id)?.let { name ->
                val hidden = decodeHidden(rhythmPatternDao.getHiddenBuiltIns()).toMutableSet()
                hidden -= name
                persistHidden(hidden)
            }
            rhythmPatternDao.insertPattern(RhythmPatternMapper.toEntity(pattern))
        }
    }

    /** Nombre del patrón integrado con ese id, o `null` si el id no es de la biblioteca. */
    private fun builtInNameOf(patternId: String): String? =
        GuitarPatternLibrary.builtInPatterns.firstOrNull { it.id == patternId }?.name

    /** Escribe la fila centinela con la lista de nombres integrados ocultos. */
    private suspend fun persistHidden(hidden: Set<String>) {
        rhythmPatternDao.upsertHiddenBuiltIns(
            RhythmPatternEntity(
                id = HIDDEN_BUILT_INS_ID,
                name = "",
                numerator = 0,
                denominator = 0,
                eventsJson = "[]",
                isBuiltIn = true,
                timeSignature = "",
                createdAt = nowMillis(),
                builtInName = null,
                deletedBuiltInsJson = json.encodeToString(hidden.toList())
            )
        )
    }

    private fun decodeHidden(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return runCatching { json.decodeFromString<List<String>>(raw).toSet() }.getOrDefault(emptySet())
    }

    private companion object {
        /** Identificador fijo de la fila centinela que guarda los integrados ocultos. */
        const val HIDDEN_BUILT_INS_ID = "__hidden_builtins__"
    }
}
