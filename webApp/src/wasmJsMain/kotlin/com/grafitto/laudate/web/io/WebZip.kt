package com.grafitto.laudate.web.io

/** Una entrada de un archivo ZIP en memoria (nombre con `/` y bytes ya descomprimidos). */
internal class WebZipEntry(val name: String, val bytes: ByteArray)

/**
 * Lector y escritor de ZIP en Kotlin puro (sin `java.util.zip`, que no existe en Wasm).
 *
 * Se usa para las **copias de seguridad** `.canticora`, los **paquetes de canto** `.canticora.song`
 * y la lectura de las **partituras** que vienen comprimidas (`.mscz`, `.gp`, `.gpx`, `.tg`, `.mxl`).
 *
 * - Al escribir no comprime (`STORED`): el archivo es válido para cualquier descompresor y evita
 *   arrastrar un deflactor completo; el contenido (JSON + adjuntos) ya pesa poco.
 * - Al leer acepta `STORED` y `DEFLATE`, porque los ficheros que llegan de Android y de las
 *   aplicaciones de partituras sí vienen comprimidos.
 */
internal object WebZip {

    fun write(entries: List<WebZipEntry>): ByteArray {
        val out = ByteSink(1024 + entries.sumOf { it.bytes.size + it.name.length + 80 })
        val directory = ByteSink(entries.size * 90)
        entries.forEach { entry ->
            val nameBytes = entry.name.encodeToByteArray()
            val crc = WebBytes.crc32(entry.bytes)
            val offset = out.length.toLong()

            out.writeU32(LOCAL_HEADER)
            out.writeU16(20)
            out.writeU16(UTF8_FLAG)
            out.writeU16(METHOD_STORED)
            out.writeU16(0)
            out.writeU16(DOS_DATE)
            out.writeU32(crc)
            out.writeU32(entry.bytes.size.toLong())
            out.writeU32(entry.bytes.size.toLong())
            out.writeU16(nameBytes.size)
            out.writeU16(0)
            out.writeBytes(nameBytes)
            out.writeBytes(entry.bytes)

            directory.writeU32(CENTRAL_HEADER)
            directory.writeU16(20)
            directory.writeU16(20)
            directory.writeU16(UTF8_FLAG)
            directory.writeU16(METHOD_STORED)
            directory.writeU16(0)
            directory.writeU16(DOS_DATE)
            directory.writeU32(crc)
            directory.writeU32(entry.bytes.size.toLong())
            directory.writeU32(entry.bytes.size.toLong())
            directory.writeU16(nameBytes.size)
            directory.writeU16(0)
            directory.writeU16(0)
            directory.writeU16(0)
            directory.writeU16(0)
            directory.writeU32(0)
            directory.writeU32(offset)
            directory.writeBytes(nameBytes)
        }
        val directoryOffset = out.length.toLong()
        val directoryBytes = directory.toByteArray()
        out.writeBytes(directoryBytes)
        out.writeU32(END_OF_DIRECTORY)
        out.writeU16(0)
        out.writeU16(0)
        out.writeU16(entries.size)
        out.writeU16(entries.size)
        out.writeU32(directoryBytes.size.toLong())
        out.writeU32(directoryOffset)
        out.writeU16(0)
        return out.toByteArray()
    }

    fun entries(bytes: ByteArray): List<WebZipEntry> {
        val end = findEndOfDirectory(bytes)
        val total = readU16(bytes, end + 10)
        var cursor = readU32(bytes, end + 16)
        val result = ArrayList<WebZipEntry>(total)
        repeat(total) {
            require(readU32(bytes, cursor) == CENTRAL_HEADER) { "ZIP: directorio corrupto" }
            val method = readU16(bytes, cursor + 10)
            val compressedSize = readU32(bytes, cursor + 20)
            val nameLength = readU16(bytes, cursor + 28)
            val extraLength = readU16(bytes, cursor + 30)
            val commentLength = readU16(bytes, cursor + 32)
            val localOffset = readU32(bytes, cursor + 42)
            val name = bytes.decodeToString(cursor + 46, cursor + 46 + nameLength)

            val localNameLength = readU16(bytes, localOffset + 26)
            val localExtraLength = readU16(bytes, localOffset + 28)
            val dataStart = localOffset + 30 + localNameLength + localExtraLength
            val dataEnd = dataStart + compressedSize
            require(dataEnd <= bytes.size) { "ZIP: entrada truncada ($name)" }

            val content = when (method) {
                METHOD_STORED -> bytes.copyOfRange(dataStart, dataEnd)
                METHOD_DEFLATE -> WebInflate.inflate(bytes, dataStart, compressedSize)
                else -> error("ZIP: método de compresión no soportado ($method)")
            }
            result += WebZipEntry(name, content)
            cursor += 46 + nameLength + extraLength + commentLength
        }
        return result
    }

