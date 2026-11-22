package com.grafitto.laudate.data.local

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/**
 * Construye la base de datos de Canticora en el target **JVM de escritorio**.
 *
 * Usa el **driver SQLite empaquetado** (`BundledSQLiteDriver`), de modo que la app de escritorio
 * no depende del SQLite del sistema operativo. Es la equivalencia de lo que `AppContainer` hace en
 * Android con `Room.databaseBuilder(context, …)`. Ver ADR-013.
 *
 * Se registran **todas las migraciones** (las mismas que en Android) para que una instalación de
 * escritorio con una base antigua se actualice sin destruir los datos del usuario. Una instalación
 * nueva crea el esquema en la versión actual. La base vive en la ruta que indique el cascarón
 * (p. ej. en la carpeta de datos del usuario).
 */
fun buildDesktopDatabase(path: String): LaudateDatabase =
    Room.databaseBuilder<LaudateDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .addMigrations(
            LaudateDatabase.MIGRATION_1_2,
            LaudateDatabase.MIGRATION_2_3,
            LaudateDatabase.MIGRATION_3_4,
            LaudateDatabase.MIGRATION_4_5,
            LaudateDatabase.MIGRATION_5_6,
            LaudateDatabase.MIGRATION_6_7,
            LaudateDatabase.MIGRATION_7_8,
            LaudateDatabase.MIGRATION_8_9,
            LaudateDatabase.MIGRATION_9_10,
            LaudateDatabase.MIGRATION_10_11,
            LaudateDatabase.MIGRATION_11_12,
            LaudateDatabase.MIGRATION_12_13,
            LaudateDatabase.MIGRATION_13_14
        )
        .build()
