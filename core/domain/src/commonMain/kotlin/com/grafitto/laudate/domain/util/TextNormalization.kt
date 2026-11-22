package com.grafitto.laudate.domain.util


/**
 * Reduce un texto a minúsculas **sin diacríticos**, de forma puramente multiplataforma.
 *
 * Es el equivalente KMP de `java.text.Normalizer.normalize(text, NFD)` seguido de la eliminación
 * de las marcas de combinación (`\p{Mn}`), que **no existe** en `commonMain`. Se usa para comparar
 * etiquetas y búsquedas sin depender de tildes ni mayúsculas.
 *
 * Cubre los diacríticos de las lenguas con las que trabaja Canticora (español, portugués, francés,
 * catalán, gallego, italiano, alemán, neerlandés…) y degrada de forma segura: un carácter no
 * listado se conserva tal cual.
 */
private val DIACRITICS: Map<Char, Char> = buildMap {
    fun fold(base: Char, vararg accented: Char) = accented.forEach { put(it, base) }

    fold('a', 'á', 'à', 'ä', 'â', 'ã', 'å', 'ā', 'ă', 'ą', 'Á', 'À', 'Ä', 'Â', 'Ã', 'Å', 'Ā', 'Ă', 'Ą')
    fold('c', 'ç', 'ć', 'č', 'ĉ', 'ċ', 'Ç', 'Ć', 'Č', 'Ĉ', 'Ċ')
    fold('d', 'ď', 'đ', 'Ď', 'Đ')
    fold('e', 'é', 'è', 'ë', 'ê', 'ě', 'ē', 'ę', 'ė', 'É', 'È', 'Ë', 'Ê', 'Ě', 'Ē', 'Ę', 'Ė')
    fold('g', 'ğ', 'ĝ', 'ġ', 'ģ', 'Ğ', 'Ĝ', 'Ġ', 'Ģ')
    fold('i', 'í', 'ì', 'ï', 'î', 'ī', 'į', 'ı', 'Í', 'Ì', 'Ï', 'Î', 'Ī', 'Į', 'İ')
    fold('l', 'ł', 'ĺ', 'ľ', 'Ļ', 'Ł', 'Ĺ', 'Ľ')
    fold('n', 'ñ', 'ń', 'ň', 'ņ', 'Ñ', 'Ń', 'Ň', 'Ņ')
    fold('o', 'ó', 'ò', 'ö', 'ô', 'õ', 'ø', 'ō', 'ő', 'Ó', 'Ò', 'Ö', 'Ô', 'Õ', 'Ø', 'Ō', 'Ő')
    fold('r', 'ř', 'ŕ', 'ŗ', 'Ř', 'Ŕ', 'Ŗ')
    fold('s', 'š', 'ś', 'ş', 'ș', 'Š', 'Ś', 'Ş', 'Ș')
    fold('t', 'ť', 'ţ', 'ț', 'Ť', 'Ţ', 'Ț')
    fold('u', 'ú', 'ù', 'ü', 'û', 'ū', 'ů', 'ű', 'ų', 'Ú', 'Ù', 'Ü', 'Û', 'Ū', 'Ů', 'Ű', 'Ų')
    fold('y', 'ý', 'ÿ', 'ŷ', 'Ý', 'Ÿ', 'Ŷ')
    fold('z', 'ž', 'ź', 'ż', 'Ž', 'Ź', 'Ż')
    fold('a', 'æ', 'Æ')
    fold('e', 'œ', 'Œ')
    // `ß` (1 carácter → 2) se aproxima a 's' para no romper la comparación.
    put('ß', 's')
}

/**
 * Devuelve [text] en minúsculas y sin diacríticos (equivalente KMP de la normalización NFD de la
 * búsqueda de Canticora).
 */
fun diacriticsFold(text: String): String = buildString(text.length) {
    for (ch in text) {
        append(DIACRITICS[ch] ?: ch)
    }
}.lowercase()
