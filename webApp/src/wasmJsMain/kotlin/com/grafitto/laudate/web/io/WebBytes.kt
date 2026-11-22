package com.grafitto.laudate.web.io

/** Codificador/decodificador Base64 y CRC-32 sin dependencias (Kotlin puro, Wasm). */
internal object WebBytes {

    private const val ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val decodeTable = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
        table['-'.code] = 62
        table['_'.code] = 63
    }

    fun base64Encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val builder = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 2 < bytes.size) {
            val triple = ((bytes[index].toInt() and 0xFF) shl 16) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
            builder.append(ALPHABET[(triple ushr 18) and 0x3F])
            builder.append(ALPHABET[(triple ushr 12) and 0x3F])
            builder.append(ALPHABET[(triple ushr 6) and 0x3F])
            builder.append(ALPHABET[triple and 0x3F])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val one = (bytes[index].toInt() and 0xFF) shl 16
                builder.append(ALPHABET[(one ushr 18) and 0x3F])
                builder.append(ALPHABET[(one ushr 12) and 0x3F])
                builder.append("==")
            }
            2 -> {
                val two = ((bytes[index].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8)
                builder.append(ALPHABET[(two ushr 18) and 0x3F])
                builder.append(ALPHABET[(two ushr 12) and 0x3F])
                builder.append(ALPHABET[(two ushr 6) and 0x3F])
                builder.append('=')
            }
        }
        return builder.toString()
    }

    fun base64Decode(text: String): ByteArray {
        val cleaned = text.substringAfter("base64,", text).filterNot { it.isWhitespace() }
        if (cleaned.isEmpty()) return ByteArray(0)
        val out = ByteSink(cleaned.length * 3 / 4 + 3)
        var accumulator = 0
        var bits = 0
        cleaned.forEach { char ->
            if (char == '=') return@forEach
            val value = if (char.code < 128) decodeTable[char.code] else -1
            if (value < 0) return@forEach
            accumulator = (accumulator shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.writeByte((accumulator ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    fun crc32(bytes: ByteArray): Long {
        var crc = -1
        bytes.forEach { byte ->
            crc = (crc ushr 8) xor CRC_TABLE[(crc xor byte.toInt()) and 0xFF]
        }
        return (crc.inv().toLong()) and 0xFFFFFFFFL
    }

    private val CRC_TABLE = IntArray(256) { index ->
        var value = index
        repeat(8) {
            value = if (value and 1 != 0) (value ushr 1) xor 0xEDB88320.toInt() else value ushr 1
        }
        value
    }
}

/** Buffer de bytes ampliable (en Wasm no existe `ByteArrayOutputStream`). */
internal class ByteSink(initialCapacity: Int = 64) {

    private var data = ByteArray(if (initialCapacity > 0) initialCapacity else 64)
    private var size = 0

    val length: Int get() = size

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var capacity = data.size
        while (capacity < size + extra) capacity *= 2
        data = data.copyOf(capacity)
    }

    fun writeByte(value: Int) {
        ensure(1)
        data[size++] = value.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensure(bytes.size)
        bytes.copyInto(data, size)
        size += bytes.size
    }

    fun writeBytes(bytes: ByteArray, offset: Int, count: Int) {
        ensure(count)
        bytes.copyInto(data, size, offset, offset + count)
        size += count
    }

    /** Copia solapada hacia delante (lo que necesita DEFLATE: la distancia puede ser 1). */
    fun copyBack(distance: Int, length: Int) {
        require(distance in 1..size) { "distancia fuera de rango" }
        ensure(length)
        var from = size - distance
        repeat(length) {
            data[size++] = data[from++]
        }
    }

    fun writeU16(value: Int) {
        writeByte(value and 0xFF)
        writeByte((value ushr 8) and 0xFF)
    }

    fun writeU32(value: Long) {
        writeByte((value and 0xFF).toInt())
        writeByte(((value ushr 8) and 0xFF).toInt())
        writeByte(((value ushr 16) and 0xFF).toInt())
        writeByte(((value ushr 24) and 0xFF).toInt())
    }

    fun writeU32(value: Int) = writeU32(value.toLong() and 0xFFFF_FFFFL)

    fun toByteArray(): ByteArray = data.copyOf(size)
}
