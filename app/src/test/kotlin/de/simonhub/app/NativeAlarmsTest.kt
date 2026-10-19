package de.simonhub.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import de.simonhub.core.HubSettings
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(application=OfflineHubApplication::class,sdk=[35],qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NativeAlarmsTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()

    @Test fun alarmButtonsUseTheNativeClockActions() {
        compose.setContent {HubTheme(HubSettings()) {NativeAlarmsCard()}}
        compose.onNodeWithText("Kein nächster Wecker gemeldet").assertExists()
        compose.onNodeWithTag("add-native-alarm").performClick()
        val add=Shadows.shadowOf(context as android.app.Application).nextStartedActivity
        assertEquals(AlarmClock.ACTION_SET_ALARM,add.action)
        assertFalse(add.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI,false))
        compose.onNodeWithTag("show-native-alarms").performClick()
        assertEquals(AlarmClock.ACTION_SHOW_ALARMS,Shadows.shadowOf(context as android.app.Application).nextStartedActivity.action)
    }

    @Test fun systemAlarmChangesRefreshTheCardAndOpenTheOwningClock() {
        val manager=context.getSystemService(AlarmManager::class.java)
        val operation=PendingIntent.getBroadcast(context,101,Intent("test.alarm.fire"),PendingIntent.FLAG_IMMUTABLE)
        val show=PendingIntent.getActivity(context,102,Intent("test.clock.show"),PendingIntent.FLAG_IMMUTABLE)
        val triggerAt=System.currentTimeMillis()+3_600_000L
        compose.setContent {HubTheme(HubSettings()) {NativeAlarmsCard()}}
        compose.runOnIdle {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt,show),operation)
            context.sendBroadcast(Intent(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        }
        compose.waitUntil(5_000) {compose.onAllNodesWithTag("native-alarm-time",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("native-alarm-time",useUnmergedTree=true).assertTextEquals(time(java.time.Instant.ofEpochMilli(triggerAt)))
        compose.onNodeWithTag("next-native-alarm").performClick()
        assertEquals("test.clock.show",Shadows.shadowOf(context as android.app.Application).nextStartedActivity.action)
        compose.runOnIdle {
            manager.cancel(operation)
            context.sendBroadcast(Intent(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED))
        }
        compose.waitUntil(5_000) {compose.onAllNodesWithText("Kein nächster Wecker gemeldet").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("native-alarm-time",useUnmergedTree=true).assertDoesNotExist()
    }
}

