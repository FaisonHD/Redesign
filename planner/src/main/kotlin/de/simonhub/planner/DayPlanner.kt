package de.simonhub.planner

import de.simonhub.core.*
import java.time.*

/** Offline planning, with real zone-aware wall-clock boundaries and five-minute buffers. */
class DayPlanner {
    fun plan(date: LocalDate,now: Instant,zone: ZoneId,tasks: List<HubTask>,events: List<HubEvent>,startHour: Int=8,endHour: Int=22): DayPlan {
        require(startHour in 0..23 && endHour in 1..24 && startHour<endHour)
        val dayStart=date.atTime(startHour,0).atZone(zone).toInstant()
        val dayEnd=if(endHour==24)date.plusDays(1).atStartOfDay(zone).toInstant()else date.atTime(endHour,0).atZone(zone).toInstant()
        val roundedSecond=now.epochSecond + if(now.nano>0)1 else 0
        val earliest=maxOf(dayStart,Instant.ofEpochSecond(((roundedSecond+299)/300)*300))
        val candidates=tasks.filter {!it.done && ((it.due ?: date)<=date)}.sortedWith(
            compareByDescending<HubTask> {(it.due ?: date)<date}.thenBy {it.due ?: LocalDate.MAX}.thenBy {it.createdAt}.thenBy {it.id})
        if(earliest>=dayEnd)return DayPlan(date,emptyList(),candidates,0,"Heute ist kein freies Zeitfenster mehr übrig. Passe deine Tageszeiten an oder plane für morgen.",now)
        val busy=events.filter {!it.allDay && it.end>earliest && it.start<dayEnd && it.end>it.start}
            .map {maxOf(earliest,it.start.minusSeconds(300)) to minOf(dayEnd,it.end.plusSeconds(300))}.sortedBy {it.first}
        val gaps=mutableListOf<Pair<Instant,Instant>>(); var cursor=earliest
        busy.forEach {(start,end)-> if(start>cursor)gaps+=cursor to start; cursor=maxOf(cursor,end)}
        if(cursor<dayEnd)gaps+=cursor to dayEnd
        val available=gaps.sumOf {Duration.between(it.first,it.second).toMinutes().toInt()}
        val blocks=mutableListOf<PlanBlock>();val remaining=candidates.toMutableList()
        for((start,end)in gaps) {
            var pos=start
            while(true) {
                val index=remaining.indexOfFirst {Duration.between(pos,end).toMinutes()>=it.minutes.coerceIn(5,480)}
                if(index<0)break
                val t=remaining.removeAt(index);val finish=pos.plusSeconds(t.minutes.coerceIn(5,480)*60L)
                blocks+=PlanBlock(t.id,t.title,pos,finish,t.area);pos=finish.plusSeconds(300)
            }
        }
        val reason=when {
            candidates.isEmpty()->"Dein Kopf hat Platz. Lege eine Aufgabe an, dann bekommt sie ein passendes Zeitfenster."
            blocks.isEmpty()->"Die freien Zeitfenster sind zu kurz. Teile größere Aufgaben in kleinere Schritte."
            remaining.isNotEmpty()->"${blocks.size} Aufgaben passen in deinen Tag. ${remaining.size} bleiben offen. Überfällige Aufgaben kommen zuerst."
            else->"Deine Aufgaben passen in den Tag. Fünf Minuten Puffer halten Übergänge frei."
        }
        return DayPlan(date,blocks,remaining,available,reason,now)
    }
}
