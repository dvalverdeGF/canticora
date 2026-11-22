package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import kotlinx.coroutines.flow.Flow

/**
 * Contrato puro de Dominio para la gestión de patrones rítmicos de rasgueo.
 * Combina la biblioteca integrada (built-in) con los patrones personalizados del usuario.
 */
interface RhythmPatternRepository {

    /** Todos los patrones disponibles (integrados + personalizados). */
    fun getAllPatterns(): Flow<List<RhythmPattern>>

    /** Patrones personalizados creados/editados por el usuario. */
    fun getCustomPatterns(): Flow<List<RhythmPattern>>

    /** Persiste un patrón personalizado (no permite modificar los integrados). */
    suspend fun savePattern(pattern: RhythmPattern)

    /**
     * Elimina un patrón por su id, **incluidos los integrados**.
     *
     * Los patrones personalizados se borran de la base; los integrados no tienen fila propia, así
     * que se recuerdan como «ocultos» para que el borrado persista entre sesiones.
     */
    suspend fun deletePattern(patternId: String)

    /**
     * Restaura un patrón previamente eliminado (deshacer).
     *
     * Para un patrón integrado vuelve a mostrarse en la biblioteca; para uno personalizado se
     * reinserta con todos sus datos. Es la operación inversa de [deletePattern].
     */
    suspend fun restorePattern(pattern: RhythmPattern)
}
