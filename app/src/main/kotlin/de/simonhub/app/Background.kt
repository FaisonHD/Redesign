package de.simonhub.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import androidx.core.app.*
import androidx.core.content.ContextCompat
import androidx.work.*
import de.simonhub.core.HubTask
import java.time.*
import java.util.concurrent.TimeUnit

object Reminders {
    fun channel(c: Context) {c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("hub-reminders","Aufgaben & Fokus",NotificationManager.IMPORTANCE_DEFAULT))}
    fun schedule(c: Context,t: HubTask) {
        val wm=WorkManager.getInstance(c);val at=t.reminder
        if(t.done || at==null || at<=Instant.now()) {wm.cancelUniqueWork("task-${t.id}");return}
        wm.enqueueUniqueWork("task-${t.id}",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(Duration.between(Instant.now(),at).toMillis().coerceAtLeast(0),TimeUnit.MILLISECONDS).setInputData(workDataOf("id" to t.id,"at" to at.toString())).build())
    }
    fun bridge(c: Context,enabled: Boolean) {
        val wm=WorkManager.getInstance(c)
        if(!enabled) {wm.cancelUniqueWork("mcp-sync");return}
        wm.enqueueUniquePeriodicWork("mcp-sync",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<BridgeSyncWorker>(15,TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun notify(c: Context,id: Int,title: String,text: String) {
        if(android.os.Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return
        if(!NotificationManagerCompat.from(c).areNotificationsEnabled())return
        val intent=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationManagerCompat.from(c).notify(id,NotificationCompat.Builder(c,"hub-reminders").setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text)).setContentIntent(intent).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build())
    }
}
class TaskReminderWorker(c: Context,p: WorkerParameters): CoroutineWorker(c,p) {
    override suspend fun doWork(): Result=try {
        val repo=applicationContext.hub.repository;repo.load();val t=repo.state.value.tasks.find {it.id==inputData.getString("id")}
        if(t!=null && !t.done && t.reminder?.toString()==inputData.getString("at"))Reminders.notify(applicationContext,t.id.hashCode(),"Dein nächster Schritt",t.title)
        Result.success()
    } catch(e: Exception) {Result.retry()}
}
class BridgeSyncWorker(c: Context,p: WorkerParameters): CoroutineWorker(c,p) {
    override suspend fun doWork(): Result=try {
        val a=applicationContext.hub;a.repository.load();val s=a.repository.state.value.settings
        if(s.bridgeEnabled) {
            val n=a.bridge.sync(if(s.calendarEnabled)a.calendar.events(LocalDate.now(),s.calendarIds)else emptyList())
            if(n>0)Reminders.notify(applicationContext,2049,"Neue Vorschläge aus ChatGPT","$n Vorschläge warten in deinem Hub.")
        };Result.success()
    } catch(e: Exception) {if(runAttemptCount<3)Result.retry()else Result.failure()}
}
class FocusWorker(c: Context,p: WorkerParameters): CoroutineWorker(c,p) {
    override suspend fun doWork(): Result {
        val prefs=applicationContext.getSharedPreferences("focus",0)
        if(prefs.getString("end",null)==inputData.getString("end"))Reminders.notify(applicationContext,2048,"Fokus abgeschlossen",inputData.getString("title") ?: "Zeit für eine Pause.")
        return Result.success()
    }
}
