package de.simonhub.integrations

import de.simonhub.core.*
import de.simonhub.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*
import org.json.*
import java.net.*
import java.time.*

/** Optional remote MCP companion. No OpenAI API or ChatGPT login is used by the APK. */
class BridgeClient(private val repo: HubRepository,private val secrets: SecretStore) {
    private val mutex=Mutex()
    private fun request(base: String,token: String,path: String,body: JSONObject): JSONObject {
        val uri=URI(base.trim().trimEnd('/'))
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.query==null && uri.fragment==null) {"Eine direkte HTTPS-Adresse ist erforderlich."}
        require(token.length>=32) {"Bitte das Bridge-Token hinterlegen (mindestens 32 Zeichen)."}
        val c=URI(uri.toString()+path).toURL().openConnection() as HttpURLConnection
        c.instanceFollowRedirects=false;c.connectTimeout=10000;c.readTimeout=15000;c.requestMethod="POST";c.doOutput=true
        c.setRequestProperty("Authorization","Bearer $token");c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("Accept","application/json")
        try {
            c.outputStream.use {it.write(body.toString().toByteArray(Charsets.UTF_8))};val status=c.responseCode
            check(status in 200..299) {when(status) {401,403->"Bridge-Zugriff verweigert. Token prüfen.";in 300..399->"Weiterleitung gesperrt. Direkte Bridge-Adresse verwenden.";else->"Bridge antwortet mit HTTP $status."}}
            val bytes=c.inputStream.use {it.bounded(1024*1024)};check(bytes.size<=1024*1024) {"Bridge-Antwort ist zu groß."}
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally {c.disconnect()}
    }
    fun snapshot(s: HubState,events: List<HubEvent>)=JSONObject().apply {
        put("date",LocalDate.now().toString());put("zone",ZoneId.systemDefault().id);put("updatedAt",Instant.now().toString());put("name",s.settings.name);put("demo",s.demo)
        put("tasks",JSONArray(s.tasks.map(HubJson::task)));put("goals",JSONArray(s.goals.map(HubJson::goal)));put("dayStart",s.settings.startHour);put("dayEnd",s.settings.endHour)
        put("events",JSONArray(events.map {JSONObject().put("id",it.id).put("title",it.title).put("start",it.start.toString()).put("end",it.end.toString()).put("allDay",it.allDay).put("calendar",it.calendar)}))
        put("notes",JSONArray(s.notes.take(20).map {JSONObject().put("path",it.path).put("title",it.title).put("excerpt",it.excerpt)}))
        put("plan",s.plan?.let(HubJson::plan));put("pendingActions",JSONArray(s.pendingActions.map {it.id}))
    }
    suspend fun sync(events: List<HubEvent>): Int=withContext(Dispatchers.IO) {mutex.withLock {
        repo.load();val s=repo.state.value;check(s.settings.bridgeEnabled) {"MCP-Bridge ist aus."};val token=secrets.token()
        request(s.settings.bridgeUrl,token,"/v1/state",snapshot(s,events))
        val received=HubJson.objects(request(s.settings.bridgeUrl,token,"/v1/actions",JSONObject()).optJSONArray("actions"))
        val ack=received.map {it.getString("id")};var count=0
        repo.update {old->
            require(old.settings.bridgeEnabled && old.settings.bridgeUrl==s.settings.bridgeUrl) {"Die Verbindung wurde während des Abgleichs geändert. Bitte erneut synchronisieren."}
            val known=old.processedActions+old.pendingActions.map {it.id}
            val additions=received.filter {it.getString("id") !in known}.map {RemoteAction(it.getString("id"),it.getString("type"),it.toString())}
            require(old.pendingActions.size+additions.size<=100) {"Bitte zunächst die ausstehenden Vorschläge prüfen."};count=additions.size
            old.copy(pendingActions=old.pendingActions+additions,bridgeSyncedAt=Instant.now())
        }
        // Acknowledge only after the review queue is durably committed.
        if(ack.isNotEmpty())request(s.settings.bridgeUrl,token,"/v1/ack",JSONObject().put("ids",JSONArray(ack)))
        count
    }}
    suspend fun decide(id: String,accept: Boolean,events: List<HubEvent>)=withContext(Dispatchers.IO) {
        repo.update {s->
            val a=s.pendingActions.find {it.id==id} ?: error("Dieser Vorschlag wurde bereits verarbeitet.")
            val changed=if(accept)apply(s,JSONObject(a.json),events)else s
            changed.copy(pendingActions=changed.pendingActions.filterNot {it.id==id},processedActions=(changed.processedActions+id).toList().takeLast(2000).toSet())
        }
    }
    private fun apply(s: HubState,action: JSONObject,events: List<HubEvent>): HubState {
        val data=action.getJSONObject("data")
        return when(action.getString("type")) {
            "create_task"->{val t=HubJson.parseTask(data);require(HubJson.validTask(t));s.copy(tasks=s.tasks.filterNot {it.id==t.id}+t,plan=null)}
            "complete_task"->{val id=data.getString("id");require(s.tasks.any {it.id==id});s.copy(tasks=s.tasks.map {if(it.id==id)it.copy(done=true,completedAt=Instant.now())else it},plan=null)}
            "set_goal"->{val g=HubJson.parseGoal(data);require(HubJson.validGoal(g));s.copy(goals=s.goals.filterNot {it.id==g.id}+g)}
            "set_plan"->{
                val p=HubJson.parsePlan(data);val zone=ZoneId.systemDefault();val d=LocalDate.now();require(p.date==d) {"Der Plan ist nicht für heute. Bitte neu planen."}
                val start=d.atTime(s.settings.startHour,0).atZone(zone).toInstant();val end=d.plusDays(if(s.settings.endHour==24)1 else 0).atTime(s.settings.endHour%24,0).atZone(zone).toInstant()
                val blocks=p.blocks.sortedBy {it.start};require(blocks.map {it.taskId}.distinct().size==blocks.size)
                require(blocks.all {b->b.start>=start && b.end<=end && b.end>b.start && b.end>Instant.now() && s.tasks.any {it.id==b.taskId && !it.done} && events.none {!it.allDay && b.start<it.end.plusSeconds(300) && b.end>it.start.minusSeconds(300)}}) {"Der Plan kollidiert mit deinen aktuellen Terminen oder Aufgaben."}
                require(blocks.zipWithNext().all {(a,b)->a.end.plusSeconds(300)<=b.start}) {"Fokusfenster brauchen fünf Minuten Puffer."}
                s.copy(plan=p.copy(blocks=blocks,source="ChatGPT · MCP",unscheduled=s.tasks.filter {!it.done && ((it.due ?: d)<=d) && blocks.none {b->b.taskId==it.id}}))
            }
            else->error("Unbekannte Aktion. Bitte die App aktualisieren.")
        }
    }
}
