package de.simonhub.app

import androidx.compose.ui.test.*
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import org.robolectric.Shadows
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import de.simonhub.core.HubState
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubLauncherUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()


    private fun awaitUi(timeout:Long=10_000,condition:()->Boolean) {
        val end=System.nanoTime()+timeout*1_000_000
        while(System.nanoTime()<end) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeByFrame()
            if(condition()) {compose.waitForIdle();return}
            Thread.sleep(10)
        }
        println(compose.onRoot(useUnmergedTree=true).printToString())
        println("Saved home settings="+model.repository.state.value.settings)
        org.junit.Assert.fail("UI condition did not update; error="+androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].error.value)
    }

    private val model get()=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java]

    @Before fun clean() {
        runBlocking {model.repository.update {HubState()}}
        awaitUi(10_000) {compose.onAllNodesWithTag("launcher-home").fetchSemanticsNodes().isNotEmpty()}
    }

    @Test fun appSearchNormalizesCaseWhitespaceAndGermanLetters() {
        org.junit.Assert.assertEquals(normalizeAppSearch("WhatsApp"),normalizeAppSearch("  WHATSAPP  "))
        org.junit.Assert.assertEquals(normalizeAppSearch("Straße"),normalizeAppSearch("STRASSE"))
        org.junit.Assert.assertEquals(normalizeAppSearch("ＡＢＣ"),normalizeAppSearch("abc"))
        listOf("whatsapp","WHATSAPP","WhAtSaPp","Whats App"," whats\u200Bapp ").forEach {query->org.junit.Assert.assertTrue(query,matchesAppSearch("WhatsApp","com.whatsapp",query))}
        org.junit.Assert.assertTrue(matchesAppSearch("École","app.ecole","ECOLE"))
        org.junit.Assert.assertFalse(matchesAppSearch("Signal","org.thoughtcrime","whatsapp"))
    }

    @Test fun homeShowsClockWeatherTodaysCalendarAndEditableApps() {
        compose.onNodeWithTag("digital-clock").assertExists()
        compose.onNodeWithTag("home-weather").assertExists()
        compose.onNodeWithTag("home-today-calendar").assertExists()
        compose.onNodeWithText("APPS").assertExists()
        compose.onNodeWithContentDescription("Home-Apps auswählen").assertExists()
        compose.onNodeWithTag("open-today-page").assertExists()
    }

    @Test fun homeAppSelectionOpens() {
        compose.onNodeWithContentDescription("Home-Apps auswählen").performClick()
        compose.onNodeWithText("Apps auf Home").assertExists()
        compose.onNodeWithTag("home-app-picker-list").assertExists()
        compose.onNodeWithText("Speichern").assertExists()
    }

    @Test fun leftSwipeOpensSearchableDrawerAndFiltersApps() {
        compose.onNodeWithTag("launcher-home").performTouchInput {swipeLeft()}
        awaitUi(10_000) {compose.onAllNodesWithTag("app-drawer").fetchSemanticsNodes().isNotEmpty()}
        awaitUi(10_000) {runCatching {compose.onNodeWithTag("app-search").assertIsFocused();true}.getOrDefault(false)}
        compose.onNodeWithTag("app-search").assertExists().assertIsFocused().performTextInput("zz-no-such-app-741")
        compose.onNodeWithText("Keine Treffer").assertExists()
        compose.onNodeWithTag("app-drawer").performTouchInput {swipeRight()}
        awaitUi(10_000) {compose.onAllNodesWithTag("launcher-home").fetchSemanticsNodes().isNotEmpty()}
    }


    @Test fun appSearchMatchesInstagramForAllLetterCasesWithoutSettingsSubstring() {
        val pm=Shadows.shadowOf(compose.activity.packageManager)
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        fun addApp(label:String,packageId:String) {
            pm.addResolveInfoForIntent(intent,ResolveInfo().apply {
                nonLocalizedLabel=label
                activityInfo=ActivityInfo().apply {
                    packageName=packageId;name="$packageId.MainActivity"
                    applicationInfo=ApplicationInfo().apply {packageName=packageId}
                }
            })
        }
        addApp("Instagram","test.instagram")
        addApp("Einstellungen","test.settings")
        androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].refreshLauncherApps()
        compose.onNodeWithTag("launcher-home").performTouchInput {swipeLeft()}
        awaitUi(10_000) {runCatching {compose.onNodeWithTag("app-search").assertIsFocused()}.isSuccess}
        awaitUi(10_000) {compose.onAllNodesWithTag("drawer-test.instagram").fetchSemanticsNodes().isNotEmpty()}
        listOf("ins","INS","Ins","iNs").forEach {query->
            compose.onNodeWithTag("app-search").performTextReplacement(query)
            compose.onNodeWithTag("drawer-test.instagram").assertIsDisplayed()
            compose.onNodeWithTag("drawer-test.settings").assertDoesNotExist()
        }
    }

    @Test fun rightSwipeOpensSettingsAndBackReturnsHome() {
        compose.onNodeWithTag("launcher-home").performTouchInput {swipeRight()}
        awaitUi(10_000) {runCatching {compose.onNodeWithTag("settings-page").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertExists()
        compose.onNodeWithContentDescription("Zurück zu Home").performClick()
        awaitUi(10_000) {runCatching {compose.onNodeWithTag("launcher-home").assertIsDisplayed()}.isSuccess}
    }

    @Test fun verticalSwipeSnapsBetweenHomeAndToday() {
        compose.onNodeWithTag("launcher-home").performTouchInput {swipe(center,center.copy(y=center.y-55.dp.toPx()),300)}
        awaitUi(10_000) {compose.onAllNodesWithTag("today-dashboard").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("today-dashboard").performTouchInput {swipe(center,center.copy(y=center.y+55.dp.toPx()),300)}
        awaitUi(10_000) {compose.onAllNodesWithTag("launcher-home").fetchSemanticsNodes().isNotEmpty()}
    }

    @Test fun overflowingHomeAppsScrollBeforeTheMonthPageOpens() {
        val pm=Shadows.shadowOf(compose.activity.packageManager)
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages=(0 until 32).map {"test.homeapp%02d".format(it)}
        packages.forEachIndexed {index,packageId->
            pm.addResolveInfoForIntent(intent,ResolveInfo().apply {
                nonLocalizedLabel="Home App %02d".format(index)
                activityInfo=ActivityInfo().apply {
                    packageName=packageId;name="$packageId.MainActivity"
                    applicationInfo=ApplicationInfo().apply {packageName=packageId}
                }
            })
        }
        runBlocking {model.repository.update {it.copy(settings=it.settings.copy(homeAppCount=32,homeAppPackages=packages.toSet()))}}
        androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].refreshLauncherApps()
        awaitUi(10_000) {compose.onAllNodesWithTag("app-${packages.first()}").fetchSemanticsNodes().isNotEmpty()}
        val grid=compose.onNodeWithTag("home-app-grid")
        grid.performTouchInput {swipe(center.copy(y=height*.8f),center.copy(y=height*.2f),300)}
        compose.onNodeWithTag("launcher-home").assertIsDisplayed()
        val range=grid.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange]
        org.junit.Assert.assertTrue("App tiles must have scrolled",range.value()>0f)
        // A fresh gesture at the end can now open the next page.
        grid.performScrollToIndex(31)
        grid.performTouchInput {swipe(center,center.copy(y=center.y-55.dp.toPx()),300)}
        awaitUi(10_000) {runCatching {compose.onNodeWithTag("today-dashboard").assertIsDisplayed()}.isSuccess}
        compose.onNodeWithTag("today-scroll-content").performScrollToNode(hasText("Wecker"))
        compose.onNodeWithTag("next-native-alarm",useUnmergedTree=true).assertExists()
    }


    @Test fun homeLayoutSavesVisibilitySizeAndSectionOrderAcrossRecreation() {
        compose.onNodeWithTag("home-customize").performClick()
        compose.onNodeWithTag("home-toggle-clock").performClick()
        compose.onNodeWithTag("home-toggle-weather").performClick()
        compose.onNodeWithTag("home-toggle-calendar").performClick()
        compose.onNodeWithTag("home-columns-3").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("home-section-up-apps").performScrollTo()
        compose.onNodeWithTag("home-section-up-apps").performClick()
        compose.onNodeWithTag("home-layout-save").assertIsDisplayed().performClick()
        awaitUi(10_000) {model.repository.state.value.settings.homeColumns==3}
        val settings=model.repository.state.value.settings
        org.junit.Assert.assertEquals(listOf("info","apps","calendar"),settings.homeSectionOrder)
        compose.onNodeWithTag("digital-clock").assertDoesNotExist()
        compose.onNodeWithTag("home-weather").assertDoesNotExist()
        compose.onNodeWithTag("home-today-calendar").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        awaitUi(10_000) {compose.onAllNodesWithTag("launcher-home").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("digital-clock").assertDoesNotExist()
        org.junit.Assert.assertEquals(settings.homeSectionOrder,model.repository.state.value.settings.homeSectionOrder)
        val persisted=de.simonhub.data.HubRepository(model.getApplication<android.app.Application>())
        runBlocking {persisted.load()}
        org.junit.Assert.assertEquals(3,persisted.state.value.settings.homeColumns)
        org.junit.Assert.assertFalse(persisted.state.value.settings.homeShowClock)
    }

    @Test fun dragReordersHomeAppsAndDoesNotNavigateAway() {
        val pm=Shadows.shadowOf(compose.activity.packageManager)
        val intent=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packages=(0..3).map {"test.drag$it"}
        packages.forEachIndexed {index,id->
            pm.addResolveInfoForIntent(intent,ResolveInfo().apply {
                nonLocalizedLabel="Drag $index"
                activityInfo=ActivityInfo().apply {
                    packageName=id;name="$id.MainActivity"
                    applicationInfo=ApplicationInfo().apply {packageName=id}
                }
            })
        }
        runBlocking {model.repository.update {it.copy(settings=it.settings.copy(homeAppPackages=packages.toSet()))}}
        androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java].refreshLauncherApps()
        awaitUi(10_000) {compose.onAllNodesWithTag("app-test.drag0").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("home-edit-apps").performClick()
        val first=compose.onNodeWithTag("app-test.drag0").fetchSemanticsNode().boundsInRoot
        val second=compose.onNodeWithTag("app-test.drag1").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("app-test.drag0").performTouchInput {
            swipe(center,center+androidx.compose.ui.geometry.Offset(second.center.x-first.center.x,0f),800)
        }
        compose.onNodeWithTag("home-edit-done").performClick()
        awaitUi(10_000) {model.repository.state.value.settings.homeAppOrder.firstOrNull()=="test.drag1"}
        compose.onNodeWithTag("launcher-home").assertIsDisplayed()
        val saved=model.repository.state.value.settings.homeAppOrder
        org.junit.Assert.assertEquals(listOf("test.drag1","test.drag0","test.drag2","test.drag3"),saved)
    }

}

