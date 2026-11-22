package com.grafitto.laudate.ui.celebration.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.usecase.GetTransposedSongUseCase
import com.grafitto.laudate.domain.usecase.ParseChordProUseCase
import com.grafitto.laudate.ui.song.SongDetailScreen
import com.grafitto.laudate.ui.song.SongDetailUiIntent
import com.grafitto.laudate.ui.song.SongDetailUiState
import com.grafitto.laudate.ui.song.SongDetailViewModel
import com.grafitto.laudate.ui.theme.M3Baseline
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.grafitto.laudate.ui.platform.KeepScreenOn

/**
 * **Modo Celebración en vivo**: lector a pantalla completa para el atril.
 *
 * Reutiliza el visor de canción ([SongDetailScreen]) para cada canto del repertorio y le pasa sus
 * propias acciones de barra ([SongDetailScreen.topBarActions]): así el título del canto y los iconos
 * (pantalla completa / cierre) van **en la misma fila** y a la misma altura, justo bajo la barra de
 * notificaciones. El progreso litúrgico (`[Canto N de M]` + momento) aparece como subtítulo bajo el
 * título.
 */
@Composable
fun LiveCelebrationScreen(
    celebrationId: String,
    celebrationRepository: CelebrationRepository,
    songRepository: SongRepository,
    getTransposedSongUseCase: GetTransposedSongUseCase,
    parseChordProUseCase: ParseChordProUseCase,
    onCloseLiveMode: () -> Unit,
    /** Preferencias globales: el modo en vivo lee y persiste la nomenclatura (Do-Re-Mi / C-D-E). */
    preferencesRepository: PreferencesRepository? = null,
    /** Tablaturas (ADR-017): el modo en vivo las muestra segun el modo de visualizacion. */
    tablatureRepository: TablatureRepository? = null,
    /**
     * Informa del **canto que se está viendo** y de cómo hablarle. El panel de ajustes de la app vive
     * **fuera** de esta pantalla, así que sin este aviso no podía saber qué canto hay delante ni
     * cambiarle el tamaño de letra o de tablatura: en el Modo Celebración el panel salía sin ningún
     * deslizador (lo reportó el usuario). Se llama también al **deslizar** a otro canto, con lo que
     * los mandos se adaptan al que está en pantalla.
     */
    onActiveSongChange: ((SongDetailUiState, (SongDetailUiIntent) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Keep Screen On
    KeepScreenOn()

    var celebration by remember { mutableStateOf<Celebration?>(null) }
    var loadedSongs by remember { mutableStateOf<Map<String, Song>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    // Modo sin distracciones: oculta las barras de progreso y navegación y deja solo el canto.
    var focusMode by remember { mutableStateOf(false) }

    LaunchedEffect(celebrationId) {
        val cel = celebrationRepository.getCelebrationById(celebrationId).firstOrNull()
        if (cel != null) {
            celebration = cel
            // Se resuelven todos los cantos en una sola consulta: el modo en vivo debe arrancar
            // rápido y no lanzar una lectura por canto.
            loadedSongs = songRepository
                .getSongsByIds(cel.items.map { it.songId })
                .associateBy { it.id }
        }
        isLoading = false
    }

    if (isLoading || celebration == null) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val items = celebration?.items.orEmpty()
    if (items.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text("Esta celebración no tiene cantos asignados", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    // Altura de la barra de estado. El visor no es edge-to-edge por sí mismo; el modo vivo le reserva
    // este inset para que su barra de título quede justo bajo las notificaciones (sin hueco de más).
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = M3Baseline.Neutral6 // Superficie oscura baseline (uso en atril)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = items[page]
                val song = loadedSongs[item.songId]

                if (song != null) {
                    val viewModel = remember(item.id) {
                        SongDetailViewModel(
                            songRepository = songRepository,
                            getTransposedSongUseCase = getTransposedSongUseCase,
                            parseChordProUseCase = parseChordProUseCase,
                            preferencesRepository = preferencesRepository,
                            tablatureRepository = tablatureRepository
                        ).apply {
                            processIntent(SongDetailUiIntent.LoadSong(song.id))
                        }
                    }
                    val uiState by viewModel.uiState.collectAsState()
                    val progress = "[Canto ${page + 1} de ${items.size}]"
                    val moment = item.liturgicalMoment?.uppercase().orEmpty()

                    // Publica el canto activo (y su procesador de intents) para que el panel de
                    // ajustes enseñe y cambie los tamaños de **este** canto.
                    LaunchedEffect(viewModel, uiState) {
                        onActiveSongChange?.invoke(uiState, viewModel::processIntent)
                    }

                    SongDetailScreen(
                        uiState = uiState,
                        onIntent = viewModel::processIntent,
                        // En el modo vivo el foco lo gestiona esta pantalla, no el visor.
                        focusModeOverride = focusMode,
                        onToggleFocus = null,
                        // En el modo canción de misa la única acción es «salir»: nunca deben
                        // aparecer «editar» ni «eliminar» (se confundiría con quitar de la misa).
                        showActions = false,
                        // Progreso litúrgico como subtítulo, bajo el título del canto.
                        subtitle = {
                            Text(
                                text = if (moment.isBlank()) progress else "$progress · $moment",
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        },
                        topBarActions = {
                            if (!focusMode) {
                                IconButton(onClick = { focusMode = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Fullscreen,
                                        contentDescription = "Modo sin distracciones"
                                    )
                                }
                                IconButton(onClick = onCloseLiveMode) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar Modo Vivo"
                                    )
                                }
                            }
                        },
                        contentPadding = PaddingValues(top = statusBarHeight),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // En modo sin distracciones solo queda una salida discreta, sin romper la lectura.
            if (focusMode) {
                FilledTonalIconButton(
                    onClick = { focusMode = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Mostrar controles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Controles de navegación táctil sobredimensionados para el atril.
            //
            // **Reservan la barra de navegación del sistema** (petición del usuario): iban con un
            // `padding(16.dp)` a secas, así que en la tableta los botones de «canto anterior» y
            // «siguiente» quedaban **debajo del menú de tres botones de Android** y no se podían
            // pulsar. No basta con `navigationBarsPadding()`: hay que añadir después el margen
            // propio, porque el inset se suma al padding y el orden importa.
            if (!focusMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pagerState.currentPage > 0) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.size(56.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Canto Anterior")
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }

                    // Recordatorio de que también se puede deslizar, además de usar los botones.
                    Text(
                        text = "Desliza para cambiar de canto",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )

                    if (pagerState.currentPage < items.size - 1) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier.size(56.dp),
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Siguiente Canto")
                        }
                    } else {
                        Spacer(modifier = Modifier.size(56.dp))
                    }
                }
            }
        }
    }
}
