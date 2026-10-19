@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class,androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package de.simonhub.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.simonhub.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val German=Locale.GERMAN
internal fun time(i: Instant)=i.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm",German))
internal fun number(v: Double)=if(v%1.0==0.0)v.toLong().toString()else String.format(German,"%.1f",v)
internal fun areaIcon(a: Area): ImageVector=when(a) {Area.STUDY->Icons.Outlined.School;Area.FITNESS->Icons.Outlined.FitnessCenter;Area.PROJECT->Icons.Outlined.Code;Area.PERSONAL->Icons.Outlined.PersonOutline}
@Composable internal fun muted()=MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun HubTheme(s: HubSettings,content: @Composable ()->Unit) {
    val dark=when(s.theme) {"light"->false;"system"->isSystemInDarkTheme();else->true}
    val activity=LocalContext.current as? android.app.Activity
    SideEffect { activity?.let {
        val controls=androidx.core.view.WindowCompat.getInsetsController(it.window,it.window.decorView)
        controls.isAppearanceLightStatusBars=!dark
        controls.isAppearanceLightNavigationBars=!dark
    } }
    // Akzentfarbe: Schlüssel bleiben stabil (Backup/Restore, Tests); "mono" ist jetzt Koralle.
    val primary=when(s.accent) {"lavender"->if(dark)Color(0xFFD0BFFD)else Color(0xFF705196);"sky"->if(dark)Color(0xFFA8D7EB)else Color(0xFF316A80);"sage"->if(dark)Color(0xFFBCDDA9)else Color(0xFF4A683F);else->if(dark)Color(0xFFD97757)else Color(0xFFB2502D)}
    val colors=if(dark)darkColorScheme(
        primary=primary,onPrimary=Color(0xFF1E120C),primaryContainer=primary.copy(alpha=.14f),onPrimaryContainer=primary,
        secondary=Color(0xFFCFC4B6),secondaryContainer=Color(0xFF2E2822),onSecondaryContainer=Color(0xFFF3ECE3),
        background=Color(0xFF15120F),surface=Color(0xFF1C1814),surfaceVariant=Color(0xFF26211C),
        onBackground=Color(0xFFF3ECE3),onSurface=Color(0xFFF3ECE3),onSurfaceVariant=Color(0xFFB0A598),
        error=Color(0xFFF0707F),onError=Color(0xFF2A0A10),
        outline=Color(0xFF776C60),outlineVariant=Color(0xFF3A332C))
        else lightColorScheme(
            primary=primary,onPrimary=Color.White,primaryContainer=primary.copy(alpha=.10f),onPrimaryContainer=primary,
            secondary=Color(0xFF5B5148),secondaryContainer=Color(0xFFE9E1D5),onSecondaryContainer=Color(0xFF2A241E),
            background=Color(0xFFF6F1E9),surface=Color(0xFFFCF9F4),surfaceVariant=Color(0xFFEDE6DB),
            onBackground=Color(0xFF211C17),onSurface=Color(0xFF211C17),onSurfaceVariant=Color(0xFF6A6056),
            error=Color(0xFFB02A3C),onError=Color.White,
            outline=Color(0xFF8A7F72),outlineVariant=Color(0xFFDDD4C7))
    MaterialTheme(colorScheme=colors,typography=Typography(),shapes=Shapes(
        small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(28.dp)),content=content)
}

