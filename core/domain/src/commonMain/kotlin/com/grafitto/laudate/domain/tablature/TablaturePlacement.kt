package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Utilidades **puras** de **colocación** de las tablaturas de un canto (ADR-017).
 *
 * Una tablatura vive en un **grupo**: la sección a la que se ata (`sectionId`) o el bloque general
 * (`sectionId == null`). Dentro del grupo tiene un **orden** ([Tablature.order]) que decide en qué
 * posición se dibuja. Estas funciones resuelven las dos operaciones que el usuario hace desde el
 * visor —*mover a otra sección* y *subir/bajar*— sin tocar la base de datos: devuelven los modelos
 * ya modificados y quien los llama los persiste.
 *
 * Son puras y sin dependencias de Android, así que se prueban en JVM y se reutilizan desde el visor
 * y desde la vista previa del editor.
 */
object TablaturePlacement {

    /**
     * **Reordena** un grupo moviendo [tablatureId] [delta] posiciones (negativo = subir).
     *
     * Devuelve el grupo con el orden **renumerado de 0 a n-1** para que quede estable y sin huecos.
     * Si el movimiento no cambia nada (ya está en un extremo, el id no existe o [delta] es 0)
     * devuelve la lista original tal cual, para no escribir en disco sin necesidad.
     */
    fun reorder(group: List<Tablature>, tablatureId: String, delta: Int): List<Tablature> {
        if (delta == 0 || group.size < 2) return group
        val ordered = group.sortedBy { it.order }
        val index = ordered.indexOfFirst { it.id == tablatureId }
        if (index < 0) return group
        val target = (index + delta).coerceIn(0, ordered.lastIndex)
        if (target == index) return group
        val moved = ordered.toMutableList().apply { add(target, removeAt(index)) }
        return moved.mapIndexed { position, item -> item.copy(order = position) }
    }

    /**
     * **Mueve** [tablature] al grupo de [sectionId] (`null` = bloque general), colocándola **al
     * final** del destino para no pisar el orden de las que ya estaban.
     *
     * [siblingsInTarget] son las tablaturas que ya viven en el grupo destino, **sin** contar la que
     * se mueve.
     */
    fun moveToSection(
        tablature: Tablature,
        sectionId: String?,
        siblingsInTarget: List<Tablature>
    ): Tablature = tablature.copy(sectionId = sectionId, order = siblingsInTarget.size)
}
