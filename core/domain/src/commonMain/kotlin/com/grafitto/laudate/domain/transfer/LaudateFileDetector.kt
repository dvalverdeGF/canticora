package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.backup.CANTICORA_BACKUP_FORMATS
import com.grafitto.laudate.domain.rhythm.RhythmTextNotation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tipo de contenido que puede traer un archivo abierto con Canticora.
 *
 * Se usa para llevar al usuario a la importación correcta sin obligarle a elegir la vía a mano.
 */
enum class LaudateFileKind {
    /** Repertorio de una misa/evento (`.canticora` con formato `canticora.event`). */
    CELEBRATION,

    /** Copia de seguridad completa (ZIP con el manifiesto `backup.json` y los adjuntos). */
    BACKUP,

    /**
     * Paquete de un **canto suelto con sus adjuntos** (ZIP con el manifiesto `song.json`;
     * ADR-017). A diferencia del repertorio `.canticora`, sí viaja con los ficheros adjuntos.
     */
    SONG,

    /** Cancionero/catálogo de canciones (JSON con el esquema `BackupPayload`). */
    CATALOG,

    /** Patrones rítmicos de rasgueo (JSON `canticora.rhythm` o texto con notación de rejilla). */
    RHYTHMS,

    /** Una o varias canciones en texto ChordPro. */
    CHORDPRO,

    /**
     * Archivo multimedia, documento o binario que Canticora **no puede importar como canción**
     * (vídeo, imagen, audio, PDF, partitura XML, programa…). Se reconoce por sus bytes mágicos o
     * porque el contenido no es texto legible, nunca se intenta leer como ChordPro.
     *
     * [DetectedLaudateFile.mediaDescription] trae la palabra concreta («vídeo», «imagen»…) para
     * explicárselo al usuario.
     */
    MEDIA,

    /** No se ha podido determinar: se ofrece la pantalla «Importar» como salida. */
    UNKNOWN
}

/**
 * Resultado de analizar un archivo recibido (por WhatsApp, correo, gestor de archivos o
 * «Compartir»). Reúne lo necesario para **preguntar al usuario** y para ejecutar la importación.
 *
 * @param kind Naturaleza detectada del contenido.
 * @param title Nombre principal (título de la celebración, del canto o del archivo).
 * @param rawText Contenido en texto, cuando se ha podido leer (no aplica a un ZIP ni a [MEDIA]).
 * @param mediaDescription Para [LaudateFileKind.MEDIA]: qué es en lenguaje llano («vídeo»,
 *   «imagen», «documento PDF»…), para explicarle al usuario por qué no se puede importar.
 */
data class DetectedLaudateFile(
    val kind: LaudateFileKind,
    val title: String? = null,
    val rawText: String? = null,
    val songCount: Int? = null,
    val celebrationItemCount: Int? = null,
    val categoryCount: Int? = null,
    val attachmentCount: Int? = null,
    /** Número de patrones rítmicos, para los archivos de ritmos. */
    val rhythmCount: Int? = null,
    val isZip: Boolean = false,
    val mediaDescription: String? = null,
    /**
     * `true` si el paquete de canción **[SONG]** viaja con ficheros adjuntos (no solo enlaces).
     * Se deduce del manifiesto cuando se ha podido leer; si no, es `null`.
     */
    val hasAttachmentFiles: Boolean? = null
) {
    /** Nombre legible del tipo, para el diálogo de confirmación. */
    val kindLabel: String
        get() = when (kind) {
            LaudateFileKind.CELEBRATION -> "repertorio"
            LaudateFileKind.BACKUP -> "copia de seguridad"
            LaudateFileKind.SONG -> "canto con adjuntos"
            LaudateFileKind.CATALOG -> "cancionero"
            LaudateFileKind.RHYTHMS -> "ritmos"
            LaudateFileKind.CHORDPRO -> "canción en ChordPro"
            LaudateFileKind.MEDIA -> mediaDescription ?: "archivo"
            LaudateFileKind.UNKNOWN -> "archivo"
        }
}

/**
 * **Detector del tipo de archivo** de Canticora (Kotlin puro).
 *
 * El reconocimiento se apoya en tres pistas, en este orden:
 *  1. **Los bytes mágicos** del ZIP (`PK`), que delatan una copia de seguridad aunque no se pueda
 *     leer como texto.
 *  2. **El campo `format`** del JSON (`canticora.event` o `canticora.backup`), que es el
 *     identificador declarado por el propio archivo. Por compatibilidad también se aceptan los
 *     identificadores históricos `laudate.*` de versiones anteriores de la app.
 *  3. **La forma del JSON** (claves presentes): un cancionero trae `songs` y `categories`; una
 *     canción suelta en ChordPro no es JSON y contiene directivas o acordes entre corchetes.
 *
 * Se evita deserializar el contenido completo: para clasificar basta con leer las claves de primer
 * nivel, así que un archivo grande (un cancionero de ~1.000 cantos) se analiza al instante.
 */