@Composable fun SimonHub(vm: HubViewModel,shared: String?=null) {
    val state by vm.state.collectAsStateWithLifecycle();val ready by vm.ready.collectAsStateWithLifecycle();val error by vm.error.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle();val month by vm.month.collectAsStateWithLifecycle();val events by vm.events.collectAsStateWithLifecycle();val monthEvents by vm.monthEvents.collectAsStateWithLifecycle();val busy by vm.busy.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle();val focus by vm.focus.collectAsStateWithLifecycle()
    var tab by rememberSaveable {mutableIntStateOf(0)};var editTask by remember {mutableStateOf<HubTask?>(null)};var taskDialog by rememberSaveable {mutableStateOf(shared!=null)}
    var taskSaving by remember {mutableStateOf(false)}
    var taskSaveError by remember {mutableStateOf<String?>(null)}
    LaunchedEffect(taskDialog,editTask?.id) {if(taskDialog)taskSaveError=null}
    var customize by remember {mutableStateOf(false)}
    var dayPopupDate by remember {mutableStateOf<LocalDate?>(null)}
    var dayPopupOrigin by remember {mutableStateOf(Rect.Zero)}
    var homeEditing by remember {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    var showPlan by remember {mutableStateOf(false)};var reading by remember {mutableStateOf<HubNote?>(null)}
    val snack=remember {SnackbarHostState()};val context=LocalContext.current
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed->if(!allowed)vm.launch {vm.notices.emit("Benachrichtigungen sind aus. Du kannst sie in Android freigeben.")}}
    fun askNotifications() {if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)notifications.launch(Manifest.permission.POST_NOTIFICATIONS)}
    LaunchedEffect(vm) {vm.notices.collect {snack.showSnackbar(it)}}
    LaunchedEffect(vm) {
        vm.completedTasks.collect {completed->
            if(snack.showSnackbar("Aufgabe erledigt",actionLabel="Rückgängig",withDismissAction=true,duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed)vm.undoTaskCompletion(completed)
        }
    }
    HubTheme(state.settings) {
        if(!ready) {Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),contentAlignment=Alignment.Center) {
            if(error==null)CircularProgressIndicator()else Text("Deine Daten bleiben erhalten. Laden fehlgeschlagen: $error",Modifier.padding(30.dp))
        };return@HubTheme}
        val outer=rememberPagerState(initialPage=1,pageCount={3});val outerScope=rememberCoroutineScope()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            HorizontalPager(state=outer,userScrollEnabled=!homeEditing && dayPopupDate==null && !taskDialog && !customize,modifier=Modifier.fillMaxSize(),key={it}) {outerPage->
                if(outerPage==0) {
                    SettingsPage(vm,state,active=outer.settledPage==0,onCustomize={customize=true},onBack={outerScope.launch {outer.animateScrollToPage(1)}})
                } else if(outerPage==1) {
                    val vertical=rememberPagerState(initialPage=0,pageCount={2});val verticalScope=rememberCoroutineScope()
                    val monthScroll=rememberLazyListState()
                    val homeAppsScroll=rememberLazyGridState()
                    val swipeDistance=with(LocalDensity.current) {32.dp.toPx()}
                    VerticalPager(state=vertical,userScrollEnabled=false,modifier=Modifier.fillMaxSize().pointerInput(vertical,tab,swipeDistance,homeAppsScroll,homeEditing,dayPopupDate,taskDialog,customize) {
                        awaitEachGesture {
                            val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                            if(homeEditing || dayPopupDate!=null || taskDialog || customize)return@awaitEachGesture
                            // A gesture that starts while apps can still scroll belongs entirely to the grid.
                            // Reaching its bottom during that gesture must not unexpectedly change pages.
                            val homeAtEnd=!homeAppsScroll.canScrollForward
                            var distance=Offset.Zero
                            while(true) {
                                val event=awaitPointerEvent(PointerEventPass.Initial)
                                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                if(!change.pressed || event.changes.count {it.pressed}>1)break
                                distance+=change.position-change.previousPosition
                                if(kotlin.math.abs(distance.y)>swipeDistance && kotlin.math.abs(distance.y)>kotlin.math.abs(distance.x)*1.3f) {
                                    val target=when {
                                        vertical.currentPage==0 && distance.y<0 && homeAtEnd->1
                                        vertical.currentPage==1 && distance.y>0 && tab==0 && !monthScroll.canScrollBackward->0
                                        else->null
                                    }
                                    if(target!=null && !vertical.isScrollInProgress) {
                                        change.consume()
                                        verticalScope.launch {vertical.animateScrollToPage(target,animationSpec=tween(220))}
                                        break
                                    }
                                }
                            }
                        }
                    },key={it}) {page->
                        if(page==0) LauncherHome(vm,appsScrollState=homeAppsScroll,onEditingChange={homeEditing=it},onToday={verticalScope.launch {vertical.animateScrollToPage(1)}},onAllApps={outerScope.launch {outer.animateScrollToPage(2)}})
                        else DashboardScaffold(backEnabled=dayPopupDate==null && vertical.settledPage==1 && outer.settledPage==1 && !homeEditing && !taskDialog && !customize,tab=tab.coerceIn(0,1),onTab={tab=it},onAdd={editTask=null;taskDialog=true},onBack={verticalScope.launch {vertical.animateScrollToPage(0)}}) {
                            when(tab.coerceIn(0,1)) {
                                0->TodayScreen(state,date,month,monthEvents,busy,monthScroll,vm::selectDate,{day,bounds->focusManager.clearFocus(force=true);keyboard?.hide();dayPopupOrigin=bounds;dayPopupDate=day},vm::selectMonth,{vm.refresh(true)},{customize=true},{outerScope.launch {outer.animateScrollToPage(0)}},{editTask=null;taskDialog=true},{editTask=it;taskDialog=true},vm::toggleTask)
                                else->TasksScreen(state.tasks,{editTask=it;taskDialog=true},vm::toggleTask,{editTask=null;taskDialog=true})
                            }
                        }
                    }
                        } else AppDrawer(vm,active=outer.settledPage==2 && !outer.isScrollInProgress && !taskDialog,onBack={outerScope.launch {outer.animateScrollToPage(1)}})
            }
            SnackbarHost(snack,Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            CalendarDayWindow(dayPopupDate,monthEvents,dayPopupOrigin,{dayPopupDate=null})
            if(taskDialog)TaskEditor(editTask,shared.takeIf {editTask==null},{taskDialog=false},{task->
                taskSaveError=null;taskSaving=true
                vm.saveTask(task,onSaved={taskDialog=false;if(task.reminder!=null)askNotifications()},onFinished={taskSaving=false},onFailed={taskSaveError=it})
            },{vm.deleteTask(it);taskDialog=false},saving=taskSaving,saveError=taskSaveError)
        }
        if(customize)WidgetEditor(state.settings,{customize=false},{widgets->vm.settings {it.copy(widgets=widgets)};customize=false})
        if(showPlan)PlanSheet(state.plan?.takeIf {it.date==date} ?: vm.plan(),{showPlan=false},{vm.savePlan(it);showPlan=false})
        if(reading!=null)NoteSheet(reading!!,{reading=null})
    }
}
@Composable internal fun Heading(eyebrow: String,title: String,subtitle: String) {
    Text(eyebrow.uppercase(German),fontSize=10.sp,color=MaterialTheme.colorScheme.primary,letterSpacing=2.sp)
    Text(title,Modifier.padding(top=8.dp),fontSize=30.sp,lineHeight=36.sp,fontWeight=FontWeight.Normal,fontFamily=FontFamily.Serif,letterSpacing=(-.5).sp)
    Text(subtitle,Modifier.padding(top=8.dp),fontSize=13.sp,color=muted())
}
@Composable internal fun HubCard(content: @Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {Column(Modifier.padding(16.dp),content=content)}
}
@Composable internal fun CardHeader(title: String,icon: ImageVector,caption: String?=null,action: @Composable ()->Unit={}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Icon(icon,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Text(title,Modifier.padding(start=9.dp).weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold);if(caption!=null)Text(caption,fontSize=11.sp,color=muted());action()}
}
@Composable internal fun Tag(text: String,color: Color=MaterialTheme.colorScheme.primary) {Text(text,Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha=.1f)).padding(8.dp,4.dp),color=color,fontSize=10.sp,fontWeight=FontWeight.Medium)}
@Composable internal fun EmptyCard(title: String,body: String,button: String,click: ()->Unit) {
    Text(title,Modifier.padding(top=20.dp),fontSize=17.sp,fontWeight=FontWeight.Medium);Text(body,Modifier.padding(top=7.dp),fontSize=13.sp,lineHeight=19.sp,color=muted())
    TextButton(onClick=click,contentPadding=PaddingValues(0.dp)) {Text(button);Icon(Icons.Outlined.ArrowForward,null,Modifier.padding(start=6.dp).size(16.dp))}
}

