package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabArticulationCategory
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabTechnique

/**
 * **Compatibilidad de los símbolos** de una misma nota (U-2).
 *
 * Una digitación **no puede llevar símbolos que se contradicen**. Hoy el modelo acepta cualquier
 * combinación (`techniques + technique`) y eso hace que aparezcan signos imposibles: un arco **hacia
 * arriba y hacia abajo** a la vez, picado **y** *tenuto*, armónico **y** cuerda apagada, etc. Este
 * objeto define **qué combinaciones son válidas** y **qué símbolos hay que retirar** cuando se añade
 * uno nuevo.
 *
 * Kotlin puro y determinista: la misma nota con la misma operación da siempre el mismo resultado.
 */
object TabSymbolCompatibility {

    /**
     * Grupos de técnicas y articulaciones **excluyentes entre sí**: dentro de un grupo solo puede
     * quedar **una**. Al añadir una, se retiran las otras del grupo (es lo que hace un editor serio:
     * no deja marcar lo imposible).
     */
    private val exclusiveTechniqueGroups: List<Set<TabTechnique>> = listOf(
        // Mano derecha: o púa hacia abajo, o hacia arriba.
        setOf(TabTechnique.PICK_DOWN, TabTechnique.PICK_UP),
        // Estiramientos: un bend no puede ser a la vez ascendente, descendente y previo.
        setOf(TabTechnique.BEND, TabTechnique.BEND_DOWN, TabTechnique.PRE_BEND),
        // Una nota no puede ser armónica y apagada a la vez.
        setOf(TabTechnique.HARMONIC, TabTechnique.MUTED),
        // Tapping y apagada se excluyen (el tapping produce una nota afinada).
        setOf(TabTechnique.TAPPING, TabTechnique.MUTED),
        // Ligado ascendente y descendente son excluyentes entre sí.
        setOf(TabTechnique.HAMMER, TabTechnique.PULL),
        // Dejar sonar y apagado de palma se contradicen.
        setOf(TabTechnique.LET_RING, TabTechnique.PALM_MUTE)
    )

    /** Grupos de **articulaciones** excluyentes: dentro de un grupo, solo una. */
    private val exclusiveArticulationGroups: List<Set<TabArticulation>> = listOf(
        // Grados de picado: picado y picadísimo se excluyen (el picadísimo ya es «más corto»).
        setOf(TabArticulation.STACCATO, TabArticulation.STACCATISSIMO),
        // El picadísimo y el tenuto se contradicen (cortar al máximo y sostener todo el valor).
        setOf(TabArticulation.STACCATISSIMO, TabArticulation.TENUTO),
        // Acentos: acento y marcato se excluyen, como en MuseScore (`chord.cpp:1826-1834`: añadir
        // uno retira el otro). El **calderón** va aparte: es de duración, no de ataque.
        setOf(TabArticulation.ACCENT, TabArticulation.MARCATO),
        // Arcos: un arco hacia arriba y otro hacia abajo a la vez es imposible (esto era el agujero
        // que dejaba poner los dos: la captura del usuario del 2026-10-12).
        setOf(TabArticulation.UP_BOW, TabArticulation.DOWN_BOW),
        // Adornos: mordente y grupeto se excluyen entre sí.
        setOf(TabArticulation.MORDENT, TabArticulation.TURN)
    )

    /**
     * **Parejas que MuseScore dibuja en un solo glifo** (`ARTICULATION_ABOVE_JOIN_GROUPS`,
     * `articulation.cpp:749`). La clave es el par de articulaciones **sueltas**; el valor, la
     * combinada que las sustituye.
     */
    private val JOIN_GROUPS: Map<Set<TabArticulation>, TabArticulation> = mapOf(
        setOf(TabArticulation.STACCATO, TabArticulation.ACCENT) to TabArticulation.ACCENT_STACCATO,
        setOf(TabArticulation.TENUTO, TabArticulation.ACCENT) to TabArticulation.TENUTO_ACCENT,
        setOf(TabArticulation.TENUTO, TabArticulation.STACCATO) to TabArticulation.TENUTO_STACCATO,
        setOf(TabArticulation.STACCATO, TabArticulation.MARCATO) to TabArticulation.MARCATO_STACCATO,
        setOf(TabArticulation.TENUTO, TabArticulation.MARCATO) to TabArticulation.MARCATO_TENUTO
    )