object LaudateFileDetector {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Prefijo de un archivo ZIP: `PK\u0003\u0004` (o las variantes vacía/extra). */
    private val zipMagic = listOf("PK\u0003\u0004", "PK\u0005\u0006", "PK\u0007\u0008")

    /**
     * Quita el **BOM** (byte order mark) inicial, si lo hay.
     *
     * Muchos programas de Windows (Notepad, PowerShell `Set-Content -Encoding UTF8`…) escriben un
     * BOM `\uFEFF` al principio del fichero. Sin quitarlo, un JSON válido **no** empieza por `{` y el
     * detector lo confunde con ChordPro. Debe aplicarse siempre que se lea texto de un archivo.
     */
    fun stripBom(text: String): String =
        if (text.isNotEmpty() && text[0] == '\uFEFF') text.substring(1) else text

    /**
     * Determina qué contiene el archivo.
     *
     * **Regla de oro: un binario nunca se clasifica como texto.** Por eso [headBytes] (los
     * primeros ~4 KB reales del fichero, nunca el fichero entero) se comprueba *antes* que
     * cualquier análisis de [text]: si son los bytes mágicos de un vídeo, audio, imagen, PDF o
     * ejecutable, se devuelve [LaudateFileKind.MEDIA] sin mirar el texto. Como red de seguridad
     * adicional (por si algún llamante ya decodificó el binario entero como texto), [text]
     * también se analiza con [looksLikeBinary] antes de aceptarlo como ChordPro o JSON.
     *
     * @param text Contenido en texto, si se ha podido leer (`null` para un ZIP o un binario).
     * @param mimeType Tipo MIME declarado por la app que lo envía (a menudo genérico).
     * @param fileName Nombre visible del archivo, del que se toma el título si no hay otro.
     * @param isZip `true` si los primeros bytes son de un ZIP.
     * @param headBytes Los primeros bytes reales del archivo (una muestra pequeña, no el archivo
     *   completo), para reconocer un binario por su cabecera antes de tratarlo como texto.
     */
    fun detect(
        text: String?,
        mimeType: String? = null,
        fileName: String? = null,
        isZip: Boolean = false,
        headBytes: ByteArray? = null
    ): DetectedLaudateFile {
        // 1) Bytes mágicos de un binario conocido (vídeo, audio, imagen, PDF…): ni se intenta leer
        //    como texto. Esto es lo que impide que, por ejemplo, un MP4 se cuele como ChordPro.
        if (headBytes != null && !isZip) {
            sniffMediaKind(headBytes)?.let { description ->
                return DetectedLaudateFile(LaudateFileKind.MEDIA, title = fileName, mediaDescription = description)
            }
        }

        // 2) ZIP: puede ser una copia de seguridad o **un canto con sus adjuntos** (ADR-017). Se
        //    distinguen por la primera entrada del ZIP (`backup.json` vs `song.json`), que se lee de
        //    la propia cabecera: no hace falta descomprimir nada para saber qué es.
        if (isZip || mimeType?.contains("zip", ignoreCase = true) == true) {
            val firstEntry = headBytes?.let { zipFirstEntryName(it) }
            return when (firstEntry) {
                SONG_DATA_ENTRY -> DetectedLaudateFile(
                    kind = LaudateFileKind.SONG,
                    title = fileName,
                    isZip = true,
                    hasAttachmentFiles = true
                )
                else -> DetectedLaudateFile(
                    kind = LaudateFileKind.BACKUP,
                    title = fileName,
                    isZip = true
                )
            }
        }

        val body = stripBom(text.orEmpty()).trim()
        if (body.isEmpty()) {
            return DetectedLaudateFile(LaudateFileKind.UNKNOWN, title = fileName)
        }

        // 3) Red de seguridad: si el texto trae bytes nulos o demasiados caracteres de control, es
        //    un binario mal leído como texto (nunca una canción), aunque no se haya podido sniffear
        //    la cabecera real. Se mira solo una muestra acotada, no el documento entero.
        if (looksLikeBinary(body)) {
            return DetectedLaudateFile(
                kind = LaudateFileKind.MEDIA,
                title = fileName,
                mediaDescription = "archivo binario"
            )
        }

        // 4) JSON: se decide por el campo `format` y por la forma del objeto.
        if (body.startsWith("{") || body.startsWith("[")) {
            detectJson(body, fileName)?.let { return it }
        }

        // 5) XML (p. ej. una partitura MusicXML): Canticora aún no lo importa, pero se explica
        //    claramente en vez de tratarlo como un archivo irreconocible genérico.
        if (body.startsWith("<?xml") || body.startsWith("<")) {
            val isMusicXml = body.contains("score-partwise") || body.contains("score-timewise")
            return DetectedLaudateFile(
                kind = LaudateFileKind.MEDIA,
                title = fileName,
                mediaDescription = if (isMusicXml) "partitura digital (MusicXML)" else "documento XML"
            )
        }

        // 6) Ritmo pegado como texto (notación de rejilla con compás).
        if (RhythmTextNotation.looksLikeRhythmText(body)) {
            return DetectedLaudateFile(
                kind = LaudateFileKind.RHYTHMS,
                title = fileName,
                rawText = body,
                rhythmCount = 1
            )
        }

        // 7) ChordPro: texto plano con acordes o directivas.
        if (looksLikeChordPro(body)) {
            return DetectedLaudateFile(
                kind = LaudateFileKind.CHORDPRO,
                title = chordProTitle(body) ?: fileName,
                rawText = body
            )
        }

        return DetectedLaudateFile(LaudateFileKind.UNKNOWN, title = fileName, rawText = body)
    }

