package de.simonhub.app

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import de.simonhub.core.HubEvent
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HubCompactHomeUiTest:HubUiFixture() {
    @Test fun busyDayStillLeavesRoomForAppsOnShortScreens() {
        val model=androidx.lifecycle.ViewModelProvider(compose.activity)[HubViewModel::class.java]
        val launch=Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm=Shadows.shadowOf(compose.activity.packageManager)
        (0..7).forEach {index->
            val id="compact.app"+index
            pm.addResolveInfoForIntent(launch,ResolveInfo().apply {
                nonLocalizedLabel="Kompakt "+index
                activityInfo=ActivityInfo().apply {
                    packageName=id;name=id+".MainActivity"
                    applicationInfo=ApplicationInfo().apply {packageName=id}
                }
            })
        }
        model.refreshLauncherApps()
        compose.waitUntil(10_000) {compose.onAllNodesWithTag("app-compact.app0").fetchSemanticsNodes().isNotEmpty()}
        awaitState {!model.busy.value}
        val now=Instant.now()
        model.homeEvents.value=(1..6).map {index->
            val start=now.plusSeconds(index*3600L)
            HubEvent("compact-event"+index,"Langer Termin "+index,start,start.plusSeconds(1800))
        }
        compose.waitUntil(10_000) {compose.onAllNodesWithText("+ 3 weitere Termine").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("digital-clock").assertIsDisplayed()
        compose.onNodeWithTag("home-weather").assertIsDisplayed()
        val bounds=compose.onNodeWithTag("home-app-grid").getUnclippedBoundsInRoot()
        assertTrue("Apps need usable space on compact home: "+bounds,(bounds.bottom-bounds.top)>=64.dp)
        val tile=compose.onNodeWithTag("app-compact.app0").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("The first app tile must fit completely above the footer",tile.bottom<=bounds.bottom)
    }
}
