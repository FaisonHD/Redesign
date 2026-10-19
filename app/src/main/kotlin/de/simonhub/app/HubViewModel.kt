package de.simonhub.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.*
import androidx.work.*
import de.simonhub.core.*
import de.simonhub.data.*
import de.simonhub.integrations.DeviceCalendar
import de.simonhub.planner.DayPlanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.*
import java.util.concurrent.TimeUnit

data class FocusSession(val taskId: String,val title: String,val end: Instant)
class HubViewModel(application: Application): AndroidViewModel(application) {
    private val app=application as HubApplication
    val repository=app.repository;val state=repository.state
    val date=MutableStateFlow(LocalDate.now());val month=MutableStateFlow(YearMonth.now());val now=MutableStateFlow(Instant.now())
    val events=MutableStateFlow<List<HubEvent>>(emptyList());val monthEvents=MutableStateFlow<List<HubEvent>>(emptyList());val homeEvents=MutableStateFlow<List<HubEvent>>(emptyList())
    val calendars=MutableStateFlow<List<DeviceCalendar>>(emptyList());val busy=MutableStateFlow(false);val ready=MutableStateFlow(false);val error=MutableStateFlow<String?>(null)
    val notices=MutableSharedFlow<String>(extraBufferCapacity=8);val focus=MutableStateFlow<FocusSession?>(null)
    private val prefs=app.getSharedPreferences("focus",0);private val weatherPrefs=app.getSharedPreferences("home_weather",0)
    val weatherCity=MutableStateFlow(weatherPrefs.getString("city","Freiburg") ?: "Freiburg")
    internal val weather=MutableStateFlow(HomeWeather.parse(weatherPrefs.getString("snapshot",null) ?: "")?.takeIf {weatherPrefs.getString("cityQuery",weatherCity.value).equals(weatherCity.value,true)})
    val weatherBusy=MutableStateFlow(false);val weatherError=MutableStateFlow<String?>(null)
    val launcherAppsRevision=MutableStateFlow(0)
    val completedTasks=MutableSharedFlow<HubTask>(extraBufferCapacity=8)
    private var observedDay=LocalDate.now()
    private var refreshJob: Job?=null;private var weatherJob: Job?=null;private var weatherRequestId=0L
    init {
        launch {
            repository.load();ready.value=true
            prefs.getString("end",null)?.let {raw->val at=Instant.parse(raw);if(at>Instant.now())focus.value=FocusSession(prefs.getString("task","") ?: "",prefs.getString("title","Fokus") ?: "Fokus",at)else prefs.edit().clear().apply()}
            Reminders.bridge(app,state.value.settings.bridgeEnabled);refresh();refreshWeather()
        }
        viewModelScope.launch {while(isActive) {now.value=Instant.now();checkDayChange(LocalDate.now());focus.value?.takeIf {it.end<=now.value}?.let {stopFocus();notices.emit("Fokus abgeschlossen. Zeit für eine Pause.")};delay(1000)}}
        viewModelScope.launch {while(isActive) {delay(TimeUnit.MINUTES.toMillis(30));refreshWeather(true)}}
    }
    fun launch(block: suspend ()->Unit) {viewModelScope.launch {try {block()}catch(e: CancellationException) {throw e}catch(e: Exception) {error.value=e.message ?: "Das hat nicht geklappt.";notices.emit(error.value!!)}}}
    fun selectDate(d: LocalDate) {date.value=d;month.value=YearMonth.from(d);refresh()}
    fun selectMonth(m: YearMonth) {month.value=m;if(YearMonth.from(date.value)!=m)date.value=m.atDay(1);refresh()}
    fun refresh(syncBridge: Boolean=false) {
        refreshJob?.cancel();refreshJob=viewModelScope.launch {
            busy.value=true
            try {
                val s=state.value;calendars.value=app.calendar.calendars()
                val selectedDate=date.value;val selectedMonth=month.value;val today=LocalDate.now()
                val first=selectedMonth.atDay(1);val afterLast=selectedMonth.plusMonths(1).atDay(1)
                val monthRows=if(s.settings.calendarEnabled)app.calendar.events(first,afterLast,s.settings.calendarIds)else emptyList()
                monthEvents.value=if(s.demo)demoEvents(selectedDate)else monthRows
                events.value=if(s.demo)demoEvents(selectedDate)else if(s.settings.calendarEnabled)app.calendar.events(selectedDate,s.settings.calendarIds)else emptyList()
                homeEvents.value=if(s.demo)demoEvents(today)else if(!s.settings.calendarEnabled)emptyList()else if(YearMonth.from(today)==selectedMonth)monthRows.filter {eventOccursOn(it,today)}else app.calendar.events(today,s.settings.calendarIds)
                if(s.settings.calendarEnabled && !app.calendar.permitted())notices.emit("Kalenderberechtigung fehlt. Bitte erneut in Mein Hub freigeben.")
                val p=s.plan
                if(p!=null && p.date==date.value && p.blocks.any {b->events.value.any {!it.allDay && b.start<it.end.plusSeconds(300) && b.end>it.start.minusSeconds(300)}})repository.update {it.copy(plan=null)}
                if(syncBridge && s.settings.bridgeEnabled) {
                    val realEvents=if(s.settings.calendarEnabled)app.calendar.events(LocalDate.now(),s.settings.calendarIds)else emptyList()
                    val count=app.bridge.sync(realEvents);notices.emit(if(count==0)"MCP-Bridge synchronisiert."else"$count Vorschläge warten in Mein Hub.")
                }
            }catch(e: CancellationException) {throw e}catch(e: Exception) {notices.emit(e.message ?: "Aktualisierung fehlgeschlagen.")}finally {busy.value=false}
        }
    }
    fun refreshWeather(force: Boolean=false) {
        if(weatherBusy.value && !force) return
        if(!force && weather.value?.let {weatherPrefs.getString("cityQuery",weatherCity.value).equals(weatherCity.value,true) && java.time.Duration.between(it.fetchedAt,Instant.now()).toMinutes()<30}==true) return
        weatherJob?.cancel()
        val requestId=++weatherRequestId
        weatherJob=viewModelScope.launch {
            val requested=weatherCity.value
            weatherBusy.value=true;weatherError.value=null
            try {
                val updated=withContext(Dispatchers.IO) {app.fetchHomeWeather(requested)}
                if(requested==weatherCity.value) {weather.value=updated;weatherPrefs.edit().putString("cityQuery",requested).putString("snapshot",updated.json().toString()).apply()}
            } catch(e: CancellationException) {throw e}catch(e: Exception) {if(requested==weatherCity.value)weatherError.value=e.message ?: "Wetter konnte nicht geladen werden."}
            finally {if(requestId==weatherRequestId)weatherBusy.value=false}
        }
    }
    fun refreshLauncherApps() {launcherAppsRevision.value++}
    fun setWeatherCity(value: String) {
        val clean=value.trim();require(clean.length in 2..80) {"Bitte einen Ort mit mindestens zwei Zeichen eingeben."}
        if(clean.equals(weatherCity.value,true)) {refreshWeather(true);return}
        weatherJob?.cancel();weatherCity.value=clean;weather.value=null;weatherPrefs.edit().putString("city",clean).putString("cityQuery",clean).remove("snapshot").apply();refreshWeather(true)
    }
    fun plan(): DayPlan {val s=state.value;return DayPlanner().plan(date.value,Instant.now(),ZoneId.systemDefault(),s.tasks,events.value,s.settings.startHour,s.settings.endHour)}
    fun savePlan(p: DayPlan)=launch {repository.update {it.copy(plan=p)};notices.emit("Tagesplan gespeichert.")}
    fun saveTask(t: HubTask,onSaved:()->Unit={},onFinished:()->Unit={},onFailed:(String)->Unit={})=launch {
        try {
            repository.saveTask(t)
            runCatching {Reminders.schedule(app,t)}.onFailure {notices.tryEmit("Aufgabe gespeichert. Die Erinnerung konnte nicht eingerichtet werden.")}
            onSaved();notices.emit("Aufgabe gespeichert.")
        } catch(e:CancellationException) {throw e}
          catch(e:Exception) {onFailed(e.message ?: "Speichern fehlgeschlagen. Dein Entwurf bleibt erhalten.");throw e}
        finally {onFinished()}
    }
    fun toggleTask(id: String)=launch {
        val wasDone=state.value.tasks.find {it.id==id}?.done ?: return@launch
        repository.toggleTask(id)
        state.value.tasks.find {it.id==id}?.let {task->
            Reminders.schedule(app,task)
            if(!wasDone && task.done)completedTasks.emit(task)
        }
    }
    fun undoTaskCompletion(completed:HubTask)=launch {
        repository.update {s->s.copy(tasks=s.tasks.map {task->
            if(task.id==completed.id && task.done && task.completedAt==completed.completedAt)task.copy(done=false,completedAt=null)else task
        },plan=null)}
        state.value.tasks.find {it.id==completed.id}?.let {Reminders.schedule(app,it)}
    }
    internal fun checkDayChange(today:LocalDate) {
        if(today==observedDay)return
        val previous=observedDay;observedDay=today
        if(date.value==previous) {
            date.value=today
            if(month.value==YearMonth.from(previous))month.value=YearMonth.from(today)
        }
        refresh();refreshWeather(true)
    }
    fun deleteTask(id: String)=launch {repository.deleteTask(id);WorkManager.getInstance(app).cancelUniqueWork("task-$id")}
    fun saveGoal(g: HubGoal)=launch {repository.saveGoal(g);notices.emit("Ziel gespeichert.")}
    fun deleteGoal(id: String)=launch {repository.deleteGoal(id)}
    fun settings(change: (HubSettings)->HubSettings)=launch {repository.settings(change);refresh()}
    fun connectVault(uri: Uri)=launch {repository.settings {it.copy(obsidianTree=uri.toString())};syncVault()}
    fun syncVault()=launch {
        busy.value=true
        try {
            val settings=state.value.settings;val snap=app.vault.read(settings.obsidianTree,settings.importNoteTasks)
            repository.update {old->
                val previous=old.tasks.associateBy {it.id};val imported=snap.tasks.map {t->previous[t.id]?.let {t.copy(done=it.done || t.done,completedAt=it.completedAt,createdAt=it.createdAt,reminder=it.reminder,priority=it.priority,minutes=it.minutes)} ?: t}
                old.copy(notes=snap.notes,tasks=old.tasks.filterNot {t->imported.any {it.id==t.id}}+imported,obsidianSyncedAt=Instant.now(),plan=null)
            }
            notices.emit("${snap.notes.size} Notizen gelesen${if(snap.limited)" · Importlimit erreicht. Bitte einen kleineren Unterordner wählen."else"."}")
        }finally {busy.value=false}
    }
    fun saveBridge(url: String,token: String,enabled: Boolean)=launch {
        val clean=url.trim().trimEnd('/')
        if(enabled) {
            val u=java.net.URI(clean);require(u.scheme=="https" && !u.host.isNullOrBlank() && u.userInfo==null && u.query==null && u.fragment==null) {"Bitte eine direkte HTTPS-Adresse eingeben."}
            require(token.length>=32 || (token.isBlank() && clean==state.value.settings.bridgeUrl && app.secrets.token().length>=32)) {"Bitte ein App-Token mit mindestens 32 Zeichen eingeben."}
        }
        if(token.isNotBlank())app.secrets.setToken(token)
        repository.update {it.copy(settings=it.settings.copy(bridgeUrl=clean,bridgeEnabled=enabled),bridgeSyncedAt=if(clean==it.settings.bridgeUrl)it.bridgeSyncedAt else null,pendingActions=if(clean==it.settings.bridgeUrl)it.pendingActions else emptyList())}
        Reminders.bridge(app,enabled);notices.emit(if(enabled)"Bridge gespeichert. Jetzt synchronisieren, um sie zu testen."else"MCP-Bridge ausgeschaltet.")
    }
    fun decideAction(id: String,accept: Boolean)=launch {
        val s=state.value.settings
        app.bridge.decide(id,accept,if(s.calendarEnabled)app.calendar.events(LocalDate.now(),s.calendarIds)else emptyList())
        state.value.tasks.forEach {Reminders.schedule(app,it)};notices.emit(if(accept)"Vorschlag übernommen."else"Vorschlag verworfen.")
    }
    fun exportBackup()=HubJson.export(state.value)
    fun restoreBackup(uri: Uri)=launch {
        val text=withContext(Dispatchers.IO) {app.contentResolver.openInputStream(uri)?.use {input->
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val n=input.read(buffer);if(n<0)break;out.write(buffer,0,n);require(out.size()<=12*1024*1024) {"Backup größer als 12 MB."}}
            out.toString("UTF-8")
        } ?: error("Backup ist nicht lesbar.")}
        // Validate before touching any reminders or current data.
        HubJson.decode(org.json.JSONObject(text))
        val previous=state.value.tasks;repository.importBackup(text);app.secrets.setToken("")
        previous.forEach {WorkManager.getInstance(app).cancelUniqueWork("task-${it.id}")};state.value.tasks.forEach {Reminders.schedule(app,it)}
        Reminders.bridge(app,false);refresh();notices.emit("Backup wiederhergestellt. Verbindungen bitte erneut freigeben.")
    }
    fun loadDemo()=launch {repository.loadDemo();refresh();notices.emit("Beispieldaten geladen. Keine echten Termine oder Statistiken.")}
    fun removeDemo()=launch {repository.removeDemo();refresh()}
    fun startFocus(t: HubTask,minutes: Int=t.minutes) {
        val session=FocusSession(t.id,t.title,Instant.now().plusSeconds(minutes*60L));focus.value=session
        prefs.edit().putString("task",session.taskId).putString("title",session.title).putString("end",session.end.toString()).apply()
        WorkManager.getInstance(app).enqueueUniqueWork("focus-timer",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<FocusWorker>().setInitialDelay(minutes.toLong(),TimeUnit.MINUTES).setInputData(workDataOf("end" to session.end.toString(),"title" to session.title)).build())
    }
    fun stopFocus() {focus.value=null;prefs.edit().clear().apply();WorkManager.getInstance(app).cancelUniqueWork("focus-timer")}
    fun shareContext(): String=buildString {
        append("Plane meinen Tag ${date.value} in ${ZoneId.systemDefault()}. Nutze belegte Fristen und vermeide Überschneidungen. Fehlende Infos nicht erfinden.\n\nAufgaben:\n")
        state.value.tasks.filter {!it.done}.take(40).forEach {append("- ${it.title} · ${it.minutes} Min · fällig ${it.due ?: "offen"}\n")}
        append("\nTermine:\n");events.value.forEach {append("- ${it.title}: ${it.start.atZone(ZoneId.systemDefault())} bis ${it.end.atZone(ZoneId.systemDefault())}${if(it.allDay)" (ganztägig)"else""}\n")}
        append("\nWenn Simon Hub als MCP verbunden ist, lies hub_get_today und sende einen Plan mit hub_set_plan. Sonst antworte mit einem Vorschlag.")
    }
    private fun demoEvents(d: LocalDate)=listOf(HubEvent("demo-lecture","DHBW · Compilerbau",d.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant(),d.atTime(12,15).atZone(ZoneId.systemDefault()).toInstant(),calendar="Beispielkalender",location="Karlsruhe"))
    private fun eventOccursOn(event: HubEvent,date: LocalDate): Boolean {
        if(event.allDay)return date>=event.start.atZone(ZoneOffset.UTC).toLocalDate() && date<event.end.atZone(ZoneOffset.UTC).toLocalDate()
        val zone=ZoneId.systemDefault();val start=date.atStartOfDay(zone).toInstant();val end=date.plusDays(1).atStartOfDay(zone).toInstant()
        return event.start<end && event.end>start
    }
}