    private fun findEndOfDirectory(bytes: ByteArray): Int {
        val minimum = maxOf(0, bytes.size - 65_557)
        var index = bytes.size - 22
        while (index >= minimum) {
            if (readU32(bytes, index) == END_OF_DIRECTORY) return index
            index--
        }
        error("El archivo no es un ZIP válido")
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(bytes: ByteArray, offset: Int): Int {
        val value = (bytes[offset].toInt() and 0xFF).toLong() or
            ((bytes[offset + 1].toInt() and 0xFF).toLong() shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF).toLong() shl 24)
        return value.toInt()
    }

    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_DIRECTORY = 0x06054b50
    private const val METHOD_STORED = 0
    private const val METHOD_DEFLATE = 8
    private const val UTF8_FLAG = 0x0800
    private const val DOS_DATE = 0x21
}

/**
 * Descompresor **DEFLATE** (RFC 1951) en Kotlin puro, basado en el algoritmo de `puff`.
 *
 * Es la mitad que falta para leer los ZIP que vienen comprimidos. No comprime: solo descomprime.
 */
internal object WebInflate {

    fun inflate(source: ByteArray, offset: Int = 0, length: Int = source.size - offset): ByteArray {
        val state = Reader(source, offset, offset + length)
        val out = ByteSink(maxOf(1024, length * 4))
        var finalBlock = false
        while (!finalBlock) {
            finalBlock = state.bits(1) == 1
            when (state.bits(2)) {
                0 -> state.stored(out)
                1 -> state.codes(out, FIXED_LITERAL, FIXED_DISTANCE)
                2 -> {
                    val (literal, distance) = state.dynamicTables()
                    state.codes(out, literal, distance)
                }
                else -> error("DEFLATE: tipo de bloque desconocido")
            }
        }
        return out.toByteArray()
    }

    private class Huffman(val count: IntArray, val symbol: IntArray)

    private class Reader(private val source: ByteArray, private var position: Int, private val end: Int) {

        private var bitBuffer = 0
        private var bitCount = 0

        fun bits(needed: Int): Int {
            var value = bitBuffer
            while (bitCount < needed) {
                check(position < end) { "DEFLATE: datos incompletos" }
                value = value or ((source[position++].toInt() and 0xFF) shl bitCount)
                bitCount += 8
            }
            bitBuffer = value ushr needed
            bitCount -= needed
            return value and ((1 shl needed) - 1)
        }

        fun stored(out: ByteSink) {
            bitBuffer = 0
            bitCount = 0
            val length = bits(16)
            val complement = bits(16)
            check(length == complement.inv() and 0xFFFF) { "DEFLATE: bloque almacenado corrupto" }
            check(position + length <= end) { "DEFLATE: bloque almacenado truncado" }
            out.writeBytes(source, position, length)
            position += length
        }