    /** Clasifica un texto JSON por su `format` declarado o por la forma de sus claves. */
    private fun detectJson(body: String, fileName: String?): DetectedLaudateFile? {
        val root = runCatching {
            json.parseToJsonElement(body).jsonObject
        }.getOrNull() ?: return null

        val declaredFormat = root.string("format")
        when {
            declaredFormat in CANTICORA_EVENT_FORMATS -> return celebrationFile(root, body, fileName)
            declaredFormat in CANTICORA_BACKUP_FORMATS -> return backupManifestFile(root, fileName)
            declaredFormat in CANTICORA_SONG_FORMATS -> return songManifestFile(root, fileName)
            declaredFormat in CANTICORA_RHYTHM_FORMATS -> return rhythmsFile(root, body, fileName)
        }

        // Sin `format`: se decide por la forma (un cancionero declara `songs` y `categories`).
        if (root.containsKey("songs") && root.containsKey("categories")) {
            return catalogFile(root, body, fileName)
        }

        // Un objeto con `patterns` (aunque no declare el formato) es un archivo de ritmos.
        if (root.containsKey("patterns")) {
            return rhythmsFile(root, body, fileName)
        }

        // Un objeto con `celebration` (aunque no declare el formato) es un repertorio.
        if (root.containsKey("celebration")) {
            return celebrationFile(root, body, fileName)
        }

        return DetectedLaudateFile(LaudateFileKind.UNKNOWN, title = fileName, rawText = body)
    }

    private fun celebrationFile(root: JsonObject, body: String, fileName: String?): DetectedLaudateFile {
        val celebration = runCatching { root["celebration"]?.jsonObject }.getOrNull()
        val name = celebration?.string("name")
        val songs = runCatching { root["songs"]?.jsonArray?.size }.getOrNull()
        val items = runCatching { celebration?.get("items")?.jsonArray?.size }.getOrNull()
        return DetectedLaudateFile(
            kind = LaudateFileKind.CELEBRATION,
            title = name?.takeIf { it.isNotBlank() } ?: fileName,
            rawText = body,
            songCount = songs,
            celebrationItemCount = items
        )
    }

    private fun backupManifestFile(root: JsonObject, fileName: String?): DetectedLaudateFile {
        val payload = runCatching { root["payload"]?.jsonObject }.getOrNull()
        val songs = payload?.arraySize("songs")
        val celebrations = payload?.arraySize("celebrations")
        val categories = payload?.arraySize("categories")
        val attachments = root.arraySize("attachments")
        return DetectedLaudateFile(
            kind = LaudateFileKind.BACKUP,
            title = fileName,
            rawText = null,
            songCount = songs,
            celebrationItemCount = celebrations,
            categoryCount = categories,
            attachmentCount = attachments
        )
    }

