package com.grafitto.laudate.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.ui.common.AppLogo

private const val GRAFITTO_URL = "https://www.grafitto.es"

/**
 * Diálogo «Acerca de Canticora» con el propósito, la autoría y la misión franciscana del proyecto.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit, appVersionName: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            AppLogo(contentDescription = "Logo de Canticora", modifier = Modifier.size(72.dp))
        },
        title = {
            Text(
                text = "Acerca de Canticora",
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = LaudateBrand.FULL_NAME,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Versión $appVersionName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Cancionero musical digital para coros, misas y eventos parroquiales. " +
                        "100% Local-First (funciona sin conexión a internet), con transposición armónica " +
                        "en vivo y gestión de celebraciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = buildAnnotatedString {
                        append("Canticora ha sido creada por ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("David Valverde") }
                        append(", de ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Grafitto") }
                        append(" (")
                        withLink(
                            LinkAnnotation.Url(
                                url = GRAFITTO_URL,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = Color(0xFF1A73E8),
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            )
                        ) { append("www.grafitto.es") }
                        append("), especialmente para la ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("comunidad franciscana") }
                        append(", con el deseo de poner la tecnología al servicio de la fe, la música y la fraternidad.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = buildAnnotatedString {
                        append("Inspirada en el espíritu de ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("San Francisco de Asís") }
                        append(", nace para ayudar a comunidades, coros y hermanos a compartir la música " +
                            "litúrgica y hacer de cada canto una forma de oración y alabanza a Dios.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = buildAnnotatedString {
                        append("San Francisco nos enseñó a descubrir a Dios en la sencillez, en la fraternidad y " +
                            "en toda la creación. Siguiendo su ejemplo y el espíritu del ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Laudato si'") }
                        append(", queremos que Canticora sea también una pequeña herramienta para unir, servir y " +
                            "celebrar juntos la alegría de nuestra fe.")
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Que cada canción compartida sea una oración, cada voz una expresión de fraternidad " +
                        "y cada encuentro una ocasión para alabar al Señor.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Canticora Dominum.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
            }
        }
    )
}
