package com.grafitto.laudate.web

import com.grafitto.laudate.web.io.WebBytes
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.js.js

/**
 * Servicios del navegador que la UI necesita (ADR-023).
 *
 * En Android y escritorio esto vive en `:core:ui`/`:core:data` con `Intent`, SAF o diálogos nativos.
 * En la web se resuelve con llamadas al navegador: abrir una pestaña, copiar al portapapeles,
 * descargar un fichero de texto, elegir un fichero del equipo e imprimir a PDF.
 */
object WebPlatform {

    /** Abre una dirección externa (Apoyar, enlaces de un adjunto) en una pestaña nueva. */
    fun openUrl(url: String) {
        if (url.isBlank()) return
        openUrlJs(url)
    }

    /**
     * Copia texto al portapapeles: es el equivalente web de «Compartir» el ChordPro de un canto.
     *
     * @return `true` si el navegador aceptó la operación (la escritura es asíncrona, así que esto
     * indica que la petición salió, no que ya esté en el portapapeles).
     */
    fun copyToClipboard(text: String): Boolean = copyTextJs(text)

    /** Descarga un fichero de texto (el repertorio `.json` que «Compartir» exporta). */
    fun downloadText(fileName: String, content: String) {
        downloadTextJs(fileName, content)
    }

    /**
     * Abre el **selector de ficheros** del navegador y entrega el contenido de texto elegido.
     *
     * Es el equivalente web de SAF: `input type="file"` con `FileReader`. Se descartan los binarios
     * (`sniffMediaKind`) y los ZIP antes de decodificar, igual que en escritorio.
     *
     * @param accept filtro de tipos para el selector (p. ej. `".chordpro,.json,.txt"`).
     * @param onPicked recibe el nombre del fichero y su texto; no se llama si el usuario cancela.
     */
    fun pickTextFile(accept: String, onPicked: (fileName: String, content: String) -> Unit) {
        pickTextFileJs(accept, onPicked)
    }

    /** Imprime un documento HTML ya generado usando el diálogo del sistema («Guardar como PDF»). */
    fun printHtml(html: String) {
        printHtmlJs(html)
    }

    /** Descarga contenido binario ya codificado en base64 (adjuntos de fichero). */
    fun downloadBase64(fileName: String, mimeType: String, base64: String) {
        downloadBase64Js(fileName, mimeType, base64)
    }

    /**
     * Abre el selector de ficheros para adjuntar **cualquier** fichero (audio, vídeo, PDF, imagen).
     *
     * El contenido se guarda en **IndexedDB** (no en `localStorage`, que solo admite texto y unos
     * pocos MB) y se entrega a [onPicked] como `idb://<clave>` junto a nombre, tipo MIME, tamaño y
     * los bytes en base64. Es el equivalente web de copiar el fichero al almacén privado de la app.
     */
    fun pickAttachmentFile(
        accept: String,
        songId: String,
        onPicked: (sourceUri: String, fileName: String, mimeType: String, sizeBytes: Int) -> Unit
    ) {
        pickAttachmentJs(accept, songId, onPicked)
    }

    /** Lee un fichero del almacén de IndexedDB. `null` si la clave ya no existe. */
    suspend fun readAttachment(base64Key: String): WebStoredFile? =
        suspendCancellableCoroutine { continuation ->
            readAttachmentJs(base64Key) { name, mimeType, sizeBytes, base64 ->
                continuation.resume(
                    if (base64.isEmpty()) null
                    else WebStoredFile(name, mimeType, sizeBytes.toLong(), base64)
                )
            }
        }

    /** `true` mientras el fichero siga guardado en IndexedDB. */
    suspend fun attachmentFileExists(sourceUri: String): Boolean =
        suspendCancellableCoroutine { continuation ->
            attachmentExistsJs(keyOf(sourceUri)) { exists -> continuation.resume(exists) }
        }

