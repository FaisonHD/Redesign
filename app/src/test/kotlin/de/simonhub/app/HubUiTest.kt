package de.simonhub.app

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.os.Looper
import org.robolectric.Shadows
import androidx.compose.ui.semantics.SemanticsActions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import de.simonhub.core.*
import de.simonhub.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.YearMonth
import java.time.ZoneId

abstract class HubUiFixture {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    protected val repo get()=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].repository
    @Before fun clear() {
        runBlocking {repo.update {HubState()}}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("launcher-home").fetchSemanticsNodes().isNotEmpty()}
    }
    protected fun openToday() {
        compose.onNodeWithTag("open-today-page").performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithTag("today-dashboard").fetchSemanticsNodes().isNotEmpty()}
    }
    protected fun revealNextTask() {
        compose.onNodeWithTag("today-scroll-content").performScrollToNode(hasText("Nächste Aufgabe"))
    }
    protected fun awaitState(condition: ()->Boolean) {
        val deadline=System.nanoTime()+10_000_000_000L
        while(!condition() && System.nanoTime()<deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
        assertTrue("State did not update; error="+androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].error.value,condition())
        compose.waitForIdle()
    }
}
@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubTaskUiTest : HubUiFixture() {
    @Test fun createCompleteAndReloadTask() {
        openToday()
        revealNextTask()
        compose.onAllNodesWithContentDescription("Aufgabe hinzufügen").onFirst().performClick()
        compose.onNodeWithText("Was möchtest du erledigen?").performTextInput("Compilerbau wiederholen")
        compose.onNodeWithText("Aufgabe speichern").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) {it()}
        awaitState {repo.state.value.tasks.size==1}
        val persisted=HubRepository(compose.activity)
        runBlocking {persisted.load()}
        assertEquals("Compilerbau wiederholen",persisted.state.value.tasks.single().title)
        compose.onNodeWithContentDescription("Erledigen").performClick()
        awaitState {repo.state.value.tasks.single().done}
        val completed=HubRepository(compose.activity)
        runBlocking {completed.load()}
        assertTrue(completed.state.value.tasks.single().done)
        compose.waitUntil(5_000) {compose.onAllNodesWithText("Alles erledigt.").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun createTaskWithScrollableReminderTime() {
        openToday()
        revealNextTask()
        compose.onNodeWithContentDescription("Aufgabe hinzufügen").performClick()
        compose.waitUntil(5_000) {runCatching {compose.onNodeWithTag("task-title").assertIsFocused()}.isSuccess}
        compose.onNodeWithTag("task-title").performTextInput("Lernpause planen")
        compose.onNodeWithTag("task-title").assertTextContains("Lernpause planen")
        compose.onNodeWithTag("task-reminder-toggle").assertIsOn()
        compose.onNodeWithTag("task-reminder-date").assertExists()
        compose.onNodeWithText("Stunde").assertExists()
        compose.onNodeWithText("Minute").assertExists()
        compose.waitForIdle()
        val validationErrors=listOf("Bitte einen Titel eingeben.","Bitte eine zukünftige Erinnerungszeit wählen.","Bitte 5 bis 480 Minuten eingeben.")
        compose.onNodeWithText("Aufgabe speichern").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick) {it()}
        val deadline=System.nanoTime()+10_000_000_000L
        fun savedOrRejected()=(compose.onAllNodesWithTag("task-title").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithText("Lernpause planen").fetchSemanticsNodes().isNotEmpty()) || validationErrors.any {compose.onAllNodesWithText(it,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        while(!savedOrRejected() && System.nanoTime()<deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
            Thread.sleep(10)
        }
        compose.waitForIdle()
        val validationError=validationErrors.firstOrNull {compose.onAllNodesWithText(it,useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val taskEditorStillOpen=compose.onAllNodesWithTag("task-title").fetchSemanticsNodes().isNotEmpty()
        assertTrue("Task was not saved; editor still open=$taskEditorStillOpen; validation=${validationError ?: "none"}",savedOrRejected() && !taskEditorStillOpen)
        val savedState=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].state.value
        val saved=savedState.tasks.single {it.title=="Lernpause planen"}
        assertNotNull(saved.reminder)
        val restored=HubJson.decode(HubJson.encode(savedState)).tasks.single {it.title=="Lernpause planen"}
        assertEquals(saved.reminder,restored.reminder)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubGoalUiTest : HubUiFixture() {
    @Test fun goalsTabIsRemovedFromDashboardNavigation() {
        openToday()
        compose.onNodeWithTag("dashboard-navigation").assertExists()
        compose.onNode(hasText("Heute") and hasAnyAncestor(hasTestTag("dashboard-navigation"))).assertExists()
        compose.onNodeWithText("Aufgaben").assertExists()
        compose.onNodeWithText("Mehr").assertDoesNotExist()
        compose.onNodeWithText("Ziele").assertDoesNotExist()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubWidgetUiTest : HubUiFixture() {
    @Test fun widgetLayoutPersists() {
        openToday()
        compose.onNodeWithContentDescription("Dashboard anpassen").performClick()
        compose.onNodeWithContentDescription("Kalender anzeigen").performClick()
        compose.onNodeWithContentDescription("Nächste Aufgabe anzeigen").performClick()
        compose.onNodeWithText("Layout übernehmen").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) {it()}
        awaitState {repo.state.value.settings.widgets.single {it.kind==WidgetKind.AGENDA}.visible.not() && repo.state.value.settings.widgets.single {it.kind==WidgetKind.TASKS}.visible.not()}
        val persisted=HubRepository(compose.activity);runBlocking {persisted.load()}
        assertEquals(repo.state.value.settings.widgets,persisted.state.value.settings.widgets)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubTodayCompactUiTest : HubUiFixture() {
    @Test fun todayKeepsCalendarAndOneTaskSection() {
        openToday()
        compose.onNodeWithText("Monatskalender").assertExists()
        compose.onNodeWithText("TERMINE IM MONAT").assertDoesNotExist()
        revealNextTask()
        compose.onNodeWithText("Nächste Aufgabe").assertExists()
        compose.onNodeWithText("Schritt für Schritt").assertDoesNotExist()
        compose.onNodeWithText("Dein nächster Schritt").assertDoesNotExist()
    }



    @Test fun androidBackClosesDayWindowBeforeLeavingMonth() {
        openToday()
        compose.onNodeWithTag("calendar-day-1",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("day-window",useUnmergedTree=true).assertExists()
        compose.runOnUiThread {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("day-window",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Monatskalender").assertIsDisplayed()
        compose.onNodeWithTag("calendar-day-2",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("back-day-window",useUnmergedTree=true).performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("day-window",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Monatskalender").assertIsDisplayed()
    }

    @Test fun selectingCalendarDayOpensWindowAndCloseKeepsMonth() {
        openToday()
        compose.onNodeWithTag("calendar-day-1",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("day-window",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("day-window-title",useUnmergedTree=true).assertExists()
        compose.onNodeWithTag("close-day-window",useUnmergedTree=true).performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("day-window",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Monatskalender").assertIsDisplayed()
    }

    @Test fun monthGridShowsEveryEventInsideDayCell() {
        openToday()
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java]
        compose.waitUntil(10_000) {model.ready.value && !model.busy.value}
        val month=YearMonth.now();val day=month.atDay(1)
        model.month.value=month;model.date.value=day
        val zone=ZoneId.systemDefault();val start=day.atTime(9,0).atZone(zone).toInstant()
        model.monthEvents.value=listOf("Vorlesung Netze","Training","Projektarbeit","Weiterer Termin vier","Weiterer Termin fünf","Weiterer Termin sechs","Weiterer Termin sieben").mapIndexed {index,title->
            val eventStart=start.plusSeconds(index*3600L)
            HubEvent("calendar-$index",title,eventStart,eventStart.plusSeconds(1800))
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("month-event-1-2",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("month-event-1-0",useUnmergedTree=true).assertTextContains("* Vorlesung Netze")
        compose.onNodeWithTag("month-event-1-1",useUnmergedTree=true).assertTextContains("* Training")
        compose.onNodeWithTag("month-event-1-2",useUnmergedTree=true).assertTextContains("* Projektarbeit")
        compose.onNodeWithTag("month-event-1-6",useUnmergedTree=true).assertIsDisplayed().assertTextContains("* Weiterer Termin sieben")
        compose.onNodeWithText("TERMINE IM MONAT").assertDoesNotExist()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubBackupUiTest : HubUiFixture() {
    @Test fun invalidBackupDoesNotChangeData() {
        runBlocking {repo.saveTask(HubTask(title="Keep me"))}
        try {runBlocking {repo.importBackup("{\"schemaVersion\":999}")};fail("Should reject unknown schema")}catch(expected: IllegalArgumentException) { }
        assertEquals("Keep me",repo.state.value.tasks.single().title)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubPreviewUiTest : HubUiFixture() {
    @Test fun renderDashboardPreview() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java]
        val pm=Shadows.shadowOf(compose.activity.packageManager)
        val launch=android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        listOf("ChatGPT","Instagram","Hevy","Kalender","Spotify","Maps","WhatsApp","Snote").forEachIndexed {index,label->
            val packageId="preview.app"+index
            pm.addResolveInfoForIntent(launch,android.content.pm.ResolveInfo().apply {
                nonLocalizedLabel=label
                activityInfo=android.content.pm.ActivityInfo().apply {
                    packageName=packageId;name=packageId+".MainActivity"
                    applicationInfo=android.content.pm.ApplicationInfo().apply {packageName=packageId}
                }
            })
        }
        model.refreshLauncherApps()
        model.weather.value=HomeWeather("Freiburg",18,21,10,2)
        runBlocking {repo.loadDemo()}
        model.refresh()
        awaitState {repo.state.value.demo && model.events.value.size==1}
        compose.waitUntil(10000) {compose.onAllNodesWithTag("home-today-calendar").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        val folder=java.io.File(requireNotNull(System.getProperty("hub.screenshotDir")));folder.mkdirs()
        fun capture(name:String) = compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            java.io.File(folder,name).outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        compose.waitUntil(10000) {compose.onAllNodesWithTag("app-preview.app0").fetchSemanticsNodes().isNotEmpty()}
        capture("SimonHub_Launcher_Home_Preview.png")
        openToday()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Monatskalender").fetchSemanticsNodes().isNotEmpty()}
        compose.waitForIdle()
        capture("SimonHub_Today_Dashboard_Preview.png")
        compose.onNodeWithText("Aufgaben").performClick()
        compose.waitForIdle()
        capture("LifeHub_Tasks_Rework.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubKeyboardLayoutTest {
    @get:Rule val compose=createComposeRule()
    @Test fun titleDateTimeAndSaveFitAboveTallKeyboardWithLargerFont() {
        compose.setContent {
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,1.25f)) {
                HubTheme(HubSettings()) {
                    Box(Modifier.requiredSize(393.dp,640.dp)) {
                        TaskEditor(null,null,{}, {}, {},windowInsets=WindowInsets(bottom=with(density) {320.dp.roundToPx()}))
                    }
                }
            }
        }
        compose.onNodeWithTag("task-title").assertIsDisplayed()
        compose.onNodeWithTag("task-reminder-date").assertIsDisplayed()
        compose.onNodeWithTag("task-reminder-toggle").assertIsDisplayed()
        compose.onNodeWithText("Stunde").assertIsDisplayed()
        compose.onNodeWithText("Minute").assertIsDisplayed()
        compose.onNodeWithTag("task-save").assertIsDisplayed()
        val save=compose.onNodeWithTag("task-save").getUnclippedBoundsInRoot()
        val overlay=compose.onNodeWithTag("task-editor-overlay").getUnclippedBoundsInRoot()
        assertTrue("Save button overlaps simulated keyboard: $save",save.bottom.value<=overlay.top.value+320f+.5f)
        assertTrue("Save button is not full height",(save.bottom-save.top).value>=47.5f)
        listOf("task-title","task-reminder-date","task-reminder-toggle").forEach {tag->
            val bounds=compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
            assertTrue("$tag overlaps keyboard: $bounds",bounds.bottom.value<=overlay.top.value+320f+.5f)
        }
        compose.onNodeWithTag("task-save").performClick()
        compose.onNodeWithText("Bitte einen Titel eingeben.").assertIsDisplayed()
        val errorSave=compose.onNodeWithTag("task-save").getUnclippedBoundsInRoot()
        assertTrue("Validation pushes Save under keyboard",errorSave.bottom.value<=overlay.top.value+320f+.5f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubCalendarWindowUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun animatedDayWindowShowsFullTitlesTimesAndLocation() {
        val day=LocalDate.of(2026,10,17)
        val selected=mutableStateOf<LocalDate?>(null)
        val start=day.atTime(9,0).atZone(ZoneId.systemDefault()).toInstant()
        val fullTitle="Netztechnik: gemeinsames Übungsprojekt und ausführliche Besprechung"
        val events=listOf(
            HubEvent("meeting",fullTitle,start,start.plusSeconds(5400),calendar="DHBW",location="Raum A105"),
            HubEvent("allday","Geburtstag",day.atStartOfDay(ZoneOffset.UTC).toInstant(),day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),allDay=true)
        )
        var previewView:android.view.View?=null
        compose.setContent {
            previewView=LocalView.current
            HubTheme(HubSettings()) {
                CalendarDayWindow(selected.value,events,Rect(24f,160f,64f,220f)) {selected.value=null}
            }
        }
        compose.onNodeWithTag("day-window",useUnmergedTree=true).assertDoesNotExist()
        compose.runOnIdle {selected.value=day}
        compose.waitForIdle()
        compose.onNodeWithTag("day-event-title-meeting",useUnmergedTree=true).assertTextEquals(fullTitle).assertIsDisplayed()
        compose.onNodeWithTag("day-event-time-meeting",useUnmergedTree=true).assertTextEquals("09:00 – 10:30")
        compose.onNodeWithTag("day-event-time-allday",useUnmergedTree=true).assertTextEquals("Ganztägig")
        compose.onNodeWithText("Raum A105",useUnmergedTree=true).assertIsDisplayed()
        compose.runOnIdle {
            val view=requireNotNull(previewView).rootView
            val bitmap=android.graphics.Bitmap.createBitmap(view.width,view.height,android.graphics.Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            val folder=java.io.File(requireNotNull(System.getProperty("hub.screenshotDir")));folder.mkdirs()
            java.io.File(folder,"LifeHub_Calendar_Day_Window.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        }
        compose.onNodeWithTag("close-day-window",useUnmergedTree=true).performClick()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("day-window",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
    }
}

