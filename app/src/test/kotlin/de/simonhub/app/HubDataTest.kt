package de.simonhub.app

import androidx.test.core.app.ApplicationProvider
import de.simonhub.core.*
import de.simonhub.data.*
import de.simonhub.integrations.BridgeClient
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35])
class HubDataTest {
    private lateinit var app: HubApplication
    private lateinit var repo: HubRepository
    @Before fun setup() {app=ApplicationProvider.getApplicationContext();repo=app.repository;runBlocking {repo.update {HubState()}}}

    @Test fun uiTestWeatherUsesOnlySyntheticData() {
        assertTrue(app is OfflineHubApplication)
        val weather=app.fetchHomeWeather("This value must never leave the test process")
        assertEquals("Beispielstadt",weather.city)
        assertEquals(18,weather.temperature)
    }
    @Test fun homeLayoutRoundTripAndLegacyDefaults() {
        val settings=HubSettings(homeColumns=3,homeShowClock=false,homeShowWeather=false,homeShowCalendar=false,homeShowLabels=false,homeAppOrder=listOf("b","a"),homeSectionOrder=listOf("apps","calendar","info"))
        assertEquals(settings,HubJson.parseSettings(HubJson.settings(settings)))
        val old=HubJson.parseSettings(JSONObject())
        assertEquals(4,old.homeColumns)
        assertTrue(old.homeShowClock && old.homeShowWeather && old.homeShowCalendar && old.homeShowLabels)
        assertEquals(listOf("info","calendar","apps"),old.homeSectionOrder)
        val invalid=HubJson.parseSettings(JSONObject().put("homeColumns",100).put("homeSectionOrder",org.json.JSONArray(listOf("apps","apps","unknown"))))
        assertEquals(5,invalid.homeColumns)
        assertEquals(listOf("apps","info","calendar"),invalid.homeSectionOrder)
    }
    @Test fun chosenSageAccentSurvivesRepositoryReload()=runBlocking {
        repo.settings {it.copy(accent="sage",homeAppCount=48)}
        val reloaded=HubRepository(app);reloaded.load()
        assertEquals("sage",reloaded.state.value.settings.accent)
        assertEquals(48,reloaded.state.value.settings.homeAppCount)
        repo.importBackup(HubJson.export(reloaded.state.value))
        assertEquals(48,repo.state.value.settings.homeAppCount)
    }
    @Test fun acceptedRemoteTaskAndMarkerPersistTogether()=runBlocking {
        val task=HubTask(id="remote-task",title="Neue Aufgabe")
        val json=JSONObject().put("id","a1").put("type","create_task").put("data",HubJson.task(task)).toString()
        repo.update {it.copy(pendingActions=listOf(RemoteAction("a1","create_task",json)))}
        val bridge=BridgeClient(repo,SecretStore(app));bridge.decide("a1",true,emptyList())
        val copy=HubRepository(app);copy.load()
        assertEquals("remote-task",copy.state.value.tasks.single().id)
        assertTrue("a1" in copy.state.value.processedActions)
        assertTrue(copy.state.value.pendingActions.isEmpty())
        try {bridge.decide("a1",true,emptyList());fail("Duplicate decision should fail")}catch(expected: IllegalStateException) { }
        assertEquals(1,repo.state.value.tasks.size)
    }
    @Test fun conflictingRemotePlanIsRejectedWithoutLosingProposal()=runBlocking {
        val now=Instant.now();val d=LocalDate.now();val task=HubTask(id="t",title="Lernen")
        val block=PlanBlock(task.id,task.title,now.plusSeconds(60),now.plusSeconds(600),task.area)
        val plan=DayPlan(d,listOf(block),emptyList(),60,"Test",source="ChatGPT · MCP")
        val json=JSONObject().put("id","p1").put("type","set_plan").put("data",HubJson.plan(plan)).toString()
        repo.update {it.copy(tasks=listOf(task),settings=it.settings.copy(startHour=0,endHour=24),pendingActions=listOf(RemoteAction("p1","set_plan",json)))}
        val event=HubEvent("e","Vorlesung",block.start,block.end)
        try {BridgeClient(repo,SecretStore(app)).decide("p1",true,listOf(event));fail("Conflict should fail")}catch(expected: IllegalArgumentException) { }
        assertNull(repo.state.value.plan);assertEquals(1,repo.state.value.pendingActions.size);assertTrue(repo.state.value.processedActions.isEmpty())
    }
    @Test fun backupRoundTripPreservesTasksAndDisablesConnections()=runBlocking {
        val task=HubTask(title="Behalten",due=LocalDate.now(),reminder=Instant.now().plusSeconds(3600))
        repo.saveTask(task)
        repo.settings {it.copy(calendarEnabled=true,bridgeEnabled=true,obsidianTree="content://test/vault")}
        val backup=HubJson.export(repo.state.value);repo.importBackup(backup)
        assertEquals(task,repo.state.value.tasks.single())
        assertFalse(repo.state.value.settings.calendarEnabled);assertFalse(repo.state.value.settings.bridgeEnabled);assertEquals("",repo.state.value.settings.obsidianTree)
    }
    @Test fun homeAppCountIsStoredAndOlderSettingsKeepTheDefault()=runBlocking {
        val selected=setOf("com.example.social","com.example.finance")
        repo.settings {it.copy(homeAppCount=20,homeAppPackages=selected)}
        val backup=HubJson.export(repo.state.value)
        repo.importBackup(backup)
        assertEquals(20,repo.state.value.settings.homeAppCount)
        assertEquals(selected,repo.state.value.settings.homeAppPackages)
        assertEquals(12,HubJson.parseSettings(JSONObject()).homeAppCount)
        assertNull(HubJson.parseSettings(JSONObject()).homeAppPackages)
        assertEquals(64,HubJson.parseSettings(JSONObject().put("homeAppCount",99)).homeAppCount)
    }
    @Test fun oldDefaultDashboardMigratesToScreenshotLayout()=runBlocking {
        val oldWidgets=listOf(WidgetKind.FOCUS,WidgetKind.AGENDA,WidgetKind.TASKS,WidgetKind.GOALS,WidgetKind.NOTES).map {WidgetConfig(it)}
        repo.update {it.copy(settings=HubSettings(accent="sage",widgets=oldWidgets))}
        val upgraded=HubRepository(app);upgraded.load()
        assertEquals("mono",upgraded.state.value.settings.accent)
        assertEquals(WidgetKind.AGENDA,upgraded.state.value.settings.widgets.first().kind)
        val afterRestart=HubRepository(app);afterRestart.load()
        assertEquals(upgraded.state.value.settings,afterRestart.state.value.settings)
    }
}

