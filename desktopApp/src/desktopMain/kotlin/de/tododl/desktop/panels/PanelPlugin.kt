package de.tododl.desktop.panels

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ein Panel-Plugin beschreibt einen anlegbaren Panel-Typ (wie Todoliste,
 * Mindboard, Markdown-Seite). Um einen NEUEN Panel-Typ hinzuzufügen:
 *
 *  1. Eine Datei in diesem Paket anlegen, die dieses Interface implementiert
 *     (siehe TodoListPanelPlugin.kt als einfachstes Beispiel).
 *  2. Das neue Objekt in PanelRegistry.all eintragen.
 *
 * Das war's - "Neues Element"-Dialog, Node-Baum-Icons/Badges, Sidebar und die
 * Navigation zeigen den neuen Typ automatisch an, ohne dass an diesen Stellen
 * Code geändert werden muss.
 *
 * Server-Sync ist NICHT automatisch Teil des Plugins - ein Panel kann rein
 * lokal sein. Soll der Inhalt auch über den Projektserver geteilt werden,
 * braucht es zusätzlich einen Push/Pull-Eintrag in SyncManager (shared-Modul)
 * sowie einen passenden Server-Endpunkt (siehe server/markdown_pages.py als
 * Vorlage für "ein Dokument pro Panel").
 */
interface PanelPlugin {
    /** Stabile ID, wird 1:1 als Node.type in der DB gespeichert. Nie ändern,
     *  sonst werden bestehende Panels "unbekannt". */
    val id: String

    /** Anzeigename im "Neues Element"-Dialog und in Titeln. */
    val displayName: String

    /** Kurzbeschreibung im Auswahl-Dialog. */
    val description: String

    /** Icon für Node-Karte, Sidebar und Badge. */
    val icon: ImageVector

    /** Kurzes Badge-Label auf der Node-Karte, z. B. "TODOLISTE". */
    val badgeLabel: String

    /** Akzentfarbe für Icon/Badge. */
    @Composable
    fun accentColor(): Color = MaterialTheme.colorScheme.primary

    /** Der eigentliche Bildschirm-Inhalt (inkl. eigenem NotionPageHeader). */
    @Composable
    fun Content(panelId: String)
}
