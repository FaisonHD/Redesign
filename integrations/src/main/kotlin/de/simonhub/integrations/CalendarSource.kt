package de.simonhub.integrations

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.provider.CalendarContract as C
import androidx.core.content.ContextCompat
import de.simonhub.core.HubEvent
import kotlinx.coroutines.*
import java.time.*

data class DeviceCalendar(val id: String,val name: String,val account: String)
class CalendarSource(private val context: Context) {
    fun permitted()=ContextCompat.checkSelfPermission(context,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED
    suspend fun calendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if(!permitted())return@withContext emptyList()
        val result=mutableListOf<DeviceCalendar>()
        context.contentResolver.query(C.Calendars.CONTENT_URI,arrayOf(C.Calendars._ID,C.Calendars.CALENDAR_DISPLAY_NAME,C.Calendars.ACCOUNT_NAME),null,null,C.Calendars.CALENDAR_DISPLAY_NAME)?.use {
            while(it.moveToNext())result+=DeviceCalendar(it.getString(0),it.getString(1) ?: "Kalender",it.getString(2) ?: "")
        };result
    }
    suspend fun events(date: LocalDate,ids: Set<String>,zone: ZoneId=ZoneId.systemDefault()): List<HubEvent> = events(date,date.plusDays(1),ids,zone)
    suspend fun events(startDate: LocalDate,endDateExclusive: LocalDate,ids: Set<String>,zone: ZoneId=ZoneId.systemDefault()): List<HubEvent> = withContext(Dispatchers.IO) {
        if(!permitted() || ids.isEmpty())return@withContext emptyList()
        require(startDate<endDateExclusive)
        val start=startDate.atStartOfDay(zone).toInstant();val end=endDateExclusive.atStartOfDay(zone).toInstant()
        val uri=C.Instances.CONTENT_URI.buildUpon().also {ContentUris.appendId(it,start.minusSeconds(86400).toEpochMilli());ContentUris.appendId(it,end.plusSeconds(86400).toEpochMilli())}.build()
        val columns=arrayOf(C.Instances.EVENT_ID,C.Instances.TITLE,C.Instances.BEGIN,C.Instances.END,C.Instances.ALL_DAY,C.Instances.CALENDAR_DISPLAY_NAME,C.Instances.EVENT_LOCATION)
        val where="${C.Instances.CALENDAR_ID} IN (${ids.joinToString(",") {"?"}}) AND (${C.Instances.STATUS} IS NULL OR ${C.Instances.STATUS} != ?) AND (${C.Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${C.Instances.SELF_ATTENDEE_STATUS} != ?)"
        val args=ids.toList()+C.Events.STATUS_CANCELED.toString()+C.Attendees.ATTENDEE_STATUS_DECLINED.toString();val result=mutableListOf<HubEvent>()
        context.contentResolver.query(uri,columns,where,args.toTypedArray(),"${C.Instances.BEGIN} ASC")?.use {
            while(it.moveToNext()) {
                val a=Instant.ofEpochMilli(it.getLong(2));val b=Instant.ofEpochMilli(it.getLong(3));val all=it.getInt(4)==1
                val intersects=if(all)startDate<b.atZone(ZoneOffset.UTC).toLocalDate() && endDateExclusive>a.atZone(ZoneOffset.UTC).toLocalDate()else a<end && b>start
                if(intersects)result+=HubEvent("${it.getString(0)}:${it.getLong(2)}",it.getString(1) ?: "Ohne Titel",a,b,all,it.getString(5) ?: "",it.getString(6) ?: "")
            }
        };result.distinctBy {it.id}
    }
}