    /** Borra un fichero del almacén (no hace nada si ya no estaba). */
    suspend fun deleteAttachmentFile(sourceUri: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            deleteAttachmentJs(keyOf(sourceUri)) { continuation.resume(Unit) }
        }
    }

    /** Borra todos los ficheros de un canto (se guardan bajo `songId/…`). */
    suspend fun deleteAttachmentFilesForSong(songId: String) {
        suspendCancellableCoroutine<Unit> { continuation ->
            deleteSongAttachmentsJs(songId) { continuation.resume(Unit) }
        }
    }

    /** Descarga cualquier contenido binario: PDF, ZIP de copia de seguridad, paquete de canto. */
    fun downloadBytes(fileName: String, mimeType: String, bytes: ByteArray) {
        downloadBase64Js(fileName, mimeType, WebBytes.base64Encode(bytes))
    }

    /**
     * Comparte un fichero con la hoja del sistema (Web Share API de nivel 2).
     *
     * @return `true` si el navegador lo compartió. Si devuelve `false` (escritorio sin soporte o
     * usuario que cancela), la UI debe ofrecer la descarga como alternativa.
     */
    suspend fun shareFile(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        title: String
    ): Boolean = suspendCancellableCoroutine { continuation ->
        shareBase64Js(fileName, mimeType, WebBytes.base64Encode(bytes), title) { shared ->
            continuation.resume(shared)
        }
    }

    /** Selector de ficheros que entrega los bytes en base64 (partituras y copias de seguridad). */
    fun pickBinaryFile(accept: String, onPicked: (fileName: String, base64: String) -> Unit) {
        pickBinaryJs(accept, onPicked)
    }

    /** Guarda bytes en IndexedDB bajo [key] (copias de seguridad automáticas, adjuntos extraídos). */
    suspend fun writeStoredFile(
        key: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ) {
        suspendCancellableCoroutine { continuation ->
            writeStoredFileJs(key, fileName, mimeType, WebBytes.base64Encode(bytes)) {
                continuation.resume(Unit)
            }
        }
    }

    /** Lee un fichero cualquiera del almacén del navegador. `null` si la clave ya no existe. */
    suspend fun readStoredFile(key: String): WebStoredFile? = readAttachment(key)

    /** Vacía el almacén binario completo (se usa al restaurar o al borrar la biblioteca). */
    suspend fun clearFileStore() {
        suspendCancellableCoroutine { continuation -> clearFileStoreJs { continuation.resume(Unit) } }
    }

    /** Lee un valor de `localStorage` (punteros y preferencias menudas). */
    fun readLocalValue(key: String): String = readLocalJs(key)

    /** Escribe un valor en `localStorage`; con [value] vacío lo elimina. */
    fun writeLocalValue(key: String, value: String) {
        writeLocalJs(key, value)
    }

    /** Convierte `idb://<clave>` en la clave; devuelve el texto tal cual si no lo es. */
    fun keyOf(sourceUri: String): String =
        sourceUri.removePrefix(IDB_SCHEME)

    /** Prefijo de los ficheros guardados en el almacén del navegador (`idb://<clave>`). */
    const val IDB_PREFIX: String = "idb://"

    private const val IDB_SCHEME = IDB_PREFIX
}

/** Fichero recuperado del almacén del navegador. */
data class WebStoredFile(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val base64: String
)

// El interop de Wasm exige que `js(...)` sea el cuerpo completo de una función de nivel superior.

private fun openUrlJs(url: String): String =
    js("(window.open(url, '_blank', 'noopener,noreferrer'), '')")

private fun copyTextJs(text: String): Boolean = js(
    """(function () {
        try {
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(text);
                return true;
            }
            var area = document.createElement('textarea');
            area.value = text;
            area.style.position = 'fixed';
            area.style.opacity = '0';
            document.body.appendChild(area);
            area.select();
            var ok = document.execCommand('copy');
            document.body.removeChild(area);
            return ok;
        } catch (e) {
            return false;
        }
    })()"""
)

private fun pickTextFileJs(
    accept: String,
    onPicked: (String, String) -> Unit
): String = js(
    """(function () {
        var input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            document.body.removeChild(input);
            if (!file) return;
            var reader = new FileReader();
            reader.onload = function () { onPicked(file.name, String(reader.result)); };
            reader.onerror = function () { onPicked(file.name, ''); };
            reader.readAsText(file);
        });
        input.click();
        return '';
    })()"""
)

