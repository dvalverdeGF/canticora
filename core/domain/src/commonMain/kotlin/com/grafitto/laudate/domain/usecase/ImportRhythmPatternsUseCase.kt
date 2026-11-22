package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.rhythmSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.platform.ioDispatcher
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.rhythm.RhythmTextNotation
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import com.grafitto.laudate.domain.transfer.RhythmBundleSerializer
import com.grafitto.laudate.domain.transfer.toDomain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Resultado de importar patrones rítmicos.
 *
 * @property created Patrones nuevos que se han añadido.
 * @property skipped Patrones descartados por ser **idénticos** a uno ya existente o por decisión del usuario.
 * @property overwritten Patrones que ya existían con el mismo nombre y se han **actualizado** con permiso.
 */
data class RhythmImportResult(
    val created: Int,
    val skipped: Int,
    val overwritten: Int = 0
) {
    /** Texto listo para mostrar al usuario. */
    val summary: String
        get() = buildString {
            append("$created ritmo")
            if (created != 1) append("s")
            if (overwritten > 0) append(" · $overwritten actualizado")
            if (overwritten > 1) append("s")
            if (skipped > 0) append(" · $skipped sin cambios")
        }
}

/**
 * Inspección previa de unos ritmos **sin escribir nada**: cuántos son nuevos, cuántos idénticos y
 * qué nombres ya existen con contenido distinto (pendientes de permiso para sobrescribir).
 */
