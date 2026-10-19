package de.simonhub.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.simonhub.core.*
import de.simonhub.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubInstrumentedTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val repo get()=compose.activity.hub.repository
    @Before fun clear() {runBlocking {repo.update {HubState()}};compose.waitUntil(10000) {repo.state.value.tasks.isEmpty()};compose.onNodeWithText("simon hub").assertExists()}

    @Test fun createCompleteAndReloadTask() {
        compose.onNodeWithContentDescription("Aufgabe hinzufügen").performClick()
        compose.onNodeWithText("Was möchtest du erledigen?").performTextInput("Compilerbau wiederholen")
        compose.onNodeWithText("Aufgabe speichern").performScrollTo().performClick()
        compose.waitUntil(5000) {repo.state.value.tasks.size==1}
        val persisted=HubRepository(compose.activity)
        runBlocking {persisted.load()}
        assertEquals("Compilerbau wiederholen",persisted.state.value.tasks.single().title)
        compose.onNodeWithText("Aufgaben",useUnmergedTree=false).performClick()
        compose.onNodeWithContentDescription("Erledigen").performClick()
        compose.waitUntil(5000) {repo.state.value.tasks.single().done}
        compose.onNodeWithText("Erledigt").performClick()
        compose.onNodeWithText("Compilerbau wiederholen").assertExists()
    }
    @Test fun goalProgressIsSaved() {
        compose.onNodeWithText("Ziele").performClick()
        compose.onNodeWithContentDescription("Ziel hinzufügen").performClick()
        compose.onNodeWithText("Ziel",substring=false).performTextInput("Lernwoche")
        compose.onNodeWithText("Aktuell").performTextReplacement("2")
        compose.onNodeWithText("Zielwert").performTextReplacement("5")
        compose.onNodeWithText("Ziel speichern").performScrollTo().performClick()
        compose.waitUntil(5000) {repo.state.value.goals.size==1}
        assertEquals(.4f,repo.state.value.goals.single().fraction,.001f)
        compose.onNodeWithText("Lernwoche").assertExists()
    }
    @Test fun widgetLayoutPersists() {
        compose.onNodeWithContentDescription("Dashboard anpassen").performClick()
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodesWithContentDescription("Nach oben")[1].performClick()
        compose.onNodeWithText("Layout übernehmen").performScrollTo().performClick()
        compose.waitUntil(5000) {repo.state.value.settings.widgets.first().kind==WidgetKind.AGENDA}
        assertFalse(repo.state.value.settings.widgets.single {it.kind==WidgetKind.FOCUS}.visible)
        val persisted=HubRepository(compose.activity);runBlocking {persisted.load()}
        assertEquals(repo.state.value.settings.widgets,persisted.state.value.settings.widgets)
    }
    @Test fun invalidBackupDoesNotChangeDataAndSecretsAreExcluded() {
        runBlocking {repo.saveTask(HubTask(title="Keep me"))}
        val store=SecretStore(compose.activity);val token="test-only-"+"x".repeat(40);store.setToken(token)
        assertEquals(token,store.token())
        val export=HubJson.export(repo.state.value)
        assertFalse(export.contains(token))
        try {runBlocking {repo.importBackup("{\"schemaVersion\":999}")};fail("Should reject unknown schema")}catch(expected: IllegalArgumentException) { }
        assertEquals("Keep me",repo.state.value.tasks.single().title)
        store.setToken("")
    }
}
