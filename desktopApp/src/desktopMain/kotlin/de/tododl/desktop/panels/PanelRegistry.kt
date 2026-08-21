package de.tododl.desktop.panels

/**
 * Alle verfügbaren Panel-Typen. Neuen Typ hinzufügen = neue PanelPlugin-Datei
 * schreiben + hier eintragen. Sonst nichts.
 */
object PanelRegistry {
    val all: List<PanelPlugin> = listOf(
        TodoListPanelPlugin,
        MindboardPanelPlugin,
        MarkdownPanelPlugin
    )

    fun find(panelTypeId: String): PanelPlugin? = all.firstOrNull { it.id == panelTypeId }
}
