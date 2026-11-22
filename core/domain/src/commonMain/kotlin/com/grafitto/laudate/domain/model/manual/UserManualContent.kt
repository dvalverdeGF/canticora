package com.grafitto.laudate.domain.model.manual

import com.grafitto.laudate.domain.model.LaudateBrand

/**
 * **Manual de usuario de Canticora** (contenido canónico).
 *
 * Fuente de verdad del texto que se muestra en la pantalla «Manual de uso». Al ser un
 * `object` de Kotlin puro, la app lo embarca compilado (sin assets, sin parser Markdown,
 * sin coste de APK) y **sin conexión a internet** (Local-First).
 *
 * Reglas de mantenimiento:
 * - El manual describe **solo funciones que existen** en la app; no anuncia fases futuras.
 * - Usa terminología musical/litúrgica en español (`tonalidad`, `capo`, `momento litúrgico`).
 * - Al añadir o cambiar una función, actualiza la sección correspondiente y, si procede,
 *   refleja el cambio en `docs/USER_MANUAL.md` (documento versionado).
 * - Los `id` de capítulos y secciones deben permanecer **estables** (los usa el buscador
 *   interno y la navegación desde el índice; cambiarlos rompe los atajos).
 */
object UserManualContent {

    /** Versión del contenido del manual (se muestra al pie de la pantalla). */
    const val VERSION = "1.26"

    /** Fecha de la última revisión del manual (ISO-8601). */
    const val LAST_UPDATED = "2026-10-30"

