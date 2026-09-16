package de.tododl.desktop.connectors

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.ui.graphics.vector.ImageVector
import de.tododl.shared.model.ActionRule

/**
 * Ein ActionProvider ist eine Quelle von Ereignissen, die per ActionRule den
 * Status eines Todos ändern können (siehe Models.kt: ActionRule). BASIS -
 * analog zu ConnectorProvider/PanelPlugin:
 *
 *  1. Neue Datei in diesem Paket anlegen, die dieses Interface implementiert.
 *  2. Objekt in ActionProviderRegistry.all eintragen.
 *
 * Beispiele aus der Anforderung:
 *  - Mail: Betreff einer eingehenden Mail == "{{todoTitel}}" (frei bestimmbares
 *    Feld) -> Todo abgeschlossen.
 *  - FTP: Datei in einem Ordner erscheint mit einem Namen, der zum Todo passt
 *    (z.B. "Rechnung_{{todoTitel}}.pdf") -> Todo in Bearbeitung.
 *
 * Die eigentliche Beobachtung (IMAP-Polling, FTP-Ordner-Watch) ist hier
 * bewusst noch NICHT implementiert - nur das Interface plus die Stelle
 * (matches), an der eine ActionRule gegen ein eingetroffenes Ereignis
 * geprüft wird.
 */
interface ActionProvider {
    /** Stabile ID, wird 1:1 als ActionRule.providerId gespeichert. Nie ändern. */
    val id: String

    /** Anzeigename, z.B. "E-Mail", "FTP-Ordner". */
    val displayName: String

    /** Kurzbeschreibung im Regel-Editor. */
    val description: String

    val icon: ImageVector

    /** Welche Ereignis-Felder dieser Provider anbietet, z.B. "Betreff"/"Absender" bei Mail, "Dateiname"/"Pfad" bei FTP. */
    val availableFields: List<String>

    /**
     * Prüft eine einzelne Regel gegen ein eingetroffenes Ereignis (z.B. eine
     * empfangene Mail, eine neue Datei im FTP-Ordner) und gibt true zurück,
     * wenn die Regel zutrifft (der Todo-Status dann entsprechend gesetzt
     * werden soll). [eventFields] enthält die rohen Werte des Ereignisses,
     * [todoTitel] den Titel des zu prüfenden Todos (für den {{todoTitel}}-Platzhalter).
     *
     * Noch ohne echte Anbindung - Standardimplementierung matcht nur den
     * Platzhalter-ersetzten Text 1:1 gegen das passende Feld.
     */
    fun matches(rule: ActionRule, eventFields: Map<String, String>, todoTitel: String): Boolean {
        val expected = rule.matchTemplate.replace("{{todoTitel}}", todoTitel)
        val actual = eventFields[rule.fieldName] ?: return false
        return actual.equals(expected, ignoreCase = true)
    }
}

/**
 * E-Mail-Trigger. TODO: IMAP/Graph-API-Anbindung, die eingehende Mails als
 * eventFields (Betreff, Absender, ...) an matches() übergibt und bei Treffer
 * TodoItemRepository die Statusänderung ausführen lässt.
 */
object MailActionProvider : ActionProvider {
    override val id = "mail"
    override val displayName = "E-Mail"
    override val description = "Todo-Status abhängig von einer eingehenden E-Mail ändern (z.B. per Betreff)."
    override val icon: ImageVector = Icons.Default.Email
    override val availableFields = listOf("Betreff", "Absender")
}

/**
 * FTP-Trigger. TODO: FTP/SFTP-Ordner-Polling, das neue/geänderte Dateien als
 * eventFields (Dateiname, Pfad, ...) an matches() übergibt.
 */
object FtpActionProvider : ActionProvider {
    override val id = "ftp"
    override val displayName = "FTP-Ordner"
    override val description = "Todo-Status abhängig von einer neuen Datei in einem FTP-Ordner ändern (z.B. per Dateiname)."
    override val icon: ImageVector = Icons.Default.Folder
    override val availableFields = listOf("Dateiname", "Pfad")
}

/** Alle verfügbaren Action-Trigger-Typen. Neuen Typ hinzufügen = Objekt hier eintragen. */
object ActionProviderRegistry {
    val all: List<ActionProvider> = listOf(
        MailActionProvider,
        FtpActionProvider
    )

    fun find(providerId: String): ActionProvider? = all.firstOrNull { it.id == providerId }
}
