package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TuningString
import com.grafitto.laudate.domain.tablature.TabStringData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Identificador del formato de intercambio de tablaturas de Canticora. */
const val CANTICORA_TAB_FORMAT: String = "canticora.tablature"

/** Extensión de los ficheros de tablatura exportados. */
const val CANTICORA_TAB_EXTENSION: String = "canticora-tab"

/** Tipo MIME propio del formato, para el selector del sistema y el «abrir con». */
const val CANTICORA_TAB_MIME: String = "application/vnd.canticora.tablature+json"

/**
 * Serializador de tablaturas (**Kotlin puro**).
 *
 * Se usa con dos fines:
 *  - **Persistencia**: el resultado de [encode] es lo que guarda la columna `pattern_json` de
 *    `numeric_tablatures` (el mapeador de `:core:data` reutiliza este código).
 *  - **Intercambio**: envuelto en [TablatureBundle] viaja en un fichero propio, en la copia de
 *    seguridad y en los bundles de repertorio.
 *
 * El serializador es **determinista** e **inverso**: decodificar lo codificado devuelve el mismo
 * valor de dominio.
 */
object TablatureSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serializa una tablatura a JSON (uso interno del modelo, sin envoltorio de formato). */
    fun encode(tablature: Tablature): String = json.encodeToString(tablature.toDto())

    /** Reconstruye una tablatura desde el JSON anterior. Lanza si el contenido es inválido. */
    fun decode(text: String): Tablature = json.decodeFromString<TablatureDto>(text).toDomain()

    /** Indica si el texto declara ser un fichero de tablatura de Canticora. */
    fun looksLikeTablature(text: String): Boolean = runCatching {
        val trimmed = text.trim()
        trimmed.startsWith("{") && trimmed.contains("\"$CANTICORA_TAB_FORMAT\"")
    }.getOrDefault(false)

    // ---------------------------------------------------------------------------------------------
    // DTOs. Reflejo directo del modelo de dominio; los nombres de enum viajan como texto para que
    // un archivo de una versión futura no rompa la importación (los desconocidos caen a su default).
    // ---------------------------------------------------------------------------------------------

    @Serializable
    private data class TablatureDto(
        val format: String = CANTICORA_TAB_FORMAT,
        val id: String,
        /**
         * Canto al que pertenece la tablatura. Se admite ausente (`""`) solo por **compatibilidad**
         * con JSON antiguos, que no lo traían; en el modelo de dominio es **obligatorio**.
         */
        val songId: String = "",
        val title: String,
        val instrument: String,
        val tuning: TuningDto,
        val time: ScoreTimeSignatureDto,
        val resolution: String = TabResolution.EIGHTH.name,
        val measures: List<MeasureDto> = emptyList(),
        val origin: String = TabOrigin.CREATED.name,
        val writtenKey: ScoreKeyDto? = null,
        /** Sección del canto a la que se asocia la tablatura (`null` = general). */
        val sectionId: String? = null,
        /** Orden dentro de su grupo (misma sección, o el bloque general). */
        val order: Int = 0,
        /** Modo de transposición forzado (`null` = automático según instrumento y origen). */
        val transpositionMode: String? = null,
        /** Rótulo libre de la tablatura (RF-127). */
        val label: String? = null,
        /** Momento litúrgico sugerido (RF-127). */
        val moment: String? = null,
        /** Nota libre del copista (RF-127). */
        val note: String? = null
    )

    @Serializable
    private data class TuningDto(
        val name: String,
        val strings: List<TuningStringDto>,
        val frets: Int = TabStringData.DEFAULT_FRETS
    )

    @Serializable
    private data class TuningStringDto(
        val step: String,
        val alter: Int = 0,
        val octave: Int,
        val isCourse: Boolean = false,
        val alwaysOpen: Boolean = false,
        val useFlat: Boolean = false
    )

    @Serializable
    private data class ScorePitchDto(val step: String, val alter: Int = 0, val octave: Int)

    @Serializable
    private data class ScoreKeyDto(val fifths: Int, val mode: String = ScoreKey.MAJOR)

    @Serializable
    private data class ScoreTimeSignatureDto(val beats: String, val beatType: Int)

    @Serializable
    private data class MeasureDto(
        val slots: List<SlotDto> = emptyList(),
        /** Barras de repetición (`|:` `:|`), RF-127. */
        val repeat: String? = null,
        /** Casilla de volta (1ª/2ª vez), RF-127. */
        val ending: Int? = null,
        /** Última pasada de la volta cuando cubre un rango (`1, 2.`). Opcional al leer. */
        val endingTo: Int? = null,
        /**
         * **Rótulo propio** de la volta (el `beginText` de MuseScore). Opcional al leer: si falta se
         * deriva de [ending]/[endingTo] (`1.`, `1, 2.`).
         */
        val endingText: String? = null,
        /** Número de ejecuciones al cerrar la repetición. */
        val repeatCount: Int = 2,
        /** Tipo de **barra de compás** (`SINGLE`/`DOUBLE`/`FINAL`), RF-127. */
        val barline: String = TabBarline.SINGLE.name,
        /** Signo de **navegación** (Segno / Coda / Fine / To Coda), RF-127. */
        val navigation: String? = null,
        /** **Salto** (D.C. / D.S. y variantes), RF-127. */
        val jump: String? = null,
        /** **Dinámica** (matiz/regulador), RF-127. */
        val dynamic: String? = null,
        /** **Regulador** (*hairpin*) que empieza en este compás: `CRESCENDO` / `DIMINUENDO`. */
        val hairpin: String? = null,
        /** Alcance del regulador en compases. Opcional para aceptar JSON de versiones anteriores. */
        val hairpinLength: Int = 1,
        /** **Cambio de octava** que empieza en este compás (8va / 15ma / 8vb), T4. */
        val octaveShift: String? = null,
        /** **Tempo** (BPM) indicado en este compás, T5. */
        val tempo: Int? = null,
        /** **Rótulo literal** del tempo tal como venía del origen (`♩ = 80`, `Andante`), D-8. */
        val tempoText: String? = null,
        /** **Texto libre** anclado a este compás (indicación del copista, comentario), T6. */
        val text: String? = null,
        /**
         * `true` si el compás **abre un sistema** (`Return` de MuseScore). Opcional para aceptar JSON
         * de versiones anteriores; antes **no se guardaba**, así que los saltos se perdían al reabrir.
         */
        val forceSystemBreak: Boolean = false,
        /** `true` si el compás **abre página** en el PDF (`Ctrl`+`Return` de MuseScore). */
        val forcePageBreak: Boolean = false,
        /**
         * `true` si el compás es la **anacrusa** (*pickup*). Opcional para aceptar JSON de versiones
         * anteriores; sin la marca, un compás corto al principio se leería como un compás al que le
         * faltan silencios y «Completar compases» lo rellenaría.
         */
        val isPickup: Boolean = false,
        /**
         * **Cambio de compás** que empieza en este compás, en formato `"4/4"`. Opcional para aceptar
         * JSON de versiones anteriores; `null` = el compás sigue la indicación del documento.
         */
        val time: String? = null,
        /**
         * `true` si el compás es **irregular** (duración real distinta de la nominal a propósito).
         * Opcional para aceptar JSON de versiones anteriores.
         */
        val isIrregular: Boolean = false
    )

    @Serializable
    private data class SlotDto(
        val strings: List<PluckDto> = emptyList(),
        val isHeld: Boolean = false,
        /** Figura y grupo irregular son opcionales para aceptar JSON de versiones anteriores. */
        val rhythmicFigure: String? = null,
        /**
         * Marca antigua de **tresillo** (`3:2`). Se sigue **escribiendo** para que un archivo abierto
         * por una versión anterior no pierda el tresillo, y se sigue **leyendo** cuando el archivo no
         * trae [tupletNum]/[tupletNormal].
         */
        val isTriplet: Boolean = false,
        /**
         * **Numerador** del grupo irregular (`actualNotes` de MuseScore): 3 en un tresillo, 5 en un
         * quintillo. `null` = la casilla no va en un grupo irregular.
         */
        val tupletNum: Int? = null,
        /** **Denominador** del grupo irregular (`normalNotes` de MuseScore): 2 en un tresillo. */
        val tupletNormal: Int? = null,
        /**
         * **Qué se rotula** sobre el corchete (`TupletNumberType` de MuseScore): `SHOW_NUMBER`,
         * `SHOW_RELATION` o `NO_TEXT`. Opcional: un archivo anterior a D-41 no lo trae y se lee como
         * `SHOW_NUMBER`, que es el valor por defecto de MuseScore.
         */
        val tupletNumberType: String? = null,
        /**
         * **Si se dibuja el corchete** (`TupletBracketType` de MuseScore): `AUTO_BRACKET`,
         * `SHOW_BRACKET` o `SHOW_NO_BRACKET`. Opcional, por la misma razón.
         */
        val tupletBracketType: String? = null,
        /** Voz de la posición (1–4). Opcional para aceptar JSON de versiones anteriores. */
        val voice: Int = 1,
        /** Ligadura de expresión hacia la casilla siguiente (`S` de MuseScore). Opcional en v1. */
        val slurToNext: Boolean = false,
        /** **Notas de adorno** antes de la casilla. Opcional para aceptar JSON de versiones anteriores. */
        val graces: List<GraceDto> = emptyList()
    )

    @Serializable
    private data class GraceDto(
        val stringNumber: Int,
        val fret: Int,
        /** `ACCIACCATURA` / `APPOGGIATURA`. Opcional: por defecto la acciaccatura. */
        val type: String = "ACCIACCATURA"
    )

    @Serializable
    private data class PluckDto(
        val stringNumber: Int,
        val fret: Int,
        /** Compatibilidad con archivos anteriores (una sola técnica). Al leer se suma a [techniques]. */
        val technique: String = TabTechnique.NONE.name,
        /** Técnicas **combinadas** de la nota (§13.5.bis). Vacío = ninguna. */
        val techniques: List<String> = emptyList(),
        /** **Eje 3 · Ejecución** (opcional), RF-127.
         *  `null` en v1: la duración se deriva de la rejilla. */
        val execution: ExecutionDto? = null,
        /** Cantidad del *bend* (`QUARTER`, `HALF`…); `null` = sin indicar. */
        val bendAmount: String? = null,
        /** Nº de columnas que abarca el `P.M.` desde esta nota; `null` = solo su columna. */
        val palmMuteSpan: Int? = null,
        /** Dedo de la **mano izquierda** que pisa la nota (`INDEX`…`PINKY`), opcional. */
        val frettingFinger: String? = null,
        /** Dedo/púa de la **mano derecha** que pulsa (`p`/`i`/`m`/`a`), opcional. */
        val pickingFinger: String? = null,
        /** **Articulaciones** combinadas de la nota (acento, picado, calderón…), T3. */
        val articulations: List<String> = emptyList(),
        /** **Alteración** escrita junto a la cifra (bemol/becuadro/sostenido…), T4. */
        val accidental: String? = null,
        /** **Ligadura de prolongación** (*tie*, B-10): la nota sigue sonando en la siguiente. */
        val tiedToNext: Boolean = false,
        /** **Trazos** del trémolo (1–4), D-28. Se guarda siempre para no perderlo al desactivar. */
        val tremoloStrokes: Int = 1
    )

    @Serializable
    private data class ExecutionDto(
        val velocity: Int? = null,
        val dynamics: String? = null,
        val midiNote: Int? = null
    )

    // ---------------------------------------------------------------------------------------------
    // Conversión modelo ⇄ DTO
    // ---------------------------------------------------------------------------------------------

    private fun Tablature.toDto(): TablatureDto = TablatureDto(
        id = id,
        songId = songId,
        title = title,
        instrument = instrument,
        tuning = tuning.toDto(),
        time = ScoreTimeSignatureDto(time.beats, time.beatType),
        resolution = resolution.name,
        measures = measures.map { it.toDto() },
        origin = origin.name,
        writtenKey = writtenKey?.let { ScoreKeyDto(it.fifths, it.mode) },
        sectionId = sectionId,
        order = order,
        transpositionMode = transpositionMode?.name,
        label = label,
        moment = moment,
        note = note
    )

    private fun Tuning.toDto(): TuningDto = TuningDto(
        name = name,
        strings = strings.map {
            TuningStringDto(
                step = it.pitch.step.toString(),
                alter = it.pitch.alter,
                octave = it.pitch.octave,
                isCourse = it.isCourse,
                alwaysOpen = it.alwaysOpen,
                useFlat = it.useFlat
            )
        },
        frets = frets
    )

    private fun TabMeasure.toDto(): MeasureDto = MeasureDto(
        slots = slots.map { it.toDto() },
        repeat = repeat?.name,
        ending = ending,
        endingTo = endingTo,
        endingText = endingText,
        repeatCount = repeatCount,
        barline = barline.name,
        navigation = navigation?.name,
        jump = jump?.name,
        dynamic = dynamic?.name,
        hairpin = hairpin?.type?.name,
        hairpinLength = hairpin?.measures ?: 1,
        octaveShift = octaveShift?.name,
        tempo = tempo,
        tempoText = tempoText,
        text = text,
        forceSystemBreak = forceSystemBreak,
        forcePageBreak = forcePageBreak,
        isPickup = isPickup,
        time = time?.toString(),
        isIrregular = isIrregular
    )

    private fun TabSlot.toDto(): SlotDto = SlotDto(
        strings = strings.map {
            PluckDto(
                stringNumber = it.stringNumber,
                fret = it.fret,
                // Se escribe el principal por compatibilidad y, además, la lista completa.
                technique = it.technique.name,
                techniques = it.techniques.map { t -> t.name },
                execution = it.execution?.toDto(),
                bendAmount = it.bendAmount?.name,
                palmMuteSpan = it.palmMuteSpan,
                frettingFinger = it.frettingFinger?.name,
                pickingFinger = it.pickingFinger?.name,
                articulations = it.articulations.map { a -> a.name },
                accidental = it.accidental?.name,
                tiedToNext = it.tiedToNext,
                tremoloStrokes = it.strokes
            )
        },
        isHeld = isHeld,
        rhythmicFigure = rhythmicFigure?.name,
        isTriplet = isTriplet,
        tupletNum = tuplet?.numNotes,
        tupletNormal = tuplet?.normalNotes,
        tupletNumberType = tuplet?.numberType?.name,
        tupletBracketType = tuplet?.bracketType?.name,
        voice = voice,
        slurToNext = slurToNext,
        graces = graces.map { GraceDto(it.stringNumber, it.fret, it.type.name) }
    )

    private fun com.grafitto.laudate.domain.model.tablature.TabExecution.toDto(): ExecutionDto =
        ExecutionDto(velocity, dynamics, midiNote)

    private fun TablatureDto.toDomain(): Tablature = Tablature(
        id = id,
        songId = songId,
        title = title,
        instrument = instrument,
        tuning = tuning.toDomain(),
        time = ScoreTimeSignature(time.beats, time.beatType),
        resolution = enumOrDefault(resolution, TabResolution.EIGHTH),
        measures = measures.map { it.toDomain() },
        origin = enumOrDefault(origin, TabOrigin.CREATED),
        writtenKey = writtenKey?.let { ScoreKey(it.fifths, it.mode) },
        sectionId = sectionId,
        order = order,
        transpositionMode = transpositionMode?.let { name ->
            com.grafitto.laudate.domain.tablature.TabTranspositionMode.entries.firstOrNull { it.name == name }
        },
        label = label,
        moment = moment,
        note = note
    )

    private fun TuningDto.toDomain(): Tuning = Tuning(
        name = name,
        strings = strings.map { TuningString(it.toPitch(), it.isCourse, it.alwaysOpen, it.useFlat) },
        frets = frets.coerceIn(TabStringData.MIN_FRETS, TabStringData.MAX_FRETS)
    )

    private fun TuningStringDto.toPitch(): ScorePitch = ScorePitch(step.firstOrNull() ?: 'E', alter, octave)

    private fun MeasureDto.toDomain(): TabMeasure = TabMeasure(
        slots = slots.map { it.toDomain() },
        repeat = repeat?.let { enumOrDefault(it, TabRepeatMark.START) },
        ending = ending,
        endingTo = endingTo,
        endingText = endingText?.takeIf { it.isNotBlank() },
        repeatCount = repeatCount.coerceIn(2, 8),
        barline = enumOrDefault(barline, TabBarline.SINGLE),
        navigation = navigation?.let { name ->
            enumValues<com.grafitto.laudate.domain.model.tablature.TabNavigationMark>()
                .firstOrNull { it.name == name }
        },
        jump = jump?.let { name ->
            enumValues<com.grafitto.laudate.domain.model.tablature.TabJump>()
                .firstOrNull { it.name == name }
        },
        dynamic = dynamic?.let { name ->
            enumValues<com.grafitto.laudate.domain.model.tablature.TabDynamic>()
                .firstOrNull { it.name == name }
        },
        hairpin = hairpin?.let { name ->
            enumValues<com.grafitto.laudate.domain.model.tablature.TabHairpinType>()
                .firstOrNull { it.name == name }
                ?.let { com.grafitto.laudate.domain.model.tablature.TabHairpin(it, hairpinLength) }
        },
        octaveShift = octaveShift?.let { name ->
            enumValues<com.grafitto.laudate.domain.model.tablature.TabOctaveShift>()
                .firstOrNull { it.name == name }
        },
        tempo = tempo,
        tempoText = tempoText,
        text = text,
        forceSystemBreak = forceSystemBreak,
        forcePageBreak = forcePageBreak,
        isPickup = isPickup,
        // Un texto corrupto deja el compás con la indicación del documento en vez de romper la carga.
        time = time?.let { ScoreTimeSignature.parse(it) },
        isIrregular = isIrregular
    )

    private fun SlotDto.toDomain(): TabSlot = TabSlot(
        strings = strings.map {
            // Las técnicas viajan en la lista; el campo antiguo `technique` se suma si la lista viene
            // vacía (archivos de versiones anteriores al modelo combinable).
            val fromList = it.techniques.mapNotNull { name ->
                enumValues<TabTechnique>().firstOrNull { t -> t.name == name }
            }
            val legacy = enumOrDefault(it.technique, TabTechnique.NONE)
            val combined = (fromList + if (legacy != TabTechnique.NONE) listOf(legacy) else emptyList())
                .toSet()
            TabPluck(
                position = com.grafitto.laudate.domain.model.tablature.TabPosition(it.stringNumber, it.fret),
                techniques = combined,
                execution = it.execution?.toDomain(),
                bendAmount = it.bendAmount?.let { name ->
                    enumValues<com.grafitto.laudate.domain.model.tablature.TabBendAmount>()
                        .firstOrNull { a -> a.name == name }
                },
                palmMuteSpan = it.palmMuteSpan,
                frettingFinger = it.frettingFinger?.let { name ->
                    enumValues<com.grafitto.laudate.domain.model.tablature.TabFrettingFinger>()
                        .firstOrNull { f -> f.name == name }
                },
                pickingFinger = it.pickingFinger?.let { name ->
                    enumValues<com.grafitto.laudate.domain.model.tablature.TabPickingFinger>()
                        .firstOrNull { f -> f.name == name }
                },
                // (D-40) Se **combinan** las parejas que MuseScore dibuja en un solo glifo: un archivo
                // guardado antes de D-40 con `ACCENT` + `STACCATO` sueltos se lee como el sforzato `𝄰`,
                // que es como lo dibuja MuseScore. Sin esto, la misma canción se vería distinta según
                // cuándo se guardó.
                articulations = com.grafitto.laudate.domain.tablature.TabSymbolCompatibility.join(
                    it.articulations.mapNotNull { name ->
                        enumValues<com.grafitto.laudate.domain.model.tablature.TabArticulation>()
                            .firstOrNull { a -> a.name == name }
                    }.toSet()
                ),
                accidental = it.accidental?.let { name ->
                    enumValues<com.grafitto.laudate.domain.model.tablature.TabAccidental>()
                        .firstOrNull { a -> a.name == name }
                },
                tiedToNext = it.tiedToNext,
                tremoloStrokes = it.tremoloStrokes
            )
        },
        isHeld = isHeld,
        rhythmicFigure = rhythmicFigure?.let { name ->
            RhythmFigure.entries.firstOrNull { it.name == name }
        },
        tuplet = when {
            tupletNum != null && tupletNormal != null && tupletNum > 0 && tupletNormal > 0 ->
                com.grafitto.laudate.domain.model.tablature.TabTuplet(
                    numNotes = tupletNum,
                    normalNotes = tupletNormal,
                    numberType = tupletNumberType?.let {
                        enumOrDefault(
                            it,
                            com.grafitto.laudate.domain.model.tablature.TabTupletNumberType.SHOW_NUMBER
                        )
                    } ?: com.grafitto.laudate.domain.model.tablature.TabTupletNumberType.SHOW_NUMBER,
                    bracketType = tupletBracketType?.let {
                        enumOrDefault(
                            it,
                            com.grafitto.laudate.domain.model.tablature.TabTupletBracketType.AUTO_BRACKET
                        )
                    } ?: com.grafitto.laudate.domain.model.tablature.TabTupletBracketType.AUTO_BRACKET
                )
            // Compatibilidad: un archivo de antes de D-38 solo trae la marca booleana.
            isTriplet -> com.grafitto.laudate.domain.model.tablature.TabTuplet.TRIPLET
            else -> null
        },
        voice = voice.coerceIn(1, 4),
        slurToNext = slurToNext,
        graces = graces.map {
            com.grafitto.laudate.domain.model.tablature.TabGrace(
                stringNumber = it.stringNumber,
                fret = it.fret,
                type = enumOrDefault(
                    it.type,
                    com.grafitto.laudate.domain.model.tablature.TabGraceType.ACCIACCATURA
                )
            )
        }
    )

    private fun ExecutionDto.toDomain(): com.grafitto.laudate.domain.model.tablature.TabExecution =
        com.grafitto.laudate.domain.model.tablature.TabExecution(velocity, dynamics, midiNote)

    /** Resuelve un nombre de enum tolerando valores desconocidos (archivos de versiones futuras). */
    private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default
}
