package de.simonhub.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun openNativeAlarms(context:Context,add:Boolean=false):Boolean = try {
    context.startActivity(Intent(if(add)AlarmClock.ACTION_SET_ALARM else AlarmClock.ACTION_SHOW_ALARMS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_:android.content.ActivityNotFoundException) {false}
  catch (_:SecurityException) {false}

@Composable internal fun NativeAlarmsCard() {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val manager=remember(context) {context.getSystemService(AlarmManager::class.java)}
    var next by remember {mutableStateOf<AlarmManager.AlarmClockInfo?>(null)}
    var readFailed by remember {mutableStateOf(false)}
    DisposableEffect(context,lifecycle,manager) {
        fun refresh() {
            runCatching {manager.nextAlarmClock}.onSuccess {next=it;readFailed=false}.onFailure {next=null;readFailed=true}
        }
        val receiver=object:BroadcastReceiver() {
            override fun onReceive(context:Context,intent:Intent) {refresh()}
        }
        val observer=LifecycleEventObserver {_,event->if(event==Lifecycle.Event.ON_RESUME)refresh()}
        ContextCompat.registerReceiver(context,receiver,IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),ContextCompat.RECEIVER_EXPORTED)
        lifecycle.addObserver(observer)
        refresh()
        onDispose {context.unregisterReceiver(receiver);lifecycle.removeObserver(observer)}
    }
    fun open(add:Boolean=false) {
        if(!openNativeAlarms(context,add))Toast.makeText(context,"Keine kompatible Uhr-App verfügbar.",Toast.LENGTH_SHORT).show()
    }
    fun openNext() {
        val show=next?.showIntent
        if(show==null)open() else try {show.send()} catch (_:PendingIntent.CanceledException) {open()}
    }
    HubCard {
        CardHeader("Wecker",Icons.Outlined.Alarm,action={
            IconButton(onClick={open(true)},modifier=Modifier.size(36.dp).testTag("add-native-alarm")) {
                Icon(Icons.Outlined.Add,"Wecker hinzufügen",Modifier.size(20.dp))
            }
        })
        Column(Modifier.fillMaxWidth().clickable {openNext()}.padding(vertical=10.dp).testTag("next-native-alarm")) {
            Text(if(next!=null)"NÄCHSTER WECKER"else"UHR-APP",fontSize=9.sp,letterSpacing=1.2.sp,color=muted())
            val alarm=next
            if(alarm!=null) {
                val instant=Instant.ofEpochMilli(alarm.triggerTime).atZone(ZoneId.systemDefault())
                Text(instant.format(DateTimeFormatter.ofPattern("HH:mm",German)),Modifier.padding(top=5.dp).testTag("native-alarm-time"),fontSize=29.sp,fontWeight=FontWeight.Medium)
                Text(instant.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",German)),Modifier.padding(top=3.dp),fontSize=12.sp,color=muted())
            } else Text(if(readFailed)"Wecker konnte nicht gelesen werden"else"Kein nächster Wecker gemeldet",Modifier.padding(top=6.dp),fontSize=13.sp,color=muted())
        }
        TextButton(onClick={open()},contentPadding=PaddingValues(0.dp),modifier=Modifier.testTag("show-native-alarms")) {
            Text("Alle Alarme");Icon(Icons.Outlined.OpenInNew,null,Modifier.padding(start=7.dp).size(15.dp))
        }
    }
}