    private fun catalogFile(root: JsonObject, body: String, fileName: String?): DetectedLaudateFile =
        DetectedLaudateFile(
            kind = LaudateFileKind.CATALOG,
            title = fileName,
            rawText = body,
            songCount = root.arraySize("songs"),
            categoryCount = root.arraySize("categories"),
            celebrationItemCount = root.arraySize("celebrations")
        )

    /** Manifiesto de un canto suelto con adjuntos (`song.json`), leído suelto o dentro de un ZIP. */
    private fun songManifestFile(root: JsonObject, fileName: String?): DetectedLaudateFile {
        val song = runCatching { root["song"]?.jsonObject }.getOrNull()
        val attachments = root.arraySize("attachments") ?: 0
        val hasFiles = runCatching {
            root["attachments"]?.jsonArray?.any { item ->
                item.jsonObject["entryPath"]?.jsonPrimitive?.contentOrNull != null
            } ?: false
        }.getOrDefault(false)
        return DetectedLaudateFile(
            kind = LaudateFileKind.SONG,
            title = song?.string("title")?.takeIf { it.isNotBlank() } ?: fileName,
            rawText = null,
            attachmentCount = attachments,
            hasAttachmentFiles = hasFiles
        )
    }

    private fun rhythmsFile(root: JsonObject, body: String, fileName: String?): DetectedLaudateFile =
        DetectedLaudateFile(
            kind = LaudateFileKind.RHYTHMS,
            title = fileName,
            rawText = body,
            rhythmCount = root.arraySize("patterns")
        )

    /**
     * Heurística de ChordPro: una directiva conocida (`{title:}`, `{start_of_…}`, `{soc}`…) o un
     * acorde entre corchetes (`[Am]`, `[G7]`, `[Do]`). Un texto corriente no dispara ninguna.
     */
    fun looksLikeChordPro(text: String): Boolean {
        val hasDirective = CHORDPRO_DIRECTIVE.containsMatchIn(text)
        val hasChord = CHORD_IN_BRACKETS.containsMatchIn(text)
        return hasDirective || hasChord
    }

