package com.grafitto.laudate.ui.tablature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabTranspositionMode

/**
 * **Visor de tablatura** (ADR-017): dibuja la rejilla de forma **vectorial** con un `Canvas`
 * (líneas de cuerda, separadores de compás y cifras de traste), **sin** caracteres `-` ni `|`.
 *
 * La maquetación vive en dominio puro ([TablatureLayout]); aquí solo se pintan las primitivas con
 * `TextMeasurer` (Compose Multiplatform, válido en Android y escritorio).
 * Es **accesible**: expone una descripción textual para los lectores de pantalla.
 */
@Composable
fun TablatureView(
    tablature: Tablature,
    modifier: Modifier = Modifier,
    /** Modo de transposición activo, para etiquetar la tablatura. */
    transpositionMode: TabTranspositionMode? = null,
    /** Muestra la cabecera con instrumento, afinación y compás. */
    showHeader: Boolean = true,
    /** Escala del dibujo (multiplica las medidas base). */
    scale: Float = 1f,
    /**
     * **Multiplicador** del tamaño de las cifras de traste sobre el que ya les da la rejilla (`1f` =
     * escala con la tablatura). Permite agrandar el número sin agrandar la rejilla.
     */
    numberScale: Float = 1f,
    /** Ajustes del pentagrama (los de MuseScore). Por defecto, el tipo `Common`. */
    options: TabStaffOptions = TabStaffOptions()
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
            .semantics { contentDescription = accessibilityDescription(tablature) },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (showHeader) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tablature.instrument,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "· ${tuningLabel(tablature)} · ${tablature.time}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (transpositionMode == TabTranspositionMode.CAPO) {
                    Text(
                        text = "· cejilla",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else if (transpositionMode == TabTranspositionMode.LOCKED) {
                    Text(
                        text = "· tonalidad original",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // El **pentagrama lo pinta el pintor único** ([TablatureCanvas]), el MISMO que usa el
        // editor: leer y crear muestran exactamente la misma tablatura (S1 del plan de paridad).
        TablatureCanvas(
            tablature = tablature,
            scale = scale,
            numberScale = numberScale,
            options = options
        )
    }
}

/** Etiqueta de la afinación comprimida (grave → aguda, convención estándar). */
private fun tuningLabel(tablature: Tablature): String =
    tablature.tuning.strings.joinToString("") { it.pitch.step.uppercaseChar().toString() }

/** Descripción textual de la tablatura para lectores de pantalla (TalkBack). */
fun accessibilityDescription(tablature: Tablature): String {
    val builder = StringBuilder()
    builder.append("Tablatura de ${tablature.instrument}, ${tablature.measures.size} compases. ")
    tablature.measures.forEachIndexed { index, measure ->
        val notes = measure.slots.flatMap { slot -> slot.strings }
        if (notes.isNotEmpty()) {
            builder.append("Compás ${index + 1}: ")
            builder.append(notes.joinToString(", ") { describePluck(it) })
            builder.append(". ")
        }
        measure.navigation?.let { builder.append("Compás ${index + 1}: ${it.label}. ") }
        measure.jump?.let { builder.append("Compás ${index + 1}: salto ${it.label}. ") }
        measure.dynamic?.let { builder.append("Compás ${index + 1}: dinámica ${it.glyph}. ") }
    }
    return builder.toString().trim()
}

private fun describePluck(pluck: TabPluck): String {
    val fret = if (pluck.fret == 0) "al aire" else "traste ${pluck.fret}"
    // La técnica se describe con su nombre (trémolo, ligado, bend…), no solo con el trémolo: la
    // paleta ofrece ya todas las familias (§13.5.bis), así que el lector de pantalla debe leerlas.
    // Las técnicas **se combinan** en la misma nota, así que se enumeran todas.
    val techniques = pluck.techniques
        .filter { it != com.grafitto.laudate.domain.model.tablature.TabTechnique.NONE }
        .joinToString(", ") { com.grafitto.laudate.domain.tablature.techniqueLabel(it).lowercase() }
    val technique = if (techniques.isEmpty()) "" else " con $techniques"
    val bend = pluck.bendAmount?.takeIf { pluck.hasBend }?.let { ", estiramiento ${it.label}" }.orEmpty()
    val palmMute = pluck.palmMuteSpan?.let { ", apagado de palma de $it casillas" }.orEmpty()
    return "cuerda ${pluck.stringNumber} $fret$technique$bend$palmMute"
}