private fun printHtmlJs(html: String): String = js(
    """(function () {
        var frame = document.createElement('iframe');
        frame.style.position = 'fixed';
        frame.style.right = '0';
        frame.style.bottom = '0';
        frame.style.width = '0';
        frame.style.height = '0';
        frame.style.border = '0';
        document.body.appendChild(frame);
        var doc = frame.contentDocument;
        doc.open();
        doc.write(html);
        doc.close();
        var done = function () {
            try {
                frame.contentWindow.focus();
                frame.contentWindow.print();
            } finally {
                setTimeout(function () { document.body.removeChild(frame); }, 60000);
            }
        };
        if (doc.readyState === 'complete') setTimeout(done, 250);
        else frame.addEventListener('load', function () { setTimeout(done, 250); });
        return '';
    })()"""
)

private fun downloadBase64Js(fileName: String, mimeType: String, base64: String): String = js(
    """(function () {
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        var url = URL.createObjectURL(new Blob([bytes], { type: mimeType || 'application/octet-stream' }));
        var link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
        return '';
    })()"""
)

private fun downloadTextJs(fileName: String, content: String): String = js(
    """(function () {
        var blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
        var url = URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        link.download = fileName;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
        return '';
    })()"""
)

// ── Almacén de ficheros en IndexedDB ────────────────────────────────────────────
// `localStorage` solo guarda texto y está limitado a unos pocos MB: los adjuntos (audio, vídeo,
// PDF) necesitan el almacén binario del navegador. Los ficheros se indexan por `songId/clave`
// para poder borrar todos los de un canto de una vez.

private fun pickAttachmentJs(
    accept: String,
    songId: String,
    onPicked: (String, String, String, Int) -> Unit
): String = js(
    """(function () {
        var input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            document.body.removeChild(input);
            if (!file) return;
            var reader = new FileReader();
            reader.onload = function () {
                var dataUrl = String(reader.result || '');
                var comma = dataUrl.indexOf(',');
                var base64 = comma >= 0 ? dataUrl.substring(comma + 1) : '';
                var key = songId + '/' + Date.now() + '-' + Math.random().toString(36).slice(2);
                var request = indexedDB.open('canticora-files', 1);
                request.onupgradeneeded = function () {
                    request.result.createObjectStore('files');
                };
                request.onsuccess = function () {
                    var db = request.result;
                    var tx = db.transaction('files', 'readwrite');
                    tx.objectStore('files').put(
                        { name: file.name, mime: file.type || '', size: file.size, base64: base64 },
                        key
                    );
                    tx.oncomplete = function () {
                        db.close();
                        onPicked('idb://' + key, file.name, file.type || '', file.size);
                    };
                };
                request.onerror = function () { onPicked('', file.name, file.type || '', file.size); };
            };
            reader.readAsDataURL(file);
        });
        input.click();
        return '';
    })()"""
)

private fun readAttachmentJs(
    key: String,
    onLoaded: (String, String, Int, String) -> Unit
): String = js(
    """(function () {
        var request = indexedDB.open('canticora-files', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('files'); };
        request.onsuccess = function () {
            var db = request.result;
            var tx = db.transaction('files', 'readonly');
            var get = tx.objectStore('files').get(key);
            get.onsuccess = function () {
                var record = get.result;
                db.close();
                if (!record) { onLoaded('', '', 0, ''); return; }
                onLoaded(record.name || '', record.mime || '', record.size || 0, record.base64 || '');
            };
            get.onerror = function () { db.close(); onLoaded('', '', 0, ''); };
        };
        request.onerror = function () { onLoaded('', '', 0, ''); };
        return '';
    })()"""
)

private fun attachmentExistsJs(key: String, onResult: (Boolean) -> Unit): String = js(
    """(function () {
        var request = indexedDB.open('canticora-files', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('files'); };
        request.onsuccess = function () {
            var db = request.result;
            var tx = db.transaction('files', 'readonly');
            var count = tx.objectStore('files').count(key);
            count.onsuccess = function () { db.close(); onResult(count.result > 0); };
            count.onerror = function () { db.close(); onResult(false); };
        };
        request.onerror = function () { onResult(false); };
        return '';
    })()"""
)

