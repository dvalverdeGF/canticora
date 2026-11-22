package com.grafitto.laudate.domain.hashing

/**
 * SHA-256 en **Kotlin puro**.
 *
 * Existe porque `java.security.MessageDigest` no está disponible fuera de la JVM y el hash de
 * contenido de una canción (`SongContentHasher`) debe ser **idéntico** en Android, escritorio y web:
 * es la clave con la que se detectan duplicados al importar. Una implementación pura garantiza el
 * mismo resultado en los tres targets y evita un `expect/actual` por plataforma.
 *
 * Implementación directa de FIPS 180-4 (sin dependencias, sin estado compartido).
 */
internal object Sha256 {

    private val K = intArrayOf(
        0x428a2f98L.toInt(), 0x71374491L.toInt(), 0xb5c0fbcfL.toInt(), 0xe9b5dba5L.toInt(),
        0x3956c25bL.toInt(), 0x59f111f1L.toInt(), 0x923f82a4L.toInt(), 0xab1c5ed5L.toInt(),
        0xd807aa98L.toInt(), 0x12835b01L.toInt(), 0x243185beL.toInt(), 0x550c7dc3L.toInt(),
        0x72be5d74L.toInt(), 0x80deb1feL.toInt(), 0x9bdc06a7L.toInt(), 0xc19bf174L.toInt(),
        0xe49b69c1L.toInt(), 0xefbe4786L.toInt(), 0x0fc19dc6L.toInt(), 0x240ca1ccL.toInt(),
        0x2de92c6fL.toInt(), 0x4a7484aaL.toInt(), 0x5cb0a9dcL.toInt(), 0x76f988daL.toInt(),
        0x983e5152L.toInt(), 0xa831c66dL.toInt(), 0xb00327c8L.toInt(), 0xbf597fc7L.toInt(),
        0xc6e00bf3L.toInt(), 0xd5a79147L.toInt(), 0x06ca6351L.toInt(), 0x14292967L.toInt(),
        0x27b70a85L.toInt(), 0x2e1b2138L.toInt(), 0x4d2c6dfcL.toInt(), 0x53380d13L.toInt(),
        0x650a7354L.toInt(), 0x766a0abbL.toInt(), 0x81c2c92eL.toInt(), 0x92722c85L.toInt(),
        0xa2bfe8a1L.toInt(), 0xa81a664bL.toInt(), 0xc24b8b70L.toInt(), 0xc76c51a3L.toInt(),
        0xd192e819L.toInt(), 0xd6990624L.toInt(), 0xf40e3585L.toInt(), 0x106aa070L.toInt(),
        0x19a4c116L.toInt(), 0x1e376c08L.toInt(), 0x2748774cL.toInt(), 0x34b0bcb5L.toInt(),
        0x391c0cb3L.toInt(), 0x4ed8aa4aL.toInt(), 0x5b9cca4fL.toInt(), 0x682e6ff3L.toInt(),
        0x748f82eeL.toInt(), 0x78a5636fL.toInt(), 0x84c87814L.toInt(), 0x8cc70208L.toInt(),
        0x90befffaL.toInt(), 0xa4506cebL.toInt(), 0xbef9a3f7L.toInt(), 0xc67178f2L.toInt()
    )

    private val INITIAL_HASH = intArrayOf(
        0x6a09e667L.toInt(), 0xbb67ae85L.toInt(), 0x3c6ef372L.toInt(), 0xa54ff53aL.toInt(),
        0x510e527fL.toInt(), 0x9b05688cL.toInt(), 0x1f83d9abL.toInt(), 0x5be0cd19L.toInt()
    )

    private const val HEX = "0123456789abcdef"

    /** Hash SHA-256 del texto UTF-8, en hexadecimal minúsculas de 64 caracteres. */
    fun hex(text: String): String = hex(text.encodeToByteArray())

    fun hex(bytes: ByteArray): String {
        val digest = digest(bytes)
        return buildString(digest.size * 2) {
            for (word in digest) {
                for (shift in 28 downTo 0 step 4) append(HEX[(word ushr shift) and 0xF])
            }
        }
    }

    private fun digest(bytes: ByteArray): IntArray {
        val bitLength = bytes.size.toLong() * 8
        // Relleno: 0x80 + ceros hasta que falten 8 bytes para un múltiplo de 64, + longitud.
        val remainder = (bytes.size + 1) % 64
        val zeroPadding = if (remainder <= 56) 56 - remainder else 120 - remainder
        val message = ByteArray(bytes.size + 1 + zeroPadding + 8)
        bytes.copyInto(message)
        message[bytes.size] = 0x80.toByte()
        for (i in 0 until 8) {
            message[message.lastIndex - i] = (bitLength ushr (8 * i)).toByte()
        }

        val hash = INITIAL_HASH.copyOf()
        val schedule = IntArray(64)

        var offset = 0
        while (offset < message.size) {
            for (i in 0 until 16) {
                val base = offset + i * 4
                schedule[i] = ((message[base].toInt() and 0xFF) shl 24) or
                    ((message[base + 1].toInt() and 0xFF) shl 16) or
                    ((message[base + 2].toInt() and 0xFF) shl 8) or
                    (message[base + 3].toInt() and 0xFF)
            }
            for (i in 16 until 64) {
                val w15 = schedule[i - 15]
                val w2 = schedule[i - 2]
                val s0 = w15.rotateRight(7) xor w15.rotateRight(18) xor (w15 ushr 3)
                val s1 = w2.rotateRight(17) xor w2.rotateRight(19) xor (w2 ushr 10)
                schedule[i] = schedule[i - 16] + s0 + schedule[i - 7] + s1
            }

            var a = hash[0]
            var b = hash[1]
            var c = hash[2]
            var d = hash[3]
            var e = hash[4]
            var f = hash[5]
            var g = hash[6]
            var h = hash[7]

            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val choice = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + choice + K[i] + schedule[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + majority
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }

            hash[0] += a
            hash[1] += b
            hash[2] += c
            hash[3] += d
            hash[4] += e
            hash[5] += f
            hash[6] += g
            hash[7] += h

            offset += 64
        }
        return hash
    }

    private fun Int.rotateRight(bits: Int): Int = (this ushr bits) or (this shl (32 - bits))
}