    /** Título declarado en el ChordPro (`{title: …}` / `{t: …}`), si lo trae. */
    private fun chordProTitle(text: String): String? {
        val match = CHORDPRO_TITLE.find(text) ?: return null
        val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.string(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.contentOrNull }.getOrNull()

    /** Tamaño de un array del objeto, o `null` si la clave falta o no es un array. */
    private fun JsonObject?.arraySize(key: String): Int? =
        runCatching { this?.get(key)?.jsonArray?.size }.getOrNull()

    private val CHORDPRO_DIRECTIVE = Regex(
        """\{\s*(title|t|subtitle|st|artist|composer|key|tempo|capo|start_of_\w+|end_of_\w+|soc|eoc|sov|eov|sob|eob|comment|comment_italic|c|ci|cb|number|n|repeat|x)\b""",
        RegexOption.IGNORE_CASE
    )

    /** Acorde entre corchetes: nota latina o anglosajona al principio del paréntesis. */
    private val CHORD_IN_BRACKETS = Regex(
        """\[\s*(Do|Re|Mi|Fa|Sol|La|Si|[A-G])(#|b|s)?(m|maj|min|dim|aug|sus|add|°|º|\+|-)?\d?"""
    )

    private val CHORDPRO_TITLE = Regex(
        """\{\s*(?:title|t)\s*:\s*([^}]+)\}""",
        RegexOption.IGNORE_CASE
    )

    /** `true` si los primeros bytes del archivo son de un ZIP. */
    fun looksLikeZip(head: ByteArray): Boolean {
        val prefix = head.decodeToString()
        return zipMagic.any { prefix.startsWith(it) }
    }

    /**
     * Nombre de la **primera entrada** de un ZIP, leído de su cabecera local (`PK\u0003\u0004`) sin
     * descomprimir nada.
     *
     * Se usa para distinguir un **canto con adjuntos** (`song.json`, ADR-017) de una **copia de
     * seguridad** (`backup.json`) sin pagar el coste de abrir el archivo: el paquete de canción
     * escribe su manifiesto siempre el primero. Devuelve `null` si la cabecera no se puede leer.
     */
    fun zipFirstEntryName(head: ByteArray): String? {
        // Firma local de entrada (PK\x03\x04) + versión + flags + método + fecha/hora + CRC +
        // tamaños + longitud del nombre + longitud de extra (2 bytes cada uno) = 30 bytes.
        if (head.size < 30) return null
        if (!(head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
                head[2] == 0x03.toByte() && head[3] == 0x04.toByte())
        ) return null

        val nameLength = (head[26].toInt() and 0xFF) or ((head[27].toInt() and 0xFF) shl 8)
        val extraLength = (head[28].toInt() and 0xFF) or ((head[29].toInt() and 0xFF) shl 8)
        val nameStart = 30 + extraLength
        val nameEnd = nameStart + nameLength
        if (nameLength <= 0 || nameEnd > head.size) return null
        return runCatching { head.copyOfRange(nameStart, nameEnd).decodeToString() }.getOrNull()
    }

    /** Tamaño de la muestra que se analiza para decidir si un texto es binario o ChordPro. */
    private const val SNIFF_SAMPLE_SIZE = 4096

    /**
     * Reconoce un **binario conocido** (vídeo, audio, imagen, PDF, ejecutable…) por sus primeros
     * bytes, sin decodificar nada como texto. Solo hace falta la cabecera del archivo (unos pocos
     * bytes), nunca el archivo completo. Devuelve la palabra en español para explicárselo al
     * usuario, o `null` si no coincide con ningún formato binario conocido (puede ser texto).
     */
    fun sniffMediaKind(head: ByteArray): String? {
        if (head.isEmpty()) return null

        // PDF: "%PDF"
        if (head.matchesAt(0, "%PDF")) return "documento PDF"

        // PNG: 0x89 'P' 'N' 'G' \r \n \u001A \n
        if (head.size >= 8 &&
            head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() &&
            head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()
        ) return "imagen"

        // JPEG: FF D8 FF
        if (head.size >= 3 &&
            head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        ) return "imagen"

        // GIF: "GIF8" (GIF87a / GIF89a)
        if (head.matchesAt(0, "GIF8")) return "imagen"

        // Contenedor RIFF: WAV (audio), WEBP (imagen) o AVI (vídeo) según la subcabecera.
        if (head.size >= 12 && head.matchesAt(0, "RIFF")) {
            return when {
                head.matchesAt(8, "WEBP") -> "imagen"
                head.matchesAt(8, "WAVE") -> "audio"
                head.matchesAt(8, "AVI ") -> "vídeo"
                else -> "archivo multimedia"
            }
        }

        // MP3 con etiqueta ID3, o cabecera de trama MPEG audio sin etiqueta.
        if (head.matchesAt(0, "ID3")) return "audio"
        if (head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0) return "audio"

        if (head.matchesAt(0, "fLaC")) return "audio"
        if (head.matchesAt(0, "OggS")) return "audio"
        if (head.matchesAt(0, "MThd")) return "audio" // MIDI

        // ISO Base Media (MP4, MOV, M4A, 3GP…): tamaño de caja + "ftyp" en el byte 4.
        if (head.size >= 8 && head.matchesAt(4, "ftyp")) return "vídeo"

        // Matroska / WebM: cabecera EBML.
        if (head.size >= 4 &&
            head[0] == 0x1A.toByte() && head[1] == 0x45.toByte() &&
            head[2] == 0xDF.toByte() && head[3] == 0xA3.toByte()
        ) return "vídeo"

        // Ejecutables: Windows PE/DOS ("MZ") o Linux ELF.
        if (head.matchesAt(0, "MZ")) return "programa"
        if (head.size >= 4 && head[0] == 0x7F.toByte() && head.matchesAt(1, "ELF")) return "programa"

        return null
    }

    /** `true` si los bytes en la posición [offset] coinciden con [ascii] (comparación por bytes). */
    private fun ByteArray.matchesAt(offset: Int, ascii: String): Boolean {
        if (offset < 0 || offset + ascii.length > size) return false
        for (i in ascii.indices) {
            if (this[offset + i] != ascii[i].code.toByte()) return false
        }
        return true
    }

    /**
     * Red de seguridad: `true` si una muestra de texto **no puede ser texto legítimo** (bytes
     * nulos, o demasiados caracteres de control que no son salto de línea/tabulador). Así, aunque
     * alguna vía de entrada haya decodificado un binario entero como `String`, nunca se clasifica
     * como ChordPro o JSON. Solo se analiza una muestra acotada, nunca el documento entero.
     */
    fun looksLikeBinary(text: String): Boolean {
        if (text.isEmpty()) return false
        val sample = text.take(SNIFF_SAMPLE_SIZE)
        if (sample.any { it == '\u0000' }) return true
        val controlChars = sample.count { it.code < 0x20 && it != '\n' && it != '\r' && it != '\t' }
        return controlChars.toDouble() / sample.length > 0.01
    }
}
