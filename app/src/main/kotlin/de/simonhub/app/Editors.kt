@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package de.simonhub.app

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import de.simonhub.core.*
import java.time.*
import java.time.format.*
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

@Composable internal fun TaskEditor(task: HubTask?,shared: String?,dismiss: ()->Unit,save: (HubTask)->Unit,delete: (String)->Unit,windowInsets:WindowInsets=WindowInsets.systemBars.union(WindowInsets.ime),saving:Boolean=false,saveError:String?=null) {
    val zone=ZoneId.systemDefault()
    val existingTime=task?.reminder?.atZone(zone)?.toLocalTime()
    val nextReminder=LocalDateTime.now().plusMinutes(15).let {it.plusMinutes(((5-it.minute%5)%5).toLong()).withSecond(0).withNano(0)}
    val initialTime=existingTime ?: nextReminder.toLocalTime()
    var title by rememberSaveable(task?.id) {mutableStateOf(task?.title ?: shared ?: "")}
    var area by rememberSaveable(task?.id) {mutableStateOf(task?.area ?: Area.PERSONAL)}
    var minutes by rememberSaveable(task?.id) {mutableStateOf((task?.minutes ?: 25).toString())}
    var reminderDate by rememberSaveable(task?.id) {mutableStateOf(task?.due ?: task?.reminder?.atZone(zone)?.toLocalDate() ?: nextReminder.toLocalDate())}
    var reminderEnabled by rememberSaveable(task?.id) {mutableStateOf(task?.let {it.reminder!=null} ?: true)}
    var hour by rememberSaveable(task?.id) {mutableIntStateOf(initialTime.hour)}
    var minute by rememberSaveable(task?.id) {mutableIntStateOf(initialTime.minute)}
    var details by rememberSaveable(task?.id) {mutableStateOf(task?.details ?: "")}
    var advanced by rememberSaveable(task?.id) {mutableStateOf(false)}
    var showDatePicker by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    val displayedError=error ?: saveError
    var confirm by remember {mutableStateOf(false)}
    val focusRequester=remember {FocusRequester()}
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    var closing by remember {mutableStateOf(false)}
    fun finish(action:()->Unit) {closing=true;focusManager.clearFocus(force=true);keyboard?.hide();action()}
    fun submit() {
        if(saving)return
        error=null
        runCatching {
                    require(title.isNotBlank()) {"Bitte einen Titel eingeben."}
                    val r=if(reminderEnabled)LocalDateTime.of(reminderDate,LocalTime.of(hour,minute)).atZone(zone).toInstant()else null
                    val sameReminder=task?.reminder==r
                    require(r==null || r>Instant.now() || sameReminder) {"Bitte eine zukünftige Erinnerungszeit wählen."}
                    val m=minutes.toIntOrNull() ?: 25
                    require(m in 5..480) {"Bitte 5 bis 480 Minuten eingeben."}
                    val saved=(task ?: HubTask(title=title.trim())).copy(title=title.trim(),area=area,minutes=m,due=reminderDate,reminder=r,details=details)
                    finish {save(saved)}
                }.onFailure {error=it.message ?: "Aufgabe konnte nicht gespeichert werden."}
    }
    val context=LocalContext.current
    val currentDate by rememberUpdatedState(reminderDate)
    DisposableEffect(Unit) {
        onDispose {keyboard?.hide()}
    }
    LaunchedEffect(showDatePicker) {
        if(showDatePicker) {
            val date=currentDate
            DatePickerDialog(context,{_,year,month,day->reminderDate=LocalDate.of(year,month+1,day);showDatePicker=false},date.year,date.monthValue-1,date.dayOfMonth).apply {
                setOnCancelListener {showDatePicker=false}
            }.show()
        }
    }
    BackHandler {if(!saving)finish(dismiss)}
    LaunchedEffect(Unit) {
        if(task==null) {
            withFrameNanos { }
            if(!closing) {focusRequester.requestFocus();keyboard?.show()}
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha=.62f))
            .clickable(interactionSource=remember {MutableInteractionSource()},indication=null) {if(!saving)finish(dismiss)}
            .testTag("task-editor-overlay"),
        contentAlignment=Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(windowInsets),
            contentAlignment=Alignment.BottomCenter
        ) {
            val availableHeight=maxHeight
            val keyboardVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0 || availableHeight<520.dp
            val wheelHeight=(availableHeight-260.dp).coerceIn(36.dp,84.dp)
            Surface(
                Modifier.fillMaxWidth().heightIn(max=availableHeight).clickable(interactionSource=remember {MutableInteractionSource()},indication=null) {}.testTag("task-editor-panel"),
                shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),color=MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(displayedError ?: if(task==null)"Neue Aufgabe"else"Aufgabe bearbeiten",Modifier.weight(1f),fontSize=if(displayedError==null)18.sp else 12.sp,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,color=if(displayedError==null)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,fontWeight=FontWeight.Medium)
                        IconButton(onClick={finish(dismiss)},enabled=!saving,modifier=Modifier.size(28.dp)) {Icon(Icons.Outlined.Close,"Aufgabe schließen",Modifier.size(18.dp))}
                    }
                    OutlinedTextField(title,{title=it.take(240)},Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("task-title"),label={Text("Was möchtest du erledigen?",fontSize=12.sp)},singleLine=true,enabled=!saving,shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Done),keyboardActions=KeyboardActions(onDone={submit()}))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        TextButton(onClick={showDatePicker=true},modifier=Modifier.weight(1f).testTag("task-reminder-date"),contentPadding=PaddingValues(horizontal=0.dp)) {
                            Icon(Icons.Outlined.CalendarMonth,null,Modifier.size(16.dp))
                            Text(reminderDateLabel(reminderDate),Modifier.padding(start=6.dp),fontSize=12.sp)
                            Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp))
                        }
                        Text("Erinnerung",fontSize=11.sp)
                        Switch(checked=reminderEnabled,onCheckedChange={reminderEnabled=it},modifier=Modifier.testTag("task-reminder-toggle"))
                    }
                    if(reminderEnabled)TimeWheelPicker(hour,minute,{hour=it},{minute=it},compact=true,compactHeight=wheelHeight)
                    if(!keyboardVisible) {
                        TextButton(onClick={advanced=!advanced},contentPadding=PaddingValues(0.dp)) {Text(if(advanced)"Weniger Optionen"else"Mehr Optionen",fontSize=12.sp)}
                        if(advanced)Column(Modifier.fillMaxWidth().heightIn(max=(availableHeight-400.dp).coerceIn(0.dp,160.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {Area.entries.forEach {a->FilterChip(selected=area==a,onClick={area=a},label={Text(a.label,fontSize=11.sp)})}}
                            OutlinedTextField(minutes,{minutes=it.filter(Char::isDigit).take(3)},Modifier.fillMaxWidth(),label={Text("Dauer in Minuten")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
                            OutlinedTextField(details,{details=it.take(4000)},Modifier.fillMaxWidth(),label={Text("Notizen")},maxLines=3)
                            if(task!=null)TextButton(onClick={confirm=true}) {Text("Aufgabe löschen")}
                        }
                    }
                Button(onClick={submit()},enabled=!saving,modifier=Modifier.fillMaxWidth().height(48.dp).testTag("task-save")) {
                    Text(if(saving)"Speichert …"else"Aufgabe speichern",fontSize=if(keyboardVisible)13.sp else 14.sp)
                }
                }
            }
        }
    }
    if(confirm)DeleteDialog("Aufgabe löschen?",task?.title ?: "",{confirm=false},{finish {delete(task!!.id)}})
}

