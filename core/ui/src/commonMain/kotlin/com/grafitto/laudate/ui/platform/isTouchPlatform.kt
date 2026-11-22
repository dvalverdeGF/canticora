package com.grafitto.laudate.ui.platform

/**
 * Indica si la plataforma es **táctil** (Android/móvil) y no de escritorio.
 *
 * Lo usa el editor de tablatura para ofrecer un **teclado numérico de traste** en una **hoja flotante**
 * solo en móvil: en MuseScore de escritorio ese teclado es el del **sistema** y no aparece en la barra,
 * así que replicarlo en la barra sería inventar un control que MuseScore no tiene. En tableta, en
 * cambio, hace falta para poder escribir. Es una **desviación deliberada** del ADR-020, documentada en
 * `docs/TABLATURE_UX_PARITY.md`.
 */
expect fun isTouchPlatform(): Boolean
