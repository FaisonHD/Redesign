package de.simonhub.app

import de.simonhub.core.HubEvent
import de.simonhub.core.HubTask
import java.time.Instant
import java.time.LocalDate

/** Keep today's remaining events visible even when the morning already filled the list. */
internal fun homeAgenda(events:List<HubEvent>,now:Instant):List<HubEvent> =
    events.distinctBy {listOf(it.title.trim().lowercase(java.util.Locale.ROOT),it.start,it.end,it.allDay)}
        .sortedWith(compareBy<HubEvent> {if(!it.allDay && it.end<=now)1 else 0}.thenBy {it.start})

/** An undated task is never silently assigned to today. Search also includes its notes. */
internal fun matchingTasks(tasks:List<HubTask>,query:String,filter:String,today:LocalDate):List<HubTask> {
    val needle=normalizeAppSearch(query)
    return tasks.filter {task->
        (if(filter=="Erledigt")task.done else !task.done) &&
        (filter!="Heute" || task.due?.let {it<=today}==true) &&
        (needle.isEmpty() || normalizeAppSearch(task.title+" "+task.details).contains(needle))
    }.let {list->
        if(filter=="Erledigt")list.sortedByDescending {it.completedAt ?: it.createdAt}
        else list.sortedWith(compareBy<HubTask> {it.due ?: LocalDate.MAX}.thenBy {it.createdAt})
    }
}

internal fun taskGroup(task:HubTask,today:LocalDate):String {
    val due=task.due
    return when {
        task.done->"Erledigt"
        due==null->"Ohne Datum"
        due<today->"Überfällig"
        due==today->"Heute"
        else->"Demnächst"
    }
}
