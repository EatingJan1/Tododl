package de.tododl.desktop.state

import org.koin.core.context.GlobalContext
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier

/**
 * Kleiner Helfer, um Repositories aus dem globalen Koin-Kontext zu holen,
 * ohne die separate koin-compose-Dependency einzubinden. Für den
 * Desktop-Prototyp völlig ausreichend.
 */
inline fun <reified T : Any> koinGet(
    qualifier: Qualifier? = null,
    noinline parameters: ParametersDefinition? = null
): T = GlobalContext.get().get(qualifier, parameters)
