package de.tododl.desktop.connectors

import org.koin.dsl.module

/**
 * Desktop-spezifisches Koin-Modul für Klassen, die auf Compose/Desktop-Typen
 * aufbauen (z.B. ImageVector in ConnectorProvider) und deshalb nicht im
 * plattformunabhängigen sharedModule liegen können.
 *
 * Neuen Connector-Sync-Service hinzugefügt? Hier registrieren.
 */
val desktopConnectorsModule = module {
    single { GitHubSyncService(get(), get(), get()) }
}