@Composable private fun TodayScreen(s: HubState,date: LocalDate,month: YearMonth,monthEvents: List<HubEvent>,busy: Boolean,scrollState:LazyListState,
    onDate: (LocalDate)->Unit,onOpenDay:(LocalDate,Rect)->Unit,onMonth: (YearMonth)->Unit,onRefresh: ()->Unit,onCustomize: ()->Unit,onConnect: ()->Unit,onAdd: ()->Unit,onTask: (HubTask)->Unit,onToggle: (String)->Unit) {
    val next=s.tasks.filter {!it.done}.minWithOrNull(compareBy<HubTask> {it.due ?: LocalDate.MAX}.thenBy {it.createdAt})
    LazyColumn(Modifier.fillMaxSize().testTag("today-scroll-content"),state=scrollState,contentPadding=PaddingValues(16.dp,18.dp,16.dp,18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Übersicht",fontSize=28.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.6).sp)
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d. MMMM",German)),Modifier.padding(top=4.dp),fontSize=12.sp,color=muted())
            }
            IconButton(onClick=onCustomize,modifier=Modifier.size(44.dp)) {Icon(Icons.Outlined.Tune,"Dashboard anpassen",Modifier.size(20.dp),tint=muted())}
        }}
        if(s.settings.widgets.firstOrNull {it.kind==WidgetKind.AGENDA}?.visible!=false)item {MonthCalendarCard(s,month,date,monthEvents,busy,onDate,onOpenDay,onMonth,onRefresh,onConnect)}
        item {NativeAlarmsCard()}
        if(s.settings.widgets.firstOrNull {it.kind==WidgetKind.TASKS}?.visible!=false)item {HubCard {
            CardHeader("Nächste Aufgabe",Icons.Outlined.Checklist,action={
                IconButton(onClick=onAdd,modifier=Modifier.size(30.dp)) {Icon(Icons.Outlined.Add,"Aufgabe hinzufügen",Modifier.size(19.dp))}
            })
            if(next==null)Text("Alles erledigt.",Modifier.padding(top=10.dp),fontSize=12.sp,color=muted())
            else TaskRow(next,onToggle,onTask)
        }}
    }
}
@Composable private fun MonthCalendarCard(s:HubState,month:YearMonth,selectedDate:LocalDate,events:List<HubEvent>,busy:Boolean,
    onDate:(LocalDate)->Unit,onOpenDay:(LocalDate,Rect)->Unit,onMonth:(YearMonth)->Unit,onRefresh:()->Unit,onConnect:()->Unit) {
    val days=(1..month.lengthOfMonth()).map {month.atDay(it)}
    val cells=List(month.atDay(1).dayOfWeek.value-1) {null}+days.map {it as LocalDate?}
    val padded=cells+List((7-cells.size%7)%7) {null}
    val monthLabel=month.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",German)).replaceFirstChar {it.titlecase(German)}
    HubCard {
        CardHeader("Monatskalender",Icons.Outlined.CalendarMonth,if(s.demo)"Beispiel"else if(events.isEmpty())null else "${events.distinctBy(::eventDisplayKey).size} Termine",action={
            IconButton(onClick=onRefresh,enabled=!busy,modifier=Modifier.size(30.dp)) {
                if(busy)CircularProgressIndicator(Modifier.size(15.dp),strokeWidth=2.dp) else Icon(Icons.Outlined.Sync,"Kalender aktualisieren",Modifier.size(17.dp),tint=muted())
            }
        })
        Row(Modifier.fillMaxWidth().padding(top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={onMonth(month.minusMonths(1))},modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.ChevronLeft,"Vorheriger Monat",Modifier.size(22.dp))}
            Text(monthLabel,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
            IconButton(onClick={onMonth(month.plusMonths(1))},modifier=Modifier.size(40.dp)) {Icon(Icons.Outlined.ChevronRight,"Nächster Monat",Modifier.size(22.dp))}
            TextButton(onClick={onDate(LocalDate.now())},contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.testTag("calendar-today")) {Text("Heute",fontSize=12.sp)}
        }
        Row(Modifier.fillMaxWidth().padding(bottom=6.dp)) {listOf("MO","DI","MI","DO","FR","SA","SO").forEach {Text(it,Modifier.weight(1f),fontSize=10.sp,color=muted(),fontWeight=FontWeight.Medium)}}
        val eventLineHeight=with(LocalDensity.current) {13.sp.toDp()}
        padded.chunked(7).forEach {week->
            val eventLists=week.map {day->day?.let {eventsForDate(events,it)} ?: emptyList()}
            val tileHeight=maxOf(72.dp,28.dp+eventLineHeight*(eventLists.maxOfOrNull {it.size} ?: 0))
            Row(Modifier.fillMaxWidth()) {week.forEachIndexed {dayIndex,day->
                if(day==null)Box(Modifier.weight(1f).height(tileHeight)) else {
                    var dayBounds by remember(day) {mutableStateOf(Rect.Zero)}
                    val dayEvents=eventLists[dayIndex]
                    val selected=day==selectedDate
                    val today=day==LocalDate.now()
                    Column(Modifier.weight(1f).height(tileHeight).padding(1.dp).clip(RoundedCornerShape(8.dp)).background(if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.48f)).onGloballyPositioned {dayBounds=it.boundsInRoot()}.clickable {onDate(day);onOpenDay(day,dayBounds)}.testTag("calendar-day-${day.dayOfMonth}").padding(horizontal=2.dp,vertical=2.dp)) {
                        Box(Modifier.padding(bottom=3.dp).size(22.dp).clip(CircleShape).background(if(today)MaterialTheme.colorScheme.primary else Color.Transparent),contentAlignment=Alignment.Center) {
                            Text(day.dayOfMonth.toString(),fontSize=11.sp,lineHeight=14.sp,fontWeight=if(selected || today)FontWeight.Bold else FontWeight.Normal,color=if(today)MaterialTheme.colorScheme.onPrimary else if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        }
                        dayEvents.forEachIndexed {index,event->
                            Text("* ${event.title}",Modifier.fillMaxWidth().height(eventLineHeight).testTag("month-event-${day.dayOfMonth}-$index"),fontSize=9.sp,lineHeight=12.sp,color=if(selected)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                    }
                }
            }}
        }
        if(events.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(s.settings.calendarEnabled)"Keine Termine in diesem Monat"else"Kalender verbinden",Modifier.weight(1f),fontSize=11.sp,color=muted())
                if(!s.settings.calendarEnabled)TextButton(onClick=onConnect,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) {Text("Verbinden",fontSize=10.sp)}
            }
        }
    }
}
private fun eventsForDate(events:List<HubEvent>,date:LocalDate):List<HubEvent> {
    val zone=ZoneId.systemDefault();val dayStart=date.atStartOfDay(zone).toInstant();val dayEnd=date.plusDays(1).atStartOfDay(zone).toInstant()
    return events.filter {event->if(event.allDay)date>=event.start.atZone(ZoneOffset.UTC).toLocalDate() && date<event.end.atZone(ZoneOffset.UTC).toLocalDate()else event.start<dayEnd && event.end>dayStart}.distinctBy(::eventDisplayKey).sortedBy {it.start}
}
private fun eventDisplayKey(event:HubEvent)=listOf(event.title.trim().lowercase(Locale.ROOT),event.start.toString(),event.end.toString(),event.allDay.toString()).joinToString("|")