    /**
     * Orden en que se intentan las parejas: el de MuseScore, que recorre los símbolos de **mayor a
     * menor `SymId`** (`articulation.cpp:804`). En la lista alfabética de `symid_p.h` eso deja
     * `tenuto` > `staccato` > `marcato` > `acento`.
     */
    private val JOIN_ORDER: List<TabArticulation> = listOf(
        TabArticulation.TENUTO,
        TabArticulation.STACCATO,
        TabArticulation.MARCATO,
        TabArticulation.ACCENT
    )

    /**
     * Técnicas incompatibles **entre grupos**: pares que nunca pueden coexistir.
     *
     * - Un **armónico** no lleva bend ni slide (la nota se produce rozando la cuerda).
     * - El **tapping** es con la mano derecha: no lleva arco ni púa.
     * - La **cuerda apagada** no tiene altura: no puede ser armónica ni llevar digitación.
     */
    private val forbiddenTechniquePairs: List<Pair<TabTechnique, TabTechnique>> = listOf(
        TabTechnique.HARMONIC to TabTechnique.BEND,
        TabTechnique.HARMONIC to TabTechnique.SLIDE,
        TabTechnique.HARMONIC to TabTechnique.TAPPING,
        TabTechnique.ARPEGGIO to TabTechnique.BEND,
        TabTechnique.VIOLIN to TabTechnique.TAPPING
    )

    /**
     * Devuelve el conjunto de [techniques] **ya coherente** tras añadir [added]: retira las que
     * chocan con ella (las del mismo grupo excluyente y los pares prohibidos).
     */
    fun withTechnique(current: Set<TabTechnique>, added: TabTechnique): Set<TabTechnique> {
        if (added == TabTechnique.NONE) return emptySet()
        // 1) Fuera las del mismo grupo excluyente.
        val group = exclusiveTechniqueGroups.firstOrNull { added in it }
        val withoutGroup = if (group == null) current else current - group
        // 2) Fuera los pares prohibidos (en cualquier orden).
        val withoutForbidden = withoutGroup.filterNot { existing ->
            forbiddenTechniquePairs.any { (a, b) ->
                (existing == a && added == b) || (existing == b && added == a)
            }
        }
        return withoutForbidden.toSet() + added
    }

    /**
     * Devuelve el conjunto de [articulations] **ya coherente** tras añadir [added].
     *
     * Se **desglosan** primero las combinadas en sus cualidades sueltas (MuseScore:
     * `splitArticulations`), se retira lo que choca y se **vuelven a combinar** las parejas que
     * MuseScore dibuja en un solo glifo (`joinArticulations`). Así `acento` + `picado` no se apilan
     * como dos signos: se convierten en el **sforzato** `𝄰`, que es lo que hace MuseScore.
     */
    fun withArticulation(current: Set<TabArticulation>, added: TabArticulation): Set<TabArticulation> {
        val singles = split(current)
        val group = exclusiveArticulationGroups.firstOrNull { added in it }
        val withoutGroup = if (group == null) singles else singles - group
        // Una nota **picada** no puede llevar un **adorno** (el adorno se toca, no se corta).
        val withoutOrnaments = if (TabArticulationCategory.STACCATO in added.categories ||
            TabArticulationCategory.STACCATISSIMO in added.categories
        ) {
            withoutGroup - setOf(TabArticulation.MORDENT, TabArticulation.TURN)
        } else {
            withoutGroup
        }
        return join(withoutOrnaments + added)
    }

