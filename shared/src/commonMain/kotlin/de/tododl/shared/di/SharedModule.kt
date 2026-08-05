package de.tododl.shared.di

import de.tododl.shared.db.DatabaseDriverFactory
import de.tododl.shared.db.createDatabase
import de.tododl.shared.remote.HttpClientFactory
import de.tododl.shared.remote.SyncManager
import de.tododl.shared.remote.TododlApiClient
import de.tododl.shared.repository.BereichRepository
import de.tododl.shared.repository.LocalBereichRepository
import de.tododl.shared.repository.LocalMindCardRepository
import de.tododl.shared.repository.LocalNodeRepository
import de.tododl.shared.repository.LocalProjektRepository
import de.tododl.shared.repository.LocalServerConnectionRepository
import de.tododl.shared.repository.LocalTodoItemRepository
import de.tododl.shared.repository.MindCardRepository
import de.tododl.shared.repository.NodeRepository
import de.tododl.shared.repository.ProjektRepository
import de.tododl.shared.repository.ServerConnectionRepository
import de.tododl.shared.repository.TodoItemRepository
import org.koin.dsl.module

/**
 * Gemeinsames Modul für alle Plattformen. Die plattformspezifischen Factories
 * (DB-Driver, HTTP-Client-Engine) werden von außen (Desktop/Android/iOS) reingereicht.
 */
fun sharedModule(driverFactory: DatabaseDriverFactory, httpClientFactory: HttpClientFactory) = module {
    single { createDatabase(driverFactory) }

    single<BereichRepository> { LocalBereichRepository(get()) }
    single<ProjektRepository> { LocalProjektRepository(get()) }
    single<NodeRepository> { LocalNodeRepository(get()) }
    single<TodoItemRepository> { LocalTodoItemRepository(get()) }
    single<MindCardRepository> { LocalMindCardRepository(get()) }
    single<ServerConnectionRepository> { LocalServerConnectionRepository(get()) }

    // --- Projektserver / Sharing (mehrere Server gleichzeitig möglich) ---
    single { httpClientFactory.create() }
    single { TododlApiClient(get()) }
    single { SyncManager(get(), get(), get(), get(), get(), get()) }
}