@Composable internal fun CalendarDayWindow(selectedDay:LocalDate?,events:List<HubEvent>,origin:Rect,dismiss:()->Unit) {
    var retainedDay by remember {mutableStateOf(LocalDate.now())}
    SideEffect {selectedDay?.let {retainedDay=it}}
    val shownDay=selectedDay ?: retainedDay
    val transition=updateTransition(targetState=selectedDay!=null,label="calendar-day-window")
    val progress by transition.animateFloat(transitionSpec={spring(dampingRatio=.82f,stiffness=360f)},label="calendar-day-expansion") {visible->if(visible)1f else 0f}
    BackHandler(enabled=selectedDay!=null,onBack=dismiss)
    if(!transition.currentState && !transition.targetState)return
    val dayEvents=eventsForDate(events,shownDay)
    var targetBounds by remember {mutableStateOf(Rect.Zero)}
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha=(progress*.56f).coerceIn(0f,.56f)))
            .clickable(interactionSource=remember {MutableInteractionSource()},indication=null,onClick=dismiss),
        contentAlignment=Alignment.Center
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),contentAlignment=Alignment.Center) {
            val windowHeight=maxHeight*.86f
            Box(Modifier.widthIn(max=560.dp).fillMaxWidth(.92f).onGloballyPositioned {targetBounds=it.boundsInRoot()}.testTag("day-window")) {
                Surface(
                    Modifier.fillMaxWidth().graphicsLayer {
                        val p=progress.coerceIn(0f,1.03f)
                        alpha=progress.coerceIn(0f,1f)
                        scaleX=.12f+.88f*p;scaleY=scaleX
                        if(origin.width>0 && targetBounds.width>0) {
                            translationX=(origin.center.x-targetBounds.center.x)*(1f-p)
                            translationY=(origin.center.y-targetBounds.center.y)*(1f-p)
                        }
                        shadowElevation=24.dp.toPx()
                    }.clickable(interactionSource=remember {MutableInteractionSource()},indication=null) {},
                    shape=RoundedCornerShape(26.dp),color=MaterialTheme.colorScheme.surface,
                    border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.heightIn(max=windowHeight)) {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.6f)).padding(horizontal=16.dp,vertical=12.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                IconButton(onClick=dismiss,modifier=Modifier.size(44.dp).testTag("close-day-window")) {
                                    Icon(Icons.Outlined.Close,"Tagesansicht schließen",Modifier.size(20.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick=dismiss,modifier=Modifier.size(44.dp).testTag("back-day-window")) {Icon(Icons.Outlined.ArrowBack,"Zurück zum Monatskalender",Modifier.size(18.dp))}
                                Text("KALENDER",fontSize=10.sp,letterSpacing=1.5.sp,color=muted())
                            }
                            Text(shownDay.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy",German)),Modifier.padding(top=6.dp).testTag("day-window-title"),fontSize=18.sp,lineHeight=24.sp,fontWeight=FontWeight.Normal,fontFamily=FontFamily.Serif)
                            Text(if(dayEvents.size==1)"1 Termin"else"${dayEvents.size} Termine",Modifier.padding(top=3.dp),fontSize=11.sp,lineHeight=16.sp,color=muted())
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).testTag("day-window-events"),contentPadding=PaddingValues(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            if(dayEvents.isEmpty())item {Text("Keine Termine an diesem Tag.",Modifier.padding(vertical=20.dp),fontSize=13.sp,color=muted())}
                            items(dayEvents,key=::eventDisplayKey) {event->
                                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
                                    Text(calendarDayTime(event,shownDay),fontSize=11.sp,lineHeight=16.sp,color=MaterialTheme.colorScheme.primary,modifier=Modifier.testTag("day-event-time-${event.id}"))
                                    Text(event.title,Modifier.fillMaxWidth().padding(top=5.dp).testTag("day-event-title-${event.id}"),fontSize=16.sp,lineHeight=23.sp,fontWeight=FontWeight.Medium)
                                    if(event.calendar.isNotBlank())Text(event.calendar,Modifier.padding(top=6.dp),fontSize=11.sp,lineHeight=16.sp,color=muted())
                                    if(event.location.isNotBlank())Text(event.location,Modifier.padding(top=3.dp),fontSize=11.sp,lineHeight=16.sp,color=muted())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
private fun calendarDayTime(event:HubEvent,day:LocalDate):String {
    if(event.allDay)return "Ganztägig"
    val zone=ZoneId.systemDefault()
    fun label(value:Instant):String {
        val local=value.atZone(zone)
        return local.format(DateTimeFormatter.ofPattern(if(local.toLocalDate()==day)"HH:mm"else"dd.MM. HH:mm",German))
    }
    return "${label(event.start)} – ${label(event.end)}"
}

@Composable private fun CompactEventRow(e: HubEvent) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(e.allDay)"Ganztags"else time(e.start),Modifier.width(53.dp),fontSize=11.sp,fontWeight=FontWeight.Medium,color=muted())
        Box(Modifier.padding(end=10.dp).width(2.dp).height(30.dp).background(MaterialTheme.colorScheme.outline))
        Column(Modifier.weight(1f)) {
            Text(e.title,fontSize=12.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            val detail=listOf(if(e.allDay)null else time(e.end),e.location.takeIf {it.isNotBlank()}).filterNotNull().joinToString(" · ")
            if(detail.isNotBlank())Text(detail,Modifier.padding(top=2.dp),fontSize=10.sp,color=muted(),maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}
@Composable private fun StatBox(value: String,label: String,icon: ImageVector,modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {Icon(icon,null,Modifier.size(16.dp),tint=muted());Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.Bottom) {Text(value,fontSize=23.sp,fontWeight=FontWeight.Medium);Text(label,Modifier.padding(start=5.dp,bottom=3.dp),fontSize=10.sp,color=muted())}}
}
@Composable private fun FocusCard(task: HubTask?,plan: DayPlan,focus: FocusSession?,now: Instant,compact: Boolean,onPlan: ()->Unit,onFocus: (HubTask)->Unit,onStop: ()->Unit,onAdd: ()->Unit) {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(c.primaryContainer,c.surface))).border(1.dp,c.outlineVariant,RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(17.dp),tint=c.primary);Text(if(focus!=null)"DEIN FOKUS LÄUFT"else"DEIN NÄCHSTER SCHRITT",Modifier.weight(1f).padding(start=8.dp),fontSize=10.sp,letterSpacing=1.2.sp,fontWeight=FontWeight.SemiBold,color=c.primary);Tag(if(plan.source.startsWith("ChatGPT"))"MCP"else"Lokal")}
        Text(focus?.title ?: task?.title ?: "Mach Platz für das Wesentliche.",Modifier.padding(top=17.dp),fontSize=23.sp,lineHeight=30.sp,fontWeight=FontWeight.Normal,fontFamily=FontFamily.Serif,letterSpacing=(-.3).sp,maxLines=3,overflow=TextOverflow.Ellipsis)
        if(focus!=null) {val sec=Duration.between(now,focus.end).seconds.coerceAtLeast(0);Text("%02d:%02d".format(sec/60,sec%60),Modifier.padding(top=18.dp),fontSize=40.sp,color=c.primary,fontWeight=FontWeight.Light);TextButton(onClick=onStop) {Text("Fokus beenden")}}
        else {
            if(!compact)Text(task?.let {"${it.minutes} Min · ${it.area.label}"} ?: "Ein kleiner Anfang reicht. Wähle eine Aufgabe und gib ihr deine volle Aufmerksamkeit.",Modifier.padding(top=10.dp),fontSize=12.sp,lineHeight=19.sp,color=muted())
            FlowRow(Modifier.padding(top=18.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Button(onClick={if(task==null)onAdd()else onFocus(task)},contentPadding=PaddingValues(14.dp,12.dp)) {Icon(if(task==null)Icons.Outlined.Add else Icons.Outlined.PlayArrow,null,Modifier.size(17.dp));Text(if(task==null)"Aufgabe anlegen"else"Fokus starten",Modifier.padding(start=6.dp),fontSize=12.sp)}
                TextButton(onClick=onPlan) {Text("Tagesplan",fontSize=12.sp);Icon(Icons.Outlined.ArrowForward,null,Modifier.padding(start=5.dp).size(15.dp))}
            }
        }
    }
}
@Composable private fun EventRow(e: HubEvent) {
    Row(Modifier.fillMaxWidth().padding(top=19.dp)) {
        Column(Modifier.width(49.dp)) {Text(if(e.allDay)"Ganzt."else time(e.start),fontSize=12.sp,fontWeight=FontWeight.Medium);if(!e.allDay)Text(time(e.end),Modifier.padding(top=3.dp),fontSize=10.sp,color=muted())}
        Box(Modifier.padding(start=5.dp,end=12.dp).width(3.dp).height(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha=.8f)))
        Column(Modifier.weight(1f)) {Text(e.title,fontSize=14.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis);Text(listOf(e.calendar,e.location).filter {it.isNotBlank()}.joinToString(" · "),Modifier.padding(top=4.dp),fontSize=10.sp,color=muted(),maxLines=1,overflow=TextOverflow.Ellipsis)}
    }
}
@Composable internal fun TaskRow(t: HubTask,onToggle: (String)->Unit,onClick: (HubTask)->Unit) {
    val today=LocalDate.now()
    val due=t.due
    val reminder=t.reminder
    val overdue=!t.done && due?.let {it<today}==true
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {onClick(t)}.padding(vertical=4.dp).testTag("task-row-"+t.id),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick={onToggle(t.id)},modifier=Modifier.size(44.dp)) {
            Icon(if(t.done)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,if(t.done)"Wieder öffnen"else"Erledigen",Modifier.size(24.dp),tint=if(t.done)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        }
        Column(Modifier.weight(1f).padding(start=8.dp)) {
            Text(t.title,fontSize=15.sp,lineHeight=21.sp,color=if(t.done)muted()else MaterialTheme.colorScheme.onSurface,maxLines=2,overflow=TextOverflow.Ellipsis,textDecoration=if(t.done)androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
            val dateLabel=when {due==null->"Ohne Datum";overdue->"Überfällig · "+due.format(DateTimeFormatter.ofPattern("dd.MM."));due==today->"Heute";due==today.plusDays(1)->"Morgen";else->due.format(DateTimeFormatter.ofPattern("dd.MM."))}
            Text(dateLabel+" · "+t.minutes+" Min",Modifier.padding(top=4.dp),fontSize=11.sp,color=if(overdue)MaterialTheme.colorScheme.error else muted(),maxLines=1,overflow=TextOverflow.Ellipsis)
            if(reminder!=null && !t.done)Row(Modifier.padding(top=3.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsNone,null,Modifier.size(12.dp),tint=muted())
                Text(time(reminder),Modifier.padding(start=3.dp),fontSize=11.sp,color=muted())
            }
        }
    }
}
@Composable internal fun GoalRow(g: HubGoal,onClick: (HubGoal)->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {onClick(g)}.padding(top=20.dp,bottom=4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {Icon(areaIcon(g.area),null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.secondary);Text(g.title,Modifier.weight(1f).padding(start=9.dp),fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis);Text("${(g.fraction*100).toInt()}%",fontSize=12.sp,color=MaterialTheme.colorScheme.primary)}
        val progress by animateFloatAsState(g.fraction,label="goal-progress")
        LinearProgressIndicator(progress={progress},modifier=Modifier.fillMaxWidth().padding(top=12.dp).height(5.dp).clip(CircleShape),trackColor=MaterialTheme.colorScheme.outlineVariant)
        Text("${number(g.current)} / ${number(g.target)} ${g.unit}",Modifier.padding(top=7.dp),fontSize=10.sp,color=muted())
    }
}
@Composable internal fun TasksScreen(tasks: List<HubTask>,onTask: (HubTask)->Unit,onToggle: (String)->Unit,onAdd: ()->Unit) {
    var query by rememberSaveable {mutableStateOf("")}
    var filter by rememberSaveable {mutableStateOf("Offen")}
    val today=LocalDate.now()
    val matching=remember(tasks,query,filter,today) {matchingTasks(tasks,query,filter,today)}
    val openCount=tasks.count {!it.done}
    LazyColumn(Modifier.fillMaxSize().testTag("tasks-list"),contentPadding=PaddingValues(20.dp,22.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("Aufgaben",fontSize=30.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.8).sp)
                Text(if(openCount==0)"Alles erledigt. Zeit für etwas anderes."else openCount.toString()+" offen · ein Schritt nach dem anderen.",Modifier.padding(top=6.dp),fontSize=13.sp,color=muted())
            }
        }
        item {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().testTag("task-search"),
                placeholder={Text("Titel und Notizen durchsuchen",fontSize=14.sp)},leadingIcon={Icon(Icons.Outlined.Search,null)},
                trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}) {Icon(Icons.Outlined.Close,"Aufgabensuche leeren")}},
                singleLine=true,shape=RoundedCornerShape(18.dp))
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("Offen","Heute","Erledigt").forEach {label->
                    FilterChip(selected=filter==label,onClick={filter=label},
                        label={Text(label)},modifier=Modifier.testTag("task-filter-"+label))
                }
            }
        }
        if(matching.isEmpty())item {
            HubCard {
                Icon(if(query.isNotBlank())Icons.Outlined.SearchOff else Icons.Outlined.TaskAlt,null,Modifier.size(32.dp),tint=muted())
                EmptyCard(
                    when {query.isNotBlank()->"Keine Treffer";filter=="Erledigt"->"Noch nichts abgehakt";filter=="Heute"->"Heute ist noch frei";else->"Alles erledigt"},
                    when {query.isNotBlank()->"Versuche einen anderen Suchbegriff.";filter=="Heute"->"Hier erscheinen Aufgaben mit heutigem oder überfälligem Datum.";else->"Neue Gedanken kannst du direkt als Aufgabe festhalten."},
                    if(query.isNotBlank())"Suche leeren"else"Aufgabe anlegen",
                    {if(query.isNotBlank())query=""else onAdd()})
            }
        }
        matching.groupBy {taskGroup(it,today)}.forEach {(label,group)->
            item(key="group-"+label) {
                Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(label,Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=if(label=="Überfällig")MaterialTheme.colorScheme.error else muted())
                    Text(group.size.toString(),fontSize=12.sp,color=muted())
                }
            }
            items(group,key={it.id}) {task->HubCard {TaskRow(task,onToggle,onTask)}}
        }
    }
}
@Composable private fun GoalsScreen(goals: List<HubGoal>,onGoal: (HubGoal)->Unit,onAdd: ()->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp,28.dp,20.dp,100.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {Column {Heading("Die Richtung zählt","Deine Ziele.","Fortschritt in deinem eigenen Tempo.")}}
        if(goals.isEmpty())item {HubCard {EmptyCard("Was ist dir wichtig?","Setze dir ein messbares Ziel. Fortschritte trägst du bewusst selbst ein.","Erstes Ziel anlegen",onAdd)}}
        items(goals,key={it.id}) {g->HubCard {CardHeader(g.area.label,areaIcon(g.area));GoalRow(g,onGoal);TextButton(onClick={onGoal(g)}) {Text("Fortschritt aktualisieren");Icon(Icons.Outlined.ArrowForward,null,Modifier.padding(start=8.dp).size(15.dp))}}}
    }
}