    /**
     * **Alterna** [toggled] sobre [current] por **cualidad**, no por glifo: pulsar «picado» sobre una
     * nota que lleva sforzato **quita solo el picado** y deja el acento suelto. Es el comportamiento
     * de `Chord::updateArticulations` (`chord.cpp:1768`), que desglosa las combinadas antes de tocar
     * nada.
     */
    fun toggleArticulation(current: Set<TabArticulation>, toggled: TabArticulation): Set<TabArticulation> {
        val singles = split(current)
        val target = toggled.components
        val alreadyThere = target.all { it in singles }
        return if (alreadyThere) {
            join(singles - target)
        } else {
            withArticulation(current, toggled)
        }
    }

    /**
     * **Desglosa** las articulaciones combinadas en sus cualidades sueltas (MuseScore:
     * `splitArticulations`, `articulation.cpp:772`). Es idempotente.
     */
    fun split(articulations: Set<TabArticulation>): Set<TabArticulation> =
        articulations.flatMapTo(mutableSetOf()) { it.components }

    /**
     * **Combina** las parejas que MuseScore dibuja en un solo glifo (MuseScore: `joinArticulations`,
     * `articulation.cpp:798`). El orden de intento reproduce el de MuseScore, que recorre los símbolos
     * de mayor a menor `SymId` —es decir, `tenuto` antes que `staccato`, y este antes que `marcato` y
     * `acento`—, de modo que una nota con tres cualidades se combina igual que allí.
     */
    fun join(articulations: Set<TabArticulation>): Set<TabArticulation> {
        val pending = JOIN_ORDER.filter { it in articulations }.toMutableList()
        val result = mutableSetOf<TabArticulation>()
        while (pending.isNotEmpty()) {
            val first = pending.removeAt(0)
            val partner = pending.firstOrNull { JOIN_GROUPS[setOf(first, it)] != null }
            if (partner == null) {
                result += first
            } else {
                pending.remove(partner)
                result += JOIN_GROUPS.getValue(setOf(first, partner))
            }
        }
        // Lo que no participa en ninguna pareja (calderón, adornos, arcadas, armónico) pasa tal cual.
        result += articulations.filterNot { it in JOIN_ORDER }
        return result
    }

    /**
     * `true` si la nota **ya expresa** todas las cualidades de [articulation], aunque sea con un glifo
     * **combinado**: una nota con sforzato `𝄰` ya lleva acento y picado, así que los dos chips de la
     * paleta deben salir marcados. Comparar por igualdad de glifo los dejaría apagados.
     */
    fun isArticulationPresent(current: Set<TabArticulation>, articulation: TabArticulation): Boolean {
        val singles = split(current)
        return articulation.categories.all { category -> singles.any { category in it.categories } }
    }

    /**
     * Indica si [added] **puede añadirse** a una nota que ya lleva [current] sin retirar nada. La usa
     * la **paleta** para deshabilitar los chips imposibles (un arco arriba cuando ya hay uno abajo),
     * en vez de ofrecerlos y «arreglarlos» en silencio.
     */
    fun isArticulationAllowed(current: Set<TabArticulation>, added: TabArticulation): Boolean {
        if (added in current) return true
        val singles = split(current)
        val group = exclusiveArticulationGroups.firstOrNull { added in it } ?: return true
        return (singles intersect group).isEmpty()
    }

    /**
     * Lista de **problemas** de una nota (vacía si es coherente). Es la misma verdad que aplican las
     * operaciones de edición, para poder avisar en la UI sin duplicar reglas.
     */
    fun problemsOf(pluck: TabPluck): List<String> = buildList {
        exclusiveTechniqueGroups.forEach { group ->
            val present = group.filter { it in pluck.techniques }
            if (present.size > 1) add("Símbolos de técnica incompatibles: ${present.joinToString(", ")}")
        }
        exclusiveArticulationGroups.forEach { group ->
            val present = split(pluck.articulations).filter { it in group }
            if (present.size > 1) add("Articulaciones incompatibles: ${present.joinToString(", ")}")
        }
        forbiddenTechniquePairs.forEach { (a, b) ->
            if (pluck.has(a) && pluck.has(b)) add("Técnicas incompatibles: $a con $b")
        }
        if (pluck.isMuted && (pluck.frettingFinger != null || pluck.pickingFinger != null)) {
            add("Una cuerda apagada no lleva digitación")
        }
    }
}