data class RhythmImportPreview(
    val total: Int,
    val newPatterns: Int,
    val identical: Int,
    val conflicts: List<ImportConflict>
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

/**
 * Importa patrones rítmicos desde un **archivo** ([invoke] con JSON) o desde **texto pegado**
 * ([importText]).
 *
 * Reglas:
 *  - Se omiten los patrones **idénticos** (mismo nombre, compás y golpes) a uno ya presente, para
 *    que reimportar el mismo archivo no genere duplicados.
 *  - Un patrón con el **mismo nombre** pero contenido distinto es un conflicto: con permiso se puede
 *    **sobrescribir**, conservar ambos u omitir (por defecto, sobrescribir el personalizado).
 *  - Un patrón nuevo recibe un identificador propio, de modo que dos personas pueden tener un
 *    patrón con el mismo nombre sin que colisionen.
 */
class ImportRhythmPatternsUseCase(
    private val rhythmPatternRepository: RhythmPatternRepository
) {

    companion object {
        /**
         * Indica si el texto parece un ritmo **pegado** (notación de rejilla) y no un archivo JSON.
         * Se usa para elegir la vía de importación sin obligar al usuario a distinguirlas.
         */
        fun looksLikePastedRhythm(text: String): Boolean =
            !text.trim().startsWith("{") && RhythmTextNotation.looksLikeRhythmText(text)
    }

    /** Analiza el contenido de un archivo de ritmos **sin escribir nada** (para poder preguntar). */
    suspend fun inspect(content: String): RhythmImportPreview = withContext(ioDispatcher) {
        val decoded = RhythmBundleSerializer.decode(content)
        inspectCandidates(decoded.patterns.map { it.toDomain(newId()) })
    }

    /** Analiza un ritmo pegado como texto **sin escribir nada**. Devuelve `null` si no se reconoce. */
    suspend fun inspectText(text: String): RhythmImportPreview? = withContext(ioDispatcher) {
        when (val result = RhythmTextNotation.decode(text)) {
            is RhythmTextNotation.DecodeResult.Failure -> null
            is RhythmTextNotation.DecodeResult.Success ->
                inspectCandidates(listOf(result.pattern.copy(id = newId())))
        }
    }

    private suspend fun inspectCandidates(candidates: List<RhythmPattern>): RhythmImportPreview {
        val existing = rhythmPatternRepository.getAllPatterns().first()
        val bySignature = existing.associateBy { it.rhythmSignature() }
        val byName = existing.groupBy { it.name.trim().lowercase() }

        var newPatterns = 0
        var identical = 0
        val conflicts = mutableListOf<ImportConflict>()

        candidates.forEach { pattern ->
            when {
                bySignature.containsKey(pattern.rhythmSignature()) -> identical++
                byName[pattern.name.trim().lowercase()]?.any { !it.isBuiltIn } == true ->
                    conflicts += ImportConflict(key = pattern.rhythmSignature(), title = pattern.name)
                else -> newPatterns++
            }
        }
        return RhythmImportPreview(
            total = candidates.size,
            newPatterns = newPatterns,
            identical = identical,
            conflicts = conflicts
        )
    }

    /** Importa el contenido de un archivo de ritmos (JSON `canticora.rhythm`). */
    suspend operator fun invoke(
        json: String,
        policies: Map<String, ImportConflictPolicy> = emptyMap()
    ): RhythmImportResult = withContext(ioDispatcher) {
        val decoded = runCatching { RhythmBundleSerializer.decode(json) }.getOrNull()
            ?: return@withContext RhythmImportResult(created = 0, skipped = 0)

        val candidates = decoded.patterns.map { it.toDomain(newId()) }
        persist(candidates, policies)
    }

    /**
     * Importa desde un **texto libre**: lo interpreta como archivo JSON o como un ritmo pegado en
     * notación de rejilla, para que el usuario pueda pegar el ritmo tal cual lo recibió.
     */
    suspend fun import(
        content: String,
        policies: Map<String, ImportConflictPolicy> = emptyMap()
    ): RhythmImportResult? = withContext(ioDispatcher) {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") && RhythmTextNotation.looksLikeRhythmText(trimmed)) {
            return@withContext importText(trimmed, policies)
        }
        val decoded = runCatching { RhythmBundleSerializer.decode(content) }.getOrNull()
            ?: return@withContext null
        persist(decoded.patterns.map { it.toDomain(newId()) }, policies)
    }

    /**
     * Importa un ritmo pegado como texto (la notación de la rejilla).
     * Devuelve `null` si el texto no contiene un ritmo reconocible.
     */
    suspend fun importText(
        text: String,
        policies: Map<String, ImportConflictPolicy> = emptyMap()
    ): RhythmImportResult? = withContext(ioDispatcher) {
        when (val result = RhythmTextNotation.decode(text)) {
            is RhythmTextNotation.DecodeResult.Failure -> null
            is RhythmTextNotation.DecodeResult.Success -> {
                val pattern = result.pattern.copy(id = newId())
                persist(listOf(pattern), policies)
            }
        }
    }

    private suspend fun persist(
        candidates: List<RhythmPattern>,
        policies: Map<String, ImportConflictPolicy> = emptyMap()
    ): RhythmImportResult {
        if (candidates.isEmpty()) return RhythmImportResult(created = 0, skipped = 0)

        val existing = rhythmPatternRepository.getAllPatterns().first()
        val existingSignatures = existing.map { it.rhythmSignature() }.toMutableSet()
        val customByName = existing.filterNot { it.isBuiltIn }
            .associateBy { it.name.trim().lowercase() }

        var created = 0
        var skipped = 0
        var overwritten = 0
        candidates.forEach { pattern ->
            if (existingSignatures.add(pattern.rhythmSignature())) {
                // Un patrón personalizado con el mismo nombre es un conflicto: se decide con permiso.
                val sameName = customByName[pattern.name.trim().lowercase()]
                if (sameName != null && sameName.rhythmSignature() != pattern.rhythmSignature()) {
                    when (policies[pattern.rhythmSignature()] ?: ImportConflictPolicy.OVERWRITE) {
                        ImportConflictPolicy.OVERWRITE -> {
                            rhythmPatternRepository.savePattern(pattern.copy(id = sameName.id))
                            overwritten++
                        }
                        ImportConflictPolicy.KEEP_BOTH -> {
                            rhythmPatternRepository.savePattern(pattern)
                            created++
                        }
                        ImportConflictPolicy.SKIP -> skipped++
                    }
                } else {
                    rhythmPatternRepository.savePattern(pattern)
                    created++
                }
            } else {
                skipped++
            }
        }
        return RhythmImportResult(created = created, skipped = skipped, overwritten = overwritten)
    }
}