        fun dynamicTables(): Pair<Huffman, Huffman> {
            val literalCount = bits(5) + 257
            val distanceCount = bits(5) + 1
            val codeCount = bits(4) + 4
            val lengths = IntArray(MAX_SYMBOLS)
            for (index in 0 until codeCount) lengths[CODE_LENGTH_ORDER[index]] = bits(3)

            var codeTable = build(lengths, 0, 19) ?: error("DEFLATE: tabla de longitudes vacía")
            var index = 0
            while (index < literalCount + distanceCount) {
                val symbol = decode(codeTable)
                when {
                    symbol < 16 -> lengths[index++] = symbol
                    else -> {
                        val repeated: Int
                        val value: Int
                        when (symbol) {
                            16 -> {
                                check(index > 0)
                                value = lengths[index - 1]
                                repeated = 3 + bits(2)
                            }
                            17 -> {
                                value = 0
                                repeated = 3 + bits(3)
                            }
                            else -> {
                                value = 0
                                repeated = 11 + bits(7)
                            }
                        }
                        check(index + repeated <= literalCount + distanceCount) {
                            "DEFLATE: longitudes repetidas de más"
                        }
                        repeat(repeated) { lengths[index++] = value }
                    }
                }
            }
            check(lengths[256] != 0) { "DEFLATE: falta el fin de bloque" }
            val literal = build(lengths, 0, literalCount)
                ?: error("DEFLATE: tabla de literales vacía")
            val distance = build(lengths, literalCount, distanceCount)
            return literal to (distance ?: EMPTY_DISTANCE)
        }

        fun codes(out: ByteSink, literal: Huffman, distance: Huffman) {
            while (true) {
                val symbol = decode(literal)
                when {
                    symbol < 256 -> out.writeByte(symbol)
                    symbol == 256 -> return
                    else -> {
                        val lengthIndex = symbol - 257
                        check(lengthIndex < LENGTH_BASE.size) { "DEFLATE: longitud inválida" }
                        val length = LENGTH_BASE[lengthIndex] + bits(LENGTH_EXTRA[lengthIndex])
                        val distanceCode = decode(distance)
                        check(distanceCode < DISTANCE_BASE.size) { "DEFLATE: distancia inválida" }
                        val back = DISTANCE_BASE[distanceCode] + bits(DISTANCE_EXTRA[distanceCode])
                        out.copyBack(back, length)
                    }
                }
            }
        }

        private fun decode(table: Huffman): Int {
            var code = 0
            var first = 0
            var index = 0
            for (length in 1..MAX_BITS) {
                code = code or bits(1)
                val count = table.count[length]
                if (code - count < first) return table.symbol[index + (code - first)]
                index += count
                first += count
                first = first shl 1
                code = code shl 1
            }
            error("DEFLATE: código de Huffman inválido")
        }

        private fun build(lengths: IntArray, offset: Int, count: Int): Huffman? {
            val counts = IntArray(MAX_BITS + 1)
            for (index in 0 until count) counts[lengths[offset + index]]++
            if (counts[0] == count) return null
            var left = 1
            for (length in 1..MAX_BITS) {
                left = left shl 1
                left -= counts[length]
                check(left >= 0) { "DEFLATE: longitudes excesivas" }
            }
            val offsets = IntArray(MAX_BITS + 2)
            for (length in 1..MAX_BITS) offsets[length + 1] = offsets[length] + counts[length]
            val symbols = IntArray(count)
            for (index in 0 until count) {
                val length = lengths[offset + index]
                if (length != 0) symbols[offsets[length]++] = index
            }
            return Huffman(counts, symbols)
        }
    }

    private const val MAX_BITS = 15
    private const val MAX_SYMBOLS = 288 + 30

    private val CODE_LENGTH_ORDER =
        intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59,
        67, 83, 99, 115, 131, 163, 195, 227, 258
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0
    )
    private val DISTANCE_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769,
        1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577
    )
    private val DISTANCE_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13
    )

    private val FIXED_LITERAL: Huffman = run {
        val lengths = IntArray(288) { index ->
            when {
                index < 144 -> 8
                index < 256 -> 9
                index < 280 -> 7
                else -> 8
            }
        }
        buildFixed(lengths)
    }

    private val FIXED_DISTANCE: Huffman = buildFixed(IntArray(30) { 5 })

    private val EMPTY_DISTANCE = Huffman(IntArray(MAX_BITS + 1), IntArray(0))

    private fun buildFixed(lengths: IntArray): Huffman {
        val counts = IntArray(MAX_BITS + 1)
        lengths.forEach { counts[it]++ }
        val offsets = IntArray(MAX_BITS + 2)
        for (length in 1..MAX_BITS) offsets[length + 1] = offsets[length] + counts[length]
        val symbols = IntArray(lengths.size)
        lengths.forEachIndexed { symbol, length -> symbols[offsets[length]++] = symbol }
        return Huffman(counts, symbols)
    }
}
