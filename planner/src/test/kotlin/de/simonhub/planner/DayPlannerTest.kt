package de.simonhub.planner

import de.simonhub.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import kotlin.random.Random

class DayPlannerTest {
    private val date=LocalDate.of(2026,10,15);private val zone=ZoneId.of("Europe/Berlin");private val planner=DayPlanner()
    private fun at(hour: Int,minute: Int=0)=date.atTime(hour,minute).atZone(zone).toInstant()
    private fun task(id: String="t",minutes: Int=25,priority: Priority=Priority.MEDIUM,due: LocalDate?=date)=HubTask(id=id,title=id,minutes=minutes,priority=priority,due=due,createdAt=Instant.EPOCH)
    private fun event(start: Instant,end: Instant,allDay: Boolean=false)=HubEvent("e","Vorlesung",start,end,allDay)
    @Test fun respectsAppointmentsAndBuffers() {
        val events=listOf(event(at(9),at(12)))
        val p=planner.plan(date,at(8),zone,listOf(task(minutes=60)),events)
        assertEquals(at(12,5),p.blocks.single().start)
    }
    @Test fun overdueComesBeforeNewerTasks() {
        val p=planner.plan(date,at(8),zone,listOf(task("high",priority=Priority.HIGH),task("overdue",priority=Priority.LOW,due=date.minusDays(1))),emptyList())
        assertEquals("overdue",p.blocks.first().taskId)
    }
    @Test fun excludesCompletedAndFutureTasks() {
        val p=planner.plan(date,at(8),zone,listOf(task("done").copy(done=true),task("future",due=date.plusDays(1)),task("open",due=null)),emptyList())
        assertEquals(listOf("open"),p.blocks.map {it.taskId})
    }
    @Test fun shortTasksUseGapsThatLongTasksCannotUse() {
        val p=planner.plan(date,at(8),zone,listOf(task("long",60,Priority.HIGH),task("short",15)),listOf(event(at(8,25),at(22))),8,22)
        assertEquals(listOf("short"),p.blocks.map {it.taskId});assertEquals("long",p.unscheduled.single().id)
    }
    @Test fun roundsUpWithoutStartingInThePast() {
        val now=at(10,2).plusSeconds(37);val p=planner.plan(date,now,zone,listOf(task()),emptyList())
        assertEquals(at(10,5),p.blocks.single().start)
    }
    @Test fun fractionalMinuteBoundaryIsRoundedForward() {
        val p=planner.plan(date,at(10).plusNanos(1),zone,listOf(task()),emptyList())
        assertEquals(at(10,5),p.blocks.single().start)
    }
    @Test fun handlesEndOfDay() {
        val p=planner.plan(date,at(23),zone,listOf(task()),emptyList())
        assertTrue(p.blocks.isEmpty());assertEquals(0,p.freeMinutes);assertEquals(1,p.unscheduled.size)
    }
    @Test fun handlesOverlappingAndMultidayEvents() {
        val p=planner.plan(date,at(8),zone,listOf(task()),listOf(event(at(8).minusSeconds(86400),at(10)),event(at(9),at(12))))
        assertEquals(at(12,5),p.blocks.single().start)
    }
    @Test fun allDayEventsRemainInformational() {
        val p=planner.plan(date,at(8),zone,listOf(task()),listOf(event(at(0),date.plusDays(1).atStartOfDay(zone).toInstant(),true)))
        assertEquals(at(8),p.blocks.single().start)
    }
    @Test fun springDstKeepsConfiguredWallClockHour() {
        val d=LocalDate.of(2026,3,29);val p=planner.plan(d,d.atStartOfDay(zone).toInstant(),zone,listOf(task(due=d)),emptyList(),8,22)
        assertEquals(8,p.blocks.single().start.atZone(zone).hour)
    }
    @Test fun autumnDstKeepsConfiguredWallClockHour() {
        val d=LocalDate.of(2026,10,25);val p=planner.plan(d,d.atStartOfDay(zone).toInstant(),zone,listOf(task(due=d)),emptyList(),8,22)
        assertEquals(8,p.blocks.single().start.atZone(zone).hour)
    }
    @Test fun midnightEndIsNextDay() {
        val p=planner.plan(date,at(23),zone,listOf(task(minutes=60)),emptyList(),8,24)
        assertEquals(date.plusDays(1).atStartOfDay(zone).toInstant(),p.blocks.single().end)
    }
    @Test fun randomSchedulesNeverOverlapOrLoseEligibleTasks() {
        val random=Random(42)
        repeat(150) {
            val events=(0 until random.nextInt(0,8)).map {val start=at(8).plusSeconds(random.nextLong(0,700)*60);event(start,start.plusSeconds(random.nextLong(5,120)*60))}
            val tasks=(0..random.nextInt(1,20)).map {task("t$it",random.nextInt(5,180))}
            val p=planner.plan(date,at(8),zone,tasks,events)
            assertEquals(tasks.size,p.blocks.size+p.unscheduled.size)
            assertEquals(p.blocks.size,p.blocks.map {it.taskId}.distinct().size)
            p.blocks.forEach {b->assertTrue(b.start>=at(8));assertTrue(b.end<=at(22));assertTrue(events.none {b.start<it.end.plusSeconds(300) && b.end>it.start.minusSeconds(300)})}
            assertTrue(p.blocks.sortedBy {it.start}.zipWithNext().all {(a,b)->a.end.plusSeconds(300)<=b.start})
        }
    }
}
