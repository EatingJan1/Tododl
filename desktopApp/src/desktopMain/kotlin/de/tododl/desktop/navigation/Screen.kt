package de.tododl.desktop.navigation

/**
 * Einfache, state-basierte Navigation (kein extra Navigations-Framework nötig
 * für den Desktop-Prototyp). Jeder Screen kennt genug Kontext (IDs), um sich
 * selbst aus dem Repository zu laden.
 */
sealed interface Screen {
    data object BereichListe : Screen
    data object ServerLogin : Screen
    data class Gruppen(val connectionId: String, val connectionName: String) : Screen
    data class ProjektListe(val bereichId: String, val bereichTitel: String) : Screen
    data class NodeBaum(
        val projectId: String,
        val projektTitel: String,
        val ordnerId: String? = null, // null = Projekt-Root
        val ordnerTitel: String? = null
    ) : Screen
    // Ein einziger Panel-Screen für ALLE Panel-Typen (auch neue Plugins) - welcher
    // Inhalt gerendert wird, entscheidet PanelRegistry.find(panelTypeId) anhand
    // der Node.type-ID. Kein neuer Screen-Fall mehr nötig für neue Panel-Typen.
    data class Panel(val panelId: String, val panelTitel: String, val panelTypeId: String) : Screen
}
