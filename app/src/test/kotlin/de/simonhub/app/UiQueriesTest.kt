package de.simonhub.app

import de.simonhub.core.HubEvent
import de.simonhub.core.HubTask
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class UiQueriesTest {
    private val today=LocalDate.of(2026,10,19)

    @Test fun todayIncludesOverdueButNeverUndatedOrFutureTasks() {
        val tasks=listOf(
            HubTask(id="undated",title="Ohne Datum"),
            HubTask(id="late",title="Überfällig",due=today.minusDays(1)),
            HubTask(id="today",title="Heute",due=today),
            HubTask(id="tomorrow",title="Morgen",due=today.plusDays(1)),
            HubTask(id="done",title="Fertig",due=today,done=true)
        )
        assertEquals(listOf("late","today"),matchingTasks(tasks,"","Heute",today).map {it.id})
        assertEquals("Ohne Datum",taskGroup(tasks[0],today))
        assertEquals("Demnächst",taskGroup(tasks[3],today))
    }

    @Test fun searchFindsNotesAndIgnoresCaseAccentsAndGermanEszett() {
        val task=HubTask(title="Übungsblatt",details="Straße und Netzwerk")
        listOf("UBUNGSBLATT","STRASSE","netz werk").forEach {query->
            assertEquals(query,listOf(task),matchingTasks(listOf(task),query,"Offen",today))
        }
        assertTrue(matchingTasks(listOf(task),"Training","Offen",today).isEmpty())
    }

    @Test fun completedTasksUseCompletionOrder() {
        val first=HubTask(title="Erste",done=true,completedAt=Instant.parse("2026-10-19T08:00:00Z"))
        val last=HubTask(title="Letzte",done=true,completedAt=Instant.parse("2026-10-19T09:00:00Z"))
        assertEquals(listOf(last,first),matchingTasks(listOf(first,last),"","Erledigt",today))
    }

    @Test fun upcomingAndRunningEventsStayVisibleAfterBusyMorning() {
        val now=Instant.parse("2026-10-19T12:00:00Z")
        val past=(1..5).map {n->HubEvent("past"+n,"Früher "+n,now.minusSeconds(n*7200L),now.minusSeconds(n*7200L-1800))}
        val running=HubEvent("running","Läuft",now.minusSeconds(1800),now.plusSeconds(1800))
        val next=HubEvent("next","Nächster",now.plusSeconds(3600),now.plusSeconds(5400))
        val duplicate=next.copy(id="other-calendar")
        val agenda=homeAgenda(past+next+running+duplicate,now)
        assertEquals(listOf(running,next),agenda.take(2))
        assertEquals(7,agenda.size)
        assertTrue(agenda.take(4).contains(next))
    }
}
