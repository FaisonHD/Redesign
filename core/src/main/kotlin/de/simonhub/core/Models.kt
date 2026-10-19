package de.simonhub.core

import java.time.*
import java.util.UUID

enum class Area(val label: String) { STUDY("Studium"), FITNESS("Fitness"), PROJECT("Projekte"), PERSONAL("Persönlich") }
enum class Priority(val label: String, val score: Int) { HIGH("Hoch",3), MEDIUM("Mittel",2), LOW("Niedrig",1) }
data class HubTask(
    val id: String = UUID.randomUUID().toString(), val title: String, val area: Area = Area.PERSONAL,
    val priority: Priority = Priority.MEDIUM, val minutes: Int = 25, val due: LocalDate? = null,
    val reminder: Instant? = null, val done: Boolean = false, val completedAt: Instant? = null,
    val createdAt: Instant = Instant.now(), val source: String? = null, val details: String = ""
)
data class HubEvent(val id: String,val title: String,val start: Instant,val end: Instant,val allDay: Boolean=false,val calendar: String="",val location: String="")
data class HubGoal(val id: String=UUID.randomUUID().toString(),val title: String,val area: Area=Area.PERSONAL,val current: Double=0.0,val target: Double,val unit: String="Einheiten") {
    val fraction: Float get()=(current/target).coerceIn(0.0,1.0).toFloat()
}
data class HubNote(val path: String,val title: String,val content: String,val modifiedAt: Long=0) {
    val excerpt: String get()=content.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }.take(3).joinToString(" ").take(170)
}
enum class WidgetKind(val label: String,val subtitle: String) {
    FOCUS("Tagesfokus","Dein nächster sinnvoller Schritt"), AGENDA("Kalender","Termine und freie Zeit"),
    TASKS("Aufgaben","Was heute zählt"), GOALS("Ziele","Fortschritt sichtbar machen"), NOTES("Second Brain","Wissen aus Obsidian")
}
data class WidgetConfig(val kind: WidgetKind,val visible: Boolean=true,val compact: Boolean=false)
data class HubSettings(
    val name: String="Simon",val theme: String="dark",val accent: String="mono",val startHour: Int=8,val endHour: Int=22,
    val homeAppCount: Int=12,val homeAppPackages: Set<String>?=null,
    val homeAppOrder: List<String> = emptyList(),val homeColumns: Int=4,val homeShowLabels: Boolean=true,
    val homeShowClock: Boolean=true,val homeShowWeather: Boolean=true,val homeShowCalendar: Boolean=true,
    val homeSectionOrder: List<String> = listOf("info","calendar","apps"),
    val calendarEnabled: Boolean=false,val calendarIds: Set<String> = emptySet(),val obsidianTree: String="",val importNoteTasks: Boolean=false,
    val bridgeUrl: String="",val bridgeEnabled: Boolean=false,val widgets: List<WidgetConfig> = listOf(WidgetKind.AGENDA,WidgetKind.TASKS,WidgetKind.FOCUS,WidgetKind.GOALS,WidgetKind.NOTES).map { WidgetConfig(it) }
)
data class PlanBlock(val taskId: String,val title: String,val start: Instant,val end: Instant,val area: Area)
data class DayPlan(val date: LocalDate,val blocks: List<PlanBlock>,val unscheduled: List<HubTask>,val freeMinutes: Int,val explanation: String,val generatedAt: Instant=Instant.now(),val source: String="Lokal")
data class HubState(
    val tasks: List<HubTask> = emptyList(),val goals: List<HubGoal> = emptyList(),val notes: List<HubNote> = emptyList(),val settings: HubSettings=HubSettings(),
    val plan: DayPlan?=null,val bridgeSyncedAt: Instant?=null,val obsidianSyncedAt: Instant?=null,val demo: Boolean=false,
    val processedActions: Set<String> = emptySet(),val pendingActions: List<RemoteAction> = emptyList()
)
data class RemoteAction(val id: String,val type: String,val json: String)

/** Compile-time extension point: contributions are registered by stable, unique ID. */
interface HubPlugin { val id: String; val name: String; val description: String; val widgets: Set<WidgetKind> }
data class BuiltinPlugin(override val id: String,override val name: String,override val description: String,override val widgets: Set<WidgetKind>): HubPlugin
class PluginRegistry(plugins: List<HubPlugin> = builtins) {
    private val entries=plugins.associateBy {it.id}
    init {require(entries.size==plugins.size) {"Duplicate plugin ID"}}
    fun all()=entries.values.toList()
    companion object {
        val builtins=listOf(
            BuiltinPlugin("calendar","Google Calendar","Kalender auf deinem Android-Gerät",setOf(WidgetKind.AGENDA)),
            BuiltinPlugin("tasks","Aufgaben & Fokus","Lokale Aufgaben und Planung",setOf(WidgetKind.TASKS,WidgetKind.FOCUS)),
            BuiltinPlugin("goals","Persönliche Ziele","Studium, Fitness und Projekte",setOf(WidgetKind.GOALS)),
            BuiltinPlugin("obsidian","Obsidian","Freigegebene Markdown-Notizen",setOf(WidgetKind.NOTES)),
            BuiltinPlugin("mcp","ChatGPT · MCP","Tageskontext und überprüfbare Aktionen",emptySet())
        )
    }
}