private fun deleteAttachmentJs(key: String, onDone: () -> Unit): String = js(
    """(function () {
        var request = indexedDB.open('canticora-files', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('files'); };
        request.onsuccess = function () {
            var db = request.result;
            var tx = db.transaction('files', 'readwrite');
            tx.objectStore('files').delete(key);
            tx.oncomplete = function () { db.close(); onDone(); };
            tx.onerror = function () { db.close(); onDone(); };
        };
        request.onerror = function () { onDone(); };
        return '';
    })()"""
)

private fun deleteSongAttachmentsJs(songId: String, onDone: () -> Unit): String = js(
    """(function () {
        var request = indexedDB.open('canticora-files', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('files'); };
        request.onsuccess = function () {
            var db = request.result;
            var tx = db.transaction('files', 'readwrite');
            var store = tx.objectStore('files');
            var range = IDBKeyRange.bound(songId + '/', songId + '/\uffff');
            var cursorRequest = store.openCursor(range);
            cursorRequest.onsuccess = function () {
                var cursor = cursorRequest.result;
                if (cursor) { cursor.delete(); cursor.continue(); }
            };
            tx.oncomplete = function () { db.close(); onDone(); };
            tx.onerror = function () { db.close(); onDone(); };
        };
        request.onerror = function () { onDone(); };
        return '';
    })()"""
)


private fun pickBinaryJs(
    accept: String,
    onPicked: (String, String) -> Unit
): String = js(
    """(function () {
        var input = document.createElement('input');
        input.type = 'file';
        if (accept) input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);
        input.addEventListener('change', function () {
            var file = input.files && input.files[0];
            document.body.removeChild(input);
            if (!file) return;
            var reader = new FileReader();
            reader.onload = function () {
                var dataUrl = String(reader.result || '');
                var comma = dataUrl.indexOf(',');
                onPicked(file.name, comma >= 0 ? dataUrl.substring(comma + 1) : '');
            };
            reader.readAsDataURL(file);
        });
        input.click();
        return '';
    })()"""
)

private fun shareBase64Js(
    fileName: String,
    mimeType: String,
    base64: String,
    title: String,
    onDone: (Boolean) -> Unit
): String = js(
    """(function () {
        if (!window.navigator || !window.navigator.share || !window.navigator.canShare) {
            onDone(false);
            return '';
        }
        var binary = atob(base64);
        var bytes = new Uint8Array(binary.length);
        for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
        var file = new File([bytes], fileName, { type: mimeType || 'application/octet-stream' });
        if (!window.navigator.canShare({ files: [file] })) { onDone(false); return ''; }
        window.navigator.share({ files: [file], title: title }).then(
            function () { onDone(true); },
            function () { onDone(false); }
        );
        return '';
    })()"""
)

private fun writeStoredFileJs(
    key: String,
    fileName: String,
    mimeType: String,
    base64: String,
    onDone: () -> Unit
): String = js(
    """(function () {
        var request = indexedDB.open('canticora-files', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('files'); };
        request.onsuccess = function () {
            var db = request.result;
            var tx = db.transaction('files', 'readwrite');
            tx.objectStore('files').put(
                { name: fileName, mime: mimeType, size: Math.floor(base64.length * 3 / 4), base64: base64 },
                key
            );
            tx.oncomplete = function () { db.close(); onDone(); };
            tx.onerror = function () { db.close(); onDone(); };
        };
        request.onerror = function () { onDone(); };
        return '';
    })()"""
)

private fun clearFileStoreJs(onDone: () -> Unit): String = js(
    """(function () {
        var request = indexedDB.open('canticora-files', 1);
        request.onupgradeneeded = function () { request.result.createObjectStore('files'); };
        request.onsuccess = function () {
            var db = request.result;
            var tx = db.transaction('files', 'readwrite');
            tx.objectStore('files').clear();
            tx.oncomplete = function () { db.close(); onDone(); };
            tx.onerror = function () { db.close(); onDone(); };
        };
        request.onerror = function () { onDone(); };
        return '';
    })()"""
)

private fun readLocalJs(key: String): String = js(
    "(function () { try { return window.localStorage.getItem(key) || ''; } catch (e) { return ''; } })()"
)

private fun writeLocalJs(key: String, value: String): String = js(
    """(function () {
        try {
            if (!value) window.localStorage.removeItem(key);
            else window.localStorage.setItem(key, value);
        } catch (e) { }
        return '';
    })()"""
)
