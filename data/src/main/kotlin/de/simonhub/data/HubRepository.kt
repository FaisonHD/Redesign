package de.simonhub.data

import android.content.*
import android.database.sqlite.*
import de.simonhub.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.*
import org.json.JSONObject
import java.time.*

private class HubDatabase(context: Context): SQLiteOpenHelper(context,"simon-hub.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) {db.execSQL("CREATE TABLE hub_state (id INTEGER PRIMARY KEY CHECK(id=1), data TEXT NOT NULL)")}
    override fun onUpgrade(db: SQLiteDatabase,oldVersion: Int,newVersion: Int)=Unit
}
/** Atomic snapshots serialize UI, imports and background writes. Failed reads never overwrite user data. */
class HubRepository(context: Context) {
    private val db=HubDatabase(context.applicationContext);private val mutex=Mutex();private var loaded=false
    private val mutable=MutableStateFlow(HubState());val state: StateFlow<HubState> = mutable.asStateFlow()
    private fun ensureLoaded() {
        if(loaded)return
        val raw=db.readableDatabase.rawQuery("SELECT data FROM hub_state WHERE id=1",null).use {if(it.moveToFirst())it.getString(0)else null}
        if(raw!=null) {
            val saved=HubJson.decode(JSONObject(raw))
            val oldDefault=listOf(WidgetKind.FOCUS,WidgetKind.AGENDA,WidgetKind.TASKS,WidgetKind.GOALS,WidgetKind.NOTES).map {WidgetConfig(it)}
            val newDefault=listOf(WidgetKind.AGENDA,WidgetKind.TASKS,WidgetKind.FOCUS,WidgetKind.GOALS,WidgetKind.NOTES).map {WidgetConfig(it)}
            val migrated=saved.copy(settings=saved.settings.copy(
                accent=if(saved.settings.widgets==oldDefault && saved.settings.accent=="sage")"mono"else saved.settings.accent,
                widgets=if(saved.settings.widgets==oldDefault)newDefault else saved.settings.widgets
            ))
            mutable.value=migrated
            if(migrated!=saved)db.writableDatabase.insertWithOnConflict("hub_state",null,ContentValues().apply {put("id",1);put("data",HubJson.encode(migrated).toString())},SQLiteDatabase.CONFLICT_REPLACE)
        }
        loaded=true
    }
    suspend fun load()=withContext(Dispatchers.IO) {mutex.withLock {ensureLoaded()}}
    suspend fun update(transform: (HubState)->HubState)=withContext(Dispatchers.IO) {mutex.withLock {
        ensureLoaded();val next=transform(mutable.value)
        require(next.tasks.size<=10000 && next.goals.size<=1000 && next.notes.size<=200 && next.pendingActions.size<=100) {"Der Import überschreitet das Limit. Maximal 10.000 Aufgaben, 1.000 Ziele und 200 Notizen; bitte einen kleineren Ordner wählen."}
        val database=db.writableDatabase
        database.beginTransaction()
        try {
            val values=ContentValues().apply {put("id",1);put("data",HubJson.encode(next).toString())}
            check(database.insertWithOnConflict("hub_state",null,values,SQLiteDatabase.CONFLICT_REPLACE)>0);database.setTransactionSuccessful()
        } finally {database.endTransaction()}
        mutable.value=next
    }}
    suspend fun saveTask(task: HubTask) {require(HubJson.validTask(task));update {it.copy(tasks=it.tasks.filterNot {t->t.id==task.id}+task,plan=null)}}
    suspend fun toggleTask(id: String)=update {s->s.copy(tasks=s.tasks.map {if(it.id==id)it.copy(done=!it.done,completedAt=if(it.done)null else Instant.now())else it},plan=null)}
    suspend fun deleteTask(id: String)=update {it.copy(tasks=it.tasks.filterNot {t->t.id==id},plan=null)}
    suspend fun saveGoal(g: HubGoal) {require(HubJson.validGoal(g));update {it.copy(goals=it.goals.filterNot {old->old.id==g.id}+g)}}
    suspend fun deleteGoal(id: String)=update {it.copy(goals=it.goals.filterNot {g->g.id==id})}
    suspend fun settings(change: (HubSettings)->HubSettings)=update {it.copy(settings=change(it.settings),plan=null)}
    suspend fun importBackup(text: String) {
        require(text.toByteArray().size<=12*1024*1024) {"Backup ist größer als 12 MB."}
        val next=HubJson.decode(JSONObject(text))
        update {next.copy(settings=next.settings.copy(bridgeEnabled=false,calendarEnabled=false,obsidianTree=""),processedActions=emptySet(),pendingActions=emptyList(),bridgeSyncedAt=null,obsidianSyncedAt=null,plan=null)}
    }
    suspend fun loadDemo()=update {old->
        require(old.tasks.isEmpty() && old.goals.isEmpty() && old.notes.isEmpty()) {"Beispieldaten sind nur in einem leeren Hub möglich."}
        val d=LocalDate.now()
        old.copy(demo=true,tasks=listOf(
            HubTask(id="demo-study",title="Compilerbau · EBNF üben",area=Area.STUDY,priority=Priority.HIGH,minutes=25,due=d),
            HubTask(id="demo-project",title="FuelPredict · nächsten Schritt planen",area=Area.PROJECT,minutes=40,due=d),
            HubTask(id="demo-fitness",title="Training vorbereiten",area=Area.FITNESS,priority=Priority.LOW,minutes=15,due=d)),
            goals=listOf(HubGoal(id="demo-goal",title="Lerneinheiten diese Woche",area=Area.STUDY,current=2.0,target=5.0)),
            notes=listOf(HubNote("Beispiele/Willkommen.md","Dein Second Brain","# Dein Second Brain\n\nDas ist eine Beispielnotiz. Verbinde deinen Obsidian-Ordner für echte Notizen.")))
    }
    suspend fun removeDemo()=update {it.copy(demo=false,tasks=it.tasks.filterNot {t->t.id.startsWith("demo-")},goals=it.goals.filterNot {g->g.id.startsWith("demo-")},notes=it.notes.filterNot {n->n.path.startsWith("Beispiele/")},plan=null)}
}