    val chapters: List<ManualChapter> = listOf(
        chapterPrimerosPasos(),
        chapterCatalogo(),
        chapterBusqueda(),
        chapterLectura(),
        chapterTransposicion(),
        chapterTablaturas(),
        chapterRitmos(),
        chapterMisas(),
        chapterCategorias(),
        chapterEditor(),
        chapterBackup(),
        chapterImportar(),
        chapterApendiceChordPro(),
        chapterAtajosConsejos()
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 1 · Primeros pasos
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterPrimerosPasos() = ManualChapter(
        id = "primeros-pasos",
        title = "1. Primeros pasos",
        icon = ManualChapterIcon.START,
        sections = listOf(
            ManualSection(
                id = "primeros-pasos/que-es",
                title = "¿Qué es Canticora?",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "**${LaudateBrand.FULL_NAME}**. Es un cancionero digital para coros, " +
                            "parroquias y comunidades. " +
                            "Las canciones no son documentos cerrados (PDF o fotos), sino **datos " +
                            "musicales estructurados**: textos y acordes separados, que la app puede " +
                            "transponer, reordenar y presentar de la forma que necesites."
                    ),
                    ManualBlock.Note(
                        "Canticora funciona **100 % sin conexión**: todo se guarda en tu dispositivo. " +
                            "No necesitas cuenta ni internet, ni siquiera en el templo."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Al abrir la app por primera vez verás la pantalla **Canciones** vacía: Canticora no trae canciones",
                            "Para llenarlo, abre **☰ → Importar** y trae tu cancionero (mira el capítulo 11).",
                            "Abre el **menú** (☰, arriba a la izquierda) para moverte entre Canciones, " +
                                "Misas y Eventos, Patrones Rítmicos, Gestión de Categorías y este Manual.",
                            "La barra inferior te permite saltar entre **Canciones**, **Misas y Eventos** y **Ritmos**."
                        )
                    )
                )
            ),
            ManualSection(
                id = "primeros-pasos/pantallas",
                title = "Las pantallas de la app",
                blocks = listOf(
                    ManualBlock.Reference(
                        caption = "Mapa general",
                        headers = listOf("Pantalla", "Para qué sirve"),
                        rows = listOf(
                            listOf("Canciones", "Explorar, buscar y leer las canciones guardadas."),
                            listOf("Misas y Eventos", "Preparar repertorios (misas, vigilias, ensayos…)."),
                            listOf("Modo Celebración", "Pasar de canto en canto durante la celebración, a pantalla completa."),
                            listOf("Patrones Rítmicos", "Crear y consultar patrones de rasgueo."),
                            listOf("Importar", "Traer tu cancionero, un repertorio, canciones en ChordPro o una copia de seguridad."),
                            listOf("Copia de seguridad", "Guardar, restaurar y **sincronizar** toda tu biblioteca entre dispositivos con un archivo."),
                            listOf("Gestión de Categorías", "Añadir o quitar momentos y tiempos litúrgicos propios."),
                            listOf("Manual de uso", "Esta misma guía, buscable y siempre disponible sin conexión."),
                            listOf("Apoyar", "Abrir en el navegador la página de apoyo al proyecto (`canticora.com/apoyar`)."),
                            listOf("Acerca de Canticora", "Autoría y propósito del proyecto.")
                        )
                    )
                )
            ),
            ManualSection(
                id = "primeros-pasos/lectura-rapida",
                title = "Lectura rápida: lo esencial en 5 pasos",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "En **Canciones**, escribe en el buscador o toca un canto.",
                            "En la ficha del canto, pulsa el botón **♭ / ♯** para transportar si hace falta.",
                            "Elige el tamaño de letra y, si quieres, «Solo letra» para los cantantes.",
                            "En **Misas y Eventos**, crea una celebración y añade los cantos a cada momento.",
                            "En la celebración, pulsa el icono **▶** para entrar en el Modo Celebración."
                        )
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 2 · Canciones
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterCatalogo() = ManualChapter(
        id = "catalogo",
        title = "2. El cancionero",
        icon = ManualChapterIcon.CATALOG,
        sections = listOf(
            ManualSection(
                id = "catalogo/explorar",
                title = "Explorar y filtros rápidos",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "La pantalla **Canciones** lista todos los cantos. Arriba tienes el buscador y, " +
                            "debajo, atajos rápidos: la tarjeta de cada canto muestra el número (si lo tiene) " +
                            "y el título. Tocar la tarjeta abre el canto; el menú de tres puntos (⋮) agrupa " +
                            "**Abrir**, **Añadir a misa/evento**, **Compartir**, **Exportar a PDF** y " +
                            "**Eliminar**."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "El buscador aparece **plegado** como una píldora **Buscar**. Al tocarla se " +
                                "despliega el campo de búsqueda y, debajo, los **filtros rápidos**.",
                            "Para volver a plegarlo, pulsa la **flecha atrás** de la izquierda del campo " +
                                "(o el gesto/botón atrás del sistema).",
                            "Usa el chip **Filtros** para desplegar los filtros por **momento litúrgico**, " +
                                "**tiempo litúrgico** y **categorías adicionales**. El panel tiene **scroll**: " +
                                "si hay muchas categorías, desplázalo para verlas todas.",
                            "Activa el chip **Favoritos** para ver solo tus cantos marcados.",
                            "Activa el chip **Sin categoría** para ver solo los cantos a los que todavía no " +
                                "has asignado ninguna etiqueta: es la forma rápida de encontrar lo que falta " +
                                "por clasificar.",
                            "Los filtros activos aparecen como chips que puedes quitar uno a uno o **limpiar todos**.",
                            "Junto a los filtros, el chip de **orden** cambia cómo se lista: **A-Z** (título), " +
                                "**Nº** (número de cancionero) o **Recientes** (última modificación).",
                            "El botón **+** (abajo a la derecha) crea una canción nueva."
                        )
                    ),
                    ManualBlock.Note(
                        "Al hacer **scroll hacia abajo**, el título y el buscador se retiran para que el " +
                            "listado aproveche toda la pantalla; al hacer **scroll hacia arriba** vuelven a " +
                            "aparecer. La **barra de navegación inferior** (Canciones · Misas y Eventos · " +
                            "Ritmos) se comporta igual: se oculta al bajar y reaparece al subir."
                    ),
                    ManualBlock.Note(
                        "Con un cancionero grande, el catálogo muestra solo una **muestra** (200 cantos) " +
                            "hasta que concretes la búsqueda: escribe al menos **3 letras** o elige un filtro " +
                            "para ver todos los resultados. Así la escritura y el desplazamiento van fluidos."
                    ),
                    ManualBlock.Note(
                        "La estrella ★ marca un canto como **favorito**. Los favoritos se consultan tanto " +
                            "aquí como con el filtro «Solo favoritos» del buscador."
                    )
                )
            ),
            ManualSection(
                id = "catalogo/numero",
                title = "El número de canción",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El **número** identifica una canción dentro del cancionero de origen (p. ej. " +
                            "«402»). Es **opcional** y **puede repetirse**: varios cancioneros reutilizan " +
                            "números y hay varios cantos con el mismo. Sirve para citarlos en los ensayos " +
                            "(«el Aleluya 402»)."
                    ),
                    ManualBlock.Note(
                        "Al buscar solo un número (p. ej. «402»), ese canto aparece **el primero**, " +
                            "aunque otro lo contenga dentro de la letra."
                    )
                )
            ),
            ManualSection(
                id = "catalogo/crear-borrar",
                title = "Crear y borrar canciones",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Pulsa el botón **+** para abrir el editor de una canción nueva.",
                            "Si vas a partir de un texto ChordPro, acepta «¿Importar desde ChordPro?» y pega el texto.",
                            "Rellena título (obligatorio) y, si quieres, número, autor, tonalidad, ritmo y patrones.",
                            "Guarda con el botón **Guardar** de la barra inferior (a su lado tienes " +
                                "**Compartir** y **PDF**).",
                            "Para borrar, abre el canto y usa el botón **Eliminar** de la barra inferior; la app " +
                                "pide confirmación."
                        )
                    ),
                    ManualBlock.Note(
                        "Borrar una canción **no** la quita de las misas ya creadas, pero al abrirlas " +
                            "aparecerá como «Canción no disponible»."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 3 · Buscador
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterBusqueda() = ManualChapter(
        id = "busqueda",
        title = "3. Buscar cantos",
        icon = ManualChapterIcon.SEARCH,
        sections = listOf(
            ManualSection(
                id = "busqueda/como-busca",
                title = "Cómo escribe el buscador",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El buscador **no distingue mayúsculas ni tildes** y busca a la vez en el " +
                            "**título**, el **autor**, el **número**, el **ritmo**, los **momentos y tiempos** " +
                            "asignados y la **letra** del canto."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Escribe varias palabras: se consideran **acumulativas** (deben aparecer todas).",
                            "Buscar «corazon» encuentra igualmente «Corazón».",
                            "Si escribes un número, el canto con ese número va primero.",
                            "Puedes **ordenar** los resultados por Título, Número de cancionero o Modificados recientemente."
                        )
                    )
                )
            ),
            ManualSection(
                id = "busqueda/filtros",
                title = "Filtrar por momento, tiempo, categoría y tonalidad",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En el buscador (el que se abre como panel inferior desde Misas y Eventos, y los " +
                            "chips de filtros de Canciones) puedes combinar filtros: **momento litúrgico**, " +
                                "**tiempo litúrgico**, **categoría adicional**, **tonalidad original**, " +
                                "**solo favoritos** y **solo cantos sin categoría**."
                    ),
                    ManualBlock.Note(
                            "Los filtros se combinan con **Y**: al activar «Comunión» y «Adviento» solo verás " +
                                "cantos que cumplan ambos."
                    ),
                    ManualBlock.Note(
                            "«Sin categoría» muestra los cantos que **no tienen ninguna etiqueta** (ni momento, " +
                                "ni tiempo, ni categoría adicional). Es útil para repasar el catálogo y clasificar " +
                                "lo que quedó pendiente."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 4 · Lectura del canto
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterLectura() = ManualChapter(
        id = "lectura",
        title = "4. Leer un canto",
        icon = ManualChapterIcon.READING,
        sections = listOf(
            ManualSection(
                id = "lectura/controles",
                title = "El panel de controles",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Al abrir un canto verás la letra con sus acordes. Toca el panel **«Tono: …»** " +
                            "para desplegar o plegar los controles. La pantalla se mantiene **encendida** " +
                            "mientras lees."
                    ),
                    ManualBlock.Reference(
                        caption = "Qué encontrarás en el panel",
                        headers = listOf("Control", "Efecto"),
                        rows = listOf(
                            listOf("♭ y ♯", "Transpone la canción un semitono abajo o arriba."),
                            listOf("↻ (Restaurar)", "Vuelve a la tonalidad original del canto."),
                            listOf("Notación", "Elige entre Latina (Do-Re-Mi) y Anglosajona (C-D-E)."),
                            listOf("Modo de vista", "«Acordes sobre letra» o «Solo letra»."),
                            listOf("Representar ritmo", "Cómo se dibujan los patrones: flechas, figuras, combinada o símbolos ChordPro."),
                            listOf("A- / A+", "Ajusta el tamaño de la letra.")
                        )
                    )
                )
            ),
            ManualSection(
                id = "lectura/modos",
                title = "Modos de vista",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En la barra de controles, el grupo **Ver** ofrece **cinco modos**: " +
                            "**Solo letra**, **Letra y acordes**, **Letra y tablatura**, " +
                            "**Letra, acordes y tablatura** y **Solo tablatura**. Los modos con tablatura " +
                            "solo aparecen si el canto tiene alguna. La elección se **recuerda** entre " +
                            "canciones y sesiones."
                    ),
                    ManualBlock.Paragraph(
                        "**Letra y acordes**: cada acorde aparece encima de la sílaba donde se toca. " +
                            "Es la vista para instrumentistas."
                    ),
                    ManualBlock.Paragraph(
                        "**Solo letra**: oculta todos los acordes. Ideal para cantantes o para proyectar. " +
                            "Las líneas que solo tenían acordes (intros instrumentales) se omiten."
                    ),
                    ManualBlock.Paragraph(
                        "**Solo tablatura**: muestra únicamente la tablatura (dibujada, no como texto)."
                    ),
                    ManualBlock.Note(
                        "Cuando el modo muestra **letra y tablatura** a la vez, el grupo **Tablatura** " +
                            "permite colocarla **Arriba**, **Abajo** o **A la derecha** de la letra. " +
                            "También se recuerda."
                    ),
                    ManualBlock.Note(
                        "El **PDF** respeta el modo elegido: si estás en «Solo letra», el PDF sale solo con " +
                            "la letra; con tablatura, incluye la tablatura."
                    )
                )
            ),
            ManualSection(
                id = "lectura/no-distraer",
                title = "Modo sin distracciones",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Cuando ya conoces el canto y solo quieres la letra, activa el **modo sin " +
                            "distracciones** con el botón **Pantalla completa** de la barra superior. " +
                            "Desaparecen el menú, los metadatos y todos los controles: en pantalla solo quedan " +
                            "el **título** y la **canción**."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Abre un canto desde **Canciones**.",
                            "Pulsa el botón **Pantalla completa** de la barra superior.",
                            "Para recuperar los controles, pulsa el mismo botón que queda en la esquina " +
                                "superior derecha."
                        )
                    ),
                    ManualBlock.Note(
                        "La pantalla permanece **encendida** mientras lees, también en modo sin " +
                            "distracciones. Ahorra batería al bajar el brillo, no la pantalla."
                    )
                )
            ),
            ManualSection(
                id = "lectura/estructura",
                title = "Estrofas, coro y otras secciones",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Cada sección del canto (Intro, Estrofa, Coro, Puente, Interludio, Final) se muestra " +
                            "con su propio color y rótulo. Si la sección se repite, verás la indicación " +
                            "«↻ Se repite ×N»."
                    ),
                    ManualBlock.Paragraph(
                        "Los comentarios del canto (indicaciones de ensayo, entradas de voces…) aparecen " +
                            "en cursiva destacada. Los símbolos de rasgueo se dibujan en su lugar."
                    )
                )
            ),
            ManualSection(
                id = "lectura/adjuntos",
                title = "Adjuntos: audios, vídeos y documentos",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Cada canto puede llevar sus propios **adjuntos**: grabaciones de ensayo, " +
                            "vídeos, partituras en PDF, imágenes… y también **enlaces** (YouTube, Drive, la web " +
                            "del autor). Los verás al final de la ficha del canto, bajo el título **Adjuntos**."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Abre el canto y desliza hasta la sección **Adjuntos**.",
                            "Pulsa **Archivo** para elegir un audio, vídeo, PDF o imagen de tu teléfono.",
                            "O pulsa **Enlace** para pegar una dirección de Internet (debe empezar por `https://`).",
                            "Toca un adjunto para abrirlo con la aplicación correspondiente de tu móvil."
                        )
                    ),
                    ManualBlock.Note(
                        "Los archivos se **copian dentro de Canticora**, así que siguen disponibles aunque " +
                            "borres el original, y **viajan dentro de tus copias de seguridad**. Los enlaces " +
                            "solo guardan la dirección. Con el botón **Descargar** descargas un adjunto a la carpeta que elijas, " +
                            "y con la papelera lo eliminas (eso borra también la copia guardada)."
                    ),
                    ManualBlock.Note(
                        "Si compartes un canto **con sus adjuntos** (ver «Compartir un canto con sus " +
                            "adjuntos»), los **archivos** viajan copiados dentro del archivo, pero los " +
                            "**enlaces** solo viajan como su dirección de Internet: si el vídeo de YouTube " +
                            "es privado, quien lo reciba podría no poder verlo."
                    )
                )
            ),
            ManualSection(
                id = "lectura/tablaturas",
                title = "Tablaturas para guitarra, laúd y bajo",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Además de la letra y los acordes, cada canto puede tener **tablaturas**: la " +
                            "digitación escrita cuerda a cuerda. Abre el canto y toca la tarjeta " +
                            "**Tablaturas**, junto a los adjuntos."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Pulsa **Nueva tablatura** y elige el **instrumento**: guitarra, guitarra en " +
                                "*drop D*, bajo de 4, 5 o 6 cuerdas, ukelele o laúd/bandurria.",
                            "Elige el **compás** (4/4, 3/4, 6/8…) y la **subdivisión** (negras, corcheas o " +
                                "semicorcheas).",
                            "Selecciona la **cuerda** y el **traste** y ve tocando las casillas de la " +
                                "rejilla. La cuerda más **aguda** se dibuja arriba, como en cualquier tablatura.",
                            "Guarda con **✓**. Puedes tener **varias tablaturas** para el mismo canto " +
                                "(por ejemplo, una de guitarra y otra de bajo)."
                        )
                    ),
                    ManualBlock.Note(
                        "Si ya tienes la pieza escrita en otro programa, el botón de **importar** de la " +
                            "lista de tablaturas la convierte en una tablatura de Canticora. Lee " +
                            "**MuseScore** (`.mscz`), **Guitar Pro 7 y 8** (`.gp`, `.gpx`), **TuxGuitar** " +
                            "(`.tg`) y **MusicXML** (`.musicxml`, `.xml`, `.mxl`)."
                    ),
                    ManualBlock.Paragraph(
                        "Las cuerdas se dibujan **punteadas** mientras editas, como en un borrador, y " +
                            "**continuas** en la vista final. Pegada a la partitura —no se va con el " +
                            "scroll— hay una **barra de escritura** con todo lo que hace falta para " +
                            "escribir sin abrir ningún panel: **Reproducir** y **Metrónomo**; las " +
                            "**figuras** (redonda, blanca, negra, corchea, semicorchea) con su **puntillo**, " +
                            "su **tresillo** y sus **silencios**; el **Teclado** numérico (solo en táctil) " +
                            "para escribir el traste sin soltar la partitura; los botones **1 2 3 4** de la " +
                            "**voz** activa, cada una con su color; y el botón **+** con los símbolos que " +
                            "se aplican a la **nota del cursor** sin salir del modo de entrada."
                    ),
                    ManualBlock.Paragraph(
                        "**Escribir como en MuseScore.** El editor tiene un **modo de entrada de notas** " +
                            "(`N`, o el botón del lápiz). Con el modo activo, el cursor se ve como un " +
                            "**rectángulo azul** del color de la voz y **tocar una cuerda escribe un `0`** " +
                            "(cuerda al aire); con el modo apagado, tocar solo **mueve el cursor**. Truco: " +
                            "aunque el modo esté apagado, **teclear la primera cifra entra en el modo y " +
                            "escribe**, para no tener que pulsar `N` en cada nota."
                    ),
                    ManualBlock.Paragraph(
                        "**Atajos de teclado.** Con un **teclado físico** (móvil con teclado, tablet u " +
                            "ordenador) se escribe sin tocar la pantalla, con los mismos atajos que " +
                            "MuseScore en un pentagrama de tablatura:"
                    ),
                    ManualBlock.Reference(
                        caption = "Atajos del editor de tablatura",
                        headers = listOf("Tecla", "Qué hace"),
                        rows = listOf(
                            listOf(
                                "`0`–`9`, `A`–`K` (sin la `I`)",
                                "Escriben el traste. Dos cifras seguidas forman el traste (1 y 2 = 12); " +
                                    "un `0` seguido de otra cifra empieza de nuevo"
                            ),
                            listOf("`←` `→`", "Recorren las casillas, saltando de compás"),
                            listOf(
                                "`↑` `↓`",
                                "Cambian de **cuerda** en modo entrada; fuera de él suben o bajan el **traste**"
                            ),
                            listOf("`Mayús` + `←`/`→`", "**Extienden** la selección de notas"),
                            listOf("`N`", "Entra o sale del **modo de entrada**"),
                            listOf("`Esc`", "Sale del modo de entrada y deselecciona"),
                            listOf("`Espacio`", "Reproduce o para"),
                            listOf(
                                "`Retroceso`",
                                "Corrige lo que estás tecleando; cuando ya está vacío, borra la nota (o " +
                                    "retrocede si no la había)"
                            ),
                            listOf("`Supr`", "Borra la nota o la selección **sin moverse**"),
                            listOf(
                                "`Intro`",
                                "**Salto de sistema**: el compás del cursor empieza un renglón nuevo"
                            ),
                            listOf(
                                "`Ctrl` + `Intro`",
                                "**Salto de página**: en el PDF el compás empieza hoja (implica el de sistema)"
                            ),
                            listOf("`Ins`", "Inserta un compás **delante** del cursor"),
                            listOf("`Q` / `W`", "Acortan o alargan la **duración** de la nota"),
                            listOf("`Mayús` + `0`…`9`", "Fijan la duración"),
                            listOf("`.`", "**Puntillo** de la figura del cursor"),
                            listOf("`;`", "Inserta un **silencio** de la figura activa"),
                            listOf("`T`", "**Ligadura de prolongación** (misma altura, misma cuerda)"),
                            listOf("`S`", "**Ligadura de expresión**"),
                            listOf("`/`", "**Nota de adorno**; `Mayús` + `/` la quita"),
                            listOf("`Mayús` + `X`", "**Nota fantasma**"),
                            listOf(
                                "`Mayús` + `V` / `O` / `S` / `N`",
                                "*Sforzato*, *marcato*, picado y *tenuto*"
                            ),
                            listOf("`R`", "**Duplica** la selección"),
                            listOf("`Ctrl` + `Z` / `Ctrl` + `Mayús` + `Z`", "Deshacer y rehacer"),
                            listOf("`Ctrl` + `A`", "Selecciona todas las notas"),
                            listOf(
                                "`Ctrl` + `C` / `X` / `V`",
                                "Copiar, cortar y pegar (notas o compases)"
                            ),
                            listOf("`Ctrl` + `D`", "Duplica"),
                            listOf("`Ctrl` + `S`", "Guarda la tablatura"),
                            listOf("`Ctrl` + `B`", "Añade un compás al final"),
                            listOf(
                                "`Ctrl` + `Ins`",
                                "Inserta **tantos** compases como tenga la selección"
                            ),
                            listOf("`Ctrl` + `Supr`", "Elimina los **compases** seleccionados"),
                            listOf("`Alt` + `Supr`", "Quita la **columna** del cursor"),
                            listOf(
                                "`Alt` + `↑` / `↓`",
                                "Mueve el compás (o el bloque) un puesto atrás o adelante"
                            ),
                            listOf(
                                "`Ctrl` + `↑` / `↓`",
                                "Mueve la nota a la **cuerda de al lado sin cambiar su altura**"
                            ),
                            listOf(
                                "`Alt` + `Mayús` + `↑` / `↓`",
                                "Recoloca la nota en la cuerda donde el traste sea **más bajo**"
                            ),
                            listOf("`Alt` + `Mayús` + `B`", "Añade compases al final"),
                            listOf(
                                "`Ctrl` + `3` / `5` / `6` / `7` / `9`",
                                "Tresillo, cinquillo, seisillo, septillo y nonillo"
                            ),
                            listOf("`Ctrl` + `Alt` + `1`…`4`", "**Voz** activa"),
                            listOf("`Ctrl` + `←` / `→`", "Compás anterior / siguiente"),
                            listOf("`Ctrl` + `Inicio` / `Fin`", "Principio / final de la partitura"),
                            listOf(
                                "`Ctrl` + `+` / `-` / `0`",
                                "Acercar, alejar y volver al 100 %"
                            ),
                            listOf(
                                "`RePág` / `AvPág`",
                                "Desplazan la **vista** una pantalla sin mover el cursor"
                            ),
                            listOf("`F8` / `F9`", "Muestran u ocultan **propiedades** y **paletas**")
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Si no recuerdas una tecla, despliega **Atajos de teclado** en el panel de " +
                            "herramientas: cada botón **repite** la acción y se **apaga** cuando no puede " +
                            "hacer nada (por ejemplo, los de nota cuando no hay nota en la cuerda activa), " +
                            "así que nunca promete algo que no va a ocurrir."
                    ),
                    ManualBlock.Paragraph(
                        "Al tocar una casilla, el panel ofrece las **marcas** del compás y de la nota, " +
                            "agrupadas por familias y con **un botón por símbolo** (el nombre de cada uno " +
                            "aparece al pasar el ratón, o al mantener pulsado en táctil):"
                    ),
                    ManualBlock.Reference(
                        caption = "Paletas de símbolos",
                        headers = listOf("Familia", "Qué escribe"),
                        rows = listOf(
                            listOf(
                                "**Adornos**",
                                "*Acciaccatura* y apoyatura, como cifras **pequeñas** a la izquierda de la nota"
                            ),
                            listOf("**Ligaduras**", "De prolongación (`T`) y de expresión (`S`)"),
                            listOf(
                                "**Técnicas**",
                                "*Hammer-on*, *pull-off*, *slide*, estiramiento (*bend*) hacia arriba y " +
                                    "hacia abajo, pre-estiramiento, vibrato, trémolo, *tapping*, armónico, " +
                                    "cuerda apagada y nota fantasma"
                            ),
                            listOf("**Trémolos**", "Los trazos sobre la cifra, con su medida"),
                            listOf(
                                "**Articulaciones**",
                                "Acento `>`, *marcato* `^`, picado, *tenuto*, calderón…, que se " +
                                    "**combinan** sobre la cifra"
                            ),
                            listOf(
                                "**Dinámicas y reguladores**",
                                "Matices `pp`…`fff`, `sf`/`sfz`/`fp` y `cresc.`/`dim.`"
                            ),
                            listOf(
                                "**Digitación**",
                                "El **dedo de la mano izquierda** que pisa (`1`–`4`) y el **dedo o púa de " +
                                    "la mano derecha** que pulsa (`p`, `i`, `m`, `a`); se rotulan bajo la cifra"
                            ),
                            listOf(
                                "**Repeticiones y saltos**",
                                "Repetición del compás (`|:`, `:|`) con su número de veces, voltas, " +
                                    "**navegación** (Segno, Coda, Fine, *To Coda*) y **saltos** (*D.C.*, " +
                                    "*D.S.* y variantes)"
                            ),
                            listOf(
                                "**Barras, compás, tempo y texto**",
                                "Barras de compás, indicaciones de compás, `♩ = N` con su indicación " +
                                    "italiana y texto libre del compás"
                            )
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Los símbolos **se combinan** sobre la misma nota (puedes tener ligado y *slide* a " +
                            "la vez) y se quitan volviendo a pulsarlos. Algunos tienen **magnitud**: la " +
                            "**cantidad del estiramiento** (`1/4`, `1/2`, `3/4`, `1`, `2`) y el **alcance " +
                            "del apagado de palma** (cuántas columnas dura el `P.M.`, de 1 a 8). La " +
                            "**cuerda apagada** (`X`) suena como percusión: no aporta altura ni entra en " +
                            "la transposición."
                    ),
                    ManualBlock.Note(
                        "La paleta de **alteraciones** (sostenidos y bemoles) **no se ofrece**: en una " +
                            "tablatura numérica la altura ya está fijada por el traste y la afinación, así " +
                            "que un `♯` delante de la cifra sería redundante. Si una tablatura importada " +
                            "los trae, se siguen dibujando y guardando."
                    ),
                    ManualBlock.Paragraph(
                        "El **cromo** de encima de la partitura tiene dos menús etiquetados y dos flechas. " +
                            "**Compás** añade al final, inserta antes o después del cursor, duplica y " +
                            "elimina; marca el compás 1 como **anacrusa**; cambia la **indicación de " +
                            "compás** (de 2/4 a 8/4 y los compuestos 6/8, 7/8, 9/8 y 12/8) desde ese compás " +
                            "en adelante; declara un compás **irregular** (duración libre, para que " +
                            "«completar compases» no lo rellene); y elige la **subdivisión** del compás, " +
                            "que es la rejilla de trabajo y **no** toca las notas ya escritas. **Columna** " +
                            "añade una columna (un evento: un acorde o una nota) o quita la del cursor. " +
                            "**↶ ↷** deshacen y rehacen. Un **silencio** y una **barra de compás** son " +
                            "símbolos, no casillas: se insertan desde la barra de escritura y desde las " +
                            "paletas, y la barra se dibuja **una sola vez** entre compases consecutivos."
                    ),
                    ManualBlock.Paragraph(
                        "**Seleccionar, copiar y pegar.** El botón del cuadrado de la cabecera selecciona " +
                            "todos los compases; desde ahí, `Mayús` + clic en el margen de otro compás " +
                            "extiende el rango, y los compases seleccionados se **resaltan** en la rejilla. " +
                            "Para notas, `Ctrl` + clic **añade o quita** notas de la selección y `Mayús` + " +
                            "clic la **extiende** a lo largo del tiempo; en un ordenador también puedes " +
                            "**arrastrar un rectángulo** (*rubber band*) sobre la partitura. Con la " +
                            "selección hecha: **copiar** (`Ctrl+C`), **cortar** (`Ctrl+X`), **pegar** " +
                            "(`Ctrl+V`), **duplicar** (`Ctrl+D` o `R`) y **borrar** (`Supr`). Solo se borra " +
                            "lo seleccionado, nunca la columna entera."
                    ),
                    ManualBlock.Paragraph(
                        "**Avisos: la rejilla avisa, no corrige.** Canticora **no te impide** escribir una " +
                            "posición rara —hay música que las usa—, pero la marca en **rojo** y dice qué " +
                            "pasa:"
                    ),
                    ManualBlock.Reference(
                        caption = "Avisos de la rejilla",
                        headers = listOf("Aviso", "Qué detecta"),
                        rows = listOf(
                            listOf(
                                "Cuerda repetida",
                                "Dos notas en la misma cuerda de la misma columna: en un instrumento de " +
                                    "cuerdas pulsadas cada cuerda suena **una** vez por ataque"
                            ),
                            listOf(
                                "Cuerda fuera de rango",
                                "La 7.ª cuerda de una guitarra de 6: la afinación no la tiene"
                            ),
                            listOf("Traste fuera del mástil", "Fuera de `0`–`24`"),
                            listOf(
                                "Palmo imposible",
                                "Más de **cuatro trastes** entre la nota más grave y la más aguda del acorde"
                            ),
                            listOf(
                                "Cuerda apagada con traste",
                                "Una `X` con una cifra distinta de `0`: la cuerda apagada suena como " +
                                    "percusión, sin altura"
                            )
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Las cuerdas **apagadas** y las notas **al aire** no cuentan para el palmo: una " +
                            "`X` no se pisa y un traste 0 tampoco."
                    ),
                    ManualBlock.Paragraph(
                        "El menú **Pentagrama** abre dos diálogos que en MuseScore están en el clic " +
                            "derecho sobre el pentagrama. **Propiedades del pentagrama**: cifras con " +
                            "**números** o **letras** (`a`–`k`), colocación de la cifra (sobre la línea o " +
                            "encima), líneas **continuas** o **cortadas** en cada cifra, silencios, plica " +
                            "hacia abajo, clave **TAB**, indicación de compás, cabecera en **todos** los " +
                            "sistemas, nombres de cuerda, números de compás, cómo se recuerda la cifra de " +
                            "una ligadura y el **tipo de pentagrama** (*Simple*: solo cifras; *Common*: " +
                            "cifras, silencios y plicas; *Full*: la blanca rayada). **Datos de cuerda**: la " +
                            "afinación de cada cuerda, su altura y la marca «siempre al aire» (una cuerda " +
                            "pedal que solo admite el traste 0)."
                    ),
                    ManualBlock.Paragraph(
                        "**Reproducir** (o `Espacio`) toca la tablatura respetando el **tempo** de cada " +
                            "compás, las **repeticiones**, las **casillas**, los **saltos** y el tempo " +
                            "escrito; mientras suena, el cursor sigue la columna que va sonando. En la " +
                            "propia barra de escritura está el **mezclador**: volumen, balance, silencio y " +
                            "solo, que se recuerdan entre sesiones porque el volumen del atril depende del " +
                            "sitio, no del canto."
                    ),
                    ManualBlock.Paragraph(
                        "El **metrónomo** da un clic por **pulsación** del compás —la del denominador: la " +
                            "negra en 4/4, la corchea en 6/8— y **acentúa** la primera (y en los compases " +
                            "compuestos también la mitad, donde empieza el segundo grupo de tres). Su " +
                            "configuración vive en **Metrónomo**, en el panel de herramientas:"
                    ),
                    ManualBlock.Reference(
                        caption = "Ajustes del metrónomo",
                        headers = listOf("Ajuste", "Qué hace"),
                        rows = listOf(
                            listOf(
                                "Suena al reproducir",
                                "Enciende y apaga el clic; es el mismo estado que el botón de la barra"
                            ),
                            listOf(
                                "Volumen del clic",
                                "Volumen **propio**, independiente del de la tablatura (0–100 %)"
                            ),
                            listOf("Acentuar el primer tiempo", "Apagado deja un pulso uniforme"),
                            listOf(
                                "Subdivisión",
                                "Un clic por pulsación, o corcheas, tresillos o semicorcheas"
                            )
                        )
                    ),
                    ManualBlock.Paragraph(
                        "La **barra de estado** tiene el desplegable de **zoom** (del 35 % al 200 %) y " +
                            "**Ajustar al ancho**. En un ordenador, `Ctrl` + rueda acerca y aleja; en " +
                            "tableta, el pellizco. El zoom **no** cambia la tablatura: solo cómo se ve."
                    ),
                    ManualBlock.Paragraph(
                        "La tablatura se **imprime** con el canto cuando el modo de vista la incluye " +
                            "(**Letra y tablatura**, **Letra, acordes y tablatura** o **Solo tablatura**). " +
                            "El PDF dibuja los mismos sistemas que la pantalla, con su clave, su compás, " +
                            "sus cifras y sus figuras rítmicas; si el canto no cabe en una página a una " +
                            "columna, pasa a **dos columnas** antes que partir la música, y respeta los " +
                            "**saltos de página** que hayas marcado."
                    ),
                    ManualBlock.Paragraph(
                        "Al **transportar** el canto, la tablatura puede seguirla de tres maneras, " +
                            "elegidas en **Al transportar** (panel de herramientas): **cejilla** (los " +
                            "números no cambian; sube la cejilla), **redigitado** (recalcula cuerdas y " +
                            "trastes) o **bloqueada** (la tablatura se queda como está)."
                    ),
                    ManualBlock.Note(
                        "Si escribiste la tablatura **a mano**, Canticora **nunca cambia tus posiciones sin " +
                            "avisarte**: te lo preguntará antes."
                    ),
                    ManualBlock.Paragraph(
                        "Puedes tener **varias tablaturas** por canto y **asignar cada una a una sección** " +
                            "(intro, estrofa, coro…): aparecerá **intercalada junto a su parte**. Las que " +
                            "dejes en «General» se muestran al final. En el **listado de canciones** verás " +
                            "una **púa** junto al título cuando el canto tenga tablatura, y un **clip** " +
                            "cuando tenga adjuntos."
                    ),
                    ManualBlock.Note(
                        "Las tablaturas también se editan desde el propio **editor de la canción**: en la " +
                            "pestaña **Tablaturas**, junto a **Editor** y **Vista Previa** (el canto debe " +
                            "estar guardado)."
                    ),
                    ManualBlock.Paragraph(
                        "**Ayuda dentro del editor.** El botón **?** del cromo abre la **ayuda " +
                            "contextual**: dice en qué compás, casilla, cuerda y traste estás y qué puedes " +
                            "hacer desde ahí (escribir, marcar, mover compases, escuchar, guardar). En la " +
                            "cabecera de las paletas, **?/Nombres** escribe el **nombre de cada símbolo** " +
                            "debajo de su celda, y el botón de ayuda de al lado explica las familias. Al " +
                            "pasar el ratón por un símbolo sale su nombre; en táctil, **mantén pulsado**. " +
                            "Si una casilla está vacía, la paleta lo dice en vez de dejar los botones " +
                            "muertos, y la barra de estado describe el elemento del cursor con el " +
                            "vocabulario de MuseScore."
                    )
                )
            ),
            ManualSection(
                id = "lectura/importar-tablatura",
                title = "Importar una partitura de otro programa",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Si la pieza ya está escrita en **MuseScore**, **Guitar Pro** o **TuxGuitar**, no " +
                            "hace falta copiarla a mano: Canticora la lee del archivo."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Entra en el canto y abre **Tablaturas**.",
                            "Pulsa el icono de **importar** de la barra superior y elige el archivo.",
                            "Canticora lo lee y **sustituye** la tablatura del canto. Un aviso te dice de " +
                                "qué formato viene y cuántos compases y notas ha leído."
                        )
                    ),
                    ManualBlock.Reference(
                        caption = "Formatos que entiende",
                        headers = listOf("Programa", "Extensión", "¿Se puede importar?"),
                        rows = listOf(
                            listOf("MuseScore 3 y 4", "`.mscz`", "Sí"),
                            listOf("Guitar Pro 7 y 8", "`.gp`, `.gpx`", "Sí"),
                            listOf("TuxGuitar", "`.tg`", "Sí"),
                            listOf("MusicXML (Finale, Sibelius, Dorico, Guitar Pro…)", "`.musicxml`, `.xml`, `.mxl`", "Sí"),
                            listOf("Guitar Pro 3, 4 y 5", "`.gp3`, `.gp4`, `.gp5`", "No")
                        )
                    ),
                    ManualBlock.Note(
                        "**Guitar Pro 3, 4 y 5** guarda en un formato binario propio que Canticora no lee. " +
                            "Ábrelo en Guitar Pro, en MuseScore o en TuxGuitar y **guárdalo como MusicXML o " +
                            "como `.gpx`**: se importa sin perder nada importante. Si eliges un `.gp3`, la " +
                            "app te lo explica en vez de dar un error."
                    ),
                    ManualBlock.Paragraph(
                        "**No hace falta que el archivo use solo lo que Canticora dibuja.** Los adornos, los " +
                            "matices de dinámica, los textos de navegación, los ornamentos o los efectos que " +
                            "Canticora no tiene se **descartan sin avisar y sin romper nada**: se importa " +
                            "toda la música que sí entiende. Nunca verás un error por un símbolo raro."
                    ),
                    ManualBlock.Reference(
                        caption = "Lo que se importa",
                        rows = listOf(
                            listOf(
                                "La **afinación** del instrumento (guitarra estándar, *drop D*, bajos, " +
                                    "ukelele…), tomada del propio archivo si la declara."
                            ),
                            listOf(
                                "El **compás**, el **tempo** y las **repeticiones** (`𝄆 𝄇`, «bis», «×N»)."
                            ),
                            listOf("Las **figuras** y los **silencios**, incluidos los **tresillos**."),
                            listOf(
                                "Las **técnicas** de mano izquierda y derecha: ligados, *hammer*, " +
                                    "*pull-off*, cejilla, vibrato, *bend*, armónicos, palm mute, trémolo, " +
                                    "*tapping*…"
                            ),
                            listOf(
                                "Los **matices** (`p`, `mf`, `f`), los **reguladores** y las **barras de " +
                                    "compás** especiales."
                            )
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Si el archivo trae un **título**, se usa ese; si no, el que tuviera la tablatura."
                    )
                )
            ),
            ManualSection(
                id = "lectura/compartir-tablatura",
                title = "Compartir una tablatura",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En la lista de tablaturas, cada tarjeta tiene un botón de **compartir**. Canticora " +
                            "escribe la tablatura en **MusicXML** —el formato que abren MuseScore, Finale, " +
                            "Sibelius, Dorico y Guitar Pro— y abre la hoja de compartir del sistema: " +
                            "correo, mensajería, Drive, o guardarla en el dispositivo."
                    ),
                    ManualBlock.Note(
                        "Al compartir un **canto** o un **repertorio**, o al hacer una **copia de " +
                            "seguridad**, las tablaturas **van incluidas**. No hay que compartirlas aparte."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 5 · Transposición
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterTransposicion() = ManualChapter(
        id = "transposicion",
        title = "5. Transponer y el capo",
        icon = ManualChapterIcon.TRANSPOSE,
        sections = listOf(
            ManualSection(
                id = "transposicion/transportar",
                title = "Cambiar de tonalidad",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Pulsa **♯** para subir medio tono o **♭** para bajarlo. Los acordes y la tonalidad " +
                            "se recalculan al instante, **sin modificar el texto original** del canto: la " +
                            "transposición es temporal."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Transporta las veces que necesites (sube o baja semitonos).",
                            "El panel resumen muestra la tonalidad actual, la original y los semitonos aplicados.",
                            "Usa **↻** para volver a la tonalidad original en cualquier momento."
                        )
                    ),
                    ManualBlock.Note(
                        "Al cambiar de canto o salir de la pantalla, la transposición se pierde. Si quieres " +
                            "una tonalidad **fija** para una celebración concreta, usa la tonalidad del " +
                            "repertorio (ver capítulo 7)."
                    )
                )
            ),
            ManualSection(
                id = "transposicion/notacion",
                title = "Nomenclatura: Do-Re-Mi o C-D-E",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Puedes leer los acordes en **notación latina** (Do, Re, Mi, Fa, Sol, La, Si) o " +
                            "**anglosajona** (C, D, E, F, G, A, B). El cambio afecta solo a cómo se muestran; " +
                            "la música no cambia."
                    ),
                    ManualBlock.Paragraph(
                        "La nomenclatura es una **preferencia de toda la aplicación**: se cambia en el visor, " +
                            "en el **editor de la canción** (justo debajo de *Categorías y etiquetas*) o en el " +
                            "modo celebración, y se conserva en todos ellos y al reiniciar la app, hasta que " +
                            "vuelvas a cambiarla. La tonalidad se muestra siempre en la nomenclatura elegida."
                    ),
                    ManualBlock.Paragraph(
                        "Al cambiarla, el **texto del editor se convierte** al nuevo sistema y el " +
                            "**constructor rápido de acordes** muestra sus botones en la notación activa " +
                            "(Do-Re-Mi o C-D-E), igual que los acordes frecuentes."
                    ),
                    ManualBlock.Note(
                        "Las alteraciones se escriben en ambos casos con sostenido (♯) o bemol (♭)."
                    )
                )
            ),
            ManualSection(
                id = "transposicion/capo",
                title = "El capo o cejilla",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El **capo** permite tocar en una posición cómoda de la guitarra y que suene en la " +
                            "tonalidad real. Un canto puede guardar su capo habitual, que aparece al abrirlo."
                    ),
                    ManualBlock.Note(
                        "Cambiar la tonalidad en pantalla **no** recoloca el capo automáticamente: el capo " +
                            "es un dato del canto y se edita en el editor."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 6 · Tablaturas
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterTablaturas() = ManualChapter(
        id = "tablaturas",
        title = "6. Tablaturas",
        icon = ManualChapterIcon.TABLATURE,
        sections = listOf(
            ManualSection(
                id = "tablaturas/que-son",
                title = "Qué es una tablatura y cuándo usarla",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Una **tablatura** es la digitación de una pieza escrita cuerda a cuerda: en vez de " +
                            "notas en un pentagrama, cada cuerda tiene su línea y en ella se escribe el " +
                            "**traste** que se pisa. Es la forma en que un guitarrista lee de verdad."
                    ),
                    ManualBlock.Paragraph(
                        "En Canticora la tablatura no es una imagen ni un PDF: es **música estructurada**, " +
                            "igual que la letra con acordes. Por eso se puede **transponer**, **imprimir** en " +
                            "el PDF del canto, **compartir** como MusicXML y **editar** nota a nota."
                    ),
                    ManualBlock.Reference(
                        caption = "Instrumentos que admite",
                        headers = listOf("Instrumento", "Cuerdas", "Notas"),
                        rows = listOf(
                            listOf("Guitarra española", "6", "Mi grave → Mi agudo"),
                            listOf("Guitarra en drop D", "6", "El Mi grave baja a Re"),
                            listOf("Bajo", "4, 5 o 6", "Afinación estándar de cada uno"),
                            listOf("Ukelele", "4", "Sol–Do–Mi–La"),
                            listOf("Laúd / bandurria", "6", "Cuerdas dobles (cursos)")
                        )
                    ),
                    ManualBlock.Note(
                        "La cuerda **más aguda** se dibuja **arriba**, como en cualquier tablatura impresa. " +
                            "El número de cuerda 1 es siempre la más aguda."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/crear",
                title = "Crear una tablatura a mano",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Abre el canto y toca la tarjeta **Tablaturas**, junto a los adjuntos.",
                            "Pulsa **Nueva tablatura** y elige el **instrumento**.",
                            "Elige el **compás** (4/4, 3/4, 6/8…) y la **subdivisión** (negras, corcheas o " +
                                "semicorcheas): es el tamaño de la rejilla de trabajo.",
                            "Toca una casilla de la rejilla para poner el cursor y **teclea el traste**. " +
                                "El cursor avanza solo a la siguiente columna.",
                            "Guarda con **✓**."
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Puedes tener **varias tablaturas** por canto (guitarra y bajo, por ejemplo) y " +
                            "asignar cada una a una **sección** (intro, estrofa, estribillo): se imprime junto " +
                            "a su parte. Las tablaturas sin sección van al final."
                    ),
                    ManualBlock.Note(
                        "Escribir un traste de **dos cifras** (12, 17…) se hace tecleando las dos cifras " +
                            "seguidas: la app espera unos instantes antes de avanzar. Con el teclado en " +
                            "pantalla funciona igual."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/barra",
                title = "La barra de escritura",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Pegada a la partitura —no se va con el scroll— hay una barra con todo lo que hace " +
                            "falta para escribir sin abrir ningún panel."
                    ),
                    ManualBlock.Reference(
                        caption = "Botones de la barra de escritura",
                        headers = listOf("Botón", "Qué hace"),
                        rows = listOf(
                            listOf("▶ Reproducir", "Escucha la tablatura, con tempo, repeticiones y saltos."),
                            listOf("♩ Metrónomo", "Enciende el clic del compás (se configura en el panel)."),
                            listOf("Figuras", "Fijan la **duración** de lo que escribas: redonda a semicorchea."),
                            listOf("Puntillo / Tresillo", "Añaden el puntillo o el grupo irregular a la figura."),
                            listOf("Silencios", "Insertan una pausa de la figura elegida."),
                            listOf("Entrada de notas", "Con el lápiz encendido (o la tecla `N`), tocar una cuerda escribe un 0."),
                            listOf("Teclado", "Solo en táctil: abre el teclado numérico para escribir el traste."),
                            listOf("+ Símbolos", "Abre los adornos de la nota del cursor sin salir del modo de entrada.")
                        )
                    ),
                    ManualBlock.Note(
                        "Con el modo de entrada apagado, tocar la rejilla solo **mueve el cursor**. Aun así, " +
                            "**teclear una cifra entra en el modo y escribe**: no hace falta activarlo en cada " +
                            "nota."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/atajos",
                title = "Atajos de teclado",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Con un teclado físico (ordenador, tablet con teclado) se escribe mucho más rápido:"
                    ),
                    ManualBlock.Reference(
                        caption = "Atajos del editor de tablatura",
                        headers = listOf("Tecla", "Acción"),
                        rows = listOf(
                            listOf("0–9", "Escriben el traste en la cuerda y casilla del cursor."),
                            listOf("A–K", "Escriben trastes del 10 al 19 (sin la `I`)."),
                            listOf("← / →", "Mueven el cursor por la rejilla, compás a compás."),
                            listOf("↑ / ↓", "Cambian de cuerda."),
                            listOf("Espacio", "Reproduce o para la tablatura."),
                            listOf("Intro", "Abre un **sistema** (salto de línea) nuevo."),
                            listOf("Ctrl+Intro", "Abre una **página** nueva."),
                            listOf("Mayús+Intro", "Inserta una barra de compás."),
                            listOf("Supr", "Borra la nota del cursor."),
                            listOf("Retroceso", "Corrige lo último tecleado y retrocede."),
                            listOf("Q / W", "Alargan o acortan la figura del cursor."),
                            listOf("Ctrl+Z / Ctrl+Y", "Deshacen y rehacen."),
                            listOf("Ctrl+C / X / V", "Copian, cortan y pegan compases o notas.")
                        )
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/marcas",
                title = "Marcas, técnicas y adornos",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Con una cifra elegida, el **panel de herramientas** ofrece las paletas: expresión " +
                            "(trémolo, vibrato, estiramiento, slide), ligados, mano derecha, duración, " +
                            "armónicos, palanca, alteraciones, articulaciones, dinámicas, digitación, " +
                            "navegación, saltos, tempo y texto libre."
                    ),
                    ManualBlock.Paragraph(
                        "Los símbolos **se combinan** sobre la misma nota y se quitan volviendo a pulsarlos. " +
                            "También puedes **arrastrar** un símbolo desde la paleta hasta una nota de la " +
                            "rejilla: se aplica a la que hay debajo del dedo."
                    ),
                    ManualBlock.Note(
                        "En táctil, el interruptor **? / Nombres** de la cabecera escribe el nombre de cada " +
                            "símbolo debajo de su celda: es la única forma de saber qué es un glifo antes de " +
                            "aprenderlo."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/estructura",
                title = "Compases, columnas y saltos de página",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El menú **Compás** del cromo edita la estructura sin salir de la partitura:"
                    ),
                    ManualBlock.Reference(
                        caption = "Operaciones de compás",
                        headers = listOf("Acción", "Qué hace"),
                        rows = listOf(
                            listOf("Añadir al final", "Crea un compás nuevo al final de la pieza."),
                            listOf("Insertar antes / después", "Mete un compás en medio, en el punto del cursor."),
                            listOf("Duplicar", "Copia el compás actual justo detrás."),
                            listOf("Eliminar", "Quita el compás actual."),
                            listOf("Anacrusa", "Marca el primer compás como incompleto (el «y» de entrada)."),
                            listOf("Indicación de compás", "Cambia el compás **desde** este punto (un 3/4 dentro de un 4/4)."),
                            listOf("Compás irregular", "Deja que el compás dure algo distinto a su medida nominal."),
                            listOf("Subdivisión", "Cuántas casillas tiene la rejilla: negras, corcheas o semicorcheas.")
                        )
                    ),
                    ManualBlock.Note(
                        "El menú **Columna** añade o quita la columna del cursor. Si una columna tiene dos " +
                            "notas en la misma cuerda, la rejilla **avisa** en rojo, pero no impide seguir " +
                            "escribiendo."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/pentagrama",
                title = "Propiedades del pentagrama y datos de cuerda",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El menú **Pentagrama** abre los ajustes de cómo se ve la tablatura: cifras con " +
                            "**números o letras**, dónde se coloca la cifra, líneas **continuas o cortadas**, " +
                            "silencios, plica, clave `TAB`, indicación de compás, cabecera en todos los " +
                            "sistemas, nombres de cuerda y números de compás."
                    ),
                    ManualBlock.Paragraph(
                        "También ofrece los tres **tipos de pentagrama** de referencia —Simple (solo " +
                            "cifras), Common (cifras, silencios y plicas) y Full (blanca rayada)— y los **datos " +
                            "de cuerda**: afinación de cada cuerda y número de cuerdas del instrumento."
                    ),
                    ManualBlock.Note(
                        "Estos ajustes son una **preferencia global**: lo que elijas se aplica también al " +
                            "visor, a la vista previa y al **PDF**, y se recuerda entre sesiones."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/leer",
                title = "Leer y escuchar",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Fuera del editor, la tablatura se ve en el **visor del canto** y en el **modo " +
                            "celebración**, con la letra y los acordes. El modo de vista decide si se muestra " +
                            "y dónde: puedes ponerla **después** de la letra o **antes**, o no mostrarla."
                    ),
                    ManualBlock.Paragraph(
                        "El **zoom** de la barra de estado (35 % a 200 %) y **Ajustar al ancho** cambian solo " +
                            "cómo se ve, nunca la música. En un ordenador, `Ctrl`+rueda acerca y aleja; en " +
                            "tableta, el pellizco. El tamaño se recuerda para la próxima vez."
                    ),
                    ManualBlock.Note(
                        "La reproducción respeta el **tempo**, las repeticiones, las casillas y los saltos " +
                            "(D.C., Fine, Coda), y el **metrónomo** acentúa la primera pulsación de cada compás."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/transponer",
                title = "Transponer la tablatura",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Al cambiar la tonalidad del canto, la tablatura se recoloca **sola**: las mismas " +
                            "alturas se buscan en otra posición del mástil. Las notas al aire y los trastes " +
                            "bajos se usan siempre que se puede, para que la digitación siga siendo cómoda."
                    ),
                    ManualBlock.Reference(
                        caption = "Modos de transposición",
                        headers = listOf("Modo", "Qué hace"),
                        rows = listOf(
                            listOf("Con el canto", "La tablatura suena en la tonalidad que estés viendo."),
                            listOf("Tonalidad original", "Se queda en la tonalidad en que se escribió, pase lo que pase."),
                            listOf("Cejilla (capo)", "Se calcula el capo necesario para tocar lo mismo con formas abiertas.")
                        )
                    ),
                    ManualBlock.Note(
                        "Redigitar para otro instrumento: desde el listado de tablaturas puedes crear una " +
                            "versión equivalente para laúd o bajo a partir de las **mismas alturas**. La " +
                            "original se conserva."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/importar",
                title = "Importar una partitura de otro programa",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Si ya tienes la pieza escrita en otro programa, no hace falta volver a copiarla: el " +
                            "botón **Importar desde un archivo** de la lista de tablaturas la convierte en una " +
                            "tablatura de Canticora."
                    ),
                    ManualBlock.Reference(
                        caption = "Formatos que se pueden importar",
                        headers = listOf("Formato", "Extensiones"),
                        rows = listOf(
                            listOf("MusicXML", "`.musicxml`, `.xml`, `.mxl`"),
                            listOf("MuseScore", "`.mscz`"),
                            listOf("Guitar Pro 7 y 8", "`.gp`, `.gpx`"),
                            listOf("TuxGuitar", "`.tg`")
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Al importar se reconocen el **instrumento**, la **afinación** de cada cuerda, el " +
                            "**compás**, las **figuras**, los **silencios**, las **ligaduras** y las " +
                            "**técnicas** (trémolo, armónicos, bends, vibrato, apagado de palma…). Si la " +
                            "afinación de la partitura coincide con una de fábrica, se usa esa; si no, se " +
                            "conserva la propia de la partitura."
                    ),
                    ManualBlock.Note(
                        "La tablatura importada **sustituye** a la que hubiera en el canto. Si quieres " +
                            "conservar las dos, importa primero una, duplícala y luego importa la otra."
                    )
                )
            ),
            ManualSection(
                id = "tablaturas/pdf",
                title = "La tablatura en el PDF y al compartir",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Al exportar el canto a **PDF**, la tablatura se imprime **arriba y a todo el ancho** " +
                            "de la hoja, y la letra con los acordes va debajo, a una o dos columnas según lo " +
                            "que ahorre papel. El algoritmo elige lo que ocupa menos hojas sin partir ninguna " +
                            "sección por la mitad."
                    ),
                    ManualBlock.Paragraph(
                        "El botón **Compartir** del listado de tablaturas genera un archivo **MusicXML**, que " +
                            "abren MuseScore, Guitar Pro y TuxGuitar. Así la tablatura sigue siendo editable " +
                            "fuera de Canticora y no queda encerrada en la app."
                    ),
                    ManualBlock.Note(
                        "En el PDF la tablatura **nunca** va a dos columnas: a media anchura las cifras no se " +
                            "leen. Lo que va a dos columnas es el texto."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 7 · Patrones rítmicos
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterRitmos() = ManualChapter(
        id = "ritmos",
        title = "7. Patrones rítmicos",
        icon = ManualChapterIcon.RHYTHM,
        sections = listOf(
            ManualSection(
                id = "ritmos/que-son",
                title = "Qué es un patrón rítmico",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Un patrón rítmico describe **cómo se rasguea** el canto: qué figura (negra, " +
                            "corchea…) suena en cada momento, en qué **dirección** (abajo ↓, arriba ↑ o " +
                            "apagado ×) y con qué **cuerdas** (todas, solo graves o solo agudos). El mismo " +
                            "patrón se puede ver de cuatro formas distintas."
                    ),
                    ManualBlock.Reference(
                        caption = "Representaciones del mismo patrón",
                        headers = listOf("Vista", "Qué muestra"),
                        rows = listOf(
                            listOf("↓ Flechas", "Dirección de cada rasgueo."),
                            listOf("♪ Figuras", "La duración musical de cada golpe."),
                            listOf("♪↓ Combinada", "Figura y dirección a la vez."),
                            listOf("⇅ ChordPro", "Símbolos oficiales de ChordPro.")
                        )
                    ),
                    ManualBlock.Note(
                        "Las barras de compás (|) **no se guardan**: la app las deduce del compás elegido " +
                            "(4/4, 6/8, 3/4…)."
                    )
                )
            ),
            ManualSection(
                id = "ritmos/rejilla",
                title = "Crear un ritmo con la rejilla (fácil)",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Al crear o editar un patrón, lo primero que ves es la **rejilla**. Cada columna es " +
                            "una subdivisión del compás y la fila de arriba marca el tiempo (**1 & 2 & 3 & 4 &**), " +
                            "como en el cancionero. Se escribe en **dos pasos**."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "**Elige el trazo** en la paleta: ↓ abajo, ↑ arriba, × apagado, las cuatro flechas " +
                                "**cortas** (↓ graves, ↓ agudos, ↑ agudos, ↑ graves), · aire (silencio) o – prolongar.",
                            "**Toca las casillas** donde suena ese trazo. Mantén pulsado para dejar la casilla " +
                                "en aire.",
                            "Ajusta la **subdivisión** con los botones ♩ Negras, ♪ Corcheas o 𝅘𝅥𝅯 Semicorcheas.",
                            "Pulsa **Auto ↓ ↑** para que la app reparta las direcciones con la regla del péndulo.",
                            "Escucha el resultado con el **reproductor** y, si quieres afinar más, cambia al " +
                                "modo **Avanzado**. Ponle nombre y guarda con el botón **Guardar** de la barra inferior."
                        )
                    ),
                    ManualBlock.Reference(
                        caption = "Qué significa cada símbolo de la rejilla",
                        headers = listOf("Símbolo", "Significa"),
                        rows = listOf(
                            listOf("↓ / ↑ / ×", "Golpe hacia abajo, hacia arriba o apagado."),
                            listOf("Flecha larga", "Barre **todas** las cuerdas."),
                            listOf("Flecha corta", "Solo un registro: **graves** o **agudos**."),
                            listOf("Corta de graves", "Flecha corta dibujada en la parte **alta** del símbolo (las graves van arriba)."),
                            listOf("Corta de agudos", "Flecha corta dibujada en la parte **baja** del símbolo (las agudas van abajo)."),
                            listOf("Corta hacia arriba", "Flecha corta con la punta **hacia arriba** (p. ej. ↑ sobre las cuerdas agudas)."),
                            listOf("– (guion)", "**Prolonga** el golpe anterior (nota más larga)."),
                            listOf("· (punto)", "**Aire**: silencio en esa subdivisión.")
                        )
                    ),
                    ManualBlock.Note(
                        "En las flechas cortas, la **banda** del símbolo indica el registro, con la caja " +
                            "orientada como se ve la guitarra al tocar: **arriba** las cuerdas graves y " +
                            "**abajo** las agudas. La **punta** de la flecha indica la dirección del golpe."
                    ),
                    ManualBlock.Note(
                        "La rejilla y el modo avanzado **son el mismo ritmo**: lo que toques en uno aparece " +
                            "al instante en el otro. No son dos patrones distintos. Tocar una casilla **solo** " +
                            "cambia esa posición: las figuras, los acentos y los registros de las demás se " +
                            "conservan tal cual. La paleta elige el **trazo**, no la articulación: escribir " +
                            "flechas en la rejilla nunca borra los acentos puestos en el modo avanzado."
                    ),
                    ManualBlock.Note(
                        "**Prolongar** (–) significa «esta casilla continúa el golpe de la izquierda». Por eso " +
                            "en la **primera** casilla no hace nada: no hay ningún golpe que prolongar."
                    )
                )
            ),
            ManualSection(
                id = "ritmos/compases",
                title = "Ritmos de varios compases",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Un patrón puede ocupar **más de un compás** (por ejemplo, un ritmo de dos compases " +
                            "que se repite en bucle). Usa el control **Compases: [−] N [+]**, encima de la rejilla."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "**[+]** añade un compás al final, relleno de silencios.",
                            "**[−]** quita el último compás."
                        )
                    ),
                    ManualBlock.Note(
                        "En la rejilla, cada compás se separa con una **barra doble** y la numeración de pulsos " +
                            "se reinicia (1 2 3 4 ‖ 1 2 3 4), para que se lean los compases de un vistazo."
                    ),
                    ManualBlock.Note(
                        "Si intentas añadir una figura que no cabe en el compás actual, la app te avisa y te " +
                            "propone **añadir otro compás** en lugar de bloquearse."
                    )
                )
            ),
            ManualSection(
                id = "ritmos/crear",
                title = "Afinar el patrón (modo avanzado)",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El botón **Avanzado** abre el editor por **posiciones**, para afinar detalles: " +
                            "seleccionar cada golpe, cambiar su figura, su dirección, su **acento** (también " +
                            "arpegio y staccato) y su **registro de cuerdas** (flecha larga o corta)."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Abre **Patrones Rítmicos** desde el menú o la barra inferior.",
                            "Elige el **compás** y la representación (flechas, figuras, combinada o ChordPro).",
                            "Pulsa **+** para crear un patrón nuevo, o **Editar** en uno integrado para " +
                                "modificarlo.",
                            "Cambia a **Avanzado** y toca una posición de la tira de arriba para **seleccionarla**.",
                            "En el **inspector** de abajo elige su **figura**, su **golpe** (↓ / ↑ / ×), sus " +
                                "**cuerdas** (todas / graves / agudos) y su **articulación** (normal, acento, " +
                                "arpegio, staccato y combinaciones). Con **Eliminar** quitas la posición.",
                            "Añade más figuras o silencios con las paletas de abajo: la dirección del golpe " +
                                "nuevo se calcula sola (↓ ↑ alternos; un silencio reinicia a ↓).",
                            "Ponle un nombre (p. ej. «Balada suave 4/4») y guarda con el botón **Guardar** de la " +
                                "barra inferior. A su lado tienes **Compartir**, **Copiar texto**, **Duplicar** " +
                                "y, si el patrón ya estaba guardado, **Eliminar**."
                        )
                    ),
                    ManualBlock.Note(
                        "**Editar** un patrón integrado lo **modifica en su sitio**: al guardar, tu versión " +
                            "sustituye a la de fábrica, así que no acabas con dos ritmos que se llaman igual. " +
                            "Si quieres conservar el original, usa **Duplicar**, que abre una copia llamada " +
                            "«… (copia)»."
                    ),
                    ManualBlock.Note(
                        "Tanto los patrones propios como los **integrados de fábrica** se pueden eliminar: al " +
                            "borrar uno, aparece un aviso con **Deshacer** para recuperarlo. Al borrar un patrón " +
                            "propio, los cantos que lo usaban se quedan sin patrón, no se rompen."
                    )
                )
            ),
            ManualSection(
                id = "ritmos/escuchar",
                title = "Escuchar el ritmo antes de guardarlo",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El editor incluye un pequeño **reproductor**: un selector de **BPM** (por defecto 80) y " +
                            "un botón **▶ / ■** para escuchar el patrón en bucle mientras lo construyes. El golpe " +
                            "que suena se **resalta** en la notación, para que veas la correspondencia entre el " +
                            "sonido y el dibujo."
                    ),
                    ManualBlock.Note(
                        "El reproductor está **siempre en el mismo sitio**: justo debajo de la " +
                            "representación del patrón, tanto en el modo **Rejilla** como en **Avanzado**. " +
                            "No cambia de lugar al alternar de modo."
                    ),
                    ManualBlock.Note(
                        "El sonido es **sintético** (no es una guitarra grabada): es una ayuda para comprobar el " +
                            "ritmo, no una pista de acompañamiento. Cambiar el BPM mientras suena reinicia el " +
                            "tempo al instante."
                    )
                )
            ),
            ManualSection(
                id = "ritmos/compartir",
                title = "Compartir un ritmo con otro músico",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En la lista de **Patrones Rítmicos**, abre el menú de tres puntos (⋮) de un patrón. " +
                            "Puedes **compartirlo como archivo** (el otro lo abre con Canticora y lo tiene tal " +
                            "cual) o **copiarlo como texto**, para pegarlo en un mensaje o una nota. El mismo " +
                            "menú permite **Editar**, **Duplicar** y **Eliminar** el patrón. También puedes " +
                            "compartir **todos tus ritmos a la vez** desde el menú **⋮** de la esquina superior."
                    ),
                    ManualBlock.Paragraph(
                        "El texto copiado es compacto y legible: lleva el nombre, el compás y los golpes, así:"
                    ),
                    ManualBlock.Note(
                        "Canticora · Ritmo «Balada suave»\n" +
                            "Compás 4/4\n" +
                            "↓ ↑ – ↓ | ↓ ↑ · ↑\n\n" +
                            "En patrones de **varios compases** se añade una línea `Compases N` (por ejemplo " +
                            "`Compases 2`) y la línea de golpes lleva los dos compases separados por `|`."
                    ),
                    ManualBlock.Note(
                        "Para **traer** ritmos de otra persona, abre su archivo con Canticora (o compártelo " +
                            "contigo) y confirma en el diálogo. También puedes pegarlo desde la pantalla " +
                            "**Importar**, en la vía «Ritmos de rasgueo»."
                    ),
                    ManualBlock.Note(
                        "Tus ritmos **también van dentro de la copia de seguridad**: al restaurarla en un móvil " +
                            "nuevo, recuperas tus patrones además de tus canciones."
                    )
                )
            ),
            ManualSection(
                id = "ritmos/asignar",
                title = "Asignar el patrón a un canto",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En el **editor de la canción** elige un patrón en «Patrón de rasgueo» y guarda. " +
                            "Después aparecerá al leer el canto, según la representación que hayas elegido."
                    ),
                    ManualBlock.Note(
                        "Primero hay que elegir el **compás** del canto: el selector de patrón solo muestra " +
                            "los patrones de ese compás. Si aún no has elegido compás, el editor te lo indica; " +
                            "si no hay patrones guardados para ese compás, créalos en **Patrones Rítmicos**."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 7 · Misas y eventos
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterMisas() = ManualChapter(
        id = "misas",
        title = "8. Misas, eventos y Modo Celebración",
        icon = ManualChapterIcon.CELEBRATION,
        sections = listOf(
            ManualSection(
                id = "misas/crear",
                title = "Crear una misa o evento",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Abre **Misas y Eventos** y pulsa **+**.",
                            "Elige el **tipo** (Misa, Hora Santa, Retiro, Vigilia, Concierto/Ensayo o Evento general). " +
                                "El tipo cambia las etiquetas para que el formulario tenga sentido.",
                            "Escribe el nombre (por ejemplo «Misa Dominical — III Cuaresma»).",
                            "Añade descripción, lugar y fecha si quieres.",
                            "Guarda con el botón **Guardar** de la barra inferior (a su lado tienes " +
                                "**Compartir**, **PDF** y, si el repertorio ya existía, **Eliminar**)."
                        )
                    ),
                    ManualBlock.Note(
                        "En eventos **no litúrgicos** (ensayos, conciertos) el momento de cada canto es " +
                            "opcional; puede quedar como «Sin asignar»."
                    )
                )
            ),
            ManualSection(
                id = "misas/anadir",
                title = "Añadir cantos a cada momento",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Dentro de la celebración, pulsa **Añadir canto**.",
                            "Busca el canto por texto, momento, tiempo o tonalidad en el buscador.",
                            "Al elegirlo, se te pide el **momento / sección** (Entrada, Ofertorio, Comunión…). " +
                                "Se sugiere el momento habitual del canto; puedes cambiarlo.",
                            "Repite con todos los cantos.",
                            "Para **cambiar el momento** de un canto ya añadido, toca la etiqueta del momento " +
                                "y elige otro."
                        )
                    ),
                    ManualBlock.Paragraph(
                        "En el desplegable, primero aparecen las **partes de la liturgia** (en su orden " +
                            "canónico) y, al final y separadas, sugerencias **adicionales no litúrgicas** " +
                            "(Ambientación, Alabanza, Adoración…)."
                    )
                )
            ),
            ManualSection(
                id = "misas/ordenar",
                title = "Ordenar los cantos",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Usa las flechas **↑** y **↓** de cada canto para subirlo o bajarlo en el orden del " +
                            "repertorio. El orden que dejes aquí es el que seguirá el Modo Celebración."
                    )
                )
            ),
            ManualSection(
                id = "misas/tonalidad",
                title = "Tonalidad propia del repertorio",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Cada canto dentro de una misa guarda su **tonalidad para esa celebración**. Así " +
                            "puedes tener el mismo canto en Do un domingo y en Re al siguiente, sin tocar la " +
                            "tonalidad original del cancionero."
                    ),
                    ManualBlock.Note(
                        "Al añadir una canción, se propone la tonalidad original. «Tono: …» en la tarjeta del " +
                            "repertorio indica la tonalidad vigente en esa misa."
                    )
                )
            ),
            ManualSection(
                id = "misas/modo-live",
                title = "Modo Celebración (pase de misa)",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El **Modo Celebración** presenta los cantos de la misa uno detrás de otro, a pantalla " +
                            "completa y con fondo oscuro para no deslumbrar en el templo. La pantalla permanece " +
                            "encendida y puedes pasar de canto con un gesto."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Desde una celebración, pulsa el icono **▶** (pase de misa en vivo).",
                            "Desliza el dedo **hacia los lados** para ir al canto anterior o siguiente. Es el " +
                                "gesto más cómodo en el atril.",
                            "También puedes usar los botones grandes **◀** y **▶** de abajo.",
                            "Arriba se indica en qué canto vas («Canto 3 de 8») y su momento litúrgico.",
                            "Pulsa **Pantalla completa** para el **modo sin distracciones**: desaparecen las barras y solo " +
                                "quedan la canción y el título, pero **sigues pasando cantos deslizando**.",
                            "Pulsa **✕** para salir del modo celebración."
                        )
                    ),
                    ManualBlock.Note(
                        "En el Modo Celebración la **única acción** sobre el canto es **salir**: no aparecen " +
                            "«editar» ni «eliminar», para que no se confundan con **quitar el canto de la " +
                            "misa**. Si quieres quitar un canto del repertorio, hazlo desde la lista de la " +
                            "misa; para borrarlo del catálogo, desde el listado de Canciones o desde el editor."
                    )
                )
            ),
            ManualSection(
                id = "misas/compartir",
                title = "Compartir una misa o evento con el coro",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Puedes enviar un repertorio **completo** por WhatsApp, Telegram o correo, y quien lo " +
                            "reciba lo abrirá directamente en su Canticora con todas sus canciones, su orden y " +
                            "sus momentos."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "En **Misas y Eventos**, abre el menú de tres puntos (⋮) de la celebración y elige " +
                                "**Compartir**; o, dentro del repertorio, el botón **Compartir** de la barra inferior.",
                            "Elige el canal (WhatsApp, Telegram, correo…). Se adjunta un archivo **.canticora**.",
                            "Quien lo reciba solo tiene que **abrirlo**; la app lo reconoce y lo importa sola.",
                            "También puede importarlo a mano con el icono **Importar** (nube con flecha, " +
                                "arriba a la derecha) de Misas y Eventos."
                        )
                    ),
                    ManualBlock.Note(
                        "Al importar **nunca se sobrescribe** ninguna canción tuya: las que ya tengas se " +
                            "reutilizan tal cual y solo se añaden las que falten. El repertorio llega siempre " +
                            "como una lista **nueva**."
                    )
                )
            ),
            ManualSection(
                id = "misas/imprimir",
                title = "Imprimir el repertorio en PDF",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Si necesitas una copia en papel para el sacerdote, el monitor o los atriles, genera " +
                            "un **único PDF** con todo el repertorio: primero una **portada con el índice** " +
                            "(momentos y títulos) y después **un canto por página**, con el tono de la " +
                            "celebración, tipografía legible y la marca de Canticora."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "En **Misas y Eventos**, abre el menú de tres puntos (⋮) de la celebración y elige " +
                                "**Exportar a PDF**; o, dentro del repertorio, el botón **PDF** de la barra inferior.",
                            "Responde al aviso **«¿Con acordes o solo la letra?»**. Canticora **recuerda tu " +
                                "elección** y la propone la próxima vez.",
                            "Elige dónde guardarlo (Descargas, Drive…) en el selector del sistema.",
                            "Ábrelo o imprímelo desde esa ubicación."
                        )
                    ),
                    ManualBlock.Paragraph(
                        "El PDF se maqueta como un **cancionero de coro**: la letra va **centrada** y en " +
                            "tipografía con serifa, los **acordes** se apoyan sobre su sílaba exacta y los " +
                            "**estribillos** salen centrados, en negrita y algo mayores, para localizarlos de " +
                            "un vistazo en el atril. Cada página del repertorio lleva el **título del canto** " +
                            "en la cabecera."
                    ),
                    ManualBlock.Paragraph(
                        "Los acordes y la tonalidad se imprimen en la **nomenclatura elegida** (latina o " +
                            "anglosajona), la misma que ves en pantalla. El PDF separa los acordes que " +
                            "quedarían muy juntos, de modo que nunca se solapan."
                    ),
                    ManualBlock.Note(
                        "También puedes exportar **un solo canto** a PDF desde el editor de la canción, con el " +
                            "botón **PDF** de la barra inferior. En ambos casos se pregunta si el PDF debe " +
                            "llevar **acordes** o **solo la letra**, y la respuesta se recuerda."
                    )
                )
            ),
            ManualSection(
                id = "misas/gestion",
                title = "Filtrar y borrar repertorios",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En la lista de **Misas y Eventos** puedes filtrar por tipo con los chips **Todos**, " +
                            "**Misas** y **Eventos**. Tocar una celebración la **abre para editarla**; el menú " +
                            "de tres puntos (⋮) agrupa **Abrir en vivo**, **Compartir**, **Exportar a PDF** y " +
                            "**Eliminar** (con confirmación)."
                    ),
                    ManualBlock.Note(
                        "Eliminar una celebración **no borra los cantos**: solo desaparece la lista."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 8 · Categorías litúrgicas
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterCategorias() = ManualChapter(
        id = "categorias",
        title = "9. Categorías y etiquetas",
        icon = ManualChapterIcon.CATEGORIES,
        sections = listOf(
            ManualSection(
                id = "categorias/que-son",
                title = "Momentos y tiempos litúrgicos",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Canticora trae precargado un catálogo base de **momentos de la Misa** (Entrada, " +
                            "Kyrie, Gloria, Ofertorio, Santo, Comunión, Envío…) y de **tiempos litúrgicos** " +
                            "(Adviento, Navidad, Cuaresma, Pascua, Tiempo Ordinario…)."
                    ),
                    ManualBlock.Paragraph(
                        "Además puedes crear tus propias **etiquetas adicionales** (por ejemplo " +
                            "«Franciscanas» o «Juveniles»), que no son momentos litúrgicos pero ayudan a " +
                            "organizar el repertorio."
                    )
                )
            ),
            ManualSection(
                id = "categorias/gestionar",
                title = "Crear, editar y eliminar categorías",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Abre **Gestión de Categorías** desde el menú.",
                            "Pulsa **Nueva** para crear un momento, tiempo o etiqueta.",
                            "Escribe el nombre, elige el tipo y confirma.",
                            "Para **renombrar** una categoría —o cambiar su tipo— pulsa el icono de lápiz de su fila. " +
                                "Puedes editar **cualquier** categoría, incluidas las precargadas.",
                            "Para **eliminar** una categoría usa el icono de la **papelera** de su fila. " +
                                "También puedes eliminar las precargadas; la app te pedirá confirmación."
                        )
                    ),
                    ManualBlock.Note(
                        "Junto a cada categoría verás **entre paréntesis cuántas canciones** la tienen " +
                            "asignada. Así sabes de antemano a cuántos cantos afecta lo que vas a hacer."
                    ),
                    ManualBlock.Note(
                        "Al renombrar una categoría, el cambio se aplica **también** a los cantos y a los " +
                            "repertorios que la usaban: no tendrás que reasignarla a mano."
                    ),
                    ManualBlock.Note(
                        "Al **eliminar** una categoría **no se borra ninguna canción**: los cantos que la " +
                            "usaban simplemente se quedan sin esa etiqueta. Si borras una categoría " +
                            "precargada, puedes recuperarla con **Restaurar categorías estándar**."
                    )
                )
            ),
            ManualSection(
                id = "categorias/asignar",
                title = "Asignar categorías a un canto",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En el editor de la canción, en «Categorías y Etiquetas», marca uno o varios momentos " +
                            "y tiempos. Puedes quitar cualquiera de los asignados con la **✕**."
                    ),
                    ManualBlock.Note(
                        "Un canto puede pertenecer a varios momentos: por ejemplo, un «Gloria» puede ir a " +
                            "«Gloria» y también a «Misas solemnes»."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 9 · Editor de canciones
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterEditor() = ManualChapter(
        id = "editor",
        title = "10. Editar una canción",
        icon = ManualChapterIcon.EDITOR,
        sections = listOf(
            ManualSection(
                id = "editor/pestanas",
                title = "Editor y Vista previa",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El editor tiene dos pestañas: **Editor**, donde escribes, y **Vista Previa**, donde " +
                            "compruebas cómo quedará el canto mientras trabajas."
                    ),
                    ManualBlock.Paragraph(
                        "La vista previa muestra un **borrador en memoria** con lo que tienes en el editor en " +
                            "ese momento, no la última versión guardada. Así ves el resultado real —título, " +
                            "autor, número, capo, tempo, compás, patrón rítmico, notas y categorías incluidos— " +
                            "**antes** de guardar. El borrador nunca se escribe en disco: si sales de la vista " +
                            "previa sin guardar, el canto guardado queda intacto."
                    ),
                    ManualBlock.Note(
                        "Si intentas salir del editor con cambios sin guardar, la app te avisa y te deja elegir " +
                            "entre **Salir sin guardar** o **Seguir editando**. Es la red de seguridad para no " +
                            "perder veinte minutos de trabajo por un toque accidental."
                    )
                )
            ),
            ManualSection(
                id = "editor/metadatos",
                title = "Metadatos del canto",
                blocks = listOf(
                    ManualBlock.Reference(
                        headers = listOf("Campo", "Descripción"),
                        rows = listOf(
                            listOf("Título *", "Obligatorio. Aparece en el catálogo y las listas."),
                            listOf("Número", "Opcional y repetible. Ver capítulo 2."),
                            listOf("Autor o Fuente", "Quién compuso o de dónde viene el canto."),
                            listOf("Compás", "Compás del patrón rítmico (4/4, 6/8…)."),
                            listOf("Patrón rítmico", "Rasgueo asociado al canto (opcional)."),
                            listOf("Tempo", "BPM e indicación italiana (Andante, Allegro…)."),
                            listOf("Capo / Cejilla", "Posición de cejilla recomendada."),
                            listOf("Notas de ensayo", "Indicaciones para el coro.")
                        )
                    )
                )
            ),
            ManualSection(
                id = "editor/chordpro",
                title = "Escribir con acordes (ChordPro)",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El cuerpo del canto se escribe en **ChordPro**: la letra normal y los acordes entre " +
                            "corchetes justo antes de la sílaba donde entran. Por ejemplo:"
                    ),
                    ManualBlock.Paragraph("[C]Alabaré a mi [G]Señor, [Am]alabaré a mi [F]Dios."),
                    ManualBlock.Paragraph(
                        "El editor incluye un **teclado de acordes** de un toque: raíz (Do, Re, Mi…), " +
                            "modificadores (m, 7, sus4…), alteraciones (♯/♭) y acordes frecuentes. También " +
                            "botones para insertar secciones ([Estrofa], [Coro], [Puente]) y una **paleta de " +
                            "símbolos** (rasgueos, barras de compás, repeticiones…)."
                    ),
                    ManualBlock.Note(
                        "La vista previa aplica el estilo real: colores de sección, cursivas de comentario y " +
                            "símbolos dibujados. Si algo no se ve bien ahí, revísalo antes de guardar."
                    ),
                    ManualBlock.Note(
                        "El área de texto es **redimensionable**: arrastra el asa gris de debajo para " +
                            "hacerla más alta o más baja, o pulsa el botón de **ampliar** (flechas " +
                            "verticales) para llevarla a su tamaño máximo y volver al normal. Muy útil en " +
                            "escritorio o al trabajar con cantos largos."
                    )
                )
            ),
            ManualSection(
                id = "editor/directivas",
                title = "Panel de directivas avanzadas",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Desde el editor puedes insertar directivas avanzadas sin memorizarlas: **voces** " +
                            "([Sopranos], [Asamblea]…), **bises / repeticiones** y **comentarios por voz**. " +
                            "El icono **(i)** de la esquina superior derecha del panel abre esta misma " +
                            "explicación sin salir de la página."
                    ),
                    ManualBlock.Paragraph(
                        "Las directivas son órdenes escritas entre llaves `{}` dentro del texto del canto. " +
                            "**No se cantan**: le dicen a Canticora (y a cualquier programa que lea " +
                            "ChordPro) cómo está organizado el canto. Al pulsar un botón del panel, la " +
                            "directiva se inserta en el punto donde tengas el cursor."
                    ),
                    ManualBlock.Reference(
                        caption = "Qué inserta cada botón",
                        headers = listOf("Botón", "Qué escribe", "Para qué sirve"),
                        rows = listOf(
                            listOf(
                                "Sopranos, Altos, Tenores, Bajos, Asamblea, Solista",
                                "{start_of_verse-voz} … {end_of_verse}",
                                "Abre y cierra un bloque cantado por una voz concreta. El sufijo -voz es " +
                                    "una extensión de Canticora: marca quién canta ese fragmento y permite " +
                                    "escribir polifonía. En ChordPro estándar se lee como una estrofa normal."
                            ),
                            listOf(
                                "Bises (se repite)",
                                "{repeat}",
                                "Marca el comienzo de un fragmento que se repite. La app lo señala con la " +
                                    "etiqueta «Bises»."
                            ),
                            listOf(
                                "Se repite ×2",
                                "{repeat: 2} … {repeat_end}",
                                "Indica que el fragmento se repite 2 veces; {repeat_end} cierra el bloque. " +
                                    "Cambia el número para otras repeticiones."
                            ),
                            listOf(
                                "Coro etiquetado",
                                "{start_of_chorus label=\"Estribillo\"}",
                                "Abre el coro y le pone un nombre propio, que sustituye a «Coro» en la " +
                                    "vista previa y en el PDF."
                            ),
                            listOf(
                                "Estrofa numerada",
                                "{start_of_verse Verse 1}",
                                "Estrofa con nombre libre: el texto que sigue a la directiva es la etiqueta " +
                                    "que se muestra en lugar de «Estrofa»."
                            ),
                            listOf(
                                "Añadir comentario",
                                "{comment-voz: ...}",
                                "Comentario dirigido a una voz concreta (indicaciones de ensayo, entradas, " +
                                    "dinámica…). Se muestra en cursiva y no se canta."
                            ),
                            listOf(
                                "Insertar ritmo",
                                "{x_rhythm: Nombre}",
                                "Asigna a la sección el patrón de rasgueo elegido, para cambiar de ritmo al " +
                                    "pasar de la estrofa al coro. El desplegable solo muestra los ritmos del " +
                                    "compás elegido para el canto; elige el patrón y la directiva se inserta " +
                                    "con su nombre exacto."
                            )
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Para cambiar el ritmo de una sección (por ejemplo, un **estribillo** con otro " +
                            "rasgueo), coloca `{x_rhythm: Nombre}` **dentro** de esa sección, justo después " +
                            "de su `{start_of_...}`. El visor mostrará bajo el título del coro el nombre del " +
                            "patrón y su rejilla. El nombre debe coincidir con uno de tus patrones de " +
                            "**Ritmos de rasgueo**."
                    ),
                    ManualBlock.Note(
                        "Todo esto es texto plano: puedes escribir las directivas a mano en la pestaña " +
                            "**ChordPro** y se guardan tal cual al exportar. En el apéndice tienes la " +
                            "referencia completa de directivas."
                    ),
                    ManualBlock.Paragraph(
                        "El **PDF** reproduce el rasgueo (rejilla de flechas) bajo el título de cada " +
                            "sección que declare su ritmo, y el patrón global del canto junto a los datos. " +
                            "Si la canción no cabe en una hoja, se maqueta automáticamente **a dos " +
                            "columnas** para ahorrar papel."
                    )
                )
            ),
            ManualSection(
                id = "editor/import-export",
                title = "Compartir, importar y exportar",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "**Importar de ChordPro**: al crear una canción nueva, acepta el aviso «¿Importar " +
                                "desde ChordPro?» y pega el texto. La app lo valida y avisa si hay errores.",
                            "**Compartir el canto**: en el editor, pulsa el icono **Compartir** (⤴). Se abre la " +
                                "hoja del móvil (WhatsApp, Telegram, correo…) con el canto en ChordPro como " +
                                "texto y como archivo.",
                            "**Compartir un canto con sus adjuntos**: al pulsar **Compartir** se te pregunta " +
                                "**«¿Quieres compartir también los adjuntos?»**. Si respondes «Sí, con los " +
                                "adjuntos», el canto viaja junto a sus audios, vídeos, documentos e imágenes en " +
                                "un archivo más pesado que quien lo reciba abre con Canticora y recupera todo. " +
                                "Si respondes «Solo el canto», se envía únicamente el texto, como siempre.",
                            "**Exportar a PDF**: pulsa el botón **PDF** de la barra inferior. Canticora pregunta " +
                                "**«¿Con acordes o solo la letra?»** y **recuerda tu elección** para la próxima " +
                                "vez. Después elige dónde guardarlo en el selector del sistema."
                        )
                    ),
                    ManualBlock.Note(
                        "El compartir respeta la sintaxis ChordPro estándar, de modo que el texto se puede " +
                            "abrir en otros programas. En el apéndice tienes la referencia de directivas. " +
                            "Los **enlaces** que compartas viajan solo como su dirección; los ficheros " +
                            "adjuntos viajan copiados dentro del archivo."
                    )
                )
            ),
            ManualSection(
                id = "editor/duplicados",
                title = "Si el canto ya existe",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Cada canto guarda una **huella de su contenido**. Si al guardar o importar un canto " +
                            "la app detecta que ya tienes **ese mismo contenido** guardado (por ejemplo, " +
                            "porque alguien te lo ha reenviado por WhatsApp tras editarlo), te preguntará qué " +
                            "hacer."
                    ),
                    ManualBlock.Reference(
                        caption = "Opciones cuando el canto ya existe",
                        headers = listOf("Opción", "Qué hace"),
                        rows = listOf(
                            listOf("Sobrescribir", "Actualiza tu copia con esta versión, conservando su identidad."),
                            listOf("Crear nueva", "Guarda una copia aparte y deja intacta la que ya tenías.")
                        )
                    ),
                    ManualBlock.Note(
                        "La huella es el **contenido** de la canción, no su ficha: si cambias una sílaba o un " +
                            "acorde, la huella cambia. Editar tu propia canción nunca se considera un duplicado."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 10 · Copias de seguridad
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterBackup() = ManualChapter(
        id = "backup",
        title = "11. Datos y copias de seguridad",
        icon = ManualChapterIcon.BACKUP,
        sections = listOf(
            ManualSection(
                id = "backup/donde",
                title = "Dónde se guardan tus datos",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Todo se guarda **en tu dispositivo**, en una base de datos local. Canticora no envía " +
                            "nada a ningún servidor y no necesita internet para funcionar."
                    ),
                    ManualBlock.Note(
                        "Al estar todo en local, si desinstalas la app o pierdes el dispositivo, perderás los " +
                            "datos salvo que hayas hecho una copia de seguridad."
                    )
                )
            ),
            ManualSection(
                id = "backup/sincronizar",
                title = "Sincronizar el móvil y el ordenador (sin nube ni cuentas)",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Canticora funciona **igual en el móvil y en el ordenador** (Windows). Puedes tener " +
                            "**todas tus instalaciones idénticas** —las mismas canciones, repertorios, " +
                            "categorías, ritmos y adjuntos— **sin crear ninguna cuenta y sin subir nada a " +
                            "internet**. El «transporte» es un único archivo `.canticora` que tú mueves de un " +
                            "dispositivo a otro."
                    ),
                    ManualBlock.Note(
                        "**Esta es la forma oficial de mantener Canticora actualizado y sincronizado en todos " +
                            "tus dispositivos.** No es solo una copia de seguridad: es el mecanismo para " +
                            "tener el móvil y el ordenador exactamente iguales."
                    ),
                    ManualBlock.Paragraph(
                        "El intercambio es **bidireccional**: el archivo se puede generar en cualquiera de " +
                            "los dos y aplicarse en el otro. Da igual en qué dispositivo lo crees."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "**En el dispositivo de origen** (el que tiene los cambios más recientes): abre " +
                                "**☰ → Copia de seguridad** y pulsa **Crear copia de seguridad** (o " +
                                "**Compartir copia** si quieres enviarlo directamente).",
                            "**Lleva el archivo al otro dispositivo**: por cable USB, por una carpeta " +
                                "compartida, por WhatsApp, por correo o por la carpeta de Google Drive que " +
                                "ya uses. Canticora no interviene en ese paso: el archivo es tuyo.",
                            "**En el dispositivo de destino**: abre **☰ → Copia de seguridad → Sincronizar con " +
                                "el archivo** y elige el archivo; o tócalo desde el gestor de archivos y " +
                                "elige **Abrir con Canticora**.",
                            "**Repite el proceso en sentido contrario** cuando hagas cambios en el otro " +
                                "dispositivo. Así los dos quedan siempre al día."
                        )
                    ),
                    ManualBlock.Note(
                        "**Sincronizar** añade lo que falta y **actualiza** los cantos que ya existían " +
                            "con la versión del archivo, sin borrar nada más. Por eso puedes aplicar el " +
                            "archivo en los dos sentidos sin miedo: no se pierde trabajo."
                    ),
                    ManualBlock.Paragraph(
                        "Si alguna vez quieres dejar el dispositivo **exactamente igual** que una copia " +
                            "(por ejemplo, para dejarlo limpio), usa en su lugar **Restaurar copia**: borra " +
                            "el cancionero actual y lo sustituye por el del archivo. Como borra, antes de " +
                            "hacerlo te muestra **qué contiene** el archivo y te pide confirmación."
                    ),
                    ManualBlock.Paragraph(
                        "**Importar una biblioteca de un compañero**: si solo quieres traer lo que te falta " +
                            "—el contenido de otro miembro del coro— usa **☰ → Importar → Importar una " +
                            "biblioteca**. Añade todo lo que no tengas y **actualiza** las canciones, misas " +
                            "y ritmos que coincidan; **no borra nada**. Antes de empezar guarda sola una copia " +
                            "de seguridad automática, por si quieres volver atrás."
                    ),
                    ManualBlock.Paragraph(
                        "**Consejo**: si usas una carpeta de Google Drive (o similar) en los dos " +
                            "dispositivos, guarda ahí siempre la copia con el **mismo nombre**. Así el " +
                            "archivo se sobrescribe y siempre tienes a mano la versión más reciente para " +
                            "llevar al otro dispositivo."
                    )
                )
            ),
            ManualSection(
                id = "backup/respaldo",
                title = "Crear una copia de seguridad",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En el menú **☰ → Copia de seguridad** puedes guardar **todo** tu cancionero en un " +
                            "único archivo: tus canciones, tus repertorios, tus categorías y los adjuntos " +
                            "(audios, vídeos, partituras…) de cada canto."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Abre **☰ → Copia de seguridad**.",
                            "Pulsa **Crear copia de seguridad**.",
                            "En el selector que aparece, elige una carpeta: puede ser de tu **Google Drive**, " +
                                "una carpeta del teléfono o una tarjeta de memoria. Escribe un nombre y acepta.",
                            "Cuando termine, la copia queda guardada en esa carpeta."
                        )
                    ),
                    ManualBlock.Note(
                        "No hace falta **crear ninguna cuenta ni iniciar sesión**: si eliges una carpeta de " +
                            "Google Drive, la propia app de Drive del móvil se encarga. La copia es un archivo " +
                            "con extensión `.canticora` que se puede **descargar, copiar o compartir** como " +
                            "cualquier otro archivo."
                    ),
                    ManualBlock.Note(
                        "Este mismo archivo es el que sirve para **sincronizar el móvil y el ordenador**: " +
                            "mira «Sincronizar el móvil y el ordenador» más arriba."
                    )
                )
            ),
            ManualSection(
                id = "backup/restaurar",
                title = "Restaurar una copia",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Sirve para dejar este dispositivo **exactamente igual** que una copia: al estrenar " +
                            "un móvil, al reinstalar la app o cuando quieres partir del contenido de un " +
                            "compañero del coro. Se hace en dos pasos:"
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Abre **☰ → Copia de seguridad → Restaurar copia** y elige el archivo.",
                            "Canticora **comprueba el archivo y te muestra qué contiene** (cuántos cantos, " +
                                "repertorios, categorías, ritmos y adjuntos trae).",
                            "Si estás de acuerdo, pulsa **Borrar y restaurar**. Si no, cancela: no se habrá " +
                                "tocado nada."
                        )
                    ),
                    ManualBlock.Note(
                        "**Restaurar borra**: tu cancionero actual y tus repertorios se borran y se " +
                            "sustituyen por los del archivo. Por eso se comprueba el archivo y se pide " +
                            "confirmación antes de borrar nada."
                    ),
                    ManualBlock.Note(
                        "**Red de seguridad**: justo antes de importar o de borrar, Canticora guarda " +
                            "**sola** una copia de lo que tenías. Si te arrepientes, entra en **☰ → Copia " +
                            "de seguridad → Recuperar lo que había antes de restaurar** (se conservan las " +
                            "últimas copias automáticas)."
                    ),
                    ManualBlock.Note(
                        "Restaurar **no** es lo mismo que sincronizar. Si lo que quieres es **poner al día** " +
                            "este dispositivo con los cambios hechos en otro sin perder lo que ya tienes aquí, " +
                            "usa **Sincronizar con el archivo** (mira «Sincronizar el móvil y el ordenador» " +
                            "más arriba)."
                    )
                )
            ),
            ManualSection(
                id = "backup/compartir",
                title = "Compartir la copia con el coro",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "En la pantalla de Copia de seguridad, el botón **Compartir copia** genera el archivo y " +
                            "abre la hoja de compartir del móvil para enviarlo por **WhatsApp, correo**, etc. " +
                            "Quien lo reciba solo tiene que tocarlo y elegir **Abrir con Canticora**."
                    ),
                    ManualBlock.Note(
                        "También puedes compartir solo **un repertorio** concreto (una misa o un evento) desde " +
                            "**Misas y Eventos → Compartir archivo**, sin enviar todo tu cancionero."
                    )
                )
            ),
            ManualSection(
                id = "backup/reset",
                title = "Empezar de cero",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Si algún día necesitas vaciar el cancionero, puedes **borrar las canciones una a " +
                            "una** desde su ficha. **Antes** de hacerlo, asegúrate de tener una copia de " +
                            "seguridad de aquello que quieras conservar."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 11 · Importar contenido
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterImportar() = ManualChapter(
        id = "importar",
        title = "12. Importar contenido",
        icon = ManualChapterIcon.IMPORT,
        sections = listOf(
            ManualSection(
                id = "importar/que-es",
                title = "Canticora se instala sin canciones",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Canticora **no incluye ninguna canción**: la app se instala con la estructura " +
                            "preparada (los momentos litúrgicos y los patrones rítmicos) y el cancionero " +
                            "vacío. **El contenido es tuyo** y lo incorporas tú cuando quieras."
                    ),
                    ManualBlock.Note(
                        "Al abrir la app por primera vez verás la pantalla **Canciones** vacía. Esto es normal: " +
                            "para llenarlo, abre **☰ → Importar** y elige una de las vías de abajo."
                    )
                )
            ),
            ManualSection(
                id = "importar/vias",
                title = "Las cuatro formas de importar",
                blocks = listOf(
                    ManualBlock.Reference(
                        caption = "Sección Importar (☰)",
                        headers = listOf("Qué traes", "Formato del archivo"),
                        rows = listOf(
                            listOf(
                                "Canciones de Canticora (cancionero completo)",
                                "Un archivo **JSON** (`.json`). Incorpora de una vez todas las canciones y sus categorías."
                            ),
                            listOf(
                                "Repertorio de una misa o evento",
                                "Un archivo **`.canticora`**. También puedes abrirlo directamente desde WhatsApp o el gestor de archivos."
                            ),
                            listOf(
                                "Canciones sueltas en ChordPro",
                                "Uno o varios archivos **`.cho` / `.chordpro` / `.txt`**."
                            ),
                            listOf(
                                "Copia de seguridad completa",
                                "Un archivo **`.zip`** creado desde **☰ → Copia de seguridad**."
                            )
                        )
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "Abre el menú **☰** y elige **Importar**.",
                            "Arriba verás un resumen de tu biblioteca actual (cuántas canciones, repertorios y categorías tienes).",
                            "En la tarjeta de la vía que quieras, pulsa su botón y elige el archivo en el selector del móvil.",
                            "Espera unos instantes: al terminar se muestra un resumen de lo importado."
                        )
                    )
                )
            ),
            ManualSection(
                id = "importar/seguridad",
                title = "Importar nunca destruye lo tuyo",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Importar un **catálogo completo** solo añade: las canciones que ya tienes no se " +
                            "sobrescriben ni se duplican, y los repertorios llegan siempre como una lista " +
                            "**nueva**."
                    ),
                    ManualBlock.Paragraph(
                        "Cuando lo que recibes es un **canto suelto, un repertorio o un ritmo** que ya tienes " +
                            "pero **con cambios** (por ejemplo, alguien te lo reenvía tras editarlo), la app " +
                            "**te pregunta antes de tocar nada**, igual que Windows al copiar archivos sobre " +
                            "una carpeta que ya los contiene:"
                    ),
                    ManualBlock.Reference(
                        caption = "Qué hacer cuando ya lo tienes",
                        headers = listOf("Opción", "Qué hace"),
                        rows = listOf(
                            listOf("**Sobrescribir**", "Sustituye tu versión por la que llega (conservando su ficha, adjuntos y presencia en repertorios)."),
                            listOf("**Conservar ambos**", "Añade la versión que llega como un elemento nuevo, sin tocar el tuyo."),
                            listOf("**Omitir**", "Se queda el tuyo y se descarta lo que llega.")
                        )
                    ),
                    ManualBlock.Note(
                        "Si no hay cambios (el elemento es idéntico) no se pregunta: nunca se duplica. Y " +
                            "puedes aplicar **la misma decisión a todos** los conflictos del archivo de una vez."
                    ),
                    ManualBlock.Note(
                        "Todo el proceso ocurre **en tu dispositivo**, sin conexión a internet."
                    )
                )
            ),
            ManualSection(
                id = "importar/chordpro",
                title = "Importar una canción en ChordPro",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "El **ChordPro** es un formato de texto plano muy usado para escribir cantos con " +
                            "acordes (mira el apéndice de este manual para conocer sus directivas)."
                    ),
                    ManualBlock.Steps(
                        listOf(
                            "En **☰ → Importar**, pulsa **Elegir canciones…** en la tarjeta de ChordPro.",
                            "Selecciona uno o varios archivos `.cho`, `.chordpro` o `.txt`.",
                            "El **título, el autor y el tono** se toman de las propias directivas del archivo; " +
                                "si no hay título, se usa el nombre del archivo."
                        )
                    ),
                    ManualBlock.Note(
                        "También puedes pegar un texto ChordPro al crear una canción a mano: en el editor, " +
                            "acepta «**¿Importar desde ChordPro?**»."
                    )
                )
            ),
            ManualSection(
                id = "importar/no-es-cancion",
                title = "Si eliges un vídeo, una foto o un PDF por error",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Canticora solo importa **canciones, repertorios y copias de seguridad**. Si eliges " +
                            "sin querer un **vídeo, un audio, una foto o un PDF** (por ejemplo, al abrir un " +
                            "archivo que te llega por WhatsApp), la app lo reconoce y te avisa: «Esto es un " +
                            "vídeo, no una canción» (o «imagen», «audio», «documento PDF»…). **Nunca se crea " +
                            "una canción** con ese contenido."
                    ),
                    ManualBlock.Note(
                        "Si quieres **guardar ese vídeo o audio junto a un canto** (por ejemplo, una grabación " +
                            "del ensayo), no lo importes: abre la canción y, en su pantalla, usa **Añadir " +
                            "adjunto** (ver el capítulo 4, «Adjuntos: audios, vídeos y documentos»)."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 12 · Apéndice ChordPro
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterApendiceChordPro() = ManualChapter(
        id = "apendice-chordpro",
        title = "13. Apéndice: referencia ChordPro",
        icon = ManualChapterIcon.CHORDPRO,
        sections = listOf(
            ManualSection(
                id = "apendice-chordpro/cuerpo",
                title = "Acordes y secciones en el cuerpo",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Los **acordes** van entre corchetes justo delante de la sílaba: `[Do]Alabaré`. " +
                            "Un corchete solo con texto en una línea suelta también se acepta como rótulo de " +
                            "sección."
                    ),
                    ManualBlock.Reference(
                        caption = "Entornos de sección",
                        headers = listOf("Se escribe", "Significado"),
                        rows = listOf(
                            listOf("{start_of_verse} … {end_of_verse}", "Estrofa (abreviatura: {sov} … {eov})."),
                            listOf("{start_of_chorus} … {end_of_chorus}", "Coro o estribillo (abreviaturas: {soc} … {eoc})."),
                            listOf("{start_of_bridge} … {end_of_bridge}", "Puente (abreviaturas: {sob} … {eob})."),
                            listOf("{start_of_intro} / {start_of_outro}", "Intro y final instrumental."),
                            listOf("{start_of_interlude}", "Interludio instrumental.")
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Las líneas que llevan **solo acordes**, sin letra debajo (intros, riffs, finales), " +
                            "se dibujan como un bloque propio: `[Am] [G] [F] [E]`. No necesitan ninguna " +
                            "directiva especial."
                    ),
                    ManualBlock.Reference(
                        caption = "Cambiar el rasgueo por sección",
                        headers = listOf("Se escribe", "Significado"),
                        rows = listOf(
                            listOf("{x_rhythm: Rock rítmico}", "La sección usa el patrón rítmico con ese nombre; se resuelve contra tus patrones guardados.")
                        )
                    ),
                    ManualBlock.Paragraph(
                        "Coloca `{x_rhythm: Nombre}` **dentro** de la sección (después de su " +
                            "`{start_of_...}`). Así un mismo canto puede cambiar de ritmo al pasar de la " +
                            "estrofa al coro. El nombre debe coincidir con uno de tus patrones de " +
                            "**Ritmos de rasgueo**."
                    )
                )
            ),
            ManualSection(
                id = "apendice-chordpro/directivas",
                title = "Directivas de cabecera",
                blocks = listOf(
                    ManualBlock.Reference(
                        caption = "Directivas reconocidas por Canticora",
                        headers = listOf("Directiva", "Para qué sirve"),
                        rows = listOf(
                            listOf("{title: ...}   (o {t: ...})", "Título de la canción."),
                            listOf("{key: ...}     (o {k: ...})", "Tonalidad original."),
                            listOf("{number: 402}  (o {n: 402})", "Número dentro del cancionero."),
                            listOf("{artist: ...}  (o {author: ...})", "Autor o fuente."),
                            listOf("{time: ...}    (o {rhythm: ...})", "Ritmo o métrica."),
                            listOf("{tempo: 92 Andante}", "Velocidad en BPM y, si quieres, indicación."),
                            listOf("{capo: 3}", "Posición del capo o cejilla.")
                        )
                    )
                )
            ),
            ManualSection(
                id = "apendice-chordpro/voces-comentarios",
                title = "Voces, comentarios y repeticiones",
                blocks = listOf(
                    ManualBlock.Reference(
                        caption = "Etiquetas y entradas de voz",
                        headers = listOf("Se escribe", "Significado"),
                        rows = listOf(
                            listOf("{start_of_verse Pre-estribillo}", "La palabra suelta es una **etiqueta** de sección."),
                            listOf("{start_of_verse-soprano}", "El guion **pegado** al nombre indica una **voz**."),
                            listOf("{sopranos: texto}", "Entrada de una voz (alternativas: {tag: ...})."),
                            listOf("{comment: indicación}", "Comentario dentro de una sección (cursiva destacada)."),
                            listOf("{comment-todos: texto}", "Comentario dirigido a una voz concreta."),
                            listOf("{repeat} … {repeat_end}", "Entorno que se repite (bises)."),
                            listOf("{repeat: 2}   (o {x: 2})", "La sección se repite el número indicado de veces."),
                            listOf("{x_rhythm: Nombre}", "La sección usa ese patrón de rasgueo (cambio de ritmo por sección).")
                        )
                    ),
                    ManualBlock.Note(
                        "Un mismo símbolo `{}` puede ser etiqueta o voz según lleve o no el guion pegado al " +
                            "nombre: `{start_of_verse Pre-estribillo}` es una etiqueta; " +
                            "`{start_of_verse-soprano}` es una entrada de la voz Sopranos."
                    )
                )
            ),
            ManualSection(
                id = "apendice-chordpro/simbolos",
                title = "Símbolos musicales",
                blocks = listOf(
                    ManualBlock.Paragraph(
                        "Los símbolos son **dibujos musicales** (rasgueos, barras de compás, repeticiones, " +
                            "alteraciones, letras y cifras en círculo) que se colocan en un punto exacto de " +
                            "la letra. Se escriben con la etiqueta `<sym nombre/>` (por ejemplo " +
                            "`<sym arrow-down/>`). Canticora los dibuja en pantalla y los conserva tal " +
                            "cual al exportar."
                    ),
                    ManualBlock.Paragraph(
                        "En el editor, el panel **Símbolos musicales** los inserta por ti en la posición " +
                            "del cursor. El icono **(i)** de su esquina superior derecha abre esta misma " +
                            "explicación sin salir de la página."
                    ),
                    ManualBlock.Reference(
                        caption = "Familias de símbolos",
                        headers = listOf("Familia", "Ejemplos", "Significado"),
                        rows = listOf(
                            listOf(
                                "Rasgueo ↓",
                                "↓, ↓', ↓~, ↓·, ↓×",
                                "Rasgueo hacia abajo. Se combina con acento ('), arpegio (~), staccato (·) " +
                                    "y apagado (×)."
                            ),
                            listOf(
                                "Rasgueo ↑",
                                "↑, ↑', ↑~, ↑·, ↑×",
                                "Rasgueo hacia arriba, con las mismas combinaciones."
                            ),
                            listOf(
                                "Barras de compás",
                                "|, ‖, ▌, ┃, |:, :|",
                                "Separadores de compás: simple, doble, gruesa, gruesa doble, inicio y fin."
                            ),
                            listOf(
                                "Repeticiones",
                                "|:, :|, :|:",
                                "Signos de repetición: comienza, termina, y termina y vuelve a comenzar."
                            ),
                            listOf(
                                "Alteraciones",
                                "♭, ♮, ♯",
                                "Bemol, becuadro y sostenido, para notas alteradas dentro de la letra."
                            ),
                            listOf(
                                "Otros",
                                "①, Ⓐ, △",
                                "Números y letras en círculo (estrofas, voces, llamadas) y el símbolo delta."
                            )
                        )
                    ),
                    ManualBlock.Note(
                        "No hace falta memorizar los nombres: la paleta de símbolos del editor los inserta " +
                            "por ti y el texto exportado sigue siendo ChordPro válido."
                    )
                )
            )
        )
    )

    // ────────────────────────────────────────────────────────────────────────────
    // 13 · Consejos y solución de problemas
    // ────────────────────────────────────────────────────────────────────────────

    private fun chapterAtajosConsejos() = ManualChapter(
        id = "consejos",
        title = "14. Consejos y solución de problemas",
        icon = ManualChapterIcon.TIPS,
        sections = listOf(
            ManualSection(
                id = "consejos/ensayo",
                title = "Durante el ensayo",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Usa «Solo letra» con el coro y «Acordes sobre letra» con los instrumentos.",
                            "Sube el tamaño de letra para quien lea de lejos.",
                            "Transporta al vuelo para probar tonalidades sin editar el canto.",
                            "Crea un **evento de ensayo** con los cantos del día y usa el Modo Celebración para no perder el orden."
                        )
                    )
                )
            ),
            ManualSection(
                id = "consejos/celebracion",
                title = "Durante la celebración",
                blocks = listOf(
                    ManualBlock.Steps(
                        listOf(
                            "Deja preparada la misa con sus momentos y el orden definitivo.",
                            "Fija la **tonalidad de cada canto** en el repertorio (no dependas de transportar en directo).",
                            "Entra en el **Modo Celebración**: pantalla oscura, encendido permanente y paso con un gesto.",
                            "Revisa arriba «Canto X de Y» para saber dónde estás."
                        )
                    )
                )
            ),
            ManualSection(
                id = "consejos/problemas",
                title = "Preguntas frecuentes",
                blocks = listOf(
                    ManualBlock.Reference(
                        headers = listOf("Situación", "Qué hacer"),
                        rows = listOf(
                            listOf("No encuentro un canto", "Busca por una sola palabra de la letra; no hace falta tilde ni mayúscula exacta."),
                            listOf("Me falta un momento litúrgico", "Créalo en Gestión de Categorías; aparecerá al asignar cantos."),
                            listOf("Los acordes no cuadran con la sílaba", "Revisa el ChordPro: el `[Acorde]` va justo antes de la sílaba de entrada."),
                            listOf("Una misa muestra «Canción no disponible»", "El canto se borró del catálogo; vuelve a añadir otro o recupera la copia de seguridad."),
                            listOf("Al transponer, el capo no cambia", "Es normal: el capo es un dato del canto y se edita en el editor."),
                            listOf("El texto latino/anglosajón no cambia", "Comprueba la nomenclatura en el panel de controles del canto.")
                        )
                    )
                )
            )
        )
    )
}
