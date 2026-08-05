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
        // Beim allerersten Start Schema erzeugen
        if (isNewDatabase) {
            TododlDatabase.Schema.create(driver)
        }
        return driver
    }
}
