package de.tododl.shared.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Plattform-spezifische Erzeugung des SQLite-Drivers.
 * - desktop (macOS/Win/Linux): JdbcSqliteDriver mit lokaler Datei
 * - iOS (später): NativeSqliteDriver
 * - Android (später): AndroidSqliteDriver
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(factory: DatabaseDriverFactory): TododlDatabase {
    val driver = factory.createDriver()
    return TododlDatabase(driver)
}
