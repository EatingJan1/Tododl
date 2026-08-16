package de.tododl.shared.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val appDir = File(System.getProperty("user.home"), ".tododl")
        if (!appDir.exists()) appDir.mkdirs()
        val dbFile = File(appDir, "tododl.db")
        val isNewDatabase = !dbFile.exists()
        val url = "jdbc:sqlite:${dbFile.absolutePath}"
        val driver = JdbcSqliteDriver(url)

        if (isNewDatabase) {
            TododlDatabase.Schema.create(driver)
            driver.setVersion(TododlDatabase.Schema.version)
        } else {
            // Schema-Version mit der DB-Datei abgleichen und fehlende Tabellen/Spalten
            // per SQLDelight-Migration nachziehen, statt bei jeder Schema-Änderung die
            // gesamte lokale DB neu anlegen zu müssen.
            val currentVersion = driver.getVersion()
            val targetVersion = TododlDatabase.Schema.version
            if (currentVersion < targetVersion) {
                TododlDatabase.Schema.migrate(driver, currentVersion, targetVersion)
                driver.setVersion(targetVersion)
            }
        }

        return driver
    }

    private fun SqlDriver.getVersion(): Long =
        executeQuery(null, "PRAGMA user_version", { cursor ->
            app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
        }, 0).value

    private fun SqlDriver.setVersion(version: Long) {
        execute(null, "PRAGMA user_version = $version", 0)
    }
}
