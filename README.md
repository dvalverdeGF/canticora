# Canticora

Cancionero musical digital para coros y comunidades litúrgicas. Aplicación **local-first**
(100 % sin conexión) para **Android**, **escritorio (Windows)** y **navegador**, construida con
Kotlin Multiplatform, Jetpack Compose (Material 3) y Room.

Sitio del producto: <https://canticora.com>

## Qué hace

- **Catálogo de cantos** con buscador y filtros por momento litúrgico (entrada, Kyrie, gloria,
  ofertorio, comunión, salida…), etiquetas y categorías.
- **Canto como dato musical**: el ChordPro se analiza a un árbol de secciones y acordes, no a un
  documento plano. Los acordes quedan anclados a la sílaba exacta.
- **Transposición y cejilla** con enarmonía correcta (círculo de quintas) y nomenclatura
  intercambiable: Do-Re-Mi, C-D-E o cifrado americano por grados.
- **Repertorios de misa** y **modo celebración**: listas ordenadas por momento litúrgico, con
  tonalidad propia por canto, navegación secuencial en pantalla completa y PDF del repertorio.
- **Tablaturas** de guitarra: edición, importación de partituras y previsualización.
- **Anotaciones personales**: dinámicas, respiraciones, cortes, entradas de voces y símbolos
  propios, con capas de visualización.
- **Ritmos**: patrones con editor y metrónomo.
- **Copia de seguridad y restauración** en un único `.zip`, con manifest y checksums.
- **Importar y exportar**: catálogo en JSON, ficheros ChordPro, paquetes `.canticora` de un canto
  con sus adjuntos y repertorios.
- **PDF real** de un canto y de un repertorio, con maquetación A4 a una o dos columnas.

## Estructura del repositorio

| Módulo | Contenido |
| :--- | :--- |
| `core/domain` | Modelos musicales, motor armónico, parser ChordPro, casos de uso. Kotlin puro, sin Android. |
| `core/presentation` | ViewModels con arquitectura MVI. |
| `core/ui` | Pantallas y componentes compartidos en Compose Multiplatform. |
| `app` | Aplicación Android (Room como fuente de verdad). |
| `desktopApp` | Aplicación de escritorio (Windows). |
| `webApp` | Aplicación web y PWA (Kotlin/Wasm). |

## Compilar

Requiere JDK 17 y el SDK de Android para las tareas de Android.

```bash
./gradlew assembleDebug                  # APK de Android
./gradlew :desktopApp:run                # aplicación de escritorio
./gradlew :webApp:wasmJsBrowserDistribution   # sitio web en webApp/build/dist/wasmJs
./gradlew testDebugUnitTest              # pruebas del dominio y de la capa de datos
```

## Licencia

El **código** se distribuye bajo **GNU GPL v3.0 only**; el texto completo está en
[`LICENSE`](./LICENSE).

El **contenido musical** (letras, composiciones, arreglos, partituras y catálogos de cantos) **no**
está cubierto por la GPLv3 ni se incluye en este repositorio; ver [`LICENSE-CONTENT`](./LICENSE-CONTENT).
