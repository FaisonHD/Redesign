package de.simonhub.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import de.simonhub.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubReworkUiTest : HubUiFixture() {
    private val model get()=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java]

    @Test fun keyboardDonePersistsTaskAndClosesEditor() {
        openToday()
        revealNextTask()
        compose.onNodeWithContentDescription("Aufgabe hinzufügen").performClick()
        compose.waitUntil(5_000) {runCatching {compose.onNodeWithTag("task-title").assertIsFocused()}.isSuccess}
        compose.onNodeWithTag("task-title").performTextInput("Direkt über die Tastatur")
        compose.onNodeWithTag("task-title").performImeAction()
        awaitState {repo.state.value.tasks.any {it.title=="Direkt über die Tastatur"}}
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("task-editor-overlay").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("today-dashboard").assertIsDisplayed()
    }

    @Test fun todayButtonReturnsFromDifferentMonth() {
        openToday()
        compose.onNodeWithContentDescription("Nächster Monat").performClick()
        awaitState {model.month.value==YearMonth.now().plusMonths(1)}
        compose.onNodeWithTag("calendar-today").performClick()
        awaitState {model.month.value==YearMonth.now() && model.date.value==LocalDate.now()}
    }

    @Test fun searchKeyboardOpensFirstAppAndLongPressAddsItToHome() {
        val packageName="test.rework.instagram"
        val launch=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        Shadows.shadowOf(compose.activity.packageManager).addResolveInfoForIntent(launch,ResolveInfo().apply {
            nonLocalizedLabel="Instagram"
            activityInfo=ActivityInfo().apply {
                this.packageName=packageName;name=packageName+".MainActivity"
                applicationInfo=ApplicationInfo().apply {this.packageName=packageName}
            }
        })
        runBlocking {repo.settings {it.copy(homeAppPackages=emptySet())}}
        model.refreshLauncherApps()
        compose.onNodeWithTag("launcher-home").performTouchInput {swipeLeft()}
        compose.waitUntil(10_000) {runCatching {compose.onNodeWithTag("app-search").assertIsFocused()}.isSuccess}
        compose.onNodeWithTag("app-search").performTextInput("INS")
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("drawer-"+packageName).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("drawer-"+packageName).performTouchInput {longClick()}
        compose.onNodeWithTag("drawer-toggle-home").performClick()
        awaitState {repo.state.value.settings.homeAppPackages?.contains(packageName)==true}
        compose.onNodeWithTag("app-search").performImeAction()
        val intent=Shadows.shadowOf(compose.activity).nextStartedActivity
        assertEquals(packageName,intent.component?.packageName)
    }

    @Test fun completionUndoDoesNotOverwriteLaterChanges() {
        val completed=HubTask(id="undo-test",title="Prüfen",done=true,completedAt=Instant.now())
        runBlocking {repo.saveTask(completed)}
        model.undoTaskCompletion(completed)
        awaitState {repo.state.value.tasks.single().done.not()}
        val newer=completed.copy(title="Inzwischen bearbeitet",completedAt=completed.completedAt!!.plusSeconds(1))
        runBlocking {repo.saveTask(newer)}
        model.undoTaskCompletion(completed)
        compose.waitForIdle()
        assertEquals(newer,repo.state.value.tasks.single())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubSaveFailureUiTest {
    @get:Rule val compose=createComposeRule()

    @Test fun failedSaveKeepsTheDraftVisible() {
        compose.setContent {
            HubTheme(HubSettings()) {
                TaskEditor(null,null,{}, {throw IllegalStateException("Speicher vorübergehend nicht verfügbar")},{})
            }
        }
        compose.onNodeWithTag("task-title").performTextInput("Mein Entwurf bleibt")
        compose.onNodeWithTag("task-title").performImeAction()
        compose.onNodeWithTag("task-title").assertTextContains("Mein Entwurf bleibt")
        compose.onNodeWithText("Speicher vorübergehend nicht verfügbar").assertIsDisplayed()
        compose.onNodeWithTag("task-save").assertIsDisplayed()
    }
}
