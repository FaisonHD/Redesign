package de.simonhub.data

import de.simonhub.core.*
import org.json.*
import java.time.*

object HubJson {
    private fun JSONObject.text(key: String)=optString(key).takeIf {it.isNotBlank() && it!="null"}
    fun objects(a: JSONArray?): List<JSONObject> = a?.let {(0 until it.length()).map(it::getJSONObject)} ?: emptyList()
    fun task(t: HubTask)=JSONObject().apply {
        put("id",t.id);put("title",t.title);put("area",t.area.name);put("priority",t.priority.name);put("minutes",t.minutes)
        put("due",t.due?.toString());put("reminder",t.reminder?.toString());put("done",t.done);put("completedAt",t.completedAt?.toString())
        put("createdAt",t.createdAt.toString());put("source",t.source);put("details",t.details)
    }
    fun parseTask(j: JSONObject)=HubTask(
        id=j.getString("id"),title=j.getString("title"),area=Area.valueOf(j.optString("area","PERSONAL")),priority=Priority.valueOf(j.optString("priority","MEDIUM")),
        minutes=j.optInt("minutes",25),due=j.text("due")?.let(LocalDate::parse),reminder=j.text("reminder")?.let(Instant::parse),done=j.optBoolean("done"),
        completedAt=j.text("completedAt")?.let(Instant::parse),createdAt=j.text("createdAt")?.let(Instant::parse) ?: Instant.now(),source=j.text("source"),details=j.optString("details")
    )
    fun goal(g: HubGoal)=JSONObject().put("id",g.id).put("title",g.title).put("area",g.area.name).put("current",g.current).put("target",g.target).put("unit",g.unit)
    fun parseGoal(j: JSONObject)=HubGoal(j.getString("id"),j.getString("title"),Area.valueOf(j.optString("area","PERSONAL")),j.getDouble("current"),j.getDouble("target"),j.optString("unit","Einheiten"))
    fun settings(s: HubSettings)=JSONObject().apply {
        put("name",s.name);put("theme",s.theme);put("accent",s.accent);put("startHour",s.startHour);put("endHour",s.endHour);put("homeAppCount",s.homeAppCount)
        put("homeAppPackages",s.homeAppPackages?.let {JSONArray(it.toList())})
        put("homeAppOrder",JSONArray(s.homeAppOrder));put("homeColumns",s.homeColumns);put("homeShowLabels",s.homeShowLabels)
        put("homeShowClock",s.homeShowClock);put("homeShowWeather",s.homeShowWeather);put("homeShowCalendar",s.homeShowCalendar)
        put("homeSectionOrder",JSONArray(s.homeSectionOrder))
        put("calendarEnabled",s.calendarEnabled);put("calendarIds",JSONArray(s.calendarIds.toList()));put("obsidianTree",s.obsidianTree);put("importNoteTasks",s.importNoteTasks)
        put("bridgeUrl",s.bridgeUrl);put("bridgeEnabled",s.bridgeEnabled)
        put("widgets",JSONArray(s.widgets.map {JSONObject().put("kind",it.kind.name).put("visible",it.visible).put("compact",it.compact)}))
    }
    fun parseSettings(j: JSONObject): HubSettings {
        val saved=objects(j.optJSONArray("widgets")).map {WidgetConfig(WidgetKind.valueOf(it.getString("kind")),it.optBoolean("visible",true),it.optBoolean("compact"))}
        val preferredOrder=listOf(WidgetKind.AGENDA,WidgetKind.TASKS,WidgetKind.FOCUS,WidgetKind.GOALS,WidgetKind.NOTES)
        return HubSettings(name=j.optString("name","Simon"),theme=j.optString("theme","dark"),accent=j.optString("accent","mono"),startHour=j.optInt("startHour",8),endHour=j.optInt("endHour",22),homeAppCount=j.optInt("homeAppCount",12).coerceIn(4,64),
            homeAppPackages=j.optJSONArray("homeAppPackages")?.let {a->(0 until a.length()).map(a::getString).toSet()},
            homeAppOrder=j.optJSONArray("homeAppOrder")?.let {a->(0 until a.length()).map(a::getString).distinct().take(1000)} ?: emptyList(),
            homeColumns=j.optInt("homeColumns",4).coerceIn(3,5),homeShowLabels=j.optBoolean("homeShowLabels",true),
            homeShowClock=j.optBoolean("homeShowClock",true),homeShowWeather=j.optBoolean("homeShowWeather",true),homeShowCalendar=j.optBoolean("homeShowCalendar",true),
            homeSectionOrder=(j.optJSONArray("homeSectionOrder")?.let {a->(0 until a.length()).map(a::getString)} ?: emptyList()).filter {it in listOf("info","calendar","apps")}.distinct().let {it+listOf("info","calendar","apps").filterNot(it::contains)},
            calendarEnabled=j.optBoolean("calendarEnabled"),calendarIds=j.optJSONArray("calendarIds")?.let {a->(0 until a.length()).map(a::getString).toSet()} ?: emptySet(),
            obsidianTree=j.optString("obsidianTree"),importNoteTasks=j.optBoolean("importNoteTasks"),bridgeUrl=j.optString("bridgeUrl"),bridgeEnabled=j.optBoolean("bridgeEnabled"),
            widgets=saved+preferredOrder.filter {kind->saved.none {it.kind==kind}}.map {WidgetConfig(it)})
    }
    fun plan(p: DayPlan)=JSONObject().apply {
        put("date",p.date.toString());put("freeMinutes",p.freeMinutes);put("explanation",p.explanation);put("generatedAt",p.generatedAt.toString());put("source",p.source)
        put("blocks",JSONArray(p.blocks.map {JSONObject().put("taskId",it.taskId).put("title",it.title).put("start",it.start.toString()).put("end",it.end.toString()).put("area",it.area.name)}))
        put("unscheduled",JSONArray(p.unscheduled.map(::task)))
    }
    fun parsePlan(j: JSONObject)=DayPlan(LocalDate.parse(j.getString("date")),objects(j.optJSONArray("blocks")).map {
        PlanBlock(it.getString("taskId"),it.getString("title"),Instant.parse(it.getString("start")),Instant.parse(it.getString("end")),Area.valueOf(it.getString("area")))
    },objects(j.optJSONArray("unscheduled")).map(::parseTask),j.optInt("freeMinutes"),j.optString("explanation"),j.text("generatedAt")?.let(Instant::parse) ?: Instant.now(),j.optString("source","Lokal"))
    fun encode(s: HubState)=JSONObject().apply {
        put("schemaVersion",1);put("tasks",JSONArray(s.tasks.map(::task)));put("goals",JSONArray(s.goals.map(::goal)));put("settings",settings(s.settings))
        put("notes",JSONArray(s.notes.map {JSONObject().put("path",it.path).put("title",it.title).put("content",it.content).put("modifiedAt",it.modifiedAt)}))
        put("plan",s.plan?.let(::plan));put("bridgeSyncedAt",s.bridgeSyncedAt?.toString());put("obsidianSyncedAt",s.obsidianSyncedAt?.toString());put("demo",s.demo)
        put("processedActions",JSONArray(s.processedActions.toList().takeLast(2000)))
        put("pendingActions",JSONArray(s.pendingActions.map {JSONObject().put("id",it.id).put("type",it.type).put("json",it.json)}))
    }
    fun decode(j: JSONObject): HubState {
        require(j.getInt("schemaVersion")==1) {"Dieses Backup benötigt eine neuere App-Version."}
        val s=HubState(tasks=objects(j.optJSONArray("tasks")).map(::parseTask),goals=objects(j.optJSONArray("goals")).map(::parseGoal),settings=parseSettings(j.getJSONObject("settings")),
            notes=objects(j.optJSONArray("notes")).map {HubNote(it.getString("path"),it.getString("title"),it.getString("content"),it.optLong("modifiedAt"))},plan=j.optJSONObject("plan")?.let(::parsePlan),
            bridgeSyncedAt=j.text("bridgeSyncedAt")?.let(Instant::parse),obsidianSyncedAt=j.text("obsidianSyncedAt")?.let(Instant::parse),demo=j.optBoolean("demo"),
            processedActions=j.optJSONArray("processedActions")?.let {a->(0 until a.length()).map(a::getString).toSet()} ?: emptySet(),
            pendingActions=objects(j.optJSONArray("pendingActions")).map {RemoteAction(it.getString("id"),it.getString("type"),it.getString("json"))})
        require(s.tasks.size<=10000 && s.goals.size<=1000 && s.notes.size<=200 && s.pendingActions.size<=100)
        require(s.tasks.map {it.id}.distinct().size==s.tasks.size && s.goals.map {it.id}.distinct().size==s.goals.size)
        require(s.tasks.all {validTask(it)} && s.goals.all {validGoal(it)}) {"Backup enthält ungültige Aufgaben oder Ziele."}
        require(s.settings.name.isNotBlank() && s.settings.name.length<=40 && s.settings.startHour in 0..23 && s.settings.endHour in 1..24 && s.settings.startHour<s.settings.endHour && s.settings.homeAppCount in 4..64 && (s.settings.homeAppPackages?.size ?: 0)<=1000)
        require(s.settings.widgets.map {it.kind}.distinct().size==s.settings.widgets.size)
        return s
    }
    fun validTask(t: HubTask)=t.id.isNotBlank() && t.title.isNotBlank() && t.title.length<=240 && t.minutes in 5..480 && t.details.length<=4000
    fun validGoal(g: HubGoal)=g.id.isNotBlank() && g.title.isNotBlank() && g.title.length<=120 && g.unit.length<=30 && g.target.isFinite() && g.target>0 && g.current.isFinite() && g.current>=0
    fun export(s: HubState)=encode(s.copy(settings=s.settings.copy(bridgeEnabled=false),processedActions=emptySet(),pendingActions=emptyList())).toString(2)
}