private fun reminderDateLabel(date:LocalDate):String=when(date) {
    LocalDate.now()->"Heute"
    LocalDate.now().plusDays(1)->"Morgen"
    else->date.format(DateTimeFormatter.ofPattern("EEE d. MMMM",Locale.GERMAN)).replaceFirstChar {it.titlecase(Locale.GERMAN)}
}

@Composable private fun TimeWheelPicker(hour:Int,minute:Int,onHour:(Int)->Unit,onMinute:(Int)->Unit,compact:Boolean,compactHeight:Dp=72.dp) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
        TimeWheel("Stunde",hour,0..23,onHour,compact,compactHeight)
        Text(":",Modifier.padding(horizontal=8.dp),fontSize=22.sp,fontWeight=FontWeight.Medium)
        TimeWheel("Minute",minute,0..59,onMinute,compact,compactHeight)
    }
}
@Composable private fun TimeWheel(label:String,selected:Int,range:IntRange,onSelected:(Int)->Unit,compact:Boolean,compactHeight:Dp=72.dp) {
    val rowHeight=if(compact)compactHeight/3 else 36.dp
    val compactTextSize=with(LocalDensity.current) {(rowHeight.toSp().value*.8f).coerceIn(8f,16f).sp}
    val wheelLineHeight=with(LocalDensity.current) {rowHeight.toSp()}
    val listState=rememberLazyListState(initialFirstVisibleItemIndex=selected.coerceIn(range))
    val currentOnSelected by rememberUpdatedState(onSelected)
    val centeredIndex by remember(listState) {derivedStateOf {
        val info=listState.layoutInfo
        val center=info.viewportStartOffset+info.viewportSize.height/2
        info.visibleItemsInfo.minByOrNull {kotlin.math.abs(it.offset+it.size/2-center)}?.index
    }}
    LaunchedEffect(listState,compact,compactHeight) {
        listState.scrollToItem(selected.coerceIn(range)+(if(compact)1 else 0))
        snapshotFlow {
            val info=listState.layoutInfo
            val center=info.viewportStartOffset+info.viewportSize.height/2
            info.visibleItemsInfo.minByOrNull {kotlin.math.abs(it.offset+it.size/2-center)}?.index?.minus(2)
        }.distinctUntilChanged().collect {index->if(index!=null && index in range)currentOnSelected(index)}
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label,fontSize=10.sp,color=muted())
        LazyColumn(state=listState,modifier=Modifier.padding(top=if(compact)1.dp else 6.dp).width(72.dp).height(if(compact)compactHeight else 180.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),flingBehavior=rememberSnapFlingBehavior(lazyListState=listState)) {
            items(2) {Spacer(Modifier.fillMaxWidth().height(rowHeight))}
            items(range.count()) {offset->
                val value=range.first+offset
                val centered=centeredIndex==offset+2
                Box(Modifier.fillMaxWidth().height(rowHeight).background(if(centered)MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),contentAlignment=Alignment.Center) {
                    Text(value.toString().padStart(2,'0'),fontSize=if(compact)if(centered)compactTextSize else compactTextSize*.75f else if(centered)19.sp else 14.sp,lineHeight=wheelLineHeight,color=if(centered)MaterialTheme.colorScheme.onPrimaryContainer else muted(),fontWeight=if(centered)FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            items(2) {Spacer(Modifier.fillMaxWidth().height(rowHeight))}
        }
    }
}
@Composable internal fun GoalEditor(goal: HubGoal?,dismiss: ()->Unit,save: (HubGoal)->Unit,delete: (String)->Unit) {
    val existing=goal?.takeIf {it.title.isNotBlank()};var title by rememberSaveable {mutableStateOf(existing?.title ?: "")};var area by rememberSaveable {mutableStateOf(existing?.area ?: Area.STUDY)}
    var current by rememberSaveable {mutableStateOf((existing?.current ?: 0.0).toString())};var target by rememberSaveable {mutableStateOf((existing?.target ?: 5.0).toString())}
    var unit by rememberSaveable {mutableStateOf(existing?.unit ?: "Einheiten")};var error by remember {mutableStateOf<String?>(null)};var confirm by remember {mutableStateOf(false)}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(if(existing==null)"Eine Richtung setzen."else"Dein Fortschritt.",fontSize=24.sp,fontWeight=FontWeight.Medium)
            OutlinedTextField(title,{title=it.take(120)},Modifier.fillMaxWidth(),label={Text("Ziel")},singleLine=true)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {Area.entries.forEach {a->FilterChip(selected=area==a,onClick={area=a},label={Text(a.label,fontSize=12.sp)})}}
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {OutlinedTextField(current,{current=it.take(12)},Modifier.weight(1f),label={Text("Aktuell")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true);OutlinedTextField(target,{target=it.take(12)},Modifier.weight(1f),label={Text("Zielwert")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)}
            OutlinedTextField(unit,{unit=it.take(30)},Modifier.fillMaxWidth(),label={Text("Einheit")},singleLine=true)
            Text("Manuelle Werte. Kein automatisch abgeleiteter Fitness- oder Lernerfolg.",fontSize=11.sp,lineHeight=17.sp,color=muted())
            if(error!=null)Text(error!!,fontSize=12.sp,color=MaterialTheme.colorScheme.error)
            Button(onClick={runCatching {
                val c=current.replace(',','.').toDoubleOrNull();val t=target.replace(',','.').toDoubleOrNull()
                require(title.isNotBlank()) {"Bitte einen Titel eingeben."};require(c!=null && c.isFinite() && c>=0 && t!=null && t.isFinite() && t>0) {"Aktuell mindestens 0, Zielwert größer als 0."}
                save((existing ?: HubGoal(title=title,target=t)).copy(title=title.trim(),area=area,current=c,target=t,unit=unit.trim()))
            }.onFailure {error=it.message}},Modifier.fillMaxWidth().height(50.dp)) {Text("Ziel speichern")}
            if(existing!=null)TextButton(onClick={confirm=true},Modifier.fillMaxWidth()) {Text("Ziel löschen")}
        }
    }
    if(confirm)DeleteDialog("Ziel löschen?",existing?.title ?: "",{confirm=false},{delete(existing!!.id)})
}
@Composable private fun DeleteDialog(title: String,body: String,dismiss: ()->Unit,delete: ()->Unit) {AlertDialog(onDismissRequest=dismiss,title={Text(title)},text={Text(body)},confirmButton={TextButton(onClick=delete) {Text("Löschen")}},dismissButton={TextButton(onClick=dismiss) {Text("Behalten")}})}
@Composable internal fun WidgetEditor(s: HubSettings,dismiss: ()->Unit,save: (List<WidgetConfig>)->Unit) {
    var widgets by remember {mutableStateOf(s.widgets)}
    fun setVisible(kind:WidgetKind,value:Boolean) {widgets=widgets.map {if(it.kind==kind)it.copy(visible=value)else it}}
    val calendar=widgets.firstOrNull {it.kind==WidgetKind.AGENDA} ?: WidgetConfig(WidgetKind.AGENDA)
    val tasks=widgets.firstOrNull {it.kind==WidgetKind.TASKS} ?: WidgetConfig(WidgetKind.TASKS)
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("Heute anpassen",fontSize=23.sp,fontWeight=FontWeight.Medium)
            Text("Der Heute-Bildschirm bleibt bei Kalender und einer nächsten Aufgabe.",fontSize=12.sp,color=muted())
            listOf(Triple(WidgetKind.AGENDA,"Kalender","Termine ganz oben anzeigen.") to calendar,Triple(WidgetKind.TASKS,"Nächste Aufgabe","Eine offene Aufgabe darunter anzeigen.") to tasks).forEach {(entry,config)->
                val (kind,title,description)=entry
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text(title,fontSize=14.sp);Text(description,Modifier.padding(top=3.dp),fontSize=11.sp,color=muted())}
                    Switch(checked=config.visible,onCheckedChange={setVisible(kind,it)},modifier=Modifier.semantics {contentDescription="$title anzeigen"})
                }
            }
            Button(onClick={save(widgets)},Modifier.fillMaxWidth().height(48.dp)) {Text("Layout übernehmen")}
        }
    }
}
@Composable internal fun PlanSheet(p: DayPlan,dismiss: ()->Unit,save: (DayPlan)->Unit) {
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Dein Tag hat einen Plan.",fontSize=24.sp,fontWeight=FontWeight.Medium);Tag(p.source)
            Text(p.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",German)),fontSize=13.sp,color=muted());Text(p.explanation,fontSize=14.sp,lineHeight=21.sp)
            Text("${p.freeMinutes} Min freie Zeit vor Aufgaben · 5 Min Puffer",fontSize=11.sp,color=muted())
            p.blocks.forEach {b->Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant) {Row(Modifier.padding(16.dp)) {Column(Modifier.width(54.dp)) {Text(time(b.start),fontSize=13.sp,color=MaterialTheme.colorScheme.primary);Text(time(b.end),fontSize=11.sp,color=muted())};Column {Text(b.title,fontSize=14.sp);Text(b.area.label,Modifier.padding(top=5.dp),fontSize=11.sp,color=muted())}}}}
            if(p.unscheduled.isNotEmpty()) {Text("Passt heute nicht mehr",fontSize=15.sp,fontWeight=FontWeight.Medium);p.unscheduled.forEach {Text("• ${it.title} · ${it.minutes} Min",fontSize=12.sp,color=muted())}}
            Text("Vorschläge berücksichtigen nur deine freigegebenen Kalender und Aufgaben. Zeitblöcke bleiben in Simon Hub und verändern keine Kalendertermine.",fontSize=11.sp,lineHeight=17.sp,color=muted())
            Button(onClick={save(p)},Modifier.fillMaxWidth().height(50.dp),enabled=p.blocks.isNotEmpty()) {Text("Plan übernehmen")}
        }
    }
}
@Composable internal fun NoteSheet(n: HubNote,dismiss: ()->Unit) {
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=36.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(n.title,fontSize=25.sp,fontWeight=FontWeight.Medium);Text(n.path,fontSize=11.sp,color=MaterialTheme.colorScheme.secondary);Text("Lokale Lesekopie · Stand des letzten Imports",fontSize=10.sp,color=muted())
            androidx.compose.foundation.text.selection.SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {n.content.lineSequence().take(1500).forEach {line->
                when {line.startsWith("# ")->Text(line.removePrefix("# "),fontSize=24.sp,fontWeight=FontWeight.Medium);line.startsWith("## ")->Text(line.removePrefix("## "),fontSize=19.sp,fontWeight=FontWeight.Medium);line.startsWith("### ")->Text(line.removePrefix("### "),fontSize=16.sp,fontWeight=FontWeight.Medium);else->Text(line,fontSize=13.sp,lineHeight=20.sp)}
            }}}
        }
    }
}

